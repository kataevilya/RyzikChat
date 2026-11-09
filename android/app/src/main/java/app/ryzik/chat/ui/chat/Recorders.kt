package app.ryzik.chat.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.camera.core.CameraSelector
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.FileRef
import app.ryzik.chat.data.MediaState
import app.ryzik.chat.data.SendStatus
import app.ryzik.chat.data.UiMessage
import app.ryzik.chat.ui.components.formatDuration
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Base64
import kotlin.math.max

/** Скругление «квадратиков» — нашей версии кружочков. */
val SquareShape = RoundedCornerShape(28.dp)
const val SQUARE_SIZE_DP = 240

// ===================== Проигрыватель голосовых и квадратиков =====================

/** Один общий проигрыватель: одновременно звучит только одно сообщение. */
object InlinePlayer {
    private var exo: ExoPlayer? = null
    private val scope = MainScope()
    private val _current = MutableStateFlow<String?>(null)
    val current: StateFlow<String?> = _current.asStateFlow()
    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()
    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    fun player(context: Context): ExoPlayer = exo ?: ExoPlayer.Builder(context.applicationContext).build().also { p ->
        p.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playing.value = isPlaying
                if (isPlaying) tick()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) stop()
            }
        })
        exo = p
    }

    private fun tick() {
        scope.launch {
            while (_playing.value) {
                val p = exo ?: break
                _position.value = p.currentPosition
                _progress.value = if (p.duration > 0) (p.currentPosition.toFloat() / p.duration).coerceIn(0f, 1f) else 0f
                delay(50)
            }
        }
    }

    fun toggle(context: Context, id: String, file: File) {
        val p = player(context)
        if (_current.value == id) {
            if (p.isPlaying) p.pause() else p.play()
            return
        }
        _current.value = id
        _progress.value = 0f
        _position.value = 0
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        p.playbackParameters = PlaybackParameters(_speed.value)
        p.prepare()
        p.play()
    }

    fun seek(id: String, fraction: Float) {
        val p = exo ?: return
        if (_current.value != id || p.duration <= 0) return
        p.seekTo((p.duration * fraction).toLong())
        _progress.value = fraction
    }

    fun cycleSpeed() {
        val next = when (_speed.value) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
        _speed.value = next
        exo?.playbackParameters = PlaybackParameters(next)
    }

    fun stop() {
        exo?.stop()
        _current.value = null
        _playing.value = false
        _progress.value = 0f
        _position.value = 0
    }
}

// ===================== Запись голосовых =====================

class VoiceRecording(val file: File, val durationMs: Long, val waveform: String)

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private val amps = ArrayList<Int>()
    val isRecording get() = recorder != null

    fun start(): Boolean {
        val f = RyzikApp.instance.repo.recordingFile("m4a")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(44100)
            r.setAudioEncodingBitRate(64000)
            r.setOutputFile(f.path)
            r.prepare()
            r.start()
            recorder = r
            file = f
            startedAt = System.currentTimeMillis()
            amps.clear()
            true
        } catch (e: Exception) {
            r.release()
            f.delete()
            false
        }
    }

    /** Громкость 0..1 прямо сейчас — для анимации. */
    fun level(): Float {
        val a = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        if (recorder != null) amps += a
        return (a / 20000f).coerceIn(0f, 1f)
    }

    fun elapsed() = if (recorder != null) System.currentTimeMillis() - startedAt else 0L

    fun stop(): VoiceRecording? {
        val r = recorder ?: return null
        val f = file!!
        val duration = elapsed()
        recorder = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        if (!ok || duration < 700) { f.delete(); return null }
        return VoiceRecording(f, duration, encodeWaveform(amps))
    }

    fun cancel() {
        val r = recorder ?: return
        recorder = null
        runCatching { r.stop() }
        r.release()
        file?.delete()
    }
}

/** 48 столбиков громкости 0..100, упакованные в base64. */
fun encodeWaveform(samples: List<Int>, bars: Int = 48): String {
    if (samples.isEmpty()) return ""
    val out = ByteArray(bars)
    val peak = max(samples.max(), 1)
    for (i in 0 until bars) {
        val from = i * samples.size / bars
        val to = max(from + 1, (i + 1) * samples.size / bars).coerceAtMost(samples.size)
        val v = samples.subList(from, to).max()
        out[i] = (v * 100 / peak).coerceIn(4, 100).toByte()
    }
    return Base64.getEncoder().encodeToString(out)
}

