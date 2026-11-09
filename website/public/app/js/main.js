// Точка входа веб-версии: маршруты, раскладка (список слева, чат справа), вкладки.
import { store, previewText } from './store.js';
import { h, icon, clear, applyTheme, toast, settings } from './ui.js';
import { authScreen } from './auth.js';
import { chatListTab } from './chats.js';
import { chatScreen } from './chat.js';
import * as pages from './pages.js';
import { initCalls } from './calls.js';
import { go, setRouter } from './nav.js';

applyTheme();

const app = document.getElementById('app');

// ---------- Маршруты ----------
// #/chats | #/contacts | #/profile | #/settings — вкладки слева;
// #/c/<id>, #/c/<id>/info, #/u/<id>, #/post/<chatId>/<postId>, #/new/<kind>, #/s/<раздел> — экран справа.

function parse() {
  const raw = location.hash.replace(/^#/, '') || '/chats';
  const [path, query] = raw.split('?');
  const parts = path.split('/').filter(Boolean);
  return { parts, query: new URLSearchParams(query ?? '') };
}

const TABS = [
  { id: 'chats', title: 'Чаты', icon: 'chat' },
  { id: 'contacts', title: 'Контакты', icon: 'contacts' },
  { id: 'profile', title: 'Профиль', icon: 'account_circle' },
  { id: 'settings', title: 'Настройки', icon: 'settings' },
];

let layout = null;
let currentTab = null;
let tabView = null;
let mainView = null;
let mainKey = null;

function mount(view, container) {
  clear(container);
  container.append(view.el);
}

function destroy(view) { try { view?.destroy?.(); } catch (e) { console.error(e); } }

function renderLayout() {
  clear(app);
  const listPane = h('div.pane-list');
  const mainPane = h('div.pane-main');
  const tabContent = h('div.tab-content');
  const bar = h('nav.tabbar');
  listPane.append(tabContent, h('div.tabbar-wrap', null, bar));
  layout = { root: h('div.layout', null, listPane, mainPane), listPane, mainPane, tabContent, bar };
  app.append(layout.root);
  currentTab = null;
  mainKey = null;
  renderTabBar();
}

function renderTabBar() {
  if (!layout) return;
  const unread = store.chats.filter((c) => c.unread > 0 && !c.muted && !c.archived).length;
  // Кнопки создаём один раз и дальше только обновляем: иначе подпись вкладки
  // заново проигрывает анимацию при каждом событии и панель дёргается.
  if (!layout.bar.firstChild) {
    for (const t of TABS) {
      layout.bar.append(h('button.tab', { onclick: () => go('/' + t.id), title: t.title, 'data-tab': t.id },
        icon(t.icon), h('span.tl', null, t.title)));
    }
  }
  for (const b of layout.bar.children) {
    b.classList.toggle('on', b.dataset.tab === currentTab);
    if (b.dataset.tab !== 'chats') continue;
    const text = unread ? (unread > 99 ? '99+' : String(unread)) : '';
    let badge = b.querySelector('.badge');
    if (!text) badge?.remove();
    else if (!badge) b.insertBefore(h('span.badge', null, text), b.lastChild);
    else if (badge.textContent !== text) badge.textContent = text;
  }
  const title = (unread ? `(${unread}) ` : '') + 'RyzikChat';
  if (document.title !== title) document.title = title;
}

const TAB_VIEWS = {
  chats: () => chatListTab(),
  contacts: () => pages.contactsTab(),
  profile: () => pages.profileTab(),
  settings: () => pages.settingsTab(),
};

function route() {
  if (store.auth !== 'in') return;
  if (!layout) renderLayout();
  const { parts, query } = parse();
  const first = parts[0];
  const tab = TAB_VIEWS[first] ? first : (sessionStorage.getItem('rc.tab') || 'chats');
  if (TAB_VIEWS[first]) sessionStorage.setItem('rc.tab', first);
  if (tab !== currentTab) {
    currentTab = tab;
    destroy(tabView);
    tabView = TAB_VIEWS[tab]();
    mount(tabView, layout.tabContent);
    renderTabBar();
  }
  const key = TAB_VIEWS[first] ? '' : parts.join('/') + '?' + query;
  if (key === mainKey) return;
  mainKey = key;
  destroy(mainView);
  mainView = null;
  store.openChatId = null;
  let view = null;
  if (first === 'c' && parts[1]) view = parts[2] === 'info' ? pages.chatInfo(parts[1]) : chatScreen(parts[1], query.get('m'));
  else if (first === 'u' && parts[1]) view = pages.userProfile(parts[1]);
  else if (first === 'post' && parts[2]) view = pages.commentsScreen(parts[1], parts[2]);
  else if (first === 'new' && parts[1]) view = pages.newScreen(parts[1]);
  else if (first === 's' && parts[1]) view = pages.settingsPage(parts[1]);
  else if (first === 'join' && parts[1]) view = pages.joinScreen(decodeURIComponent(parts[1]));
  else if (first === 'stickers' && parts[1]) view = pages.stickerPackScreen(parts[1]);
  layout.root.classList.toggle('has-main', !!view);
  if (view) {
    mainView = view;
    mount(view, layout.mainPane);
  } else {
    clear(layout.mainPane).append(h('div.placeholder', null,
      h('div.ph-logo', null, icon('forum', 'fill')),
      h('h2', null, 'RyzikChat Web'),
      h('p', null, 'Выберите чат слева или начните новый.'),
      h('p.muted', { style: { fontSize: '13px' } }, '🔒 Сообщения шифруются прямо в браузере'),
    ));
  }
  // Вкладки отмечают открытый чат.
  tabView?.onRoute?.(parts);
}

setRouter(route);
addEventListener('popstate', route);

// ---------- Вход / выход ----------

store.on('auth', () => {
  if (store.auth === 'in') {
    layout = null;
    route();
    askNotifications();
  } else if (store.auth === 'out') {
    destroy(tabView); destroy(mainView);
    tabView = mainView = layout = null;
    clear(app).append(authScreen().el);
  }
});

store.on('chats', renderTabBar);
store.on('toast', (t) => toast(t));
store.on('notice', (t) => toast(t));
store.on('removed', (id) => { if (store.openChatId === id || location.hash.startsWith(`#/c/${id}`)) go('/chats', true); });

// ---------- «В сети» и уведомления ----------

document.addEventListener('visibilitychange', () => {
  store.setActive(document.visibilityState === 'visible');
  if (document.visibilityState === 'visible' && store.openChatId) store.markRead(store.openChatId);
});

function askNotifications() {
  if (!('Notification' in window) || Notification.permission !== 'default' || !settings.notifications) return;
  const ask = () => { Notification.requestPermission().catch(() => {}); removeEventListener('pointerdown', ask); };
  addEventListener('pointerdown', ask, { once: true });
}

let audioCtx = null;
function blip() {
  if (!settings.sounds) return;
  try {
    audioCtx ??= new AudioContext();
    const o = audioCtx.createOscillator(), g = audioCtx.createGain();
    o.type = 'sine'; o.frequency.value = 880;
    g.gain.setValueAtTime(0.0001, audioCtx.currentTime);
    g.gain.exponentialRampToValueAtTime(0.12, audioCtx.currentTime + 0.01);
    g.gain.exponentialRampToValueAtTime(0.0001, audioCtx.currentTime + 0.25);
    o.frequency.exponentialRampToValueAtTime(1320, audioCtx.currentTime + 0.12);
    o.connect(g).connect(audioCtx.destination);
    o.start(); o.stop(audioCtx.currentTime + 0.3);
  } catch { /* звук не обязателен */ }
}

store.on('incoming', (chat, msg) => {
  const visible = document.visibilityState === 'visible';
  if (chat.muted) return;
  if (visible && store.openChatId === chat.id) return;
  const sender = store.user(msg.senderId);
  const title = chat.type === 'direct' ? (sender?.displayName ?? 'Новое сообщение') : store.chatTitle(chat);
  const text = msg.content ? previewText(msg.type, msg.content) : 'Новое сообщение';
  const body = chat.type === 'group' && sender ? `${sender.displayName}: ${text}` : text;
  blip();
  if (!visible && settings.notifications && 'Notification' in window && Notification.permission === 'granted') {
    try {
      const n = new Notification(title, { body, tag: chat.id, icon: '/app/icon-192.png', silent: true });
      n.onclick = () => { focus(); go('/c/' + chat.id); n.close(); };
    } catch { /* */ }
  } else if (visible) {
    toast(`${title}: ${body}`.slice(0, 140), { label: 'Открыть', run: () => go('/c/' + chat.id) });
  }
});

store.on('reaction', (chat, who, emoji) => {
  if (chat.muted) return;
  toast(`${who?.displayName ?? 'Кто-то'} отреагировал(а) ${emoji} на ваше сообщение`);
});

// ---------- Старт ----------

initCalls();
store.restore().catch((e) => { console.error(e); store.setAuth('out'); });

