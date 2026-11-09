// Состояние приложения — перенос ChatRepository.kt из приложения для Android.
import * as E2E from './crypto.js';
import { Api, ApiError, deviceName } from './api.js';

export const api = new Api();

class Emitter {
  constructor() { this.map = new Map(); }
  on(ev, fn) {
    if (!this.map.has(ev)) this.map.set(ev, new Set());
    this.map.get(ev).add(fn);
    return () => this.map.get(ev)?.delete(fn);
  }
  emit(ev, ...args) {
    for (const fn of [...(this.map.get(ev) ?? [])]) {
      try { fn(...args); } catch (e) { console.error(e); }
    }
  }
}

const SESSION_KEY = 'rc.session';
const uuid = () => crypto.randomUUID();

function loadSession() {
  try { return JSON.parse(localStorage.getItem(SESSION_KEY) || 'null'); } catch { return null; }
}
function saveSession(s) {
  try { s ? localStorage.setItem(SESSION_KEY, JSON.stringify(s)) : localStorage.removeItem(SESSION_KEY); } catch { /* */ }
}

export function errorText(e) {
  if (e instanceof ApiError) return e.message || 'Ошибка';
  return e?.message && /[а-яё]/i.test(e.message) ? e.message : 'Нет связи с сервером';
}

export const QUICK_REACTIONS = ['❤️', '👍', '😂', '🔥', '😮', '😢', '🎉', '👎', '🙏', '🤝'];

class Store extends Emitter {
  constructor() {
    super();
    this.auth = 'loading'; // loading | out | in
    this.me = null;
    this.privateKey = null;
    this.chats = [];
    this.users = new Map();
    this.messages = new Map(); // chatId -> UiMessage[]
    this.hasMore = new Map();
    this.decryptCache = new Map(); // `${id}:${editedAt}` -> Content|null
    this.typing = new Map(); // chatId -> Map(userId -> until)
    this.readStates = new Map(); // chatId -> Map(userId -> seq)
    this.contacts = [];
    this.stickerPacks = [];
    this.connected = false;
    this.openChatId = null;
    this.media = new Map(); // fileId -> { state, url, progress, error }
    this.pendingLogin = null;
    this.ws = null;
    this.viewed = new Set();
    this.pendingViews = new Map();
    setInterval(() => this.tickTyping(), 1000);
    setInterval(() => this.poll(), 6000);
  }

  get myId() { return this.me?.id ?? null; }

  // ================= Вход =================

  async restore() {
    await api.loadConfig();
    const s = loadSession();
    if (!s?.token || !s.wrappedKey) return this.setAuth('out');
    api.token = s.token;
    try {
      this.privateKey = await E2E.unwrapLocal(s.wrappedKey);
    } catch {
      saveSession(null);
      return this.setAuth('out');
    }
    let me;
    try {
      me = await api.me();
    } catch (e) {
      if (e.status === 401) { saveSession(null); api.token = null; this.privateKey = null; return this.setAuth('out'); }
      me = { id: s.userId, username: s.username, displayName: s.username };
    }
    this.onLoggedIn(me);
  }

  setAuth(a) {
    this.auth = a;
    this.emit('auth');
  }

  async register(username, displayName, password) {
    const keys = await E2E.derivePasswordKeys(username, password);
    const kp = E2E.generateKeyPair();
    const res = await api.register({
      username, displayName, password: keys.authKey,
      publicKey: E2E.b64(kp.publicKey),
      encryptedPrivateKey: await E2E.sealPrivateKey(kp.privateKey, keys.vaultKey),
      device: deviceName(),
    });
    await this.finishAuth(res, kp.privateKey);
  }

  /** Вход. Возвращает null, если вход завершён, или ответ с need2fa. */
  async login(username, password) {
    const keys = await E2E.derivePasswordKeys(username, password);
    const res = await api.login(username, keys.authKey, deviceName());
    if (res.need2fa && res.challengeId) {
      this.pendingLogin = { challengeId: res.challengeId, vaultKey: keys.vaultKey };
      return res;
    }
    let priv;
    try { priv = await E2E.openPrivateKey(res.encryptedPrivateKey, keys.vaultKey); } catch { throw new Error('Не удалось расшифровать ключи аккаунта'); }
    await this.finishAuth(res, priv);
    return null;
  }

  async confirmLogin2fa(password) {
    const p = this.pendingLogin;
    if (!p) throw new Error('Начните вход заново');
    const res = await api.login2fa(p.challengeId, password);
    let priv;
    try { priv = await E2E.openPrivateKey(res.encryptedPrivateKey, p.vaultKey); } catch { throw new Error('Не удалось расшифровать ключи аккаунта'); }
    this.pendingLogin = null;
    await this.finishAuth(res, priv);
  }

  async finishAuth(res, priv) {
    api.token = res.token;
    this.privateKey = priv;
    saveSession({ token: res.token, userId: res.user.id, username: res.user.username, wrappedKey: await E2E.wrapLocal(priv) });
    this.onLoggedIn(res.user);
  }

  onLoggedIn(me) {
    this.me = me;
    this.rememberUsers([me]);
    this.setAuth('in');
    this.refreshChats();
    this.refreshMe().catch(() => {});
    this.refreshContacts().catch(() => {});
    this.refreshStickers().catch(() => {});
    this.connectSocket();
  }

