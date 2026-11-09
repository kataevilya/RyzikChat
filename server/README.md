# Сервер RyzikChat

Собственный API мессенджера: REST + WebSocket, хранение в SQLite (встроенный `node:sqlite`).
Сервер **не видит** содержимого сообщений и файлов: они приходят уже зашифрованными с устройства.

## Запуск

```bash
cd server
npm install
npm start            # http://0.0.0.0:8080, данные в ../data (папка data рядом с папкой сервера)
```

Или в Docker:

```bash
docker build -t ryzikchat-server server
docker run -d -p 8080:8080 -v ryzik-data:/data ryzikchat-server
```

Переменные окружения:

| Переменная | Что делает | По умолчанию |
|---|---|---|
| `PORT` | порт | `8080` |
| `DATA_DIR` | где лежат база и файлы | `./data` |
| `ADMIN_USERNAMES` | кто станет админом при регистрации, через запятую | первый зарегистрированный |
| `AUTO_UPDATE` | сервер сам обновляется с GitHub раз в 30 минут (релиз `ryzikchat-latest`): делает копию базы в `data/backups`, заменяет код, папку `data` не трогает и перезапускается. `off` — выключить | включено |
| `UPDATE_SOURCE` | раздавать сборки приложения с этого сервера: `github` — забирать их с GitHub, или адрес папки с `update.json` и `RyzikChat.apk`. Приложение и без этого проверяет обновления на GitHub само | `off` |
| `PREMIUM_MONTH_FLUX` | цена месяца Премиума в FLUX | `1000` |
| `MAX_FILE_MB` | максимальный размер файла | `2048` |

Первый зарегистрированный пользователь автоматически становится администратором и может выдавать бейджи.

## Самообновление

`npm start` запускает сервер под присмотром (`src/run.js`): после самообновления сервер выходит с кодом 75
и сразу запускается заново уже с новой версией, а если упадёт — перезапускается через 3 секунды.
Состояние и кнопка «Обновить сейчас» — в админ-панели приложения, вкладка «Сервер»
(`GET /api/admin/server`, `POST /api/admin/server/update`). Запуск без присмотра: `npm run serve`.

## API (кратко)

Все запросы, кроме `/api/health` и `/api/auth/*`, требуют заголовок `Authorization: Bearer <token>`.

| Метод | Путь | Назначение |
|---|---|---|
| POST | `/api/auth/register` | регистрация (`username, displayName, password, publicKey, encryptedPrivateKey`) |
| POST | `/api/auth/login` | вход |
| GET/PATCH | `/api/me` | профиль |
| GET | `/api/users/search?q=` | поиск людей |
| GET | `/api/chats` | список чатов |
| POST | `/api/chats/direct`, `/api/chats/group` | новый чат / группа |
| GET/POST | `/api/chats/:id/messages` | история / отправка (payload — шифротекст) |
| PATCH/DELETE | `/api/messages/:id` | изменить / удалить |
| PUT | `/api/messages/:id/reaction` | реакция |
| POST/GET | `/api/files`, `/api/files/:id` | загрузка / скачивание зашифрованных файлов |
| GET | `/api/badges` | список бейджей |
| POST/DELETE | `/api/admin/badges` | создать / удалить бейдж (только админ) |
| PUT/DELETE | `/api/admin/users/:id/badges/:badgeId` | выдать / снять бейдж (только админ) |
| PUT | `/api/admin/users/:id/admin` | назначить админа |
| WS | `/ws?token=` | события: `message.new`, `message.updated`, `typing`, `read`, `presence`, `user.updated`, `chat.*` |

Тесты: `npm test`.
