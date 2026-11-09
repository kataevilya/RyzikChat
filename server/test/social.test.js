import { test, before, after } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { startServer } from '../src/index.js';

let srv, base, dir;

before(async () => {
  dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ryzik-social-'));
  srv = await startServer({ port: 0, dataDir: dir });
  base = `http://127.0.0.1:${srv.port}`;
});
after(() => { srv.wss.close(); srv.server.closeAllConnections(); srv.server.close(); fs.rmSync(dir, { recursive: true, force: true }); });

let ip = 0;
async function api(method, url, body, token) {
  const res = await fetch(base + url, {
    method,
    headers: { 'content-type': 'application/json', 'x-forwarded-for': `10.1.0.${++ip}`, ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: res.status, body: await res.json() };
}
const reg = async (username) => (await api('POST', '/api/auth/register', {
  username, displayName: username, password: 'x'.repeat(64), publicKey: 'pk-' + username, encryptedPrivateKey: 'enc-' + username,
})).body;
const say = (chatId, token) => api('POST', `/api/chats/${chatId}/messages`, { type: 'text', payload: '{"v":0,"plain":"hi"}' }, token);
const dayKey = (t) => new Date(t + 3 * 3600_000).toISOString().slice(0, 10);
const daysAgo = (n) => dayKey(Date.now() - n * 86400_000);

test('огоньки: в личке нужны оба, дни подряд продлевают, пропуск гасит', async () => {
  const a = await reg('fire_a');
  const b = await reg('fire_b');
  const chat = (await api('POST', '/api/chats/direct', { userId: b.user.id }, a.token)).body;
  await say(chat.id, a.token);
  await say(chat.id, a.token);
  let view = (await api('GET', `/api/chats/${chat.id}`, null, b.token)).body;
  assert.equal(view.streak.days, 0, 'пока написал только один');
  assert.deepEqual(view.streak.todayWriters, [a.user.id]);
  await say(chat.id, b.token);
  view = (await api('GET', `/api/chats/${chat.id}`, null, a.token)).body;
  assert.equal(view.streak.days, 1);
  assert.equal(view.streak.today, true);

  // Вчера огонёк был 4 дня: сегодняшний день его продлевает до 5.
  srv.db.prepare('UPDATE chat_streaks SET days = 4, last_day = ? WHERE chat_id = ?').run(daysAgo(1), chat.id);
  view = (await api('GET', `/api/chats/${chat.id}`, null, a.token)).body;
  assert.equal(view.streak.days, 4);
  assert.equal(view.streak.today, false, 'сегодня ещё не продлили');
  assert.ok(view.streak.endsAt > Date.now());
  srv.db.prepare('DELETE FROM chat_activity WHERE chat_id = ?').run(chat.id);
  await say(chat.id, b.token);
  await say(chat.id, a.token);
  view = (await api('GET', `/api/chats/${chat.id}`, null, b.token)).body;
  assert.equal(view.streak.days, 5);
  assert.equal(view.streak.best, 5);

  // Пропустили день — огонёк погас и начинается заново.
  srv.db.prepare('UPDATE chat_streaks SET last_day = ? WHERE chat_id = ?').run(daysAgo(2), chat.id);
  srv.db.prepare('DELETE FROM chat_activity WHERE chat_id = ?').run(chat.id);
  assert.equal((await api('GET', `/api/chats/${chat.id}`, null, b.token)).body.streak, null);
  await say(chat.id, b.token);
  await say(chat.id, a.token);
  view = (await api('GET', `/api/chats/${chat.id}`, null, b.token)).body;
  assert.equal(view.streak.days, 1);
  assert.equal(view.streak.best, 5, 'рекорд сохраняется');
});

test('огоньки: в группе продлевают любые двое', async () => {
  const a = await reg('grp_a');
  const b = await reg('grp_b');
  const c = await reg('grp_c');
  const g = (await api('POST', '/api/chats/group', { title: 'G', memberIds: [b.user.id, c.user.id] }, a.token)).body;
  await say(g.id, a.token);
  assert.equal((await api('GET', `/api/chats/${g.id}`, null, a.token)).body.streak.days, 0);
  await say(g.id, c.token);
  const view = (await api('GET', `/api/chats/${g.id}`, null, b.token)).body;
  assert.equal(view.streak.days, 1);
  assert.equal(view.streak.todayWriters.length, 2);
});

test('истории: статус видят собеседники, просмотры и реакции, чужие не видят', async () => {
  const a = await reg('story_a');
  const b = await reg('story_b');
  const stranger = await reg('story_x');
  const chat = (await api('POST', '/api/chats/direct', { userId: b.user.id }, a.token)).body;
  await say(chat.id, a.token);

  assert.equal((await api('POST', '/api/stories', { kind: 'text', text: '' }, a.token)).status, 400);
  assert.equal((await api('POST', '/api/stories', { kind: 'image' }, a.token)).body.error, 'bad_file');
  const st = await api('POST', '/api/stories', { kind: 'text', text: 'На учёбе 📚', bg: '#ff8800', emoji: '📚' }, a.token);
  assert.equal(st.status, 201);
  assert.equal(st.body.views, 0);

  const feedB = (await api('GET', '/api/stories', null, b.token)).body;
  assert.equal(feedB.length, 1);
  assert.equal(feedB[0].user.id, a.user.id);
  assert.equal(feedB[0].hasUnseen, true);
  assert.equal(feedB[0].stories[0].text, 'На учёбе 📚');
  assert.equal((await api('GET', '/api/stories', null, stranger.token)).body.length, 0, 'незнакомец не видит');
  assert.equal((await api('POST', `/api/stories/${st.body.id}/view`, null, stranger.token)).status, 404);

  await api('POST', `/api/stories/${st.body.id}/view`, null, b.token);
  await api('PUT', `/api/stories/${st.body.id}/reaction`, { emoji: '🔥' }, b.token);
  assert.equal((await api('GET', '/api/stories', null, b.token)).body[0].hasUnseen, false);
  const viewers = (await api('GET', `/api/stories/${st.body.id}/viewers`, null, a.token)).body;
  assert.equal(viewers.length, 1);
  assert.equal(viewers[0].reaction, '🔥');
  assert.equal((await api('GET', `/api/stories/${st.body.id}/viewers`, null, b.token)).status, 404, 'зрителей видит только автор');
  assert.equal((await api('GET', '/api/stories', null, a.token)).body[0].stories[0].views, 1);

  // «Только контакты»: собеседник без контакта не видит, а после добавления в контакты — видит.
  const priv = (await api('POST', '/api/stories', { kind: 'text', text: 'секрет', privacy: 'contacts' }, a.token)).body;
  assert.equal((await api('GET', `/api/users/${a.user.id}/stories`, null, b.token)).body.length, 1);
  await api('PUT', `/api/contacts/${b.user.id}`, null, a.token);
  assert.equal((await api('GET', `/api/users/${a.user.id}/stories`, null, b.token)).body.length, 2);

  // Удаление и истечение.
  assert.equal((await api('DELETE', `/api/stories/${priv.id}`, null, b.token)).status, 404);
  assert.equal((await api('DELETE', `/api/stories/${priv.id}`, null, a.token)).status, 200);
  srv.db.prepare('UPDATE stories SET expires_at = ? WHERE id = ?').run(Date.now() - 1, st.body.id);
  assert.equal((await api('GET', '/api/stories', null, b.token)).body.length, 0);
});
