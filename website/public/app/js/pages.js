// Остальные экраны: контакты, профиль, настройки, информация о чате, профиль человека,
// комментарии, создание чатов, приглашения, наборы стикеров.
import { store, api, errorText, previewText } from './store.js';
import {
  h, icon, iconBtn, clear, avatar, chatAvatar, nameLine, userNameLine, formatLastSeen, formatTime, formatDay, members, richText,
  dialog, confirm, prompt, field, toast, menu, pickFile, scaleImage, copyText, emptyState, spinner, topBar, listItem, switchEl,
  section, settings, saveSettings, SEEDS, PREMIUM_SEEDS_FROM, sheet,
} from './ui.js';
import { go, back } from './nav.js';
import { dangerItems, joinByLink } from './chats.js';
import { startCall } from './calls.js';

const WEB_VERSION = '1.0';
const subs = (...offs) => () => offs.forEach((f) => f());

// =====================================================================
// Контакты
// =====================================================================

export function contactsTab() {
  const list = h('div.scroll');
  const input = h('input', { placeholder: 'Найти или добавить по имени, @нику', type: 'search' });
  let found = null;
  let seq = 0;
  input.addEventListener('input', () => {
    const q = input.value.trim();
    const my = ++seq;
    if (q.length < 2) { found = null; render(); return; }
    setTimeout(async () => {
      if (my !== seq) return;
      const r = await store.searchUsers(q).catch(() => []);
      if (my === seq) { found = r.filter((u) => u.id !== store.myId); render(); }
    }, 300);
  });

  function userRow(u) {
    const fresh = store.user(u.id) ?? u;
    return listItem({
      leading: avatar(fresh.displayName, fresh.avatarFileId, 48, { online: fresh.online && !fresh.isBot }),
      title: userNameLine(fresh),
      subtitle: fresh.isBot ? 'бот' : formatLastSeen(fresh.online, fresh.lastSeen),
      onClick: () => go('/u/' + u.id),
      trailing: iconBtn('chat', 'Написать', async () => { try { go('/c/' + (await store.openDirect(u.id)).id); } catch (e) { toast(errorText(e)); } }, 'primary'),
    });
  }

  function render() {
    clear(list);
    if (found) {
      if (!found.length) list.append(emptyState('person_search', 'Никого не нашли', 'Проверьте имя или @ник.'));
      found.forEach((u) => list.append(userRow(u)));
      return;
    }
    const online = store.contacts.filter((u) => (store.user(u.id) ?? u).online);
    if (!store.contacts.length) list.append(emptyState('contacts', 'Контактов пока нет', 'Найдите людей по имени или @нику и добавьте их в контакты.'));
    else {
      if (online.length) list.append(h('div.list-head', null, `В сети · ${online.length}`));
      online.forEach((u) => list.append(userRow(u)));
      const rest = store.contacts.filter((u) => !(store.user(u.id) ?? u).online);
      if (rest.length) list.append(h('div.list-head', null, 'Все контакты'));
      rest.forEach((u) => list.append(userRow(u)));
    }
  }

  const el = h('div.screen', null,
    topBar({ title: 'Контакты', large: true, actions: [iconBtn('block', 'Заблокированные', () => go('/s/blocked'))] }),
    h('div.search', null, icon('person_search'), input), list);
  const off = subs(store.on('contacts', render), store.on('users', () => { if (!found) render(); }));
  store.refreshContacts().catch(() => {});
  render();
  return { el, destroy: off };
}

// =====================================================================
// Мой профиль
// =====================================================================

export function profileTab() {
  const body = h('div.scroll');
  const el = h('div.screen', null, topBar({ title: 'Профиль', large: true, actions: [iconBtn('edit', 'Изменить', () => editProfile())] }), body);

  function render() {
    const me = store.me;
    if (!me) return;
    clear(body);
    body.append(profileHeader(me, { editable: true }));
    body.append(section(null,
      listItem({ leading: 'badge', title: me.displayName, subtitle: 'Имя', onClick: () => editProfile() }),
      listItem({ leading: 'alternate_email', title: '@' + me.username, subtitle: 'Имя пользователя', onClick: () => changeUsername(), trailing: iconBtn('content_copy', 'Копировать', () => copyText('@' + me.username)) }),
      listItem({ leading: 'info', title: me.bio || 'Пара слов о себе', subtitle: 'О себе', onClick: () => editProfile() }),
    ));
    body.append(section('Профиль',
      listItem({ leading: 'photo_camera', title: 'Сменить фото', onClick: () => changeAvatar() }),
      me.avatarFileId ? listItem({ leading: 'hide_image', title: 'Удалить фото', onClick: () => store.updateProfile({ avatarFileId: '' }).catch((e) => toast(errorText(e))) }) : null,
      listItem({ leading: 'music_note', title: me.music ? `Музыка: ${me.music.title || 'трек'}` : 'Музыка в профиле', subtitle: me.music ? 'Нажмите, чтобы сменить или убрать' : 'Трек под аватаркой', onClick: () => musicMenu() }),
      listItem({ leading: 'campaign', title: me.featuredChannel ? `Канал: ${me.featuredChannel.title}` : 'Канал в профиле', subtitle: 'Покажите свой канал под аватаркой', onClick: () => featuredChannel() }),
      listItem({ leading: 'bookmark', title: 'Избранное', onClick: () => { const s = store.savedChat(); if (s) go('/c/' + s.id); } }),
    ));
    body.append(section('FLUX и Премиум',
      listItem({ leading: h('span.premium-star', null, icon('star', 'fill')), title: me.isPremium ? 'RyzikChat Премиум' : 'Премиум', subtitle: me.isPremium ? (me.premiumUntil ? `до ${new Date(me.premiumUntil).toLocaleDateString('ru-RU')}` : 'бессрочно') : 'Цвета, эмодзи-статус, большие файлы', onClick: () => go('/s/flux') }),
      listItem({ leading: 'toll', title: `${me.flux ?? 0} FLUX`, subtitle: 'Баланс, подарки и покупки', onClick: () => go('/s/flux') }),
    ));
    body.append(h('div.web-note', null, '🔒 Сквозное шифрование. Отпечаток ключа можно сверить с собеседником в настройках.'));
  }

  async function changeAvatar() {
    const [f] = await pickFile('image/*');
    if (!f) return;
    try {
      const blob = await scaleImage(f, 640, 'image/jpeg', true);
      const id = await store.uploadPublic(blob, 'image/jpeg');
      await store.updateProfile({ avatarFileId: id });
      toast('Фото обновлено');
    } catch (e) { toast(errorText(e)); }
  }

  function musicMenu() {
    menu(event, [
      { icon: 'upload', label: 'Загрузить трек', run: async () => {
        const [f] = await pickFile('audio/*');
        if (!f) return;
        try {
          toast('Загружаем трек…');
          const id = await store.uploadPublic(f, f.type || 'audio/mpeg');
          store.rememberUsers([await api.updateMe({ musicFileId: id, musicTitle: f.name.replace(/\.[^.]+$/, '').slice(0, 100) })]);
          toast('Музыка добавлена в профиль');
        } catch (e) { toast(errorText(e)); }
      } },
      store.me.music ? { icon: 'delete', label: 'Убрать музыку', danger: true, run: () => api.updateMe({ musicFileId: null, musicTitle: '' }).then((u) => store.rememberUsers([u])).catch((e) => toast(errorText(e))) } : null,
    ]);
  }

  function featuredChannel() {
    const mine = store.chats.filter((c) => c.type === 'channel' && (c.myRole === 'owner' || c.myRole === 'admin'));
    const box = h('div');
    const s = sheet({ title: 'Канал в профиле', body: box });
    if (!mine.length) box.append(h('p.pad.muted', null, 'У вас пока нет своих каналов.'));
    for (const c of mine) box.append(h('div.chat-row', { onclick: () => { api.updateMe({ featuredChatId: c.id }).then((u) => store.rememberUsers([u])).catch((e) => toast(errorText(e))); s.close(); } }, chatAvatar(store, c, 44), h('div.cr-mid', null, h('div.cr-title', null, c.title))));
    if (store.me.featuredChannel) box.append(listItem({ leading: 'close', title: 'Не показывать канал', danger: true, onClick: () => { api.updateMe({ featuredChatId: null }).then((u) => store.rememberUsers([u])).catch((e) => toast(errorText(e))); s.close(); } }));
  }

  const off = subs(store.on('me', render));
  store.refreshMe().catch(() => {});
  render();
  return { el, destroy: off };
}

