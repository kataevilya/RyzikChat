package app.ryzik.chat.ui.premium

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color

/** Иконка приложения: цвет фона под лисёнком. */
class AppIcon(val key: String, val title: String, val colors: List<Color>)

/** Смена иконки приложения (Премиум). Каждая иконка — activity-alias в манифесте, включена ровно одна. */
object AppIcons {
    const val supported = true

    val all = listOf(
        AppIcon("default", "Классическая", listOf(Color(0xFF6E5BFF), Color(0xFF9B3DF0), Color(0xFFE23C8C))),
        AppIcon("night", "Ночь", listOf(Color(0xFF1B1F3B), Color(0xFF2D2A5A), Color(0xFF0E0E1A))),
        AppIcon("sunset", "Закат", listOf(Color(0xFFFF8A3D), Color(0xFFFF5E62), Color(0xFFC2185B))),
        AppIcon("mint", "Мята", listOf(Color(0xFF1DE9B6), Color(0xFF26A69A), Color(0xFF00796B))),
        AppIcon("ocean", "Океан", listOf(Color(0xFF40C4FF), Color(0xFF2979FF), Color(0xFF1A237E))),
        AppIcon("gold", "Золото", listOf(Color(0xFFFFE082), Color(0xFFFFB300), Color(0xFFE65100))),
    )

    private fun alias(context: Context, key: String) =
        ComponentName(context.packageName, "app.ryzik.chat.Icon" + key.replaceFirstChar { it.uppercase() })

    fun current(context: Context): String = all.firstOrNull { icon ->
        val state = context.packageManager.getComponentEnabledSetting(alias(context, icon.key))
        if (icon.key == "default") state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED else state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }?.key ?: "default"

    /** Включить иконку key и выключить остальные. Приложение при этом не закрывается. */
    fun apply(context: Context, key: String) {
        if (current(context) == key) return
        val pm = context.packageManager
        pm.setComponentEnabledSetting(alias(context, key), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        for (icon in all) if (icon.key != key) {
            pm.setComponentEnabledSetting(alias(context, icon.key), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }

    /** Премиум закончился — возвращаем обычную иконку. */
    fun enforce(context: Context, isPremium: Boolean) {
        if (!isPremium && current(context) != "default") runCatching { apply(context, "default") }
    }
}
