package app.ryzik.chat.data

import kotlinx.serialization.Serializable

/** Сезонное оформление. Администратор включает его для всех, пользователь может отключить у себя. */
enum class SeasonStyle(val key: String, val title: String, val emoji: String) {
    None("none", "Без сезона", "💬"),
    Winter("winter", "Зима", "❄️"),
    Halloween("halloween", "Хэллоуин", "🎃"),
    NewYear("newyear", "Новый год", "🎄");

    companion object {
        fun fromKey(key: String?): SeasonStyle = entries.firstOrNull { it.key == key } ?: Winter
    }
}

/** Ответ сервера: какой сезон сейчас включён для всех. */
@Serializable
data class SeasonInfo(val season: String = SeasonStyle.Winter.key, val updatedAt: Long = 0) {
    val style: SeasonStyle get() = SeasonStyle.fromKey(season)
}
