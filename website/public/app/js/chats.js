// Вкладка «Чаты» — как ChatListScreen.kt.
import { store, api, errorText } from './store.js';
import { h, icon, iconBtn, clear, patchChildren, chatAvatar, avatar, nameLine, userNameLine, formatListTime, menu, confirm, dialog, toast, emptyState, prompt, members, spinner } from './ui.js';
import { go } from './nav.js';

const FILTERS = [
  ['all', 'Все'], ['unread', 'Непрочитанные'], ['direct', 'Личные'], ['group', 'Группы'], ['channel', 'Каналы'],
];

export function chatListTab() {
  let filter = 'all';
  let query = '';
  let showArchive = false;
  let activeId = null;
  let searchSeq = 0;
  const shown = new Set(); // анимация появления — только для новых строк
  const rows = new Map(); // id -> { sig, el } — строки перерисовываются, только если что-то в них поменялось
  let found = { users: [], channels: [] };

  const sub = h('div.s');
  const bar = h('header.top-bar.large', null,
    h('div.top-title', null, h('div.t', null, 'Чаты'), sub),
    h('div.top-actions', null, iconBtn('edit_square', 'Новый чат', () => go('/new/chat'))),
  );
  const input = h('input', { placeholder: 'Поиск чатов и людей', type: 'search' });
  const clearBtn = iconBtn('close', 'Очистить', () => { input.value = ''; onSearch(); }, 'hidden');
  const search = h('div.search', null, icon('search'), input, clearBtn);
  const chips = h('div.chips');
  const list = h('div.scroll');
  const fab = h('button.fab', { title: 'Создать', onclick: (e) => fabMenu(e) }, icon('edit'));
  const el = h('div.screen', null, bar, search, chips, list, fab);

  function onSearch() {
    query = input.value.trim();
    clearBtn.classList.toggle('hidden', !query);
    render();
    const seq = ++searchSeq;
    found = { users: [], channels: [] };
    if (query.length >= 2) {
      setTimeout(async () => {
        if (seq !== searchSeq) return;
        const [users, channels] = await Promise.all([
          store.searchUsers(query).catch(() => []),
          api.searchChannels(query).catch(() => []),
        ]);
        if (seq !== searchSeq) return;
        found = { users, channels };
        render();
      }, 300);
    }
  }
  input.addEventListener('input', onSearch);

  let chipsFor = null;
  function renderChips() {
    if (chipsFor === filter) return;
    chipsFor = filter;
    clear(chips);
    for (const [id, title] of FILTERS) {
      chips.append(h('button.chip' + (filter === id ? '.on' : ''), { onclick: () => { filter = id; showArchive = false; render(); } }, filter === id ? icon('check') : null, title));
    }
  }

  function renderStatus() {
    const text = store.connected ? '' : (api.wsUrl ? 'Соединение…' : '');
    if (sub.textContent !== text) sub.textContent = text;
    sub.classList.toggle('accent', !store.connected);
  }

  function matches(c) {
    if (!query) return true;
    const q = query.toLowerCase().replace(/^@/, '');
    const t = store.chatTitle(c).toLowerCase();
    const p = store.peerOf(c);
    return t.includes(q) || (p?.username ?? '').toLowerCase().includes(q) || (c.username ?? '').toLowerCase().includes(q);
  }

  function render() {
    renderChips();
    renderStatus();
    const out = [];
    let chats = store.chats.filter(matches);
    const archived = chats.filter((c) => c.archived);
    chats = chats.filter((c) => showArchive ? c.archived : !c.archived);
    if (filter === 'unread') chats = chats.filter((c) => c.unread > 0);
    else if (filter === 'direct') chats = chats.filter((c) => c.type === 'direct' || c.type === 'saved');
    else if (filter !== 'all') chats = chats.filter((c) => c.type === filter);

    if (showArchive) {
      out.push(h('div.list-item.clickable', { onclick: () => { showArchive = false; render(); } },
        h('div.li-lead', null, icon('arrow_back')), h('div.li-text', null, h('div.li-title', null, 'Архив'))));
    } else if (archived.length && !query) {
      const unread = archived.reduce((n, c) => n + (c.unread > 0 ? 1 : 0), 0);
      out.push(h('div.chat-row.still', { onclick: () => { showArchive = true; render(); } },
        h('div.avatar', { style: { width: '52px', height: '52px', '--av': 'var(--outline)' } }, icon('archive')),
        h('div.cr-mid', null,
          h('div.cr-top', null, h('div.cr-title', null, 'Архив')),
          h('div.cr-bot', null, h('div.cr-prev', null, archived.map((c) => store.chatTitle(c)).join(', ')), unread ? h('span.unread.muted', null, unread) : null))));
    }

    if (!store.chats.length && !query) {
      out.push(store.loadedOnce ? emptyState('forum', 'Пока нет чатов', 'Найдите друзей по имени или @нику и начните переписку.') : spinner());
    }
    const seen = new Set();
    chats.forEach((c, i) => { seen.add(c.id); out.push(row(c, i)); });
    for (const id of [...rows.keys()]) if (!seen.has(id)) rows.delete(id);

    if (query.length >= 2) {
      const users = found.users.filter((u) => u.id !== store.myId && !store.chats.some((c) => c.type === 'direct' && store.peerOf(c)?.id === u.id));
      const channels = found.channels.filter((c) => !store.chat(c.id));
      if (users.length) {
        out.push(h('div.list-head', null, 'Люди'));
        for (const u of users) {
          out.push(h('div.chat-row.still', { onclick: () => openUser(u) },
            avatar(u.displayName, u.avatarFileId, 52, { online: u.online }),
            h('div.cr-mid', null, h('div.cr-top', null, h('div.cr-title', null, userNameLine(u))), h('div.cr-bot', null, h('div.cr-prev', null, '@' + u.username)))));
        }
      }
      if (channels.length) {
        out.push(h('div.list-head', null, 'Каналы и группы'));
        for (const c of channels) {
          out.push(h('div.chat-row.still', { onclick: () => { store.previewChat(c); go('/c/' + c.id); } },
            avatar(c.title, c.avatarFileId, 52),
            h('div.cr-mid', null, h('div.cr-top', null, h('div.cr-title', null, nameLine(c.title, { verified: c.verified }))),
              h('div.cr-bot', null, h('div.cr-prev', null, (c.username ? '@' + c.username + ' · ' : '') + members(c.memberCount, c.type === 'channel'))))));
        }
      }
      if (!chats.length && !users.length && !channels.length) out.push(emptyState('search_off', 'Ничего не найдено', null));
    }
    patchChildren(list, out);
  }

  async function openUser(u) {
    try {
      const c = await store.openDirect(u.id);
      input.value = '';
      onSearch();
      go('/c/' + c.id);
    } catch (e) { toast(errorText(e)); }
  }

  function row(c, i) {
    const peer = store.peerOf(c);
    const typingMap = store.typing.get(c.id);
    const typingNames = typingMap ? [...typingMap.keys()].map((id) => store.user(id)?.displayName).filter(Boolean) : [];
    const last = c.lastMessage;
    const mine = last && last.senderId === store.myId;
    let prevNode;
    if (typingNames.length) {
      prevNode = h('div.cr-prev.typing', null, c.type === 'direct' ? 'печатает' : `${typingNames[0]} печатает`, h('span.typing-dots', null, h('i'), h('i'), h('i')));
    } else {
      const who = last && c.type === 'group' && !last.deleted && last.type !== 'call'
        ? (mine ? 'Вы' : store.user(last.senderId)?.displayName?.split(' ')[0]) : (mine && c.type === 'direct' && !last.deleted ? 'Вы' : null);
      prevNode = h('div.cr-prev', null, who ? h('b', null, who + ': ') : null, last ? store.previewOf(last) : (c.type === 'channel' ? 'Канал' : c.type === 'group' ? 'Группа' : 'Нет сообщений'));
    }
    const read = mine && c.type !== 'saved' && (c.othersReadSeq ?? 0) >= last.seq;
    const title = c.type === 'direct'
      ? nameLine(store.chatTitle(c), { verified: peer?.verified, premium: peer?.isPremium, emoji: peer?.emojiStatus, bot: peer?.isBot })
      : nameLine(store.chatTitle(c), { verified: c.verified || c.isService });
    const time = formatListTime(last?.createdAt ?? c.createdAt);
    const sig = JSON.stringify([c.id === activeId, store.chatTitle(c), c.type, c.verified, c.isService, c.avatarFileId, c.muted, c.pinned, c.unread,
      peer && [peer.displayName, peer.avatarFileId, peer.online, peer.verified, peer.isPremium, peer.emojiStatus, peer.isBot],
      last && [last.id, last.deleted, last.editedAt, last.senderId, store.previewOf(last)], typingNames, read, time,
      c.type === 'group' && last ? store.user(last.senderId)?.displayName : null]);
    const cached = rows.get(c.id);
    if (cached?.sig === sig) return cached.el;
    const fresh = !shown.has(c.id);
    shown.add(c.id);
    const el = h('div.chat-row' + (c.id === activeId ? '.active' : '') + (fresh ? '' : '.still'), {
      style: fresh ? { animationDelay: Math.min(i, 12) * 18 + 'ms' } : null,
      onclick: () => go('/c/' + c.id),
      oncontextmenu: (e) => { e.preventDefault(); rowMenu(e, c); },
    },
    chatAvatar(store, c, 52),
    h('div.cr-mid', null,
      h('div.cr-top', null,
        c.type === 'channel' ? icon('campaign', 'fill') : c.type === 'group' ? icon('group', 'fill') : null,
        h('div.cr-title', null, title),
        h('div.cr-time', null, mine && c.type !== 'saved' && !last.deleted ? icon(read ? 'done_all' : 'check') : null, time)),
      h('div.cr-bot', null, prevNode,
        h('div.cr-badges', null,
          c.muted ? icon('volume_off') : null,
          c.pinned && !c.unread ? icon('keep', 'fill') : null,
          c.unread ? h('span.unread' + (c.muted ? '.muted' : ''), null, c.unread > 999 ? '999+' : c.unread) : null))));
    for (const s of el.querySelectorAll('.cr-top > .ms')) Object.assign(s.style, { fontSize: '18px', color: 'var(--on-surface-variant)' });
    // Аватарку переносим из старой строки, если она не поменялась, — иначе картинка мигает.
    const av = el.firstChild;
    const avKey = JSON.stringify([c.type, c.avatarFileId, c.title, peer?.displayName, peer?.avatarFileId, peer?.online, peer?.isBot]);
    if (cached?.avKey === avKey) av.replaceWith(cached.el.firstChild);
    rows.set(c.id, { sig, el, avKey });
    return el;
  }

  function rowMenu(e, c) {
    const items = [
      { icon: c.pinned ? 'keep_off' : 'keep', label: c.pinned ? 'Открепить' : 'Закрепить', run: () => store.chatSettings(c.id, { pinned: !c.pinned }).catch((x) => toast(errorText(x))) },
      { icon: c.muted ? 'volume_up' : 'volume_off', label: c.muted ? 'Включить звук' : 'Без звука', run: () => store.chatSettings(c.id, { muted: !c.muted }).catch((x) => toast(errorText(x))) },
      c.type !== 'saved' ? { icon: c.archived ? 'unarchive' : 'archive', label: c.archived ? 'Вернуть из архива' : 'В архив', run: () => store.chatSettings(c.id, { archived: !c.archived }).catch((x) => toast(errorText(x))) } : null,
      c.unread ? { icon: 'mark_chat_read', label: 'Отметить прочитанным', run: () => markChatRead(c) } : null,
      '-',
      ...dangerItems(c),
    ];
    menu(e, items);
  }

  function fabMenu(e) {
    menu(e, [
      { icon: 'person_add', label: 'Новый чат', run: () => go('/new/chat') },
      { icon: 'group_add', label: 'Новая группа', run: () => go('/new/group') },
      { icon: 'campaign', label: 'Новый канал', run: () => go('/new/channel') },
      { icon: 'link', label: 'Вступить по ссылке', run: () => joinByLink() },
    ]);
  }

  const offs = [
    store.on('chats', () => { store.loadedOnce = true; render(); }),
    store.on('users', () => render()),
    store.on('typing', () => render()),
    store.on('connected', renderStatus),
  ];
  if (store.chats.length) store.loadedOnce = true;
  render();

  return {
    el,
    destroy() { offs.forEach((f) => f()); },
    onRoute(parts) {
      const id = parts[0] === 'c' ? parts[1] : null;
      if (id === activeId) return;
      activeId = id;
      render();
    },
  };
}

