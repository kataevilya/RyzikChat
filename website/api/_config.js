export const SERVER = process.env.RYZIKCHAT_SERVER || 'https://ryzik3489-ryzikchat.hf.space';
export const RELEASE = 'https://github.com/kataevilya/RyzikChat/releases/download/ryzikchat-latest';
export const APK_URL = `${RELEASE}/RyzikChat.apk`;

export async function getJson(url, ms = 8000) {
  const res = await fetch(url, { signal: AbortSignal.timeout(ms), headers: { accept: 'application/json' } });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}