  async logout() {
    try { await api.logout(); } catch { /* всё равно выходим */ }
    this.resetLocal();
  }

  resetLocal() {
    saveSession(null);
    this.closeSocket();
    api.token = null;
    this.privateKey = null;
    this.me = null;
    this.chats = [];
    this.chatsLoaded = false;
    this.users.clear();
    this.messages.clear();
    this.hasMore.clear();
    this.decryptCache.clear();
    this.contacts = [];
    for (const m of this.media.values()) if (m.url) URL.revokeObjectURL(m.url);
    this.media.clear();
    this.setAuth('out');
  }

  async changePassword(oldP, newP) {
    const oldK = await E2E.derivePasswordKeys(this.me.username, oldP);
    const newK = await E2E.derivePasswordKeys(this.me.username, newP);
    await api.changePassword(oldK.authKey, newK.authKey, await E2E.sealPrivateKey(this.privateKey, newK.vaultKey));
  }

  async changeUsername(newName, password) {
    const name = newName.trim().replace(/^@/, '');
    const oldK = await E2E.derivePasswordKeys(this.me.username, password);
    const newK = await E2E.derivePasswordKeys(name, password);
    await api.changeUsername(name, oldK.authKey, newK.authKey, await E2E.sealPrivateKey(this.privateKey, newK.vaultKey));
    const s = loadSession();
    if (s) saveSession({ ...s, username: name });
    await this.refreshMe();
  }

  async deleteAccount(password) {
    const k = await E2E.derivePasswordKeys(this.me.username, password);
    await api.deleteAccount(k.authKey);
    this.resetLocal();
  }

  async set2fa(accountPassword, current, password, hint) {
    const authKey = (await E2E.derivePasswordKeys(this.me.username, accountPassword)).authKey;
    this.setMe(await api.set2fa(authKey, current, password, hint.trim()));
  }

  async disable2fa(password) { this.setMe(await api.disable2fa(password)); }

  async myFingerprint() { return this.privateKey ? E2E.fingerprint(E2E.publicFromPrivate(this.privateKey)) : ''; }
  async fingerprintOf(u) { try { return await E2E.fingerprint(E2E.unb64(u.publicKey)); } catch { return '—'; } }

  // ================= Пользователи =================

  setMe(u) {
    this.me = u;
    this.users.set(u.id, u);
    this.emit('me');
    this.emit('users');
  }

  rememberUsers(list, full = false) {
    if (!list?.length) return;
    let changed = false;
    for (const u of list) {
      if (!u) continue;
      const old = this.users.get(u.id);
      const merged = { ...(old ?? {}), ...u };
      if (old && JSON.stringify(old) === JSON.stringify(merged)) continue;
      this.users.set(u.id, merged);
      changed = true;
    }
    if (!changed) return;
    const mine = list.find((u) => u && u.id === this.myId);
    if (mine && this.me) {
      this.me = full ? mine : {
        ...mine, has2fa: this.me.has2fa, twofaHint: this.me.twofaHint, flux: this.me.flux, premiumUntil: this.me.premiumUntil,
        restrictedUntil: this.me.restrictedUntil, restrictReason: this.me.restrictReason,
      };
      this.emit('me');
    }
    this.emit('users');
  }

  user(id) { return this.users.get(id); }
  async refreshMe() { this.rememberUsers([await api.me()], true); }
  async loadUser(id) { const u = await api.user(id); this.rememberUsers([u]); return u; }
  async searchUsers(s) { const l = await api.searchUsers(s); this.rememberUsers(l); return l; }

  async updateProfile(fields) { this.rememberUsers([await api.updateMe(fields)]); }

  async uploadPublic(blob, mime) { return (await api.upload(blob, mime || blob.type || 'application/octet-stream')).id; }

  async refreshContacts() {
    const l = await api.contacts();
    this.rememberUsers(l);
    this.contacts = l;
    this.emit('contacts');
  }

  async setContact(id, on) {
    const u = await api.setContact(id, on);
    this.rememberUsers([u]);
    this.contacts = on ? [...this.contacts.filter((x) => x.id !== u.id), u].sort((a, b) => a.displayName.localeCompare(b.displayName)) : this.contacts.filter((x) => x.id !== u.id);
    this.chats = this.chats.map((c) => c.type === 'direct' && c.members.some((m) => m.user.id === id) ? { ...c, peerIsContact: on } : c);
    this.emit('contacts');
    this.emit('chats');
  }

  async setBlocked(id, on) {
    const u = await api.setBlocked(id, on);
    this.rememberUsers([u]);
    if (on) this.contacts = this.contacts.filter((x) => x.id !== id);
    this.chats = this.chats.map((c) => c.type === 'direct' && c.members.some((m) => m.user.id === id) ? { ...c, peerBlocked: on, peerIsContact: c.peerIsContact && !on } : c);
    this.emit('contacts');
    this.emit('chats');
  }

  // ================= Стикеры =================

  async refreshStickers() {
    this.stickerPacks = await api.stickerPacks();
    this.emit('stickers');
  }

