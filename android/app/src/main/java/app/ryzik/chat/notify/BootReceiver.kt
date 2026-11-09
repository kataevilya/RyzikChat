package app.ryzik.chat.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** После перезагрузки телефона: запуск приложения поднимает соединение (см. RyzikApp). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Ничего делать не нужно: RyzikApp.onCreate уже запустил вход и фоновую службу.
    }
}