fun decodeWaveform(s: String, bars: Int = 48): List<Float> =
    runCatching { Base64.getDecoder().decode(s).map { (it.toInt() / 100f).coerceIn(0.04f, 1f) } }.getOrNull()?.takeIf { it.isNotEmpty() }
        ?: List(bars) { i -> 0.2f + 0.15f * ((i * 7) % 5) }

// ===================== Запись квадратиков =====================

/** Управляет записью квадратного видеосообщения с фронтальной камеры (CameraX). */
class SquareRecorder(private val context: Context) {
    var active by mutableStateOf(false)
        private set
    var startedAt by mutableLongStateOf(0L)
        private set
    private var recording: Recording? = null
    private var file: File? = null
    private var send = true
    private var finishRequested = false
    private var provider: ProcessCameraProvider? = null
    private var owner: LifecycleOwner? = null
    private var preview: Preview? = null
    private var capture: VideoCapture<Recorder>? = null
    /** Какая камера снимает. По умолчанию — фронтальная, переключается кнопкой под квадратом. */
    var front by mutableStateOf(true)
        private set
    var onReady: ((File, Long) -> Unit)? = null

    fun begin() {
        if (active) return
        finishRequested = false
        startedAt = 0
        front = true
        active = true
    }

    private fun selector(p: ProcessCameraProvider): CameraSelector {
        val wanted = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val other = if (front) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        return if (runCatching { p.hasCamera(wanted) }.getOrDefault(false)) wanted else other
    }

    /** Есть ли вторая камера, на которую можно переключиться. */
    fun canSwitch(): Boolean {
        val p = provider ?: return false
        return runCatching { p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) && p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)
    }

    /** Переключить фронтальную и основную камеру прямо во время записи: запись не прерывается. */
    fun switchCamera() {
        val p = provider ?: return
        val o = owner ?: return
        val pv = preview ?: return
        val vc = capture ?: return
        if (!canSwitch()) return
        front = !front
        runCatching {
            p.unbindAll()
            p.bindToLifecycle(o, selector(p), pv, vc)
        }.onFailure {
            front = !front
            runCatching { p.unbindAll(); p.bindToLifecycle(o, selector(p), pv, vc) }
        }
    }

    @androidx.annotation.OptIn(androidx.camera.video.ExperimentalPersistentRecording::class)
    @SuppressLint("MissingPermission")
    fun bind(owner: LifecycleOwner, view: PreviewView) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!active) return@addListener
            val p = runCatching { future.get() }.getOrNull() ?: run { active = false; return@addListener }
            provider = p
            this.owner = owner
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
            this.preview = preview
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                .build()
            val capture = VideoCapture.Builder(recorder).setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY).build()
            this.capture = capture
            val bound = runCatching {
                p.unbindAll()
                val sel = selector(p)
                front = sel == CameraSelector.DEFAULT_FRONT_CAMERA
                p.bindToLifecycle(owner, sel, preview, capture)
            }.isSuccess
            if (!bound) { active = false; return@addListener }
            if (finishRequested) { cleanup(); return@addListener }
            val f = RyzikApp.instance.repo.recordingFile("mp4")
            file = f
            recording = capture.output
                .prepareRecording(context, FileOutputOptions.Builder(f).build())
                .withAudioEnabled()
                // Запись переживает смену камеры: при переключении видео продолжается.
                .asPersistentRecording()
                .start(ContextCompat.getMainExecutor(context)) { ev ->
                    when (ev) {
                        is VideoRecordEvent.Start -> startedAt = System.currentTimeMillis()
                        is VideoRecordEvent.Finalize -> {
                            val duration = ev.recordingStats.recordedDurationNanos / 1_000_000
                            // Часть ошибок (например, камеру отпустили) всё равно оставляет годный файл.
                            val ok = ev.error != VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA && f.exists() && f.length() > 1024
                            if (send && ok && duration >= 800) onReady?.invoke(f, duration) else f.delete()
                            cleanup()
                        }
                    }
                }
        }, ContextCompat.getMainExecutor(context))
    }

    fun elapsed() = if (startedAt > 0) System.currentTimeMillis() - startedAt else 0L

    /** Отпустили палец: send = true — отправить, false — отменить. */
    fun finish(send: Boolean) {
        if (!active) return
        this.send = send
        finishRequested = true
        val r = recording
        if (r != null) r.stop() else if (provider != null) cleanup()
    }

    private fun cleanup() {
        recording = null
        runCatching { provider?.unbindAll() }
        provider = null
        owner = null
        preview = null
        capture = null
        active = false
        startedAt = 0
    }
}

