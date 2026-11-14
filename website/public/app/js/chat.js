// Экран чата — как ChatScreen.kt и MessageBubble.kt.
import { store, api, errorText, previewText, callText, QUICK_REACTIONS } from './store.js';
import {
  h, icon, iconBtn, clear, patchChildren, chatAvatar, avatar, nameLine, formatTime, formatDay, sameDay, formatLastSeen, formatSize,
  formatDuration, members, richText, isEmojiOnly, menu, dialog, confirm, toast, sheet, pickFile, copyText, settings, emptyState, spinner, field,
} from './ui.js';
import { go, back } from './nav.js';
import { dangerItems, parseInvite } from './chats.js';
import { startCall } from './calls.js';
import { EMOJI } from './emoji.js';

export function chatScreen(chatId, focusMessageId) {
  const offs = [];
  let chat = store.chat(chatId);
  let replyTo = null;
  let editing = null;
  let panel = null; // 'emoji' | 'stickers'
  let loadingOlder = false;
  let atBottom = true;
  let newBelow = 0;
  let lastTyping = 0;
  let recorder = null;
  const nodes = new Map(); // clientId или id -> { sig, el }
  const daySeps = new Map(); // подпись дня -> элемент
  let headerSig = null, headerAv = null, pinnedSig = null, contactSig = null;
  let loader = null, emptyEl = null, emptyKey = null;
  const mediaUpdaters = new Map(); // fileId -> Set<fn>

  store.openChatId = chatId;

  const bg = h('div.chat-bg');
  const header = h('div');
  const pinnedBar = h('div');
  const contactBar = h('div');
  const inner = h('div.messages-inner');
  const scroller = h('div.messages', null, inner);
  const downBtn = h('button.scroll-down.hide', { title: 'Вниз', onclick: () => scrollToBottom(true) }, icon('keyboard_arrow_down'));
  const bottom = h('div.composer-wrap');
  const dropZone = h('div.drop-zone', null, 'Отпустите, чтобы отправить');
  const el = h('div.chat-screen.screen', null, bg, header, contactBar, pinnedBar, scroller, downBtn, bottom, dropZone);

  // ---------- Шапка ----------

  function subtitle() {
    const t = store.typing.get(chatId);
    if (t?.size) {
      const who = chat?.type === 'group' ? (store.user([...t.keys()][0])?.displayName ?? '') + ' ' : '';
      return h('span.s.accent', null, `${who}печатает`, h('span.typing-dots', null, h('i'), h('i'), h('i')));
    }
    const peer = store.peerOf(chat);
    let text = '';
    if (chat?.isService) text = 'официальный чат';
    else if (chat?.type === 'direct' && peer?.isBot) text = 'бот';
    else if (chat?.type === 'saved') text = 'только для вас';
    else if (chat?.type === 'group') text = `участников: ${chat.members?.length || chat.memberCount}`;
    else if (chat?.type === 'channel') text = members(chat.memberCount, true);
    else if (peer) text = formatLastSeen(peer.online, peer.lastSeen);
    return h('span.s' + (peer?.online && !peer?.isBot ? '.accent' : ''), null, text);
  }

  function renderHeader() {
    if (!chat) { clear(header).append(h('header.top-bar', null, iconBtn('arrow_back', 'Назад', () => back()), h('div.top-title', null, h('div.t', null, 'Загрузка…')))); return; }
    const peer = store.peerOf(chat);
    const sub = subtitle();
    // Шапка меняется редко, а события «печатает»/«в сети» приходят постоянно — без проверки она мигала.
    const sig = JSON.stringify([store.chatTitle(chat), chat.type, chat.verified, chat.isService, chat.peerBlocked, chat.avatarFileId,
      peer && [peer.id, peer.verified, peer.isPremium, peer.emojiStatus, peer.isBot, peer.avatarFileId, peer.online], sub.className, sub.textContent]);
    if (sig === headerSig) return;
    headerSig = sig;
    const title = chat.type === 'direct'
      ? nameLine(store.chatTitle(chat), { verified: peer?.verified, premium: peer?.isPremium, emoji: peer?.emojiStatus, bot: peer?.isBot })
      : nameLine(store.chatTitle(chat), { verified: chat.verified || chat.isService });
    const avKey = JSON.stringify([chat.type, chat.avatarFileId, chat.title, peer?.displayName, peer?.avatarFileId, peer?.online, peer?.isBot]);
    if (headerAv?.key !== avKey) headerAv = { key: avKey, el: chatAvatar(store, chat, 42) };
    const canCall = chat.type === 'direct' && peer && !peer.isBot && !chat.isService && !chat.peerBlocked;
    const openInfo = () => chat.type === 'saved' ? null : chat.type === 'direct' ? go('/u/' + peer?.id) : go(`/c/${chatId}/info`);
    clear(header).append(h('header.top-bar', null,
      iconBtn('arrow_back', 'Назад', () => back()),
      h('div', { style: { cursor: 'pointer' }, onclick: openInfo }, headerAv.el),
      h('div.top-title.clickable', { onclick: openInfo }, h('div.t', null, title), sub),
      h('div.top-actions', null,
        canCall ? iconBtn('call', 'Звонок', () => startCall(peer.id, false), 'primary') : null,
        iconBtn('more_vert', 'Ещё', (e) => chatMenu(e))),
    ));
  }

  function chatMenu(e) {
    const peer = store.peerOf(chat);
    const isOwnerish = chat.myRole === 'owner' || chat.myRole === 'admin';
    const items = [
      chat.type !== 'saved' ? { icon: chat.type === 'direct' ? 'person' : 'info', label: chat.type === 'direct' ? 'Профиль' : 'Информация', run: () => chat.type === 'direct' ? go('/u/' + peer.id) : go(`/c/${chatId}/info`) } : null,
      chat.type === 'direct' && peer && !peer.isBot && !chat.isService && !chat.peerBlocked ? { icon: 'videocam', label: 'Видеозвонок', run: () => startCall(peer.id, true) } : null,
      (chat.type !== 'channel' || isOwnerish) ? { icon: 'wallpaper', label: 'Обои чата', run: () => pickWallpaper() } : null,
      chat.wallpaperFileId ? { icon: 'hide_image', label: 'Убрать обои', run: () => store.setWallpaper(chatId, null).catch((x) => toast(errorText(x))) } : null,
      chat.myRole || chat.type === 'direct' || chat.type === 'saved' ? { icon: chat.muted ? 'volume_up' : 'volume_off', label: chat.muted ? 'Включить звук' : 'Без звука', run: () => store.chatSettings(chatId, { muted: !chat.muted }).catch((x) => toast(errorText(x))) } : null,
      chat.myRole || chat.type === 'direct' || chat.type === 'saved' ? { icon: chat.pinned ? 'keep_off' : 'keep', label: chat.pinned ? 'Открепить чат' : 'Закрепить чат', run: () => store.chatSettings(chatId, { pinned: !chat.pinned }).catch((x) => toast(errorText(x))) } : null,
      chat.type === 'direct' && peer && !chat.isService ? { icon: 'block', label: chat.peerBlocked ? 'Разблокировать' : 'Заблокировать', danger: !chat.peerBlocked, run: () => store.setBlocked(peer.id, !chat.peerBlocked).catch((x) => toast(errorText(x))) } : null,
      '-',
      ...dangerItems(chat),
    ];
    menu(e, items);
  }

  async function pickWallpaper() {
    const [f] = await pickFile('image/*');
    if (!f) return;
    try {
      const { scaleImage } = await import('./ui.js');
      await store.setWallpaper(chatId, await scaleImage(f, 1600, 'image/jpeg'));
      toast('Обои установлены');
    } catch (x) { toast(errorText(x)); }
  }

  function renderBg() {
    if (chat?.wallpaperFileId) {
      bg.classList.add('photo');
      bg.style.backgroundImage = `url("${api.avatarUrl(chat.wallpaperFileId)}")`;
    } else {
      bg.classList.remove('photo');
      bg.style.backgroundImage = '';
    }
  }

  function renderContactBar() {
    const peer = store.peerOf(chat);
    const hidden = (() => { try { return localStorage.getItem('rc.cbar.' + chatId) === '1'; } catch { return false; } })();
    const show = !(chat?.type !== 'direct' || !peer || peer.isService || chat.isService || peer.isBot || chat.peerIsContact || chat.peerBlocked || hidden);
    const sig = show ? peer.id : '';
    if (sig === contactSig) return;
    contactSig = sig;
    clear(contactBar);
    if (!show) return;
    contactBar.append(h('div.compose-ctx', { style: { margin: '8px 12px 0', borderRadius: '26px' } },
      h('button.text-btn.grow', { onclick: () => store.setContact(peer.id, true).catch((x) => toast(errorText(x))) }, icon('person_add'), 'Добавить контакт'),
      h('button.text-btn.danger.grow', { onclick: () => store.setBlocked(peer.id, true).catch((x) => toast(errorText(x))) }, icon('block'), 'Заблокировать'),
      iconBtn('close', 'Скрыть', () => { try { localStorage.setItem('rc.cbar.' + chatId, '1'); } catch { /* */ } renderContactBar(); }),
    ));
  }

  function renderPinned() {
    const p = chat?.pinnedMessage;
    const ui = p && !p.deleted ? store.messagesOf(chatId).find((m) => m.id === p.id) : null;
    const text = !p || p.deleted ? '' : ui?.content ? previewText(ui.type, ui.content) : store.previewOf(p);
    const sig = !p || p.deleted ? '' : JSON.stringify([p.id, text, chat.canPin]);
    if (sig === pinnedSig) return;
    pinnedSig = sig;
    clear(pinnedBar);
    if (!sig) return;
    pinnedBar.append(h('div.pinned-bar', { onclick: () => scrollToMessage(p.id) },
      h('div.pb-line'), icon('keep', 'fill'),
      h('div.pb-text', null, h('div.pb-t', null, 'Закреплённое сообщение'), h('div.pb-s', null, text)),
      chat.canPin ? iconBtn('close', 'Открепить', () => store.pinMessage(chatId, null).catch((x) => toast(errorText(x)))) : null,
    ));
  }

  // ---------- Сообщения ----------

  function readSeqOthers() {
    const rs = store.readStates.get(chatId);
    let max = chat?.othersReadSeq ?? 0;
    if (rs) for (const [uid, seq] of rs) if (uid !== store.myId) max = Math.max(max, seq);
    return max;
  }

  function renderMessages(opts = {}) {
    const list = store.messagesOf(chatId);
    const keepBottom = atBottom || opts.toBottom;
    const prevHeight = scroller.scrollHeight;
    const prevTop = scroller.scrollTop;
    const othersRead = readSeqOthers();
    const byId = new Map(list.map((m) => [m.id, m]));
    const frag = [];
    const seen = new Set();
    const seenDays = new Set();
    if (!list.length) {
      const loaded = store.loadedChats?.has(chatId);
      const key = loaded ? 'empty' : 'loading';
      if (emptyKey !== key) {
        emptyKey = key;
        const saved = chat?.type === 'saved', channel = chat?.type === 'channel';
        emptyEl = loaded ? h('div', { style: { margin: 'auto 0' } }, emptyState(saved ? 'bookmark' : channel ? 'campaign' : 'waving_hand',
          saved ? 'Избранное' : channel ? 'В канале пока нет постов' : 'Пока тихо',
          saved ? 'Пересылайте сюда сообщения, фото и файлы, чтобы не потерять.' : channel ? 'Здесь появятся посты, голосовые и квадратики владельца канала.' : 'Напишите первым! Сообщения защищены сквозным шифрованием.'))
          : spinner();
      }
      frag.push(emptyEl);
    }
    if (list.length && store.canLoadMore(chatId)) frag.push(loader ??= h('div.spinner', { style: { padding: '8px' } }, h('i')));
    for (let i = 0; i < list.length; i++) {
      const m = list[i], prev = list[i - 1], next = list[i + 1];
      if (!prev || !sameDay(prev.createdAt, m.createdAt)) {
        const day = formatDay(m.createdAt);
        if (!daySeps.has(day)) daySeps.set(day, h('div.day-sep', null, day));
        seenDays.add(day);
        frag.push(daySeps.get(day));
      }
      const groupable = (a, b) => a && b && a.senderId === b.senderId && sameDay(a.createdAt, b.createdAt) && Math.abs(b.createdAt - a.createdAt) < 5 * 60000 && a.type !== 'call' && b.type !== 'call';
      const first = !groupable(prev, m);
      const last = !groupable(m, next);
      const read = m.senderId === store.myId && m.status === 'sent' && othersRead >= m.seq;
      const st = store.mediaOf(m.content?.file?.id ?? '');
      const sig = [m.id, m.editedAt, m.status, m.progress ? Math.round(m.progress * 50) : '', JSON.stringify(m.reactions), m.comments, m.views, first, last, read,
        m.decryptFailed, m.replyTo ? byId.get(m.replyTo)?.editedAt ?? 'x' : '', st.state === 'ready' ? 1 : 0, chat?.canPin, chat?.pinnedMessage?.id === m.id].join('|');
      // Своё сообщение сначала живёт с временным id, потом получает настоящий —
      // ключ по clientId, чтобы оно не «прилетало» второй раз.
      const key = m.clientId || m.id;
      let node = nodes.get(key);
      if (!node || node.sig !== sig) {
        const fresh = !node && opts.animateNew && m.createdAt > Date.now() - 10000;
        node = { sig, el: messageEl(m, { first, last, read, appear: fresh }) };
        nodes.set(key, node);
      }
      seen.add(key);
      frag.push(node.el);
      if (chat?.type === 'channel' && m.status === 'sent') store.markViewed(chatId, m.id);
    }
    for (const id of [...nodes.keys()]) if (!seen.has(id)) nodes.delete(id);
    for (const d of [...daySeps.keys()]) if (!seenDays.has(d)) daySeps.delete(d);
    patchChildren(inner, frag);
    if (keepBottom) stickBottom();
    else if (opts.keepOffset) scroller.scrollTop = prevTop + (scroller.scrollHeight - prevHeight);
    updateDown();
  }

  function stickBottom() {
    atBottom = true;
    scroller.scrollTop = scroller.scrollHeight;
  }

  function senderName(id) {
    if (id === store.myId) return store.me?.displayName ?? 'Вы';
    return store.user(id)?.displayName ?? (chat?.members?.find((x) => x.user.id === id)?.user.displayName) ?? '…';
  }

  function messageEl(m, { first, last, read, appear }) {
    const mine = m.senderId === store.myId;
    const c = m.content;
    const isChannel = chat?.type === 'channel';
    const showAvatar = chat?.type === 'group' && !mine;
    const sender = store.user(m.senderId);

    if (m.type === 'call' && c?.call) {
      const missed = ['missed', 'declined', 'busy', 'cancelled', 'failed', 'forbidden'].includes(c.call.status);
      const label = mine ? callText(c.call) : callText(c.call).replace('Отменённый', 'Пропущенный');
      return h('div.msg' + (mine ? '.mine' : '') + '.first', { 'data-id': m.id },
        h('div.bubble', null, h('div.b-call' + (missed ? '.missed' : ''), null,
          h('div.ci', null, icon(c.call.video ? 'videocam' : (mine ? 'call_made' : 'call_received'))),
          h('div.grow', null, h('div', { style: { fontWeight: 700 } }, label), h('div', { style: { fontSize: '12px', opacity: .7 } }, formatTime(m.createdAt))),
          store.peerOf(chat) && !chat.peerBlocked ? iconBtn(c.call.video ? 'videocam' : 'call', 'Позвонить', () => startCall(store.peerOf(chat).id, c.call.video), 'primary') : null)));
    }

    const isSquare = (m.type === 'square' || (m.type === 'video' && c?.file?.square)) && c?.file;
    const isMedia = (m.type === 'image' || m.type === 'video') && c?.file && !isSquare;
    const isSticker = m.type === 'sticker' && c?.sticker;
    const isGift = m.type === 'gift' && c?.gift;
    const emojiOnly = settings.bigEmoji && m.type === 'text' && c && isEmojiOnly(c.text) && !m.replyTo && !m.forwardedFrom;
    const bare = emojiOnly || isSquare || isSticker;

    const bubble = h('div.bubble' + (isMedia ? '.media' : '') + (bare ? '.bare' : '') + (m.decryptFailed ? '.decrypt-failed' : ''));
    if (first && !mine && chat?.type === 'group' && !bare) {
      bubble.append(h('div.b-name', { style: { color: nameColor(m.senderId) }, onclick: () => go('/u/' + m.senderId) }, senderName(m.senderId)));
    }
    if (m.forwardedFrom) {
      bubble.append(h('div.b-fwd', null, icon('forward'), 'Переслано от ', h('b', null, store.user(m.forwardedFrom)?.displayName ?? 'пользователя')));
      if (!store.user(m.forwardedFrom)) store.loadUser(m.forwardedFrom).then(() => scheduleRender()).catch(() => {});
    }
    if (m.replyTo) {
      const r = store.messagesOf(chatId).find((x) => x.id === m.replyTo);
      bubble.append(h('div.b-reply', { onclick: () => scrollToMessage(m.replyTo) },
        h('div.rn', null, r ? senderName(r.senderId) : 'Ответ'),
        h('div.rt', null, r?.deleted ? 'Сообщение удалено' : r?.content ? previewText(r.type, r.content) : 'Сообщение')));
    }

    const meta = h('span.b-meta' + ((isMedia && !c.text) || bare ? '.over' : ''), null,
      m.editedAt ? 'изм. ' : '', formatTime(m.createdAt),
      mine && chat?.type !== 'channel' && chat?.type !== 'saved' ? (m.status === 'sending' ? icon('schedule') : m.status === 'failed' ? null : icon(read ? 'done_all' : 'check', read ? 'read' : '')) : null);
    if (m.status === 'failed') {
      meta.classList.add('failed');
      meta.append(icon('error'), ' не отправлено');
      meta.onclick = (e) => { e.stopPropagation(); menu(e, [{ icon: 'refresh', label: 'Отправить ещё раз', run: () => store.retry(m) }, { icon: 'delete', label: 'Удалить', danger: true, run: () => store.discard(m) }]); };
    }

    if (m.decryptFailed) {
      bubble.append(h('div.b-text', null, icon('lock'), ' Не удалось расшифровать'), meta);
    } else if (isSticker) {
      const img = h('img', { src: api.avatarUrl(c.sticker.fileId), alt: c.sticker.emoji || 'Стикер', loading: 'lazy' });
      bubble.append(h('div.b-sticker', { onclick: () => openStickerPack(c.sticker.packId) }, img), meta);
    } else if (isGift) {
      bubble.classList.add('media');
      bubble.append(h('div.b-gift', null,
        c.gift.fileId ? h('img', { src: api.avatarUrl(c.gift.fileId), alt: '' }) : h('div', { style: { fontSize: '80px' } }, c.gift.emoji || '🎁'),
        h('div.gt', null, c.gift.title || 'Подарок'),
        c.gift.serial ? h('div.gs', null, `№ ${c.gift.serial}${c.gift.supply ? ' из ' + c.gift.supply : ''}`) : null,
        c.gift.message ? h('div.b-text', { style: { marginTop: '6px' } }, c.gift.message) : null), meta);
    } else if (isSquare) {
      bubble.append(squareEl(m), meta);
    } else if (isMedia) {
      bubble.append(mediaEl(m));
      if (c.text) bubble.append(h('div.b-text', null, richText(c.text, onLink), meta)); else bubble.append(meta);
    } else if (m.type === 'voice' && c?.file) {
      bubble.append(voiceEl(m), meta);
    } else if (m.type === 'file' && c?.file) {
      bubble.append(fileEl(m));
      if (c.text) bubble.append(h('div.b-text', null, richText(c.text, onLink)));
      bubble.append(meta);
    } else {
      bubble.append(h('div.b-text' + (emojiOnly ? '.big-emoji' : ''), null, richText(c?.text ?? '', onLink), emojiOnly ? null : meta));
      if (emojiOnly) bubble.append(meta);
    }

    if (m.reactions?.length) {
      const groups = new Map();
      for (const r of m.reactions) groups.set(r.emoji, [...(groups.get(r.emoji) ?? []), r.userId]);
      bubble.append(h('div.reactions', null, [...groups].map(([e, ids]) => h('button.reaction' + (ids.includes(store.myId) ? '.on' : ''), {
        title: ids.map((id) => senderName(id)).join(', '),
        onclick: (ev) => { ev.stopPropagation(); store.react(m, e).catch((x) => toast(errorText(x))); },
      }, e, ids.length > 1 ? h('small', null, ids.length) : null))));
    }

    if (isChannel && m.status === 'sent') {
      bubble.append(h('div.post-foot', null,
        chat.discussionId ? h('button.comments-btn', { onclick: (e) => { e.stopPropagation(); go(`/post/${chatId}/${m.id}`); } }, icon('chat_bubble'), m.comments ? `${m.comments} ${plural(m.comments)}` : 'Комментировать') : null,
        h('span.views', null, icon('visibility'), compact(m.views ?? 0))));
    }

    const row = h('div.msg' + (mine ? '.mine' : '') + (first ? '.first' : '') + (appear ? '.appear' : ''), {
      'data-id': m.id,
      oncontextmenu: (e) => { e.preventDefault(); messageMenu(e, m); },
      ondblclick: (e) => { if (!e.target.closest('a,button,video,audio') && m.status === 'sent' && canWrite()) setReply(m); },
    });
    if (showAvatar) {
      row.append(h('div.m-av', null, last ? h('div', { style: { cursor: 'pointer' }, onclick: () => go('/u/' + m.senderId) }, avatar(sender?.displayName ?? '?', sender?.avatarFileId, 34)) : null));
      if (!sender) store.loadUser(m.senderId).then(() => scheduleRender()).catch(() => {});
    }
    row.append(bubble);
    if (m.status === 'sent' && !m.deleted) {
      row.append(h('div.msg-actions', null,
        canWrite() ? iconBtn('reply', 'Ответить', () => setReply(m)) : null,
        iconBtn('add_reaction', 'Реакция', (e) => reactionMenu(e, m)),
        iconBtn('more_horiz', 'Ещё', (e) => messageMenu(e, m))));
    }
    return row;
  }

  function onLink(text) {
    if (text.startsWith('ryzik://')) {
      const st = text.match(/^ryzik:\/\/stickers\/([\w-]+)/);
      if (st) return openStickerPack(st[1]);
      const code = parseInvite(text);
      if (code) go('/join/' + encodeURIComponent(code));
      return;
    }
    if (text.startsWith('@')) openMention(text);
  }

  async function openMention(name) {
    try {
      const users = await store.searchUsers(name.slice(1));
      const u = users.find((x) => x.username.toLowerCase() === name.slice(1).toLowerCase());
      if (u) return go('/u/' + u.id);
      const c = await api.chatByUsername(name);
      store.previewChat(c);
      go('/c/' + c.id);
    } catch { toast('Не найдено: ' + name); }
  }

  function openStickerPack(id) { go('/stickers/' + id); }

  // ---------- Медиа ----------

  function onMedia(fileId, fn) {
    if (!mediaUpdaters.has(fileId)) mediaUpdaters.set(fileId, new Set());
    mediaUpdaters.get(fileId).add(fn);
  }
  offs.push(store.on('media', (fileId) => { for (const fn of mediaUpdaters.get(fileId) ?? []) fn(); }));

  function progressRing(p) {
    const r = 22, len = 2 * Math.PI * r;
    const ring = svgEl('svg', { class: 'progress-ring', viewBox: '0 0 52 52' },
      svgEl('circle', { class: 'bg', cx: 26, cy: 26, r }),
      svgEl('circle', { class: 'fg', cx: 26, cy: 26, r, 'stroke-dasharray': len, 'stroke-dashoffset': len * (1 - Math.max(0.03, p)) }));
    return h('div.dl', null, ring);
  }

  function mediaEl(m) {
    const f = m.content.file;
    const ratio = f.width && f.height ? Math.min(Math.max(f.height / f.width, 0.4), 1.6) : 0.75;
    const w = Math.max(Math.min(f.width || 320, 340), 180);
    const box = h('div.b-media', { style: { width: w + 'px', aspectRatio: `${1 / ratio}` } });
    const video = m.type === 'video';
    const render = () => {
      clear(box);
      const url = m.localUrl ?? store.mediaOf(f.id).url;
      const st = m.localUrl ? { state: 'ready' } : store.mediaOf(f.id);
      if (url) {
        if (video) {
          box.append(h('video', { src: url, preload: 'metadata', muted: true, playsInline: true }), h('div.dl', null, icon('play_arrow', 'fill')));
        } else box.append(h('img', { src: url, alt: f.name }));
      } else if (st.state === 'loading') box.append(progressRing(st.progress ?? 0));
      else if (st.state === 'error') box.append(h('div.dl', { title: st.error }, icon('refresh')));
      else box.append(h('div.dl', null, icon(video ? 'play_arrow' : 'download', 'fill')));
      if (m.status === 'sending') box.append(progressRing(m.progress ?? 0));
      if (video && f.durationMs) box.append(h('span.dur', null, formatDuration(f.durationMs)));
    };
    box.onclick = async () => {
      if (m.status !== 'sent' && !m.localUrl) return;
      const url = m.localUrl ?? await store.download(f).catch((e) => { toast(errorText(e)); return null; });
      if (url) openViewer(url, video, f, m.content.text);
    };
    onMedia(f.id, render);
    render();
    // Фото до 15 МБ скачиваем сразу, как в приложении.
    if (!video && !m.localUrl && f.id && store.mediaOf(f.id).state === 'idle' && (f.size ?? 0) < 15 * 1048576) store.download(f).catch(() => {});
    return box;
  }

  function squareEl(m) {
    const f = m.content.file;
    const box = h('div.b-square');
    let video = null;
    const render = () => {
      const url = m.localUrl ?? store.mediaOf(f.id).url;
      const st = store.mediaOf(f.id);
      if (url && !video) {
        clear(box);
        video = h('video', { src: url, playsInline: true, loop: false, preload: 'metadata' });
        video.onended = () => { video.currentTime = 0; };
        box.append(video, h('span.dur', null, formatDuration(f.durationMs)));
      } else if (!url) {
        clear(box).append(st.state === 'loading' ? progressRing(st.progress ?? 0) : h('div.dl', null, icon('play_arrow', 'fill')));
      }
    };
    box.onclick = async () => {
      if (!video) { await store.download(f).catch((e) => toast(errorText(e))); render(); video?.play(); return; }
      if (video.paused) video.play(); else video.pause();
    };
    onMedia(f.id, render);
    render();
    if (!m.localUrl && f.id && store.mediaOf(f.id).state === 'idle' && (f.size ?? 0) < 20 * 1048576) store.download(f).catch(() => {});
    return box;
  }

  function voiceEl(m) {
    const f = m.content.file;
    let bars = [];
    try { bars = f.waveform ? Array.from(atob(f.waveform), (ch) => ch.charCodeAt(0)) : []; } catch { /* */ }
    if (!bars.length) bars = Array.from({ length: 40 }, (_, i) => 20 + ((i * 37) % 60));
    const N = 40;
    const samples = Array.from({ length: N }, (_, i) => bars[Math.floor(i * bars.length / N)] ?? 0);
    const wave = h('div.wave', null, samples.map((v) => h('i', { style: { height: Math.max(12, Math.min(100, v)) + '%' } })));
    const time = h('span.vt', null, formatDuration(f.durationMs));
    const btn = h('button.play', { title: 'Слушать' }, icon('play_arrow', 'fill'));
    let audio = null;
    const setIcon = (n) => { btn.firstChild.textContent = n; };
    const tick = () => {
      if (!audio) return;
      const dur = audio.duration && Number.isFinite(audio.duration) ? audio.duration : (f.durationMs / 1000 || 1);
      const p = audio.currentTime / dur;
      [...wave.children].forEach((b, i) => b.classList.toggle('on', i / N < p));
      time.textContent = formatDuration(audio.currentTime * 1000);
    };
    btn.onclick = async (e) => {
      e.stopPropagation();
      if (!audio) {
        setIcon('hourglass_empty');
        const url = m.localUrl ?? await store.download(f).catch((x) => { toast(errorText(x)); return null; });
        if (!url) { setIcon('play_arrow'); return; }
        audio = new Audio(url);
        audio.ontimeupdate = tick;
        audio.onended = () => { setIcon('play_arrow'); [...wave.children].forEach((b) => b.classList.remove('on')); time.textContent = formatDuration(f.durationMs); };
        audio.onpause = () => setIcon('play_arrow');
        audio.onplay = () => { setIcon('pause'); stopOthers(audio); };
      }
      if (audio.paused) audio.play().catch(() => toast('Не удалось воспроизвести')); else audio.pause();
    };
    wave.onclick = (e) => {
      if (!audio?.duration || !Number.isFinite(audio.duration)) return;
      const r = wave.getBoundingClientRect();
      audio.currentTime = ((e.clientX - r.left) / r.width) * audio.duration;
    };
    return h('div.b-voice', null, btn, wave, time);
  }

  function fileEl(m) {
    const f = m.content.file;
    const fi = h('div.fi', null, icon('description', 'fill'));
    const sub = h('div.fs', null, formatSize(f.size ?? 0));
    const render = () => {
      const st = store.mediaOf(f.id);
      clear(fi).append(icon(m.status === 'sending' ? 'upload' : st.state === 'loading' ? 'downloading' : st.state === 'ready' ? 'description' : 'download', 'fill'));
      sub.textContent = m.status === 'sending' ? `${Math.round((m.progress ?? 0) * 100)}% · ${formatSize(f.size ?? 0)}`
        : st.state === 'loading' ? `${Math.round((st.progress ?? 0) * 100)}% · ${formatSize(f.size ?? 0)}` : formatSize(f.size ?? 0);
    };
    onMedia(f.id, render);
    render();
    return h('div.b-file', {
      onclick: async () => {
        if (m.status !== 'sent' && !m.localUrl) return;
        const url = m.localUrl ?? await store.download(f).catch((x) => { toast(errorText(x)); return null; });
        if (url) saveAs(url, f.name);
      },
    }, fi, h('div', { style: { minWidth: 0 } }, h('div.fn', null, f.name), sub));
  }

  // ---------- Меню сообщения ----------

  function canWrite() {
    if (!chat) return false;
    if (chat.peerBlocked) return false;
    return (chat.type !== 'channel' && (chat.myRole != null || chat.type === 'direct' || chat.type === 'saved')) || chat.myRole === 'owner' || chat.myRole === 'admin';
  }

  function reactionMenu(e, m) {
    const mine = m.reactions.find((r) => r.userId === store.myId)?.emoji;
    menu(e, [{ node: h('div.menu-reactions', null, QUICK_REACTIONS.map((x) => h('button' + (x === mine ? '.on' : ''), { onclick: (ev) => { ev.stopPropagation(); document.querySelector('.menu-scrim')?.click(); store.react(m, x).catch((err) => toast(errorText(err))); } }, x))) }]);
  }

  function messageMenu(e, m) {
    if (m.status !== 'sent') {
      menu(e, [m.status === 'failed' ? { icon: 'refresh', label: 'Отправить ещё раз', run: () => store.retry(m) } : null, { icon: 'delete', label: 'Удалить', danger: true, run: () => store.discard(m) }]);
      return;
    }
    const mine = m.senderId === store.myId;
    const me = store.me;
    const canDelete = mine || me?.isAdmin || chat?.myRole === 'owner' || (chat?.type === 'channel' && chat?.myRole === 'admin');
    const isPinned = chat?.pinnedMessage?.id === m.id;
    const myReaction = m.reactions.find((r) => r.userId === store.myId)?.emoji;
    const items = [
      { node: h('div.menu-reactions', null, QUICK_REACTIONS.map((x) => h('button' + (x === myReaction ? '.on' : ''), { onclick: (ev) => { ev.stopPropagation(); document.querySelector('.menu-scrim')?.click(); store.react(m, x).catch((err) => toast(errorText(err))); } }, x))) },
      canWrite() ? { icon: 'reply', label: 'Ответить', run: () => setReply(m) } : null,
      m.content?.text ? { icon: 'content_copy', label: 'Копировать текст', run: () => copyText(m.content.text) } : null,
      mine && m.content && ['text', 'image', 'video', 'file'].includes(m.type) && !m.forwardedFrom ? { icon: 'edit', label: 'Изменить', run: () => setEdit(m) } : null,
      chat?.canPin ? { icon: 'keep', label: isPinned ? 'Открепить' : 'Закрепить', run: () => store.pinMessage(chatId, isPinned ? null : m.id).catch((x) => toast(errorText(x))) } : null,
      chat?.type !== 'saved' && store.savedChat() && m.content ? { icon: 'bookmark', label: 'В Избранное', run: () => { store.forward(m, store.savedChat().id); toast('Сохранено в Избранное'); } } : null,
      m.content ? { icon: 'forward', label: 'Переслать', run: () => forwardSheet(m) } : null,
      m.content?.file && m.status === 'sent' ? { icon: 'download', label: 'Сохранить файл', run: async () => { const url = m.localUrl ?? await store.download(m.content.file).catch(() => null); if (url) saveAs(url, m.content.file.name); } } : null,
      mine && chat?.type !== 'saved' && chat?.type !== 'channel' ? { icon: 'done_all', label: 'Кто прочитал', run: () => showReaders(m) } : null,
      m.reactions.length ? { icon: 'mood', label: `Реакции: ${m.reactions.length}`, run: () => showReactions(m) } : null,
      canDelete ? '-' : null,
      canDelete ? { icon: 'delete', label: 'Удалить', danger: true, run: async () => { if (await confirm('Удалить сообщение?', 'Сообщение пропадёт у всех участников чата.', 'Удалить', true)) store.deleteMessage(m).catch((x) => toast(errorText(x))); } } : null,
    ];
    menu(e, items);
  }

  async function showReaders(m) {
    try {
      const r = await api.readers(m.id);
      store.rememberUsers(r.users ?? []);
      dialog({
        title: r.count === 0 ? 'Ещё никто не прочитал' : chat.type === 'direct' ? 'Прочитано' : `Прочитали: ${r.count}`,
        body: h('div', null, (r.users ?? []).map((u) => h('div.list-item', null, avatar(u.displayName, u.avatarFileId, 36), h('div.li-text', null, h('div.li-title', null, u.displayName)))),
          r.count > (r.users?.length ?? 0) ? h('p.hint', null, `и ещё ${r.count - r.users.length}`) : null),
        actions: [{ label: 'Закрыть' }],
      });
    } catch (x) { toast(errorText(x)); }
  }

  function showReactions(m) {
    dialog({
      title: 'Реакции',
      body: h('div', null, m.reactions.map((r) => h('div.list-item', null, h('span', { style: { fontSize: '24px' } }, r.emoji), h('div.li-text', null, h('div.li-title', null, r.userId === store.myId ? 'Вы' : senderName(r.userId)))))),
      actions: [{ label: 'Закрыть' }],
    });
  }

  function forwardSheet(m) {
    const listBox = h('div');
    const s = sheet({ title: 'Переслать в…', body: listBox });
    const targets = store.chats.filter((c) => c.type !== 'channel' || c.myRole === 'owner' || c.myRole === 'admin').filter((c) => !c.peerBlocked);
    for (const c of targets) {
      listBox.append(h('div.chat-row', { onclick: () => { store.forward(m, c.id); s.close(); toast(`Переслано: ${store.chatTitle(c)}`); } },
        chatAvatar(store, c, 44), h('div.cr-mid', null, h('div.cr-title', null, store.chatTitle(c)))));
    }
  }

  function setReply(m) {
    editing = null;
    replyTo = m;
    renderComposer();
    focusInput();
  }

  function setEdit(m) {
    replyTo = null;
    editing = m;
    renderComposer();
    input.value = m.content?.text ?? '';
    autosize();
    focusInput();
  }

  // ---------- Поле ввода ----------

  const input = h('textarea', { rows: 1, placeholder: 'Сообщение', maxlength: 8000 });
  const draftKey = 'rc.draft.' + chatId;
  try { input.value = sessionStorage.getItem(draftKey) ?? ''; } catch { /* */ }

  function autosize() {
    input.style.height = 'auto';
    input.style.height = Math.min(input.scrollHeight, 180) + 'px';
  }
  input.addEventListener('input', () => {
    autosize();
    updateSendIcon();
    try { sessionStorage.setItem(draftKey, input.value); } catch { /* */ }
    if (input.value && Date.now() - lastTyping > 3000 && chat?.type !== 'channel') { lastTyping = Date.now(); store.sendTyping(chatId); }
  });
  input.addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.isComposing && settings.enterToSend && matchMedia('(pointer: fine)').matches) { e.preventDefault(); send(); }
    if (e.key === 'Escape' && (replyTo || editing)) { if (editing) input.value = ''; replyTo = editing = null; renderComposer(); }
    if (e.key === 'ArrowUp' && !input.value) {
      const lastMine = [...store.messagesOf(chatId)].reverse().find((x) => x.senderId === store.myId && x.status === 'sent' && x.type === 'text');
      if (lastMine) { e.preventDefault(); setEdit(lastMine); }
    }
  });
  input.addEventListener('paste', (e) => {
    const files = [...(e.clipboardData?.files ?? [])];
    if (files.length) { e.preventDefault(); sendFiles(files); }
  });

  const sendBtn = h('button.send-btn', { title: 'Отправить', onclick: () => onSendBtn() }, icon('mic'));
  function updateSendIcon() {
    const hasText = !!input.value.trim();
    sendBtn.firstChild.textContent = editing ? 'check' : hasText ? 'send' : 'mic';
    sendBtn.title = editing ? 'Сохранить' : hasText ? 'Отправить' : 'Записать голосовое';
  }

  function onSendBtn() {
    if (editing || input.value.trim()) return send();
    startRecording('voice');
  }

  function focusInput() { setTimeout(() => input.focus(), 0); }

  async function send() {
    const text = input.value;
    if (editing) {
      const m = editing;
      editing = null;
      input.value = '';
      renderComposer();
      if (text.trim() && text.trim() !== m.content?.text) await store.editMessage(m, text).catch((x) => toast(errorText(x)));
      return;
    }
    if (!text.trim()) return;
    store.sendText(chatId, text, replyTo?.id ?? null);
    input.value = '';
    try { sessionStorage.removeItem(draftKey); } catch { /* */ }
    replyTo = null;
    panel = panel === 'emoji' ? panel : null;
    renderComposer();
    atBottom = true;
    focusInput();
  }

  // ---------- AI-помощник ----------

  function aiEndpoint(raw) {
    const base = String(raw || '').trim().replace(/\/$/, '');
    if (!base) return '';
    if (base.endsWith('/chat/completions')) return base;
    if (base.endsWith('/v1')) return base + '/chat/completions';
    return base + '/v1/chat/completions';
  }

  function aiConfigure(after) {
    const url = field('Ссылка на API', { value: settings.aiUrl || '', placeholder: 'https://api.example.com/v1' });
    const key = field('API-ключ', { value: settings.aiKey || '', type: 'password' });
    const model = field('Модель', { value: settings.aiModel || 'gpt-4o-mini', placeholder: 'gpt-4o-mini' });
    dialog({
      title: 'Подключить AI',
      icon: 'auto_awesome',
      wide: true,
      body: h('div', null, url.el, key.el, model.el,
        h('p.hint', null, 'Ключ хранится только в этом браузере и отправляется напрямую на указанный адрес.')),
      actions: [
        { label: 'Отмена' },
        { label: 'Сохранить', primary: true, run: () => {
          saveAi(url.input.value, key.input.value, model.input.value);
          after?.();
        } },
      ],
    });
  }

  function saveAi(url, key, model) {
    // settings сохраняется через saveSettings из ui.js; здесь достаточно localStorage-совместимого patch.
    Object.assign(settings, { aiUrl: String(url || '').trim(), aiKey: String(key || '').trim(), aiModel: String(model || '').trim() || 'gpt-4o-mini' });
    try { localStorage.setItem('rc.settings', JSON.stringify(settings)); } catch { /* */ }
  }

  const AI_ACTIONS = {
    style: ['✨', 'Стиль текста', 'Приведи текст в порядок: исправь орфографию, пунктуацию и грамматику, сделай формулировки естественными. Сохрани смысл и язык. Верни только готовый текст.'],
    emoji: ['😊', 'Добавить эмодзи', 'Расставь в текст подходящие по смыслу эмодзи. Не меняй слова и смысл. Верни только готовый текст.'],
    shorten: ['✂️', 'Сократить текст', 'Сократи текст примерно на треть, сохранив главный смысл и тон. Убери повторы. Верни только готовый текст.'],
  };

  function aiMenu() {
    if (!settings.aiUrl || !settings.aiKey) { aiConfigure(aiMenu); return; }
    let sh;
    const body = h('div', null, ...Object.entries(AI_ACTIONS).map(([key, [emoji, title]]) =>
      h('button.list-item', { style: { width: '100%' }, onclick: () => { sh.close(); aiRun(key); } },
        h('span', { style: { fontSize: '22px', width: '36px' } }, emoji), h('span.li-text', null, h('span.li-title', null, title)))));
    body.append(h('button.text-btn', { style: { marginTop: '6px' }, onclick: () => { sh.close(); aiConfigure(aiMenu); } }, 'Настроить AI'));
    sh = sheet({ title: 'AI-помощник', body });
  }

  async function aiRun(kind) {
    const [emoji, title, prompt] = AI_ACTIONS[kind];
    const text = input.value;
    const wait = dialog({ title: emoji + ' ' + title, icon: 'auto_awesome', body: h('div', null, spinner(), h('p.hint', null, 'AI думает…')), actions: [] });
    try {
      const res = await fetch(aiEndpoint(settings.aiUrl), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + settings.aiKey },
        body: JSON.stringify({
          model: settings.aiModel || 'gpt-4o-mini',
          temperature: 0.3,
          messages: [
            { role: 'system', content: 'Ты помощник в мессенджере RyzikChat. Отвечай на языке пользователя. ' + prompt },
            { role: 'user', content: text },
          ],
        }),
      });
      const raw = await res.text();
      let data = null;
      try { data = JSON.parse(raw); } catch { /* */ }
      if (!res.ok) throw new Error(data?.error?.message || (res.status === 401 ? 'AI-сервис отклонил ключ' : 'Ошибка AI-сервиса (' + res.status + ')'));
      const out = data?.choices?.[0]?.message?.content?.trim();
      if (!out) throw new Error('AI вернул пустой ответ');
      wait.close();
      dialog({
        title: 'Готовый текст',
        icon: 'auto_awesome',
        wide: true,
        body: h('div', null, h('p', { style: { whiteSpace: 'pre-wrap', lineHeight: '1.5' } }, out)),
        actions: [
          { label: 'Вставить ниже', run: () => { input.value = input.value.trimEnd() + '\n\n' + out; autosize(); updateSendIcon(); focusInput(); } },
          { label: 'Заменить', primary: true, run: () => { input.value = out; autosize(); updateSendIcon(); focusInput(); } },
        ],
      });
    } catch (e) {
      wait.close();
      toast(e?.message || 'Не удалось получить ответ AI');
    }
  }

  function sendFiles(files, asType = null) {
    for (const f of files) store.sendFile(chatId, f, '', asType, replyTo?.id ?? null);
    replyTo = null;
    atBottom = true;
    renderComposer();
  }

  async function attach(e) {
    menu(e, [
      { icon: 'image', label: 'Фото или видео', run: async () => sendFiles(await pickFile('image/*,video/*', true)) },
      { icon: 'draft', label: 'Файл', run: async () => sendFiles(await pickFile('*/*', true), 'file') },
      { icon: 'radio_button_checked', label: 'Видеосообщение', run: () => startRecording('square') },
      { icon: 'sticky_note', label: 'Стикеры', run: () => { panel = 'stickers'; renderComposer(); } },
    ]);
  }

  function renderComposer() {
    clear(bottom);
    updateSendIcon();
    if (!chat) return;
    const col = h('div.composer-col');
    bottom.append(col);
    if (chat.banned) {
      col.append(h('div.compose-ctx.readonly-bar', null, icon('gpp_bad'), 'Чат заблокирован модерацией' + (chat.banReason ? `. Причина: ${chat.banReason}` : '')));
      return;
    }
    if (chat.peerBlocked) {
      col.append(h('div.compose-ctx', { style: { borderRadius: '26px' } }, icon('block', 'lead'), h('div.cc-text', null, 'Пользователь заблокирован'),
        h('button.text-btn', { onclick: () => store.setBlocked(store.peerOf(chat).id, false).catch((x) => toast(errorText(x))) }, 'Разблокировать')));
      return;
    }
    if (!canWrite()) {
      const subscribed = chat.myRole != null;
      col.append(subscribed
        ? h('button.tonal-btn.btn-block', { onclick: () => store.chatSettings(chatId, { muted: !chat.muted }).catch((x) => toast(errorText(x))) }, icon(chat.muted ? 'notifications' : 'notifications_off'), chat.muted ? 'Включить звук' : 'Выключить звук')
        : h('button.filled-btn.btn-block', { onclick: () => store.subscribe(chatId).then(() => store.loadLatest(chatId)).catch((x) => toast(errorText(x))) }, icon('add'), chat.type === 'group' ? 'Вступить в группу' : 'Подписаться'));
      return;
    }
    if (store.me?.restrictedUntil && store.me.restrictedUntil > Date.now()) {
      col.append(h('div.compose-ctx.readonly-bar', null, icon('gpp_maybe'), 'Аккаунт ограничен: писать сообщения пока нельзя' + (store.me.restrictReason ? `. Причина: ${store.me.restrictReason}` : '')));
      return;
    }
    const peer = store.peerOf(chat);
    if (chat.type === 'direct' && peer?.isBot) {
      if (!store.messagesOf(chatId).some((m) => m.senderId === store.myId)) col.append(h('button.filled-btn.btn-block', { style: { marginBottom: '6px' }, onclick: () => store.sendText(chatId, '/start') }, 'Начать'));
      else if (peer.botCommands?.length) col.append(h('div.chips', { style: { padding: '0 0 6px' } }, peer.botCommands.map((bc) => h('button.chip', { title: bc.description, onclick: () => store.sendText(chatId, '/' + bc.command) }, '/' + bc.command))));
    }
    if (panel) col.append(panelEl());
    const ctx = editing ?? replyTo;
    if (ctx) {
      col.append(h('div.compose-ctx', null, icon(editing ? 'edit' : 'reply', 'lead'),
        h('div.cc-text', null, h('div.cc-t', null, editing ? 'Редактирование' : senderName(ctx.senderId)), h('div.cc-s', null, ctx.content ? previewText(ctx.type, ctx.content) : '')),
        iconBtn('close', 'Отмена', () => { if (editing) input.value = ''; replyTo = editing = null; renderComposer(); })));
    }
    const aiLines = (input.value.match(/\n/g) || []).length + 1;
    if (aiLines >= 3 || input.value.length >= 180) {
      col.append(h('div.ai-row', null, h('button.ai-chip', { onclick: () => aiMenu() }, icon('auto_awesome'), 'AI-помощник')));
    }
    if (recorder) { col.append(recorder.bar); return; }
    col.append(h('div.composer', null,
      h('div.composer-box', null,
        iconBtn(panel === 'emoji' ? 'keyboard' : 'mood', 'Эмодзи и стикеры', () => { panel = panel ? null : 'emoji'; renderComposer(); focusInput(); }),
        input,
        iconBtn('attach_file', 'Прикрепить', (e) => attach(e))),
      sendBtn));
    autosize();
  }

  function panelEl() {
    const box = h('div.panel');
    const tabs = h('div.panel-tabs');
    const body = h('div');
    box.append(tabs, body);
    const packs = store.stickerPacks;
    let cur = panel === 'stickers' ? (packs[0]?.id ?? 'emoji') : 'emoji';
    const draw = () => {
      clear(tabs);
      tabs.append(h('button' + (cur === 'emoji' ? '.on' : ''), { title: 'Эмодзи', onclick: () => { cur = 'emoji'; draw(); } }, '😀'));
      for (const p of packs) {
        tabs.append(h('button' + (cur === p.id ? '.on' : ''), { title: p.title, onclick: () => { cur = p.id; draw(); } },
          p.stickers[0] ? h('img', { src: api.avatarUrl(p.stickers[0].fileId), alt: '' }) : icon('sticky_note')));
      }
      tabs.append(h('div.grow'), iconBtn('close', 'Закрыть', () => { panel = null; renderComposer(); }));
      clear(body);
      if (cur === 'emoji') {
        body.append(h('div.emoji-grid', null, EMOJI.map((e) => h('button', {
          onclick: () => {
            const s = input.selectionStart ?? input.value.length;
            input.value = input.value.slice(0, s) + e + input.value.slice(input.selectionEnd ?? s);
            input.selectionStart = input.selectionEnd = s + e.length;
            input.dispatchEvent(new Event('input'));
          },
        }, e))));
      } else {
        const p = packs.find((x) => x.id === cur);
        body.append(h('div.sticker-grid', null, (p?.stickers ?? []).map((s) => h('button', {
          title: s.emoji, onclick: () => { store.sendSticker(chatId, p, s, replyTo?.id ?? null); replyTo = null; atBottom = true; renderComposer(); },
        }, h('img', { src: api.avatarUrl(s.fileId), alt: s.emoji, loading: 'lazy' })))));
        if (!packs.length) body.append(h('p.pad.muted.center', null, 'Наборов стикеров пока нет. Добавить их можно по ссылке на набор.'));
      }
    };
    if (!packs.length) store.refreshStickers().catch(() => {});
    draw();
    return box;
  }

  // ---------- Запись голосовых и квадратиков ----------

  async function startRecording(kind) {
    if (recorder) return;
    if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) { toast('Браузер не поддерживает запись'); return; }
    let stream;
    try {
      stream = await navigator.mediaDevices.getUserMedia(kind === 'voice' ? { audio: true } : { audio: true, video: { width: { ideal: 480 }, height: { ideal: 480 }, aspectRatio: 1, facingMode: 'user' } });
    } catch { toast(kind === 'voice' ? 'Нет доступа к микрофону' : 'Нет доступа к камере'); return; }
    const types = kind === 'voice'
      ? ['audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/mp4', 'audio/webm']
      : ['video/mp4;codecs=avc1,mp4a', 'video/webm;codecs=vp8,opus', 'video/webm', 'video/mp4'];
    const mimeType = types.find((t) => MediaRecorder.isTypeSupported(t)) ?? '';
    const rec = new MediaRecorder(stream, mimeType ? { mimeType, audioBitsPerSecond: 64000, videoBitsPerSecond: 1000000 } : undefined);
    const chunks = [];
    const levels = [];
    let ctx, analyser, data, raf;
    try {
      ctx = new AudioContext();
      analyser = ctx.createAnalyser();
      analyser.fftSize = 512;
      ctx.createMediaStreamSource(stream).connect(analyser);
      data = new Uint8Array(analyser.fftSize);
    } catch { /* без волны */ }
    const started = Date.now();
    const timeEl = h('span.rt', null, '0:00');
    let preview = null;
    if (kind === 'square') {
      const v = h('video', { autoplay: true, muted: true, playsInline: true });
      v.srcObject = stream;
      preview = h('div.rec-preview', null, v);
      document.body.append(preview);
    }
    let cancelled = false;
    const finish = (send) => { cancelled = !send; if (rec.state !== 'inactive') rec.stop(); };
    const bar = h('div.composer', null,
      h('div.rec-bar', null, h('i.dotred'), timeEl, h('span.rh', null, kind === 'voice' ? 'Голосовое' : 'Квадратик'),
        iconBtn('delete', 'Отменить', () => finish(false))),
      h('button.send-btn.rec', { title: 'Отправить', onclick: () => finish(true) }, icon('send')));
    let lastSample = 0;
    const loop = () => {
      const ms = Date.now() - started;
      timeEl.textContent = formatDuration(ms);
      if (analyser && ms - lastSample > 100) {
        lastSample = ms;
        analyser.getByteTimeDomainData(data);
        let peak = 0;
        for (const x of data) peak = Math.max(peak, Math.abs(x - 128));
        levels.push(Math.min(100, Math.round(peak / 128 * 140)));
      }
      if (ms > (kind === 'voice' ? 15 * 60000 : 60000)) finish(true);
      raf = requestAnimationFrame(loop);
    };
    rec.ondataavailable = (e) => e.data.size && chunks.push(e.data);
    rec.onstop = () => {
      cancelAnimationFrame(raf);
      stream.getTracks().forEach((t) => t.stop());
      ctx?.close().catch(() => {});
      preview?.remove();
      recorder = null;
      renderComposer();
      const durationMs = Date.now() - started;
      if (cancelled || durationMs < 700 || !chunks.length) return;
      const type = (rec.mimeType || mimeType || (kind === 'voice' ? 'audio/webm' : 'video/webm')).split(';')[0];
      const ext = type.includes('mp4') ? (kind === 'voice' ? 'm4a' : 'mp4') : type.includes('ogg') ? 'ogg' : 'webm';
      const file = new File(chunks, `${kind}-${Date.now()}.${ext}`, { type });
      const step = Math.max(1, Math.ceil(levels.length / 100));
      const wave = levels.filter((_, i) => i % step === 0);
      const waveform = btoa(String.fromCharCode(...wave));
      const tr = stream.getVideoTracks()[0]?.getSettings?.() ?? {};
      store.sendFile(chatId, file, '', kind, replyTo?.id ?? null, { durationMs, waveform: kind === 'voice' ? waveform : '', width: tr.width ?? 0, height: tr.height ?? 0 });
      replyTo = null;
      atBottom = true;
      renderComposer();
    };
    rec.start(250);
    recorder = { bar, rec };
    renderComposer();
    loop();
  }

  // ---------- Прокрутка ----------

  function updateDown() {
    downBtn.classList.toggle('hide', atBottom);
    const text = !atBottom && newBelow ? String(newBelow) : '';
    const badge = downBtn.querySelector('.badge');
    if (!text) badge?.remove();
    else if (!badge) downBtn.append(h('span.badge', null, text));
    else if (badge.textContent !== text) badge.textContent = text;
  }

  function scrollToBottom(smooth) {
    atBottom = true;
    newBelow = 0;
    scroller.scrollTo({ top: scroller.scrollHeight, behavior: smooth ? 'smooth' : 'auto' });
    updateDown();
    store.markRead(chatId);
  }

  async function scrollToMessage(id) {
    let tries = 0;
    while (!store.messagesOf(chatId).some((m) => m.id === id) && store.canLoadMore(chatId) && tries++ < 20) {
      await store.loadOlder(chatId).catch(() => {});
    }
    renderMessages({ keepOffset: true });
    const node = inner.querySelector(`[data-id="${CSS.escape(id)}"]`);
    if (!node) { toast('Сообщение не найдено'); return; }
    atBottom = false;
    node.scrollIntoView({ block: 'center', behavior: 'smooth' });
    node.classList.remove('highlight');
    void node.offsetWidth;
    node.classList.add('highlight');
  }

  scroller.addEventListener('scroll', async () => {
    const dist = scroller.scrollHeight - scroller.scrollTop - scroller.clientHeight;
    atBottom = dist < 80;
    if (atBottom) { newBelow = 0; store.markRead(chatId); }
    updateDown();
    if (scroller.scrollTop < 300 && !loadingOlder && store.canLoadMore(chatId) && store.messagesOf(chatId).length) {
      loadingOlder = true;
      try { await store.loadOlder(chatId); renderMessages({ keepOffset: true }); } catch { /* */ } finally { loadingOlder = false; }
    }
  }, { passive: true });

  // ---------- Перетаскивание файлов ----------
  let dragDepth = 0;
  el.addEventListener('dragenter', (e) => { if (canWrite() && e.dataTransfer?.types?.includes('Files')) { dragDepth++; el.classList.add('dragging'); e.preventDefault(); } });
  el.addEventListener('dragover', (e) => { if (el.classList.contains('dragging')) e.preventDefault(); });
  el.addEventListener('dragleave', () => { if (--dragDepth <= 0) { dragDepth = 0; el.classList.remove('dragging'); } });
  el.addEventListener('drop', (e) => {
    e.preventDefault();
    dragDepth = 0;
    el.classList.remove('dragging');
    const files = [...(e.dataTransfer?.files ?? [])];
    if (files.length) sendFiles(files);
  });

  // ---------- Подписки ----------

  // Все обновления за один кадр собираем в одну перерисовку.
  let pendingOpts = null;
  function scheduleRender(opts = {}) {
    const queued = !!pendingOpts;
    pendingOpts = { ...pendingOpts, ...opts };
    if (queued) return;
    requestAnimationFrame(() => { const o = pendingOpts; pendingOpts = null; renderMessages(o); });
  }

  // Картинки догружаются, окно меняет размер, клавиатура на телефоне выезжает —
  // если были внизу, остаёмся внизу, а не уезжаем вверх рывками.
  if (window.ResizeObserver) {
    const ro = new ResizeObserver(() => { if (atBottom) scroller.scrollTop = scroller.scrollHeight; });
    ro.observe(scroller);
    ro.observe(inner);
    offs.push(() => ro.disconnect());
  }

  offs.push(store.on('messages', (id) => {
    if (id !== chatId) return;
    const list = store.messagesOf(chatId);
    const last = list[list.length - 1];
    if (!atBottom && last && last.senderId !== store.myId && last.createdAt > Date.now() - 10000) newBelow++;
    if (last?.senderId === store.myId && last.status !== 'sent') atBottom = true;
    scheduleRender({ animateNew: true });
    renderPinned();
    if (atBottom && document.visibilityState === 'visible') store.markRead(chatId);
  }));
  offs.push(store.on('progress', (id, clientId) => {
    if (id !== chatId) return;
    const m = store.messagesOf(chatId).find((x) => x.clientId === clientId && x.status === 'sending');
    if (m) scheduleRender();
  }));
  offs.push(store.on('chat', (id) => {
    if (id !== chatId) return;
    const prevRole = chat?.myRole;
    chat = store.chat(chatId);
    renderHeader(); renderBg(); renderPinned(); renderContactBar();
    if (prevRole !== chat?.myRole || !bottom.firstChild) renderComposer();
    scheduleRender();
  }));
  offs.push(store.on('chats', () => {
    const c = store.chat(chatId);
    if (c && c !== chat) {
      const blockedChanged = c.peerBlocked !== chat?.peerBlocked || c.peerIsContact !== chat?.peerIsContact || c.muted !== chat?.muted;
      chat = c;
      renderHeader();
      if (blockedChanged) { renderComposer(); renderContactBar(); }
    }
  }));
  offs.push(store.on('users', () => renderHeader()));
  offs.push(store.on('typing', () => renderHeader()));
  offs.push(store.on('read', (id) => { if (id === chatId) scheduleRender(); }));
  offs.push(store.on('stickers', () => { if (panel) renderComposer(); }));

  // ---------- Старт ----------

  store.loadedChats ??= new Set();
  renderHeader(); renderBg(); renderContactBar(); renderPinned(); renderComposer(); renderMessages();

  (async () => {
    if (!chat) {
      try { chat = await store.loadChat(chatId); } catch (e) { toast(errorText(e)); back(); return; }
      renderHeader(); renderBg(); renderContactBar(); renderPinned(); renderComposer();
    }
    try {
      await store.loadLatest(chatId);
    } catch (e) { toast(errorText(e)); }
    store.loadedChats.add(chatId);
    renderMessages({ toBottom: true });
    renderPinned();
    if (focusMessageId) scrollToMessage(focusMessageId); else scrollToBottom(false);
    store.markRead(chatId);
    if (matchMedia('(pointer: fine)').matches) focusInput();
  })();

  return {
    el,
    destroy() {
      offs.forEach((f) => f());
      if (recorder) recorder.rec.stop();
      stopOthers(null);
      if (store.openChatId === chatId) store.openChatId = null;
    },
  };
}

