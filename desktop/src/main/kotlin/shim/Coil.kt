package coil.compose

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import app.ryzik.chat.data.DeviceId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Загрузка картинок для ПК вместо Coil: по ссылке, из файла; с кэшем в памяти и на диске.
 *
 * Картинка декодируется под размер, в котором её показывают (аватарка 48 dp не держит в памяти фото 3000×2000),
 * уменьшенная копия сохраняется на диск, а декодируют одновременно не больше двух картинок —
 * иначе прокрутка чата с фото занимала все ядра процессора и окно подтормаживало.
 */
object ImageCache {
    // Создаётся при первой загрузке, уже в фоне: сборка клиента (сертификаты) занимает десятки миллисекунд.
    private val http by lazy { OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).addInterceptor(app.ryzik.chat.data.ServerFallback.interceptor).build() }

    /** Размеры, под которые декодируем (по меньшей стороне картинки), в пикселях. */
    private val BUCKETS = intArrayOf(128, 256, 512, 1024, 2048)
    /** Длинная сторона не больше этого даже на экране просмотра. */
    private const val MAX_SIDE = 2560
    // Кэш в памяти ограничен по объёму пикселей, а не по числу картинок.
    private const val MEMORY_BUDGET = 192L * 1024 * 1024
    private var memoryBytes = 0L
    private val memory = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            if (memoryBytes <= MEMORY_BUDGET || eldest == null) return false
            memoryBytes -= bytesOf(eldest.value)
            return true
        }
    }
    private val inFlight = HashMap<String, Deferred<ImageBitmap?>>()
    private val decoder = Dispatchers.IO.limitedParallelism(2)
    private val diskDir by lazy { File(app.ryzik.chat.platform.Platform.cacheDir, "images").apply { mkdirs() } }
    private val thumbDir by lazy { File(app.ryzik.chat.platform.Platform.cacheDir, "thumbs-img").apply { mkdirs() } }

    private fun bytesOf(b: ImageBitmap) = b.width.toLong() * b.height * 4

    fun key(model: Any?): String? = when (model) {
        null -> null
        is String -> model
        is File -> "file:" + model.absolutePath + ":" + model.lastModified()
        is Uri -> model.toString()
        else -> model.toString()
    }

    /** Наименьший размер из BUCKETS, которого хватит для области side×side; 0 — размер неизвестен. */
    fun bucket(side: Int): Int = if (side <= 0) 1024 else BUCKETS.firstOrNull { it >= side } ?: BUCKETS.last()

    /** Уже готовая картинка не меньше нужного размера (bucket = 0 — любая, лучше крупнее). */
    fun cached(key: String, bucket: Int = 0): ImageBitmap? = synchronized(memory) {
        for (i in BUCKETS.indices.reversed()) {
            if (BUCKETS[i] < bucket) break
            memory["$key@${BUCKETS[i]}"]?.let { return it }
        }
        null
    }

    suspend fun load(model: Any?, bucket: Int = 1024): ImageBitmap? {
        val k = key(model) ?: return null
        cached(k, bucket)?.let { return it }
        val id = "$k@$bucket"
        // Одна и та же аватарка в десятке сообщений — грузим её один раз.
        val (job, owner) = synchronized(memory) {
            inFlight[id]?.let { it to false } ?: CompletableDeferred<ImageBitmap?>().also { inFlight[id] = it }.let { it to true }
        }
        if (!owner) return job.await()
        val result = runCatching { loadNow(model!!, k, bucket) }.getOrNull()
        synchronized(memory) {
            inFlight.remove(id)
            if (result != null) {
                memory.put(id, result)?.let { memoryBytes -= bytesOf(it) }
                memoryBytes += bytesOf(result)
            }
        }
        (job as CompletableDeferred).complete(result)
        return result
    }

    private suspend fun loadNow(model: Any, k: String, bucket: Int): ImageBitmap? {
        val thumb = File(thumbDir, sha1(k) + "_" + bucket)
        if (thumb.length() > 0) {
            withContext(decoder) { runCatching { decode(thumb.readBytes(), bucket, null) }.getOrNull() }?.let { return it }
        }
        val bytes = withContext(Dispatchers.IO) {
            when (model) {
                is File -> model.readBytes()
                is Uri -> if (model.scheme == "file") model.toFile().readBytes() else fetch(model.toString())
                is String -> if (model.startsWith("http")) fetch(model) else File(model.removePrefix("file://")).readBytes()
                else -> null
            }
        } ?: return null
        return withContext(decoder) { decode(bytes, bucket, thumb) }
    }

    /**
     * Декодирует и уменьшает так, чтобы картинка покрывала квадрат bucket×bucket (хватит и для Crop).
     * Если уменьшили заметно — кладём копию в thumb, чтобы в следующий раз не разбирать исходник.
     */
    private fun decode(bytes: ByteArray, bucket: Int, thumb: File?): ImageBitmap {
        val img = org.jetbrains.skia.Image.makeFromEncoded(bytes)
        val iw = img.width
        val ih = img.height
        val k = minOf(1f, bucket.toFloat() / minOf(iw, ih), MAX_SIDE.toFloat() / maxOf(iw, ih))
        if (k > 0.85f) return img.toComposeImageBitmap()
        val w = (iw * k).toInt().coerceAtLeast(1)
        val h = (ih * k).toInt().coerceAtLeast(1)
        val surface = Surface.makeRasterN32Premul(w, h)
        // Линейная выборка с мип-картами: в 2–3 раза быстрее Catmull-Rom и без «лесенки» при сильном уменьшении.
        surface.canvas.drawImageRect(
            img, Rect.makeWH(iw.toFloat(), ih.toFloat()), Rect.makeWH(w.toFloat(), h.toFloat()),
            FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR), null, true,
        )
        val small = surface.makeImageSnapshot()
        if (thumb != null) runCatching {
            val data = if (img.imageInfo.isOpaque) small.encodeToData(EncodedImageFormat.JPEG, 90) else small.encodeToData(EncodedImageFormat.PNG)
            data?.bytes?.let { b ->
                val tmp = File(thumb.path + ".tmp")
                tmp.writeBytes(b)
                tmp.renameTo(thumb)
            }
        }
        return small.toComposeImageBitmap()
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun fetch(url: String): ByteArray? {
        val f = File(diskDir, Integer.toHexString(url.hashCode()) + "_" + url.length)
        if (f.exists() && f.length() > 0 && System.currentTimeMillis() - f.lastModified() < 10 * 60_000) return f.readBytes()
        val b = Request.Builder().url(url).header("ngrok-skip-browser-warning", "1")
        if (DeviceId.value.isNotEmpty()) b.header("X-Device-Id", DeviceId.value)
        return http.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) return if (f.exists()) f.readBytes() else null
            r.body!!.bytes().also { runCatching { f.writeBytes(it) } }
        }
    }
}

@Composable
fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
    onSuccess: ((Any) -> Unit)? = null,
    onError: ((Any) -> Unit)? = null,
) {
    val key = ImageCache.key(model)
    // Сколько пикселей занимает картинка на экране: -1 — ещё не разметили.
    var side by remember(key) { mutableIntStateOf(-1) }
    var bmp by remember(key) { mutableStateOf(key?.let { ImageCache.cached(it) }) }
    LaunchedEffect(key, side) {
        if (key == null || model == null || side < 0) return@LaunchedEffect
        val bucket = ImageCache.bucket(side)
        val ready = ImageCache.cached(key, bucket)
        val loaded = ready ?: ImageCache.load(model, bucket)
        if (loaded != null) {
            if (loaded !== bmp) bmp = loaded
            onSuccess?.invoke(Unit)
        } else if (bmp == null) onError?.invoke(Unit)
    }
    val m = modifier.onSizeChanged { side = maxOf(it.width, it.height) }
    val b = bmp
    if (b != null) Image(b, contentDescription, m, alignment, contentScale, alpha, colorFilter)
    else Box(m)
}