  async setPackAdded(id, on) {
    const p = await api.setPackAdded(id, on);
    this.stickerPacks = on ? [...this.stickerPacks.filter((x) => x.id !== p.id), p] : this.stickerPacks.filter((x) => x.id !== id);
    this.emit('stickers');
    return p;
  }

  sendSticker(chatId, pack, s, replyTo) {
    this.sendContent(chatId, 'sticker', { text: '', sticker: { packId: pack.id, id: s.id, fileId: s.fileId, emoji: s.emoji || '' } }, replyTo, null);
  }

  // ================= Чаты =================

  async refreshChats() {
    try {
      const tok = api.token;
      const list = await api.chats();
      if (api.token !== tok) return;
      this.rememberUsers(list.flatMap((c) => c.members.map((m) => m.user)));
      const previews = this.chats.filter((c) => (c.type === 'channel' || c.type === 'group') && c.myRole == null && !list.some((n) => n.id === c.id));
      // Опрос приходит каждые несколько секунд и обычно ничего не меняет:
      // оставляем старые объекты и не будим интерфейс зря.
      const old = new Map(this.chats.map((c) => [c.id, c]));
      const next = sortChats([...list, ...previews]).map((c) => {
        const o = old.get(c.id);
        return o && o !== c && JSON.stringify(o) === JSON.stringify(c) ? o : c;
      });
      const same = next.length === this.chats.length && next.every((c, i) => c === this.chats[i]);
      if (!same || !this.chatsLoaded) {
        this.chatsLoaded = true;
        this.chats = next;
        this.emit('chats');
      }
      this.decryptPreviews();
    } catch (e) {
      if (e.status === 401) this.resetLocal();
    }
  }

  async decryptPreviews() {
    let changed = false;
    for (const c of this.chats) {
      const m = c.lastMessage;
      if (m && !m.deleted && m.payload && !this.decryptCache.has(cacheKey(m))) { await this.decrypt(m); changed = true; }
    }
    if (changed) this.emit('chats');
  }

  upsertChat(chat) {
    this.rememberUsers(chat.members?.map((m) => m.user) ?? []);
    this.chats = sortChats([...this.chats.filter((c) => c.id !== chat.id), chat]);
    this.emit('chats');
    this.emit('chat', chat.id);
    this.decryptPreviews();
    return chat;
  }

  chat(id) { return this.chats.find((c) => c.id === id); }
  async loadChat(id) { return this.upsertChat(await api.chat(id)); }

  chatTitle(c) {
    if (!c) return '';
    if (c.type === 'saved') return 'Избранное';
    if (c.type === 'direct') return this.peerOf(c)?.displayName ?? 'Чат';
    return c.title;
  }

  peerOf(c) {
    if (c?.type !== 'direct') return null;
    const m = c.members.find((x) => x.user.id !== this.myId);
    if (!m) return null;
    return this.users.get(m.user.id) ?? m.user;
  }

  async openDirect(userId) { return this.upsertChat(await api.openDirect(userId)); }
  async createGroup(o) { return this.upsertChat(await api.createGroup(o)); }
  async createChannel(o) { return this.upsertChat(await api.createChannel(o)); }
  async updateChat(id, fields) { return this.upsertChat(await api.updateChat(id, fields)); }
  async chatSettings(id, fields) { return this.upsertChat(await api.chatSettings(id, fields)); }
  async addMembers(id, ids) { return this.upsertChat(await api.addMembers(id, ids)); }
  async removeMember(id, userId) {
    await api.removeMember(id, userId);
    if (userId === this.myId) this.dropChat(id); else await this.loadChat(id);
  }
  async leave(id) { await api.removeMember(id, this.myId); this.dropChat(id); }
  async deleteChat(id, forAll = true) { await api.deleteChat(id, forAll); this.dropChat(id); }
  dropChat(id) {
    this.chats = this.chats.filter((c) => c.id !== id);
    this.messages.delete(id);
    this.hasMore.delete(id);
    this.emit('chats');
    this.emit('removed', id);
  }
  async subscribe(id) { return this.upsertChat(await api.subscribe(id)); }
  async setRole(id, userId, admin) { return this.upsertChat(await api.setRole(id, userId, admin)); }
  async resetInvite(id) { return this.upsertChat(await api.resetInvite(id)); }
  async pinMessage(chatId, messageId) { return this.upsertChat(await api.pin(chatId, messageId)); }
  async setDiscussion(id, groupId) { return this.upsertChat(await api.setDiscussion(id, groupId)); }
  async createDiscussion(channel) {
    const g = await this.createGroup({ title: `${channel.title} — обсуждение`.slice(0, 128), memberIds: [], isPublic: channel.isPublic, description: '' });
    return this.setDiscussion(channel.id, g.id);
  }
  async invitePreviewAny(code) { return code.startsWith('@') ? api.chatByUsername(code) : api.invitePreview(code); }
  async joinAny(code, chat) { return this.upsertChat(code.startsWith('@') ? await api.subscribe(chat.id) : await api.joinInvite(code)); }
  /** Показать канал или группу без подписки (предпросмотр). */
  previewChat(chat) { if (!this.chat(chat.id)) this.upsertChat(chat); return chat; }
  async setWallpaper(chatId, blob) {
    const fileId = blob ? await this.uploadPublic(blob, blob.type || 'image/jpeg') : null;
    return this.upsertChat(await api.setWallpaper(chatId, fileId));
  }