// ---------- Вспомогательное ----------

let playing = null;
function stopOthers(a) {
  if (playing && playing !== a) { try { playing.pause(); } catch { /* */ } }
  playing = a;
}

const NAME_COLORS = ['#E17076', '#7BC862', '#E5A64E', '#65AADD', '#A695E7', '#EE7AAE', '#6EC9CB', '#FAA774'];
function nameColor(id) {
  let x = 0;
  for (const ch of id) x = (x * 31 + ch.charCodeAt(0)) | 0;
  return NAME_COLORS[Math.abs(x) % NAME_COLORS.length];
}

function plural(n) {
  const m10 = n % 10, m100 = n % 100;
  return m10 === 1 && m100 !== 11 ? 'комментарий' : m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14) ? 'комментария' : 'комментариев';
}

function compact(n) {
  if (n >= 1e6) return (n / 1e6).toFixed(1).replace('.0', '') + 'M';
  if (n >= 1e3) return (n / 1e3).toFixed(1).replace('.0', '') + 'K';
  return String(n);
}

function svgEl(tag, attrs, ...children) {
  const e = document.createElementNS('http://www.w3.org/2000/svg', tag);
  for (const [k, v] of Object.entries(attrs)) e.setAttribute(k, String(v));
  e.append(...children);
  return e;
}

export function saveAs(url, name) {
  const a = h('a', { href: url, download: name || 'file' });
  document.body.append(a);
  a.click();
  a.remove();
}

export function openViewer(url, video, f, caption) {
  const media = video ? h('video', { src: url, controls: true, autoplay: true, playsInline: true }) : h('img', { src: url, alt: f?.name ?? '' });
  const close = () => { v.remove(); document.removeEventListener('keydown', onKey); };
  const onKey = (e) => { if (e.key === 'Escape') close(); };
  const v = h('div.viewer', { onclick: (e) => { if (e.target === v) close(); } },
    h('div.v-top', null, iconBtn('download', 'Сохранить', () => saveAs(url, f?.name)), iconBtn('close', 'Закрыть', close)),
    media, caption ? h('div.v-cap', null, caption) : null);
  document.addEventListener('keydown', onKey);
  document.body.append(v);
}
