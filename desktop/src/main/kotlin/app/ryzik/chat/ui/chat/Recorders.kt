package app.ryzik.chat.ui.chat

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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.unit.dp
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.FileRef
import app.ryzik.chat.data.MediaState
import app.ryzik.chat.data.SendStatus
import app.ryzik.chat.data.UiMessage
import app.ryzik.chat.ui.components.formatDuration
import coil.compose.AsyncImage
import kotlinx.coroutines.MainScope
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import app.ryzik.chat.platform.FxMedia
import app.ryzik.chat.platform.VideoSurface
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.TargetDataLine
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

    /** Текущий проигрыватель JavaFX (для квадратиков — ещё и картинка). */
    private val _player = MutableStateFlow<javafx.scene.media.MediaPlayer?>(null)
    val player: StateFlow<javafx.scene.media.MediaPlayer?> = _player.asStateFlow()

    private fun tick() {
        scope.launch {
            while (_playing.value) {
                val p = _player.value ?: break
                FxMedia.run {
                    val pos = p.currentTime.toMillis()
                    val dur = p.totalDuration?.toMillis() ?: 0.0
                    _position.value = pos.toLong()
                    _progress.value = if (dur > 0) (pos / dur).toFloat().coerceIn(0f, 1f) else 0f
                }
                delay(50)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun toggle(context: Context, id: String, file: File) {
        val p = _player.value
        if (_current.value == id && p != null) {
            FxMedia.run { if (p.status == javafx.scene.media.MediaPlayer.Status.PLAYING) p.pause() else p.play() }
            return
        }
        stop()
        _current.value = id
        _progress.value = 0f
        _position.value = 0
        FxMedia.run {
            val mp = runCatching { javafx.scene.media.MediaPlayer(javafx.scene.media.Media(file.toURI().toString())) }.getOrNull()
            if (mp == null) {
                _current.value = null
                app.ryzik.chat.platform.Platform.toast("Не удалось воспроизвести этот файл")
                return@run
            }
            mp.rate = _speed.value.toDouble()
            mp.setOnPlaying { _playing.value = true; tick() }
            mp.setOnPaused { _playing.value = false }
            mp.setOnEndOfMedia { scope.launch { if (_player.value === mp) stop() } }
            mp.setOnError { scope.launch { if (_player.value === mp) { stop(); app.ryzik.chat.platform.Platform.toast("Не удалось воспроизвести этот файл") } } }
            _player.value = mp
            mp.play()
        }
    }

    fun seek(id: String, fraction: Float) {
        val p = _player.value ?: return
        if (_current.value != id) return
        FxMedia.run {
            val d = p.totalDuration ?: return@run
            if (d.toMillis() > 0) p.seek(d.multiply(fraction.toDouble()))
        }
        _progress.value = fraction
    }

    fun cycleSpeed() {
        val next = when (_speed.value) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
        _speed.value = next
        _player.value?.let { p -> FxMedia.run { p.rate = next.toDouble() } }
    }

    fun stop() {
        val p = _player.value
        _player.value = null
        if (p != null) FxMedia.run { p.stop(); p.dispose() }
        _current.value = null
        _playing.value = false
        _progress.value = 0f
        _position.value = 0
    }
}

// ===================== Запись голосовых =====================

class VoiceRecording(val file: File, val durationMs: Long, val waveform: String)

/** Запись голосового с микрофона компьютера в WAV (16 кГц, моно). Его проигрывают и телефоны, и ПК. */
class VoiceRecorder(@Suppress("UNUSED_PARAMETER") context: Context) {
    private var line: TargetDataLine? = null
    private var file: File? = null
    private var thread: Thread? = null
    private var startedAt = 0L
    private val amps = ArrayList<Int>()
    @Volatile private var lastLevel = 0
    val isRecording get() = line != null

    fun start(): Boolean {
        val f = RyzikApp.instance.repo.recordingFile("wav")
        val format = AudioFormat(16000f, 16, 1, true, false)
        return try {
            val l = AudioSystem.getTargetDataLine(format)
            l.open(format)
            l.start()
            line = l
            file = f
            startedAt = System.currentTimeMillis()
            amps.clear()
            thread = Thread {
                val levelStream = object : java.io.InputStream() {
                    private val one = ByteArray(1)
                    override fun read(): Int = if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        val n = l.read(b, off, len - len % 2)
                        if (n <= 0) return -1
                        var peak = 0
                        var i = off
                        while (i + 1 < off + n) {
                            val v = (b[i].toInt() and 0xff) or (b[i + 1].toInt() shl 8)
                            peak = max(peak, kotlin.math.abs(v.toShort().toInt()))
                            i += 2
                        }
                        lastLevel = peak
                        return n
                    }
                }
                runCatching { AudioSystem.write(AudioInputStream(levelStream, format, AudioSystem.NOT_SPECIFIED.toLong()), AudioFileFormat.Type.WAVE, f) }
            }.apply { isDaemon = true; start() }
            true
        } catch (e: Exception) {
            line?.close()
            line = null
            f.delete()
            false
        }
    }

    /** Громкость 0..1 прямо сейчас — для анимации. */
    fun level(): Float {
        val a = lastLevel
        if (line != null) amps += a
        return (a / 20000f).coerceIn(0f, 1f)
    }

    fun elapsed() = if (line != null) System.currentTimeMillis() - startedAt else 0L

    private fun close() {
        val l = line ?: return
        line = null
        runCatching { l.stop(); l.close() }
        thread?.join(2000)
        thread = null
    }

    fun stop(): VoiceRecording? {
        if (line == null) return null
        val f = file!!
        val duration = elapsed()
        close()
        if (duration < 700 || !f.exists() || f.length() < 1000) { f.delete(); return null }
        return VoiceRecording(f, duration, encodeWaveform(amps))
    }

    fun cancel() {
        if (line == null) return
        close()
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

/** На ПК квадратики не записываются (только смотрятся): запись есть в приложении для телефона. */
class SquareRecorder(@Suppress("UNUSED_PARAMETER") context: Context) {
    val active = false
    var onReady: ((File, Long) -> Unit)? = null
    fun begin() {}
    fun elapsed() = 0L
    fun finish(@Suppress("UNUSED_PARAMETER") send: Boolean) {}
}

/** Размеры видео: на ПК не определяем. */
@Suppress("UNUSED_PARAMETER")
fun videoSize(file: File): Pair<Int, Int> = 0 to 0

@Suppress("UNUSED_PARAMETER")
@Composable
fun SquareRecordingOverlay(recorder: SquareRecorder, limitMs: Long, cancelProgress: Float) {}

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
    val fxPlayer by InlinePlayer.player.collectAsState()
    val size = SQUARE_SIZE_DP.dp
    val scale by animateFloatAsState(if (isCurrent) 1.08f else 1f, label = "grow")

    Box(
        Modifier.size(size * scale + 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size * scale).clip(SquareShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            if (isCurrent && fxPlayer != null) VideoSurface(fxPlayer!!, Modifier.fillMaxSize(), cover = true)
            else if (local != null) app.ryzik.chat.platform.VideoThumbnail(local, Modifier.fillMaxSize())
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