  savedChat() { return this.chats.find((c) => c.type === 'saved'); }

  // ================= Сообщения =================

  messagesOf(chatId) { return this.messages.get(chatId) ?? []; }
  canLoadMore(chatId) { return this.hasMore.get(chatId) !== false; }

  async loadLatest(chatId) {
    const list = await api.messages(chatId, null, 50);
    if (!this.hasMore.has(chatId)) this.hasMore.set(chatId, list.length >= 50);
    await this.merge(chatId, await Promise.all(list.map((m) => this.decrypt(m))));
    this.loadReadState(chatId);
  }

  async loadOlder(chatId) {
    if (!this.canLoadMore(chatId)) return;
    const first = this.messagesOf(chatId).find((m) => m.status === 'sent');
    if (!first) return;
    const list = await api.messages(chatId, first.seq, 50);
    this.hasMore.set(chatId, list.length >= 50);
    await this.merge(chatId, await Promise.all(list.map((m) => this.decrypt(m))));
  }

  async loadReadState(chatId) {
    try {
      const l = await api.readState(chatId);
      this.readStates.set(chatId, new Map(l.map((r) => [r.userId, r.seq])));
      this.emit('read', chatId);
    } catch { /* */ }
  }

  merge(chatId, incoming) {
    const byId = new Map(this.messagesOf(chatId).map((m) => [m.id, m]));
    for (const m of incoming) {
      if (m.deleted) { byId.delete(m.id); continue; }
      if (m.clientId) {
        for (const [id, p] of byId) if (p.status !== 'sent' && p.clientId === m.clientId) { byId.delete(id); if (p.localUrl && !m.localUrl) m.localUrl = p.localUrl; }
      }
      const old = byId.get(m.id);
      byId.set(m.id, old?.localUrl && !m.localUrl ? { ...m, localUrl: old.localUrl } : m);
    }
    const list = [...byId.values()].sort((a, b) => (a.status === 'sent' ? 0 : 1) - (b.status === 'sent' ? 0 : 1) || a.seq - b.seq || a.createdAt - b.createdAt);
    this.messages.set(chatId, list);
    this.emit('messages', chatId);
  }

  async decrypt(m) {
    const key = cacheKey(m);
    let content = null;
    if (!m.deleted && m.payload) {
      if (this.decryptCache.has(key)) content = this.decryptCache.get(key);
      else {
        try {
          const obj = JSON.parse(m.payload);
          if (obj.v === 0) content = obj.plain ?? null;
          else content = JSON.parse(await E2E.decrypt(obj, this.myId, this.privateKey));
        } catch { content = null; }
        this.decryptCache.set(key, content);
      }
    }
    return {
      id: m.id, chatId: m.chatId, seq: m.seq, senderId: m.senderId, type: m.type,
      content: content ? normalizeContent(content) : null, decryptFailed: !m.deleted && !content,
      replyTo: m.replyTo ?? null, forwardedFrom: m.forwardedFrom ?? null, createdAt: m.createdAt, editedAt: m.editedAt ?? null,
      deleted: !!m.deleted, reactions: m.reactions ?? [], clientId: m.clientId ?? null,
      commentOf: m.commentOf ?? null, views: m.views ?? null, comments: m.comments ?? 0, status: 'sent',
    };
  }

  previewOf(m) {
    if (!m) return '';
    if (m.deleted) return 'Сообщение удалено';
    const key = cacheKey(m);
    if (!this.decryptCache.has(key)) return '…';
    const c = this.decryptCache.get(key);
    if (!c) return 'Зашифрованное сообщение';
    return previewText(m.type, normalizeContent(c));
  }

  isBotChat(chatId) {
    const c = this.chat(chatId);
    if (!c) return false;
    return (c.type === 'direct' || c.type === 'group') && c.members.some((m) => m.user.id !== this.myId && (this.users.get(m.user.id) ?? m.user).isBot);
  }

  recipientsOf(chatId) {
    const c = this.chat(chatId);
    const out = {};
    for (const m of c?.members ?? []) {
      const u = this.users.get(m.user.id) ?? m.user;
      if (u.publicKey) out[u.id] = E2E.unb64(u.publicKey);
    }
    if (this.privateKey) out[this.myId] = E2E.publicFromPrivate(this.privateKey);
    return out;
  }

  async encryptFor(chatId, content) {
    if (this.chat(chatId)?.type === 'channel' || this.isBotChat(chatId)) return JSON.stringify({ v: 0, plain: content });
    return E2E.encrypt(JSON.stringify(content), this.recipientsOf(chatId));
  }

  sendText(chatId, text, replyTo) {
    const t = text.trim();
    if (!t) return;
    this.sendContent(chatId, 'text', { text: t }, replyTo, null);
  }

