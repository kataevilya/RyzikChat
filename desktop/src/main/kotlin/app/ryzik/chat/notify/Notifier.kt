package app.ryzik.chat.notify

import java.awt.SystemTray
import java.awt.TrayIcon

/**
 * Уведомления на ПК: всплывающее сообщение из значка в трее (в Windows — обычное уведомление системы).
 * Нажатие на уведомление открывает чат.
 */
object Notifier {
    @Volatile var tray: TrayIcon? = null
    /** Что открыть по нажатию на последнее уведомление. */
    @Volatile var lastChatId: String? = null
    /** Вызывается из главного окна: показать окно и открыть чат. */
    @Volatile var onOpenChat: (String?) -> Unit = {}

    val supported: Boolean get() = runCatching { SystemTray.isSupported() }.getOrDefault(false)

    fun showMessage(
        chatId: String,
        chatTitle: String,
        isGroup: Boolean,
        senderName: String,
        text: String,
    ) {
        val title = if (isGroup && senderName != chatTitle) "$chatTitle · $senderName" else chatTitle
        show(title, text, chatId)
    }

    fun showEvent(title: String, text: String, chatId: String?) = show(title, text, chatId)

    private fun show(title: String, text: String, chatId: String?) {
        val t = tray ?: return
        lastChatId = chatId
        runCatching { t.displayMessage(title.take(64), text.take(240), TrayIcon.MessageType.NONE) }
    }

    fun clearChat(@Suppress("UNUSED_PARAMETER") chatId: String) {}
}