function markChatRead(c) {
  const seq = c.lastMessage?.seq;
  if (!seq) return;
  store.chats = store.chats.map((x) => x.id === c.id ? { ...x, unread: 0, lastReadSeq: seq } : x);
  store.emit('chats');
  api.markRead(c.id, seq).catch(() => {});
}

/** Пункты «удалить / выйти» для чата — общие для списка и экрана чата. */
export function dangerItems(c) {
  const title = store.chatTitle(c);
  if (c.type === 'saved' || c.isService) return [];
  if (c.type === 'direct') {
    return [{
      icon: 'delete', label: 'Удалить чат', danger: true,
      run: () => {
        let forAll = false;
        const cb = h('input', { type: 'checkbox', onchange: (e) => { forAll = e.target.checked; } });
        dialog({
          title: 'Удалить чат?', icon: 'delete',
          body: h('div', null, h('p', null, 'История переписки будет удалена.'), h('label.row.mt16', { style: { cursor: 'pointer' } }, cb, `Удалить также у «${title}»`)),
          actions: [{ label: 'Отмена' }, { label: 'Удалить', primary: true, danger: true, run: () => store.deleteChat(c.id, forAll).catch((e) => toast(errorText(e))) }],
        });
      },
    }];
  }
  const owner = c.myRole === 'owner';
  const out = [];
  if (c.myRole && !owner) {
    out.push({
      icon: 'logout', label: c.type === 'channel' ? 'Отписаться' : 'Покинуть группу', danger: true,
      run: async () => { if (await confirm(c.type === 'channel' ? 'Отписаться от канала?' : 'Покинуть группу?', `«${title}»`, c.type === 'channel' ? 'Отписаться' : 'Выйти', true)) store.leave(c.id).catch((e) => toast(errorText(e))); },
    });
  }
  if (owner) {
    out.push({
      icon: 'delete', label: c.type === 'channel' ? 'Удалить канал' : 'Удалить группу', danger: true,
      run: async () => { if (await confirm('Удалить для всех?', `«${title}» и вся история будут удалены у всех участников.`, 'Удалить', true)) store.deleteChat(c.id, true).catch((e) => toast(errorText(e))); },
    });
  }
  return out;
}

/** Ссылка-приглашение: ryzik://join/<код>, …/join/<код>, @имя или просто код. */
export async function joinByLink() {
  const v = await prompt('Вступить в группу или канал по ссылке', { label: 'Ссылка или @юзернейм', ok: 'Открыть приглашение' });
  if (!v) return;
  const code = parseInvite(v);
  if (!code) { toast('Не похоже на приглашение'); return; }
  go('/join/' + encodeURIComponent(code));
}

export function parseInvite(v) {
  const s = v.trim();
  const m = s.match(/join\/([A-Za-z0-9_-]+)/);
  if (m) return m[1];
  if (/^@[a-zA-Z0-9_]{3,32}$/.test(s)) return s;
  if (/^[A-Za-z0-9_-]{4,}$/.test(s)) return s;
  return null;
}