  pending(chatId, clientId, type, content, replyTo, forwardedFrom, localUrl) {
    return {
      id: 'pending:' + clientId, chatId, seq: Number.MAX_SAFE_INTEGER, senderId: this.myId, type, content: normalizeContent(content),
      decryptFailed: false, replyTo, forwardedFrom, createdAt: Date.now(), editedAt: null, deleted: false, reactions: [],
      clientId, status: 'sending', localUrl: localUrl ?? null, comments: 0, views: null,
    };
  }

  sendContent(chatId, type, content, replyTo, forwardedFrom, pendingId) {
    const clientId = pendingId ?? uuid();
    if (!pendingId) this.merge(chatId, [this.pending(chatId, clientId, type, content, replyTo, forwardedFrom)]);
    (async () => {
      try {
        const sent = await this.postMessage(chatId, type, content, replyTo, forwardedFrom, clientId);
        this.decryptCache.set(`${sent.id}:0`, content);
        this.merge(chatId, [await this.decrypt(sent)]);
        this.bumpChat(sent);
      } catch (e) {
        this.markFailed(chatId, clientId);
        if (e instanceof ApiError) this.emit('toast', e.message || 'Не удалось отправить');
      }
    })();
  }

  async postMessage(chatId, type, content, replyTo, forwardedFrom, clientId) {
    const body = { type, payload: await this.encryptFor(chatId, content), clientId };
    if (replyTo) body.replyTo = replyTo;
    if (forwardedFrom) body.forwardedFrom = forwardedFrom;
    return api.send(chatId, body);
  }

  markFailed(chatId, clientId) {
    this.messages.set(chatId, this.messagesOf(chatId).map((m) => m.clientId === clientId && m.status !== 'sent' ? { ...m, status: 'failed', progress: null } : m));
    this.emit('messages', chatId);
  }

  setProgress(chatId, clientId, p) {
    this.messages.set(chatId, this.messagesOf(chatId).map((m) => m.clientId === clientId && m.status === 'sending' ? { ...m, progress: p } : m));
    this.emit('progress', chatId, clientId, p);
  }

  retry(msg) {
    this.messages.set(msg.chatId, this.messagesOf(msg.chatId).map((m) => m.id === msg.id ? { ...m, status: 'sending' } : m));
    this.emit('messages', msg.chatId);
    if (msg.localBlob && !msg.content?.file?.id) this.uploadAndSend(msg.chatId, msg.type, msg.localBlob, msg.content, msg.replyTo, msg.clientId, msg.localUrl);
    else this.sendContent(msg.chatId, msg.type, msg.content, msg.replyTo, msg.forwardedFrom, msg.clientId);
  }

  discard(msg) {
    this.messages.set(msg.chatId, this.messagesOf(msg.chatId).filter((m) => m.id !== msg.id));
    this.emit('messages', msg.chatId);
  }

  /** Фото, видео или любой файл: шифруется в браузере ещё до загрузки. */
  async sendFile(chatId, file, caption = '', asType = null, replyTo = null, extra = {}) {
    const mime = file.type || 'application/octet-stream';
    const type = asType ?? (mime.startsWith('image/') && !mime.includes('svg') ? 'image' : mime.startsWith('video/') ? 'video' : 'file');
    const clientId = uuid();
    const localUrl = URL.createObjectURL(file);
    let width = extra.width ?? 0, height = extra.height ?? 0, durationMs = extra.durationMs ?? 0;
    try {
      if (type === 'image' && !width) ({ width, height } = await imageSize(localUrl));
      else if ((type === 'video' || type === 'square') && !width) ({ width, height, durationMs } = await videoMeta(localUrl));
      else if (type === 'voice' && !durationMs) ({ durationMs } = await audioMeta(localUrl));
    } catch { /* без размеров тоже покажется */ }
    const content = {
      text: caption.trim(),
      file: {
        id: '', key: '', name: file.name || `${type}-${Date.now()}`, size: file.size, mime, width, height, durationMs,
        waveform: extra.waveform ?? '', square: type === 'square',
      },
    };
    const p = this.pending(chatId, clientId, type, content, replyTo, null, localUrl);
    p.localBlob = file;
    this.merge(chatId, [p]);
    this.uploadAndSend(chatId, type, file, content, replyTo, clientId, localUrl);
  }

  async uploadAndSend(chatId, type, blob, content, replyTo, clientId, localUrl) {
    try {
      this.setProgress(chatId, clientId, 0.02);
      const plain = new Uint8Array(await blob.arrayBuffer());
      const enc = await E2E.encryptFile(plain);
      this.setProgress(chatId, clientId, 0.1);
      const up = await api.upload(new Blob([enc.data], { type: 'application/octet-stream' }), 'application/octet-stream', (p) => this.setProgress(chatId, clientId, 0.1 + 0.9 * p));
      if (localUrl) this.media.set(up.id, { state: 'ready', url: localUrl });
      const finalContent = { ...content, file: { ...content.file, id: up.id, key: enc.key } };
      const sent = await this.postMessage(chatId, type, finalContent, replyTo, null, clientId);
      this.decryptCache.set(`${sent.id}:0`, finalContent);
      const ui = await this.decrypt(sent);
      ui.localUrl = localUrl;
      this.merge(chatId, [ui]);
      this.bumpChat(sent);
    } catch (e) {
      this.markFailed(chatId, clientId);
      this.emit('toast', errorText(e));
    }
  }

