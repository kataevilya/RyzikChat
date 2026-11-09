"""
Библиотека для ботов RyzikChat.

    pip install requests

    from ryzikchat_bot import Bot

    bot = Bot("ТОКЕН", "https://адрес-сервера")

    @bot.command("start")
    def start(msg):
        msg.reply("Привет! 🦊")

    @bot.message()
    def echo(msg):
        msg.reply(msg.text)

    bot.run()

Токен и адрес сервера выдаёт RyzikBot в RyzikChat (команда /newbot или /token).
"""
import json
import time
import traceback

import requests

__all__ = ["Bot", "Message", "RyzikError"]
__version__ = "1.0"


class RyzikError(Exception):
    """Ошибка Bot API: code — короткий код, description — текст для человека."""

    def __init__(self, code, description, status=0):
        super().__init__(f"{code}: {description}")
        self.code, self.description, self.status = code, description, status


class User:
    def __init__(self, d):
        d = d or {}
        self.id = d.get("id")
        self.username = d.get("username", "")
        self.display_name = d.get("displayName", "")
        self.is_bot = bool(d.get("isBot", False))

    def __repr__(self):
        return f"User(@{self.username})"


class Message:
    """Входящее сообщение. Ответить — msg.reply("текст")."""

    def __init__(self, bot, d):
        self.bot = bot
        self.raw = d
        self.id = d.get("messageId")
        self.chat_id = d.get("chatId")
        self.chat_type = d.get("chatType", "direct")
        self.date = d.get("date", 0)
        self.type = d.get("type", "text")
        self.text = d.get("text") or ""
        self.encrypted = bool(d.get("encrypted"))
        self.reply_to = d.get("replyTo")
        self.file = d.get("file")
        self.sender = User(d.get("from"))

    @property
    def command(self):
        """Команда без «/» (для «/start привет» — "start") или None."""
        if not self.text.startswith("/"):
            return None
        return self.text[1:].split(maxsplit=1)[0].split("@")[0].lower() if len(self.text) > 1 else None

    @property
    def args(self):
        """Текст после команды: для «/weather Москва» — "Москва"."""
        parts = self.text.split(maxsplit=1)
        return parts[1] if self.command and len(parts) > 1 else ""

    def reply(self, text, quote=False):
        return self.bot.send_message(self.chat_id, text, reply_to=self.id if quote else None)

    def reply_photo(self, path, caption=""):
        return self.bot.send_photo(self.chat_id, path, caption)

    def reply_document(self, path, caption=""):
        return self.bot.send_document(self.chat_id, path, caption)

    def download(self, path=None):
        """Скачать файл из сообщения. Без path — вернуть байты."""
        return self.bot.download_file(self.id, path)

    def __repr__(self):
        return f"Message({self.sender}: {self.text!r})"


