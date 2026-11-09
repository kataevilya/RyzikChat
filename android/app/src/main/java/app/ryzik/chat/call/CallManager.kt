package app.ryzik.chat.call

import app.ryzik.chat.data.CallRef
import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.ChatRepository
import app.ryzik.chat.notify.Notifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class CallPhase { Idle, Outgoing, Incoming, Connecting, Active, Ended }

data class CallState(
    val phase: CallPhase = CallPhase.Idle,
    val peerId: String = "",
    val callId: String = "",
    val video: Boolean = false,
    val muted: Boolean = false,
    val speaker: Boolean = false,
    val cameraOn: Boolean = false,
    val startedAt: Long = 0,
    val endReason: String = "",
    /** Собеседник не в сети: звонок ждёт, пока он подключится. */
    val peerOffline: Boolean = false,
    /** Звоним мы (а не нам): тогда мы и пишем запись о звонке в чат. */
    val outgoing: Boolean = false,
)

/**
 * Звонки через WebRTC: голос и видео идут напрямую между телефонами и шифруются (DTLS-SRTP).
 * Наш сервер только передаёт сигналы (offer/answer/ice) через WebSocket.
 */
class CallManager(private val context: Context, private val repo: ChatRepository) {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(CallState())
    val state: StateFlow<CallState> = _state.asStateFlow()

    private val _localVideo = MutableStateFlow<VideoTrack?>(null)
    val localVideo: StateFlow<VideoTrack?> = _localVideo.asStateFlow()
    private val _remoteVideo = MutableStateFlow<VideoTrack?>(null)
    val remoteVideo: StateFlow<VideoTrack?> = _remoteVideo.asStateFlow()

    val eglBase: EglBase by lazy { EglBase.create() }
    private var factory: PeerConnectionFactory? = null
    private var pc: PeerConnection? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var capturer: CameraVideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null

    private var pendingOffer: String? = null
    private val pendingIce = mutableListOf<IceCandidate>()
    private var remoteSet = false
    private var timeoutJob: Job? = null

