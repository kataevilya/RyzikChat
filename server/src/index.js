import fs from 'node:fs';
import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { openDb } from './db.js';
import { createApp } from './app.js';
import { Hub } from './realtime.js';
import { SelfUpdater, RESTART_CODE } from './selfupdate.js';

/** Откуда сервер забирает свежие сборки приложения (папка с update.json и RyzikChat.apk). */
export const DEFAULT_UPDATE_SOURCE = 'https://github.com/kataevilya/RyzikChat/releases/download/ryzikchat-latest';

/** Имена спам-аккаунтов, которые сервер удаляет при запуске. Свой список: PURGE_NAMES="имя1,имя2", выключить: PURGE_NAMES=off. */
export const DEFAULT_PURGE_NAMES = ['Буба Ложит', 'Буба Кормит'];

export function startServer({ port = 8080, dataDir = './data', adminUsernames = [], updateSource = null, autoUpdate = false, purgeNames = [] } = {}) {
  const db = openDb(dataDir);
  const hub = new Hub();
  const app = createApp({ db, dataDir, hub, adminUsernames, updateSource });
  // При каждом запуске удаляем спам-аккаунты по имени вместе с сообщениями.
  if (purgeNames.length) {
    const r = app.locals.purgeUsers({ names: purgeNames, dryRun: false });
    if (r.users.length) console.log(`Удалены спам-аккаунты (${r.users.length}, сообщений: ${r.deletedMessages}): ${r.users.map((u) => `${u.displayName} @${u.username}`).join(', ')}`);
  }
  // Самообновление сервера с GitHub (данные в dataDir не трогаются, перед обновлением — копия базы).
  const updater = new SelfUpdater({
    dataDir,
    backupDb: (file) => db.exec(`VACUUM INTO '${file.replace(/'/g, "''")}'`),
  });
  app.locals.selfUpdater = updater;
  app.locals.applyServerUpdate = async () => {
    const v = await updater.checkAndApply();
    if (v) {
      if (process.env.RYZIK_SUPERVISED === '1') {
        console.log('Перезапускаюсь с новой версией…');
        setTimeout(() => process.exit(RESTART_CODE), 1500);
      } else {
        console.log('Сервер обновлён. Перезапустите его (npm start), чтобы включилась новая версия.');
      }
    }
    return v;
  };
  if (autoUpdate) {
    const tick = () => app.locals.applyServerUpdate().catch((e) => console.warn(`Обновление сервера: ${e.message}`));
    setTimeout(tick, 60_000).unref();
    setInterval(tick, 30 * 60_000).unref();
  }
  // Проверяем новую сборку при запуске и раз в 3 часа.
  if (updateSource) {
    const sync = () => app.locals.syncUpdate().then((u) => u && console.log(`Скачана сборка приложения ${u.versionName}`))
      .catch((e) => console.warn(`Обновления: ${e.message}`));
    sync();
    setInterval(sync, 3 * 3600_000).unref();
  }
  const server = http.createServer(app);
  const wss = hub.attach(server, app.locals);
  return new Promise((resolve) => {
    server.listen(port, () => resolve({ server, wss, db, hub, port: server.address().port }));
  });
}

/**
 * Где лежат данные. DATA_DIR — если задан. Иначе папка data рядом с папкой сервера
 * (например, ISO/data для сервера в ISO/server): так самообновление кода её точно не заденет.
 * Если там базы ещё нет, а в ./data (старое место) есть — берём старое, чтобы ничего не потерять.
 */
export function resolveDataDir(env = process.env) {
  if (env.DATA_DIR) return path.resolve(env.DATA_DIR);
  const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  const sibling = path.resolve(root, '..', 'data');
  const legacy = [path.resolve('data'), path.join(root, 'data')];
  const hasDb = (dir) => fs.existsSync(path.join(dir, 'ryzikchat.db'));
  if (hasDb(sibling)) return sibling;
  return legacy.find(hasDb) ?? sibling;
}

// Запуск напрямую (`npm start`). Сравниваем пути, а не строки URL: на Windows и в папках
// с русскими буквами URL выглядит иначе, и раньше сервер молча завершался.
const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);

if (isMain) {
  // Одна непойманная ошибка не должна ронять сервер для всех: пишем её в лог и работаем дальше.
  process.on('uncaughtException', (e) => console.error('Непойманная ошибка:', e));
  process.on('unhandledRejection', (e) => console.error('Необработанный промис:', e));
  const port = Number(process.env.PORT ?? 8080);
  const dataDir = resolveDataDir();
  const adminUsernames = (process.env.ADMIN_USERNAMES ?? '').split(',').map((s) => s.trim().toLowerCase()).filter(Boolean);
  // Приложение само проверяет обновления на GitHub. Раздавать сборки с этого сервера можно,
  // указав UPDATE_SOURCE (например, UPDATE_SOURCE=github) — тогда сервер будет их забирать.
  const env = process.env.UPDATE_SOURCE ?? 'off';
  const src = env === 'github' ? DEFAULT_UPDATE_SOURCE : env;
  const updateSource = src && src !== 'off' ? src.replace(/\/$/, '') : null;
  // Сервер сам обновляется с GitHub раз в 30 минут. Выключить: AUTO_UPDATE=off.
  const autoUpdate = process.env.AUTO_UPDATE !== 'off';
  const purgeEnv = process.env.PURGE_NAMES;
  const purgeNames = purgeEnv === 'off' ? [] : purgeEnv ? purgeEnv.split(',').map((n) => n.trim()).filter(Boolean) : DEFAULT_PURGE_NAMES;
  startServer({ port, dataDir, adminUsernames, updateSource, autoUpdate, purgeNames }).then(({ port: p }) => {
    console.log(`RyzikChat server: http://0.0.0.0:${p} (данные: ${dataDir})`);
  });
}
