// Навигация по адресам вида #/c/<id>. Маршрутизатор подключает main.js.
let router = () => {};
let depth = 0;

export function setRouter(fn) { router = fn; }

export function go(path, replace = false) {
  const url = '#' + path;
  if (location.hash === url) { router(); return; }
  if (replace) history.replaceState(null, '', url);
  else { history.pushState(null, '', url); depth++; }
  router();
}

/** Назад: по истории, если пришли изнутри приложения, иначе на fallback. */
export function back(fallback = '/chats') {
  if (depth > 0) { depth--; history.back(); } else go(fallback, true);
}

addEventListener('popstate', () => { if (depth > 0) depth--; });