function editProfile() {
  const me = store.me;
  const name = field('Имя', { value: me.displayName, maxlength: 64 });
  const bio = field('О себе', { value: me.bio ?? '', multiline: true, maxlength: 300 });
  dialog({
    title: 'Изменить профиль', body: h('div', null, name.el, bio.el),
    actions: [{ label: 'Отмена' }, { label: 'Сохранить', primary: true, run: () => store.updateProfile({ displayName: name.input.value.trim() || me.displayName, bio: bio.input.value.trim() }) }],
  });
}

function changeUsername() {
  const u = field('Новое имя пользователя', { value: store.me.username, maxlength: 32 });
  const p = field('Текущий пароль', { type: 'password', autocomplete: 'current-password' });
  dialog({
    title: 'Имя пользователя',
    body: h('div', null, h('p', null, 'Пароль нужен, чтобы перешифровать ключи аккаунта для нового имени.'), u.el, p.el),
    actions: [{ label: 'Отмена' }, { label: 'Сохранить', primary: true, run: async () => { await store.changeUsername(u.input.value, p.input.value); toast('Имя пользователя изменено'); } }],
  });
}

/** Шапка профиля с оформлением Премиума. */
function profileHeader(u, { editable = false, status = null } = {}) {
  const st = u.profileStyle;
  const styled = !!(st && (st.color1 || st.color2 || st.bannerFileId));
  const head = h('div.profile-head' + (styled ? '.styled' : ''), styled ? { style: { '--pc1': st.color1 || st.color2 || 'var(--primary)', '--pc2': st.color2 || st.color1 || 'var(--tertiary)' } } : null);
  if (styled && st.bannerFileId) head.append(h('div.ph-banner', { style: { backgroundImage: `url("${api.avatarUrl(st.bannerFileId)}")` } }));
  if (styled && st.pattern) head.append(h('div.ph-pattern', null, Array.from({ length: 30 }, () => h('span', null, st.pattern))));
  const av = avatar(u.displayName, u.avatarFileId, 112, { online: false });
  if (u.avatarFileId) av.onclick = () => import('./chat.js').then((m) => m.openViewer(api.avatarUrl(u.avatarFileId), false, { name: 'avatar.jpg' }));
  head.append(av,
    h('div.ph-name', { style: st?.nameColor ? { color: st.nameColor } : null }, userNameLine(u)),
    h('div.ph-sub', null, status ?? '@' + u.username));
  if (u.music) head.append(musicRow(u.music));
  const badges = u.badges ?? [];
  const wrap = h('div', null, head);
  if (badges.length) wrap.append(h('div.badges-row', { style: { marginTop: '10px' } }, badges.map((b) => h('span.badge-chip', { style: { '--bc': b.color }, title: b.description }, b.emoji, ' ', b.title))));
  void editable;
  return wrap;
}

let profileAudio = null;
function musicRow(music) {
  const btn = h('button.play', { title: 'Слушать' }, icon('play_arrow', 'fill'));
  btn.onclick = () => {
    if (profileAudio && profileAudio.dataset.id === music.fileId) {
      if (profileAudio.paused) profileAudio.play(); else profileAudio.pause();
      return;
    }
    profileAudio?.pause();
    profileAudio = new Audio(api.avatarUrl(music.fileId));
    profileAudio.dataset.id = music.fileId;
    profileAudio.onplay = () => { btn.firstChild.textContent = 'pause'; };
    profileAudio.onpause = profileAudio.onended = () => { btn.firstChild.textContent = 'play_arrow'; };
    profileAudio.play().catch(() => toast('Не удалось воспроизвести'));
  };
  return h('div.music-row', null, btn, icon('music_note'), h('span', null, music.title || 'Музыка'));
}

// =====================================================================
// Профиль другого человека
// =====================================================================

export function userProfile(userId) {
  const body = h('div.scroll');
  const el = h('div.screen', null, topBar({ title: 'Профиль', back: () => back(), actions: [iconBtn('more_vert', 'Ещё', (e) => more(e))] }), body);
  let gifts = null;

  const u = () => store.user(userId);
  const direct = () => store.chats.find((c) => c.type === 'direct' && c.members.some((m) => m.user.id === userId));

  async function openChat() { try { go('/c/' + (await store.openDirect(userId)).id); } catch (e) { toast(errorText(e)); } }

  function more(e) {
    const user = u();
    if (!user || userId === store.myId) return;
    const c = direct();
    menu(e, [
      { icon: user.isContact || c?.peerIsContact ? 'person_remove' : 'person_add', label: user.isContact || c?.peerIsContact ? 'Удалить из контактов' : 'Добавить в контакты', run: () => store.setContact(userId, !(user.isContact || c?.peerIsContact)).then(render).catch((x) => toast(errorText(x))) },
      { icon: 'key', label: 'Ключ шифрования', run: () => showFingerprint(user) },
      { icon: 'redeem', label: 'Подарить подарок', run: () => giftShop(user) },
      { icon: 'content_copy', label: 'Копировать @имя', run: () => copyText('@' + user.username) },
      '-',
      { icon: 'block', label: user.isBlocked || c?.peerBlocked ? 'Разблокировать' : 'Заблокировать', danger: !(user.isBlocked || c?.peerBlocked), run: () => store.setBlocked(userId, !(user.isBlocked || c?.peerBlocked)).then(render).catch((x) => toast(errorText(x))) },
    ]);
  }

  function render() {
    const user = u();
    clear(body);
    if (!user) { body.append(spinner()); return; }
    const status = user.isBot ? 'бот' : user.isService ? 'официальный аккаунт' : formatLastSeen(user.online, user.lastSeen);
    body.append(profileHeader(user, { status }));
    if (userId !== store.myId) {
      const blocked = user.isBlocked || direct()?.peerBlocked;
      body.append(h('div.profile-actions', null,
        h('button.pa-btn', { onclick: openChat }, icon('chat', 'fill'), 'Написать'),
        !user.isBot && !user.isService && !blocked ? h('button.pa-btn', { onclick: () => startCall(userId, false) }, icon('call', 'fill'), 'Звонок') : null,
        !user.isBot && !user.isService && !blocked ? h('button.pa-btn', { onclick: () => startCall(userId, true) }, icon('videocam', 'fill'), 'Видео') : null,
        !user.isBot ? h('button.pa-btn', { onclick: () => giftShop(user) }, icon('redeem', 'fill'), 'Подарок') : null,
      ));
    }
    body.append(section(null,
      listItem({ leading: 'alternate_email', title: '@' + user.username, subtitle: 'Имя пользователя', onClick: () => copyText('@' + user.username) }),
      user.bio ? listItem({ leading: 'info', title: h('span', { style: { whiteSpace: 'pre-wrap' } }, richText(user.bio)), subtitle: 'О себе' }) : null,
      user.messagePrice && !user.isContact ? listItem({ leading: 'toll', title: `${user.messagePrice} FLUX`, subtitle: 'Стоимость сообщения, если вы не в контактах' }) : null,
      !user.isBot ? listItem({ leading: 'key', title: 'Ключ шифрования', subtitle: 'Сверьте отпечаток с собеседником', onClick: () => showFingerprint(user) }) : null,
    ));
    if (user.featuredChannel) {
      const fc = user.featuredChannel;
      body.append(section('Канал', listItem({
        leading: avatar(fc.title, fc.avatarFileId, 44), title: nameLine(fc.title, { verified: fc.verified }), subtitle: members(fc.memberCount, true),
        onClick: async () => {
          try {
            const c = fc.username ? await api.chatByUsername(fc.username) : fc.inviteCode ? await api.invitePreview(fc.inviteCode) : await api.chat(fc.id);
            store.previewChat(c);
            go('/c/' + c.id);
          } catch (e) { toast(errorText(e)); }
        },
      })));
    }
    if (gifts?.length) {
      body.append(h('div.group-title', { style: { padding: '14px 24px 4px' } }, `Подарки · ${gifts.length}`));
      body.append(h('div.gifts-grid', null, gifts.map((g) => giftCard(g, user, loadGifts))));
    }
  }

  const off = subs(store.on('users', render), store.on('chats', render));
  render();
  store.loadUser(userId).then(render).catch((e) => toast(errorText(e)));
  const loadGifts = () => api.userGifts(userId).then((g) => { gifts = g; render(); }).catch(() => {});
  loadGifts();
  return { el, destroy: off };
}

