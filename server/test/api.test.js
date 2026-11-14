import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import WebSocket from 'ws';
import { startServer } from '../src/index.js';

let srv, base, dir;

before(async () => {
  dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ryzik-'));
  srv = await startServer({ port: 0, dataDir: dir });
  base = `http://127.0.0.1:${srv.port}`;
});
after(() => { srv.wss.close(); srv.server.closeAllConnections(); srv.server.close(); fs.rmSync(dir, { recursive: true, force: true }); });

async function api(method, url, body, token) {
  const res = await fetch(base + url, {
    method,
    headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: res.status, body: await res.json() };
}

let aliceToken;

const reg = (username) => api('POST', '/api/auth/register', {
  username, displayName: username.toUpperCase(), password: 'x'.repeat(64), publicKey: 'pk-' + username, encryptedPrivateKey: 'enc-' + username,
});

test('регистрация, чаты, сообщения, бейджи, websocket', async () => {
  const a = await reg('alice');
  assert.equal(a.status, 201);
  assert.equal(a.body.user.isAdmin, true, 'первый пользователь — администратор');
  const b = await reg('bob');
  assert.equal(b.body.user.isAdmin, false);
  assert.equal((await reg('Alice')).status, 409);

  aliceToken = a.body.token;
  // Без двухэтапной проверки вход сразу выдаёт токен, а в RyzikChat Info приходит уведомление о входе.
  const plain = await api('POST', '/api/auth/login', { username: 'bob', password: 'x'.repeat(64), device: 'Pixel' });
  assert.ok(plain.body.token);
  assert.equal(plain.body.encryptedPrivateKey, 'enc-bob');

  // Включаем двухэтапную проверку: нужен пароль от аккаунта.
  assert.equal((await api('PUT', '/api/me/2fa', { accountPassword: 'bad', password: 'tiger' }, b.body.token)).status, 403);
  const on = await api('PUT', '/api/me/2fa', { accountPassword: 'x'.repeat(64), password: 'tiger', hint: 'кот' }, b.body.token);
  assert.equal(on.body.has2fa, true);
  assert.equal(on.body.twofaHint, 'кот');
  const step1 = await api('POST', '/api/auth/login', { username: 'bob', password: 'x'.repeat(64), device: 'Pixel' });
  assert.equal(step1.body.need2fa, true);
  assert.equal(step1.body.hint, 'кот');
  assert.equal(step1.body.token, undefined);
  assert.equal((await api('POST', '/api/auth/login/2fa', { challengeId: step1.body.challengeId, password: 'lion' })).body.error, 'bad_2fa');
  const login = await api('POST', '/api/auth/login/2fa', { challengeId: step1.body.challengeId, password: 'tiger' });
  assert.equal(login.status, 200);
  assert.ok(login.body.token);
  assert.equal((await api('POST', '/api/auth/login/2fa', { challengeId: step1.body.challengeId, password: 'tiger' })).status, 400, 'вход одноразовый');
  // Смена требует текущий пароль, выключение — тоже.
  assert.equal((await api('PUT', '/api/me/2fa', { accountPassword: 'x'.repeat(64), currentPassword: 'no', password: 'puma' }, b.body.token)).status, 403);
  assert.equal((await api('DELETE', '/api/me/2fa', { password: 'no' }, b.body.token)).status, 403);
  assert.equal((await api('DELETE', '/api/me/2fa', { password: 'tiger' }, b.body.token)).body.has2fa, false);
  assert.equal((await api('POST', '/api/auth/login', { username: 'bob', password: 'nope' })).status, 401);

  const chatsA = await api('GET', '/api/chats', null, a.body.token);
  // Избранное, RyzikChat Info, общая группа «RyzikChat (Обсуждение)» и канал «Новости RyzikChat».
  assert.deepEqual(chatsA.body.map((c) => c.type).sort(), ['channel', 'channel', 'group', 'saved']);
  const discussion = chatsA.body.find((c) => c.type === 'group');
  assert.equal(discussion.title, 'RyzikChat (Обсуждение)');
  assert.ok(discussion.members.length >= 2, 'в общей группе все пользователи');
  assert.equal((await api('DELETE', `/api/chats/${discussion.id}/members/${a.body.user.id}`, null, a.body.token)).body.error, 'discussion_leave');
  const infoA = chatsA.body.find((c) => c.isService);
  assert.equal(infoA.title, 'RyzikChat Info');
  assert.equal(infoA.pinned, true);
  assert.equal((await api('DELETE', `/api/chats/${infoA.id}/members/${a.body.user.id}`, null, a.body.token)).status, 400);

  const found = await api('GET', '/api/users/search?q=bo', null, a.body.token);
  assert.equal(found.body[0].username, 'bob');

  // Bob слушает websocket
  const ws = new WebSocket(`${base.replace('http', 'ws')}/ws?token=${b.body.token}`);
  const events = [];
  ws.on('message', (d) => events.push(JSON.parse(String(d))));
  await new Promise((r) => ws.on('open', r));

  const direct = await api('POST', '/api/chats/direct', { userId: b.body.user.id }, a.body.token);
  assert.equal(direct.status, 201);
  const again = await api('POST', '/api/chats/direct', { userId: b.body.user.id }, a.body.token);
  assert.equal(again.body.id, direct.body.id);

  const sent = await api('POST', `/api/chats/${direct.body.id}/messages`, { type: 'text', payload: '{"ct":"..."}' }, a.body.token);
  assert.equal(sent.status, 201);
  assert.equal(sent.body.seq, 1);

  const chatsB = await api('GET', '/api/chats', null, b.body.token);
  const dB = chatsB.body.find((c) => c.id === direct.body.id);
  assert.equal(dB.unread, 1);

  await api('PUT', `/api/messages/${sent.body.id}/reaction`, { emoji: '🔥' }, b.body.token);
  const msgs = await api('GET', `/api/chats/${direct.body.id}/messages`, null, b.body.token);
  assert.deepEqual(msgs.body[0].reactions, [{ userId: b.body.user.id, emoji: '🔥' }]);

  // Bob не может удалить сообщение Alice
  assert.equal((await api('DELETE', `/api/messages/${sent.body.id}`, null, b.body.token)).status, 403);

  // Бейджи: только админ
  assert.equal((await api('POST', '/api/admin/badges', { emoji: '⭐', title: 'Звезда' }, b.body.token)).status, 403);
  const badge = await api('POST', '/api/admin/badges', { emoji: '⭐', title: 'Звезда', color: '#FFB300' }, a.body.token);
  assert.equal(badge.status, 201);
  const granted = await api('PUT', `/api/admin/users/${b.body.user.id}/badges/${badge.body.id}`, null, a.body.token);
  assert.equal(granted.body.badges[0].title, 'Звезда');

  await new Promise((r) => setTimeout(r, 100));
  const types = events.map((e) => e.type);
  assert.ok(types.includes('chat.new'));
  assert.ok(types.includes('message.new'));
  assert.ok(types.includes('user.updated'));
  ws.close();
});

test('каналы, премиум, сигналы звонков, документация API', async () => {
  const owner = await reg('postowner');
  const fan = await reg('chanfan');
  const ch = await api('POST', '/api/chats/channel', { title: 'Новости Рыжика', description: 'Всё самое важное' }, owner.body.token);
  assert.equal(ch.status, 201);
  assert.equal(ch.body.type, 'channel');
  assert.equal(ch.body.myRole, 'owner');

  const found = await api('GET', '/api/channels/search?q=Рыжик', null, fan.body.token);
  assert.equal(found.body[0].id, ch.body.id);
  assert.equal(found.body[0].myRole, null);

  // Публичный @юзернейм: поиск, открытие по ссылке, занятость, освобождение при переходе в частный.
  assert.equal((await api('PATCH', `/api/chats/${ch.body.id}`, { username: 'ab' }, owner.body.token)).body.error, 'bad_username');
  assert.equal((await api('PATCH', `/api/chats/${ch.body.id}`, { username: 'chanfan' }, owner.body.token)).body.error, 'username_taken');
  assert.equal((await api('PATCH', `/api/chats/${ch.body.id}`, { username: 'ryzik_news' }, owner.body.token)).body.username, 'ryzik_news');
  assert.equal((await api('GET', '/api/channels/search?q=@ryzik_news', null, fan.body.token)).body[0].id, ch.body.id);
  assert.equal((await api('GET', '/api/chats/by-username/Ryzik_News', null, fan.body.token)).body.id, ch.body.id);
  assert.equal((await api('GET', '/api/chat-username-check?username=ryzik_news', null, fan.body.token)).body.ok, false);
  const grp = await api('POST', '/api/chats/group', { title: 'Клуб', username: 'ryzik_club' }, fan.body.token);
  assert.equal(grp.body.isPublic, true);
  assert.equal(grp.body.username, 'ryzik_club');
  assert.equal((await api('POST', '/api/chats/group', { title: 'Клуб 2', username: 'RYZIK_CLUB' }, owner.body.token)).body.error, 'username_taken');
  assert.equal((await api('POST', '/api/auth/register', { username: 'ryzik_club', password: 'x'.repeat(64), publicKey: 'k', encryptedPrivateKey: 'k' })).body.error, 'username_taken');
  await api('PATCH', `/api/chats/${grp.body.id}`, { isPublic: false }, fan.body.token);
  assert.equal((await api('GET', '/api/chat-username-check?username=ryzik_club', null, fan.body.token)).body.ok, true);

  const post = await api('POST', `/api/chats/${ch.body.id}/messages`, { type: 'text', payload: '{"v":0,"plain":{"text":"Привет"}}' }, owner.body.token);
  assert.equal(post.status, 201);
  // Не подписан, но читать публичный канал можно
  assert.equal((await api('GET', `/api/chats/${ch.body.id}/messages`, null, fan.body.token)).body.length, 1);

  const sub = await api('POST', `/api/chats/${ch.body.id}/subscribe`, null, fan.body.token);
  assert.equal(sub.body.myRole, 'subscriber');
  assert.equal(sub.body.memberCount, 2);
  assert.equal(sub.body.unread, 0);
  // Подписчик писать не может
  assert.equal((await api('POST', `/api/chats/${ch.body.id}/messages`, { type: 'text', payload: 'x' }, fan.body.token)).status, 403);
  // Отписка
  assert.equal((await api('DELETE', `/api/chats/${ch.body.id}/members/${fan.body.user.id}`, null, fan.body.token)).status, 200);
  assert.equal((await api('GET', '/api/chats', null, fan.body.token)).body.some((c) => c.id === ch.body.id), false);

  // Премиум выдаёт только админ (alice из первого теста)
  const alice = { body: { token: aliceToken } };
  assert.equal((await api('PUT', `/api/admin/users/${fan.body.user.id}/premium`, { isPremium: true }, fan.body.token)).status, 403);
  const prem = await api('PUT', `/api/admin/users/${fan.body.user.id}/premium`, { isPremium: true }, alice.body.token);
  assert.equal(prem.body.isPremium, true);

  // Сигналы звонка доходят только тем, с кем есть общий чат
  await api('POST', '/api/chats/direct', { userId: fan.body.user.id }, owner.body.token);
  const wsUrl = (t) => `${base.replace('http', 'ws')}/ws?token=${t}`;
  const a = new WebSocket(wsUrl(owner.body.token));
  const b = new WebSocket(wsUrl(fan.body.token));
  const got = [];
  b.on('message', (d) => got.push(JSON.parse(String(d))));
  await Promise.all([new Promise((r) => a.on('open', r)), new Promise((r) => b.on('open', r))]);
  a.send(JSON.stringify({ type: 'call.signal', to: fan.body.user.id, data: { kind: 'offer', callId: 'c1', sdp: 'v=0' } }));
  await new Promise((r) => setTimeout(r, 150));
  const sig = got.find((e) => e.type === 'call.signal');
  assert.equal(sig.from, owner.body.user.id);
  assert.equal(sig.data.kind, 'offer');
  // Собеседник принял — звонок больше не висит в очереди
  b.send(JSON.stringify({ type: 'call.signal', to: owner.body.user.id, data: { kind: 'answer', callId: 'c1', sdp: 'v=0' } }));
  b.close();
  await new Promise((r) => setTimeout(r, 150));

  // Собеседник не в сети: звонящий получает «waiting», а offer и ICE приходят, когда тот подключится
  const fromA = [];
  a.on('message', (d) => fromA.push(JSON.parse(String(d))));
  a.send(JSON.stringify({ type: 'call.signal', to: fan.body.user.id, data: { kind: 'offer', callId: 'c2', sdp: 'v=0' } }));
  a.send(JSON.stringify({ type: 'call.signal', to: fan.body.user.id, data: { kind: 'ice', callId: 'c2', candidate: 'x' } }));
  await new Promise((r) => setTimeout(r, 150));
  assert.equal(fromA.find((e) => e.type === 'call.signal')?.data.kind, 'waiting');
  const late = [];
  const b2 = new WebSocket(wsUrl(fan.body.token));
  b2.on('message', (d) => late.push(JSON.parse(String(d))));
  await new Promise((r) => b2.on('open', r));
  await new Promise((r) => setTimeout(r, 150));
  const replay = late.filter((e) => e.type === 'call.signal').map((e) => e.data.kind);
  assert.deepEqual(replay, ['offer', 'ice']);
  a.send(JSON.stringify({ type: 'call.signal', to: fan.body.user.id, data: { kind: 'hangup', callId: 'c2' } }));
  await new Promise((r) => setTimeout(r, 100));
  b2.close();
  const b3 = new WebSocket(wsUrl(fan.body.token));
  const after = [];
  b3.on('message', (d) => after.push(JSON.parse(String(d))));
  await new Promise((r) => b3.on('open', r));
  await new Promise((r) => setTimeout(r, 150));
  assert.equal(after.filter((e) => e.type === 'call.signal').length, 0);
  b3.close();

  // «Кто может мне звонить»: никто — звонящий сразу получает forbidden, сигнал не доходит.
  await api('PATCH', '/api/me', { callPrivacy: 'nobody' }, fan.body.token);
  const fromA2 = [];
  a.on('message', (d) => fromA2.push(JSON.parse(String(d))));
  a.send(JSON.stringify({ type: 'call.signal', to: fan.body.user.id, data: { kind: 'offer', callId: 'c3', sdp: 'v=0' } }));
  await new Promise((r) => setTimeout(r, 150));
  assert.equal(fromA2.find((e) => e.type === 'call.signal')?.data.kind, 'forbidden');
  // Только контакты: пока владелец не в контактах — нельзя, после добавления — можно.
  await api('PATCH', '/api/me', { callPrivacy: 'contacts' }, fan.body.token);
  assert.equal((await api('GET', '/api/me', null, fan.body.token)).body.callPrivacy, 'contacts');
  await api('PUT', `/api/contacts/${owner.body.user.id}`, null, fan.body.token);
  fromA2.length = 0;
  a.send(JSON.stringify({ type: 'call.signal', to: fan.body.user.id, data: { kind: 'offer', callId: 'c4', sdp: 'v=0' } }));
  await new Promise((r) => setTimeout(r, 150));
  assert.equal(fromA2.find((e) => e.type === 'call.signal')?.data.kind, 'waiting');
  a.send(JSON.stringify({ type: 'call.signal', to: fan.body.user.id, data: { kind: 'hangup', callId: 'c4' } }));
  a.close();

  const cfg = await api('GET', '/api/calls/config', null, fan.body.token);
  assert.ok(cfg.body.iceServers.length >= 1);
  const spec = await fetch(base + '/api/openapi.json');
  assert.equal(spec.status, 200);
  assert.ok((await spec.json()).paths['/api/chats/channel']);
});

test('открытые и частные группы, приглашения, премиум-оформление', async () => {
  const o = (await reg('owner3')).body;
  const g = (await reg('guest3')).body;

  // Частная группа: не ищется и не открывается без ссылки.
  const priv = await api('POST', '/api/chats/group', { title: 'Тайная группа', memberIds: [], isPublic: false }, o.token);
  assert.equal(priv.status, 201);
  assert.equal(priv.body.isPublic, false);
  assert.ok(priv.body.inviteCode);
  assert.equal((await api('GET', '/api/chats/search?q=Тайная', null, g.token)).body.length, 0);
  assert.equal((await api('POST', `/api/chats/${priv.body.id}/subscribe`, null, g.token)).status, 404);
  const preview = await api('GET', `/api/invite/${priv.body.inviteCode}`, null, g.token);
  assert.equal(preview.body.title, 'Тайная группа');
  assert.equal(preview.body.myRole, null);
  const joined = await api('POST', `/api/invite/${priv.body.inviteCode}/join`, null, g.token);
  assert.equal(joined.body.myRole, 'member');
  assert.equal(joined.body.inviteCode, null, 'обычный участник частной группы не видит ссылку');

  // Сброс ссылки делает старую недействительной.
  const reset = await api('POST', `/api/chats/${priv.body.id}/invite/reset`, null, o.token);
  assert.notEqual(reset.body.inviteCode, priv.body.inviteCode);
  assert.equal((await api('GET', `/api/invite/${priv.body.inviteCode}`, null, g.token)).status, 404);

  // Открытая группа ищется и в неё можно вступить; аватарку можно поставить.
  const pub = await api('POST', '/api/chats/group', { title: 'Открытая группа', memberIds: [], isPublic: true }, o.token);
  const found = await api('GET', '/api/chats/search?q=Открытая', null, g.token);
  assert.equal(found.body[0].id, pub.body.id);
  assert.equal((await api('POST', `/api/chats/${pub.body.id}/subscribe`, null, g.token)).body.myRole, 'member');
  const av = await api('PATCH', `/api/chats/${pub.body.id}`, { avatarFileId: 'file-1', isPublic: false }, o.token);
  assert.equal(av.body.avatarFileId, 'file-1');
  assert.equal(av.body.isPublic, false);
  assert.equal((await api('PATCH', `/api/chats/${pub.body.id}`, { isPublic: true }, g.token)).status, 403);

  // Частный канал не читается посторонними.
  const ch = await api('POST', '/api/chats/channel', { title: 'Закрытый канал', isPublic: false }, o.token);
  assert.equal(ch.body.isPublic, false);
  assert.equal((await api('GET', `/api/chats/${ch.body.id}/messages`, null, g.token)).status, 404);

  // Эмодзи-статус и оформление профиля — только с Премиумом.
  const style = { color1: '#FF5CA8', color2: '#8E5CFF', pattern: '⭐', ring: 'gradient', junk: 'x', nameColor: 'red' };
  assert.equal((await api('PATCH', '/api/me', { emojiStatus: '🔥' }, g.token)).status, 403);
  const admin = { token: aliceToken };
  await api('PUT', `/api/admin/users/${g.user.id}/premium`, { isPremium: true }, admin.token);
  const me = await api('PATCH', '/api/me', { emojiStatus: '🔥', profileStyle: style }, g.token);
  assert.equal(me.status, 200);
  assert.equal(me.body.emojiStatus, '🔥');
  assert.deepEqual(me.body.profileStyle, { color1: '#FF5CA8', color2: '#8E5CFF', pattern: '⭐', ring: 'gradient' });
});

test('контакты, блокировка, «в сети», почта', async () => {
  const p = (await reg('petya')).body;
  const v = (await reg('vasya')).body;
  const direct = (await api('POST', '/api/chats/direct', { userId: v.user.id }, p.token)).body;
  assert.equal(direct.peerIsContact, false);

  // Контакты
  assert.equal((await api('PUT', `/api/contacts/${v.user.id}`, null, p.token)).body.isContact, true);
  assert.deepEqual((await api('GET', '/api/contacts', null, p.token)).body.map((u) => u.username), ['vasya']);
  assert.equal((await api('GET', `/api/chats/${direct.id}`, null, p.token)).body.peerIsContact, true);

  // Блокировка: vasya больше не может писать petya
  assert.equal((await api('PUT', `/api/blocks/${v.user.id}`, null, p.token)).body.isBlocked, true);
  assert.equal((await api('GET', '/api/contacts', null, p.token)).body.length, 0, 'заблокированный убран из контактов');
  const sent = await api('POST', `/api/chats/${direct.id}/messages`, { type: 'text', payload: 'x' }, v.token);
  assert.equal(sent.body.error, 'blocked');
  await api('DELETE', `/api/blocks/${v.user.id}`, null, p.token);
  assert.equal((await api('POST', `/api/chats/${direct.id}/messages`, { type: 'text', payload: 'x' }, v.token)).status, 201);

  // «В сети» — только пока приложение открыто (фоновое соединение с active=0 не считается)
  const ws = new WebSocket(`${base.replace('http', 'ws')}/ws?token=${v.token}&active=0`);
  await new Promise((r) => ws.on('open', r));
  assert.equal((await api('GET', `/api/users/${v.user.id}`, null, p.token)).body.online, false);
  ws.send(JSON.stringify({ type: 'presence', active: true }));
  await new Promise((r) => setTimeout(r, 50));
  assert.equal((await api('GET', `/api/users/${v.user.id}`, null, p.token)).body.online, true);
  ws.send(JSON.stringify({ type: 'presence', active: false }));
  await new Promise((r) => setTimeout(r, 50));
  assert.equal((await api('GET', `/api/users/${v.user.id}`, null, p.token)).body.online, false);
  ws.close();

  // Служебного пользователя не найти поиском
  assert.equal((await api('GET', '/api/users/search?q=ryzikchat', null, p.token)).body.length, 0);
});

test('аватарки открываются без токена, остальные файлы — нет', async () => {
  const u = (await reg('avatarka')).body;
  const upload = async () => {
    const form = new FormData();
    form.append('mime', 'image/png');
    form.append('file', new Blob([Buffer.from('fake-png')]), 'blob');
    const res = await fetch(base + '/api/files', { method: 'POST', headers: { authorization: `Bearer ${u.token}` }, body: form });
    return (await res.json()).id;
  };
  const avatar = await upload();
  const secret = await upload();
  await api('PATCH', '/api/me', { avatarFileId: avatar }, u.token);
  const ok = await fetch(`${base}/api/avatars/${avatar}`);
  assert.equal(ok.status, 200);
  assert.equal(ok.headers.get('content-type'), 'image/png');
  assert.equal(await ok.text(), 'fake-png');
  assert.equal((await fetch(`${base}/api/avatars/${secret}`)).status, 404, 'не аватарку так не скачать');
  assert.equal((await fetch(`${base}/api/files/${secret}`)).status, 401);
});

async function uploadImage(token, body = 'img') {
  const form = new FormData();
  form.append('mime', 'image/png');
  form.append('file', new Blob([Buffer.from(body)]), 'blob');
  const res = await fetch(base + '/api/files', { method: 'POST', headers: { authorization: `Bearer ${token}` }, body: form });
  return (await res.json()).id;
}

test('обои чата видят все участники', async () => {
  const x = (await reg('wallx')).body;
  const y = (await reg('wally')).body;
  const chat = (await api('POST', '/api/chats/direct', { userId: y.user.id }, x.token)).body;
  const pic = await uploadImage(x.token, 'wall');
  const set = await api('PUT', `/api/chats/${chat.id}/wallpaper`, { fileId: pic }, x.token);
  assert.equal(set.body.wallpaperFileId, pic);
  const seen = (await api('GET', `/api/chats/${chat.id}`, null, y.token)).body;
  assert.equal(seen.wallpaperFileId, pic, 'собеседник видит те же обои');
  assert.equal(await (await fetch(`${base}/api/avatars/${pic}`)).text(), 'wall');
  // Чужую картинку поставить нельзя.
  assert.equal((await api('PUT', `/api/chats/${chat.id}/wallpaper`, { fileId: pic }, y.token)).status, 400);
  assert.equal((await api('PUT', `/api/chats/${chat.id}/wallpaper`, { fileId: null }, y.token)).body.wallpaperFileId, null);
});

test('свои стикеры: набор, добавление, пересылка другу', async () => {
  const x = (await reg('stickx')).body;
  const y = (await reg('sticky')).body;
  const pack = (await api('POST', '/api/stickers/packs', { title: 'Котики' }, x.token)).body;
  assert.equal(pack.isMine, true);
  assert.equal(pack.isAdded, true);
  const pic = await uploadImage(x.token, 'cat');
  const withSticker = await api('POST', `/api/stickers/packs/${pack.id}/stickers`, { fileId: pic, emoji: '😺' }, x.token);
  assert.equal(withSticker.status, 201);
  assert.equal(withSticker.body.stickers.length, 1);
  assert.equal(await (await fetch(`${base}/api/avatars/${pic}`)).text(), 'cat', 'стикер открывается без токена');
  // Друг открывает набор по ссылке и добавляет себе, но менять его не может.
  const shared = (await api('GET', `/api/stickers/packs/${pack.id}`, null, y.token)).body;
  assert.equal(shared.isAdded, false);
  assert.equal((await api('PUT', `/api/stickers/packs/${pack.id}/added`, null, y.token)).body.isAdded, true);
  assert.deepEqual((await api('GET', '/api/stickers', null, y.token)).body.map((p) => p.title), ['Котики']);
  assert.equal((await api('POST', `/api/stickers/packs/${pack.id}/stickers`, { fileId: pic }, y.token)).status, 403);
  assert.equal((await api('DELETE', `/api/stickers/packs/${pack.id}/added`, null, y.token)).body.isAdded, false);
  const sid = withSticker.body.stickers[0].id;
  assert.equal((await api('DELETE', `/api/stickers/packs/${pack.id}/stickers/${sid}`, null, x.token)).body.stickers.length, 0);
});

test('обновления приложения раздаёт сам сервер', async () => {
  assert.equal((await fetch(`${base}/api/app/update`)).status, 404);
  const admin = (await api('POST', '/api/auth/login', { username: 'alice', password: 'x'.repeat(64) })).body.token;
  const form = new FormData();
  form.append('versionCode', '42');
  form.append('versionName', '9.9.9');
  form.append('notes', 'Новое');
  form.append('apk', new Blob([Buffer.from('apk-bytes')]), 'RyzikChat.apk');
  const up = await fetch(base + '/api/admin/app', { method: 'POST', headers: { authorization: `Bearer ${admin}` }, body: form });
  assert.equal(up.status, 200);
  const info = await (await fetch(`${base}/api/app/update`)).json();
  assert.equal(info.versionCode, 42);
  assert.equal(info.apk, '/api/app/apk');
  assert.equal(await (await fetch(base + info.apk)).text(), 'apk-bytes');
});

test('модерация: бан, ограничение, блокировка и удаление групп', async () => {
  const admin = (await api('POST', '/api/auth/login', { username: 'alice', password: 'x'.repeat(64) })).body.token;
  const bad = (await reg('troll')).body;
  const friend = (await reg('trollfriend')).body;
  // Не админ не может модерировать.
  assert.equal((await api('POST', `/api/admin/users/${friend.user.id}/ban`, {}, bad.token)).status, 403);

  // Ограничение: читать можно, писать нельзя.
  const dm = (await api('POST', '/api/chats/direct', { userId: friend.user.id }, bad.token)).body;
  const r1 = await api('POST', `/api/admin/users/${bad.user.id}/restrict`, { reason: 'спам', days: 1 }, admin);
  assert.ok(r1.body.restrictedUntil > Date.now());
  assert.equal((await api('GET', '/api/me', null, bad.token)).body.restrictReason, 'спам');
  const send = await api('POST', `/api/chats/${dm.id}/messages`, { type: 'text', payload: 'x' }, bad.token);
  assert.equal(send.status, 403);
  assert.equal(send.body.error, 'restricted');
  assert.equal((await api('POST', '/api/chats/group', { title: 'g' }, bad.token)).body.error, 'restricted');
  assert.equal((await api('GET', '/api/chats', null, bad.token)).status, 200);
  await api('DELETE', `/api/admin/users/${bad.user.id}/restrict`, null, admin);
  assert.equal((await api('POST', `/api/chats/${dm.id}/messages`, { type: 'text', payload: 'x' }, bad.token)).status, 201);

  // Бан: сеансы закрыты, войти нельзя, админа банить нельзя.
  assert.equal((await api('POST', `/api/admin/users/${(await api('GET', '/api/me', null, admin)).body.id}/ban`, {}, admin)).status, 400);
  const ban = await api('POST', `/api/admin/users/${bad.user.id}/ban`, { reason: 'оскорбления' }, admin);
  assert.ok(ban.body.isBanned);
  assert.equal((await api('GET', '/api/me', null, bad.token)).status, 401);
  const login = await api('POST', '/api/auth/login', { username: 'troll', password: 'x'.repeat(64) });
  assert.equal(login.body.error, 'banned');
  assert.match(login.body.message, /оскорбления/);
  assert.deepEqual((await api('GET', '/api/admin/users', null, admin)).body.map((u) => u.username), ['troll']);
  await api('DELETE', `/api/admin/users/${bad.user.id}/ban`, null, admin);
  assert.ok((await api('POST', '/api/auth/login', { username: 'troll', password: 'x'.repeat(64) })).body.token);

  // Блокировка канала: не ищется, не вступить, не написать. Потом удаление.
  const ch = (await api('POST', '/api/chats/channel', { title: 'Плохой канал', isPublic: true }, friend.token)).body;
  const b = await api('POST', `/api/admin/chats/${ch.id}/ban`, { reason: 'нарушения' }, admin);
  assert.equal(b.body.banned, true);
  assert.equal((await api('GET', '/api/channels/search?q=Плохой', null, admin)).body.length, 0);
  assert.equal((await api('POST', `/api/chats/${ch.id}/subscribe`, null, admin)).body.error, 'chat_banned');
  assert.equal((await api('POST', `/api/chats/${ch.id}/messages`, { type: 'text', payload: 'x' }, friend.token)).body.error, 'chat_banned');
  assert.equal((await api('GET', `/api/chats/${ch.id}`, null, friend.token)).body.banReason, 'нарушения');
  await api('DELETE', `/api/admin/chats/${ch.id}/ban`, null, admin);
  assert.equal((await api('POST', `/api/chats/${ch.id}/messages`, { type: 'text', payload: 'x' }, friend.token)).status, 201);
  assert.equal((await api('DELETE', `/api/admin/chats/${ch.id}`, null, admin)).status, 200);
  assert.equal((await api('GET', `/api/chats/${ch.id}`, null, friend.token)).status, 404);
  const log = (await api('GET', '/api/admin/log', null, admin)).body;
  assert.ok(log.some((l) => l.action === 'delete' && l.targetName === 'Плохой канал'));
});

test('FLUX: выдача, подарки (NFT), Премиум за FLUX, платные сообщения', async () => {
  const admin = (await api('POST', '/api/auth/login', { username: 'alice', password: 'x'.repeat(64) })).body.token;
  const x = (await reg('fluxx')).body;
  const y = (await reg('fluxy')).body;
  assert.equal((await api('GET', '/api/me', null, x.token)).body.flux, 0);
  assert.equal((await api('POST', `/api/admin/users/${x.user.id}/flux`, { amount: 1500, note: 'тест' }, x.token)).status, 403);
  assert.equal((await api('POST', `/api/admin/users/${x.user.id}/flux`, { amount: 1500, note: 'тест' }, admin)).body.flux, 1500);

  // NFT-создатель: картинка, цена, тираж 1.
  const pic = await uploadImage(admin, 'nft');
  const item = (await api('POST', '/api/admin/gift-items', { title: 'Рыжик', price: 300, supply: 1, fileId: pic }, admin)).body;
  assert.equal(item.left, 1);
  assert.equal(await (await fetch(`${base}/api/avatars/${pic}`)).text(), 'nft');
  const bought = await api('POST', '/api/gifts/buy', { itemId: item.id, toUserId: y.user.id, message: 'держи' }, x.token);
  assert.equal(bought.status, 201);
  assert.equal(bought.body.serial, 1);
  assert.equal((await api('POST', '/api/gifts/buy', { itemId: item.id }, x.token)).body.error, 'sold_out');
  const yGifts = (await api('GET', `/api/users/${y.user.id}/gifts`, null, x.token)).body;
  assert.equal(yGifts[0].from.username, 'fluxx');
  // На витрине есть встроенные подарки-эмодзи.
  const shop = (await api('GET', '/api/gifts/shop', null, x.token)).body;
  assert.ok(shop.some((g) => g.kind === 'emoji' && g.emoji === '🧸' && g.animation === 'bounce'));
  assert.equal(shop.find((g) => g.id === item.id)?.kind, 'nft');
  // Подарок виден в личном чате как сообщение от дарителя.
  const giftChat = (await api('POST', '/api/chats/direct', { userId: x.user.id }, y.token)).body;
  const giftMsgs = (await api('GET', `/api/chats/${giftChat.id}/messages`, null, y.token)).body;
  const gm = (giftMsgs.messages ?? giftMsgs).find((m) => m.type === 'gift');
  assert.ok(gm, 'сообщение о подарке в чате');
  assert.equal(gm.senderId, x.user.id);
  assert.equal(JSON.parse(gm.payload).plain.gift.title, 'Рыжик');
  // Y передаёт NFT обратно X.
  assert.equal((await api('POST', `/api/gifts/${yGifts[0].id}/transfer`, { toUserId: x.user.id }, y.token)).body.ownerId, x.user.id);
  assert.equal((await api('POST', `/api/gifts/${yGifts[0].id}/transfer`, { toUserId: x.user.id }, y.token)).status, 404);

  // Премиум за 1000 FLUX: остаток 1200 → 200.
  const prem = await api('POST', '/api/premium/buy', { months: 1 }, x.token);
  assert.equal(prem.body.isPremium, true);
  assert.equal(prem.body.flux, 200);
  assert.ok(prem.body.premiumUntil > Date.now());
  assert.equal((await api('POST', '/api/premium/buy', { months: 1 }, x.token)).body.error, 'need_flux');

  // Платные сообщения: Y берёт 150 FLUX за сообщение от незнакомцев.
  await api('PATCH', '/api/me', { messagePrice: 150 }, y.token);
  const dm = (await api('POST', '/api/chats/direct', { userId: y.user.id }, x.token)).body;
  assert.equal((await api('POST', `/api/chats/${dm.id}/messages`, { type: 'text', payload: 'a' }, x.token)).status, 201);
  assert.equal((await api('GET', '/api/me', null, y.token)).body.flux, 150);
  assert.equal((await api('POST', `/api/chats/${dm.id}/messages`, { type: 'text', payload: 'b' }, x.token)).body.error, 'need_flux');
  await api('PUT', `/api/contacts/${x.user.id}`, null, y.token);
  assert.equal((await api('POST', `/api/chats/${dm.id}/messages`, { type: 'text', payload: 'c' }, x.token)).status, 201, 'контактам бесплатно');
  const hist = (await api('GET', '/api/flux', null, x.token)).body;
  assert.deepEqual(hist.history.map((h) => h.kind), ['paid_message', 'premium', 'gift', 'admin']);
});

test('смена юзернейма: новый ключ входа, старый перестаёт работать, занятые имена', async () => {
  const u = await reg('renameme');
  await reg('takenname');
  const t = u.body.token;
  const body = (username) => ({ username, password: 'x'.repeat(64), newPassword: 'y'.repeat(64), encryptedPrivateKey: 'enc-new' });
  assert.equal((await api('POST', '/api/me/username', { ...body('newname1'), password: 'wrong-password' }, t)).body.error, 'bad_credentials');
  assert.equal((await api('POST', '/api/me/username', body('TakenName'), t)).body.error, 'username_taken');
  assert.equal((await api('POST', '/api/me/username', body('no'), t)).body.error, 'bad_username');
  const ok = await api('POST', '/api/me/username', body('newname1'), t);
  assert.equal(ok.body.username, 'newname1');
  assert.equal((await api('POST', '/api/auth/login', { username: 'renameme', password: 'x'.repeat(64) })).status, 401);
  const login = await api('POST', '/api/auth/login', { username: 'newname1', password: 'y'.repeat(64) });
  assert.equal(login.body.encryptedPrivateKey, 'enc-new');
  assert.equal((await api('GET', '/api/me', null, t)).status, 200, 'текущий сеанс остаётся');
});

test('дополнительные юзернеймы за FLUX: покупка, поиск, занятость, сделать основным', async () => {
  const admin = (await api('POST', '/api/auth/login', { username: 'alice', password: 'x'.repeat(64) })).body.token;
  const u = (await reg('collector')).body;
  const other = (await reg('otheruser')).body;
  await api('POST', `/api/admin/users/${u.user.id}/flux`, { amount: 3000, note: 'тест' }, admin);
  const check = (await api('GET', '/api/usernames/check?username=@ryzhik_fan', null, u.token)).body;
  assert.deepEqual([check.available, check.price], [true, 500]);
  assert.equal((await api('GET', '/api/usernames/check?username=otheruser', null, u.token)).body.available, false);
  assert.equal((await api('GET', '/api/usernames/check?username=abcde', null, u.token)).body.price, 2500);
  assert.equal((await api('POST', '/api/me/usernames', { username: 'abc' }, u.token)).body.error, 'need_flux');
  const bought = await api('POST', '/api/me/usernames', { username: '@ryzhik_fan' }, u.token);
  assert.deepEqual(bought.body.extraUsernames, ['ryzhik_fan']);
  assert.equal(bought.body.flux, 2500);
  assert.equal((await api('POST', '/api/me/usernames', { username: 'RYZHIK_FAN' }, other.token)).body.error, 'username_taken');
  // Занятое дополнительное имя нельзя взять ни при регистрации, ни для канала.
  assert.equal((await reg('Ryzhik_Fan')).body.error, 'username_taken');
  assert.equal((await api('GET', '/api/chat-username-check?username=ryzhik_fan', null, other.token)).body.ok, false);
  // Человека находят по дополнительному имени, оно видно в профиле.
  const found = (await api('GET', '/api/users/search?q=ryzhik_f', null, other.token)).body;
  assert.equal(found[0]?.id, u.user.id);
  assert.deepEqual((await api('GET', `/api/users/${u.user.id}`, null, other.token)).body.extraUsernames, ['ryzhik_fan']);
  // Дополнительное имя становится основным, старое основное — дополнительным.
  const swap = await api('POST', '/api/me/username', { username: 'ryzhik_fan', password: 'x'.repeat(64), newPassword: 'z'.repeat(64), encryptedPrivateKey: 'enc-z' }, u.token);
  assert.equal(swap.body.username, 'ryzhik_fan');
  assert.deepEqual(swap.body.extraUsernames, ['collector']);
  assert.equal((await api('POST', '/api/auth/login', { username: 'ryzhik_fan', password: 'z'.repeat(64) })).status, 200);
  // Отказ от имени: оно освобождается, FLUX не возвращаются.
  const freed = await api('DELETE', '/api/me/usernames/collector', null, u.token);
  assert.deepEqual([freed.body.extraUsernames, freed.body.flux], [[], 2500]);
  assert.equal((await api('DELETE', '/api/me/usernames/collector', null, u.token)).status, 404);
  assert.equal((await api('GET', '/api/usernames/check?username=collector', null, other.token)).body.available, true);
  assert.equal((await api('GET', '/api/flux', null, u.token)).body.history[0].kind, 'username');
});

test('папка данных: рядом с сервером (ISO/data), DATA_DIR или старая ./data', async () => {
  const { resolveDataDir } = await import('../src/index.js');
  const path = await import('node:path');
  const root = path.resolve(import.meta.dirname, '..');
  assert.equal(resolveDataDir({ DATA_DIR: '/tmp/x' }), '/tmp/x');
  const r = resolveDataDir({});
  assert.ok(r === path.resolve(root, '..', 'data') || r.endsWith(path.join('server', 'data')) || r === path.resolve('data'));
});

test('проверка запросов, пропавшие файлы, повтор отправки и кривые WebSocket-сообщения', async () => {
  const u = await reg('validator');
  const v = await reg('validpeer');
  const t = u.body.token;
  const bad = await api('POST', '/api/chats/group', { title: 'g', memberIds: 'x' }, t);
  assert.equal(bad.status, 400);
  assert.match(bad.body.message, /memberIds/);
  assert.equal((await api('PATCH', '/api/me', { displayName: ['x'] }, t)).status, 400);
  assert.equal((await api('GET', '/api/users/search?q[]=a&q[]=b', null, t)).status, 400);
  const raw = await fetch(base + '/api/chats/group', { method: 'POST', headers: { 'content-type': 'application/json', authorization: `Bearer ${t}` }, body: '[1,2]' });
  assert.equal(raw.status, 400);

  // Повтор с тем же clientId не создаёт второе сообщение.
  const dm = (await api('POST', '/api/chats/direct', { userId: v.body.user.id }, t)).body;
  const m1 = await api('POST', `/api/chats/${dm.id}/messages`, { type: 'text', payload: 'p', clientId: 'same' }, t);
  const m2 = await api('POST', `/api/chats/${dm.id}/messages`, { type: 'text', payload: 'p', clientId: 'same' }, t);
  assert.equal(m1.status, 201);
  assert.equal(m2.body.id, m1.body.id);
  assert.equal((await api('POST', `/api/chats/${dm.id}/messages`, { type: 'text', payload: 'p', replyTo: { x: 1 } }, t)).status, 400);

  // Файл есть в базе, но пропал с диска: 404, а не 500.
  const form = new FormData();
  form.append('file', new Blob([Buffer.from('hello')], { type: 'text/plain' }), 'a.txt');
  const up = await (await fetch(base + '/api/files', { method: 'POST', headers: { authorization: `Bearer ${t}` }, body: form })).json();
  fs.rmSync(path.join(dir, 'files', up.id));
  const missing = await fetch(`${base}/api/files/${up.id}`, { headers: { authorization: `Bearer ${t}` } });
  assert.equal(missing.status, 404);

  // Сервер переживает мусор в WebSocket и продолжает отвечать.
  const ws = new WebSocket(`${base.replace('http', 'ws')}/ws?token=${t}`);
  await new Promise((r) => ws.on('open', r));
  for (const junk of ['null', '5', '"x"', '[]', '{"type":"typing","chatId":5}', '{"type":"call.signal","to":"x","data":null}']) ws.send(junk);
  await new Promise((r) => setTimeout(r, 200));
  ws.close();
  assert.equal((await api('GET', '/api/me', null, t)).status, 200);
});

test('галочка верификации: админ выдаёт людям и каналам', async () => {
  const admin = (await api('POST', '/api/auth/login', { username: 'alice', password: 'x'.repeat(64) })).body.token;
  const u = await reg('verifyme');
  assert.equal(u.body.user.verified, false);
  assert.equal((await api('PUT', `/api/admin/users/${u.body.user.id}/verified`, { verified: true }, u.body.token)).status, 403);
  const r = await api('PUT', `/api/admin/users/${u.body.user.id}/verified`, { verified: true }, admin);
  assert.equal(r.body.verified, true);
  assert.equal((await api('GET', `/api/users/${u.body.user.id}`, null, u.body.token)).body.verified, true);
  const ch = (await api('POST', '/api/chats/channel', { title: 'Новости', username: 'verifiednews', isPublic: true }, u.body.token)).body;
  assert.equal(ch.verified, false);
  const found = (await api('GET', '/api/admin/chats?q=@verifiednews', null, admin)).body;
  assert.equal(found[0].id, ch.id);
  assert.equal((await api('PUT', `/api/admin/chats/${ch.id}/verified`, { verified: true }, admin)).body.verified, true);
  assert.equal((await api('GET', `/api/chats/${ch.id}`, null, u.body.token)).body.verified, true);
  assert.ok((await api('GET', '/api/admin/chats', null, admin)).body.some((c) => c.id === ch.id), 'верифицированные видны в списке');
  await api('PUT', `/api/admin/users/${u.body.user.id}/verified`, { verified: false }, admin);
  assert.equal((await api('GET', `/api/users/${u.body.user.id}`, null, u.body.token)).body.verified, false);
});

test('канал: просмотры, привязанная группа и комментарии', async () => {
  const owner = await reg('chowner7');
  const reader = await reg('chreader7');
  const t = owner.body.token, r = reader.body.token;
  const ch = (await api('POST', '/api/chats/channel', { title: 'Канал', username: 'commentschan', isPublic: true }, t)).body;
  const grp = (await api('POST', '/api/chats/group', { title: 'Обсуждение канала', memberIds: [] }, t)).body;
  const plain = (text) => JSON.stringify({ v: 0, plain: { text } });
  const post = (await api('POST', `/api/chats/${ch.id}/messages`, { type: 'text', payload: plain('пост') }, t)).body;
  assert.equal(post.views, 0);
  assert.equal((await api('GET', `/api/messages/${post.id}/comments`, null, r)).body.error, 'no_discussion');
  assert.equal((await api('PUT', `/api/chats/${ch.id}/discussion`, { groupId: grp.id }, r)).status, 404, 'не участник канала');
  const linked = await api('PUT', `/api/chats/${ch.id}/discussion`, { groupId: grp.id }, t);
  assert.equal(linked.body.discussionId, grp.id);
  assert.equal((await api('GET', `/api/chats/${grp.id}`, null, t)).body.linkedChannelId, ch.id);

  // Просмотры: читатель открыл канал.
  await api('POST', `/api/chats/${ch.id}/subscribe`, null, r);
  const v = await api('POST', `/api/chats/${ch.id}/views`, { ids: [post.id, 'nope'] }, r);
  assert.deepEqual(v.body.views, { [post.id]: 1 });
  await api('POST', `/api/chats/${ch.id}/views`, { ids: [post.id] }, r);
  assert.equal((await api('GET', `/api/chats/${ch.id}/messages`, null, t)).body[0].views, 1, 'повторный просмотр не считается');

  // Комментарий пишет подписчик, который не состоит в группе.
  assert.equal((await api('POST', `/api/messages/${post.id}/comments`, { type: 'text', payload: 'шифр' }, r)).status, 400);
  const c = await api('POST', `/api/messages/${post.id}/comments`, { type: 'text', payload: plain('класс'), clientId: 'k1' }, r);
  assert.equal(c.status, 201);
  assert.equal(c.body.chatId, grp.id);
  assert.equal(c.body.commentOf, post.id);
  const list = (await api('GET', `/api/messages/${post.id}/comments`, null, r)).body;
  assert.equal(list.comments.length, 1);
  assert.equal(list.users[0].username, 'chreader7');
  assert.equal((await api('GET', `/api/chats/${ch.id}/messages`, null, r)).body[0].comments, 1);
  // В самой группе комментарий тоже виден.
  assert.equal((await api('GET', `/api/chats/${grp.id}/messages`, null, t)).body.at(-1).commentOf, post.id);
});

test('пустой личный чат не виден, удаление чатов и аккаунта', async () => {
  const a = await reg('deleter1');
  const b = await reg('deleter2');
  const ta = a.body.token, tb = b.body.token;
  const dm = (await api('POST', '/api/chats/direct', { userId: b.body.user.id }, ta)).body;
  const ids = async (t) => (await api('GET', '/api/chats', null, t)).body.map((c) => c.id);
  assert.ok(!(await ids(ta)).includes(dm.id), 'без сообщений чата нет в списке');
  assert.ok(!(await ids(tb)).includes(dm.id));
  await api('POST', `/api/chats/${dm.id}/messages`, { type: 'text', payload: 'привет' }, ta);
  assert.ok((await ids(ta)).includes(dm.id));
  assert.ok((await ids(tb)).includes(dm.id), 'после первого сообщения чат есть у обоих');

  // Удаление личного чата — у обоих.
  assert.equal((await api('DELETE', `/api/chats/${dm.id}`, null, tb)).body.ok, true);
  assert.ok(!(await ids(ta)).includes(dm.id));
  // Группу удаляет только владелец.
  const g = (await api('POST', '/api/chats/group', { title: 'Удаляемая', memberIds: [b.body.user.id] }, ta)).body;
  assert.equal((await api('DELETE', `/api/chats/${g.id}`, null, tb)).status, 403);
  assert.equal((await api('DELETE', `/api/chats/${g.id}`, null, ta)).status, 200);
  assert.equal((await api('GET', `/api/chats/${g.id}`, null, ta)).status, 404);
  const saved = (await api('GET', '/api/chats', null, ta)).body.find((c) => c.type === 'saved');
  assert.equal((await api('DELETE', `/api/chats/${saved.id}`, null, ta)).body.error, 'cannot_delete');

  // Удаление аккаунта: канал переходит админу, вход больше не работает.
  const ch = (await api('POST', '/api/chats/channel', { title: 'Наследство', isPublic: true }, ta)).body;
  await api('POST', `/api/chats/${ch.id}/subscribe`, null, tb);
  await api('PUT', `/api/chats/${ch.id}/members/${b.body.user.id}/role`, { role: 'admin' }, ta);
  await api('POST', '/api/chats/group', { title: 'Своя', memberIds: [] }, ta);
  assert.equal((await api('POST', '/api/me/delete', { password: 'wrong' }, ta)).status, 403);
  assert.equal((await api('POST', '/api/me/delete', { password: 'x'.repeat(64) }, ta)).body.ok, true);
  assert.equal((await api('GET', '/api/me', null, ta)).status, 401);
  assert.equal((await api('POST', '/api/auth/login', { username: 'deleter1', password: 'x'.repeat(64) })).status, 401);
  const inherited = (await api('GET', `/api/chats/${ch.id}`, null, tb)).body;
  assert.equal(inherited.myRole, 'owner');
});

test('админ: удаление пользователя с сообщениями и бан по устройству и сети', async () => {
  const from = (ip, dev) => async (method, url, body, token) => {
    const res = await fetch(base + url, {
      method,
      headers: { 'content-type': 'application/json', 'x-forwarded-for': ip, 'x-device-id': dev, ...(token ? { authorization: `Bearer ${token}` } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    return { status: res.status, body: await res.json() };
  };
  const bad = from('10.0.0.66', 'dev-bad'), other = from('10.0.0.7', 'dev-ok');
  const regAt = (fn, username) => fn('POST', '/api/auth/register', {
    username, displayName: username, password: 'x'.repeat(64), publicKey: 'pk', encryptedPrivateKey: 'enc',
  });
  const spam = (await regAt(bad, 'spammer9')).body;
  const fine = (await regAt(other, 'fine9')).body;
  const g = (await other('POST', '/api/chats/group', { title: 'Чистка', memberIds: [spam.user.id] }, fine.token)).body;
  const m = (await bad('POST', `/api/chats/${g.id}/messages`, { type: 'text', payload: 'спам' }, spam.token)).body;
  await other('POST', `/api/chats/${g.id}/messages`, { type: 'text', payload: 'норм' }, fine.token);

  // Обычный человек банить не может.
  assert.equal((await other('POST', `/api/admin/users/${spam.user.id}/device-ban`, {}, fine.token)).status, 403);
  const ban = await api('POST', `/api/admin/users/${spam.user.id}/device-ban`, { reason: 'спам' }, aliceToken);
  assert.equal(ban.status, 200);
  assert.equal(ban.body.devices, 1);
  assert.equal(ban.body.ips, 1);
  // С того же устройства (даже через другую сеть) и из той же сети — не пускает, новый аккаунт тоже.
  const sameDevice = from('10.9.9.9', 'dev-bad'), sameNet = from('10.0.0.66', 'dev-new');
  assert.equal((await regAt(sameDevice, 'spammer10')).body.error, 'device_banned');
  assert.equal((await regAt(sameNet, 'spammer11')).body.error, 'device_banned');
  assert.equal((await other('GET', '/api/me', null, fine.token)).status, 200, 'других не задевает');
  const list = (await api('GET', '/api/admin/device-bans', null, aliceToken)).body;
  const ipBan = list.find((b) => b.kind === 'ip' && b.value === '10.0.0.66');
  assert.ok(ipBan);
  assert.equal((await api('DELETE', `/api/admin/device-bans/${ipBan.id}`, null, aliceToken)).body.ok, true);
  assert.equal((await regAt(sameNet, 'spammer11')).status, 201, 'после разбана сети пускает');

  // Удаление пользователя стирает все его сообщения.
  const del = await api('POST', `/api/admin/users/${spam.user.id}/delete`, {}, aliceToken);
  assert.equal(del.body.deletedMessages, 1);
  const msgs = (await other('GET', `/api/chats/${g.id}/messages`, null, fine.token)).body;
  assert.equal(msgs.find((x) => x.id === m.id).deleted, true);
  assert.equal(msgs.at(-1).deleted, false);
  assert.equal((await api('POST', `/api/admin/users/${spam.user.id}/delete`, {}, aliceToken)).status, 404);
});

test('боты: RyzikBot создаёт бота, Bot API, long polling, файлы', async () => {
  const owner = (await reg('botmaker1')).body;
  const t = owner.token;
  const plain = (text) => JSON.stringify({ v: 0, plain: { text } });
  const fatherUser = (await api('GET', '/api/users/search?q=ryzikbot', null, t)).body.find((u) => u.username === 'ryzikbot');
  assert.equal(fatherUser.isBot, true);
  const fc = (await api('POST', '/api/chats/direct', { userId: fatherUser.id }, t)).body;
  const say = async (text) => {
    await api('POST', `/api/chats/${fc.id}/messages`, { type: 'text', payload: plain(text) }, t);
    const msgs = (await api('GET', `/api/chats/${fc.id}/messages`, null, t)).body;
    return JSON.parse(msgs.at(-1).payload).plain.text;
  };
  assert.match(await say('/start'), /newbot/);
  assert.match(await say('/newbot'), /назовём/);
  assert.match(await say('Погода'), /юзернейм/);
  assert.match(await say('weather'), /bot/);
  await say('ryzik_weather_bot');
  const msgs = (await api('GET', `/api/chats/${fc.id}/messages`, null, t)).body;
  const tokenMsg = JSON.parse(msgs.at(-1).payload).plain.text;
  const token = tokenMsg.match(/\n\n(\S+:\S+)\n\n/)[1];
  const bot = (m, body, method = 'POST') => fetch(`${base}/bot/${token}/${m}`, {
    method, headers: { 'content-type': 'application/json' }, body: body ? JSON.stringify(body) : undefined,
  }).then(async (r) => ({ status: r.status, body: await r.json() }));

  const me = await bot('getMe');
  assert.equal(me.body.ok, true);
  assert.equal(me.body.result.username, 'ryzik_weather_bot');
  assert.equal((await fetch(`${base}/bot/bad:token/getMe`).then((r) => r.json())).ok, false);

  assert.match(await say('/setdescription'), /описание/);
  assert.match(await say('Показываю погоду'), /обновлено/);
  assert.equal((await bot('getMe')).body.result.description, 'Показываю погоду');

  // Человек пишет боту — бот получает обновление через long polling.
  const user = (await reg('botuser1')).body;
  const dc = (await api('POST', '/api/chats/direct', { userId: me.body.result.id }, user.token)).body;
  const poll = bot('getUpdates', { timeout: 5 });
  await new Promise((r) => setTimeout(r, 100));
  await api('POST', `/api/chats/${dc.id}/messages`, { type: 'text', payload: plain('/start') }, user.token);
  const ups = (await poll).body.result;
  assert.equal(ups.length, 1);
  assert.equal(ups[0].message.text, '/start');
  assert.equal(ups[0].message.from.username, 'botuser1');
  const sent = await bot('sendMessage', { chatId: ups[0].message.chatId, text: 'Солнечно ☀️' });
  assert.equal(sent.body.ok, true);
  const seen = (await api('GET', `/api/chats/${dc.id}/messages`, null, user.token)).body.at(-1);
  assert.equal(JSON.parse(seen.payload).plain.text, 'Солнечно ☀️');
  assert.equal((await bot('getUpdates', { offset: ups[0].updateId + 1 })).body.result.length, 0, 'подтверждённые обновления удаляются');
  assert.equal((await bot('editMessageText', { messageId: sent.body.result.messageId, text: 'Дождь 🌧' })).body.ok, true);
  assert.equal((await bot('sendMessage', { chatId: fc.id, text: 'чужой чат' })).body.error, 'chat_not_found');

  // Файл от бота шифруется так же, как в приложении, и бот может скачать файлы из сообщений.
  const fd = new FormData();
  fd.append('chatId', dc.id); fd.append('caption', 'карта');
  fd.append('file', new Blob([Buffer.from('hello-file')], { type: 'text/plain' }), 'a.txt');
  const doc = await fetch(`${base}/bot/${token}/sendDocument`, { method: 'POST', body: fd }).then((r) => r.json());
  assert.equal(doc.ok, true);
  const back = await fetch(`${base}/bot/${token}/file/${doc.result.messageId}`);
  assert.equal(await back.text(), 'hello-file');

  assert.equal((await bot('setMyCommands', { commands: [{ command: 'start', description: 'Начать' }] })).body.ok, true);
  assert.equal((await api('GET', `/api/users/${me.body.result.id}`, null, user.token)).body.botCommands[0].command, 'start');

  // Новый токен — старый перестаёт работать; удаление бота.
  await say('/revoke');
  assert.equal((await bot('getMe')).status, 401);
  await say('/deletebot');
  assert.match(await say('@ryzik_weather_bot'), /удалён/);
  assert.equal((await api('GET', `/api/users/${me.body.result.id}`, null, t)).status, 404);
});

test('публичная статистика для сайта', async () => {
  const res = await fetch(base + '/api/stats');
  assert.equal(res.status, 200);
  assert.equal(res.headers.get('access-control-allow-origin'), '*');
  const s = await res.json();
  assert.ok(s.users >= 2, 'считаются люди без системных аккаунтов и ботов');
  for (const k of ['online', 'newToday', 'newWeek', 'bots', 'groups', 'channels', 'messages']) assert.equal(typeof s[k], 'number', k);
  assert.ok(s.version);
  assert.equal(s.username, undefined);
});

test('закреп сообщений: в группах — владелец и админы, в личных — все', async () => {
  const o = (await reg('pinowner')).body;
  const m = (await reg('pinmember')).body;
  const g = (await api('POST', '/api/chats/group', { title: 'Закреп', memberIds: [m.user.id] }, o.token)).body;
  const msg = (await api('POST', `/api/chats/${g.id}/messages`, { type: 'text', payload: 'важно' }, m.token)).body;
  assert.equal((await api('GET', `/api/chats/${g.id}`, null, m.token)).body.canPin, false);
  assert.equal((await api('PUT', `/api/chats/${g.id}/pin`, { messageId: msg.id }, m.token)).status, 403);
  const pinned = await api('PUT', `/api/chats/${g.id}/pin`, { messageId: msg.id }, o.token);
  assert.equal(pinned.body.pinnedMessage.id, msg.id);
  assert.equal((await api('GET', `/api/chats/${g.id}`, null, m.token)).body.pinnedMessage.id, msg.id, 'закреп видят все');
  assert.equal((await api('PUT', `/api/chats/${g.id}/pin`, { messageId: 'nope' }, o.token)).status, 404);
  assert.equal((await api('PUT', `/api/chats/${g.id}/pin`, { messageId: null }, o.token)).body.pinnedMessage, null);

  const dm = (await api('POST', '/api/chats/direct', { userId: o.user.id }, m.token)).body;
  const dmMsg = (await api('POST', `/api/chats/${dm.id}/messages`, { type: 'text', payload: 'привет' }, o.token)).body;
  assert.equal((await api('PUT', `/api/chats/${dm.id}/pin`, { messageId: dmMsg.id }, m.token)).body.pinnedMessage.id, dmMsg.id);
  // Удалённое сообщение откреплено само.
  await api('DELETE', `/api/messages/${dmMsg.id}`, null, o.token);
  assert.equal((await api('GET', `/api/chats/${dm.id}`, null, o.token)).body.pinnedMessage, null);
});

test('продажа своего подарка за FLUX', async () => {
  const admin = (await api('POST', '/api/auth/login', { username: 'alice', password: 'x'.repeat(64) })).body.token;
  const s = (await reg('seller')).body;
  const b = (await reg('giver')).body;
  await api('POST', `/api/admin/users/${b.user.id}/flux`, { amount: 100 }, admin);
  const rose = (await api('GET', '/api/gifts/shop', null, b.token)).body.find((g) => g.emoji === '🌹');
  await api('POST', '/api/gifts/buy', { itemId: rose.id, toUserId: s.user.id }, b.token);
  const [gift] = (await api('GET', `/api/users/${s.user.id}/gifts`, null, s.token)).body;
  assert.equal(gift.sellPrice, Math.floor(rose.price * 0.85));
  assert.equal((await api('POST', `/api/gifts/${gift.id}/sell`, null, b.token)).status, 404, 'чужой подарок не продать');
  const sold = (await api('POST', `/api/gifts/${gift.id}/sell`, null, s.token)).body;
  assert.equal(sold.amount, gift.sellPrice);
  assert.equal((await api('GET', '/api/me', null, s.token)).body.flux, gift.sellPrice);
  assert.equal((await api('GET', `/api/users/${s.user.id}/gifts`, null, s.token)).body.length, 0);
  assert.equal((await api('POST', `/api/gifts/${gift.id}/sell`, null, s.token)).status, 404);
});

test('общий канал: в нём все, пишут только администраторы', async () => {
  const admin = (await api('POST', '/api/auth/login', { username: 'alice', password: 'x'.repeat(64) })).body.token;
  const u = (await reg('newsreader')).body;
  const chats = (await api('GET', '/api/chats', null, u.token)).body;
  const news = chats.find((c) => c.title === 'Новости RyzikChat');
  assert.ok(news, 'новый пользователь сразу подписан');
  assert.equal(news.type, 'channel');
  assert.equal(news.verified, true);
  assert.equal(news.myRole, 'subscriber');
  assert.equal((await api('POST', `/api/chats/${news.id}/messages`, { type: 'text', payload: 'я' }, u.token)).status, 403);
  assert.equal((await api('POST', `/api/chats/${news.id}/messages`, { type: 'text', payload: 'новость' }, admin)).status, 201);
  // У канала один админ: администратор приложения не становится админом канала.
  await api('PUT', `/api/admin/users/${u.user.id}/admin`, { isAdmin: true }, admin);
  assert.equal((await api('GET', `/api/chats/${news.id}`, null, u.token)).body.myRole, 'subscriber');
  await api('PUT', `/api/admin/users/${u.user.id}/admin`, { isAdmin: false }, admin);
  assert.equal((await api('GET', `/api/chats/${news.id}`, null, u.token)).body.myRole, 'subscriber');
});

test('кто прочитал моё сообщение', async () => {
  const a = (await reg('readera')).body;
  const b = (await reg('readerb')).body;
  const c = (await reg('readerc')).body;
  const g = (await api('POST', '/api/chats/group', { title: 'Чтение', memberIds: [b.user.id, c.user.id] }, a.token)).body;
  const msg = (await api('POST', `/api/chats/${g.id}/messages`, { type: 'text', payload: 'кто тут' }, a.token)).body;
  assert.equal((await api('GET', `/api/messages/${msg.id}/readers`, null, a.token)).body.count, 0);
  await api('POST', `/api/chats/${g.id}/read`, { seq: msg.seq }, b.token);
  const r = (await api('GET', `/api/messages/${msg.id}/readers`, null, a.token)).body;
  assert.equal(r.count, 1);
  assert.equal(r.users[0].username, 'readerb');
  assert.equal((await api('GET', `/api/messages/${msg.id}/readers`, null, b.token)).status, 403, 'видно только автору');
});

test('удаление личного чата: у себя или у всех', async () => {
  const a = (await reg('delforme1')).body;
  const b = (await reg('delforme2')).body;
  const dm = (await api('POST', '/api/chats/direct', { userId: b.user.id }, a.token)).body;
  await api('POST', `/api/chats/${dm.id}/messages`, { type: 'text', payload: 'старое' }, a.token);
  const ids = async (t) => (await api('GET', '/api/chats', null, t)).body.map((c) => c.id);
  // У себя: у меня чата и истории нет, у собеседника всё осталось.
  assert.equal((await api('DELETE', `/api/chats/${dm.id}?forAll=false`, null, a.token)).body.forAll, false);
  assert.ok(!(await ids(a.token)).includes(dm.id));
  assert.ok((await ids(b.token)).includes(dm.id));
  assert.equal((await api('GET', `/api/chats/${dm.id}/messages`, null, b.token)).body.length, 1);
  // Собеседник пишет снова — чат возвращается, но только с новым сообщением.
  await api('POST', `/api/chats/${dm.id}/messages`, { type: 'text', payload: 'новое' }, b.token);
  assert.ok((await ids(a.token)).includes(dm.id));
  const mine = (await api('GET', `/api/chats/${dm.id}/messages`, null, a.token)).body;
  assert.deepEqual(mine.map((m) => m.payload), ['новое']);
  // У всех: чат пропадает у обоих.
  assert.equal((await api('DELETE', `/api/chats/${dm.id}?forAll=true`, null, a.token)).body.ok, true);
  assert.ok(!(await ids(b.token)).includes(dm.id));
  assert.equal((await api('GET', `/api/chats/${dm.id}/messages`, null, b.token)).status, 404);
});

test('0.8.9: музыка и канал в профиле, галочки прочтения, админы в группе, боты, админ-списки, удаление подарка', async () => {
  const admin = (await api('POST', '/api/auth/login', { username: 'alice', password: 'x'.repeat(64) })).body.token;
  const u = (await reg('musician')).body;
  const v = (await reg('listener')).body;
  // Музыка: только свой аудиофайл.
  const fd = new FormData();
  fd.append('file', new Blob([Buffer.from('ID3fake')], { type: 'audio/mpeg' }), 'song.mp3');
  const up = await (await fetch(`${base}/api/files`, { method: 'POST', headers: { authorization: `Bearer ${u.token}` }, body: fd })).json();
  assert.ok(up.id, JSON.stringify(up));
  const me = (await api('PATCH', '/api/me', { musicFileId: up.id, musicTitle: 'Песня' }, u.token)).body;
  assert.deepEqual(me.music, { fileId: up.id, title: 'Песня' });
  assert.equal((await fetch(`${base}/api/avatars/${up.id}`)).status, 200, 'трек слушают без токена');
  // Канал в профиле: только свой.
  const ch = (await api('POST', '/api/chats/channel', { title: 'Мой канал', isPublic: true }, u.token)).body;
  assert.equal((await api('PATCH', '/api/me', { featuredChatId: ch.id }, v.token)).status, 403);
  assert.equal((await api('PATCH', '/api/me', { featuredChatId: ch.id }, u.token)).body.featuredChannel.title, 'Мой канал');
  assert.equal((await api('GET', `/api/users/${u.user.id}`, null, v.token)).body.featuredChannel.id, ch.id);
  // Две галочки — только когда прочитал собеседник.
  const dm = (await api('POST', '/api/chats/direct', { userId: v.user.id }, u.token)).body;
  const msg = (await api('POST', `/api/chats/${dm.id}/messages`, { type: 'text', payload: 'x' }, u.token)).body;
  assert.equal((await api('GET', `/api/chats/${dm.id}`, null, u.token)).body.othersReadSeq, 0);
  await api('POST', `/api/chats/${dm.id}/read`, { seq: msg.seq }, v.token);
  assert.equal((await api('GET', `/api/chats/${dm.id}`, null, u.token)).body.othersReadSeq, msg.seq);
  // Админ группы: назначить и снять, права в приложении не меняются.
  const g = (await api('POST', '/api/chats/group', { title: 'Админы', memberIds: [v.user.id] }, u.token)).body;
  await api('PUT', `/api/chats/${g.id}/members/${v.user.id}/role`, { role: 'admin' }, u.token);
  assert.equal((await api('GET', `/api/chats/${g.id}`, null, v.token)).body.myRole, 'admin');
  await api('PUT', `/api/chats/${g.id}/members/${v.user.id}/role`, { role: 'member' }, u.token);
  assert.equal((await api('GET', `/api/chats/${g.id}`, null, v.token)).body.myRole, 'member');
  // Админ-панель: списки всех аккаунтов и групп.
  const allUsers = (await api('GET', '/api/admin/users?all=1', null, admin)).body;
  assert.ok(allUsers.some((x) => x.username === 'listener'));
  const allGroups = (await api('GET', '/api/admin/chats?all=1&type=group', null, admin)).body;
  assert.ok(allGroups.some((x) => x.id === g.id) && allGroups.every((x) => x.type === 'group'));
  // Удаление подарка: пропадает из магазина и у владельцев.
  await api('POST', `/api/admin/users/${v.user.id}/flux`, { amount: 50 }, admin);
  const bear = (await api('GET', '/api/gifts/shop', null, v.token)).body.find((x) => x.emoji === '🧸');
  await api('POST', '/api/gifts/buy', { itemId: bear.id, toUserId: u.user.id }, v.token);
  assert.equal((await api('DELETE', `/api/admin/gift-items/${bear.id}`, null, admin)).body.removed >= 1, true);
  assert.ok(!(await api('GET', '/api/gifts/shop', null, v.token)).body.some((x) => x.id === bear.id));
  assert.equal((await api('GET', `/api/users/${u.user.id}/gifts`, null, u.token)).body.length, 0);
});

test('подарки: закреп в профиле и продажа другому пользователю', async () => {
  const admin = (await api('POST', '/api/auth/login', { username: 'alice', password: 'x'.repeat(64) })).body.token;
  const s = (await reg('giftseller')).body;
  const b = (await reg('giftbuyer')).body;
  await api('POST', `/api/admin/users/${s.user.id}/flux`, { amount: 1000 }, admin);
  await api('POST', `/api/admin/users/${b.user.id}/flux`, { amount: 500 }, admin);
  const shop = (await api('GET', '/api/gifts/shop', null, s.token)).body;
  const bear = shop.find((i) => i.kind === 'emoji' && i.price <= 300);
  const g1 = (await api('POST', '/api/gifts/buy', { itemId: bear.id }, s.token)).body;
  const g2 = (await api('POST', '/api/gifts/buy', { itemId: bear.id }, s.token)).body;

  // Закреплённый подарок — первым в профиле, даже если он старше.
  assert.equal((await api('PATCH', `/api/gifts/${g1.id}`, { pinned: true }, s.token)).body.pinned, true);
  const list = (await api('GET', `/api/users/${s.user.id}/gifts`, null, b.token)).body;
  assert.equal(list[0].id, g1.id);
  assert.equal((await api('PATCH', `/api/gifts/${g1.id}`, { pinned: true }, b.token)).status, 404, 'чужой подарок не закрепить');

  // Выставляем на продажу, покупатель видит его в маркете и покупает.
  assert.equal((await api('PATCH', `/api/gifts/${g1.id}`, { resalePrice: -5 }, s.token)).body.error, 'bad_price');
  assert.equal((await api('PATCH', `/api/gifts/${g1.id}`, { resalePrice: 300 }, s.token)).body.resalePrice, 300);
  const market = (await api('GET', '/api/gifts/market', null, b.token)).body;
  assert.ok(market.some((g) => g.id === g1.id && g.owner.id === s.user.id));
  assert.equal((await api('POST', `/api/gifts/${g1.id}/buy`, { price: 300 }, s.token)).body.error, 'own_gift');
  assert.equal((await api('POST', `/api/gifts/${g1.id}/buy`, { price: 100 }, b.token)).body.error, 'price_changed');
  assert.equal((await api('POST', `/api/gifts/${g2.id}/buy`, { price: 1 }, b.token)).body.error, 'gift_not_for_sale');
  const sellerBefore = (await api('GET', '/api/me', null, s.token)).body.flux;
  const bought = await api('POST', `/api/gifts/${g1.id}/buy`, { price: 300 }, b.token);
  assert.equal(bought.status, 200);
  assert.equal(bought.body.ownerId, b.user.id);
  assert.equal(bought.body.pinned, false);
  assert.equal(bought.body.resalePrice, null);
  assert.equal((await api('GET', '/api/me', null, b.token)).body.flux, 200);
  assert.equal((await api('GET', '/api/me', null, s.token)).body.flux, sellerBefore + 300);
  assert.equal((await api('POST', `/api/gifts/${g1.id}/buy`, { price: 300 }, b.token)).body.error, 'gift_not_for_sale');
  // Скрытый подарок снимается с продажи.
  await api('PATCH', `/api/gifts/${g2.id}`, { resalePrice: 50 }, s.token);
  assert.equal((await api('PATCH', `/api/gifts/${g2.id}`, { hidden: true }, s.token)).body.resalePrice, null);
});

test('массовый сброс: FLUX, админки, галочки и бейджи — только главный админ', async () => {
  const main = (await api('POST', '/api/auth/login', { username: 'alice', password: 'x'.repeat(64) })).body;
  const admin = main.token;
  const p = (await reg('resetp')).body;
  const q = (await reg('resetq')).body;
  await api('PUT', `/api/admin/users/${q.user.id}/admin`, { isAdmin: true }, admin);
  await api('POST', `/api/admin/users/${p.user.id}/flux`, { amount: 500 }, admin);
  await api('POST', `/api/admin/users/${main.user.id}/flux`, { amount: 70 }, admin);
  await api('PUT', `/api/admin/users/${p.user.id}/verified`, { verified: true }, admin);
  await api('PUT', `/api/admin/users/${main.user.id}/verified`, { verified: true }, admin);
  const badge = (await api('POST', '/api/admin/badges', { emoji: '⭐', title: 'Звезда' }, admin)).body;
  await api('PUT', `/api/admin/users/${p.user.id}/badges/${badge.id}`, null, admin);
  await api('PUT', `/api/admin/users/${main.user.id}/badges/${badge.id}`, null, admin);
  const ch = (await api('POST', '/api/chats/channel', { title: 'Чужой', username: 'resetchan', isPublic: true }, p.token)).body;
  await api('PUT', `/api/admin/chats/${ch.id}/verified`, { verified: true }, admin);

  const all = { flux: true, admins: true, verified: true, badges: true, confirm: 'СБРОС' };
  assert.equal((await api('POST', '/api/admin/reset', all, q.token)).body.error, 'not_main_admin', 'другой админ не может');
  assert.equal((await api('POST', '/api/admin/reset', all, p.token)).status, 403);
  assert.equal((await api('POST', '/api/admin/reset', { ...all, confirm: 'да' }, admin)).body.error, 'need_confirm');
  assert.equal((await api('POST', '/api/admin/reset', { confirm: 'СБРОС' }, admin)).body.error, 'nothing_to_reset');

  const r = await api('POST', '/api/admin/reset', all, admin);
  assert.equal(r.status, 200);
  assert.ok(r.body.backup.startsWith('ryzikchat-reset-'));
  assert.ok(fs.existsSync(path.join(dir, 'backups', r.body.backup)), 'перед сбросом сохраняется копия базы');

  const pu = (await api('GET', `/api/users/${p.user.id}`, null, admin)).body;
  assert.equal(pu.verified, false);
  assert.deepEqual(pu.badges, []);
  assert.equal((await api('GET', '/api/me', null, p.token)).body.flux, 0);
  assert.equal((await api('GET', `/api/users/${q.user.id}`, null, admin)).body.isAdmin, false);
  assert.equal((await api('GET', `/api/chats/${ch.id}`, null, p.token)).body.verified, false);

  // Главному админу, общему чату и каналу всё оставлено.
  const me = (await api('GET', '/api/me', null, admin)).body;
  assert.equal(me.isAdmin, true);
  assert.equal(me.verified, true);
  assert.equal(me.flux >= 70, true);
  assert.equal(me.badges.length >= 1, true);
  const chats = (await api('GET', '/api/chats', null, admin)).body;
  assert.equal(chats.find((c) => c.title === 'Новости RyzikChat').verified, true);
});

test('канал новостей: единственный админ — @ryzik3489, он же главный администратор', async () => {
  const alice = (await api('POST', '/api/auth/login', { username: 'alice', password: 'x'.repeat(64) })).body;
  const r = (await reg('ryzik3489')).body;
  const news = (await api('GET', '/api/chats', null, r.token)).body.find((c) => c.title === 'Новости RyzikChat');
  assert.equal(news.myRole, 'owner');
  const members = (await api('GET', `/api/chats/${news.id}`, null, r.token)).body.members;
  assert.deepEqual(members.filter((m) => m.role !== 'subscriber').map((m) => m.user?.id ?? m.userId ?? m.id), [r.user.id]);
  assert.equal((await api('POST', `/api/chats/${news.id}/messages`, { type: 'text', payload: 'я' }, alice.token)).status, 403, 'прежний владелец больше не пишет');
  assert.equal((await api('POST', `/api/chats/${news.id}/messages`, { type: 'text', payload: 'новость' }, r.token)).status, 201);
  // Став администратором приложения, @ryzik3489 становится главным: только он может делать массовый сброс.
  await api('PUT', `/api/admin/users/${r.user.id}/admin`, { isAdmin: true }, alice.token);
  const reset = { badges: true, confirm: 'СБРОС' };
  assert.equal((await api('POST', '/api/admin/reset', reset, alice.token)).body.error, 'not_main_admin');
  assert.equal((await api('POST', '/api/admin/reset', reset, r.token)).status, 200);
});

test('не больше трёх аккаунтов с одного IP и чистка спама по имени', async () => {
  const from = (ip) => async (method, url, body, token) => {
    const res = await fetch(base + url, {
      method,
      headers: { 'content-type': 'application/json', 'x-forwarded-for': ip, ...(token ? { authorization: `Bearer ${token}` } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    return { status: res.status, body: await res.json() };
  };
  const spamNet = from('10.20.30.40'), home = from('10.20.30.41');
  const regAt = (fn, username, displayName) => fn('POST', '/api/auth/register', {
    username, displayName, password: 'x'.repeat(64), publicKey: 'pk', encryptedPrivateKey: 'enc',
  });
  const spam = [];
  for (let i = 1; i <= 3; i++) {
    const r = await regAt(spamNet, `buba${i}`, `Буба Ложит`);
    assert.equal(r.status, 201);
    spam.push(r.body);
  }
  const fourth = await regAt(spamNet, 'buba4', 'Буба Ложит');
  assert.equal(fourth.status, 429);
  assert.equal(fourth.body.error, 'too_many_accounts');
  const fine = (await regAt(home, 'bubafan', 'Настоящий Вова')).body;
  assert.ok(fine.token, 'другая сеть регистрируется');

  const g = (await home('POST', '/api/chats/group', { title: 'Спам', memberIds: spam.map((s) => s.user.id) }, fine.token)).body;
  const sent = (await spamNet('POST', `/api/chats/${g.id}/messages`, { type: 'text', payload: 'Буба кормит' }, spam[0].token)).body;
  await home('POST', `/api/chats/${g.id}/messages`, { type: 'text', payload: 'норм' }, fine.token);

  assert.equal((await home('POST', '/api/admin/purge', { names: ['буба ложит'] }, fine.token)).status, 403);
  const preview = await api('POST', '/api/admin/purge', { names: ['буба ложит'] }, aliceToken);
  assert.deepEqual(preview.body.users.map((u) => u.username).sort(), ['buba1', 'buba2', 'buba3']);
  assert.equal(preview.body.deletedMessages, 0);
  assert.equal((await spamNet('GET', '/api/me', null, spam[0].token)).status, 200, 'проверка ничего не удаляет');

  const done = await api('POST', '/api/admin/purge', { names: ['буба ложит'], dryRun: false }, aliceToken);
  assert.equal(done.body.users.length, 3);
  assert.equal(done.body.deletedMessages, 1);
  assert.equal((await spamNet('GET', '/api/me', null, spam[1].token)).status, 401);
  const msgs = (await home('GET', `/api/chats/${g.id}/messages`, null, fine.token)).body;
  const list = msgs.messages ?? msgs;
  assert.ok(!list.some((m) => m.id === sent.id && m.payload), 'сообщение спамера стёрто');
  assert.ok(list.some((m) => m.payload === 'норм'));
  // Удалённые аккаунты больше не занимают место: из этой сети снова можно зарегистрироваться.
  assert.equal((await regAt(spamNet, 'newbie', 'Новичок')).status, 201);
});

test('сезонное оформление: админ включает для всех, неверный сезон отклоняется', async () => {
  const start = await api('GET', '/api/season');
  assert.equal(start.status, 200);
  assert.equal(start.body.season, 'winter', 'по умолчанию зима');
  const changed = await api('PUT', '/api/admin/season', { season: 'halloween' }, aliceToken);
  assert.equal(changed.status, 200);
  assert.equal(changed.body.season, 'halloween');
  assert.equal((await api('GET', '/api/season')).body.season, 'halloween');
  assert.equal((await api('PUT', '/api/admin/season', { season: 'summer' }, aliceToken)).status, 400);
  assert.equal((await api('PUT', '/api/admin/season', { season: 'none' })).status, 401);
  assert.equal((await api('PUT', '/api/admin/season', { season: 'winter' }, aliceToken)).body.season, 'winter');
});
