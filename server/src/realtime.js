import { WebSocketServer } from 'ws';

/**
 * Хаб WebSocket-подключений. Один пользователь может быть онлайн с нескольких устройств.
 * Клиент подключается к /ws?token=..., сервер шлёт JSON-события:
 *   message.new / message.updated / chat.new / chat.updated / chat.removed / chat.streak / read / typing / presence / user.updated
 *   story.new / story.deleted / story.reaction
 * Клиент может слать: { type: "typing", chatId }, { type: "ping" } и { type: "presence", active }.
 * «В сети» — только пока приложение открыто: фоновое соединение подключается с ?active=0.
 */
export class Hub {
  constructor() {
    this.sockets = new Map(); // userId -> Set<WebSocket>
    this.locals = null;
    // Звонки, которые ещё не приняли: calleeId -> { callId, from, signals[], at }.
    // Если собеседник не в сети или только что переключил аккаунт, он получит
    // offer и ICE-кандидаты, как только подключится (в течение CALL_RING_MS).
    this.pendingCalls = new Map();
  }

  static CALL_RING_MS = 60_000;

  /** Запоминает сигнал звонка, чтобы доставить его позже, и чистит законченные звонки. */
  trackCallSignal(from, to, data) {
    const kind = data.kind;
    const callId = data.callId;
    const toCallee = this.pendingCalls.get(to);
    const fromCallee = this.pendingCalls.get(from);
    if (kind === 'offer') {
      this.pendingCalls.set(to, { callId, from, signals: [data], at: Date.now() });
    } else if (toCallee && toCallee.callId === callId && toCallee.from === from) {
      if (kind === 'hangup') this.pendingCalls.delete(to);
      else if (toCallee.signals.length < 200) toCallee.signals.push(data);
    } else if (fromCallee && fromCallee.callId === callId && fromCallee.from === to) {
      // Собеседник ответил, отклонил или занят — звонок больше не ждёт.
      this.pendingCalls.delete(from);
    }
  }

  /** Новое подключение: отдаём звонок, который ещё звонит. */
  replayPendingCall(userId, ws) {
    const p = this.pendingCalls.get(userId);
    if (!p) return;
    if (Date.now() - p.at > Hub.CALL_RING_MS) {
      this.pendingCalls.delete(userId);
      return;
    }
    for (const data of p.signals) safeSend(ws, JSON.stringify({ type: 'call.signal', from: p.from, data }));
  }

  isOnline(userId) {
    for (const ws of this.sockets.get(userId) ?? []) if (ws.active) return true;
    return false;
  }

  /** Есть ли хоть одно подключение (даже фоновое): туда можно доставить звонок. */
  isConnected(userId) {
    return (this.sockets.get(userId)?.size ?? 0) > 0;
  }

  /** Сообщает собеседникам, если пользователь появился в сети или ушёл. */
  presenceChanged(userId, wasOnline) {
    const now = this.isOnline(userId);
    if (now === wasOnline) return;
    this.locals?.touchLastSeen(userId);
    this.broadcastUser(userId, { type: 'presence', userId, online: now, lastSeen: Date.now() });
  }

  sendToUsers(userIds, event) {
    const data = JSON.stringify(event);
    for (const id of new Set(userIds)) {
      for (const ws of this.sockets.get(id) ?? []) {
        safeSend(ws, data);
      }
    }
  }

  /** Закрывает все подключения пользователя (бан): клиент увидит код 4003. */
  disconnectUser(userId, reason = 'banned') {
    for (const ws of this.sockets.get(userId) ?? []) { try { ws.close(4003, reason); } catch { ws.terminate(); } }
  }

  /** Событие про пользователя: ему самому и всем, с кем у него есть общий чат. */
  broadcastUser(userId, event) {
    const peers = this.locals ? this.locals.sharedChatPeers(userId) : [];
    this.sendToUsers([userId, ...peers], event);
  }