function giftCard(g, owner, reload = () => {}) {
  const it = g.item ?? {};
  const mine = owner.id === store.myId;
  const act = (fn, done) => async () => { try { await fn(); await store.refreshMe(); if (done) toast(done); reload(); } catch (e) { toast(errorText(e)); } };
  return h('div.gift-card', {
    onclick: () => dialog({
      title: it.title || 'Подарок',
      body: h('div.center', null,
        it.fileId ? h('img', { src: api.avatarUrl(it.fileId), alt: '', style: { width: '160px', height: '160px', objectFit: 'contain', margin: '0 auto' } }) : h('div', { style: { fontSize: '96px' } }, it.emoji || '🎁'),
        h('p', null, `№ ${g.serial}${it.supply ? ' из ' + it.supply : ''}`),
        g.from ? h('p', null, `От: ${g.from.displayName}`) : null,
        g.message ? h('p', null, `«${g.message}»`) : null,
        it.description ? h('p.hint', null, it.description) : null,
        g.resalePrice ? h('p', null, `Продаётся за ${g.resalePrice} FLUX`) : null),
      actions: [
        mine ? { label: g.pinned ? 'Открепить' : 'Закрепить', run: act(() => api.patch(`/api/gifts/${g.id}`, { pinned: !g.pinned })) } : null,
        mine && !g.resalePrice ? { label: 'Продать пользователям', run: act(async () => {
          const v = await prompt('Продать пользователям', { label: 'Цена в FLUX', value: String(it.price ?? ''), type: 'number', ok: 'Выставить', hint: 'Подарок появится с ценой в вашем профиле. Когда его купят, FLUX придут вам.' });
          if (v != null && Number(v) > 0) await api.patch(`/api/gifts/${g.id}`, { resalePrice: Number(v) });
        }) } : null,
        mine && g.resalePrice ? { label: 'Снять с продажи', run: act(() => api.patch(`/api/gifts/${g.id}`, { resalePrice: null })) } : null,
        !mine && g.resalePrice ? { label: `Купить за ${g.resalePrice} FLUX`, run: act(async () => {
          if (await confirm('Купить подарок?', `Спишется ${g.resalePrice} FLUX, подарок перейдёт к вам.`, 'Купить')) await api.post(`/api/gifts/${g.id}/buy`, { price: g.resalePrice });
        }, 'Подарок ваш 🎉') } : null,
        mine && g.sellPrice ? { label: `Продать за ${g.sellPrice} FLUX`, run: async () => { if (await confirm('Продать подарок?', `Вы получите ${g.sellPrice} FLUX.`, 'Продать')) { await api.post(`/api/gifts/${g.id}/sell`); await store.refreshMe(); toast('Подарок продан'); } } } : null,
        { label: 'Закрыть', primary: true },
      ].filter(Boolean),
    }),
  }, it.fileId ? h('img', { src: api.avatarUrl(it.fileId), alt: '', loading: 'lazy' }) : h('div', { style: { fontSize: '56px' } }, it.emoji || '🎁'), h('div.gc-t', null, it.title || 'Подарок'), h('div.muted', null, (g.pinned ? '📌 ' : '') + '№ ' + g.serial + (g.resalePrice ? ` · ${g.resalePrice} FLUX` : '')));
}

async function giftShop(toUser) {
  const box = h('div', null, spinner());
  const s = sheet({ title: toUser ? `Подарок для ${toUser.displayName}` : 'Магазин подарков', body: box });
  try {
    const items = await api.giftShop();
    clear(box);
    box.append(h('p.pad.muted', { style: { paddingTop: 0 } }, `Ваш баланс: ${store.me?.flux ?? 0} FLUX`));
    if (!items.length) box.append(h('p.pad.muted', null, 'Подарков пока нет.'));
    box.append(h('div.gifts-grid', null, items.map((it) => h('div.gift-card', {
      onclick: () => {
        const msg = field('Подпись (необязательно)', { maxlength: 200 });
        dialog({
          title: `${it.title} · ${it.price} FLUX`,
          body: h('div', null, it.description ? h('p', null, it.description) : null, it.left != null ? h('p.hint', null, `Осталось: ${it.left}`) : null, msg.el),
          actions: [{ label: 'Отмена' }, { label: 'Купить', primary: true, run: async () => {
            await api.buyGift(it.id, toUser?.id ?? null, msg.input.value.trim());
            await store.refreshMe();
            s.close();
            toast(toUser ? 'Подарок отправлен 🎉' : 'Подарок куплен 🎉');
          } }],
        });
      },
    }, it.fileId ? h('img', { src: api.avatarUrl(it.fileId), alt: '', loading: 'lazy' }) : h('div', { style: { fontSize: '56px' } }, it.emoji || '🎁'),
    h('div.gc-t', null, it.title), h('div', { style: { color: 'var(--primary)', fontWeight: 800 } }, `${it.price} FLUX`)))));
  } catch (e) { clear(box).append(h('p.pad', null, errorText(e))); }
}

async function showFingerprint(user) {
  const [mine, theirs] = await Promise.all([store.myFingerprint(), store.fingerprintOf(user)]);
  dialog({
    title: 'Ключ шифрования', icon: 'key',
    body: h('div', null,
      h('p', null, `Сверьте эти числа с ${user.displayName} при встрече или по звонку. Если совпадают — переписку никто не подменяет.`),
      h('div.group-title', null, user.displayName), h('div.fp', null, theirs),
      h('div.group-title', null, 'Вы'), h('div.fp', null, mine)),
    actions: [{ label: 'Закрыть', primary: true }],
  });
}

// =====================================================================
// Настройки
// =====================================================================

