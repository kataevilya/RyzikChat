// Удаление спам-аккаунтов вместе с их сообщениями.
//   node src/purge.js "Буба"            — только показать, кого найдёт (ничего не удаляет)
//   node src/purge.js "Буба" --yes      — удалить найденные аккаунты и стереть их сообщения
// Ищет по имени и @юзернейму, без учёта регистра, по части строки. Админов и ботов не трогает.
// Лучше запускать при остановленном сервере, потом запустить его снова.
import { openDb } from './db.js';
import { createApp } from './app.js';
import { Hub } from './realtime.js';
import { resolveDataDir } from './index.js';

const args = process.argv.slice(2);
const yes = args.includes('--yes');
const names = args.filter((a) => !a.startsWith('--'));
if (!names.length) {
  console.log('Укажите имя: node src/purge.js "Буба" [--yes]');
  process.exit(1);
}
const dataDir = resolveDataDir();
const db = openDb(dataDir);
const app = createApp({ db, dataDir, hub: new Hub() });
const r = app.locals.purgeUsers({ names, dryRun: !yes });
console.log(`База: ${dataDir}`);
console.log(`Найдено аккаунтов: ${r.users.length}`);
for (const u of r.users) console.log(`  ${u.displayName}  @${u.username}`);
if (!r.users.length) process.exit(0);
if (yes) console.log(`Удалено аккаунтов: ${r.users.length}, стёрто сообщений: ${r.deletedMessages}`);
else console.log('Это только проверка. Чтобы удалить, добавьте --yes');
db.close();
