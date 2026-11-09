// В пакете androidx.emoji2.text, потому что MetadataListReader виден только внутри него.
package androidx.emoji2.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

/** Проверяет, что собранный шрифт эмодзи читается так же, как его прочитает EmojiCompat. */
class EmojiFontTest {
    @Test
    fun metadataIsReadable() {
        val font = File("src/main/assets/emoji/ryzik_emoji.ttf")
        assumeTrue("шрифт эмодзи не собран", font.exists())
        val list = MetadataListReader.read(ByteBuffer.wrap(font.readBytes()))
        assertTrue("слишком мало эмодзи: ${list.listLength()}", list.listLength() > 1000)
        val byCodepoints = HashMap<List<Int>, Int>()
        for (i in 0 until list.listLength()) {
            val item = list.list(i)!!
            assertEquals(0xF0001 + i, item.id())
            assertEquals(128, item.width().toInt())
            assertEquals(128, item.height().toInt())
            byCodepoints[(0 until item.codepointsLength()).map { item.codepoints(it) }] = item.id()
        }
        assertTrue("нет 😂", byCodepoints.containsKey(listOf(0x1F602)))
        assertTrue("нет 👍🏽", byCodepoints.containsKey(listOf(0x1F44D, 0x1F3FD)))
        assertTrue("нет ❤", byCodepoints.containsKey(listOf(0x2764)))
    }
}