export function settingsTab() {
  const body = h('div.scroll');
  const el = h('div.screen', null, topBar({ title: 'Настройки', large: true }), body);

  function aiSettings() {
    const url = field('Ссылка на API', { value: settings.aiUrl || '', placeholder: 'https://api.example.com/v1' });
    const key = field('API-ключ', { value: settings.aiKey || '', type: 'password' });
    const model = field('Модель', { value: settings.aiModel || 'gpt-4o-mini', placeholder: 'gpt-4o-mini' });
    dialog({
      title: 'AI-помощник',
      icon: 'auto_awesome',
      wide: true,
      body: h('div', null, url.el, key.el, model.el,
        h('p.hint', null, 'Любой OpenAI-совместимый API. Ключ хранится только в этом браузере. Кнопка AI появляется в чате, когда текста становится три строки.')),
      actions: [
        { label: 'Отмена' },
        { label: 'Сохранить', primary: true, run: () => {
          saveSettings({
            aiUrl: String(url.input.value || '').trim(),
            aiKey: String(key.input.value || '').trim(),
            aiModel: String(model.input.value || '').trim() || 'gpt-4o-mini',
          });
          render();
        } },
      ],
    });
  }

  function render() {
    const me = store.me;
    if (!me) return;
    clear(body);
    body.append(h('div.list-item.clickable', { onclick: () => go('/profile'), style: { padding: '8px 20px 16px' } },
      avatar(me.displayName, me.avatarFileId, 64), h('div.li-text', null, h('div.li-title', { style: { fontSize: '20px', fontWeight: 800 } }, userNameLine(me)), h('div.li-sub', null, '@' + me.username))));

    const themeTitle = { system: 'Как в системе', light: 'Светлая', dark: 'Тёмная' };
    body.append(section('Оформление',
      listItem({ leading: 'ac_unit', title: 'Сезонное оформление', subtitle: 'Снег и зимние акценты — только у вас', trailing: switchEl(settings.seasonal !== false, (v) => { saveSettings({ seasonal: v }); const snow = document.getElementById('snow-layer'); if (snow) snow.style.display = v ? 'block' : 'none'; render(); }) }),
      listItem({ leading: 'auto_awesome', title: 'AI-помощник', subtitle: settings.aiUrl ? 'Подключён' : 'Ссылка и ключ', onClick: aiSettings }),
      listItem({ leading: 'dark_mode', title: 'Тема', subtitle: themeTitle[settings.theme], onClick: (e) => menu(e, Object.entries(themeTitle).map(([k, v]) => ({ icon: settings.theme === k ? 'radio_button_checked' : 'radio_button_unchecked', label: v, run: () => { saveSettings({ theme: k }); render(); } }))) }),
      h('div', null, h('div.li-sub', { style: { padding: '12px 16px 0', fontWeight: 700 } }, 'Цвет приложения'), seedPicker(render)),
      listItem({ leading: 'format_size', title: 'Размер текста', subtitle: `${Math.round(settings.fontScale * 100)}%`, trailing: h('input.slider', { type: 'range', min: 0.85, max: 1.3, step: 0.05, value: settings.fontScale, style: { width: '140px' }, oninput: (e) => saveSettings({ fontScale: Number(e.target.value) }), onchange: () => render() }) }),
      listItem({ leading: 'rounded_corner', title: 'Скругление сообщений', subtitle: `${settings.radius} px`, trailing: h('input.slider', { type: 'range', min: 6, max: 26, step: 1, value: settings.radius, style: { width: '140px' }, oninput: (e) => saveSettings({ radius: Number(e.target.value) }), onchange: () => render() }) }),
    ));
    body.append(section('Чаты',
      listItem({ leading: 'mood', title: 'Крупные эмодзи', subtitle: 'Сообщение только из эмодзи — без пузыря', trailing: switchEl(settings.bigEmoji, (v) => saveSettings({ bigEmoji: v })) }),
      listItem({ leading: 'keyboard_return', title: 'Отправка по Enter', subtitle: 'Shift+Enter — новая строка', trailing: switchEl(settings.enterToSend, (v) => saveSettings({ enterToSend: v })) }),
      listItem({ leading: 'sticky_note', title: 'Стикеры', subtitle: `Наборов: ${store.stickerPacks.length}`, onClick: () => go('/s/stickers') }),
      listItem({ leading: 'archive', title: 'Архив', subtitle: `Чатов: ${store.chats.filter((c) => c.archived).length}`, onClick: () => go('/chats') }),
    ));
    body.append(section('Уведомления',
      listItem({ leading: 'notifications', title: 'Уведомления', subtitle: 'Когда вкладка свёрнута', trailing: switchEl(settings.notifications, (v) => { saveSettings({ notifications: v }); if (v && 'Notification' in window) Notification.requestPermission().catch(() => {}); }) }),
      listItem({ leading: 'volume_up', title: 'Звук сообщений', trailing: switchEl(settings.sounds, (v) => saveSettings({ sounds: v })) }),
    ));
    const callTitle = { all: 'Все', contacts: 'Только контакты', nobody: 'Никто' };
    body.append(section('Конфиденциальность',
      listItem({ leading: 'call', title: 'Кто может звонить', subtitle: callTitle[me.callPrivacy ?? 'all'], onClick: (e) => menu(e, Object.entries(callTitle).map(([k, v]) => ({ icon: (me.callPrivacy ?? 'all') === k ? 'radio_button_checked' : 'radio_button_unchecked', label: v, run: () => api.updateMe({ callPrivacy: k }).then(() => store.refreshMe()).catch((x) => toast(errorText(x))) }))) }),
      listItem({ leading: 'toll', title: 'Платные сообщения', subtitle: me.messagePrice ? `${me.messagePrice} FLUX за сообщение от незнакомцев` : 'Выключено', onClick: async () => {
        const v = await prompt('Платные сообщения', { label: 'Цена в FLUX (0 — бесплатно)', value: String(me.messagePrice ?? 0), type: 'number', hint: 'Люди не из ваших контактов заплатят эту сумму за каждое сообщение вам.' });
        if (v != null) api.updateMe({ messagePrice: Math.max(0, Math.floor(Number(v) || 0)) }).then(() => store.refreshMe()).catch((x) => toast(errorText(x)));
      } }),
      listItem({ leading: 'block', title: 'Заблокированные', onClick: () => go('/s/blocked') }),
      listItem({ leading: 'devices', title: 'Устройства', subtitle: 'Где выполнен вход', onClick: () => go('/s/sessions') }),
      listItem({ leading: 'shield_lock', title: 'Двухэтапная проверка', subtitle: me.has2fa ? 'Включена' : 'Выключена', onClick: () => twofa() }),
      listItem({ leading: 'password', title: 'Сменить пароль', onClick: () => changePassword() }),
      listItem({ leading: 'key', title: 'Мой ключ шифрования', onClick: async () => dialog({ title: 'Мой ключ шифрования', icon: 'key', body: h('div', null, h('p', null, 'Отпечаток вашего ключа. Собеседник видит такой же у себя в вашем профиле.'), h('div.fp', null, await store.myFingerprint())), actions: [{ label: 'Закрыть', primary: true }] }) }),
    ));
    body.append(section('Аккаунт',
      listItem({ leading: 'alternate_email', title: 'Имя пользователя', subtitle: '@' + me.username, onClick: () => changeUsername() }),
      listItem({ leading: 'toll', title: 'FLUX и Премиум', subtitle: `${me.flux ?? 0} FLUX`, onClick: () => go('/s/flux') }),
      listItem({ leading: 'link', title: 'Вступить по ссылке', onClick: () => joinByLink() }),
      listItem({ leading: 'logout', title: 'Выйти', danger: true, onClick: async () => { if (await confirm('Выйти из аккаунта?', 'Чтобы войти снова, понадобятся имя пользователя и пароль.', 'Выйти', true)) store.logout(); } }),
      listItem({ leading: 'delete_forever', title: 'Удалить аккаунт', danger: true, onClick: () => deleteAccount() }),
    ));
    body.append(section('О приложении',
      listItem({ leading: h('img', { src: '/app/icon.svg', alt: '', style: { width: '28px', height: '28px', borderRadius: '9px' } }), title: 'RyzikChat Web', subtitle: `Версия ${WEB_VERSION} · ${store.connected ? 'на связи с сервером' : 'без соединения в реальном времени'}` }),
      listItem({ leading: 'android', title: 'Приложение для Android', subtitle: 'Скачать последнюю версию', onClick: () => open('/download', '_blank') }),
      listItem({ leading: 'description', title: 'Правила использования', onClick: () => import('./auth.js').then((m) => m.showTerms()) }),
    ));
    body.append(h('div.web-note', null, 'Сообщения шифруются прямо в браузере. Ключ хранится только на этом устройстве.'));
  }

  const off = subs(store.on('me', render), store.on('connected', render), store.on('stickers', render));
  render();
  return { el, destroy: off };
}

