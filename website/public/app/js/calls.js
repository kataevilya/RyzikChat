// Звонки WebRTC — тот же протокол сигналов, что CallManager.kt:
// { kind: offer|answer|ice|hangup|decline|busy|waiting|forbidden|unavailable, callId, sdp?, video?, candidate?, sdpMid?, sdpMLineIndex? }
import { store, api, callDuration } from './store.js';
import { h, icon, avatar, toast } from './ui.js';

let state = null; // { phase, peerId, callId, video, outgoing, startedAt, muted, cameraOn, peerOffline }
let pc = null;
let local = null;
let pendingOffer = null;
let pendingIce = [];
let remoteSet = false;
let timeout = null;
let ui = null;
let ringTimer = null;
let audioCtx = null;
let tickTimer = null;

export function initCalls() {
  store.on('call.signal', (from, d) => onSignal(from, d).catch((e) => console.error(e)));
}

function send(data, to = state?.peerId) {
  if (!to) return;
  if (!store.sendCallSignal(to, data)) toast('Нет соединения с сервером');
}

async function iceServers() {
  try {
    const cfg = await api.callsConfig();
    if (cfg?.iceServers?.length) return cfg.iceServers;
  } catch { /* */ }
  return [{ urls: 'stun:stun.l.google.com:19302' }];
}

export async function startCall(peerId, video) {
  if (state && state.phase !== 'ended') return;
  if (!store.connected) { toast('Звонки работают, когда есть соединение с сервером'); return; }
  const callId = crypto.randomUUID();
  state = { phase: 'outgoing', peerId, callId, video, outgoing: true, muted: false, cameraOn: video };
  render();
  try {
    await createPeer(video);
    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    send({ kind: 'offer', callId, sdp: offer.sdp, video });
    timeout = setTimeout(() => {
      if (state?.phase === 'outgoing') { send({ kind: 'hangup', callId }); finish('Нет ответа', 'missed'); }
    }, 60000);
  } catch (e) {
    console.error(e);
    finish(e?.name === 'NotAllowedError' ? 'Нет доступа к микрофону' : 'Не удалось начать звонок', 'failed');
  }
}

async function accept() {
  if (state?.phase !== 'incoming') return;
  stopRinging();
  state.phase = 'connecting';
  state.cameraOn = state.video;
  render();
  try {
    await createPeer(state.video);
    await pc.setRemoteDescription({ type: 'offer', sdp: pendingOffer });
    remoteSet = true;
    for (const c of pendingIce) await pc.addIceCandidate(c).catch(() => {});
    pendingIce = [];
    const answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);
    send({ kind: 'answer', callId: state.callId, sdp: answer.sdp });
  } catch (e) {
    console.error(e);
    send({ kind: 'hangup', callId: state.callId });
    finish(e?.name === 'NotAllowedError' ? 'Нет доступа к микрофону' : 'Не удалось соединиться', 'failed');
  }
}

function hangup() {
  if (!state) return;
  const incoming = state.phase === 'incoming';
  send({ kind: incoming ? 'decline' : 'hangup', callId: state.callId });
  if (incoming) finish('Звонок отклонён', 'declined');
  else finish('Звонок завершён', state.phase === 'active' ? 'ok' : 'cancelled');
}

