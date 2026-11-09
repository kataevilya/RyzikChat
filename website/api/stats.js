import { SERVER, getJson } from './_config.js';

// Статистика сервера RyzikChat (публичный эндпоинт /api/stats).
export default async function handler(_req, res) {
  try {
    const s = await getJson(`${SERVER}/api/stats`);
    res.setHeader('Cache-Control', 's-maxage=30, stale-while-revalidate=300');
    res.status(200).json({ ok: true, ...s });
  } catch (e) {
    res.setHeader('Cache-Control', 's-maxage=10');
    res.status(200).json({ ok: false, error: String(e.message || e) });
  }
}