function seedPicker(onChange) {
  return h('div.seed-grid', null, SEEDS.map(([c, name], i) => {
    const locked = i >= PREMIUM_SEEDS_FROM && !store.me?.isPremium;
    return h('div.seed' + (settings.seed === i ? '.on' : '') + (locked ? '.locked' : ''), {
      onclick: () => { if (locked) { toast('Этот цвет — для Премиума'); return; } saveSettings({ seed: i }); onChange(); },
    }, h('i', { style: { '--c': c } }, settings.seed === i ? icon('check') : locked ? icon('lock') : null), name);
  }));
}

function changePassword() {
  const old = field('Текущий пароль', { type: 'password', autocomplete: 'current-password' });
  const p1 = field('Новый пароль', { type: 'password', autocomplete: 'new-password' });
  const p2 = field('Повторите новый пароль', { type: 'password', autocomplete: 'new-password' });
  dialog({
    title: 'Сменить пароль', body: h('div', null, old.el, p1.el, p2.el),
    actions: [{ label: 'Отмена' }, { label: 'Сменить', primary: true, run: async () => {
      if (p1.input.value.length < 8) throw new Error('Пароль — минимум 8 символов');
      if (p1.input.value !== p2.input.value) throw new Error('Пароли не совпадают');
      await store.changePassword(old.input.value, p1.input.value);
      toast('Пароль изменён');
    } }],
  });
}

function twofa() {
  const me = store.me;
  if (me.has2fa) {
    menu(event, [
      { icon: 'edit', label: 'Сменить дополнительный пароль', run: () => set2fa(true) },
      { icon: 'lock_open', label: 'Выключить', danger: true, run: async () => {
        const p = await prompt('Выключить двухэтапную проверку', { label: 'Дополнительный пароль', type: 'password', ok: 'Выключить' });
        if (p) store.disable2fa(p).then(() => toast('Двухэтапная проверка выключена')).catch((e) => toast(errorText(e)));
      } },
    ]);
  } else set2fa(false);
}

function set2fa(change) {
  const acc = field('Пароль от аккаунта', { type: 'password', autocomplete: 'current-password' });
  const cur = change ? field('Текущий дополнительный пароль', { type: 'password' }) : null;
  const p1 = field('Дополнительный пароль', { type: 'password', autocomplete: 'new-password' });
  const hint = field('Подсказка (необязательно)', { maxlength: 64 });
  dialog({
    title: 'Двухэтапная проверка', icon: 'shield_lock',
    body: h('div', null, h('p', null, 'При входе с нового устройства понадобится ещё и этот пароль.'), acc.el, cur?.el, p1.el, hint.el),
    actions: [{ label: 'Отмена' }, { label: 'Сохранить', primary: true, run: async () => {
      if (p1.input.value.length < 4) throw new Error('Дополнительный пароль — минимум 4 символа');
      await store.set2fa(acc.input.value, cur ? cur.input.value : null, p1.input.value, hint.input.value);
      toast('Двухэтапная проверка включена');
    } }],
  });
}

function deleteAccount() {
  const p = field('Пароль', { type: 'password', autocomplete: 'current-password' });
  dialog({
    title: 'Удалить аккаунт навсегда?', icon: 'delete_forever',
    body: h('div', null, h('p', null, 'Профиль, чаты и сообщения будут удалены без возможности восстановления.'), p.el),
    actions: [{ label: 'Отмена' }, { label: 'Удалить', primary: true, danger: true, run: () => store.deleteAccount(p.input.value) }],
  });
}

/** Подстраницы настроек: #/s/<name>. */
export function settingsPage(name) {
  const body = h('div.scroll');
  const titles = { sessions: 'Устройства', blocked: 'Заблокированные', stickers: 'Стикеры', flux: 'FLUX и Премиум' };
  const el = h('div.screen', null, topBar({ title: titles[name] ?? 'Настройки', back: () => back('/settings') }), body);
  const load = async () => {
    clear(body).append(spinner());
    try {
      if (name === 'sessions') {
        const list = await api.sessions();
        clear(body);
        body.append(section('Это устройство', ...list.filter((s) => s.current).map((s) => listItem({ leading: 'computer', title: s.device || 'Неизвестно', subtitle: `с ${new Date(s.createdAt).toLocaleString('ru-RU')}` }))));
        const others = list.filter((s) => !s.current);
        if (others.length) {
          body.append(section('Другие устройства', ...others.map((s) => listItem({ leading: /android|phone/i.test(s.device) ? 'smartphone' : 'devices', title: s.device || 'Неизвестно', subtitle: `с ${new Date(s.createdAt).toLocaleString('ru-RU')}` }))));
          body.append(h('div.pad', null, h('button.tonal-btn.btn-block', { onclick: async () => { if (await confirm('Завершить другие сеансы?', 'На всех других устройствах придётся войти заново.', 'Завершить', true)) { await api.terminateOtherSessions().catch((e) => toast(errorText(e))); load(); } } }, icon('logout'), 'Завершить все другие сеансы')));
        }
      } else if (name === 'blocked') {
        const list = await api.blocks();
        store.rememberUsers(list);
        clear(body);
        if (!list.length) body.append(emptyState('block', 'Никого нет', 'Заблокированные люди не смогут писать и звонить вам.'));
        for (const u of list) body.append(listItem({ leading: avatar(u.displayName, u.avatarFileId, 44), title: u.displayName, subtitle: '@' + u.username, trailing: h('button.text-btn', { onclick: () => store.setBlocked(u.id, false).then(load).catch((e) => toast(errorText(e))) }, 'Разблокировать') }));
      } else if (name === 'stickers') {
        await store.refreshStickers();
        clear(body);
        if (!store.stickerPacks.length) body.append(emptyState('sticky_note', 'Наборов нет', 'Откройте ссылку на набор стикеров в чате, чтобы добавить его.'));
        for (const p of store.stickerPacks) {
          body.append(listItem({
            leading: p.stickers[0] ? h('img', { src: api.avatarUrl(p.stickers[0].fileId), alt: '', style: { width: '44px', height: '44px', objectFit: 'contain' } }) : 'sticky_note',
            title: p.title, subtitle: `${p.stickers.length} стикеров${p.isMine ? ' · ваш набор' : ''}`, onClick: () => go('/stickers/' + p.id),
            trailing: iconBtn('remove_circle', 'Убрать', () => store.setPackAdded(p.id, false).then(load).catch((e) => toast(errorText(e)))),
          }));
        }
      } else if (name === 'flux') {
        const [info] = await Promise.all([api.flux(), store.refreshMe()]);
        const me = store.me;
        clear(body);
        body.append(h('div.flux-card', null, h('div', null, 'Баланс'), h('div.fb', null, `${info.balance} FLUX`),
          h('div', { style: { opacity: .9, marginTop: '6px' } }, me.isPremium ? `Премиум ${me.premiumUntil ? 'до ' + new Date(me.premiumUntil).toLocaleDateString('ru-RU') : 'бессрочно'}` : 'Премиум не подключён')));
        body.append(section('Премиум',
          listItem({ leading: h('span.premium-star', null, icon('star', 'fill')), title: `Премиум на месяц — ${info.premiumMonthPrice} FLUX`, subtitle: 'Цвета профиля, эмодзи-статус, премиум-цвета темы, файлы до 2 ГБ', onClick: async () => {
            if (await confirm('Купить Премиум?', `С баланса спишется ${info.premiumMonthPrice} FLUX.`, 'Купить')) { await api.buyPremium(1).catch((e) => toast(errorText(e))); load(); }
          } }),
          me.isPremium ? listItem({ leading: 'palette', title: 'Эмодзи-статус', subtitle: me.emojiStatus || 'Не выбран', onClick: async () => {
            const v = await prompt('Эмодзи-статус', { label: 'Эмодзи (пусто — убрать)', value: me.emojiStatus ?? '', ok: 'Сохранить' });
            if (v != null) api.updateMe({ emojiStatus: v.trim(), profileStyle: me.profileStyle ?? null }).then(() => store.refreshMe()).catch((e) => toast(errorText(e)));
          } }) : null,
          listItem({ leading: 'redeem', title: 'Магазин подарков', onClick: () => giftShop(null) }),
          listItem({ leading: 'chat', title: 'Как получить FLUX', subtitle: 'Напишите владельцу в Telegram: @ryzik3489', onClick: () => open('https://t.me/ryzik3489', '_blank') }),
        ));
        if (info.history?.length) {
          body.append(section('История', ...info.history.slice(0, 50).map((t) => listItem({
            leading: t.amount > 0 ? 'add_circle' : 'remove_circle', title: `${t.amount > 0 ? '+' : ''}${t.amount} FLUX`, subtitle: `${t.note || t.kind} · ${formatDay(t.createdAt)} ${formatTime(t.createdAt)}`,
          }))));
        }
      }
    } catch (e) { clear(body).append(h('p.pad', null, errorText(e))); }
  };
  load();
  return { el };
}

