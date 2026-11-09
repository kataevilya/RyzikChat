package app.ryzik.chat.platform

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import javafx.animation.AnimationTimer
import javafx.application.Platform as FxPlatform
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.image.PixelFormat
import javafx.scene.image.WritableImage
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.withPermit

/** Звук и видео на ПК через JavaFX Media: играет MP4 (H.264/AAC), M4A, MP3 и WAV. */
object FxMedia {
    @Volatile private var started = false

    @Synchronized
    private fun ensure() {
        if (started) return
        val latch = CountDownLatch(1)
        runCatching { FxPlatform.startup { latch.countDown() } }.onFailure { latch.countDown() }
        latch.await(10, TimeUnit.SECONDS)
        FxPlatform.setImplicitExit(false)
        started = true
    }

    @Volatile private var ready = false
    private val starter = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "fx-start").apply { isDaemon = true } }

    /** Выполнить на потоке JavaFX. */
    fun run(block: () -> Unit) {
        if (ready) {
            if (FxPlatform.isFxApplicationThread()) block() else FxPlatform.runLater(block)
            return
        }
        // Первый запуск JavaFX занимает до секунды: делаем его в фоне, а не в потоке окна.
        // Через одну очередь, чтобы задачи выполнились в том порядке, в каком пришли.
        starter.execute {
            ensure()
            FxPlatform.runLater(block)
            ready = true
        }
    }
}

/**
 * Картинка видео из проигрывателя JavaFX, нарисованная прямо в Compose:
 * каждый кадр снимается с MediaView и превращается в ImageBitmap.
 * Так видео можно скруглять и класть под кнопки, как на телефоне.
 */
@Composable
fun VideoSurface(player: MediaPlayer, modifier: Modifier = Modifier, cover: Boolean = false) {
    var frame by remember(player) { mutableStateOf<ImageBitmap?>(null) }
    DisposableEffect(player) {
        var timer: AnimationTimer? = null
        FxMedia.run {
            val view = MediaView(player).apply { isPreserveRatio = true }
            Scene(Group(view))
            var buf = ByteArray(0)
            var img: WritableImage? = null
            var last = 0L
            var shotAt = -1.0
            timer = object : AnimationTimer() {
                override fun handle(now: Long) {
                    if (now - last < 30_000_000L) return
                    last = now
                    // На паузе кадр не меняется — не снимаем его заново, чтобы не грузить процессор.
                    val t = player.currentTime?.toMillis() ?: 0.0
                    if (t == shotAt) return
                    shotAt = t
                    val w = player.media?.width ?: 0
                    val h = player.media?.height ?: 0
                    if (w <= 0 || h <= 0) return
                    view.fitWidth = w.toDouble(); view.fitHeight = h.toDouble()
                    if (img == null || img!!.width.toInt() != w || img!!.height.toInt() != h) img = WritableImage(w, h)
                    val snap = view.snapshot(null, img)
                    val sw = snap.width.toInt(); val sh = snap.height.toInt()
                    if (buf.size != sw * sh * 4) buf = ByteArray(sw * sh * 4)
                    snap.pixelReader.getPixels(0, 0, sw, sh, PixelFormat.getByteBgraPreInstance(), buf, 0, sw * 4)
                    val sk = org.jetbrains.skia.Image.makeRaster(ImageInfo(sw, sh, ColorType.BGRA_8888, ColorAlphaType.PREMUL), buf.copyOf(), sw * 4)
                    frame = sk.toComposeImageBitmap()
                }
            }.also { it.start() }
        }
        onDispose { FxMedia.run { timer?.stop() } }
    }
    val f = frame
    if (f != null) Image(f, null, modifier, contentScale = if (cover) ContentScale.Crop else ContentScale.Fit)
    else Box(modifier)
}

/** Первый кадр видео (для превью в ленте). Снимается один раз и хранится в папке кэша. */
object VideoThumbs {
    private val dir by lazy { java.io.File(Platform.cacheDir, "thumbs").apply { mkdirs() } }
    private val memory = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()
    private val gate = kotlinx.coroutines.sync.Semaphore(2)

    fun cached(file: java.io.File): ImageBitmap? = memory[file.absolutePath]

    // Чтение и разбор картинки — не в потоке окна.
    suspend fun load(file: java.io.File): ImageBitmap? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { loadNow(file) }

    private suspend fun loadNow(file: java.io.File): ImageBitmap? {
        memory[file.absolutePath]?.let { return it }
        val png = java.io.File(dir, Integer.toHexString(file.absolutePath.hashCode()) + "_" + file.length() + ".png")
        if (png.exists()) {
            runCatching { org.jetbrains.skia.Image.makeFromEncoded(png.readBytes()).toComposeImageBitmap() }.getOrNull()?.let {
                memory[file.absolutePath] = it; return it
            }
        }
        val bytes = gate.withPermit { grab(file) } ?: return null
        runCatching { png.writeBytes(bytes) }
        return runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()?.also { memory[file.absolutePath] = it }
    }

    private suspend fun grab(file: java.io.File): ByteArray? = kotlinx.coroutines.withTimeoutOrNull(8000) {
        val result = kotlinx.coroutines.CompletableDeferred<ByteArray?>()
        var player: MediaPlayer? = null
        var timer: AnimationTimer? = null
        FxMedia.run {
            runCatching {
                val mp = MediaPlayer(javafx.scene.media.Media(file.toURI().toString()))
                player = mp
                mp.isMute = true
                val view = MediaView(mp).apply { isPreserveRatio = true }
                Scene(Group(view))
                mp.setOnError { result.complete(null) }
                mp.setOnReady { mp.play() }
                var frames = 0
                timer = object : AnimationTimer() {
                    override fun handle(now: Long) {
                        if (mp.status != MediaPlayer.Status.PLAYING) return
                        val w = mp.media.width; val h = mp.media.height
                        if (w <= 0 || h <= 0 || mp.currentTime.toMillis() < 80 || ++frames < 3) return
                        stop()
                        mp.pause()
                        val k = minOf(1.0, 480.0 / maxOf(w, h))
                        view.fitWidth = w * k; view.fitHeight = h * k
                        val snap = view.snapshot(null, null)
                        val sw = snap.width.toInt(); val sh = snap.height.toInt()
                        val buf = ByteArray(sw * sh * 4)
                        snap.pixelReader.getPixels(0, 0, sw, sh, PixelFormat.getByteBgraPreInstance(), buf, 0, sw * 4)
                        val sk = org.jetbrains.skia.Image.makeRaster(ImageInfo(sw, sh, ColorType.BGRA_8888, ColorAlphaType.PREMUL), buf, sw * 4)
                        result.complete(sk.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)?.bytes)
                    }
                }.also { it.start() }
            }.onFailure { result.complete(null) }
        }
        try { result.await() } finally {
            FxMedia.run { timer?.stop(); player?.stop(); player?.dispose() }
        }
    }
}

@Composable
fun VideoThumbnail(file: java.io.File, modifier: Modifier = Modifier) {
    var bmp by remember(file) { mutableStateOf(VideoThumbs.cached(file)) }
    androidx.compose.runtime.LaunchedEffect(file) { if (bmp == null) bmp = VideoThumbs.load(file) }
    val b = bmp
    if (b != null) Image(b, null, modifier, contentScale = ContentScale.Crop) else Box(modifier)
}