async function onSignal(from, d) {
  const { kind, callId } = d;
  if (!kind || !callId) return;
  if (kind === 'offer') {
    if (state && state.phase !== 'ended') {
      if (state.callId !== callId) send({ kind: 'busy', callId }, from);
      return;
    }
    pendingOffer = d.sdp;
    pendingIce = [];
    remoteSet = false;
    state = { phase: 'incoming', peerId: from, callId, video: !!d.video, outgoing: false, muted: false, cameraOn: false };
    if (!store.user(from)) await store.loadUser(from).catch(() => {});
    render();
    startRinging();
    notifyIncoming();
    timeout = setTimeout(() => { if (state?.phase === 'incoming' && state.callId === callId) finish('Пропущенный звонок', 'missed'); }, 50000);
    return;
  }
  if (!state || state.callId !== callId || from !== state.peerId) return;
  switch (kind) {
    case 'answer':
      clearTimeout(timeout);
      state.phase = 'connecting';
      render();
      try {
        await pc.setRemoteDescription({ type: 'answer', sdp: d.sdp });
        remoteSet = true;
        for (const c of pendingIce) await pc.addIceCandidate(c).catch(() => {});
        pendingIce = [];
      } catch { finish('Не удалось соединиться', 'failed'); }
      break;
    case 'ice': {
      if (!d.candidate) return;
      const c = { candidate: d.candidate, sdpMid: d.sdpMid ?? '', sdpMLineIndex: d.sdpMLineIndex ?? 0 };
      if (remoteSet && pc) await pc.addIceCandidate(c).catch(() => {}); else pendingIce.push(c);
      break;
    }
    case 'hangup': finish('Звонок завершён', state.phase === 'active' ? 'ok' : (state.outgoing ? 'cancelled' : 'missed')); break;
    case 'decline': finish('Собеседник отклонил звонок', 'declined'); break;
    case 'busy': finish('Собеседник занят', 'busy'); break;
    case 'unavailable': finish('Собеседник не в сети', 'failed'); break;
    case 'forbidden': finish('Собеседник ограничил звонки', 'forbidden'); break;
    case 'waiting': state.peerOffline = true; render(); break;
    default:
  }
}

async function createPeer(video) {
  local = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true }, video: video ? { width: { ideal: 1280 }, height: { ideal: 720 }, facingMode: 'user' } : false });
  pc = new RTCPeerConnection({ iceServers: await iceServers() });
  for (const t of local.getTracks()) pc.addTrack(t, local);
  pc.onicecandidate = (e) => {
    if (!e.candidate || !state) return;
    send({ kind: 'ice', callId: state.callId, candidate: e.candidate.candidate, sdpMid: e.candidate.sdpMid, sdpMLineIndex: e.candidate.sdpMLineIndex });
  };
  pc.ontrack = (e) => {
    if (!ui) return;
    const stream = e.streams[0] ?? new MediaStream([e.track]);
    if (e.track.kind === 'video') { ui.remoteVideo.srcObject = stream; ui.remoteVideo.classList.remove('hidden'); }
    ui.remoteAudio.srcObject = stream;
    ui.remoteAudio.play().catch(() => {});
  };
  pc.onconnectionstatechange = () => {
    if (!state) return;
    if (pc.connectionState === 'connected' && (state.phase === 'connecting' || state.phase === 'outgoing')) {
      state.phase = 'active';
      state.startedAt = Date.now();
      render();
    } else if (pc.connectionState === 'failed') finish('Связь потеряна', state.phase === 'active' ? 'ok' : 'failed');
  };
}

function finish(text, status) {
  if (!state || state.phase === 'ended') return;
  clearTimeout(timeout);
  stopRinging();
  const s = state;
  // Запись о звонке в чате делает звонивший, как в приложении.
  if (s.outgoing) store.logCall(s.peerId, { video: s.video, status, duration: s.startedAt ? Date.now() - s.startedAt : 0 });
  state = { ...s, phase: 'ended', endText: text };
  try { pc?.close(); } catch { /* */ }
  pc = null;
  local?.getTracks().forEach((t) => t.stop());
  local = null;
  pendingOffer = null;
  pendingIce = [];
  remoteSet = false;
  render();
  setTimeout(() => { if (state?.phase === 'ended') { state = null; render(); } }, 1600);
}

// ---------- Интерфейс ----------