class Bot:
    def __init__(self, token, server, timeout=30):
        self.token = token
        self.server = server.rstrip("/")
        self.poll_timeout = timeout
        self.http = requests.Session()
        # Сервер обычно за ngrok: этот заголовок убирает его страницу-предупреждение.
        self.http.headers["ngrok-skip-browser-warning"] = "1"
        self._commands = {}
        self._descriptions = {}
        self._handlers = []
        self._offset = 0

    # ---------- запросы ----------

    def call(self, method, params=None, files=None, timeout=None):
        url = f"{self.server}/bot/{self.token}/{method}"
        if files:
            r = self.http.post(url, data=params or {}, files=files, timeout=timeout or 120)
        else:
            r = self.http.post(url, json=params or {}, timeout=timeout or 60)
        try:
            data = r.json()
        except ValueError:
            raise RyzikError("bad_response", f"Сервер ответил не JSON (HTTP {r.status_code})", r.status_code)
        if not data.get("ok"):
            raise RyzikError(data.get("error", "error"), data.get("description", ""), r.status_code)
        return data.get("result")

    def get_me(self):
        return self.call("getMe")

    def get_updates(self, offset=0, timeout=0, limit=100):
        return self.call("getUpdates", {"offset": offset, "timeout": timeout, "limit": limit}, timeout=timeout + 15)

    def send_message(self, chat_id, text, reply_to=None):
        p = {"chatId": chat_id, "text": str(text)}
        if reply_to:
            p["replyTo"] = reply_to
        return self.call("sendMessage", p)

    def edit_message(self, message_id, text):
        return self.call("editMessageText", {"messageId": message_id, "text": str(text)})

    def delete_message(self, message_id):
        return self.call("deleteMessage", {"messageId": message_id})

    def _send_file(self, method, chat_id, path, caption):
        with open(path, "rb") as f:
            return self.call(method, {"chatId": chat_id, "caption": caption}, files={"file": f})

    def send_photo(self, chat_id, path, caption=""):
        return self._send_file("sendPhoto", chat_id, path, caption)

    def send_document(self, chat_id, path, caption=""):
        return self._send_file("sendDocument", chat_id, path, caption)

    def download_file(self, message_id, path=None):
        r = self.http.get(f"{self.server}/bot/{self.token}/file/{message_id}", timeout=120)
        if r.status_code != 200:
            try:
                d = r.json()
                raise RyzikError(d.get("error", "error"), d.get("description", ""), r.status_code)
            except ValueError:
                raise RyzikError("http", f"HTTP {r.status_code}", r.status_code)
        if path is None:
            return r.content
        with open(path, "wb") as f:
            f.write(r.content)
        return path

    def get_chat(self, chat_id):
        return self.call("getChat", {"chatId": chat_id})

    def set_name(self, name):
        return self.call("setMyName", {"name": name})

    def set_description(self, description):
        return self.call("setMyDescription", {"description": description})

    def set_avatar(self, path):
        with open(path, "rb") as f:
            return self.call("setMyAvatar", files={"file": f})

    def set_commands(self, commands):
        """commands — {"start": "Начать", "help": "Помощь"} или список словарей {command, description}."""
        if isinstance(commands, dict):
            commands = [{"command": k, "description": v} for k, v in commands.items()]
        return self.call("setMyCommands", {"commands": commands})

    # ---------- обработчики ----------

    def command(self, *names, description=""):
        """@bot.command("start", description="Начать") — обработчик команды /start.
        Команды с описанием показываются кнопками над полем ввода в приложении."""
        def deco(fn):
            for n in names:
                self._commands[n.lstrip("/").lower()] = fn
                if description:
                    self._descriptions[n.lstrip("/").lower()] = description
            return fn
        return deco

    def message(self, func=None):
        """@bot.message() — любое сообщение; @bot.message(lambda m: "привет" in m.text) — с фильтром."""
        def deco(fn):
            self._handlers.append((func, fn))
            return fn
        return deco

    def process(self, update):
        d = update.get("message")
        if not d:
            return
        msg = Message(self, d)
        handler = self._commands.get(msg.command) if msg.command else None
        if handler is None:
            for flt, fn in self._handlers:
                if flt is None or flt(msg):
                    handler = fn
                    break
        if handler:
            handler(msg)

    def run(self):
        """Получать сообщения и вызывать обработчики. Останов — Ctrl+C."""
        me = self.get_me()
        print(f"Бот @{me['username']} запущен. Остановить — Ctrl+C")
        if self._descriptions:
            try:
                self.set_commands(self._descriptions)
            except RyzikError:
                pass
        delay = 1
        while True:
            try:
                for up in self.get_updates(self._offset, self.poll_timeout):
                    self._offset = up["updateId"] + 1
                    try:
                        self.process(up)
                    except Exception:
                        traceback.print_exc()
                delay = 1
            except KeyboardInterrupt:
                print("Бот остановлен")
                return
            except RyzikError as e:
                if e.status == 401:
                    raise
                print("Ошибка API:", e)
                time.sleep(delay)
                delay = min(delay * 2, 30)
            except requests.RequestException as e:
                print("Нет связи с сервером:", e)
                time.sleep(delay)
                delay = min(delay * 2, 30)
