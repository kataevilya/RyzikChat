// Общие кусочки интерфейса: DOM, тема Material You, значки, диалоги, форматирование.
import { api } from './store.js';

// ---------- DOM ----------

export function h(tag, props, ...children) {
  const [name, ...classes] = tag.split('.');
  const el = document.createElement(name || 'div');
  if (classes.length) el.className = classes.join(' ');
  if (props) {
    for (const [k, v] of Object.entries(props)) {
      if (v == null || v === false) continue;
      if (k === 'class') el.className += (el.className ? ' ' : '') + v;
      else if (k === 'style' && typeof v === 'object') {
        for (const [sk, sv] of Object.entries(v)) if (sv != null) { if (sk.startsWith('--')) el.style.setProperty(sk, sv); else el.style[sk] = sv; }
      }
      else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2).toLowerCase(), v);
      else if (k in el && typeof v !== 'string') el[k] = v;
      else el.setAttribute(k, v === true ? '' : v);
    }
  }
  append(el, children);
  return el;
}

function append(el, children) {
  for (const c of children) {
    if (c == null || c === false) continue;
    if (Array.isArray(c)) append(el, c);
    else el.append(c instanceof Node ? c : document.createTextNode(String(c)));
  }
}

export function icon(name, cls = '') {
  return h('span.ms' + (cls ? '.' + cls.split(' ').join('.') : ''), { 'aria-hidden': 'true' }, name);
}

export function iconBtn(name, title, onClick, cls = '') {
  return h('button.icon-btn' + (cls ? '.' + cls : ''), { title, 'aria-label': title, onclick: (e) => { e.stopPropagation(); onClick(e); } }, icon(name));
}

export function clear(el) { while (el.firstChild) el.firstChild.remove(); return el; }

/**
 * Приводит детей parent к списку nodes, не трогая те, что уже стоят на месте.
 * В отличие от clear()+append не пересоздаёт DOM: картинки не мигают, анимации не повторяются.
 */
export function patchChildren(parent, nodes) {
  let cur = parent.firstChild;
  for (const n of nodes) {
    if (n === cur) { cur = cur.nextSibling; continue; }
    parent.insertBefore(n, cur);
  }
  while (cur) { const next = cur.nextSibling; cur.remove(); cur = next; }
  return parent;
}

// ---------- Настройки и тема ----------

export const SEEDS = [
  ['#FF8A3D', 'Рыжик'], ['#6750A4', 'Лаванда'], ['#006A6A', 'Бирюза'], ['#2E6BE6', 'Небо'],
  ['#3A7D2C', 'Лес'], ['#D0346A', 'Малина'], ['#8B5E3C', 'Какао'], ['#5C5F66', 'Графит'],
  ['#9C4DFF', 'Аметист'], ['#FF4F8B', 'Неон'], ['#C9A227', 'Золото'], ['#00B8D4', 'Лёд'],
];
export const PREMIUM_SEEDS_FROM = 8;

const DEFAULT_SETTINGS = { theme: 'system', seed: 0, fontScale: 1, bigEmoji: true, enterToSend: true, notifications: true, sounds: true, radius: 20 };

export const settings = (() => {
  try { return { ...DEFAULT_SETTINGS, ...JSON.parse(localStorage.getItem('rc.settings') || '{}') }; } catch { return { ...DEFAULT_SETTINGS }; }
})();

export function saveSettings(patch) {
  Object.assign(settings, patch);
  try { localStorage.setItem('rc.settings', JSON.stringify(settings)); } catch { /* */ }
  applyTheme();
}

function hexToHsl(hex) {
  const n = parseInt(hex.slice(1), 16);
  const r = ((n >> 16) & 255) / 255, g = ((n >> 8) & 255) / 255, b = (n & 255) / 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b);
  let hh = 0, s = 0;
  const l = (max + min) / 2;
  if (max !== min) {
    const d = max - min;
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    hh = max === r ? ((g - b) / d + (g < b ? 6 : 0)) : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
    hh *= 60;
  }
  return [hh, s, l];
}

const hsl = (hh, s, l) => `hsl(${hh.toFixed(1)} ${(Math.min(1, Math.max(0, s)) * 100).toFixed(1)}% ${(l * 100).toFixed(1)}%)`;

