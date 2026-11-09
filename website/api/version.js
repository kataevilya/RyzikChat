import { RELEASE, APK_URL, getJson } from './_config.js';

// Последняя версия приложения из update.json релиза ryzikchat-latest.
export default async function handler(_req, res) {
  try {
    const u = await getJson(`${RELEASE}/update.json`);
    res.setHeader('Cache-Control', 's-maxage=300, stale-while-revalidate=3600');
    res.status(200).json({ ok: true, version: u.versionName, versionCode: u.versionCode, notes: u.notes ?? '', apk: APK_URL });
  } catch (e) {
    res.setHeader('Cache-Control', 's-maxage=60');
    res.status(200).json({ ok: false, apk: APK_URL, error: String(e.message || e) });
  }
}
