package app.ryzik.chat

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import app.ryzik.chat.call.CallManager
import app.ryzik.chat.data.AuthState
import app.ryzik.chat.data.ChatRepository
import app.ryzik.chat.data.DeviceId
import app.ryzik.chat.data.Notice
import app.ryzik.chat.data.Prefs
import app.ryzik.chat.notify.AccountWatcher
import app.ryzik.chat.notify.ConnectionService
import app.ryzik.chat.notify.Notifier
import app.ryzik.chat.ui.emoji.IosEmoji
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class RyzikApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var repo: ChatRepository
        private set
    lateinit var calls: CallManager
        private set

    /** Соединения неактивных аккаунтов: по id пользователя. */
    val watchers = ConcurrentHashMap<String, AccountWatcher>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private fun foreground() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    override fun onCreate() {
        super.onCreate()
        instance = this
        DeviceId.value = runCatching {
            android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        prefs = Prefs(this)
        repo = ChatRepository(this, prefs)
        calls = CallManager(this, repo)
        Notifier.createChannels(this)

        // «В сети» только пока приложение открыто, даже если фоновое соединение работает.
        ProcessLifecycleOwner.get().lifecycle.addObserver(androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> repo.setAppActive(true)
                Lifecycle.Event.ON_STOP -> repo.setAppActive(false)
                else -> {}
            }
        })

        // Эмодзи в стиле iOS (выключение вступает в силу после перезапуска).
        scope.launch {
            prefs.settings.map { it.iosEmoji }.distinctUntilChanged().filter { it }.collect { IosEmoji.install(this@RyzikApp) }
        }

        // Фоновая служба: пока вы вошли и она включена, сообщения и звонки приходят при закрытом приложении.
        scope.launch {
            combine(repo.auth, prefs.settings) { a, s -> a is AuthState.LoggedIn && s.backgroundConnection }
                .distinctUntilChanged()
                .collect { on -> if (on) ConnectionService.start(this@RyzikApp) else ConnectionService.stop(this@RyzikApp) }
        }

        // Уведомления с остальных аккаунтов
        scope.launch {
            combine(prefs.accounts, repo.auth, prefs.settings) { list, a, s ->
                val active = (a as? AuthState.LoggedIn)?.me?.id
                if (active == null || !s.allAccountsNotifications) emptyList()
                else list.filter { it.userId != active && it.token.isNotEmpty() }
            }.distinctUntilChanged().collect { wanted ->
                val ids = wanted.associateBy { it.userId }
                for ((id, w) in watchers.entries.toList()) {
                    val acc = ids[id]
                    if (acc == null || acc.token != w.account.token || acc.serverUrl != w.account.serverUrl) {
                        watchers.remove(id)?.stop()
                    }
                }
                for (acc in wanted) if (!watchers.containsKey(acc.userId)) {
                    watchers[acc.userId] = AccountWatcher(this@RyzikApp, acc).also { it.start() }
                }
            }
        }

        // Новые сообщения
        scope.launch {
            repo.incoming.collect { (chat, msg) ->
                val s = prefs.settings.first()
                if (!s.notifications || chat.muted || msg.type == "call") return@collect
                if ((chat.type == "group" || chat.type == "channel") && !s.groupNotifications) return@collect
                val fg = foreground()
                if (fg && repo.openChatId == chat.id) return@collect
                if (fg && !s.inAppNotifications) return@collect
                val sender = repo.users.value[msg.senderId]?.displayName
                    ?: runCatching { repo.loadUser(msg.senderId).displayName }.getOrNull()
                    ?: "Новое сообщение"
                val text = if (s.notificationPreview) {
                    msg.content?.let { repo.previewText(msg.type, it).ifBlank { "Сообщение" } } ?: "Сообщение"
                } else "Новое сообщение"
                val isGroup = chat.type == "group" || chat.type == "channel"
                Notifier.showMessage(
                    this@RyzikApp,
                    chatId = chat.id,
                    chatTitle = repo.chatTitle(chat),
                    isGroup = isGroup,
                    senderId = msg.senderId,
                    senderName = if (chat.type == "channel") repo.chatTitle(chat) else sender,
                    text = text,
                    time = msg.createdAt,
                    vibrate = s.vibrate,
                    canReply = chat.type != "channel" || chat.myRole == "owner" || chat.myRole == "admin",
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
                        Notifier.showEvent(this@RyzikApp, "added_${n.chat.id}", title, text, n.chat.id, iconName = title)
                    }
                    is Notice.ReactionOnMine -> {
                        if (!s.reactionNotifications || n.chat.muted) return@collect
                        if (foreground() && repo.openChatId == n.chat.id) return@collect
                        val who = n.user?.displayName ?: "Кто-то"
                        val what = n.preview.takeIf { it.isNotBlank() }?.let { " на «${it.take(60)}»" } ?: " на ваше сообщение"
                        Notifier.showEvent(this@RyzikApp, "reaction_${n.messageId}", who, "${n.emoji} Реакция$what", n.chat.id, iconName = who)
                    }
                }
            }
        }
    }

    companion object {
        lateinit var instance: RyzikApp
            private set
    }
}