  async editMessage(msg, text) {
    const c = { ...msg.content, text: text.trim() };
    const updated = await api.edit(msg.id, await this.encryptFor(msg.chatId, c));
    this.decryptCache.set(`${updated.id}:${updated.editedAt ?? 0}`, c);
    this.merge(msg.chatId, [await this.decrypt(updated)]);
  }

  async deleteMessage(msg) {
    if (msg.status !== 'sent') return this.discard(msg);
    this.merge(msg.chatId, [await this.decrypt(await api.deleteMessage(msg.id))]);
    if (this.chat(msg.chatId)?.lastMessage?.id === msg.id) this.loadChat(msg.chatId).catch(() => {});
  }

  async react(msg, emoji) {
    const mine = msg.reactions.find((r) => r.userId === this.myId)?.emoji;
    const updated = await api.react(msg.id, mine === emoji ? null : emoji);
    this.merge(msg.chatId, [await this.decrypt(updated)]);
  }

  forward(msg, toChatId) {
    if (!msg.content) return;
    const { text, file, sticker, gift, call } = msg.content;
    const c = { text };
    if (file) c.file = file;
    if (sticker) c.sticker = sticker;
    if (gift) c.gift = gift;
    if (call) c.call = call;
    this.sendContent(toChatId, msg.type, c, null, msg.forwardedFrom ?? msg.senderId);
  }

  markRead(chatId) {
    const last = [...this.messagesOf(chatId)].reverse().find((m) => m.status === 'sent');
    const c = this.chat(chatId);
    if (!last || !c) return;
    if (c.lastReadSeq >= last.seq && c.unread === 0) return;
    this.chats = this.chats.map((x) => x.id === chatId ? { ...x, unread: 0, lastReadSeq: last.seq } : x);
    this.emit('chats');
    api.markRead(chatId, last.seq).catch(() => {});
  }

  bumpChat(m) {
    const c = this.chat(m.chatId);
    if (!c) { this.loadChat(m.chatId).catch(() => {}); return; }
    const mine = m.senderId === this.myId;
    const unread = mine || (this.openChatId === m.chatId && document.visibilityState === 'visible') ? 0 : c.unread + 1;
    this.upsertChat({ ...c, lastMessage: m, unread, lastReadSeq: mine ? m.seq : c.lastReadSeq });
  }

  // ================= Комментарии и просмотры =================

  async loadComments(postId) {
    const page = await api.comments(postId);
    this.rememberUsers(page.users ?? []);
    return { post: await this.decrypt(page.post), groupId: page.groupId, comments: await Promise.all((page.comments ?? []).map((m) => this.decrypt(m))) };
  }

  async sendComment(postId, text, replyTo) {
    const payload = JSON.stringify({ v: 0, plain: { text: text.trim() } });
    const body = { type: 'text', payload, clientId: uuid() };
    if (replyTo) body.replyTo = replyTo;
    return this.decrypt(await api.sendComment(postId, body));
  }

  markViewed(chatId, id) {
    if (this.viewed.has(id)) return;
    this.viewed.add(id);
    if (!this.pendingViews.has(chatId)) this.pendingViews.set(chatId, new Set());
    this.pendingViews.get(chatId).add(id);
    if (this.viewsTimer) return;
    this.viewsTimer = setTimeout(async () => {
      this.viewsTimer = null;
      for (const [cid, set] of [...this.pendingViews]) {
        this.pendingViews.delete(cid);
        const ids = [...set];
        try {
          const r = await api.markViews(cid, ids);
          this.messages.set(cid, this.messagesOf(cid).map((m) => r.views?.[m.id] != null ? { ...m, views: r.views[m.id] } : m));
          this.emit('messages', cid);
        } catch { ids.forEach((x) => this.viewed.delete(x)); }
      }
    }, 1500);
  }

  // ================= Медиа =================

  mediaOf(fileId) { return this.media.get(fileId) ?? { state: 'idle' }; }

  /** Скачивает и расшифровывает файл; возвращает blob: URL. */
  download(ref) {
    if (!ref?.id) return Promise.reject(new Error('Нет файла'));
    const cur = this.media.get(ref.id);
    if (cur?.state === 'ready') return Promise.resolve(cur.url);
    if (cur?.promise) return cur.promise;
    const entry = { state: 'loading', progress: 0 };
    entry.promise = (async () => {
      try {
        const data = await api.download(ref.id, (p) => { entry.progress = p * 0.9; this.emit('media', ref.id); });
        const plain = await E2E.decryptFile(data, ref.key);
        const url = URL.createObjectURL(new Blob([plain], { type: ref.mime || 'application/octet-stream' }));
        this.media.set(ref.id, { state: 'ready', url });
        this.emit('media', ref.id);
        return url;
      } catch (e) {
        this.media.set(ref.id, { state: 'error', error: errorText(e) });
        this.emit('media', ref.id);
        throw e;
      }
    })();
    this.media.set(ref.id, entry);
    this.emit('media', ref.id);
    return entry.promise;
  }

