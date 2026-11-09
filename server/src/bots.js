/**
 * Боты RyzikChat.
 *
 * Бот — это аккаунт с флагом is_bot, у которого вместо пароля токен. Создают ботов через
 * встроенного бота RyzikBot (как BotFather в Telegram), а управляют ими по HTTP:
 *
 *   GET/POST  /bot/<токен>/<метод>        → { ok: true, result } или { ok: false, error, description }
 *   GET       /bot/<токен>/file/<messageId> → файл из сообщения (уже расшифрованный)
 *
 * Переписка с ботом не шифруется сквозным шифрованием (приложение шлёт ботам открытый текст,
 * как в каналах), иначе бот не смог бы её прочитать. Файлы в такой переписке зашифрованы
 * ключом, который лежит прямо в сообщении, поэтому сервер может отдать их боту.
 */
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import multer from 'multer';

const MAX_BOTS = 20;
const MAX_TEXT = 4096;
const MAX_UPDATES = 1000;
const BOT_FILE_MB = 20;

const FATHER_COMMANDS = [
  { command: 'newbot', description: 'Создать нового бота' },
  { command: 'mybots', description: 'Мои боты' },
  { command: 'setavatar', description: 'Поставить аватарку' },
  { command: 'setdescription', description: 'Изменить описание' },
  { command: 'setname', description: 'Изменить имя' },
  { command: 'token', description: 'Показать токен API' },
  { command: 'revoke', description: 'Выпустить новый токен' },
  { command: 'deletebot', description: 'Удалить бота' },
  { command: 'cancel', description: 'Отменить действие' },
];

/** Файл в формате приложения: [iv 16][AES-256-CTR][HMAC-SHA256 32], ключ 64 байта (AES + HMAC). */
export function encryptFileBuffer(data) {
  const key = crypto.randomBytes(64), iv = crypto.randomBytes(16);
  const c = crypto.createCipheriv('aes-256-ctr', key.subarray(0, 32), iv);
  const body = Buffer.concat([c.update(data), c.final()]);
  const mac = crypto.createHmac('sha256', key.subarray(32)).update(iv).update(body).digest();
  return { blob: Buffer.concat([iv, body, mac]), key: key.toString('base64') };
}

export function decryptFileBuffer(blob, keyB64) {
  const key = Buffer.from(keyB64, 'base64');
  if (key.length !== 64 || blob.length < 48) throw new Error('bad file');
  const iv = blob.subarray(0, 16), body = blob.subarray(16, blob.length - 32), mac = blob.subarray(blob.length - 32);
  const want = crypto.createHmac('sha256', key.subarray(32)).update(iv).update(body).digest();
  if (!crypto.timingSafeEqual(mac, want)) throw new Error('bad mac');
  const d = crypto.createDecipheriv('aes-256-ctr', key.subarray(0, 32), iv);
  return Buffer.concat([d.update(body), d.final()]);
}

/** Размер картинки PNG/JPEG/WebP по заголовку (без сторонних библиотек). */
function imageSize(b) {
  try {
    if (b.length > 24 && b.readUInt32BE(0) === 0x89504e47) return { width: b.readUInt32BE(16), height: b.readUInt32BE(20) };
    if (b.length > 30 && b.toString('ascii', 0, 4) === 'RIFF' && b.toString('ascii', 8, 12) === 'WEBP') {
      const kind = b.toString('ascii', 12, 16);
      if (kind === 'VP8 ') return { width: b.readUInt16LE(26) & 0x3fff, height: b.readUInt16LE(28) & 0x3fff };
      if (kind === 'VP8L') { const v = b.readUInt32LE(21); return { width: (v & 0x3fff) + 1, height: ((v >> 14) & 0x3fff) + 1 }; }
      if (kind === 'VP8X') return { width: 1 + b.readUIntLE(24, 3), height: 1 + b.readUIntLE(27, 3) };
    }
    if (b[0] === 0xff && b[1] === 0xd8) {
      let i = 2;
      while (i + 9 < b.length) {
        if (b[i] !== 0xff) { i++; continue; }
        const marker = b[i + 1], len = b.readUInt16BE(i + 2);
        if (marker >= 0xc0 && marker <= 0xcf && ![0xc4, 0xc8, 0xcc].includes(marker)) return { width: b.readUInt16BE(i + 7), height: b.readUInt16BE(i + 5) };
        i += 2 + len;
      }
    }
  } catch { /* не картинка */ }
  return { width: 0, height: 0 };
}

