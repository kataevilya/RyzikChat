// Клиент API сервера RyzikChat (server/openapi.json).
// Запросы идут через сайт (/rc → сервер, см. vercel.json), поэтому браузеру не мешают CORS
// и страница-предупреждение туннеля. WebSocket подключается к серверу напрямую.

export class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

function deviceId() {
  try {
    let id = localStorage.getItem('rc.device');
    if (!id) { id = 'web-' + crypto.randomUUID(); localStorage.setItem('rc.device', id); }
    return id;
  } catch { return 'web'; }
}

export const DEVICE_ID = deviceId();

export function deviceName() {
  const ua = navigator.userAgent;
  const browser = /Edg\//.test(ua) ? 'Edge' : /OPR\//.test(ua) ? 'Opera' : /YaBrowser/.test(ua) ? 'Яндекс Браузер'
    : /Firefox\//.test(ua) ? 'Firefox' : /Chrome\//.test(ua) ? 'Chrome' : /Safari\//.test(ua) ? 'Safari' : 'Браузер';
  const os = /Windows/.test(ua) ? 'Windows' : /Android/.test(ua) ? 'Android' : /iPhone|iPad/.test(ua) ? 'iOS'
    : /Mac OS/.test(ua) ? 'macOS' : /Linux/.test(ua) ? 'Linux' : '';
  return `Веб · ${browser}${os ? ' · ' + os : ''}`;
}

const q = encodeURIComponent;

export class Api {
  constructor() {
    this.base = '/rc';
    this.token = null;
    this.wsUrl = null;
  }

  /** Адрес сервера для WebSocket отдаёт функция сайта /api/config. */
  async loadConfig() {
    try {
      const r = await fetch('/api/config', { cache: 'no-store' });
      const c = await r.json();
      this.wsUrl = c.ws;
    } catch { /* без WebSocket будем обновляться опросом */ }
  }

  headers(json = true) {
    const h = { 'ngrok-skip-browser-warning': '1', 'X-Device-Id': DEVICE_ID };
    if (json) h['Content-Type'] = 'application/json';
    if (this.token) h.Authorization = `Bearer ${this.token}`;
    return h;
  }

  async raw(method, path, body, { retry = method === 'GET' } = {}) {
    let res;
    for (let attempt = 0; ; attempt++) {
      try {
        res = await fetch(this.base + path, {
          method,
          headers: this.headers(body !== undefined || !['GET', 'DELETE'].includes(method)),
          body: body !== undefined ? JSON.stringify(body) : ['GET', 'DELETE'].includes(method) ? undefined : '{}',
        });
      } catch {
        if (retry && attempt < 2) { await sleep(1000 * (attempt + 1)); continue; }
        throw new ApiError(0, 'network', 'Нет связи с сервером');
      }
      if (retry && attempt < 2 && res.status >= 502 && res.status <= 504) { await sleep(1000 * (attempt + 1)); continue; }
      break;
    }
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { /* не JSON */ }
    if (!res.ok) {
      const fallback = res.status === 404 && !data?.error ? 'Сервер сейчас выключен. Попробуйте чуть позже'
        : res.status >= 502 && res.status <= 504 ? 'Сервер перезапускается. Попробуйте через минуту'
          : res.status >= 500 ? `Ошибка на сервере (${res.status})` : `Ошибка сети (${res.status})`;
      throw new ApiError(res.status, data?.error || `http_${res.status}`, data?.message || fallback);
    }
    if (data === null && text) throw new ApiError(res.status, 'bad_response', 'Сервер ответил не так, как ожидалось');
    return data;
  }

  get(p) { return this.raw('GET', p); }
  post(p, b) { return this.raw('POST', p, b); }
  put(p, b) { return this.raw('PUT', p, b); }
  patch(p, b) { return this.raw('PATCH', p, b); }
  del(p, b) { return this.raw('DELETE', p, b); }

