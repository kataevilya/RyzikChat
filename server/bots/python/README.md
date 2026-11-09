# Боты RyzikChat на Python

## 1. Создайте бота

В RyzikChat найдите **@ryzikbot** и напишите `/newbot`: придумайте имя и юзернейм (в конце — `bot`).
RyzikBot пришлёт **токен API** и **адрес сервера**.

Там же: `/setavatar` — аватарка (пришлите фото), `/setdescription` — описание, `/setname` — имя,
`/token` — показать токен, `/revoke` — новый токен, `/deletebot` — удалить бота.

## 2. Запустите

```bash
pip install requests
python example_echo.py   # перед этим вставьте в файл токен и адрес сервера
```

```python
from ryzikchat_bot import Bot

bot = Bot("ТОКЕН", "https://адрес-сервера")

@bot.command("start")
def start(msg):
    msg.reply("Привет! 🦊")

@bot.message()
def echo(msg):
    msg.reply(msg.text)

bot.run()
```

Напишите своему боту в RyzikChat — он ответит.

## Что умеет `msg`

| | |
|---|---|
| `msg.text`, `msg.command`, `msg.args` | текст, команда без «/», текст после команды |
| `msg.sender.username`, `msg.sender.display_name` | кто написал |
| `msg.chat_id`, `msg.id` | чат и сообщение |
| `msg.file` | файл: `name`, `mime`, `size` (или `None`) |
| `msg.reply(text)`, `msg.reply_photo(path)`, `msg.reply_document(path)` | ответить |
| `msg.download(path)` | скачать файл из сообщения |

## Методы `bot`

`send_message(chat_id, text)`, `edit_message(message_id, text)`, `delete_message(message_id)`,
`send_photo(chat_id, path, caption)`, `send_document(...)`, `download_file(message_id, path)`,
`get_chat(chat_id)`, `set_name(name)`, `set_description(text)`, `set_avatar(path)`,
`set_commands({"start": "Начать"})` — кнопки команд над полем ввода в приложении
(или просто `@bot.command("start", description="Начать")`: при `bot.run()` они ставятся сами).

## HTTP API без библиотеки

Любой язык: `POST {сервер}/bot/{токен}/{метод}` с JSON, ответ `{"ok": true, "result": ...}`
или `{"ok": false, "error": "...", "description": "..."}`.

| Метод | Параметры |
|---|---|
| `getMe` | — |
| `getUpdates` | `offset`, `timeout` (до 50 с, long polling), `limit` |
| `sendMessage` | `chatId`, `text`, `replyTo` |
| `editMessageText` | `messageId`, `text` |
| `deleteMessage` | `messageId` |
| `sendPhoto`, `sendDocument` | multipart: `chatId`, `caption`, `file` |
| `getChat` | `chatId` |
| `setMyName`, `setMyDescription` | `name` / `description` |
| `setMyCommands` | `commands`: `[{"command": "start", "description": "Начать"}]` |
| `setMyAvatar` | multipart: `file` |

`GET {сервер}/bot/{токен}/file/{messageId}` — скачать файл из сообщения.

Бот может писать только в чаты, где он есть: человек сначала пишет боту сам.
Переписка с ботами не шифруется сквозным шифрованием, иначе бот не смог бы её прочитать.
Боты работают в личных сообщениях.