/** То же, что schemeFromSeed в Theme.kt. */
function scheme(seedHex, dark) {
  const [sh, ss] = hexToHsl(seedHex);
  const tone = (l, sat = 1, hue = sh) => hsl(hue, ss * sat, l);
  const sec = (l) => hsl(sh, ss * 0.35, l);
  const ter = (l) => tone(l, 1, (sh + 60) % 360);
  if (!dark) {
    return {
      primary: tone(0.40), 'on-primary': '#fff', 'primary-container': tone(0.88), 'on-primary-container': tone(0.12),
      secondary: sec(0.40), 'secondary-container': tone(0.90, 0.4), 'on-secondary-container': tone(0.12, 0.4),
      tertiary: ter(0.40), 'tertiary-container': ter(0.88), 'on-tertiary-container': ter(0.12),
      bg: tone(0.985, 0.3), surface: tone(0.985, 0.3), 'surface-variant': tone(0.90, 0.15), 'on-surface-variant': tone(0.30, 0.1),
      'on-surface': '#1C1B1F', 'sc-lowest': '#fff', 'sc-low': tone(0.96, 0.3), sc: tone(0.945, 0.3), 'sc-high': tone(0.925, 0.3), 'sc-highest': tone(0.90, 0.3),
      outline: tone(0.50, 0.1), 'outline-variant': tone(0.80, 0.1), error: '#B3261E', 'error-container': '#F9DEDC', 'on-error-container': '#410E0B',
      scrim: 'rgba(0,0,0,.32)', shadow: 'rgba(0,0,0,.14)',
    };
  }
  return {
    primary: tone(0.78), 'on-primary': tone(0.20), 'primary-container': tone(0.30), 'on-primary-container': tone(0.90),
    secondary: sec(0.78), 'secondary-container': tone(0.25, 0.4), 'on-secondary-container': tone(0.90, 0.4),
    tertiary: ter(0.78), 'tertiary-container': ter(0.30), 'on-tertiary-container': ter(0.90),
    bg: tone(0.07, 0.3), surface: tone(0.07, 0.3), 'surface-variant': tone(0.25, 0.15), 'on-surface-variant': tone(0.80, 0.1),
    'on-surface': '#E6E1E5', 'sc-lowest': tone(0.04, 0.3), 'sc-low': tone(0.09, 0.3), sc: tone(0.11, 0.3), 'sc-high': tone(0.14, 0.3), 'sc-highest': tone(0.17, 0.3),
    outline: tone(0.55, 0.1), 'outline-variant': tone(0.30, 0.1), error: '#F2B8B5', 'error-container': '#8C1D18', 'on-error-container': '#F9DEDC',
    scrim: 'rgba(0,0,0,.5)', shadow: 'rgba(0,0,0,.5)',
  };
}

const darkQuery = matchMedia('(prefers-color-scheme: dark)');
darkQuery.addEventListener?.('change', () => applyTheme());

export function isDark() { return settings.theme === 'dark' || (settings.theme === 'system' && darkQuery.matches); }

export function applyTheme() {
  const dark = isDark();
  const seed = SEEDS[settings.seed]?.[0] ?? SEEDS[0][0];
  const root = document.documentElement;
  for (const [k, v] of Object.entries(scheme(seed, dark))) root.style.setProperty('--' + k, v);
  root.style.setProperty('--font-scale', settings.fontScale);
  root.style.setProperty('--bubble-r', settings.radius + 'px');
  root.dataset.theme = dark ? 'dark' : 'light';
  document.querySelector('meta[name=theme-color]')?.setAttribute('content', getComputedStyle(root).getPropertyValue('--surface'));
}

// ---------- Аватарки ----------

const AVATAR_PALETTE = ['#FF8A3D', '#7C4DFF', '#00A3A3', '#2E6BE6', '#43A047', '#E91E63', '#FFB300', '#8D6E63'];

function javaHash(s) {
  let x = 0;
  for (let i = 0; i < s.length; i++) x = (Math.imul(31, x) + s.charCodeAt(i)) | 0;
  return x;
}

