// В пакете androidx.emoji2.text, потому что конструктор EmojiSpan виден только внутри него.
package androidx.emoji2.text

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.google.flatbuffers.FlatBufferBuilder
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Эмодзи из шрифта, который пользователь загрузил сам (например, iOS-эмодзи в формате
 * NotoColorEmoji.ttf для Android). В таком шрифте нет метаданных EmojiCompat, поэтому мы
 * собираем их на лету: проверяем, какие эмодзи шрифт умеет рисовать, и рисуем каждое
 * целой строкой этим шрифтом.
 */
object CustomFontEmoji {
    private const val FIRST_ID = 0xF0001
    private const val FE0F = 0xFE0F

    class Setup(val repo: MetadataRepo, val spanFactory: EmojiCompat.SpanFactory, val count: Int)

    /** sequences — все известные эмодзи (с FE0F), как их пишет Unicode. */
    fun build(typeface: Typeface, sequences: List<IntArray>): Setup {
        val paint = Paint().apply { this.typeface = typeface; textSize = 128f }
        val texts = HashMap<List<Int>, String>()
        val entries = ArrayList<Pair<IntArray, Boolean>>()
        for (seq in sequences) {
            val text = String(seq, 0, seq.size)
            if (!paint.hasGlyph(text)) continue
            val key = seq.filter { it != FE0F }
            if (key.isEmpty() || texts.containsKey(key)) continue
            texts[key] = text
            // Символ, который по умолчанию текстовый (например ❤ = 2764 FE0F), помечаем emojiStyle=false.
            val emojiStyle = !(key.size == 1 && seq.contains(FE0F))
            entries += key.toIntArray() to emojiStyle
        }
        require(entries.isNotEmpty()) { "В этом шрифте нет эмодзи" }

        val fm = paint.fontMetrics
        val width = paint.measureText(texts.values.first()).coerceIn(1f, 1000f).toInt()
        val height = (fm.descent - fm.ascent).coerceIn(1f, 1000f).toInt()
        val metadata = metadataList(entries, width, height)
        val repo = MetadataRepo.create(typeface, fakeFontWithMeta(metadata))
        val factory = EmojiCompat.SpanFactory { r ->
            val key = (0 until r.codepointsLength).map { r.getCodepointAt(it) }
            SequenceEmojiSpan(r, texts[key] ?: String(key.toIntArray(), 0, key.size))
        }
        return Setup(repo, factory, entries.size)
    }

    /** FlatBuffer MetadataList — тот же формат, что пишет tools/emoji/build_font.py. */
    private fun metadataList(entries: List<Pair<IntArray, Boolean>>, width: Int, height: Int): ByteBuffer {
        val b = FlatBufferBuilder(entries.size * 48 + 1024)
        val items = IntArray(entries.size)
        entries.forEachIndexed { i, (cps, style) ->
            b.startVector(4, cps.size, 4)
            for (j in cps.indices.reversed()) b.addInt(cps[j])
            val vec = b.endVector()
            b.startTable(7)
            b.addInt(0, FIRST_ID + i, 0)            // id
            b.addBoolean(1, style, false)           // emojiStyle
            b.addShort(2, 0, 0)                     // sdkAdded
            b.addShort(3, 0, 0)                     // compatAdded
            b.addShort(4, width.toShort(), 0)       // width
            b.addShort(5, height.toShort(), 0)      // height
            b.addOffset(6, vec, 0)                  // codepoints
            items[i] = b.endTable()
        }
        val sha = b.createString("user-emoji-font")
        b.startVector(4, items.size, 4)
        for (j in items.indices.reversed()) b.addOffset(items[j])
        val list = b.endVector()
        b.startTable(3)
        b.addInt(0, 1, 0)                           // version
        b.addOffset(1, list, 0)                     // list
        b.addOffset(2, sha, 0)                      // sourceSha
        b.finish(b.endTable())
        return b.dataBuffer()
    }

    /** Минимальный «шрифт» из одной таблицы meta: его читает MetadataRepo, сами глифы берутся из typeface. */
    private fun fakeFontWithMeta(emji: ByteBuffer): ByteBuffer {
        val data = ByteArray(emji.remaining()).also { emji.duplicate().get(it) }
        val metaHeader = 16 + 12
        val metaOffset = 12 + 16
        val out = ByteBuffer.allocate(metaOffset + metaHeader + data.size).order(ByteOrder.BIG_ENDIAN)
        // Заголовок OpenType: версия, 1 таблица, поля поиска
        out.putInt(0x00010000).putShort(1).putShort(16).putShort(0).putShort(0)
        // Запись таблицы meta
        out.put("meta".toByteArray(Charsets.US_ASCII)).putInt(0).putInt(metaOffset).putInt(metaHeader + data.size)
        // Таблица meta: version, flags, reserved, dataMapsCount, затем Emji → данные
        out.putInt(1).putInt(0).putInt(0).putInt(1)
        out.put("Emji".toByteArray(Charsets.US_ASCII)).putInt(metaHeader).putInt(data.size)
        out.put(data)
        out.flip()
        return out
    }
}

/** Рисует эмодзи целой последовательностью символов шрифтом пользователя. */
internal class SequenceEmojiSpan(rasterizer: TypefaceEmojiRasterizer, private val text: String) : EmojiSpan(rasterizer) {
    private var spanWidth = 0

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
        super.getSize(paint, text, start, end, fm).also { spanWidth = it }

    override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val line = paint.fontMetrics
        val lineHeight = line.descent - line.ascent
        val oldTypeface = paint.typeface
        val oldSize = paint.textSize
        paint.typeface = typefaceRasterizer.typeface
        val own = paint.fontMetrics
        val ownHeight = own.descent - own.ascent
        if (ownHeight > 0f) paint.textSize = oldSize * lineHeight / ownHeight
        val scaled = paint.fontMetrics
        val advance = paint.measureText(this.text)
        val dx = if (spanWidth > 0) (spanWidth - advance) / 2f else 0f
        canvas.drawText(this.text, x + dx, y + line.ascent - scaled.ascent, paint)
        paint.typeface = oldTypeface
        paint.textSize = oldSize
    }
}

/** Обычный span EmojiCompat — для встроенного шрифта, если свой не загрузился. */
object TypefaceEmojiSpanCompat {
    fun create(rasterizer: TypefaceEmojiRasterizer): EmojiSpan = TypefaceEmojiSpan(rasterizer)
}