// =====================================================================
// Информация о группе или канале
// =====================================================================

export function chatInfo(chatId) {
  const body = h('div.scroll');
  const el = h('div.screen', null, topBar({ title: 'Информация', back: () => back('/c/' + chatId) }), body);

  function render() {
    const c = store.chat(chatId);
    clear(body);
    if (!c) { body.append(spinner()); return; }
    const admin = c.myRole === 'owner' || c.myRole === 'admin';
    const channel = c.type === 'channel';
    const head = h('div.profile-head', null,
      (() => { const a = chatAvatar(store, c, 112); if (admin) { a.title = 'Сменить фото'; a.onclick = () => changeChatAvatar(c); } return a; })(),
      h('div.ph-name', null, nameLine(c.title, { verified: c.verified })),
      h('div.ph-sub', null, members(c.memberCount || c.members.length, channel)));
    body.append(head);
    body.append(h('div.profile-actions', null,
      h('button.pa-btn', { onclick: () => go('/c/' + chatId) }, icon(channel ? 'campaign' : 'chat', 'fill'), channel ? 'Канал' : 'Чат'),
      c.myRole ? h('button.pa-btn', { onclick: () => store.chatSettings(chatId, { muted: !c.muted }).catch((e) => toast(errorText(e))) }, icon(c.muted ? 'notifications_off' : 'notifications', 'fill'), c.muted ? 'Без звука' : 'Звук') : null,
      admin ? h('button.pa-btn', { onclick: () => editChat(c) }, icon('edit', 'fill'), 'Изменить') : null,
      !c.myRole ? h('button.pa-btn', { onclick: () => store.subscribe(chatId).catch((e) => toast(errorText(e))) }, icon('add', 'fill'), channel ? 'Подписаться' : 'Вступить') : null,
    ));
    body.append(section(null,
      c.description ? listItem({ leading: 'info', title: h('span', { style: { whiteSpace: 'pre-wrap' } }, richText(c.description)), subtitle: 'Описание' }) : null,
      c.username ? listItem({ leading: 'alternate_email', title: '@' + c.username, subtitle: 'Публичная ссылка', onClick: () => copyText('@' + c.username) }) : null,
      c.inviteCode ? listItem({ leading: 'link', title: `ryzik://join/${c.inviteCode}`, subtitle: 'Ссылка-приглашение', onClick: () => copyText(`ryzik://join/${c.inviteCode}`), trailing: admin ? iconBtn('refresh', 'Сбросить ссылку', async () => { if (await confirm('Сбросить ссылку?', 'Старая ссылка перестанет работать.', 'Сбросить', true)) store.resetInvite(chatId).catch((e) => toast(errorText(e))); }) : null }) : null,
      admin ? listItem({ leading: 'public', title: c.isPublic ? (channel ? 'Публичный канал' : 'Открытая группа') : (channel ? 'Частный канал' : 'Частная группа'), subtitle: c.isPublic ? 'Находится в поиске' : 'Вступить можно только по ссылке', trailing: switchEl(c.isPublic, (v) => store.updateChat(chatId, { isPublic: v }).catch((e) => toast(errorText(e)))) }) : null,
      admin && c.isPublic ? listItem({ leading: 'edit', title: 'Публичное имя', subtitle: c.username ? '@' + c.username : 'Не задано', onClick: async () => {
        const v = await prompt('Публичное имя', { label: '@юзернейм', value: c.username ?? '', hint: 'Латиница, цифры и _, от 3 до 32 символов.' });
        if (v != null) store.updateChat(chatId, { username: v.trim().replace(/^@/, '') }).catch((e) => toast(errorText(e)));
      } }) : null,
    ));
    if (channel) {
      body.append(section('Обсуждение',
        c.discussionId ? listItem({ leading: 'forum', title: 'Группа с комментариями', subtitle: store.chat(c.discussionId)?.title ?? 'Открыть', onClick: () => go('/c/' + c.discussionId) }) : null,
        admin && !c.discussionId ? listItem({ leading: 'add_comment', title: 'Включить комментарии', subtitle: 'Создать группу обсуждения', onClick: () => store.createDiscussion(c).then(() => toast('Комментарии включены')).catch((e) => toast(errorText(e))) }) : null,
        admin && c.discussionId ? listItem({ leading: 'comments_disabled', title: 'Отключить комментарии', danger: true, onClick: () => store.setDiscussion(chatId, null).catch((e) => toast(errorText(e))) }) : null,
      ));
    }
    if (c.linkedChannelId) body.append(section(null, listItem({ leading: 'campaign', title: 'Канал этого обсуждения', onClick: () => go('/c/' + c.linkedChannelId) })));
    const memberRows = (c.members ?? []).map((m) => {
      const u = store.user(m.user.id) ?? m.user;
      const roleText = m.role === 'owner' ? 'владелец' : m.role === 'admin' ? 'админ' : '';
      return listItem({
        leading: avatar(u.displayName, u.avatarFileId, 44, { online: u.online && !u.isBot }),
        title: userNameLine(u), subtitle: roleText ? h('span.member-role', null, roleText) : (u.isBot ? 'бот' : formatLastSeen(u.online, u.lastSeen)),
        onClick: () => go('/u/' + u.id),
        trailing: admin && u.id !== store.myId && m.role !== 'owner' ? iconBtn('more_vert', 'Действия', (e) => menu(e, [
          channel ? { icon: 'shield_person', label: m.role === 'admin' ? 'Снять админа' : 'Сделать админом', run: () => store.setRole(chatId, u.id, m.role !== 'admin').catch((x) => toast(errorText(x))) } : null,
          { icon: 'person_remove', label: channel ? 'Удалить из канала' : 'Удалить из группы', danger: true, run: async () => { if (await confirm('Удалить участника?', u.displayName, 'Удалить', true)) store.removeMember(chatId, u.id).catch((x) => toast(errorText(x))); } },
        ])) : null,
      });
    });
    body.append(section(channel ? `Подписчики · ${c.memberCount}` : `Участники · ${c.members?.length ?? 0}`,
      admin || (!channel && c.myRole) ? listItem({ leading: h('div.lead-tonal', null, icon('person_add')), title: channel ? 'Пригласить подписчиков' : 'Добавить участников', onClick: () => addMembersSheet(c) }) : null,
      ...memberRows));
    const danger = dangerItems(c);
    if (danger.length) body.append(section(null, ...danger.map((d) => listItem({ leading: d.icon, title: d.label, danger: true, onClick: d.run }))));
  }

  const off = subs(store.on('chats', render), store.on('users', render));
  render();
  store.loadChat(chatId).catch((e) => toast(errorText(e)));
  return { el, destroy: off };
}

