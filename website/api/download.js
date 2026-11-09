import { APK_URL } from './_config.js';

// Короткая ссылка /download всегда ведёт на самый новый APK.
export default function handler(_req, res) {
  res.setHeader('Cache-Control', 'no-store');
  res.redirect(302, APK_URL);
}