/** Размеры видео с учётом поворота. */
fun videoSize(file: File): Pair<Int, Int> = runCatching {
    val r = MediaMetadataRetriever()
    r.setDataSource(file.path)
    var w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
    var h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
    val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
    r.release()
    if (rot == 90 || rot == 270) h to w else w to h
}.getOrDefault(0 to 0)

/** Экран записи квадратика: превью камеры в скруглённом квадрате и бегущая рамка-таймер. */
@Composable
fun SquareRecordingOverlay(recorder: SquareRecorder, limitMs: Long, cancelProgress: Float) {
    val context = LocalContext.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (recorder.active) {
            elapsed = recorder.elapsed()
            if (elapsed >= limitMs) recorder.finish(send = true)
            delay(50)
        }
    }
    val appear by animateFloatAsState(if (recorder.active) 1f else 0f, label = "appear")
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f * appear)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size((SQUARE_SIZE_DP + 40).dp), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            recorder.bind(owner, this)
                        }
                    },
                    modifier = Modifier.size((SQUARE_SIZE_DP + 20).dp).clip(SquareShape).background(Color.Black),
                )
                SquareProgressBorder((elapsed.toFloat() / limitMs).coerceIn(0f, 1f), Color.White.copy(alpha = 0.25f), accent, Modifier.size((SQUARE_SIZE_DP + 34).dp))
            }
            // Кнопка смены камеры: снизу справа под квадратом.
            Box(Modifier.width((SQUARE_SIZE_DP + 40).dp).height(52.dp)) {
                FilledTonalIconButton(
                    onClick = { recorder.switchCamera() },
                    modifier = Modifier.align(Alignment.CenterEnd).size(48.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White),
                ) {
                    val turn by animateFloatAsState(if (recorder.front) 0f else 180f, label = "camTurn")
                    Icon(Icons.Rounded.Cameraswitch, if (recorder.front) "Основная камера" else "Фронтальная камера", Modifier.graphicsLayer { rotationY = turn })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecordingDot()
                Spacer(Modifier.width(8.dp))
                Text(formatDuration(elapsed), color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (cancelProgress > 0.6f) "Отпустите для отмены" else "Отпустите — отправить · Влево — отмена",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    DisposableEffect(Unit) { onDispose { if (recorder.active) recorder.finish(send = false) } }
}

@Composable
fun RecordingDot() {
    val t = rememberInfiniteTransition(label = "rec")
    val a by t.animateFloat(1f, 0.2f, infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFE53935).copy(alpha = a)))
}

