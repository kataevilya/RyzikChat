package app.ryzik.chat.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import app.ryzik.chat.RyzikApp
import app.ryzik.chat.data.AuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Кнопки в уведомлениях: ответить, прочитано, отклонить звонок. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as RyzikApp
        when (intent.action) {
            ACTION_DECLINE_CALL -> {
                val account = intent.getStringExtra(Notifier.EXTRA_ACCOUNT_ID)
                if (account != null) app.watchers[account]?.declineCall() else app.calls.decline()
                Notifier.cancelCall(context)
            }
            ACTION_READ, ACTION_REPLY -> {
                val chatId = intent.getStringExtra(Notifier.EXTRA_CHAT_ID) ?: return
                val text = if (intent.action == ACTION_REPLY) RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_REPLY)?.toString()?.trim() else null
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        // Если приложение только что запустилось, ждём вход и список чатов:
                        // без него сообщение нельзя зашифровать для собеседника.
                        val repo = app.repo
                        val ready = withTimeoutOrNull(8000) {
                            repo.auth.first { it is AuthState.LoggedIn }
                            repo.chats.first { list -> list.any { it.id == chatId } }
                        }
                        if (ready != null) {
                            if (!text.isNullOrEmpty()) {
                                repo.sendText(chatId, text)
                                val chat = repo.chat(chatId)
                                Notifier.clearChat(context, chatId)
                                if (chat != null) repo.markChatRead(chatId)
                            } else {
                                repo.markChatRead(chatId)
                                Notifier.clearChat(context, chatId)
                            }
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "app.ryzik.chat.REPLY"
        const val ACTION_READ = "app.ryzik.chat.READ"
        const val ACTION_DECLINE_CALL = "app.ryzik.chat.DECLINE_CALL"
    }
}
