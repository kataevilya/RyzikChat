"""Бот с командами и аргументами: /roll, /coin, /calc 2+2*2"""
import random
import re

from ryzikchat_bot import Bot

bot = Bot("ВСТАВЬТЕ_ТОКЕН_ОТ_RYZIKBOT", "https://адрес-вашего-сервера")


@bot.command("start", "help", description="Что я умею")
def start(msg):
    msg.reply("🎲 /roll — кубик\n🪙 /coin — монетка\n🧮 /calc 2+2*2 — калькулятор")


@bot.command("roll", description="Бросить кубик")
def roll(msg):
    msg.reply(f"🎲 Выпало: {random.randint(1, 6)}")


@bot.command("coin", description="Подбросить монетку")
def coin(msg):
    msg.reply(random.choice(["🪙 Орёл", "🪙 Решка"]))


@bot.command("calc", description="Калькулятор")
def calc(msg):
    expr = msg.args.replace(" ", "")
    if not expr or not re.fullmatch(r"[0-9+\-*/().]+", expr):
        return msg.reply("Напиши пример, например: /calc 2+2*2")
    try:
        msg.reply(f"🧮 {expr} = {eval(expr, {'__builtins__': {}})}")
    except Exception:
        msg.reply("Не получилось посчитать 🤔")


@bot.message()
def other(msg):
    msg.reply("Не понимаю 🙈 Команды — /help")


bot.run()
