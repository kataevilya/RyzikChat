package app.ryzik.chat.ui.premium

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color

/** Иконка приложения: цвет фона под логотипом. */
class AppIcon(val key: String, val title: String, val colors: List<Color>)

/** Смена иконки приложения (Премиум). Каждая иконка — activity-alias в манифесте, включена ровно одна. */
object AppIcons {
    const val supported = true

    val all = listOf(
        AppIcon("default", "Классическая", listOf(Color.White, Color.White)),
        AppIcon("winter", "Зима", listOf(Color(0xFF0B1A2E), Color(0xFF1B3550), Color(0xFF2E5677))),
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

    /** Иконки, доступные только с Премиумом. Зимняя — основная и бесплатная. */
    private val premiumKeys = setOf("night", "sunset", "mint", "ocean", "gold")

    /** Премиум закончился — возвращаем зимнюю иконку. */
    fun enforce(context: Context, isPremium: Boolean) {
        if (!isPremium && current(context) in premiumKeys) runCatching { apply(context, "winter") }
    }

    /**
     * После установки обновления зимняя иконка включается в манифесте. Если у пользователя
     * уже была выбрана другая (например, премиумная), на экране оказались бы две иконки.
     * Оставляем выбранную пользователем и выключаем остальные.
     */
    fun syncAfterUpdate(context: Context) {
        val pm = context.packageManager
        val enabled = all.filter { icon ->
            val state = pm.getComponentEnabledSetting(alias(context, icon.key))
            if (icon.key == "default") state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            else state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
        if (enabled.size <= 1) return
        val keep = enabled.firstOrNull { it.key !in setOf("winter", "default") }
            ?: enabled.firstOrNull { it.key == "winter" }
            ?: enabled.first()
        for (icon in all) {
            val on = icon.key == keep.key
            pm.setComponentEnabledSetting(
                alias(context, icon.key),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
