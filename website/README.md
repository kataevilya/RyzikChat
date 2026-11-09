# Сайт RyzikChat

Чёрно-белый сайт для Vercel (Node.js):

- `/` — главная, кнопка «Скачать» всегда ведёт на самый новый APK из релиза `ryzikchat-latest`;
- `/статистика` (и `/stats`) — живая статистика сервера, обновляется каждые 30 секунд;
- `/download` — короткая ссылка на последний APK;
- `/app` — веб-версия RyzikChat (тот же интерфейс, что в приложении, сквозное шифрование совместимо с Android и ПК).

### Веб-версия

Лежит в `public/app/` — обычные ES-модули без сборки. Ключи шифрования создаются в браузере
(tweetnacl + WebCrypto), закрытый ключ хранится в localStorage, обёрнутый неизвлекаемым ключом из IndexedDB.

- REST идёт через `/rc/*` — rewrite в `vercel.json` на сервер, поэтому нет проблем с CORS;
- WebSocket подключается к серверу напрямую (адрес даёт `/api/config`); если не вышло — веб-версия опрашивает сервер каждые 6 секунд;
- звонки — WebRTC с теми же сигналами, что в приложении.

Если сервер переехал, поменяйте адрес и в `RYZIKCHAT_SERVER`, и в правиле `/rc/:path*` в `vercel.json` (rewrite не умеет читать переменные окружения).

Статистику отдаёт сервер RyzikChat: `GET https://ryzikchat.serveousercontent.com/api/stats` (нужна версия сервера 0.8.6+).
Функции `api/stats.js` и `api/version.js` проксируют данные, чтобы браузеру не мешали CORS и страница-предупреждение туннеля.

## Как выложить на Vercel

**Через сайт:** vercel.com → Add New → Project → Import репозитория `RyzikChat` →
Root Directory: `website` → Framework Preset: Other → Deploy.

**Через терминал:**

```bash
cd website
npx vercel --prod
```

Другой адрес сервера можно задать переменной окружения `RYZIKCHAT_SERVER` в настройках проекта Vercel.