function render() {
  clearInterval(tickTimer);
  if (!state) { ui?.root.remove(); ui = null; return; }
  if (!ui) {
    const remoteVideo = h('video.remote-video.hidden', { autoplay: true, playsInline: true });
    const remoteAudio = h('audio', { autoplay: true });
    const localVideo = h('video.local-video.hidden', { autoplay: true, playsInline: true, muted: true });
    const info = h('div.call-info');
    const btns = h('div.call-btns');
    const root = h('div.call-screen', null, remoteVideo, localVideo, remoteAudio, info, btns);
    document.body.append(root);
    ui = { root, remoteVideo, remoteAudio, localVideo, info, btns };
  }
  const peer = store.user(state.peerId);
  if (local && state.video) { ui.localVideo.srcObject = local; ui.localVideo.classList.toggle('hidden', !state.cameraOn); }
  const status = () => {
    switch (state.phase) {
      case 'outgoing': return state.peerOffline ? 'Собеседник не в сети — звонок дойдёт, когда он появится' : 'Вызов…';
      case 'incoming': return state.video ? 'Входящий видеозвонок' : 'Входящий звонок';
      case 'connecting': return 'Соединение…';
      case 'active': return callDuration(Date.now() - state.startedAt);
      default: return state.endText ?? 'Звонок завершён';
    }
  };
  const statusEl = h('p', null, status());
  ui.info.className = 'call-info' + (state.phase === 'incoming' || state.phase === 'outgoing' ? ' ringing' : '');
  ui.info.replaceChildren(avatar(peer?.displayName ?? '?', peer?.avatarFileId, 120, { round: true }), h('h2', null, peer?.displayName ?? 'Звонок'), statusEl);
  if (state.phase === 'active') tickTimer = setInterval(() => { statusEl.textContent = status(); }, 1000);
  const b = (ic, label, run, cls = '') => h('div.call-btn-wrap', null, h('button.call-btn' + (cls ? '.' + cls : ''), { onclick: run, title: label }, icon(ic, 'fill')), h('span', null, label));
  ui.btns.replaceChildren();
  if (state.phase === 'incoming') {
    ui.btns.append(b('call_end', 'Отклонить', hangup, 'red'), b(state.video ? 'videocam' : 'call', 'Ответить', accept, 'green'));
  } else if (state.phase !== 'ended') {
    ui.btns.append(...[
      b(state.muted ? 'mic_off' : 'mic', state.muted ? 'Микрофон выкл.' : 'Микрофон', () => {
        state.muted = !state.muted;
        local?.getAudioTracks().forEach((t) => { t.enabled = !state.muted; });
        render();
      }, state.muted ? 'on' : ''),
      state.video ? b(state.cameraOn ? 'videocam' : 'videocam_off', 'Камера', () => {
        state.cameraOn = !state.cameraOn;
        local?.getVideoTracks().forEach((t) => { t.enabled = state.cameraOn; });
        render();
      }, state.cameraOn ? '' : 'on') : null,
      b('call_end', 'Завершить', hangup, 'red'),
    ].filter(Boolean));
  }
}

function startRinging() {
  stopRinging();
  const ring = () => {
    try {
      audioCtx ??= new AudioContext();
      const t = audioCtx.currentTime;
      for (const [f, d] of [[660, 0], [880, 0.18]]) {
        const o = audioCtx.createOscillator(), g = audioCtx.createGain();
        o.frequency.value = f;
        g.gain.setValueAtTime(0.0001, t + d);
        g.gain.exponentialRampToValueAtTime(0.15, t + d + 0.02);
        g.gain.exponentialRampToValueAtTime(0.0001, t + d + 0.16);
        o.connect(g).connect(audioCtx.destination);
        o.start(t + d); o.stop(t + d + 0.2);
      }
    } catch { /* */ }
  };
  ring();
  ringTimer = setInterval(ring, 1500);
}

function stopRinging() { clearInterval(ringTimer); ringTimer = null; }

function notifyIncoming() {
  if (document.visibilityState === 'visible' || !('Notification' in window) || Notification.permission !== 'granted') return;
  const peer = store.user(state.peerId);
  try {
    const n = new Notification(peer?.displayName ?? 'RyzikChat', { body: state.video ? 'Входящий видеозвонок' : 'Входящий звонок', tag: 'call', icon: '/app/icon-192.png', requireInteraction: true });
    n.onclick = () => { focus(); n.close(); };
  } catch { /* */ }
}
