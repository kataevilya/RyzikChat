// Сквозное шифрование RyzikChat — точная копия android/.../crypto/E2E.kt.
// X25519 (tweetnacl) + HKDF-SHA256 + AES-256-GCM для сообщений,
// AES-256-CTR + HMAC-SHA256 для файлов, PBKDF2 для ключей из пароля.

const nacl = globalThis.nacl;
const subtle = crypto.subtle;
const enc = new TextEncoder();
const dec = new TextDecoder();

const AAD_MESSAGE = enc.encode('ryzik/msg/v1');
const AAD_WRAP = enc.encode('ryzik/wrap/v1');
const AAD_VAULT = enc.encode('ryzik/vault/v1');
const INFO_WRAP = enc.encode('ryzik-wrap-v1');

export function b64(bytes) {
  let s = '';
  const b = bytes instanceof Uint8Array ? bytes : new Uint8Array(bytes);
  for (let i = 0; i < b.length; i += 0x8000) s += String.fromCharCode.apply(null, b.subarray(i, i + 0x8000));
  return btoa(s);
}

export function unb64(s) {
  const bin = atob(s);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

export function randomBytes(n) {
  return crypto.getRandomValues(new Uint8Array(n));
}

function concat(...parts) {
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0));
  let o = 0;
  for (const p of parts) { out.set(p, o); o += p.length; }
  return out;
}

const hex = (b) => Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');

// ---------- Ключи пользователя ----------

export function generateKeyPair() {
  const privateKey = randomBytes(32);
  // Как в Tink: приватный ключ хранится уже «зажатым».
  privateKey[0] &= 248; privateKey[31] &= 127; privateKey[31] |= 64;
  return { privateKey, publicKey: nacl.scalarMult.base(privateKey) };
}

export function publicFromPrivate(priv) {
  return nacl.scalarMult.base(priv);
}

/** Ключ входа (уходит на сервер вместо пароля) и ключ для приватного ключа (не покидает устройство). */
export async function derivePasswordKeys(username, password) {
  const salt = enc.encode(`ryzikchat:v1:${username.toLowerCase()}`);
  const base = await subtle.importKey('raw', enc.encode(password), 'PBKDF2', false, ['deriveBits']);
  const master = new Uint8Array(await subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt, iterations: 150000 }, base, 512));
  return { authKey: hex(master.subarray(0, 32)), vaultKey: master.slice(32, 64) };
}

export async function sealPrivateKey(priv, vaultKey) {
  return b64(await aesGcmEncrypt(vaultKey, priv, AAD_VAULT));
}

export async function openPrivateKey(sealed, vaultKey) {
  return aesGcmDecrypt(vaultKey, unb64(sealed), AAD_VAULT);
}

/** Короткий «отпечаток» ключа, чтобы собеседники могли сверить его голосом. */
export async function fingerprint(publicKey) {
  const d = new Uint8Array(await subtle.digest('SHA-256', publicKey));
  const parts = [];
  for (let i = 0; i < 12; i++) parts.push(String((d[i * 2] << 8) | d[i * 2 + 1]).padStart(5, '0'));
  return parts.join(' ');
}

// ---------- Сообщения ----------

/** recipients: { userId: Uint8Array(32) публичный ключ }. */
export async function encrypt(plaintext, recipients) {
  const messageKey = randomBytes(32);
  const ct = await aesGcmEncrypt(messageKey, enc.encode(plaintext), AAD_MESSAGE);
  const eph = generateKeyPair();
  const k = {};
  for (const [id, pub] of Object.entries(recipients)) {
    k[id] = b64(await aesGcmEncrypt(await wrapKey(eph.privateKey, pub, eph.publicKey, pub), messageKey, AAD_WRAP));
  }
  return JSON.stringify({ v: 1, epk: b64(eph.publicKey), ct: b64(ct), k });
}

export async function decrypt(payload, myUserId, myPrivateKey) {
  const env = typeof payload === 'string' ? JSON.parse(payload) : payload;
  const wrapped = env.k?.[myUserId];
  if (!wrapped) throw new Error('Сообщение зашифровано не для этого аккаунта');
  const epk = unb64(env.epk);
  const wk = await wrapKey(myPrivateKey, epk, epk, publicFromPrivate(myPrivateKey));
  const messageKey = await aesGcmDecrypt(wk, unb64(wrapped), AAD_WRAP);
  return dec.decode(await aesGcmDecrypt(messageKey, unb64(env.ct), AAD_MESSAGE));
}