  // ---------- вход ----------
  health() { return this.get('/api/health').then(() => true, () => false); }
  register(o) { return this.post('/api/auth/register', o); }
  login(username, password, device) { return this.post('/api/auth/login', { username, password, device }); }
  login2fa(challengeId, password) { return this.post('/api/auth/login/2fa', { challengeId, password }); }
  logout() { return this.post('/api/auth/logout'); }
  sessions() { return this.get('/api/sessions'); }
  terminateOtherSessions() { return this.post('/api/sessions/terminate-others'); }
  changePassword(oldPassword, newPassword, encryptedPrivateKey) { return this.post('/api/me/password', { oldPassword, newPassword, encryptedPrivateKey }); }
  changeUsername(username, password, newPassword, encryptedPrivateKey) { return this.post('/api/me/username', { username, password, newPassword, encryptedPrivateKey }); }
  deleteAccount(password) { return this.post('/api/me/delete', { password }); }
  set2fa(accountPassword, currentPassword, password, hint) {
    const b = { accountPassword, password, hint };
    if (currentPassword != null) b.currentPassword = currentPassword;
    return this.put('/api/me/2fa', b);
  }
  disable2fa(password) { return this.del('/api/me/2fa', { password }); }

  // ---------- пользователи ----------
  me() { return this.get('/api/me'); }
  updateMe(fields) { return this.patch('/api/me', fields); }
  searchUsers(s) { return this.get('/api/users/search?q=' + q(s)); }
  user(id) { return this.get('/api/users/' + id); }
  contacts() { return this.get('/api/contacts'); }
  setContact(id, on) { return on ? this.put('/api/contacts/' + id) : this.del('/api/contacts/' + id); }
  blocks() { return this.get('/api/blocks'); }
  setBlocked(id, on) { return on ? this.put('/api/blocks/' + id) : this.del('/api/blocks/' + id); }

  // ---------- чаты ----------
  chats() { return this.get('/api/chats'); }
  chat(id) { return this.get('/api/chats/' + id); }
  openDirect(userId) { return this.post('/api/chats/direct', { userId }); }
  createGroup(o) { return this.post('/api/chats/group', o); }
  createChannel(o) { return this.post('/api/chats/channel', o); }
  updateChat(id, fields) { return this.patch('/api/chats/' + id, fields); }
  chatSettings(id, fields) { return this.patch(`/api/chats/${id}/settings`, fields); }
  deleteChat(id, forAll = true) { return this.del(`/api/chats/${id}?forAll=${forAll}`); }
  addMembers(id, userIds) { return this.post(`/api/chats/${id}/members`, { userIds }); }
  removeMember(id, userId) { return this.del(`/api/chats/${id}/members/${userId}`); }
  setRole(id, userId, admin) { return this.put(`/api/chats/${id}/members/${userId}/role`, { role: admin ? 'admin' : 'subscriber' }); }
  resetInvite(id) { return this.post(`/api/chats/${id}/invite/reset`); }
  invitePreview(code) { return this.get('/api/invite/' + q(code)); }
  joinInvite(code) { return this.post(`/api/invite/${q(code)}/join`); }
  chatByUsername(name) { return this.get('/api/chats/by-username/' + q(name.replace(/^@/, ''))); }
  searchChannels(s) { return this.get('/api/channels/search?q=' + q(s)); }
  subscribe(id) { return this.post(`/api/chats/${id}/subscribe`); }
  markRead(id, seq) { return this.post(`/api/chats/${id}/read`, { seq }); }
  readState(id) { return this.get(`/api/chats/${id}/read-state`); }
  pin(id, messageId) { return this.put(`/api/chats/${id}/pin`, { messageId }); }
  setWallpaper(id, fileId) { return this.put(`/api/chats/${id}/wallpaper`, { fileId }); }
  setDiscussion(id, groupId) { return this.put(`/api/chats/${id}/discussion`, { groupId }); }
  checkChatUsername(name, chatId) { return this.get('/api/chat-username-check?username=' + q(name) + (chatId ? '&chatId=' + chatId : '')); }
  addBot(id, botId) { return this.post(`/api/chats/${id}/bots`, { botId }); }

