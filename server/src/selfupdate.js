import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

/**
 * Самообновление сервера с GitHub. CI кладёт в релиз ryzikchat-latest файлы
 * server.json ({ version }) и ryzikchat-server.tar.gz (папка server из репозитория).
 * Сервер сравнивает версию со своей, делает копию базы, заменяет код (кроме data/ и
 * node_modules/), при смене зависимостей запускает npm install и перезапускается
 * через src/run.js. Данные в data/ не трогаются.
 */
export const SERVER_UPDATE_BASE = 'https://github.com/kataevilya/RyzikChat/releases/download/ryzikchat-latest';
export const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
/** Код выхода, по которому run.js сразу запускает сервер заново. */
export const RESTART_CODE = 75;

export function serverVersion(root = ROOT) {
  try {
    return JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8')).version ?? '0.0.0';
  } catch {
    return '0.0.0';
  }
}

/** -1, 0, 1 для версий вида 0.7.4. */
export function compareVersions(a, b) {
  const pa = String(a).split('.').map((n) => parseInt(n, 10) || 0);
  const pb = String(b).split('.').map((n) => parseInt(n, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (d) return d > 0 ? 1 : -1;
  }
  return 0;
}

/** Разбирает tar (в том числе с pax-заголовками от git archive): [{ name, data }]. */
export function untar(buf) {
  const files = [];
  let off = 0;
  let paxPath = null;
  const str = (b) => b.toString('utf8').replace(/\0.*$/s, '');
  while (off + 512 <= buf.length) {
    const h = buf.subarray(off, off + 512);
    if (h.every((x) => x === 0)) break;
    const size = parseInt(str(h.subarray(124, 136)).trim() || '0', 8);
    const type = String.fromCharCode(h[156] || 48);
    const prefix = str(h.subarray(345, 500));
    let name = str(h.subarray(0, 100));
    if (prefix) name = prefix + '/' + name;
    const data = buf.subarray(off + 512, off + 512 + size);
    off += 512 + Math.ceil(size / 512) * 512;
    if (type === 'x') {
      const m = data.toString('utf8').match(/\d+ path=([^\n]*)\n/);
      paxPath = m ? m[1] : null;
      continue;
    }
    if (type === 'g') continue;
    if (paxPath) { name = paxPath; paxPath = null; }
    if (type === '0' || type === '\0' || type === '7') files.push({ name, data: Buffer.from(data) });
  }
  return files;
}

export class SelfUpdater {
  constructor({ base = SERVER_UPDATE_BASE, root = ROOT, dataDir, backupDb, log = console } = {}) {
    this.base = base.replace(/\/$/, '');
    this.root = root;
    this.dataDir = dataDir;
    this.backupDb = backupDb;
    this.log = log;
    this.status = { version: serverVersion(root), latest: null, checkedAt: null, error: null, updating: false, updatedTo: null };
  }

  async latest() {
    const r = await fetch(`${this.base}/server.json?t=${Date.now()}`, { redirect: 'follow' });
    if (!r.ok) throw new Error(`server.json: HTTP ${r.status}`);
    const info = await r.json();
    this.status.latest = info.version ?? null;
    this.status.checkedAt = Date.now();
    return info;
  }

  /** Проверяет и, если есть новая версия, ставит её. Возвращает новую версию или null. */
  async checkAndApply() {
    if (this.status.updating) return null;
    try {
      const info = await this.latest();
      this.status.error = null;
      if (!info.version || compareVersions(info.version, this.status.version) <= 0) return null;
      this.status.updating = true;
      await this.apply(info.version);
      this.status.updatedTo = info.version;
      return info.version;
    } catch (e) {
      this.status.error = e.message;
      throw e;
    } finally {
      this.status.updating = false;
    }
  }

  async apply(version) {
    const r = await fetch(`${this.base}/ryzikchat-server.tar.gz?t=${Date.now()}`, { redirect: 'follow' });
    if (!r.ok) throw new Error(`архив сервера: HTTP ${r.status}`);
    const files = untar(zlib.gunzipSync(Buffer.from(await r.arrayBuffer())));
    // Внутри архива всё лежит в папке ryzikchat-server/.
    const entries = files
      .map((f) => ({ rel: f.name.replace(/^[^/]+\//, ''), data: f.data }))
      .filter((f) => f.rel && !f.rel.startsWith('data/') && !f.rel.startsWith('node_modules/') && !f.rel.includes('..'));
    const pkg = entries.find((f) => f.rel === 'package.json');
    if (!pkg || !entries.some((f) => f.rel === 'src/index.js')) throw new Error('в архиве нет сервера');

    // Копия базы перед обновлением: data/backups/ryzikchat-<старая версия>-<время>.db, храним 5 последних.
    if (this.backupDb && this.dataDir) {
      const dir = path.join(this.dataDir, 'backups');
      fs.mkdirSync(dir, { recursive: true });
      const stamp = new Date().toISOString().replace(/[:.]/g, '-');
      this.backupDb(path.join(dir, `ryzikchat-${this.status.version}-${stamp}.db`));
      const old = fs.readdirSync(dir).filter((n) => n.endsWith('.db')).sort();
      for (const n of old.slice(0, Math.max(0, old.length - 5))) fs.rmSync(path.join(dir, n), { force: true });
    }

    const deps = (buf) => { try { return JSON.stringify(JSON.parse(buf.toString('utf8')).dependencies ?? {}); } catch { return ''; } };
    let oldDeps = '';
    try { oldDeps = deps(fs.readFileSync(path.join(this.root, 'package.json'))); } catch { /* нет файла */ }

    for (const f of entries) {
      const dest = path.join(this.root, f.rel);
      fs.mkdirSync(path.dirname(dest), { recursive: true });
      fs.writeFileSync(dest, f.data);
    }
    if (deps(pkg.data) !== oldDeps) {
      this.log.log('Обновление сервера: ставлю зависимости (npm install)…');
      const npm = process.platform === 'win32' ? 'npm.cmd' : 'npm';
      execFileSync(npm, ['install', '--omit=dev', '--no-bin-links', '--no-audit', '--no-fund'], { cwd: this.root, stdio: 'inherit' });
    }
    this.log.log(`Сервер обновлён до ${version}. Данные сохранены, копия базы в data/backups.`);
  }
}
