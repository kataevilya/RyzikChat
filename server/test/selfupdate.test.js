import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import { execFileSync } from 'node:child_process';
import { SelfUpdater, compareVersions, untar } from '../src/selfupdate.js';
import zlib from 'node:zlib';

test('самообновление: скачивает новую версию, не трогает data/, делает копию базы', async () => {
  assert.equal(compareVersions('0.7.10', '0.7.9'), 1);
  assert.equal(compareVersions('0.7.4', '0.7.4'), 0);
  assert.equal(compareVersions('0.6.9', '0.7.0'), -1);

  // Архив как в релизе: git archive папки server с префиксом ryzikchat-server/.
  const repo = path.resolve(import.meta.dirname, '..', '..');
  const tgz = execFileSync('git', ['archive', '--format=tar.gz', '--prefix=ryzikchat-server/', 'HEAD:server'], { cwd: repo, maxBuffer: 64 << 20 });
  const names = untar(zlib.gunzipSync(tgz)).map((f) => f.name);
  assert.ok(names.includes('ryzikchat-server/src/index.js'));
  const version = JSON.parse(untar(zlib.gunzipSync(tgz)).find((f) => f.name === 'ryzikchat-server/package.json').data.toString()).version;

  const srv = http.createServer((req, res) => {
    if (req.url.startsWith('/server.json')) res.end(JSON.stringify({ version }));
    else if (req.url.startsWith('/ryzikchat-server.tar.gz')) res.end(tgz);
    else { res.statusCode = 404; res.end(); }
  });
  await new Promise((r) => srv.listen(0, r));
  const base = `http://127.0.0.1:${srv.address().port}`;

  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ryzik-upd-'));
  const dataDir = path.join(root, 'data');
  fs.mkdirSync(dataDir);
  fs.writeFileSync(path.join(dataDir, 'ryzikchat.db'), 'база');
  // Старая версия с теми же зависимостями, чтобы тест не запускал npm install.
  const cur = JSON.parse(fs.readFileSync(path.join(repo, 'server', 'package.json'), 'utf8'));
  fs.writeFileSync(path.join(root, 'package.json'), JSON.stringify({ ...cur, version: '0.0.1' }));
  const backups = [];
  const u = new SelfUpdater({ base, root, dataDir, backupDb: (f) => { backups.push(f); fs.writeFileSync(f, 'копия'); }, log: { log() {} } });

  assert.equal(await u.checkAndApply(), version);
  assert.ok(fs.existsSync(path.join(root, 'src', 'index.js')));
  assert.equal(JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8')).version, version);
  assert.equal(fs.readFileSync(path.join(dataDir, 'ryzikchat.db'), 'utf8'), 'база', 'данные не тронуты');
  assert.equal(backups.length, 1);
  assert.ok(backups[0].includes(path.join('data', 'backups', 'ryzikchat-0.0.1-')));

  // Та же версия — ничего не делаем.
  const again = new SelfUpdater({ base, root, dataDir, log: { log() {} } });
  assert.equal(await again.checkAndApply(), null);
  srv.close();
  fs.rmSync(root, { recursive: true, force: true });
});
