/**
 * Проверка запросов. Тело POST/PUT/PATCH должно быть JSON-объектом, а известные поля —
 * нужного типа. Иначе клиент получает понятную 400, а не 500 где-то в глубине обработчика.
 * null и отсутствие поля разрешены: обязательность проверяет сам обработчик.
 */
const STRING = [
  'username', 'displayName', 'password', 'publicKey', 'encryptedPrivateKey', 'device', 'challengeId',
  'accountPassword', 'currentPassword', 'hint', 'oldPassword', 'newPassword', 'bio', 'avatarFileId',
  'emojiStatus', 'title', 'description', 'payload', 'replyTo', 'forwardedFrom', 'clientId', 'type',
  'emoji', 'color', 'reason', 'role', 'fileId', 'toUserId', 'userId', 'itemId', 'giftId', 'note',
  'notes', 'caption', 'animation', 'mime', 'callPrivacy', 'versionName', 'message', 'code', 'groupId', 'confirm',
  'kind', 'text', 'bg', 'privacy',
];
const STRING_LIST = ['memberIds', 'userIds', 'ids'];
const NUMBER = ['resalePrice', 'amount', 'price', 'supply', 'messagePrice', 'months', 'days', 'seq', 'versionCode'];
const BOOL = ['isPublic', 'isAdmin', 'isPremium', 'hidden', 'active', 'muted', 'pinned', 'archived', 'verified', 'deleteMessages', 'banDevice', 'banIp', 'flux', 'admins', 'badges'];
const OBJECT = ['profileStyle'];

const RULES = new Map([
  ...STRING.map((k) => [k, ['строкой', (v) => typeof v === 'string' && v.length <= 1_000_000]]),
  ...STRING_LIST.map((k) => [k, ['списком', (v) => Array.isArray(v) && v.length <= 1000 && v.every((x) => typeof x === 'string')]]),
  ...NUMBER.map((k) => [k, ['числом', (v) => (typeof v === 'number' && Number.isFinite(v)) || (typeof v === 'string' && v.trim() !== '' && Number.isFinite(Number(v)))]]),
  ...BOOL.map((k) => [k, ['да/нет', (v) => typeof v === 'boolean' || v === 0 || v === 1]]),
  ...OBJECT.map((k) => [k, ['объектом', (v) => typeof v === 'object' && !Array.isArray(v)]]),
]);

/** Возвращает текст ошибки или null, если тело в порядке. */
export function checkBody(body) {
  if (body === undefined) return null;
  if (body === null || typeof body !== 'object' || Array.isArray(body)) return 'Тело запроса должно быть JSON-объектом';
  for (const [key, value] of Object.entries(body)) {
    if (value === null || value === undefined) continue;
    const rule = RULES.get(key);
    if (rule && !rule[1](value)) return `Поле «${key}» должно быть ${rule[0]}`;
  }
  return null;
}

/** Строки в query тоже: ?q[]=1 превращается в массив — такое не принимаем. */
export function checkQuery(query) {
  for (const [key, value] of Object.entries(query ?? {})) {
    if (typeof value !== 'string') return `Параметр «${key}» должен быть строкой`;
  }
  return null;
}