  /** Открытый файл (музыка профиля): скачиваем без расшифровки. */
  async downloadPlain(fileId, mime) {
    const cur = this.media.get('plain:' + fileId);
    if (cur?.url) return cur.url;
    const data = await api.download(fileId);
    const url = URL.createObjectURL(new Blob([data], { type: mime || 'application/octet-stream' }));
    this.media.set('plain:' + fileId, { state: 'ready', url });
    return url;
  }

  // ================= Звонки =================

  sendCallSignal(to, data) {
    if (this.ws?.readyState !== 1) return false;
    this.ws.send(JSON.stringify({ type: 'call.signal', to, data }));
    return true;
  }

  logCall(peerId, call) {
    (async () => {
      const c = this.chats.find((x) => x.type === 'direct' && x.members.some((m) => m.user.id === peerId)) ?? await this.openDirect(peerId);
      this.sendContent(c.id, 'call', { text: '', call }, null, null);
    })().catch(() => {});
  }

  // ================= Реальное время =================

  sendTyping(chatId) {
    if (this.ws?.readyState === 1) this.ws.send(JSON.stringify({ type: 'typing', chatId }));
  }

  setActive(active) {
    if (this.ws?.readyState === 1) this.ws.send(JSON.stringify({ type: 'presence', active }));
  }

  closeSocket() {
    this.socketGen = (this.socketGen ?? 0) + 1;
    clearTimeout(this.reconnectTimer);
    try { this.ws?.close(1000); } catch { /* */ }
    this.ws = null;
    this.setConnected(false);
  }

  setConnected(v) {
    if (this.connected === v) return;
    this.connected = v;
    this.emit('connected');
  }

  connectSocket() {
    this.closeSocket();
    if (!api.wsUrl || !api.token) return;
    const gen = this.socketGen;
    let backoff = 1000;
    const open = () => {
      if (gen !== this.socketGen || this.auth !== 'in') return;
      let ws;
      try {
        ws = new WebSocket(`${api.wsUrl}?token=${encodeURIComponent(api.token)}&active=${document.visibilityState === 'visible' ? 1 : 0}`);
      } catch { return; }
      this.ws = ws;
      ws.onopen = () => {
        backoff = 1000;
        this.setConnected(true);
        this.resync();
      };
      ws.onmessage = (e) => {
        let ev;
        try { ev = JSON.parse(e.data); } catch { return; }
        this.handle(ev).catch((err) => console.error(err));
      };
      ws.onclose = (e) => {
        if (this.ws === ws) this.ws = null;
        this.setConnected(false);
        if (e.code === 4001) { this.resetLocal(); return; }
        if (gen !== this.socketGen) return;
        this.reconnectTimer = setTimeout(open, backoff);
        backoff = Math.min(backoff * 2, 30000);
      };
    };
    open();
  }

  async resync() {
    await this.refreshChats();
    for (const id of this.messages.keys()) this.loadLatest(id).catch(() => {});
  }

  /** Без WebSocket (туннель его не пропустил) обновляемся опросом. */
  poll() {
    if (this.auth !== 'in' || this.connected || document.visibilityState !== 'visible') return;
    this.refreshChats();
    if (this.openChatId) this.loadLatest(this.openChatId).catch(() => {});
  }

  tickTyping() {
    const now = Date.now();
    let changed = false;
    for (const [chatId, m] of this.typing) {
      for (const [u, until] of m) if (until < now) { m.delete(u); changed = true; }
      if (!m.size) this.typing.delete(chatId);
    }
    if (changed) this.emit('typing');
  }

