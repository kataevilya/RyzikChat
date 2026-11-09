package app.ryzik.chat.notify

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.crypto.E2E
import app.ryzik.chat.crypto.KeyVault
import app.ryzik.chat.data.ApiClient
import app.ryzik.chat.data.AppJson
import app.ryzik.chat.data.Chat
import app.ryzik.chat.data.Content
import app.ryzik.chat.data.Message
import app.ryzik.chat.data.RealtimeEvent
import app.ryzik.chat.data.SavedAccount
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap

/**
 * Держит соединение для неактивного аккаунта, чтобы его сообщения и звонки
 * тоже приходили уведомлениями. Ответить на звонок — значит переключиться на этот аккаунт.
 */
class AccountWatcher(private val context: Context, val account: SavedAccount) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api = ApiClient().apply {
        baseUrl = account.serverUrl
        token = account.token
    }
    private val privateKey: ByteArray? = runCatching { KeyVault.unwrap(account.wrappedPrivateKey) }.getOrNull()
    private val chats = ConcurrentHashMap<String, Chat>()
    private val names = ConcurrentHashMap<String, String>()
    private val tag = account.userId to account.username
    private var socket: WebSocket? = null
    private var job: Job? = null

    /** Входящий звонок, который ещё звонит: id звонка, кто звонит, видео ли. */
    @Volatile private var ringing: Triple<String, String, Boolean>? = null
    private var ringTimeout: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            var backoff = 1000L
            while (true) {
                val closed = CompletableDeferred<Int>()
                // active=0: соединение только для уведомлений, «в сети» из-за него не показываем.
                val wsUrl = api.baseUrl.replaceFirst("http", "ws") + "/ws?token=" + api.token + "&active=0"
                val ws = api.http.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) { backoff = 1000L }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val ev = runCatching { AppJson.decodeFromString(RealtimeEvent.serializer(), text) }.getOrNull() ?: return
                        scope.launch { runCatching { handle(ev) } }
                    }
                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                        closed.complete(code)
                    }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { closed.complete(code) }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) { closed.complete(-1) }
                })
                socket = ws
                val code = try { closed.await() } finally { ws.cancel() }
                if (code == 4001) break // сессия этого аккаунта завершена
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000)
            }
        }
    }

    fun stop() {
        socket?.close(1000, null)
        scope.cancel()
        if (ringing != null) Notifier.cancelCall(context)
        ringing = null
    }

    fun declineCall() {
        val r = ringing ?: return
        ringing = null
        ringTimeout?.cancel()
        Notifier.cancelCall(context)
        val msg = buildJsonObject {
            put("type", "call.signal")
            put("to", r.second)
            put("data", buildJsonObject { put("kind", "decline"); put("callId", r.first) })
        }
        socket?.send(msg.toString())
    }

    private fun foreground() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private suspend fun chat(id: String, fresh: Boolean = false): Chat? =
        (if (fresh) null else chats[id]) ?: runCatching { api.chat(id) }.getOrNull()?.also { chats[id] = it }

    private suspend fun name(userId: String): String =
        names[userId] ?: runCatching { api.user(userId).displayName }.getOrNull()?.also { names[userId] = it } ?: "Кто-то"

    private fun title(c: Chat): String = when (c.type) {
        "saved" -> "Избранное"
        "direct" -> c.members.firstOrNull { it.user.id != account.userId }?.user?.displayName ?: "Чат"
        else -> c.title
    }

    private fun decrypt(m: Message): Content? = runCatching {
        val payload = m.payload ?: return null
        val obj = AppJson.parseToJsonElement(payload).jsonObject
        if (obj["v"]?.jsonPrimitive?.intOrNull == 0) AppJson.decodeFromJsonElement(Content.serializer(), obj["plain"]!!)
        else AppJson.decodeFromString(Content.serializer(), E2E.decrypt(payload, account.userId, privateKey!!))
    }.getOrNull()

    private suspend fun handle(ev: RealtimeEvent) {
        val s = RyzikApp.instance.prefs.settings.first()
        when (ev.type) {
            "message.new" -> {
                val m = ev.message ?: return
                if (m.senderId == account.userId || m.deleted || m.type == "call") return
                if (!s.notifications) return
                if (foreground() && !s.inAppNotifications) return
                val c = chat(m.chatId) ?: return
                if (c.muted) return
                val isGroup = c.type == "group" || c.type == "channel"
                if (isGroup && !s.groupNotifications) return
                val sender = if (c.type == "channel") title(c) else name(m.senderId)
                val text = if (s.notificationPreview) {
                    decrypt(m)?.let { RyzikApp.instance.repo.previewText(m.type, it).ifBlank { "Сообщение" } } ?: "Сообщение"
                } else "Новое сообщение"
                Notifier.showMessage(
                    context, chatId = c.id, chatTitle = title(c), isGroup = isGroup,
                    senderId = m.senderId, senderName = sender, text = text, time = m.createdAt,
                    vibrate = s.vibrate, canReply = false, account = tag,
                )
            }
            "chat.updated" -> ev.chatId?.let { chat(it, fresh = true) }
            "chat.new" -> {
                val c = ev.chat ?: return
                chats[c.id] = c
                val by = ev.by ?: return
                if (!s.notifications || by == account.userId || (c.type != "group" && c.type != "channel")) return
                val who = name(by)
                val text = if (c.type == "channel") "$who добавил вас в канал «${c.title}»" else "$who добавил вас в группу «${c.title}»"
                Notifier.showEvent(context, "added_${c.id}", c.title, text, c.id, iconName = c.title, account = tag)
            }
            "reaction" -> {
                val chatId = ev.chatId ?: return
                val userId = ev.userId ?: return
                val emoji = ev.emoji ?: return
                if (!s.notifications || !s.reactionNotifications || userId == account.userId) return
                val c = chat(chatId) ?: return
                if (c.muted) return
                val who = name(userId)
                Notifier.showEvent(context, "reaction_${ev.messageId}", who, "$emoji Реакция на ваше сообщение", chatId, iconName = who, account = tag)
            }
            "read" -> if (ev.userId == account.userId) ev.chatId?.let { Notifier.clearChat(context, it) }
            "call.signal" -> {
                val from = ev.from ?: return
                val d = ev.data ?: return
                val kind = d["kind"]?.jsonPrimitive?.contentOrNull ?: return
                val callId = d["callId"]?.jsonPrimitive?.contentOrNull ?: return
                when (kind) {
                    "offer" -> {
                        val video = d["video"]?.jsonPrimitive?.booleanOrNull == true
                        ringing = Triple(callId, from, video)
                        if (s.callNotifications) Notifier.showIncomingCall(context, name(from), video, account = tag)
                        ringTimeout?.cancel()
                        ringTimeout = scope.launch {
                            delay(50_000)
                            if (ringing?.first == callId) missed()
                        }
                    }
                    "hangup" -> if (ringing?.first == callId) missed()
                }
            }
        }
    }

    private suspend fun missed() {
        val r = ringing ?: return
        ringing = null
        ringTimeout?.cancel()
        Notifier.cancelCall(context)
        if (RyzikApp.instance.prefs.settings.first().callNotifications) {
            Notifier.missedCall(context, r.second, name(r.second), r.third, null, account = tag)
        }
    }
}
