import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Запуск сервера под присмотром (`npm start`). Когда сервер обновил сам себя,
 * он выходит с кодом 75 — запускаем его снова уже с новым кодом.
 * Если сервер упал, перезапускаем через несколько секунд.
 */
const RESTART_CODE = 75;
const entry = path.join(path.dirname(fileURLToPath(import.meta.url)), 'index.js');
let child = null;
let stopping = false;

function start() {
  child = spawn(process.execPath, [entry], { stdio: 'inherit', env: { ...process.env, RYZIK_SUPERVISED: '1' } });
  child.on('exit', (code, signal) => {
    child = null;
    if (stopping) return process.exit(0);
    if (code === RESTART_CODE) {
      console.log('Перезапуск сервера после обновления…');
      start();
    } else if (code === 0 && !signal) {
      process.exit(0);
    } else {
      console.log(`Сервер остановился (${signal ?? code}), перезапуск через 3 с…`);
      setTimeout(start, 3000);
    }
  });
}

for (const sig of ['SIGINT', 'SIGTERM']) {
  process.on(sig, () => {
    stopping = true;
    if (child) child.kill(sig);
    else process.exit(0);
  });
}

start();