  async handle(ev) {
    switch (ev.type) {
      case 'comment.new': {
        if (ev.user) this.rememberUsers([ev.user]);
        if (ev.chatId && ev.messageId) {
          this.messages.set(ev.chatId, this.messagesOf(ev.chatId).map((m) => m.id === ev.messageId ? { ...m, comments: ev.count ?? m.comments } : m));
          this.emit('messages', ev.chatId);
        }
        this.emit('comment', ev);
        break;
      }
      case 'message.new': {
        const m = ev.message;
        if (!m) return;
        if (!this.users.has(m.senderId)) this.loadUser(m.senderId).catch(() => {});
        const ui = await this.decrypt(m);
        if (this.messages.has(m.chatId)) this.merge(m.chatId, [ui]);
        this.bumpChat(m);
        if (m.senderId !== this.myId) {
          this.typing.get(m.chatId)?.delete(m.senderId);
          this.emit('typing');
          const c = this.chat(m.chatId);
          if (c) this.emit('incoming', c, ui);
        }
        break;
      }
      case 'message.updated': {
        const m = ev.message;
        if (!m) return;
        if (this.messages.has(m.chatId)) this.merge(m.chatId, [await this.decrypt(m)]);
        const c = this.chat(m.chatId);
        if (c?.lastMessage?.id === m.id) {
          if (m.deleted) this.loadChat(m.chatId).catch(() => {}); else this.upsertChat({ ...c, lastMessage: m });
        }
        break;
      }
      case 'chat.new':
        if (ev.chat) {
          this.upsertChat(ev.chat);
          if (ev.by && ev.by !== this.myId && (ev.chat.type === 'group' || ev.chat.type === 'channel')) {
            const who = this.users.get(ev.by);
            this.emit('notice', `${who?.displayName ?? 'Вас'} ${who ? 'добавил(а) вас в' : 'добавили в'} «${ev.chat.title}»`);
          }
        }
        break;
      case 'chat.updated':
        if (ev.chatId) this.loadChat(ev.chatId).catch(() => {});
        break;
      case 'chat.removed':
        if (ev.chatId) this.dropChat(ev.chatId);
        break;
      case 'reaction': {
        if (!ev.userId || ev.userId === this.myId) return;
        const who = this.users.get(ev.userId) ?? await this.loadUser(ev.userId).catch(() => null);
        const c = this.chat(ev.chatId);
        if (c) this.emit('reaction', c, who, ev.emoji, ev.messageId);
        break;
      }
      case 'typing': {
        if (!ev.chatId || !ev.userId) return;
        if (!this.typing.has(ev.chatId)) this.typing.set(ev.chatId, new Map());
        this.typing.get(ev.chatId).set(ev.userId, Date.now() + 5000);
        this.emit('typing');
        break;
      }
      case 'read': {
        if (!ev.chatId || !ev.userId) return;
        if (!this.readStates.has(ev.chatId)) this.readStates.set(ev.chatId, new Map());
        this.readStates.get(ev.chatId).set(ev.userId, ev.seq ?? 0);
        if (ev.userId !== this.myId) {
          this.chats = this.chats.map((c) => c.id === ev.chatId ? { ...c, othersReadSeq: Math.max(c.othersReadSeq ?? 0, ev.seq ?? 0) } : c);
          this.emit('chats');
        }
        this.emit('read', ev.chatId);
        break;
      }
      case 'presence': {
        const u = this.users.get(ev.userId);
        if (u) { this.users.set(u.id, { ...u, online: ev.online === true, lastSeen: ev.lastSeen ?? u.lastSeen }); this.emit('users'); }
        break;
      }
      case 'user.updated':
        if (ev.user) {
          if (ev.user.id === this.myId) this.refreshMe().catch(() => {}); else this.rememberUsers([ev.user]);
        }
        break;
      case 'call.signal':
        if (ev.from && ev.data) this.emit('call.signal', ev.from, ev.data);
        break;
      default:
    }
  }
}

function cacheKey(m) { return `${m.id}:${m.editedAt ?? 0}`; }

function sortChats(list) {
  return list.sort((a, b) => (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0) || (b.lastMessage?.createdAt ?? b.createdAt) - (a.lastMessage?.createdAt ?? a.createdAt));
}

function normalizeContent(c) {
  return { text: c.text ?? '', file: c.file ?? null, sticker: c.sticker ?? null, gift: c.gift ?? null, call: c.call ?? null };
}

export function callText(c) {
  const kind = c?.video ? 'Видеозвонок' : 'Звонок';
  switch (c?.status) {
    case 'ok': return `${kind} · ${callDuration(c.duration ?? 0)}`;
    case 'missed': return `Пропущенный ${kind.toLowerCase()}`;
    case 'declined': return `${kind} отклонён`;
    case 'busy': return `${kind}: занято`;
    case 'forbidden': return `${kind}: звонки ограничены`;
    case 'cancelled': return `Отменённый ${kind.toLowerCase()}`;
    default: return `${kind} не состоялся`;
  }
}

export function callDuration(ms) {
  const s = Math.floor(ms / 1000);
  const p = (n) => String(n).padStart(2, '0');
  return s >= 3600 ? `${Math.floor(s / 3600)}:${p(Math.floor(s / 60) % 60)}:${p(s % 60)}` : `${Math.floor(s / 60)}:${p(s % 60)}`;
}

export function previewText(type, c) {
  const cap = c.text?.trim() ? ` · ${c.text}` : '';
  switch (type) {
    case 'image': return 'Фото' + cap;
    case 'video': return c.file?.square ? 'Видеосообщение' : 'Видео' + cap;
    case 'file': return c.file?.name ?? 'Файл';
    case 'voice': return 'Голосовое сообщение';
    case 'square': return 'Видеосообщение';
    case 'sticker': return 'Стикер';
    case 'gift': return 'Подарок' + (c.gift?.title ? ` «${c.gift.title}»` : '');
    case 'call': return callText(c.call);
    default: return c.text;
  }
}

function imageSize(url) {
  return new Promise((res, rej) => { const i = new Image(); i.onload = () => res({ width: i.naturalWidth, height: i.naturalHeight }); i.onerror = rej; i.src = url; });
}
function videoMeta(url) {
  return new Promise((res, rej) => {
    const v = document.createElement('video');
    v.preload = 'metadata';
    v.onloadedmetadata = () => res({ width: v.videoWidth, height: v.videoHeight, durationMs: Math.round((v.duration || 0) * 1000) });
    v.onerror = rej;
    v.src = url;
  });
}
function audioMeta(url) {
  return new Promise((res, rej) => {
    const a = document.createElement('audio');
    a.preload = 'metadata';
    a.onloadedmetadata = () => res({ durationMs: Number.isFinite(a.duration) ? Math.round(a.duration * 1000) : 0 });
    a.onerror = rej;
    a.src = url;
  });
}

export const store = new Store();