async function changeChatAvatar(c) {
  const [f] = await pickFile('image/*');
  if (!f) return;
  try {
    const id = await store.uploadPublic(await scaleImage(f, 640, 'image/jpeg', true), 'image/jpeg');
    await store.updateChat(c.id, { avatarFileId: id });
  } catch (e) { toast(errorText(e)); }
}

function editChat(c) {
  const t = field('Название', { value: c.title, maxlength: 128 });
  const d = field('Описание', { value: c.description ?? '', multiline: true, maxlength: 500 });
  dialog({
    title: c.type === 'channel' ? 'Изменить канал' : 'Изменить группу', body: h('div', null, t.el, d.el),
    actions: [
      c.avatarFileId ? { label: 'Удалить фото', run: () => store.updateChat(c.id, { avatarFileId: '' }) } : null,
      { label: 'Отмена' },
      { label: 'Сохранить', primary: true, run: () => store.updateChat(c.id, { title: t.input.value.trim() || c.title, description: d.input.value.trim() }) },
    ].filter(Boolean),
  });
}

function addMembersSheet(c) {
  const picked = new Set();
  const box = h('div');
  const s = sheet({ title: 'Добавить участников', body: box });
  const picker = userPicker(picked, (c.members ?? []).map((m) => m.user.id));
  box.append(picker.el, h('div.pad', null, h('button.filled-btn.btn-block', {
    onclick: async () => {
      if (!picked.size) return;
      try { await store.addMembers(c.id, [...picked]); s.close(); toast('Участники добавлены'); } catch (e) { toast(errorText(e)); }
    },
  }, 'Добавить')));
}

/** Список контактов с поиском и галочками. */
function userPicker(picked, exclude = [], onChange) {
  const input = h('input', { placeholder: 'Имя или @username', type: 'search' });
  const chips = h('div.selected-chips');
  const list = h('div.pick-list');
  let found = null;
  let seq = 0;
  const known = new Map();
  input.addEventListener('input', () => {
    const q = input.value.trim();
    const my = ++seq;
    if (q.length < 2) { found = null; render(); return; }
    setTimeout(async () => {
      if (my !== seq) return;
      const r = await store.searchUsers(q).catch(() => []);
      if (my === seq) { found = r; render(); }
    }, 300);
  });
  function render() {
    clear(chips);
    for (const id of picked) {
      const u = known.get(id) ?? store.user(id);
      chips.append(h('button.chip.on', { onclick: () => { picked.delete(id); render(); onChange?.(); } }, u?.displayName ?? '…', icon('close')));
    }
    clear(list);
    const base = found ?? [...store.contacts, ...store.chats.filter((c) => c.type === 'direct').map((c) => store.peerOf(c)).filter(Boolean)];
    const seen = new Set();
    for (const u of base) {
      if (!u || seen.has(u.id) || u.id === store.myId || exclude.includes(u.id) || u.isService) continue;
      seen.add(u.id);
      known.set(u.id, u);
      const on = picked.has(u.id);
      list.append(listItem({
        leading: avatar(u.displayName, u.avatarFileId, 44), title: userNameLine(u), subtitle: '@' + u.username,
        trailing: icon(on ? 'check_circle' : 'radio_button_unchecked', on ? 'fill check' : ''),
        onClick: () => { if (on) picked.delete(u.id); else picked.add(u.id); render(); onChange?.(); },
      }));
    }
    if (!list.firstChild) list.append(h('p.pad.muted.center', null, found ? 'Никого не нашли' : 'Найдите людей по имени или @нику'));
  }
  render();
  return { el: h('div', null, h('div.search', null, icon('search'), input), chips, list) };
}

// =====================================================================
// Новый чат, группа, канал
// =====================================================================

export function newScreen(kind) {
  const body = h('div.scroll');
  const titles = { chat: 'Новый чат', group: 'Новая группа', channel: 'Новый канал' };
  const el = h('div.screen', null, topBar({ title: titles[kind] ?? 'Создать', back: () => back() }), body);

  if (kind === 'chat') {
    const input = h('input', { placeholder: 'Имя или @username', type: 'search' });
    const list = h('div');
    let seq = 0;
    const open = async (u) => { try { go('/c/' + (await store.openDirect(u.id)).id, true); } catch (e) { toast(errorText(e)); } };
    const renderList = (users) => {
      clear(list);
      for (const u of users) if (u.id !== store.myId) list.append(listItem({ leading: avatar(u.displayName, u.avatarFileId, 44, { online: u.online }), title: userNameLine(u), subtitle: '@' + u.username, onClick: () => open(u) }));
      if (!users.length) list.append(h('p.pad.muted.center', null, 'Никого не нашли'));
    };
    input.addEventListener('input', () => {
      const q = input.value.trim();
      const my = ++seq;
      if (q.length < 2) { renderList(store.contacts); return; }
      setTimeout(async () => { if (my !== seq) return; const r = await store.searchUsers(q).catch(() => []); if (my === seq) renderList(r); }, 300);
    });
    body.append(h('div.search', null, icon('search'), input),
      listItem({ leading: h('div.lead-tonal', null, icon('group_add')), title: 'Новая группа', onClick: () => go('/new/group', true) }),
      listItem({ leading: h('div.lead-tonal', null, icon('campaign')), title: 'Новый канал', onClick: () => go('/new/channel', true) }),
      listItem({ leading: h('div.lead-tonal', null, icon('bookmark')), title: 'Избранное', subtitle: 'Заметки и сохранённые сообщения', onClick: () => { const s = store.savedChat(); if (s) go('/c/' + s.id, true); } }),
      h('div.list-head', null, 'Контакты'), list);
    renderList(store.contacts);
    setTimeout(() => input.focus(), 50);
    return { el };
  }

  let avatarBlob = null;
  const av = h('div', { style: { cursor: 'pointer' }, title: 'Фото' });
  const drawAv = () => {
    clear(av);
    if (avatarBlob) av.append(h('div.avatar', { style: { width: '88px', height: '88px', '--av': 'var(--primary)' } }, h('img', { src: URL.createObjectURL(avatarBlob), alt: '' })));
    else av.append(h('div.avatar', { style: { width: '88px', height: '88px', '--av': 'var(--primary)' } }, icon('add_a_photo')));
  };
  av.onclick = async () => { const [f] = await pickFile('image/*'); if (f) { avatarBlob = await scaleImage(f, 640, 'image/jpeg', true); drawAv(); } };
  drawAv();
  const title = field(kind === 'group' ? 'Название группы' : 'Название канала', { maxlength: 128 });
  const desc = field('Описание (необязательно)', { multiline: true, maxlength: 500 });
  let isPublic = kind === 'channel';
  const uname = field('Публичное имя (необязательно)', { maxlength: 32 });
  const unameWrap = h('div', null, uname.el, h('p.hint', null, 'По нему найдут в поиске: латиница, цифры и _.'));
  const toggle = listItem({ leading: 'public', title: kind === 'group' ? 'Открытая группа' : 'Публичный канал', subtitle: 'Находится в поиске, вступить может любой', trailing: switchEl(isPublic, (v) => { isPublic = v; unameWrap.classList.toggle('hidden', !v); }) });
  unameWrap.classList.toggle('hidden', !isPublic);
  const picked = new Set();
  const picker = kind === 'group' ? userPicker(picked) : null;
  const btn = h('button.filled-btn.btn-block', {
    onclick: async () => {
      const t = title.input.value.trim();
      if (!t) { toast('Введите название'); return; }
      btn.disabled = true;
      try {
        const avatarFileId = avatarBlob ? await store.uploadPublic(avatarBlob, 'image/jpeg') : undefined;
        const o = { title: t, description: desc.input.value.trim(), isPublic, avatarFileId, username: isPublic ? uname.input.value.trim().replace(/^@/, '') || undefined : undefined };
        const c = kind === 'group' ? await store.createGroup({ ...o, memberIds: [...picked] }) : await store.createChannel(o);
        go('/c/' + c.id, true);
      } catch (e) { toast(errorText(e)); btn.disabled = false; }
    },
  }, kind === 'group' ? 'Создать группу' : 'Создать канал');
  body.append(h('div.pad', null, h('div.row', { style: { gap: '16px', alignItems: 'center' } }, av, h('div.grow', null, title.el)), desc.el), section(null, toggle), h('div', { style: { padding: '0 16px' } }, unameWrap));
  if (picker) body.append(h('div.list-head', null, 'Участники'), picker.el);
  body.append(h('div.pad', null, btn));
  setTimeout(() => title.input.focus(), 50);
  return { el };
}