    /** «Ответить» нажали в уведомлении другого аккаунта: звонок придёт после переключения, принимаем его сразу. */
    @Volatile var autoAcceptUntil: Long = 0L
    private var ringtone: Ringtone? = null
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        scope.launch { repo.callSignals.collect { (from, data) -> onSignal(from, data) } }
    }

    private fun factory(): PeerConnectionFactory = factory ?: run {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setAudioDeviceModule(JavaAudioDeviceModule.builder(context).createAudioDeviceModule())
            .createPeerConnectionFactory()
            .also { factory = it }
    }

    // ================= Действия пользователя =================

    fun startCall(peerId: String, video: Boolean) {
        if (_state.value.phase != CallPhase.Idle && _state.value.phase != CallPhase.Ended) return
        val callId = UUID.randomUUID().toString()
        _state.value = CallState(CallPhase.Outgoing, peerId, callId, video, speaker = video, cameraOn = video, outgoing = true)
        scope.launch {
            try {
                createPeer(video)
                val offer = createSdp(offer = true)
                setLocal(offer)
                send(buildJsonObject {
                    put("kind", "offer")
                    put("callId", callId)
                    put("sdp", offer.description)
                    put("video", video)
                })
                timeoutJob = scope.launch {
                    delay(60_000)
                    if (_state.value.phase == CallPhase.Outgoing) {
                        send(buildJsonObject { put("kind", "hangup"); put("callId", callId) })
                        finish("Нет ответа")
                    }
                }
            } catch (e: Exception) {
                finish("Не удалось начать звонок")
            }
        }
    }

    fun accept() {
        val s = _state.value
        if (s.phase != CallPhase.Incoming) return
        stopRinging()
        Notifier.cancelCall(context)
        _state.update { it.copy(phase = CallPhase.Connecting, speaker = it.video, cameraOn = it.video) }
        scope.launch {
            try {
                createPeer(s.video)
                setRemote(SessionDescription(SessionDescription.Type.OFFER, pendingOffer ?: error("no offer")))
                val answer = createSdp(offer = false)
                setLocal(answer)
                send(buildJsonObject { put("kind", "answer"); put("callId", s.callId); put("sdp", answer.description) })
            } catch (e: Exception) {
                send(buildJsonObject { put("kind", "hangup"); put("callId", s.callId) })
                finish("Не удалось соединиться")
            }
        }
    }

    fun decline() {
        val s = _state.value
        scope.launch {
            send(buildJsonObject { put("kind", if (s.phase == CallPhase.Incoming) "decline" else "hangup"); put("callId", s.callId) })
            finish(if (s.phase == CallPhase.Incoming) "Звонок отклонён" else "Звонок завершён")
        }
    }

    fun hangup() = decline()

    fun toggleMute() {
        val muted = !_state.value.muted
        audioTrack?.setEnabled(!muted)
        _state.update { it.copy(muted = muted) }
    }

    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        val on = !_state.value.speaker
        audio.isSpeakerphoneOn = on
        _state.update { it.copy(speaker = on) }
    }

    fun toggleCamera() {
        val on = !_state.value.cameraOn
        _localVideo.value?.setEnabled(on)
        _state.update { it.copy(cameraOn = on) }
    }

    fun switchCamera() {
        capturer?.switchCamera(null)
    }

    /** Экран звонка закрыт после завершения. */
    fun dismiss() {
        if (_state.value.phase == CallPhase.Ended) _state.value = CallState()
    }

    // ================= Сигналы =================

    private suspend fun onSignal(from: String, d: JsonObject) {
        val kind = d["kind"]?.jsonPrimitive?.contentOrNull ?: return
        val callId = d["callId"]?.jsonPrimitive?.contentOrNull ?: return
        val s = _state.value
        if (kind == "offer") {
            if (s.phase != CallPhase.Idle && s.phase != CallPhase.Ended) {
                if (s.callId != callId) send(buildJsonObject { put("kind", "busy"); put("callId", callId) }, to = from)
                return
            }
            pendingOffer = d["sdp"]?.jsonPrimitive?.contentOrNull ?: return
            pendingIce.clear()
            remoteSet = false
            val video = d["video"]?.jsonPrimitive?.booleanOrNull == true
            _state.value = CallState(CallPhase.Incoming, from, callId, video)
            startRinging()
            val name = repo.users.value[from]?.displayName
                ?: runCatching { repo.loadUser(from).displayName }.getOrNull()
                ?: "Входящий звонок"
            if (System.currentTimeMillis() < autoAcceptUntil) {
                autoAcceptUntil = 0L
                accept()
                return
            }
            if (RyzikApp.instance.prefs.settings.first().callNotifications) Notifier.showIncomingCall(context, name, video)
            timeoutJob = scope.launch {
                delay(50_000)
                if (_state.value.phase == CallPhase.Incoming && _state.value.callId == callId) finish("Пропущенный звонок")
            }
            return
        }
        if (s.callId != callId || from != s.peerId) return
        when (kind) {
            "answer" -> {
                timeoutJob?.cancel()
                _state.update { it.copy(phase = CallPhase.Connecting) }
                val sdp = d["sdp"]?.jsonPrimitive?.contentOrNull ?: return
                runCatching { setRemote(SessionDescription(SessionDescription.Type.ANSWER, sdp)) }
                    .onFailure { finish("Не удалось соединиться") }
            }
            "ice" -> {
                val c = IceCandidate(
                    d["sdpMid"]?.jsonPrimitive?.contentOrNull ?: "",
                    d["sdpMLineIndex"]?.jsonPrimitive?.intOrNull ?: 0,
                    d["candidate"]?.jsonPrimitive?.contentOrNull ?: return,
                )
                if (remoteSet && pc != null) pc?.addIceCandidate(c) else pendingIce += c
            }
            "hangup" -> finish("Звонок завершён")
            "decline" -> finish("Собеседник отклонил звонок")
            "busy" -> finish("Собеседник занят")
            "unavailable" -> finish("Собеседник не в сети")
            "forbidden" -> finish("Собеседник ограничил звонки")
            "waiting" -> _state.update { it.copy(peerOffline = true) }
        }
    }

    private fun send(data: JsonObject, to: String = _state.value.peerId) {
        repo.sendCallSignal(to, data)
    }

    // ================= WebRTC =================

    private suspend fun iceServers(): List<PeerConnection.IceServer> {
        val cfg = runCatching { repo.callsConfig() }.getOrNull()
        val list = cfg?.get("iceServers")?.jsonArray?.mapNotNull { el ->
            val o = el.jsonObject
            val urls = when (val u = o["urls"]) {
                is JsonArray -> u.map { it.jsonPrimitive.content }
                is JsonPrimitive -> listOf(u.content)
                else -> return@mapNotNull null
            }
            PeerConnection.IceServer.builder(urls)
                .setUsername(o["username"]?.jsonPrimitive?.contentOrNull ?: "")
                .setPassword(o["credential"]?.jsonPrimitive?.contentOrNull ?: "")
                .createIceServer()
        }
        return list?.takeIf { it.isNotEmpty() }
            ?: listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
    }

    @Suppress("DEPRECATION")
    private suspend fun createPeer(video: Boolean) {
        val f = factory()
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        audio.isSpeakerphoneOn = video

        val config = PeerConnection.RTCConfiguration(iceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val peer = f.createPeerConnection(config, observer) ?: error("peer")
        pc = peer

        audioTrack = f.createAudioTrack("audio0", f.createAudioSource(MediaConstraints())).also {
            peer.addTrack(it, listOf("ryzik"))
        }
        if (video) {
            val enumerator = Camera2Enumerator(context)
            val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: enumerator.deviceNames.firstOrNull()
            if (name != null) {
                val cap = enumerator.createCapturer(name, null)
                val helper = SurfaceTextureHelper.create("camera", eglBase.eglBaseContext)
                val source = f.createVideoSource(false)
                cap.initialize(helper, context, source.capturerObserver)
                cap.startCapture(1280, 720, 30)
                capturer = cap
                textureHelper = helper
                videoSource = source
                val track = f.createVideoTrack("video0", source)
                peer.addTrack(track, listOf("ryzik"))
                _localVideo.value = track
            }
        }
    }

    private val observer = object : PeerConnection.Observer {
        override fun onIceCandidate(c: IceCandidate) {
            val s = _state.value
            send(buildJsonObject {
                put("kind", "ice")
                put("callId", s.callId)
                put("candidate", c.sdp)
                put("sdpMid", c.sdpMid)
                put("sdpMLineIndex", c.sdpMLineIndex)
            })
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            scope.launch {
                when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> {
                        Notifier.cancelCall(context)
                        _state.update { if (it.phase == CallPhase.Connecting || it.phase == CallPhase.Outgoing) it.copy(phase = CallPhase.Active, startedAt = System.currentTimeMillis()) else it }
                    }
                    PeerConnection.PeerConnectionState.FAILED -> finish("Связь потеряна")
                    else -> Unit
                }
            }
        }

        override fun onTrack(transceiver: RtpTransceiver) {
            (transceiver.receiver.track() as? VideoTrack)?.let { _remoteVideo.value = it }
        }

        override fun onSignalingChange(s: PeerConnection.SignalingState) {}
        override fun onIceConnectionChange(s: PeerConnection.IceConnectionState) {}
        override fun onIceConnectionReceivingChange(b: Boolean) {}
        override fun onIceGatheringChange(s: PeerConnection.IceGatheringState) {}
        override fun onIceCandidatesRemoved(c: Array<out IceCandidate>) {}
        override fun onAddStream(s: MediaStream) {}
        override fun onRemoveStream(s: MediaStream) {}
        override fun onDataChannel(d: DataChannel) {}
        override fun onRenegotiationNeeded() {}
    }

    private suspend fun createSdp(offer: Boolean): SessionDescription = suspendCoroutine { cont ->
        val obs = object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) = cont.resume(sdp)
            override fun onCreateFailure(e: String?) = cont.resumeWith(Result.failure(IllegalStateException(e)))
            override fun onSetSuccess() {}
            override fun onSetFailure(e: String?) {}
        }
        val c = MediaConstraints()
        if (offer) pc!!.createOffer(obs, c) else pc!!.createAnswer(obs, c)
    }

    private suspend fun setLocal(sdp: SessionDescription) = suspendCoroutine<Unit> { cont ->
        pc!!.setLocalDescription(setObserver { cont.resumeWith(it) }, sdp)
    }

    private suspend fun setRemote(sdp: SessionDescription) {
        suspendCoroutine<Unit> { cont -> pc!!.setRemoteDescription(setObserver { cont.resumeWith(it) }, sdp) }
        remoteSet = true
        pendingIce.forEach { pc?.addIceCandidate(it) }
        pendingIce.clear()
    }

    private fun setObserver(done: (Result<Unit>) -> Unit) = object : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onCreateFailure(e: String?) {}
        override fun onSetSuccess() = done(Result.success(Unit))
        override fun onSetFailure(e: String?) = done(Result.failure(IllegalStateException(e)))
    }

    // ================= Завершение =================

    @Suppress("DEPRECATION")
    private fun finish(reason: String) {
        val before = _state.value
        if (before.phase == CallPhase.Incoming && reason != "Звонок отклонён") {
            // Не ответили: оставляем уведомление «Пропущенный звонок».
            val name = repo.users.value[before.peerId]?.displayName ?: "Собеседник"
            val chatId = repo.chats.value.firstOrNull { c -> c.type == "direct" && c.members.any { it.user.id == before.peerId } }?.id
            Notifier.missedCall(context, before.peerId, name, before.video, chatId)
        }
        if (before.outgoing && before.peerId.isNotEmpty() && before.phase != CallPhase.Idle && before.phase != CallPhase.Ended) {
            val status = when {
                before.startedAt > 0 -> "ok"
                reason == "Нет ответа" || reason == "Собеседник не в сети" -> "missed"
                reason == "Собеседник отклонил звонок" -> "declined"
                reason == "Собеседник занят" -> "busy"
                reason == "Собеседник ограничил звонки" -> "forbidden"
                reason == "Звонок завершён" -> "cancelled"
                else -> "failed"
            }
            val duration = if (before.startedAt > 0) System.currentTimeMillis() - before.startedAt else 0L
            repo.logCall(before.peerId, CallRef(video = before.video, status = status, duration = duration))
        }
        timeoutJob?.cancel()
        stopRinging()
        Notifier.cancelCall(context)
        _localVideo.value = null
        _remoteVideo.value = null
        runCatching { capturer?.stopCapture() }
        capturer?.dispose(); capturer = null
        videoSource?.dispose(); videoSource = null
        textureHelper?.dispose(); textureHelper = null
        audioTrack = null
        runCatching { pc?.close() }
        runCatching { pc?.dispose() }
        pc = null
        pendingOffer = null
        pendingIce.clear()
        remoteSet = false
        audio.mode = AudioManager.MODE_NORMAL
        audio.isSpeakerphoneOn = false
        if (_state.value.phase != CallPhase.Idle) _state.update { it.copy(phase = CallPhase.Ended, endReason = reason) }
        scope.launch {
            delay(2500)
            if (_state.value.phase == CallPhase.Ended) _state.value = CallState()
        }
    }

    private fun startRinging() {
        runCatching {
            ringtone = RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.also { it.play() }
        }
    }

    private fun stopRinging() {
        runCatching { ringtone?.stop() }
        ringtone = null
    }
}