class BotError extends Error {
  constructor(status, code, message) { super(message); this.status = status; this.code = code; }
}

export function setupBots(app, ctx) {
  const {
    db, hub, filesDir, getUserRow, getChatRow, publicUser, publicMessage, memberIds, broadcastChat, chatView,
    isBlocked, newId, now, USERNAME_RE, RYZIKBOT_ID, getUser,
  } = ctx;

  // ---------- встроенный RyzikBot ----------
  if (!getUserRow.get(RYZIKBOT_ID)) {
    db.prepare(`INSERT INTO users (id, username, display_name, password_hash, bio, public_key, encrypted_private_key, created_at, is_bot)
      VALUES (?, 'ryzikbot', 'RyzikBot', ?, ?, '', '', ?, 1)`)
      .run(RYZIKBOT_ID, 'disabled:' + crypto.randomBytes(16).toString('hex'), 'Создаю ботов для RyzikChat. Напишите /newbot', now());
  }
  db.prepare('UPDATE users SET is_bot = 1, bot_commands = ? WHERE id = ?').run(JSON.stringify(FATHER_COMMANDS), RYZIKBOT_ID);

  const botByToken = db.prepare('SELECT * FROM users WHERE bot_token = ? AND is_bot = 1');
  const newToken = (botId) => `${botId.replace(/-/g, '').slice(0, 12)}:${crypto.randomBytes(24).toString('base64url')}`;

  /** Сообщение от имени бота. Не шифруется: бот не участвует в сквозном шифровании. */
  function postAs(senderId, chatId, type, content, replyTo = null) {
    db.exec('BEGIN');
    let msg;
    try {
      const chat = getChatRow.get(chatId);
      const seq = chat.last_seq + 1, id = newId();
      db.prepare('UPDATE chats SET last_seq = ? WHERE id = ?').run(seq, chatId);
      db.prepare(`INSERT INTO messages (id, chat_id, seq, sender_id, type, payload, reply_to, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`)
        .run(id, chatId, seq, senderId, type, JSON.stringify({ v: 0, plain: content }), replyTo, now());
      db.prepare('UPDATE chat_members SET last_read_seq = ? WHERE chat_id = ? AND user_id = ?').run(seq, chatId, senderId);
      msg = publicMessage(db.prepare('SELECT * FROM messages WHERE id = ?').get(id));
      db.exec('COMMIT');
    } catch (e) { db.exec('ROLLBACK'); throw e; }
    const chat = getChatRow.get(chatId);
    if (chat.type === 'direct' && msg.seq === 1) {
      for (const uid of memberIds(chatId)) if (uid !== senderId) hub.sendToUsers([uid], { type: 'chat.new', chat: chatView(chatId, uid) });
    }
    broadcastChat(chatId, { type: 'message.new', message: msg });
    return msg;
  }

  function plainOf(payload) {
    try {
      const o = JSON.parse(payload ?? '');
      return o?.v === 0 && o.plain && typeof o.plain === 'object' ? o.plain : null;
    } catch { return null; }
  }

  function userBrief(id) {
    const u = getUserRow.get(id);
    return u ? { id: u.id, username: u.username, displayName: u.display_name, isBot: !!u.is_bot } : { id };
  }

  /** Сообщение глазами бота. */
  function botMessage(msg) {
    const chat = getChatRow.get(msg.chatId);
    const plain = plainOf(msg.payload);
    const out = {
      messageId: msg.id, chatId: msg.chatId, chatType: chat?.type ?? 'direct', chatTitle: chat?.title ?? '',
      date: msg.createdAt, from: userBrief(msg.senderId), type: msg.type, replyTo: msg.replyTo,
      text: plain ? String(plain.text ?? '') : null, encrypted: !plain,
    };
    if (plain?.file) out.file = { name: plain.file.name ?? '', mime: plain.file.mime ?? '', size: plain.file.size ?? 0, width: plain.file.width ?? 0, height: plain.file.height ?? 0 };
    if (plain?.sticker) out.sticker = { emoji: plain.sticker.emoji ?? '' };
    return out;
  }

  // ---------- очередь обновлений и long polling ----------
  const waiters = new Map();
  function pushUpdate(botId, update) {
    db.prepare('INSERT INTO bot_updates (bot_id, data, created_at) VALUES (?, ?, ?)').run(botId, JSON.stringify(update), now());
    db.prepare(`DELETE FROM bot_updates WHERE bot_id = ? AND id <= (SELECT id FROM bot_updates WHERE bot_id = ? ORDER BY id DESC LIMIT 1 OFFSET ?)`)
      .run(botId, botId, MAX_UPDATES);
    for (const wake of waiters.get(botId) ?? []) wake();
  }

  /** Вызывается после каждого нового сообщения в чате. */
  function onMessage(msg, req) {
    for (const uid of memberIds(msg.chatId)) {
      if (uid === msg.senderId) continue;
      const row = getUserRow.get(uid);
      if (!row?.is_bot) continue;
      if (uid === RYZIKBOT_ID) {
        if (getChatRow.get(msg.chatId)?.type === 'direct') {
          try { father(msg, req); } catch (e) { console.error('RyzikBot:', e); }
        }
        continue;
      }
      if (getUserRow.get(msg.senderId)?.is_bot) continue; // боты не переписываются между собой
      pushUpdate(uid, { message: botMessage(msg) });
    }
  }

  // ---------- RyzikBot: создание и настройка ботов ----------
  const getState = db.prepare('SELECT * FROM bot_father_state WHERE user_id = ?');
  const setState = (userId, step, botId = null, data = '') =>
    db.prepare('INSERT OR REPLACE INTO bot_father_state (user_id, step, bot_id, data) VALUES (?, ?, ?, ?)').run(userId, step, botId, data);
  const clearState = (userId) => db.prepare('DELETE FROM bot_father_state WHERE user_id = ?').run(userId);
  const myBots = (userId) => db.prepare('SELECT * FROM users WHERE is_bot = 1 AND bot_owner_id = ? ORDER BY created_at').all(userId);
  const usernameTaken = (name) => db.prepare('SELECT 1 FROM users WHERE username = ?').get(name) ||
    db.prepare('SELECT 1 FROM chats WHERE lower(username) = lower(?)').get(name) ||
    db.prepare('SELECT 1 FROM user_usernames WHERE username = ?').get(name);

  function serverUrl(req) {
    const proto = String(req?.headers?.['x-forwarded-proto'] ?? req?.protocol ?? 'http').split(',')[0].trim();
    const host = String(req?.headers?.['x-forwarded-host'] ?? req?.get?.('host') ?? 'localhost').split(',')[0].trim();
    return `${proto}://${host}`;
  }

  const HELP = 'Я помогу создать бота для RyzikChat и управлять им.\n\n' +
    '/newbot — создать нового бота\n/mybots — список ваших ботов\n\n' +
    'Настройка бота:\n/setname — имя\n/setdescription — описание\n/setavatar — аватарка\n\n' +
    'Токен API:\n/token — показать токен\n/revoke — выпустить новый, старый перестанет работать\n\n' +
    '/deletebot — удалить бота\n/cancel — отменить текущее действие\n\n' +
    'Бота пишут на Python: библиотека ryzikchat_bot.py и примеры лежат в папке bots/python сервера.';

  function tokenText(bot, req) {
    return `Токен API для @${bot.username}:\n\n${bot.bot_token}\n\nАдрес сервера: ${serverUrl(req)}\n\n` +
      'Храните токен в секрете: с ним кто угодно сможет управлять ботом. Новый токен — /revoke.\n\n' +
      'Пример на Python:\n\n' +
      'from ryzikchat_bot import Bot\n\n' +
      `bot = Bot("${bot.bot_token}", "${serverUrl(req)}")\n\n` +
      '@bot.command("start")\ndef start(msg):\n    msg.reply("Привет! Я бот RyzikChat 🦊")\n\n' +
      '@bot.message()\ndef echo(msg):\n    msg.reply(msg.text)\n\nbot.run()';
  }

  function father(msg, req) {
    const userId = msg.senderId, chatId = msg.chatId;
    const say = (text) => postAs(RYZIKBOT_ID, chatId, 'text', { text });
    const plain = plainOf(msg.payload);
    if (!plain) return say('Не могу прочитать сообщение. Обновите RyzikChat до последней версии и попробуйте ещё раз.');
    const text = String(plain.text ?? '').trim();
    const state = getState.get(userId);
    const cmdMatch = text.match(/^\/([a-zA-Z_]+)(?:@ryzikbot)?(?:\s+(.*))?$/i);

    if (cmdMatch) {
      const cmd = cmdMatch[1].toLowerCase(), arg = (cmdMatch[2] ?? '').trim();
      clearState(userId);
      if (cmd === 'start' || cmd === 'help') return say(HELP);
      if (cmd === 'cancel') return say(state ? 'Действие отменено.' : 'Нечего отменять. Список команд — /help');
      if (cmd === 'newbot') {
        if (myBots(userId).length >= MAX_BOTS) return say(`У вас уже ${MAX_BOTS} ботов, это максимум. Удалите ненужного через /deletebot.`);
        setState(userId, 'newbot_name');
        return say('Создаём бота. Как его назовём? Это имя увидят в чатах, например «Погода Рыжика».');
      }
      if (cmd === 'mybots') {
        const list = myBots(userId);
        if (!list.length) return say('У вас пока нет ботов. Создайте первого: /newbot');
        return say('Ваши боты:\n\n' + list.map((b) => `• ${b.display_name} — @${b.username}`).join('\n') +
          '\n\nНастроить: /setname, /setdescription, /setavatar. Токен: /token');
      }
      const actions = ['setname', 'setdescription', 'setavatar', 'token', 'revoke', 'deletebot'];
      if (actions.includes(cmd)) {
        const list = myBots(userId);
        if (!list.length) return say('У вас пока нет ботов. Создайте первого: /newbot');
        const pick = arg ? list.find((b) => b.username.toLowerCase() === arg.replace(/^@/, '').toLowerCase()) : list.length === 1 ? list[0] : null;
        if (arg && !pick) return say(`Бот ${arg} не найден среди ваших. Список — /mybots`);
        if (!pick) {
          setState(userId, 'choose', null, cmd);
          return say('Какого бота? Напишите его @username:\n\n' + list.map((b) => `@${b.username}`).join('\n'));
        }
        return startAction(cmd, pick, userId, say, req);
      }
      return say('Не знаю такой команды. Список команд — /help');
    }

    if (!state) return say(HELP);
    if (state.step === 'choose') {
      const pick = myBots(userId).find((b) => b.username.toLowerCase() === text.replace(/^@/, '').toLowerCase());
      if (!pick) return say('Такого бота у вас нет. Напишите @username из списка или /cancel');
      return startAction(state.data, pick, userId, say, req);
    }
    if (state.step === 'newbot_name') {
      const name = text.slice(0, 64);
      if (!name) return say('Имя не может быть пустым. Как назовём бота?');
      setState(userId, 'newbot_username', null, name);
      return say(`Отлично, «${name}». Теперь придумайте юзернейм: латиница, цифры и _, в конце обязательно «bot». Например: ryzik_weather_bot`);
    }
    if (state.step === 'newbot_username') {
      const username = text.replace(/^@/, '');
      if (!USERNAME_RE.test(username)) return say('Юзернейм: 3–32 символа, латиница, цифры и _. Попробуйте ещё раз.');
      if (!/bot$/i.test(username)) return say('Юзернейм бота должен заканчиваться на «bot», например ryzik_weather_bot. Попробуйте ещё раз.');
      if (usernameTaken(username)) return say('Этот юзернейм уже занят. Придумайте другой.');
      const id = newId();
      db.prepare(`INSERT INTO users (id, username, display_name, password_hash, bio, public_key, encrypted_private_key, created_at, last_seen,
        is_bot, bot_owner_id, bot_token) VALUES (?, ?, ?, ?, '', '', '', ?, ?, 1, ?, ?)`)
        .run(id, username, state.data, 'disabled:' + crypto.randomBytes(16).toString('hex'), now(), now(), userId, newToken(id));
      clearState(userId);
      const bot = getUserRow.get(id);
      say(`Готово! Бот ${bot.display_name} создан: @${bot.username}\n\nТеперь можно поставить аватарку (/setavatar) и описание (/setdescription).`);
      return say(tokenText(bot, req));
    }
    const bot = state.bot_id && getUserRow.get(state.bot_id);
    if (!bot || bot.bot_owner_id !== userId) { clearState(userId); return say(HELP); }
    if (state.step === 'setname') {
      const name = text.slice(0, 64);
      if (!name) return say('Имя не может быть пустым.');
      db.prepare('UPDATE users SET display_name = ? WHERE id = ?').run(name, bot.id);
      clearState(userId); botChanged(bot.id);
      return say(`Имя @${bot.username} теперь «${name}».`);
    }
    if (state.step === 'setdescription') {
      const desc = text === '-' ? '' : text.slice(0, 500);
      db.prepare('UPDATE users SET bio = ? WHERE id = ?').run(desc, bot.id);
      clearState(userId); botChanged(bot.id);
      return say(desc ? `Описание @${bot.username} обновлено.` : `Описание @${bot.username} удалено.`);
    }
    if (state.step === 'setavatar') {
      if (msg.type !== 'image' || !plain.file?.id || !plain.file?.key) return say('Пришлите картинку (как фото), и она станет аватаркой. Отменить — /cancel');
      const src = db.prepare('SELECT * FROM files WHERE id = ?').get(String(plain.file.id));
      let image;
      try { image = decryptFileBuffer(fs.readFileSync(path.join(filesDir, src.id)), plain.file.key); } catch {
        return say('Не получилось открыть картинку. Пришлите её ещё раз.');
      }
      const fileId = newId();
      fs.writeFileSync(path.join(filesDir, fileId), image);
      db.prepare('INSERT INTO files (id, owner_id, size, mime, created_at) VALUES (?, ?, ?, ?, ?)')
        .run(fileId, bot.id, image.length, String(plain.file.mime || 'image/jpeg').slice(0, 100), now());
      db.prepare('UPDATE users SET avatar_file_id = ? WHERE id = ?').run(fileId, bot.id);
      clearState(userId); botChanged(bot.id);
      return say(`Аватарка @${bot.username} обновлена 👍`);
    }
    if (state.step === 'deletebot') {
      if (text.replace(/^@/, '').toLowerCase() !== bot.username.toLowerCase()) return say(`Чтобы удалить, напишите @${bot.username}. Отменить — /cancel`);
      deleteBot(bot.id);
      clearState(userId);
      return say(`Бот @${bot.username} удалён.`);
    }
    clearState(userId);
    return say(HELP);
  }

  function startAction(cmd, bot, userId, say, req) {
    if (cmd === 'token') { clearState(userId); return say(tokenText(bot, req)); }
    if (cmd === 'revoke') {
      db.prepare('UPDATE users SET bot_token = ? WHERE id = ?').run(newToken(bot.id), bot.id);
      clearState(userId);
      return say('Старый токен больше не работает.\n\n' + tokenText(getUserRow.get(bot.id), req));
    }
    setState(userId, cmd, bot.id);
    if (cmd === 'setname') return say(`Новое имя для @${bot.username}?`);
    if (cmd === 'setdescription') return say(`Пришлите описание для @${bot.username}. Его увидят в профиле бота. Чтобы убрать описание, отправьте «-».`);
    if (cmd === 'setavatar') return say(`Пришлите картинку для аватарки @${bot.username}.`);
    if (cmd === 'deletebot') return say(`Точно удалить @${bot.username}? Это нельзя отменить. Для подтверждения напишите @${bot.username}`);
  }

  function botChanged(botId) {
    hub.broadcastUser(botId, { type: 'user.updated', user: getUser(botId) });
  }

  function deleteBot(botId) {
    const direct = db.prepare(`SELECT c.id FROM chats c JOIN chat_members m ON m.chat_id = c.id WHERE m.user_id = ? AND c.type = 'direct'`).all(botId);
    for (const c of direct) {
      const members = memberIds(c.id);
      db.prepare('DELETE FROM chats WHERE id = ?').run(c.id);
      hub.sendToUsers(members, { type: 'chat.removed', chatId: c.id });
    }
    db.prepare('DELETE FROM chat_members WHERE user_id = ?').run(botId);
    db.prepare('DELETE FROM bot_updates WHERE bot_id = ?').run(botId);
    db.prepare('DELETE FROM users WHERE id = ?').run(botId);
  }

  // ---------- Bot API ----------
  const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: BOT_FILE_MB * 1024 * 1024 } });

  function requireBotChat(bot, chatId) {
    const chat = getChatRow.get(String(chatId ?? ''));
    if (!chat || !memberIds(chat.id).includes(bot.id)) throw new BotError(404, 'chat_not_found', 'Чат не найден или бот в нём не состоит');
    if (chat.banned) throw new BotError(403, 'chat_banned', 'Чат заблокирован модерацией');
    if (chat.type === 'channel') {
      const m = db.prepare('SELECT role FROM chat_members WHERE chat_id = ? AND user_id = ?').get(chat.id, bot.id);
      if (!['owner', 'admin'].includes(m?.role)) throw new BotError(403, 'forbidden', 'Писать в канал может только бот-админ');
    }
    if (chat.type === 'direct') {
      const peer = memberIds(chat.id).find((id) => id !== bot.id);
      if (peer && isBlocked(peer, bot.id)) throw new BotError(403, 'blocked', 'Пользователь заблокировал бота');
    }
    return chat;
  }

  function ownMessage(bot, messageId) {
    const row = db.prepare('SELECT * FROM messages WHERE id = ?').get(String(messageId ?? ''));
    if (!row || row.sender_id !== bot.id || row.deleted) throw new BotError(404, 'message_not_found', 'Сообщение не найдено или написано не ботом');
    return row;
  }

  function cleanText(t, required = true) {
    const s = String(t ?? '');
    if (required && !s.trim()) throw new BotError(400, 'empty_text', 'Пустой текст');
    if (s.length > MAX_TEXT) throw new BotError(400, 'text_too_long', `Текст длиннее ${MAX_TEXT} символов`);
    return s;
  }

  function storeEncrypted(bot, buf, name, mime) {
    const { blob, key } = encryptFileBuffer(buf);
    const id = newId();
    fs.writeFileSync(path.join(filesDir, id), blob);
    db.prepare('INSERT INTO files (id, owner_id, size, mime, created_at) VALUES (?, ?, ?, ?, ?)').run(id, bot.id, blob.length, 'application/octet-stream', now());
    return { id, key, name: String(name || 'file').slice(0, 200), size: buf.length, mime: String(mime || 'application/octet-stream').slice(0, 100) };
  }

  const methods = {
    getMe: (bot) => ({ ...publicUser(bot), description: bot.bio }),

    getUpdates: async (bot, p, req) => {
      const offset = Number(p.offset ?? 0) || 0;
      const limit = Math.min(Math.max(Number(p.limit ?? 100) || 100, 1), 100);
      const timeout = Math.min(Math.max(Number(p.timeout ?? 0) || 0, 0), 50);
      if (offset > 0) db.prepare('DELETE FROM bot_updates WHERE bot_id = ? AND id < ?').run(bot.id, offset);
      const read = () => db.prepare('SELECT * FROM bot_updates WHERE bot_id = ? AND id >= ? ORDER BY id LIMIT ?').all(bot.id, offset, limit);
      let rows = read();
      if (!rows.length && timeout > 0) {
        await new Promise((resolve) => {
          const set = waiters.get(bot.id) ?? new Set();
          waiters.set(bot.id, set);
          const done = () => { clearTimeout(timer); set.delete(done); if (!set.size) waiters.delete(bot.id); resolve(); };
          const timer = setTimeout(done, timeout * 1000);
          set.add(done);
          req.res?.on('close', done);
        });
        rows = read();
      }
      return rows.map((r) => ({ updateId: r.id, ...JSON.parse(r.data) }));
    },

    sendMessage: (bot, p) => {
      const chat = requireBotChat(bot, p.chatId);
      return botMessage(postAs(bot.id, chat.id, 'text', { text: cleanText(p.text) }, p.replyTo ? String(p.replyTo) : null));
    },

    editMessageText: (bot, p) => {
      const row = ownMessage(bot, p.messageId);
      const plain = plainOf(row.payload) ?? {};
      db.prepare('UPDATE messages SET payload = ?, edited_at = ? WHERE id = ?')
        .run(JSON.stringify({ v: 0, plain: { ...plain, text: cleanText(p.text, !plain.file) } }), now(), row.id);
      const msg = publicMessage(db.prepare('SELECT * FROM messages WHERE id = ?').get(row.id));
      broadcastChat(row.chat_id, { type: 'message.updated', message: msg });
      return botMessage(msg);
    },

    deleteMessage: (bot, p) => {
      const row = ownMessage(bot, p.messageId);
      db.prepare("UPDATE messages SET deleted = 1, payload = '' WHERE id = ?").run(row.id);
      broadcastChat(row.chat_id, { type: 'message.updated', message: publicMessage(db.prepare('SELECT * FROM messages WHERE id = ?').get(row.id)) });
      return true;
    },

    sendPhoto: (bot, p, req) => {
      const chat = requireBotChat(bot, p.chatId);
      if (!req.file) throw new BotError(400, 'no_file', 'Нет файла: пришлите его в поле file (multipart/form-data)');
      const file = { ...storeEncrypted(bot, req.file.buffer, req.file.originalname, req.file.mimetype || 'image/jpeg'), ...imageSize(req.file.buffer) };
      return botMessage(postAs(bot.id, chat.id, 'image', { text: cleanText(p.caption, false), file }, p.replyTo ? String(p.replyTo) : null));
    },

    sendDocument: (bot, p, req) => {
      const chat = requireBotChat(bot, p.chatId);
      if (!req.file) throw new BotError(400, 'no_file', 'Нет файла: пришлите его в поле file (multipart/form-data)');
      const file = storeEncrypted(bot, req.file.buffer, req.file.originalname, req.file.mimetype);
      return botMessage(postAs(bot.id, chat.id, 'file', { text: cleanText(p.caption, false), file }, p.replyTo ? String(p.replyTo) : null));
    },

    getChat: (bot, p) => {
      const chat = requireBotChat(bot, p.chatId);
      const ids = memberIds(chat.id);
      return { id: chat.id, type: chat.type, title: chat.title, memberCount: ids.length, members: chat.type === 'direct' ? ids.map(userBrief) : undefined };
    },

    setMyName: (bot, p) => {
      const name = String(p.name ?? '').trim().slice(0, 64);
      if (!name) throw new BotError(400, 'bad_name', 'Пустое имя');
      db.prepare('UPDATE users SET display_name = ? WHERE id = ?').run(name, bot.id);
      botChanged(bot.id);
      return true;
    },

    setMyDescription: (bot, p) => {
      db.prepare('UPDATE users SET bio = ? WHERE id = ?').run(String(p.description ?? '').slice(0, 500), bot.id);
      botChanged(bot.id);
      return true;
    },

    setMyCommands: (bot, p) => {
      let list = p.commands;
      if (typeof list === 'string') { try { list = JSON.parse(list); } catch { list = null; } }
      if (!Array.isArray(list) || list.length > 30) throw new BotError(400, 'bad_commands', 'commands — список до 30 команд вида {command, description}');
      const clean = list.map((c) => ({
        command: String(c?.command ?? '').replace(/^\//, '').toLowerCase(),
        description: String(c?.description ?? '').slice(0, 100),
      }));
      if (clean.some((c) => !/^[a-z0-9_]{1,32}$/.test(c.command))) throw new BotError(400, 'bad_commands', 'Команда: латиница, цифры и _, до 32 символов');
      db.prepare('UPDATE users SET bot_commands = ? WHERE id = ?').run(JSON.stringify(clean), bot.id);
      botChanged(bot.id);
      return true;
    },

    setMyAvatar: (bot, _p, req) => {
      if (!req.file) throw new BotError(400, 'no_file', 'Нет картинки: пришлите её в поле file (multipart/form-data)');
      const id = newId();
      fs.writeFileSync(path.join(filesDir, id), req.file.buffer);
      db.prepare('INSERT INTO files (id, owner_id, size, mime, created_at) VALUES (?, ?, ?, ?, ?)')
        .run(id, bot.id, req.file.size, String(req.file.mimetype || 'image/jpeg').slice(0, 100), now());
      db.prepare('UPDATE users SET avatar_file_id = ? WHERE id = ?').run(id, bot.id);
      botChanged(bot.id);
      return true;
    },
  };

  function auth(req) {
    const bot = req.params.token && botByToken.get(String(req.params.token));
    if (!bot || bot.id === RYZIKBOT_ID) throw new BotError(401, 'unauthorized', 'Неверный токен бота');
    return bot;
  }

  function fail(res, e) {
    if (e instanceof BotError) return res.status(e.status).json({ ok: false, error: e.code, description: e.message });
    if (e?.code === 'LIMIT_FILE_SIZE') return res.status(413).json({ ok: false, error: 'too_large', description: `Файл больше ${BOT_FILE_MB} МБ` });
    console.error('Bot API:', e);
    res.status(500).json({ ok: false, error: 'internal', description: 'Ошибка сервера' });
  }

  // Файл из сообщения, которое видит бот (сервер расшифровывает его ключом из сообщения).
  app.get('/bot/:token/file/:messageId', (req, res) => {
    try {
      const bot = auth(req);
      const row = db.prepare('SELECT * FROM messages WHERE id = ?').get(String(req.params.messageId));
      if (!row || row.deleted || !memberIds(row.chat_id).includes(bot.id)) throw new BotError(404, 'message_not_found', 'Сообщение не найдено');
      const f = plainOf(row.payload)?.file;
      if (!f?.id || !f?.key) throw new BotError(404, 'no_file', 'В сообщении нет файла');
      const data = decryptFileBuffer(fs.readFileSync(path.join(filesDir, String(f.id))), f.key);
      res.type(f.mime || 'application/octet-stream');
      res.set('Content-Disposition', `attachment; filename*=UTF-8''${encodeURIComponent(f.name || 'file')}`);
      res.send(data);
    } catch (e) {
      if (e?.code === 'ENOENT') return fail(res, new BotError(404, 'file_missing', 'Файл не найден на сервере'));
      if (e?.message === 'bad mac' || e?.message === 'bad file') return fail(res, new BotError(422, 'bad_file', 'Файл повреждён'));
      fail(res, e);
    }
  });

  app.all('/bot/:token/:method', (req, res) => {
    upload.single('file')(req, res, async (err) => {
      if (err) return fail(res, err);
      try {
        const bot = auth(req);
        const fn = Object.hasOwn(methods, req.params.method) ? methods[req.params.method] : null;
        if (!fn) throw new BotError(404, 'unknown_method', `Нет метода ${req.params.method}`);
        const params = { ...req.query, ...(req.body && typeof req.body === 'object' && !Array.isArray(req.body) ? req.body : {}) };
        const result = await fn(bot, params, req);
        if (!res.headersSent) res.json({ ok: true, result });
      } catch (e) { if (!res.headersSent) fail(res, e); }
    });
  });

  return { onMessage, pushUpdate, deleteBot };
}
