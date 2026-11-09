package app.ryzik.chat.ui.premium

import android.content.Context
import androidx.compose.ui.graphics.Color

class AppIcon(val key: String, val title: String, val colors: List<Color>)

/** На ПК иконка приложения не меняется: раздел скрыт. */
object AppIcons {
    const val supported = false
    val all = emptyList<AppIcon>()
    @Suppress("UNUSED_PARAMETER") fun current(context: Context): String = "default"
    @Suppress("UNUSED_PARAMETER") fun apply(context: Context, key: String) {}
    @Suppress("UNUSED_PARAMETER") fun enforce(context: Context, isPremium: Boolean) {}
}