async function wrapKey(priv, peerPub, epk, recipientPub) {
  const shared = nacl.scalarMult(priv, peerPub);
  if (shared.every((x) => x === 0)) throw new Error('Неверный ключ');
  const ikm = await subtle.importKey('raw', shared, 'HKDF', false, ['deriveBits']);
  const bits = await subtle.deriveBits({ name: 'HKDF', hash: 'SHA-256', salt: concat(epk, recipientPub), info: INFO_WRAP }, ikm, 256);
  return new Uint8Array(bits);
}

async function aesGcmEncrypt(key, plain, aad) {
  const iv = randomBytes(12);
  const k = await subtle.importKey('raw', key, 'AES-GCM', false, ['encrypt']);
  const ct = new Uint8Array(await subtle.encrypt({ name: 'AES-GCM', iv, additionalData: aad, tagLength: 128 }, k, plain));
  return concat(iv, ct);
}

async function aesGcmDecrypt(key, data, aad) {
  const k = await subtle.importKey('raw', key, 'AES-GCM', false, ['decrypt']);
  return new Uint8Array(await subtle.decrypt({ name: 'AES-GCM', iv: data.subarray(0, 12), additionalData: aad, tagLength: 128 }, k, data.subarray(12)));
}

// ---------- Файлы ----------

/** Шифрует файл: [iv 16][AES-CTR][HMAC 32]. Возвращает { data: Uint8Array, key: base64(64 байта) }. */
export async function encryptFile(plain) {
  const key = randomBytes(64);
  const iv = randomBytes(16);
  const aes = await subtle.importKey('raw', key.subarray(0, 32), 'AES-CTR', false, ['encrypt']);
  const ct = new Uint8Array(await subtle.encrypt({ name: 'AES-CTR', counter: iv, length: 128 }, aes, plain));
  const macKey = await subtle.importKey('raw', key.subarray(32), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const body = concat(iv, ct);
  const mac = new Uint8Array(await subtle.sign('HMAC', macKey, body));
  return { data: concat(body, mac), key: b64(key) };
}

/** Проверяет HMAC и расшифровывает файл, скачанный с сервера. */
export async function decryptFile(encrypted, keyB64) {
  const key = unb64(keyB64);
  const data = encrypted instanceof Uint8Array ? encrypted : new Uint8Array(encrypted);
  if (data.length < 48) throw new Error('Файл повреждён');
  const macKey = await subtle.importKey('raw', key.subarray(32, 64), { name: 'HMAC', hash: 'SHA-256' }, false, ['verify']);
  const ok = await subtle.verify('HMAC', macKey, data.subarray(data.length - 32), data.subarray(0, data.length - 32));
  if (!ok) throw new Error('Файл подменён или повреждён');
  const aes = await subtle.importKey('raw', key.subarray(0, 32), 'AES-CTR', false, ['decrypt']);
  return new Uint8Array(await subtle.decrypt({ name: 'AES-CTR', counter: data.subarray(0, 16), length: 128 }, aes, data.subarray(16, data.length - 32)));
}

// ---------- Хранение приватного ключа в браузере ----------
// Приватный ключ лежит в localStorage, зашифрованный неизвлекаемым ключом AES из IndexedDB
// (аналог Android Keystore): скрипт может им пользоваться, но не может его выгрузить.

function idb() {
  return new Promise((resolve, reject) => {
    const r = indexedDB.open('ryzikchat', 1);
    r.onupgradeneeded = () => r.result.createObjectStore('keys');
    r.onsuccess = () => resolve(r.result);
    r.onerror = () => reject(r.error);
  });
}

async function vaultKey() {
  const db = await idb();
  const get = (k) => new Promise((res, rej) => { const t = db.transaction('keys').objectStore('keys').get(k); t.onsuccess = () => res(t.result); t.onerror = () => rej(t.error); });
  let key = await get('vault');
  if (!key) {
    key = await subtle.generateKey({ name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt']);
    await new Promise((res, rej) => { const t = db.transaction('keys', 'readwrite').objectStore('keys').put(key, 'vault'); t.onsuccess = res; t.onerror = () => rej(t.error); });
  }
  return key;
}

export async function wrapLocal(secret) {
  const iv = randomBytes(12);
  const ct = new Uint8Array(await subtle.encrypt({ name: 'AES-GCM', iv }, await vaultKey(), secret));
  return b64(concat(iv, ct));
}

export async function unwrapLocal(wrapped) {
  const data = unb64(wrapped);
  return new Uint8Array(await subtle.decrypt({ name: 'AES-GCM', iv: data.subarray(0, 12) }, await vaultKey(), data.subarray(12)));
}