export function initials(name) {
  const parts = (name || '').replace(/[^\p{L}\p{N}\p{Extended_Pictographic}\s]/gu, '').trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return '?';
  const first = (p) => Array.from(p)[0] ?? '';
  return (parts.length === 1 ? first(parts[0]) : first(parts[0]) + first(parts[1])).toUpperCase();
}

export function avatar(name, fileId, size = 48, { online = false, saved = false, service = false, round = false } = {}) {
  const base = service ? 'var(--primary)' : AVATAR_PALETTE[((javaHash(name || '') % 8) + 8) % 8];
  const el = h('div.avatar' + (round ? '.round' : ''), { style: { width: size + 'px', height: size + 'px', '--av': base, fontSize: size * 0.38 + 'px' } });
  if (saved) el.append(icon('bookmark', 'fill'));
  else if (service) el.append(icon('verified', 'fill'));
  else el.append(h('span', null, initials(name)));
  if (fileId && !saved && !service) {
    const img = h('img', { src: api.avatarUrl(fileId), alt: '', loading: 'lazy', decoding: 'async', draggable: false });
    img.onerror = () => img.remove();
    el.append(img);
  }
  if (online) el.append(h('i.online'));
  return el;
}

export function chatAvatar(store, chat, size = 52) {
  if (chat.type === 'saved') return avatar('Избранное', null, size, { saved: true });
  if (chat.isService) return avatar(chat.title, null, size, { service: true });
  if (chat.type === 'direct') {
    const p = store.peerOf(chat);
    return avatar(p?.displayName ?? '?', p?.avatarFileId, size, { online: p?.online && !p?.isBot });
  }
  return avatar(chat.title, chat.avatarFileId, size);
}

/** Имя с галочкой, звездой премиума и эмодзи-статусом. */
export function nameLine(text, { verified = false, premium = false, emoji = null, bot = false } = {}) {
  return h('span.name-line', null,
    h('span.nm', null, text),
    verified ? icon('verified', 'fill verified') : null,
    premium ? h('span.premium-star', { title: 'Премиум' }, icon('star', 'fill')) : null,
    emoji ? h('span.estatus', null, emoji) : null,
    bot ? h('span.bot-tag', null, 'бот') : null,
  );
}

export function userNameLine(u) {
  return nameLine(u?.displayName ?? '…', { verified: u?.verified, premium: u?.isPremium, emoji: u?.emojiStatus, bot: u?.isBot });
}

// ---------- Форматирование ----------

const pad = (n) => String(n).padStart(2, '0');
export function formatTime(t) { const d = new Date(t); return `${pad(d.getHours())}:${pad(d.getMinutes())}`; }

export function sameDay(a, b) {
  const x = new Date(a), y = new Date(b);
  return x.getFullYear() === y.getFullYear() && x.getMonth() === y.getMonth() && x.getDate() === y.getDate();
}

export function formatDay(t) {
  const now = Date.now();
  if (sameDay(t, now)) return 'Сегодня';
  if (sameDay(t, now - 86400000)) return 'Вчера';
  const d = new Date(t);
  return d.toLocaleDateString('ru-RU', { day: 'numeric', month: 'long', ...(d.getFullYear() !== new Date().getFullYear() ? { year: 'numeric' } : {}) });
}

export function formatListTime(t) {
  if (!t) return '';
  const now = Date.now();
  if (sameDay(t, now)) return formatTime(t);
  if (now - t < 6 * 86400000) return new Date(t).toLocaleDateString('ru-RU', { weekday: 'short' });
  return new Date(t).toLocaleDateString('ru-RU', { day: 'numeric', month: 'short' });
}

export function formatLastSeen(online, lastSeen) {
  if (online) return 'в сети';
  if (!lastSeen) return 'был(а) давно';
  const diff = Date.now() - lastSeen;
  if (diff < 60000) return 'был(а) только что';
  if (diff < 3600000) return `был(а) ${Math.floor(diff / 60000)} мин. назад`;
  if (sameDay(lastSeen, Date.now())) return `был(а) в ${formatTime(lastSeen)}`;
  return `был(а) ${formatDay(lastSeen).toLowerCase()} в ${formatTime(lastSeen)}`;
}

