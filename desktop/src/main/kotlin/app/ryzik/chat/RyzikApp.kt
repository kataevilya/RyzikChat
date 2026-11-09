package app.ryzik.chat

import app.ryzik.chat.data.ChatRepository
import app.ryzik.chat.data.DeviceId
import app.ryzik.chat.data.Notice
import app.ryzik.chat.data.Prefs
import app.ryzik.chat.notify.Notifier
import app.ryzik.chat.platform.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** Приложение на ПК: настройки, репозиторий чатов и уведомления. */
class RyzikApp private constructor() {
    val prefs = Prefs()
    val repo: ChatRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Окно на переднем плане: тогда уведомления об открытом чате не показываем. */
    @Volatile var windowFocused = true

    init {
        instance = this
        DeviceId.value = deviceId()
        repo = ChatRepository(prefs)

        // Новые сообщения
        scope.launch {
            repo.incoming.collect { (chat, msg) ->
                val s = prefs.settings.first()
                if (!s.notifications || chat.muted || msg.type == "call") return@collect
                if ((chat.type == "group" || chat.type == "channel") && !s.groupNotifications) return@collect
                if (windowFocused && repo.openChatId == chat.id) return@collect
                if (windowFocused && !s.inAppNotifications) return@collect
                val sender = repo.users.value[msg.senderId]?.displayName
                    ?: runCatching { repo.loadUser(msg.senderId).displayName }.getOrNull()
                    ?: "Новое сообщение"
                val text = if (s.notificationPreview) {
                    msg.content?.let { repo.previewText(msg.type, it).ifBlank { "Сообщение" } } ?: "Сообщение"
                } else "Новое сообщение"
                Notifier.showMessage(
                    chatId = chat.id,
                    chatTitle = repo.chatTitle(chat),
                    isGroup = chat.type == "group" || chat.type == "channel",
                    senderName = if (chat.type == "channel") repo.chatTitle(chat) else sender,
                    text = text,
                )
            }
        }

        // Реакции и добавление в группы
        scope.launch {
            repo.notices.collect { n ->
                val s = prefs.settings.first()
                if (!s.notifications) return@collect
                when (n) {
                    is Notice.AddedToChat -> {
                        val who = n.by?.displayName ?: "Кто-то"
                        val title = n.chat.title
                        val text = if (n.chat.type == "channel") "$who добавил вас в канал «$title»" else "$who добавил вас в группу «$title»"
                        Notifier.showEvent(title, text, n.chat.id)
                    }
                    is Notice.ReactionOnMine -> {
                        if (!s.reactionNotifications || n.chat.muted) return@collect
                        if (windowFocused && repo.openChatId == n.chat.id) return@collect
                        val who = n.user?.displayName ?: "Кто-то"
                        val what = n.preview.takeIf { it.isNotBlank() }?.let { " на «${it.take(60)}»" } ?: " на ваше сообщение"
                        Notifier.showEvent(who, "${n.emoji} Реакция$what", n.chat.id)
                    }
                }
            }
        }
    }

    /** Постоянный ID этого компьютера (как ANDROID_ID на телефоне). */
    private fun deviceId(): String {
        val f = File(Platform.dataDir, "device.id")
        f.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.length >= 16 }?.let { return it }
        val id = "pc-" + UUID.randomUUID().toString().replace("-", "")
        runCatching { f.writeText(id) }
        return id
    }

    companion object {
        lateinit var instance: RyzikApp
            private set

        fun start() = RyzikApp()
    }
}