  // ---------- сообщения ----------
  messages(id, before, limit = 50) { return this.get(`/api/chats/${id}/messages?limit=${limit}` + (before ? '&before=' + before : '')); }
  send(id, o) { return this.post(`/api/chats/${id}/messages`, o); }
  edit(id, payload) { return this.patch('/api/messages/' + id, { payload }); }
  deleteMessage(id) { return this.del('/api/messages/' + id); }
  react(id, emoji) { return this.put(`/api/messages/${id}/reaction`, { emoji: emoji ?? '' }); }
  readers(id) { return this.get(`/api/messages/${id}/readers`); }
  comments(postId) { return this.get(`/api/messages/${postId}/comments`); }
  sendComment(postId, o) { return this.post(`/api/messages/${postId}/comments`, o); }
  markViews(chatId, ids) { return this.post(`/api/chats/${chatId}/views`, { ids }); }

  // ---------- стикеры ----------
  stickerPacks() { return this.get('/api/stickers'); }
  stickerPack(id) { return this.get('/api/stickers/packs/' + id); }
  setPackAdded(id, on) { return on ? this.put(`/api/stickers/packs/${id}/added`) : this.del(`/api/stickers/packs/${id}/added`); }

  // ---------- FLUX, премиум, подарки ----------
  flux() { return this.get('/api/flux'); }
  buyPremium(months) { return this.post('/api/premium/buy', { months }); }
  giftShop() { return this.get('/api/gifts/shop'); }
  buyGift(itemId, toUserId, message) { return this.post('/api/gifts/buy', { itemId, toUserId, message }); }
  userGifts(id) { return this.get(`/api/users/${id}/gifts`); }
  badges() { return this.get('/api/badges'); }
  callsConfig() { return this.get('/api/calls/config'); }

  // ---------- файлы ----------
  /** Загружает Blob. onProgress(0..1). */
  upload(blob, mime, onProgress) {
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open('POST', this.base + '/api/files');
      const h = this.headers(false);
      for (const k in h) xhr.setRequestHeader(k, h[k]);
      xhr.upload.onprogress = (e) => e.lengthComputable && onProgress?.(e.loaded / e.total);
      xhr.onload = () => {
        let data = null;
        try { data = JSON.parse(xhr.responseText); } catch { /* */ }
        if (xhr.status >= 200 && xhr.status < 300 && data) resolve(data);
        else reject(new ApiError(xhr.status, data?.error || 'upload_failed', data?.message || (xhr.status === 413 ? 'Файл слишком большой' : 'Не удалось загрузить файл')));
      };
      xhr.onerror = () => reject(new ApiError(0, 'network', 'Нет связи с сервером'));
      const fd = new FormData();
      fd.append('mime', mime);
      fd.append('file', blob, 'blob');
      xhr.send(fd);
    });
  }

  async download(fileId, onProgress) {
    const res = await fetch(`${this.base}/api/files/${fileId}`, { headers: this.headers(false) });
    if (!res.ok) throw new ApiError(res.status, 'download_failed', 'Не удалось скачать файл');
    const total = Number(res.headers.get('content-length')) || 0;
    if (!res.body || !total || !onProgress) return new Uint8Array(await res.arrayBuffer());
    const reader = res.body.getReader();
    const out = new Uint8Array(total);
    let got = 0;
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      if (got + value.length > out.length) return concatChunks(out.subarray(0, got), value, reader);
      out.set(value, got);
      got += value.length;
      onProgress(got / total);
    }
    return out.subarray(0, got);
  }

  avatarUrl(fileId) { return fileId ? `${this.base}/api/avatars/${fileId}` : null; }
}

async function concatChunks(head, value, reader) {
  const parts = [head, value];
  for (;;) {
    const { done, value: v } = await reader.read();
    if (done) break;
    parts.push(v);
  }
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0));
  let o = 0;
  for (const p of parts) { out.set(p, o); o += p.length; }
  return out;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
