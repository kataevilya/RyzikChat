import fs from 'node:fs'; import os from 'node:os'; import path from 'node:path';
import { startServer } from '../src/index.js';
const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ryzikfz-'));
const srv = await startServer({ port: 0, dataDir: dir, autoUpdate: false });
const base = `http://127.0.0.1:${srv.port}`;
const errs = [];
const origErr = console.error; console.error = (...a) => errs.push(a.map(x => x?.stack ?? String(x)).join(' '));
async function api(method, url, body, token, raw) {
  const r = await fetch(base + url, { method, headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) }, body: raw ?? (body === undefined ? undefined : JSON.stringify(body)) });
  let j; try { j = await r.json(); } catch { j = null; }
  return { status: r.status, body: j };
}
const reg = (u) => api('POST', '/api/auth/register', { username: u, displayName: u, password: 'x'.repeat(64), publicKey: 'pk', encryptedPrivateKey: 'e' });
const A = (await reg('alice')).body, B = (await reg('bobby')).body;
const ta = A.token, tb = B.token;
const direct = (await api('POST', '/api/chats/direct', { userId: B.user.id }, ta)).body;
const grp = (await api('POST', '/api/chats/group', { title: 'g', memberIds: [B.user.id] }, ta)).body;
const msg = (await api('POST', `/api/chats/${direct.id}/messages`, { type: 'text', payload: 'hi', clientId: 'c1' }, ta)).body;
const src = fs.readFileSync(new URL('../src/app.js', import.meta.url), 'utf8');
const routes = [...src.matchAll(/app\.(get|post|patch|put|delete)\('([^']+)'/g)].map(m => [m[1].toUpperCase(), m[2]]);
const ids = { id: [direct.id, grp.id, msg?.id, B.user.id, 'nope', '%00', '1'], userId: [B.user.id, 'nope'], badgeId: ['nope'], code: ['nope'], name: ['nope', 'x'], stickerId: ['nope'], packId: ['nope'] };
const bodies = [undefined, {}, null, [], 'str', 5, { a: 1 },
  { title: 5, username: 5, displayName: [], bio: {}, userId: {}, memberIds: 'x', type: 5, payload: 5, text: 5, emoji: 5, role: 5, q: 5, amount: 'x', days: 'x', password: 5, price: 'x', name: 5, description: 5, isPublic: 'x', reason: 5, until: 'x', minutes: 'x', duration: 'x' },
  { title: null, username: null, displayName: null, userId: null, memberIds: null, type: null, payload: null, emoji: null, amount: null, password: null, until: null, price: null, giftId: null, toUserId: null, itemId: null },
  { type: 'text', payload: { x: 1 } }, { userId: 'nope', memberIds: ['nope', 5, null] },
  { amount: 1e309, price: -1, days: -5, until: -1, minutes: 1e20 }, { username: 'a'.repeat(5000), title: 'b'.repeat(100000) },
];
const queries = ['', '?q=', '?q[]=1&q[]=2', '?limit=abc&before=x', '?limit=-1', '?username[]=a', '?q=%', '?chatId[]=1'];
let n = 0; const fails = new Map();
const login = async (u) => (await api('POST', '/api/auth/login', { username: u, password: 'x'.repeat(64), device: 'fz' })).body.token;
let ta2, tb2;
const refresh = async () => { if (ta2 && tb2 && (await api('GET', '/api/me', undefined, ta2)).status === 200 && (await api('GET', '/api/me', undefined, tb2)).status === 200) return; ta2 = await login('alice'); await api('DELETE', `/api/admin/users/${B.user.id}/ban`, undefined, ta2); await api('DELETE', `/api/admin/users/${B.user.id}/restrict`, undefined, ta2); tb2 = await login('bobby'); };
for (const [m, p] of routes) {
  const params = [...p.matchAll(/:(\w+)/g)].map(x => x[1]);
  let paths = [p];
  for (const k of params) paths = paths.flatMap(pp => (ids[k] ?? ['nope']).filter(Boolean).map(v => pp.replace(':' + k, encodeURIComponent(v))));
  for (const pp of paths.slice(0, 8)) for (const who of ['a', 'b', null]) {
    const variants = m === 'GET' || m === 'DELETE' ? queries.map(q => [pp + q, undefined]) : bodies.map(b => [pp, b]);
    for (const [u, b] of variants) {
      await refresh(); const tok = who === 'a' ? ta2 : who === 'b' ? tb2 : undefined;
      if (p.includes('server/update')) continue;
      n++; const before = errs.length;
      let r; try { r = await api(m, u, b, tok); } catch (e) { r = { status: 'EXC ' + e.message }; }
      globalThis.st = globalThis.st ?? {}; st[r.status] = (st[r.status] ?? 0) + 1;
      if (r.status === 500 || String(r.status).startsWith('EXC')) {
        const key = `${m} ${p}`; if (!fails.has(key)) fails.set(key, { u, b: JSON.stringify(b)?.slice(0, 120), err: errs.slice(before).join('\n').split('\n').slice(0, 4).join(' | ') });
      }
    }
  }
}
console.error = origErr;
console.log(JSON.stringify(globalThis.st)); console.log('requests', n, 'routes', routes.length, 'with 500:', fails.size);
for (const [k, v] of fails) console.log('\n' + k, '\n  ', v.u, v.b, '\n  ', v.err);
srv.wss.close(); srv.server.closeAllConnections(); srv.server.close(); fs.rmSync(dir, { recursive: true, force: true }); process.exit(0);