export function formatSize(b) {
  if (b < 1024) return `${b} Б`;
  if (b < 1048576) return `${(b / 1024).toFixed(1).replace('.', ',')} КБ`;
  if (b < 1073741824) return `${(b / 1048576).toFixed(1).replace('.', ',')} МБ`;
  return `${(b / 1073741824).toFixed(2).replace('.', ',')} ГБ`;
}

export function formatDuration(ms) {
  const s = Math.floor((ms || 0) / 1000);
  return `${Math.floor(s / 60)}:${pad(s % 60)}`;
}

export function plural(n, one, few, many) {
  const m10 = n % 10, m100 = n % 100;
  if (m10 === 1 && m100 !== 11) return one;
  if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return few;
  return many;
}

export function members(n, channel) {
  return channel ? `${n} ${plural(n, 'подписчик', 'подписчика', 'подписчиков')}` : `${n} ${plural(n, 'участник', 'участника', 'участников')}`;
}

/** Текст со ссылками, @упоминаниями и переносами. */
export function richText(text, onMention) {
  const frag = document.createDocumentFragment();
  const re = /(https?:\/\/[^\s<]+[^\s<.,:;"')\]!?])|(ryzik:\/\/[^\s]+)|(@[a-zA-Z0-9_]{3,32})/g;
  let last = 0, m;
  while ((m = re.exec(text))) {
    if (m.index > last) frag.append(text.slice(last, m.index));
    if (m[1]) frag.append(h('a', { href: m[1], target: '_blank', rel: 'noopener noreferrer' }, m[1]));
    else if (m[2]) frag.append(h('a', { href: '#', onclick: (e) => { e.preventDefault(); onMention?.(m[2]); } }, m[2]));
    else frag.append(h('a.mention', { href: '#', onclick: (e) => { e.preventDefault(); onMention?.(m[3]); } }, m[3]));
    last = m.index + m[0].length;
  }
  if (last < text.length) frag.append(text.slice(last));
  return frag;
}

const EMOJI_RE = /^(?:\p{Extended_Pictographic}|\p{Emoji_Component}|‍|️|\s)+$/u;
export function isEmojiOnly(t) {
  const s = (t || '').trim();
  if (!s || s.length > 24 || /^[\d#*\s]+$/.test(s)) return false;
  return EMOJI_RE.test(s);
}

// ---------- Всплывающие окна ----------

const layer = () => document.getElementById('overlay');

export function toast(text, action) {
  const box = document.getElementById('toasts');
  const t = h('div.toast', null, h('span', null, text), action ? h('button.text-btn', { onclick: () => { action.run(); t.remove(); } }, action.label) : null);
  box.append(t);
  requestAnimationFrame(() => t.classList.add('show'));
  setTimeout(() => { t.classList.remove('show'); setTimeout(() => t.remove(), 300); }, action ? 6000 : 3200);
}

/** Модальный диалог Material 3. Возвращает { el, close }. */
export function dialog({ title, icon: ic, body, actions = [], wide = false, onClose, dismissible = true }) {
  const scrim = h('div.scrim');
  const close = () => {
    scrim.classList.remove('show');
    setTimeout(() => scrim.remove(), 200);
    document.removeEventListener('keydown', onKey);
    onClose?.();
  };
  const onKey = (e) => { if (e.key === 'Escape' && dismissible) close(); };
  const btns = actions.map((a) => h('button.' + (a.primary ? 'filled-btn' : 'text-btn') + (a.danger ? '.danger' : ''), {
    onclick: async (e) => {
      if (a.run) {
        const btn = e.currentTarget;
        btn.disabled = true;
        try { const keep = await a.run(); if (keep !== false) close(); } catch (err) { toast(err?.message || 'Ошибка'); } finally { btn.disabled = false; }
      } else close();
    },
  }, a.label));
  const box = h('div.dialog' + (wide ? '.wide' : ''), { role: 'dialog', onclick: (e) => e.stopPropagation() },
    ic ? h('div.dialog-icon', null, icon(ic)) : null,
    title ? h('h2', null, title) : null,
    h('div.dialog-body', null, body),
    btns.length ? h('div.dialog-actions', null, btns) : null,
  );
  scrim.append(box);
  scrim.addEventListener('click', () => dismissible && close());
  document.addEventListener('keydown', onKey);
  layer().append(scrim);
  requestAnimationFrame(() => scrim.classList.add('show'));
  setTimeout(() => box.querySelector('input,textarea')?.focus(), 50);
  return { el: box, close };
}

export function confirm(title, text, okLabel = 'OK', danger = false) {
  return new Promise((resolve) => {
    let done = false;
    dialog({
      title, body: h('p', null, text),
      actions: [{ label: 'Отмена' }, { label: okLabel, primary: true, danger, run: () => { done = true; resolve(true); } }],
      onClose: () => { if (!done) resolve(false); },
    });
  });
}

export function prompt(title, { label = '', value = '', ok = 'Сохранить', multiline = false, type = 'text', hint = '' } = {}) {
  return new Promise((resolve) => {
    let done = false;
    const f = field(label, { value, multiline, type });
    dialog({
      title, body: h('div', null, f.el, hint ? h('p.hint', null, hint) : null),
      actions: [{ label: 'Отмена' }, { label: ok, primary: true, run: () => { done = true; resolve(f.input.value); } }],
      onClose: () => { if (!done) resolve(null); },
    });
    f.input.addEventListener('keydown', (e) => { if (e.key === 'Enter' && !multiline) e.target.closest('.dialog').querySelector('.filled-btn').click(); });
  });
}

/** Поле ввода в стиле Material 3 (outlined). */
export function field(label, { value = '', type = 'text', multiline = false, autocomplete, maxlength, oninput, placeholder = ' ', trailing } = {}) {
  const input = multiline
    ? h('textarea', { rows: 3, placeholder, maxlength })
    : h('input', { type, placeholder, autocomplete, maxlength, spellcheck: type === 'text' ? 'true' : 'false' });
  input.value = value;
  if (oninput) input.addEventListener('input', oninput);
  const el = h('label.field', null, input, h('span.field-label', null, label), trailing ?? null);
  if (type === 'password') {
    const t = iconBtn('visibility', 'Показать пароль', () => {
      input.type = input.type === 'password' ? 'text' : 'password';
      t.firstChild.textContent = input.type === 'password' ? 'visibility' : 'visibility_off';
    }, 'field-trail');
    el.append(t);
  }
  return { el, input };
}

/** Контекстное меню. items: [{ icon, label, run, danger }] */
export function menu(anchorOrEvent, items) {
  const scrim = h('div.menu-scrim');
  const box = h('div.menu', { role: 'menu' });
  for (const it of items) {
    if (!it) continue;
    if (it === '-') { box.append(h('div.menu-sep')); continue; }
    if (it.node) { box.append(it.node); continue; }
    box.append(h('button.menu-item' + (it.danger ? '.danger' : ''), {
      onclick: (e) => { e.stopPropagation(); close(); it.run(); },
    }, it.icon ? icon(it.icon) : null, h('span', null, it.label)));
  }
  const close = () => { scrim.remove(); document.removeEventListener('keydown', onKey); };
  const onKey = (e) => { if (e.key === 'Escape') close(); };
  scrim.addEventListener('click', close);
  scrim.addEventListener('contextmenu', (e) => { e.preventDefault(); close(); });
  document.addEventListener('keydown', onKey);
  scrim.append(box);
  layer().append(scrim);
  let x, y;
  if (anchorOrEvent instanceof Event && anchorOrEvent.clientX != null && anchorOrEvent.type !== 'click') { x = anchorOrEvent.clientX; y = anchorOrEvent.clientY; }
  else {
    const t = anchorOrEvent instanceof Event ? anchorOrEvent.currentTarget ?? anchorOrEvent.target : anchorOrEvent;
    const r = t.getBoundingClientRect();
    x = r.right; y = r.bottom;
  }
  const bw = box.offsetWidth, bh = box.offsetHeight;
  box.style.left = Math.max(8, Math.min(x - (x + bw > innerWidth - 8 ? bw : 0), innerWidth - bw - 8)) + 'px';
  box.style.top = Math.max(8, Math.min(y, innerHeight - bh - 8)) + 'px';
  return close;
}

/** Нижний лист (на широком экране — диалог). */
export function sheet({ title, body, onClose }) {
  const scrim = h('div.scrim.sheet-scrim');
  const close = () => { scrim.classList.remove('show'); setTimeout(() => scrim.remove(), 220); onClose?.(); };
  const box = h('div.sheet', { onclick: (e) => e.stopPropagation() }, h('div.sheet-handle'), title ? h('h2', null, title) : null, h('div.sheet-body', null, body));
  scrim.append(box);
  scrim.addEventListener('click', close);
  layer().append(scrim);
  requestAnimationFrame(() => scrim.classList.add('show'));
  return { el: box, close };
}

/** Выбор файла. */
export function pickFile(accept = '*/*', multiple = false) {
  return new Promise((resolve) => {
    const i = h('input', { type: 'file', accept, multiple, style: { display: 'none' } });
    i.onchange = () => { resolve([...i.files]); i.remove(); };
    document.body.append(i);
    i.click();
  });
}

/** Уменьшает картинку до maxSide и возвращает Blob (для аватарок, обоев). */
export async function scaleImage(file, maxSide, mime = 'image/jpeg', square = false) {
  const url = URL.createObjectURL(file);
  try {
    const img = await new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = () => rej(new Error('Не удалось открыть картинку')); i.src = url; });
    const k = Math.min(1, maxSide / Math.max(img.naturalWidth, img.naturalHeight));
    const w = Math.round(img.naturalWidth * k), hh = Math.round(img.naturalHeight * k);
    const c = document.createElement('canvas');
    if (square) {
      const side = Math.min(img.naturalWidth, img.naturalHeight);
      c.width = c.height = Math.min(maxSide, side);
      c.getContext('2d').drawImage(img, (img.naturalWidth - side) / 2, (img.naturalHeight - side) / 2, side, side, 0, 0, c.width, c.height);
    } else {
      c.width = w; c.height = hh;
      c.getContext('2d').drawImage(img, 0, 0, w, hh);
    }
    return await new Promise((res) => c.toBlob((b) => res(b), mime, 0.9));
  } finally {
    URL.revokeObjectURL(url);
  }
}

export function copyText(t) {
  navigator.clipboard?.writeText(t).then(() => toast('Скопировано'), () => toast('Не удалось скопировать'));
}

export function emptyState(ic, title, text) {
  return h('div.empty', null, h('div.empty-icon', null, icon(ic)), h('h3', null, title), text ? h('p', null, text) : null);
}

export function spinner() { return h('div.spinner', null, h('i')); }

/** Верхняя панель экрана. */
export function topBar({ title, subtitle, back, actions = [], leading, onTitle, large = false }) {
  return h('header.top-bar' + (large ? '.large' : ''), null,
    back ? iconBtn('arrow_back', 'Назад', back) : null,
    leading ?? null,
    h('div.top-title' + (onTitle ? '.clickable' : ''), { onclick: onTitle },
      h('div.t', null, title),
      subtitle != null ? h('div.s', null, subtitle) : null),
    h('div.top-actions', null, actions),
  );
}

export function listItem({ leading, title, subtitle, trailing, onClick, danger = false, cls = '' }) {
  return h('div.list-item' + (onClick ? '.clickable' : '') + (danger ? '.danger' : '') + (cls ? '.' + cls : ''), { onclick: onClick, tabindex: onClick ? 0 : null },
    leading ? h('div.li-lead', null, typeof leading === 'string' ? icon(leading) : leading) : null,
    h('div.li-text', null, h('div.li-title', null, title), subtitle ? h('div.li-sub', null, subtitle) : null),
    trailing ? h('div.li-trail', null, trailing) : null,
  );
}

export function switchEl(checked, onChange) {
  const i = h('input', { type: 'checkbox', checked });
  i.addEventListener('change', () => onChange(i.checked));
  return h('label.switch', { onclick: (e) => e.stopPropagation() }, i, h('span.track', null, h('span.thumb')));
}

export function section(title, ...items) {
  return h('section.group', null, title ? h('div.group-title', null, title) : null, h('div.group-card', null, items));
}
