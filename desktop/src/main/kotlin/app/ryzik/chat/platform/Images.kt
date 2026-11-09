package app.ryzik.chat.platform

import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File

/** Работа с картинками на ПК через Skia (вместо Bitmap на Android). */
object Images {
    enum class Format { Jpeg, Webp, Png }

    private val mimes = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "webp" to "image/webp", "gif" to "image/gif",
        "bmp" to "image/bmp", "heic" to "image/heic", "svg" to "image/svg+xml",
        "mp4" to "video/mp4", "m4v" to "video/mp4", "mov" to "video/quicktime", "webm" to "video/webm", "mkv" to "video/x-matroska", "avi" to "video/x-msvideo",
        "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "ogg" to "audio/ogg", "wav" to "audio/wav", "opus" to "audio/ogg",
        "pdf" to "application/pdf", "zip" to "application/zip", "txt" to "text/plain", "apk" to "application/vnd.android.package-archive",
        "doc" to "application/msword", "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls" to "application/vnd.ms-excel", "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    )

    fun mimeOf(name: String, fallback: String = "application/octet-stream"): String =
        mimes[name.substringAfterLast('.', "").lowercase()] ?: fallback

    fun extOf(mime: String): String = mimes.entries.firstOrNull { it.value == mime }?.key ?: "bin"

    fun size(file: File): Pair<Int, Int>? = runCatching {
        val codec = Codec.makeFromData(Data.makeFromBytes(file.readBytes()))
        codec.width to codec.height
    }.getOrNull()

    /** Уменьшает по длинной стороне до maxSide; square — вписывает в прозрачный квадрат (стикеры). */
    fun scaled(bytes: ByteArray, maxSide: Int, square: Boolean, format: Format): ByteArray? = runCatching {
        val src = Image.makeFromEncoded(bytes)
        val k = minOf(if (square) Float.MAX_VALUE else 1f, maxSide.toFloat() / maxOf(src.width, src.height))
        val w = (src.width * k).toInt().coerceAtLeast(1)
        val h = (src.height * k).toInt().coerceAtLeast(1)
        val surface = if (square) Surface.makeRasterN32Premul(maxSide, maxSide) else Surface.makeRasterN32Premul(w, h)
        val dst = if (square) Rect.makeXYWH(((maxSide - w) / 2).toFloat(), ((maxSide - h) / 2).toFloat(), w.toFloat(), h.toFloat())
        else Rect.makeWH(w.toFloat(), h.toFloat())
        surface.canvas.drawImageRect(src, Rect.makeWH(src.width.toFloat(), src.height.toFloat()), dst, FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR), null, true)
        val img = surface.makeImageSnapshot()
        val data = when (format) {
            Format.Webp -> img.encodeToData(EncodedImageFormat.WEBP, 90)
            Format.Jpeg -> img.encodeToData(EncodedImageFormat.JPEG, 88)
            Format.Png -> img.encodeToData(EncodedImageFormat.PNG)
        } ?: error("encode")
        data.bytes
    }.getOrNull()
}

/** Как android.graphics.Color.parseColor: #RRGGBB или #AARRGGBB. */
fun parseColorInt(hex: String): Int {
    val h = hex.trim().removePrefix("#")
    return when (h.length) {
        6 -> (0xFF000000L or h.toLong(16)).toInt()
        8 -> h.toLong(16).toInt()
        else -> throw IllegalArgumentException("Unknown color")
    }
}