// =====================================================================
// Приглашение
// =====================================================================

export function joinScreen(code) {
  const body = h('div.scroll', null, spinner());
  const el = h('div.screen', null, topBar({ title: 'Приглашение', back: () => back() }), body);
  (async () => {
    try {
      const c = await store.invitePreviewAny(code);
      if (store.chat(c.id)?.myRole) { go('/c/' + c.id, true); return; }
      clear(body).append(
        h('div.profile-head', null, avatar(c.title, c.avatarFileId, 112), h('div.ph-name', null, nameLine(c.title, { verified: c.verified })), h('div.ph-sub', null, members(c.memberCount, c.type === 'channel'))),
        c.description ? h('p.pad.center', null, c.description) : null,
        h('div.pad', null,
          h('button.filled-btn.btn-block', { onclick: async () => { try { const j = await store.joinAny(code, c); go('/c/' + j.id, true); } catch (e) { toast(errorText(e)); } } }, c.type === 'channel' ? 'Подписаться' : 'Вступить в группу'),
          h('button.text-btn.btn-block.mt8', { onclick: () => { store.previewChat(c); go('/c/' + c.id, true); } }, 'Посмотреть')));
    } catch (e) { clear(body).append(emptyState('link_off', 'Приглашение не работает', errorText(e))); }
  })();
  return { el };
}

// =====================================================================
// Набор стикеров
// =====================================================================

export function stickerPackScreen(id) {
  const body = h('div.scroll', null, spinner());
  const el = h('div.screen', null, topBar({ title: 'Стикеры', back: () => back() }), body);
  const load = async () => {
    try {
      const p = await api.stickerPack(id);
      clear(body).append(
        h('div.pad', null, h('h2', null, p.title), h('p.muted', null, `${p.stickers.length} стикеров`)),
        h('div.sticker-grid', { style: { maxHeight: 'none' } }, p.stickers.map((s) => h('button', { title: s.emoji }, h('img', { src: api.avatarUrl(s.fileId), alt: s.emoji, loading: 'lazy' })))),
        h('div.pad', null,
          h('button.' + (p.isAdded ? 'tonal-btn' : 'filled-btn') + '.btn-block', { onclick: () => store.setPackAdded(id, !p.isAdded).then(load).catch((e) => toast(errorText(e))) }, p.isAdded ? 'Убрать набор' : 'Добавить набор'),
          h('button.text-btn.btn-block.mt8', { onclick: () => copyText(`ryzik://stickers/${id}`) }, 'Скопировать ссылку')));
    } catch (e) { clear(body).append(emptyState('sticky_note', 'Набор не найден', errorText(e))); }
  };
  load();
  return { el };
}

// =====================================================================
// Комментарии к посту канала
// =====================================================================

export function commentsScreen(chatId, postId) {
  const list = h('div.messages-inner');
  const scroller = h('div.messages', null, list);
  const input = h('textarea', { rows: 1, placeholder: 'Комментарий', maxlength: 4000 });
  let replyTo = null;
  const ctx = h('div');
  const sendBtn = h('button.send-btn', { title: 'Отправить', onclick: () => send() }, icon('send'));
  const composer = h('div.composer-wrap', null, h('div.composer-col', null, ctx, h('div.composer', null, h('div.composer-box', null, input), sendBtn)));
  const el = h('div.chat-screen.screen', null, h('div.chat-bg'), topBar({ title: 'Комментарии', back: () => back('/c/' + chatId) }), scroller, composer);
  let page = null;

  input.addEventListener('input', () => { input.style.height = 'auto'; input.style.height = Math.min(input.scrollHeight, 160) + 'px'; });
  input.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !e.shiftKey && settings.enterToSend && matchMedia('(pointer: fine)').matches) { e.preventDefault(); send(); } });

  function bubble(m, isPost) {
    const u = store.user(m.senderId);
    const mine = m.senderId === store.myId;
    const reply = m.replyTo && page?.comments.find((x) => x.id === m.replyTo);
    return h('div.msg.first' + (mine && !isPost ? '.mine' : ''), { oncontextmenu: (e) => { e.preventDefault(); if (!isPost) menu(e, [{ icon: 'reply', label: 'Ответить', run: () => { replyTo = m; drawCtx(); input.focus(); } }, m.content?.text ? { icon: 'content_copy', label: 'Копировать текст', run: () => copyText(m.content.text) } : null, { icon: 'person', label: 'Открыть профиль', run: () => go('/u/' + m.senderId) }]); } },
      !isPost && !mine ? h('div.m-av', null, avatar(u?.displayName ?? '?', u?.avatarFileId, 34)) : null,
      h('div.bubble', { style: isPost ? { maxWidth: '100%', width: '100%' } : null },
        !isPost && !mine ? h('div.b-name', { style: { color: 'var(--primary)' }, onclick: () => go('/u/' + m.senderId) }, u?.displayName ?? '…') : null,
        reply ? h('div.b-reply', null, h('div.rn', null, store.user(reply.senderId)?.displayName ?? ''), h('div.rt', null, reply.content?.text ?? '')) : null,
        h('div.b-text', null, m.content ? (m.type === 'text' ? richText(m.content.text) : previewText(m.type, m.content)) : 'Сообщение', h('span.b-meta', null, formatTime(m.createdAt)))));
  }

  function render() {
    clear(list);
    if (!page) { list.append(spinner()); return; }
    list.append(bubble(page.post, true), h('div.day-sep', null, page.comments.length ? `Комментарии · ${page.comments.length}` : 'Комментариев пока нет'));
    for (const m of page.comments) list.append(bubble(m, false));
    scroller.scrollTop = scroller.scrollHeight;
  }

  function drawCtx() {
    clear(ctx);
    if (replyTo) ctx.append(h('div.compose-ctx', null, icon('reply', 'lead'), h('div.cc-text', null, h('div.cc-t', null, store.user(replyTo.senderId)?.displayName ?? ''), h('div.cc-s', null, replyTo.content?.text ?? '')), iconBtn('close', 'Отмена', () => { replyTo = null; drawCtx(); })));
  }

  async function send() {
    const t = input.value.trim();
    if (!t) return;
    input.value = '';
    try {
      const m = await store.sendComment(postId, t, replyTo?.id);
      replyTo = null;
      drawCtx();
      if (!page.comments.some((x) => x.id === m.id)) page.comments.push(m);
      render();
    } catch (e) { input.value = t; toast(errorText(e)); }
  }

  const load = async () => {
    try { page = await store.loadComments(postId); render(); } catch (e) { clear(list).append(emptyState('comments_disabled', 'Комментарии недоступны', errorText(e))); composer.remove(); }
  };
  const off = subs(store.on('comment', async (ev) => {
    if (ev.messageId !== postId || !page) return;
    if (ev.message) {
      const m = await store.decrypt(ev.message);
      if (!page.comments.some((x) => x.id === m.id)) { page.comments.push(m); render(); }
    } else load();
  }));
  render();
  load();
  return { el, destroy: off };
}
