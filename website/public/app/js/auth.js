// Вход и регистрация — как AuthScreen.kt.
import { store, errorText } from './store.js';
import { h, field, icon } from './ui.js';

const USERNAME_RE = /^[a-zA-Z0-9_]{3,32}$/;

export function authScreen() {
  let mode = 'login';
  let step = null; // { hint } — нужен пароль двухэтапной проверки
  const root = h('div.auth');
  const card = h('div.auth-card');
  root.append(card);

  function render() {
    card.replaceChildren();
    card.append(h('img.auth-logo', { src: '/app/icon.svg', alt: '' }));
    if (step) return render2fa();
    card.append(
      h('h1', null, 'RyzikChat'),
      h('p.sub', null, mode === 'login' ? 'Войдите в свой аккаунт' : 'Создайте аккаунт — это займёт минуту'),
      h('div.seg', null,
        h('button' + (mode === 'login' ? '.on' : ''), { onclick: () => { mode = 'login'; render(); } }, 'Вход'),
        h('button' + (mode === 'register' ? '.on' : ''), { onclick: () => { mode = 'register'; render(); } }, 'Регистрация')),
    );
    const err = h('div.err');
    const user = field('Имя пользователя', { autocomplete: 'username', maxlength: 32 });
    const name = mode === 'register' ? field('Как вас зовут', { autocomplete: 'name', maxlength: 64 }) : null;
    const pass = field('Пароль', { type: 'password', autocomplete: mode === 'login' ? 'current-password' : 'new-password' });
    const pass2 = mode === 'register' ? field('Повторите пароль', { type: 'password', autocomplete: 'new-password' }) : null;
    const btn = h('button.filled-btn.btn-block', { type: 'submit' }, mode === 'login' ? 'Войти' : 'Зарегистрироваться');
    const form = h('form', {
      onsubmit: async (e) => {
        e.preventDefault();
        err.textContent = '';
        const u = user.input.value.trim().replace(/^@/, '');
        const p = pass.input.value;
        if (!USERNAME_RE.test(u)) { err.textContent = 'Имя пользователя: 3–32 символа, латиница, цифры и _'; return; }
        if (mode === 'register') {
          if (p.length < 8) { err.textContent = 'Пароль — минимум 8 символов'; return; }
          if (p !== pass2.input.value) { err.textContent = 'Пароли не совпадают'; return; }
        } else if (!p) { err.textContent = 'Введите пароль'; return; }
        btn.disabled = true;
        btn.replaceChildren(h('div.spinner.sm', null, h('i')), mode === 'login' ? 'Входим…' : 'Создаём ключи…');
        try {
          if (mode === 'login') {
            const r = await store.login(u, p);
            if (r?.need2fa) { step = { hint: r.hint || '' }; render(); }
          } else {
            await store.register(u, name.input.value.trim() || u, p);
          }
        } catch (ex) {
          err.textContent = errorText(ex);
          btn.disabled = false;
          btn.textContent = mode === 'login' ? 'Войти' : 'Зарегистрироваться';
        }
      },
    }, user.el, name?.el, pass.el, pass2?.el, err, btn);
    card.append(form);
    if (mode === 'register') {
      card.append(h('p.foot', null, 'Регистрируясь, вы соглашаетесь с ', h('a', { href: '#', onclick: (e) => { e.preventDefault(); showTerms(); } }, 'правилами RyzikChat'), '.'));
    }
    card.append(h('p.foot', null, '🔒 Ключи шифрования создаются в браузере. Пароль не уходит на сервер.'));
    card.append(h('p.foot', null, h('a', { href: '/' }, '← На сайт'), ' · ', h('a', { href: '/download' }, 'Приложение для Android')));
    setTimeout(() => user.input.focus(), 30);
  }

  function render2fa() {
    const err = h('div.err');
    const pass = field('Дополнительный пароль', { type: 'password', autocomplete: 'one-time-code' });
    const btn = h('button.filled-btn.btn-block', { type: 'submit' }, 'Войти');
    card.append(
      h('div.empty-icon', { style: { margin: '0 auto 12px' } }, icon('shield_lock', 'fill')),
      h('h1', null, 'Двухэтапная проверка'),
      h('p.sub', null, 'Аккаунт защищён дополнительным паролем.'),
      h('form', {
        onsubmit: async (e) => {
          e.preventDefault();
          err.textContent = '';
          btn.disabled = true;
          try { await store.confirmLogin2fa(pass.input.value); } catch (ex) { err.textContent = errorText(ex); btn.disabled = false; }
        },
      }, pass.el, step.hint ? h('p.hint', null, `Подсказка: ${step.hint}`) : null, err, btn),
      h('button.text-btn', { style: { margin: '10px auto 0', display: 'flex' }, onclick: () => { step = null; store.pendingLogin = null; render(); } }, 'Назад'),
    );
    setTimeout(() => pass.input.focus(), 30);
  }

  render();
  return { el: root };
}

export async function showTerms() {
  const { dialog } = await import('./ui.js');
  dialog({
    title: 'Правила использования', wide: true,
    body: h('div', null, TERMS.map(([t, x]) => [h('h3', { style: { color: 'var(--primary)', fontSize: '16px', margin: '12px 0 4px' } }, t), h('p', null, x)])),
    actions: [{ label: 'Понятно', primary: true }],
  });
}

// Тот же текст, что в TermsScreen.kt.
export const TERMS = [
  ["1. Общие положения", "RyzikChat — мессенджер для личного и группового общения. Создавая аккаунт или входя в него, вы принимаете эти правила. Если вы с ними не согласны, не пользуйтесь сервисом."],
  ["2. Аккаунт", "Вы отвечаете за сохранность своего пароля. Пароль защищает и ключи шифрования: восстановить переписку без него невозможно. Один человек — один основной аккаунт; выдавать себя за другого человека запрещено."],
  ["3. Запрещено", "Рассылать спам и вредоносные файлы; распространять незаконный контент; угрожать, травить и оскорблять других; публиковать чужие личные данные без согласия; пытаться взломать сервис или других пользователей."],
  ["4. Шифрование и данные", "Сообщения и файлы шифруются на вашем устройстве. Сервер хранит только зашифрованные данные и служебную информацию: имя пользователя, отображаемое имя, аватар, время отправки сообщений и участников чатов."],
  ["5. Бейджи", "Бейджи рядом с именем выдаёт только администрация RyzikChat. Их нельзя купить или передать другому пользователю. Администрация может снять бейдж, если он выдан по ошибке или правила нарушены."],
  ["6. Модерация", "Если на вас пожаловались и нарушение подтвердилось, администрация может ограничить или удалить аккаунт. Содержимое зашифрованных чатов администрация прочитать не может, поэтому жалобы рассматриваются по присланным материалам."],
  ["7. Изменения", "Правила могут обновляться. О важных изменениях мы сообщим в приложении. Продолжая пользоваться RyzikChat после изменений, вы соглашаетесь с новой редакцией."],
];
