import { SERVER } from './_config.js';

// Настройки веб-версии: куда подключаться по WebSocket (REST идёт через /rc на этом же сайте).
export default function handler(_req, res) {
  res.setHeader('Cache-Control', 's-maxage=300');
  res.status(200).json({ server: SERVER, ws: SERVER.replace(/^http/, 'ws').replace(/\/$/, '') + '/ws' });
}
