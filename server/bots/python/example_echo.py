"""Простейший бот: отвечает тем же текстом. Запуск: python example_echo.py"""
from ryzikchat_bot import Bot

TOKEN = "ВСТАВЬТЕ_ТОКЕН_ОТ_RYZIKBOT"
SERVER = "https://адрес-вашего-сервера"

bot = Bot(TOKEN, SERVER)


@bot.command("start", description="Начать")
def start(msg):
    msg.reply(f"Привет, {msg.sender.display_name}! Напиши мне что-нибудь, и я повторю 🦊")


@bot.command("help", description="Помощь")
def help_(msg):
    msg.reply("/start — начать\n/help — помощь\nЛюбой текст — повторю его")


@bot.message()
def echo(msg):
    if msg.file:
        msg.reply(f"Получил файл «{msg.file['name']}», {msg.file['size']} байт")
    else:
        msg.reply(msg.text)


bot.run()