  attach(server, locals) {
    this.locals = locals;
    const wss = new WebSocketServer({ server, path: '/ws' });

    // Без обработчиков 'error' любая сетевая ошибка сокета роняет весь сервер.
    wss.on('error', (e) => console.warn(`WebSocket-сервер: ${e.message}`));
    wss.on('connection', (ws, req) => {
      ws.on('error', () => ws.terminate());
      try { this.onConnection(ws, req, locals); } catch (e) {
        console.error('WebSocket: ошибка подключения', e);
        try { ws.close(1011, 'error'); } catch { ws.terminate(); }
      }
    });

    const interval = setInterval(() => {
      for (const ws of wss.clients) {
        if (!ws.isAlive) { ws.terminate(); continue; }
        ws.isAlive = false;
        try { ws.ping(); } catch { ws.terminate(); }
      }
    }, 30_000);
    wss.on('close', () => clearInterval(interval));
    return wss;
  }

  onConnection(ws, req, locals) {
    const url = new URL(req.url, 'http://localhost');
    const userId = locals.resolveToken(url.searchParams.get('token') ?? '', req);
    if (!userId) {
      ws.close(4001, 'unauthorized');
      return;
    }
    const wasOnline = this.isOnline(userId);
    if (!this.sockets.has(userId)) this.sockets.set(userId, new Set());
    ws.active = url.searchParams.get('active') !== '0';
    this.sockets.get(userId).add(ws);
    ws.isAlive = true;
    if (ws.active) locals.touchLastSeen(userId);
    this.presenceChanged(userId, wasOnline);
    this.replayPendingCall(userId, ws);

    ws.on('pong', () => { ws.isAlive = true; });
    ws.on('message', (raw) => {
      let msg;
      try { msg = JSON.parse(String(raw)); } catch { return; }
      if (!msg || typeof msg !== 'object') return;
      try { this.onMessage(ws, userId, msg, locals); } catch (e) { console.error('WebSocket: ошибка обработки', e); }
    });
    ws.on('close', () => {
      try {
        const was = this.isOnline(userId);
        const set = this.sockets.get(userId);
        set?.delete(ws);
        if (set && set.size === 0) this.sockets.delete(userId);
        this.presenceChanged(userId, was);
      } catch (e) { console.error('WebSocket: ошибка при отключении', e); }
    });
  }

  onMessage(ws, userId, msg, locals) {
    if (msg.type === 'typing' && typeof msg.chatId === 'string' && locals.isMember(msg.chatId, userId)) {
      const others = locals.memberIds(msg.chatId).filter((id) => id !== userId);
      this.sendToUsers(others, { type: 'typing', chatId: msg.chatId, userId, action: msg.action ?? 'typing' });
    } else if (msg.type === 'call.signal' && typeof msg.to === 'string' && msg.data && typeof msg.data === 'object') {
      // Сигналы WebRTC (offer/answer/ice/hangup…) пересылаем, только если у людей есть общий чат.
      if (msg.data.kind === 'offer' && msg.to !== userId && locals.canCall && !locals.canCall(userId, msg.to)) {
        safeSend(ws, JSON.stringify({ type: 'call.signal', from: msg.to, data: { kind: 'forbidden', callId: msg.data.callId } }));
      } else if (msg.to !== userId && locals.sharedChatPeers(userId).includes(msg.to) && !locals.isBlocked(userId, msg.to)) {
        const delivered = this.isConnected(msg.to);
        this.trackCallSignal(userId, msg.to, msg.data);
        this.sendToUsers([msg.to], { type: 'call.signal', from: userId, data: msg.data });
        if (!delivered && msg.data.kind === 'offer') {
          // Не сбрасываем звонок: он дойдёт, когда собеседник появится в сети.
          safeSend(ws, JSON.stringify({ type: 'call.signal', from: msg.to, data: { kind: 'waiting', callId: msg.data.callId } }));
        }
      }
    } else if (msg.type === 'presence') {
      const was = this.isOnline(userId);
      ws.active = !!msg.active;
      this.presenceChanged(userId, was);
    } else if (msg.type === 'ping') {
      safeSend(ws, JSON.stringify({ type: 'pong' }));
    }
  }
}

function safeSend(ws, data) {
  if (ws.readyState !== ws.OPEN) return;
  try { ws.send(data); } catch { /* сокет уже закрывается */ }
}
