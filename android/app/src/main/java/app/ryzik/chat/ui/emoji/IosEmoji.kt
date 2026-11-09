package app.ryzik.chat.ui.emoji

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.util.Log
import androidx.emoji2.text.CustomFontEmoji
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.text.MetadataRepo
import java.io.File

/**
 * Эмодзи вместо системных. Два варианта:
 *  - встроенный объёмный набор Fluent Emoji (MIT) из assets/emoji, собирается на CI скриптом tools/emoji/build_font.py;
 *  - свой шрифт, загруженный с телефона (например, iOS-эмодзи в формате NotoColorEmoji.ttf).
 * Compose сам подхватывает EmojiCompat, поэтому эмодзи меняются во всех текстах: сообщениях, реакциях, поле ввода.
 */
object IosEmoji {
    const val ASSET = "emoji/ryzik_emoji.ttf"
    private const val SEQUENCES = "emoji_sequences.txt"
    private const val CUSTOM = "custom_emoji.ttf"

    fun customFile(context: Context) = File(context.applicationContext.filesDir, CUSTOM)

    fun hasCustom(context: Context) = customFile(context).let { it.exists() && it.length() > 0 }

    /** Шрифт пользователя для превью в настройках. */
    fun customTypeface(context: Context): Typeface? =
        if (hasCustom(context)) runCatching { Typeface.createFromFile(customFile(context)) }.getOrNull() else null

    /**
     * Копирует выбранный файл шрифта к себе и проверяет, что в нём есть эмодзи.
     * Возвращает текст ошибки или null, если всё хорошо. Вызывать не в главном потоке.
     */
    fun importCustom(context: Context, uri: Uri): String? {
        val tmp = File(context.cacheDir, "emoji_import.ttf")
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: return "Не удалось открыть файл"
            if (tmp.length() < 1024) return "Это не файл шрифта"
            val tf = runCatching { Typeface.createFromFile(tmp) }.getOrNull() ?: return "Это не файл шрифта .ttf"
            val paint = Paint().apply { typeface = tf }
            val samples = listOf("😀", "👍", "🔥", "😂")
            if (samples.none { paint.hasGlyph(it) }) return "В этом шрифте нет эмодзи. Нужен цветной шрифт эмодзи для Android (.ttf)"
            if (!tmp.copyTo(customFile(context), overwrite = true).exists()) return "Не удалось сохранить шрифт"
            return null
        } catch (t: Throwable) {
            return t.message ?: "Не удалось загрузить шрифт"
        } finally {
            tmp.delete()
        }
    }

    fun removeCustom(context: Context) {
        customFile(context).delete()
    }

    private fun sequences(context: Context): List<IntArray> =
        context.assets.open(SEQUENCES).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.map { line -> line.trim().split(' ').map { it.toInt(16) }.toIntArray() }.toList()
        }

    fun install(context: Context) {
        if (EmojiCompat.isConfigured()) return
        val app = context.applicationContext
        val custom = customFile(app).takeIf { hasCustom(app) }
        var spanFactory: EmojiCompat.SpanFactory? = null
        val loader = EmojiCompat.MetadataRepoLoader { callback ->
            Thread({
                try {
                    if (custom != null) {
                        val setup = runCatching { CustomFontEmoji.build(Typeface.createFromFile(custom), sequences(app)) }
                            .onFailure { Log.w("IosEmoji", "Свой шрифт эмодзи не подошёл, беру встроенный", it) }
                            .getOrNull()
                        if (setup != null) {
                            spanFactory = setup.spanFactory
                            callback.onLoaded(setup.repo)
                            return@Thread
                        }
                    }
                    callback.onLoaded(MetadataRepo.create(app.assets, ASSET))
                } catch (t: Throwable) {
                    Log.w("IosEmoji", "Шрифт эмодзи не загрузился, остаются системные", t)
                    callback.onFailed(t)
                }
            }, "emoji-loader").start()
        }
        val config = object : EmojiCompat.Config(loader) {}
            .setReplaceAll(true)
            .setUseEmojiAsDefaultStyle(true)
        // Для своего шрифта эмодзи рисуются целой строкой (см. CustomFontEmoji), для встроенного — как обычно.
        if (custom != null) config.setSpanFactory { r -> spanFactory?.createSpan(r) ?: androidx.emoji2.text.TypefaceEmojiSpanCompat.create(r) }
        EmojiCompat.init(config)
    }
}
