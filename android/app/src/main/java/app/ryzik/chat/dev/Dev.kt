package app.ryzik.chat.dev

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

/** Одна запись журнала. */
data class DevEntry(val time: Long, val tag: String, val text: String)

/**
 * Короткий журнал событий приложения: связь, отправка, ключи шифрования.
 * Хранится только в памяти процесса (последние 300 записей) и никуда не отправляется.
 */
object DevLog {
    private const val MAX = 300
    private val items = ConcurrentLinkedDeque<DevEntry>()

    fun log(tag: String, text: String) {
        items.addFirst(DevEntry(System.currentTimeMillis(), tag, text))
        while (items.size > MAX) items.pollLast()
    }

    fun snapshot(): List<DevEntry> = items.toList()

    fun clear() = items.clear()

    fun time(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date(ms))

    fun asText(): String = snapshot().joinToString("\n") { "${time(it.time)}  ${it.tag}  ${it.text}" }
}

/** Флаги отладки: живут до перезапуска приложения, чтобы не портить обычный интерфейс. */
object DevFlags {
    var fpsOverlay by mutableStateOf(false)
    var layoutGrid by mutableStateOf(false)
}