/** Рамка по периметру скруглённого квадрата, заполняется по часовой стрелке. */
@Composable
fun SquareProgressBorder(progress: Float, track: Color, color: Color, modifier: Modifier, stroke: Float = 10f) {
    Canvas(modifier) {
        val inset = stroke / 2
        val r = 30.dp.toPx()
        val path = Path().apply {
            addRoundRect(RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(r, r)))
        }
        drawPath(path, track, style = Stroke(stroke))
        if (progress > 0f) {
            val m = PathMeasure()
            m.setPath(path, false)
            val seg = Path()
            m.getSegment(0f, m.length * progress, seg, true)
            drawPath(seg, color, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

// ===================== Пузыри =====================

@Composable
fun VoiceContent(msg: UiMessage, file: FileRef, color: Color, accent: Color) {
    val repo = RyzikApp.instance.repo
    val context = LocalContext.current
    val state by mediaStateOf(file)
    val current by InlinePlayer.current.collectAsState()
    val playing by InlinePlayer.playing.collectAsState()
    val progress by InlinePlayer.progress.collectAsState()
    val position by InlinePlayer.position.collectAsState()
    val speed by InlinePlayer.speed.collectAsState()
    val isCurrent = current == msg.id
    val local = msg.localFile ?: (state as? MediaState.Ready)?.file
    var wantPlay by remember { mutableStateOf(false) }
    LaunchedEffect(file.id) { if (local == null) repo.download(file) }
    LaunchedEffect(local, wantPlay) {
        if (wantPlay && local != null) { wantPlay = false; InlinePlayer.toggle(context, msg.id, local) }
    }
    val bars = remember(file.waveform) { decodeWaveform(file.waveform) }

    Row(Modifier.padding(start = 8.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(accent).clickable {
                if (local != null) InlinePlayer.toggle(context, msg.id, local) else { wantPlay = true; repo.download(file) }
            },
            contentAlignment = Alignment.Center,
        ) {
            val loading = msg.status == SendStatus.Sending || state is MediaState.Loading
            when {
                loading -> CircularProgressIndicator(Modifier.size(30.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.5.dp)
                local == null -> Icon(Icons.Rounded.Download, "Скачать", tint = MaterialTheme.colorScheme.onPrimary)
                else -> Icon(if (isCurrent && playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Слушать", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            val shown = if (isCurrent) progress else 0f
            Canvas(
                Modifier
                    .width(170.dp)
                    .height(30.dp)
                    .pointerInput(isCurrent) {
                        if (isCurrent) detectTapGestures { o -> InlinePlayer.seek(msg.id, (o.x / size.width).coerceIn(0f, 1f)) }
                    },
            ) {
                val n = bars.size
                val gap = 2.dp.toPx()
                val w = (size.width - gap * (n - 1)) / n
                bars.forEachIndexed { i, v ->
                    val h = max(3.dp.toPx(), v * size.height)
                    val x = i * (w + gap)
                    val played = (i + 0.5f) / n <= shown
                    drawRoundRect(
                        if (played) accent else color.copy(alpha = 0.35f),
                        topLeft = Offset(x, (size.height - h) / 2),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(w / 2, w / 2),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDuration(if (isCurrent) position else file.durationMs),
                    color = color.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                )
                if (isCurrent) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${if (speed % 1f == 0f) speed.toInt() else speed}x",
                        color = accent,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { InlinePlayer.cycleSpeed() }.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

/** Квадратик в ленте: превью-кадр, по нажатию играет прямо в чате со звуком. */
@Composable
fun SquareContent(msg: UiMessage, file: FileRef, autoDownload: Boolean) {
    val repo = RyzikApp.instance.repo
    val context = LocalContext.current
    val state by mediaStateOf(file)
    val current by InlinePlayer.current.collectAsState()
    val playing by InlinePlayer.playing.collectAsState()
    val progress by InlinePlayer.progress.collectAsState()
    val position by InlinePlayer.position.collectAsState()
    val isCurrent = current == msg.id
    val local = msg.localFile ?: (state as? MediaState.Ready)?.file
    LaunchedEffect(file.id, autoDownload) { if (autoDownload && local == null) repo.download(file) }
    val loader = remember { coil.ImageLoader.Builder(context).components { add(coil.decode.VideoFrameDecoder.Factory()) }.build() }
    val size = SQUARE_SIZE_DP.dp
    val scale by animateFloatAsState(if (isCurrent) 1.08f else 1f, label = "grow")

    Box(
        Modifier.size(size * scale + 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size * scale).clip(SquareShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            if (local != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(local).videoFrameMillis(300).build(),
                    imageLoader = loader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (isCurrent) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            player = InlinePlayer.player(ctx)
                        }
                    },
                    onRelease = { it.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val uploading = msg.status == SendStatus.Sending
            when {
                uploading -> CircularProgressIndicator(progress = { msg.uploadProgress ?: 0f }, color = Color.White, modifier = Modifier.size(44.dp))
                state is MediaState.Loading -> CircularProgressIndicator(color = Color.White, modifier = Modifier.size(44.dp))
                local == null -> Icon(Icons.Rounded.Download, "Скачать", tint = Color.White, modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)).padding(8.dp))
                !isCurrent || !playing -> Icon(Icons.Rounded.PlayArrow, "Смотреть", tint = Color.White, modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)).padding(8.dp))
            }
            Row(
                Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.4f)).padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatDuration(if (isCurrent) position else file.durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                if (!isCurrent) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.VolumeOff, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
        if (isCurrent) SquareProgressBorder(progress, Color.Transparent, MaterialTheme.colorScheme.primary, Modifier.size(size * scale + 10.dp), stroke = 8f)
    }
}

/** Нажатие на квадратик: скачать или играть/пауза. */
fun toggleSquare(context: Context, msg: UiMessage) {
    val file = msg.content?.file ?: return
    val repo = RyzikApp.instance.repo
    val local = msg.localFile ?: (repo.mediaState(file.id).value as? MediaState.Ready)?.file
    if (local != null) InlinePlayer.toggle(context, msg.id, local) else repo.download(file)
}
