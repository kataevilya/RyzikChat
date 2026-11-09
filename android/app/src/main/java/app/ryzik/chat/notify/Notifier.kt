package app.ryzik.chat.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import app.ryzik.chat.MainActivity
import app.ryzik.chat.R
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Все уведомления приложения: сообщения (с ответом прямо из шторки), звонки,
 * пропущенные звонки, реакции, добавление в группу и фоновая работа.
 */
object Notifier {
    const val CH_MESSAGES = "messages"
    const val CH_CALLS = "calls"
    const val CH_EVENTS = "events"
    const val CH_BACKGROUND = "background"

    const val CALL_ID = 7001
    const val BACKGROUND_ID = 7002
    private const val MISSED_BASE = 8000

    const val KEY_REPLY = "reply_text"
    const val EXTRA_CHAT_ID = "chat_id"
    const val EXTRA_ACCOUNT_ID = "account_id"

    private class Line(val sender: String, val senderId: String, val text: String, val time: Long)
    private val history = ConcurrentHashMap<String, MutableList<Line>>()
    /** chatId -> аккаунт, если уведомление пришло не для текущего аккаунта. */
    private val chatAccount = ConcurrentHashMap<String, Pair<String, String>>()

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_MESSAGES, "Сообщения", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Новые сообщения в чатах, группах и каналах"
        })
        nm.createNotificationChannel(NotificationChannel(CH_CALLS, "Звонки", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Входящие звонки"
            setSound(null, null)
        })
        nm.createNotificationChannel(NotificationChannel(CH_EVENTS, "События", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Пропущенные звонки, реакции, добавление в группы"
        })
        nm.createNotificationChannel(NotificationChannel(CH_BACKGROUND, "Работа в фоне", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Постоянное уведомление, чтобы RyzikChat получал сообщения при закрытом приложении. Его можно скрыть здесь."
            setShowBadge(false)
        })
    }

    private fun allowed(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @android.annotation.SuppressLint("MissingPermission")
    private fun post(context: Context, id: Int, n: android.app.Notification) {
        if (!allowed(context)) return
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    fun openChatIntent(context: Context, chatId: String?, accountId: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (chatId != null) putExtra(MainActivity.EXTRA_CHAT_ID, chatId)
            if (accountId != null) putExtra(EXTRA_ACCOUNT_ID, accountId)
        }
        return PendingIntent.getActivity(context, ((chatId ?: "app") + accountId.orEmpty()).hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun actionIntent(context: Context, action: String, chatId: String, mutable: Boolean = false): PendingIntent {
        val i = Intent(context, NotificationActionReceiver::class.java).setAction(action).putExtra(EXTRA_CHAT_ID, chatId)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, (action + chatId).hashCode(), i, flags)
    }

    private val palette = intArrayOf(0xFFFF8A3D.toInt(), 0xFF7C4DFF.toInt(), 0xFF00A3A3.toInt(), 0xFF2E6BE6.toInt(), 0xFF43A047.toInt(), 0xFFE91E63.toInt(), 0xFFFFB300.toInt(), 0xFF8D6E63.toInt())

    /** Круглая аватарка с инициалами, как в приложении. */
    fun initialsIcon(name: String): IconCompat = IconCompat.createWithBitmap(initialsBitmap(name))

    fun initialsBitmap(name: String): Bitmap {
        val size = 128
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette[Math.floorMod(name.hashCode(), palette.size)] }
        c.drawCircle(size / 2f, size / 2f, size / 2f, p)
        val parts = name.trim().split(" ").filter { it.isNotBlank() }
        val text = when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(1).uppercase()
            else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
        }
        p.color = 0xFFFFFFFF.toInt()
        p.textSize = size * 0.42f
        p.typeface = Typeface.DEFAULT_BOLD
        p.textAlign = Paint.Align.CENTER
        c.drawText(text, size / 2f, size / 2f - (p.descent() + p.ascent()) / 2, p)
        return bmp
    }

    // ================= Сообщения =================

    fun showMessage(
        context: Context,
        chatId: String,
        chatTitle: String,
        isGroup: Boolean,
        senderId: String,
        senderName: String,
        text: String,
        time: Long,
        vibrate: Boolean,
        canReply: Boolean,
        /** Если сообщение для другого вашего аккаунта: его id и @ник. */
        account: Pair<String, String>? = null,
    ) {
        if (account != null) chatAccount[chatId] = account else chatAccount.remove(chatId)
        val lines = history.getOrPut(chatId) { mutableListOf() }
        synchronized(lines) {
            lines += Line(senderName, senderId, text, time)
            while (lines.size > 8) lines.removeAt(0)
        }
        render(context, chatId, chatTitle, isGroup, vibrate, canReply && account == null, silent = false)
    }

    /** Ответ из шторки: добавляем свою строку и обновляем уведомление без звука. */
    fun appendMine(context: Context, chatId: String, chatTitle: String, isGroup: Boolean, text: String) {
        val lines = history[chatId] ?: return
        synchronized(lines) { lines += Line("Вы", "", text, System.currentTimeMillis()) }
        render(context, chatId, chatTitle, isGroup, vibrate = false, canReply = true, silent = true)
    }

    private fun render(context: Context, chatId: String, chatTitle: String, isGroup: Boolean, vibrate: Boolean, canReply: Boolean, silent: Boolean) {
        val lines = history[chatId]?.let { synchronized(it) { it.toList() } } ?: return
        val account = chatAccount[chatId]
        val me = Person.Builder().setName("Вы").setKey("me").build()
        val style = NotificationCompat.MessagingStyle(me)
        if (isGroup) {
            style.conversationTitle = chatTitle
            style.isGroupConversation = true
        }
        for (l in lines) {
            val person = if (l.senderId.isEmpty()) null
            else Person.Builder().setName(l.sender).setKey(l.senderId).setIcon(initialsIcon(l.sender)).build()
            style.addMessage(l.text, l.time, person)
        }
        val b = NotificationCompat.Builder(context, CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setLargeIcon(initialsBitmap(chatTitle))
            .setContentIntent(openChatIntent(context, chatId, account?.first))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(false)
            .setSilent(silent)
            .setGroup("ryzik_messages")
            .setNumber(lines.count { it.senderId.isNotEmpty() })
        if (!vibrate) b.setVibrate(longArrayOf(0))
        if (account != null) b.setSubText("для @${account.second}")
        if (canReply) {
            val input = RemoteInput.Builder(KEY_REPLY).setLabel("Ответить…").build()
            b.addAction(
                NotificationCompat.Action.Builder(R.drawable.ic_notification, "Ответить", actionIntent(context, NotificationActionReceiver.ACTION_REPLY, chatId, mutable = true))
                    .addRemoteInput(input)
                    .setAllowGeneratedReplies(true)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .build()
            )
        }
        if (account == null) b.addAction(
            NotificationCompat.Action.Builder(R.drawable.ic_notification, "Прочитано", actionIntent(context, NotificationActionReceiver.ACTION_READ, chatId))
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                .setShowsUserInterface(false)
                .build()
        )
        post(context, chatId.hashCode(), b.build())
    }

    /** Чат открыли или прочитали — убираем его уведомление. */
    fun clearChat(context: Context, chatId: String) {
        history.remove(chatId)
        chatAccount.remove(chatId)
        NotificationManagerCompat.from(context).cancel(chatId.hashCode())
    }

    // ================= События =================

    fun showEvent(context: Context, key: String, title: String, text: String, chatId: String?, iconName: String? = null, account: Pair<String, String>? = null) {
        val b = NotificationCompat.Builder(context, CH_EVENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openChatIntent(context, chatId, account?.first))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
        if (account != null) b.setSubText("для @${account.second}")
        if (iconName != null) b.setLargeIcon(initialsBitmap(iconName))
        post(context, MISSED_BASE + (key.hashCode() and 0xFFFF), b.build())
    }

    fun missedCall(context: Context, peerId: String, name: String, video: Boolean, chatId: String?, account: Pair<String, String>? = null) {
        showEvent(context, "missed_$peerId", if (video) "Пропущенный видеозвонок" else "Пропущенный звонок", name, chatId, iconName = name, account = account)
    }

    // ================= Звонки =================

    fun showIncomingCall(context: Context, name: String, video: Boolean, account: Pair<String, String>? = null) {
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (account != null) putExtra(EXTRA_ACCOUNT_ID, account.first)
        }
        val openPi = PendingIntent.getActivity(context, CALL_ID, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val accept = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_CALL_ACTION, "accept")
            if (account != null) putExtra(EXTRA_ACCOUNT_ID, account.first)
        }
        val acceptPi = PendingIntent.getActivity(context, CALL_ID + 1, accept, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val declinePi = PendingIntent.getBroadcast(
            context, CALL_ID + 2,
            Intent(context, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.ACTION_DECLINE_CALL)
                .apply { if (account != null) putExtra(EXTRA_ACCOUNT_ID, account.first) },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val caller = Person.Builder().setName(name).setIcon(initialsIcon(name)).setImportant(true).build()
        val b = NotificationCompat.Builder(context, CH_CALLS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(name)
            .setContentText((if (video) "Входящий видеозвонок" else "Входящий звонок") + (account?.let { " · для @${it.second}" } ?: ""))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(openPi)
            .setFullScreenIntent(openPi, true)
            .setOngoing(true)
            .setAutoCancel(false)
        val styled = runCatching {
            b.setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, declinePi, acceptPi).setIsVideo(video)).build()
        }.getOrElse {
            b.setStyle(null)
            b.addAction(R.drawable.ic_notification, "Отклонить", declinePi)
            b.addAction(R.drawable.ic_notification, "Ответить", acceptPi)
            b.build()
        }
        post(context, CALL_ID, styled)
    }

    fun cancelCall(context: Context) {
        NotificationManagerCompat.from(context).cancel(CALL_ID)
    }

    // ================= Фон =================

    fun backgroundNotification(context: Context): android.app.Notification =
        NotificationCompat.Builder(context, CH_BACKGROUND)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("RyzikChat на связи")
            .setContentText("Получаем сообщения и звонки в фоне")
            .setContentIntent(openChatIntent(context, null))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
}
