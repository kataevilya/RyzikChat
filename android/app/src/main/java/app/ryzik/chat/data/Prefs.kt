package app.ryzik.chat.data

import kotlinx.serialization.builtins.ListSerializer
import androidx.datastore.preferences.core.MutablePreferences
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.ryzik.chat.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("ryzikchat")

enum class ThemeMode(val title: String) { System("Как в системе"), Light("Светлая"), Dark("Тёмная") }

/** Все пользовательские настройки приложения. */
data class AppSettings(
    val onboardingDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val seedColor: Int = 0,
    val amoled: Boolean = false,
    val textSize: Float = 16f,
    val bubbleRadius: Float = 18f,
    val wallpaper: Int = 0,
    val sendByEnter: Boolean = false,
    val swipeToReply: Boolean = true,
    val animations: Boolean = true,
    val autoDownload: Boolean = true,
    val notifications: Boolean = true,
    val notificationPreview: Boolean = true,
    val groupNotifications: Boolean = true,
    val vibrate: Boolean = true,
    val showReadReceipts: Boolean = true,
    val showTyping: Boolean = true,
    val compactList: Boolean = false,
    val bigEmoji: Boolean = true,
    /** Объёмные эмодзи в стиле iOS вместо системных. */
    val iosEmoji: Boolean = true,
    val quickReaction: String = "❤️",
    /** Держать соединение в фоне, чтобы уведомления приходили при закрытом приложении. */
    val backgroundConnection: Boolean = true,
    val callNotifications: Boolean = true,
    val reactionNotifications: Boolean = true,
    val inAppNotifications: Boolean = true,
    /** Уведомления и звонки со всех добавленных аккаунтов, а не только с открытого. */
    val allAccountsNotifications: Boolean = true,
)

/** Данные текущей сессии. Приватный ключ хранится обёрнутым ключом Android Keystore. */
data class StoredSession(
    val serverUrl: String,
    val token: String?,
    val userId: String?,
    val username: String?,
    val wrappedPrivateKey: String?,
)

/** Сохранённый аккаунт: можно держать несколько и переключаться между ними. */
@kotlinx.serialization.Serializable
data class SavedAccount(
    val serverUrl: String,
    val token: String,
    val userId: String,
    val username: String,
    val displayName: String = "",
    val avatarFileId: String? = null,
    val wrappedPrivateKey: String,
)

const val MAX_ACCOUNTS = 3

class Prefs(private val context: Context) {
    private object K {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val theme = stringPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val seed = intPreferencesKey("seed_color")
        val amoled = booleanPreferencesKey("amoled")
        val textSize = floatPreferencesKey("text_size")
        val radius = floatPreferencesKey("bubble_radius")
        val wallpaper = intPreferencesKey("wallpaper")
        val sendByEnter = booleanPreferencesKey("send_by_enter")
        val swipeReply = booleanPreferencesKey("swipe_reply")
        val animations = booleanPreferencesKey("animations")
        val autoDownload = booleanPreferencesKey("auto_download")
        val notifications = booleanPreferencesKey("notifications")
        val notifPreview = booleanPreferencesKey("notif_preview")
        val groupNotif = booleanPreferencesKey("group_notif")
        val vibrate = booleanPreferencesKey("vibrate")
        val readReceipts = booleanPreferencesKey("read_receipts")
        val typing = booleanPreferencesKey("typing")
        val compact = booleanPreferencesKey("compact_list")
        val bigEmoji = booleanPreferencesKey("big_emoji")
        val iosEmoji = booleanPreferencesKey("ios_emoji")
        val quickReaction = stringPreferencesKey("quick_reaction")
        val background = booleanPreferencesKey("background_connection")
        val callNotif = booleanPreferencesKey("call_notifications")
        val reactionNotif = booleanPreferencesKey("reaction_notifications")
        val inAppNotif = booleanPreferencesKey("in_app_notifications")
        val allAccountsNotif = booleanPreferencesKey("all_accounts_notifications")

        val server = stringPreferencesKey("server_url")
        val token = stringPreferencesKey("token")
        val userId = stringPreferencesKey("user_id")
        val username = stringPreferencesKey("username")
        val privKey = stringPreferencesKey("wrapped_private_key")
        val accounts = stringPreferencesKey("accounts")
    }

    val settings: Flow<AppSettings> = context.store.data.map { read(it) }

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            onboardingDone = p[K.onboarding] ?: d.onboardingDone,
            themeMode = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            dynamicColor = p[K.dynamic] ?: d.dynamicColor,
            seedColor = p[K.seed] ?: d.seedColor,
            amoled = p[K.amoled] ?: d.amoled,
            textSize = p[K.textSize] ?: d.textSize,
            bubbleRadius = p[K.radius] ?: d.bubbleRadius,
            wallpaper = p[K.wallpaper] ?: d.wallpaper,
            sendByEnter = p[K.sendByEnter] ?: d.sendByEnter,
            swipeToReply = p[K.swipeReply] ?: d.swipeToReply,
            animations = p[K.animations] ?: d.animations,
            autoDownload = p[K.autoDownload] ?: d.autoDownload,
            notifications = p[K.notifications] ?: d.notifications,
            notificationPreview = p[K.notifPreview] ?: d.notificationPreview,
            groupNotifications = p[K.groupNotif] ?: d.groupNotifications,
            vibrate = p[K.vibrate] ?: d.vibrate,
            showReadReceipts = p[K.readReceipts] ?: d.showReadReceipts,
            showTyping = p[K.typing] ?: d.showTyping,
            compactList = p[K.compact] ?: d.compactList,
            bigEmoji = p[K.bigEmoji] ?: d.bigEmoji,
            iosEmoji = p[K.iosEmoji] ?: d.iosEmoji,
            quickReaction = p[K.quickReaction] ?: d.quickReaction,
            backgroundConnection = p[K.background] ?: d.backgroundConnection,
            callNotifications = p[K.callNotif] ?: d.callNotifications,
            reactionNotifications = p[K.reactionNotif] ?: d.reactionNotifications,
            inAppNotifications = p[K.inAppNotif] ?: d.inAppNotifications,
            allAccountsNotifications = p[K.allAccountsNotif] ?: d.allAccountsNotifications,
        )
    }

    suspend fun update(block: (AppSettings) -> AppSettings) {
        context.store.edit { p ->
            val s = block(read(p))
            p[K.onboarding] = s.onboardingDone
            p[K.theme] = s.themeMode.name
            p[K.dynamic] = s.dynamicColor
            p[K.seed] = s.seedColor
            p[K.amoled] = s.amoled
            p[K.textSize] = s.textSize
            p[K.radius] = s.bubbleRadius
            p[K.wallpaper] = s.wallpaper
            p[K.sendByEnter] = s.sendByEnter
            p[K.swipeReply] = s.swipeToReply
            p[K.animations] = s.animations
            p[K.autoDownload] = s.autoDownload
            p[K.notifications] = s.notifications
            p[K.notifPreview] = s.notificationPreview
            p[K.groupNotif] = s.groupNotifications
            p[K.vibrate] = s.vibrate
            p[K.readReceipts] = s.showReadReceipts
            p[K.typing] = s.showTyping
            p[K.compact] = s.compactList
            p[K.bigEmoji] = s.bigEmoji
            p[K.iosEmoji] = s.iosEmoji
            p[K.quickReaction] = s.quickReaction
            p[K.background] = s.backgroundConnection
            p[K.callNotif] = s.callNotifications
            p[K.reactionNotif] = s.reactionNotifications
            p[K.inAppNotif] = s.inAppNotifications
            p[K.allAccountsNotif] = s.allAccountsNotifications
        }
    }

    val session: Flow<StoredSession> = context.store.data.map { p ->
        StoredSession(
            serverUrl = BuildConfig.DEFAULT_SERVER,
            token = p[K.token],
            userId = p[K.userId],
            username = p[K.username],
            wrappedPrivateKey = p[K.privKey],
        )
    }

    suspend fun setServer(url: String) {
        context.store.edit { it[K.server] = url.trim().trimEnd('/') }
    }

    private fun readAccounts(p: Preferences): List<SavedAccount> {
        // Сервер у всех один и сменить его нельзя: старые адреса из настроек не используем.
        val list = p[K.accounts]?.let { runCatching { AppJson.decodeFromString(ListSerializer(SavedAccount.serializer()), it) }.getOrNull() }.orEmpty()
            .map { it.copy(serverUrl = BuildConfig.DEFAULT_SERVER) }
        // Аккаунт из прошлой версии приложения, где был только один вход
        val token = p[K.token]
        val userId = p[K.userId]
        val key = p[K.privKey]
        if (token != null && userId != null && key != null && list.none { it.userId == userId }) {
            return listOf(SavedAccount(BuildConfig.DEFAULT_SERVER, token, userId, p[K.username] ?: "", p[K.username] ?: "", null, key)) + list
        }
        return list
    }

    private fun writeAccounts(p: MutablePreferences, list: List<SavedAccount>) {
        p[K.accounts] = AppJson.encodeToString(ListSerializer(SavedAccount.serializer()), list)
    }

    val accounts: Flow<List<SavedAccount>> = context.store.data.map { readAccounts(it) }

    suspend fun saveSession(token: String, userId: String, username: String, wrappedPrivateKey: String) {
        context.store.edit {
            val server = BuildConfig.DEFAULT_SERVER
            val old = readAccounts(it)
            val prev = old.firstOrNull { a -> a.userId == userId }
            val acc = SavedAccount(server, token, userId, username, prev?.displayName ?: username, prev?.avatarFileId, wrappedPrivateKey)
            writeAccounts(it, old.filterNot { a -> a.userId == userId } + acc)
            it[K.token] = token
            it[K.userId] = userId
            it[K.username] = username
            it[K.privKey] = wrappedPrivateKey
        }
    }

    /** Обновляет имя и аватар аккаунта в списке для переключателя. */
    suspend fun updateAccountInfo(userId: String, displayName: String, avatarFileId: String?) {
        context.store.edit {
            val list = readAccounts(it)
            if (list.any { a -> a.userId == userId }) {
                writeAccounts(it, list.map { a -> if (a.userId == userId) a.copy(displayName = displayName, avatarFileId = avatarFileId) else a })
            }
        }
    }

    /** Делает аккаунт текущим. */
    suspend fun activate(acc: SavedAccount) {
        context.store.edit {
            it[K.server] = acc.serverUrl
            it[K.token] = acc.token
            it[K.userId] = acc.userId
            it[K.username] = acc.username
            it[K.privKey] = acc.wrappedPrivateKey
        }
    }

    /** Выход из текущего аккаунта: он удаляется из списка. Возвращает оставшиеся аккаунты. */
    suspend fun clearSession(): List<SavedAccount> {
        var rest: List<SavedAccount> = emptyList()
        context.store.edit {
            val current = it[K.userId]
            rest = readAccounts(it).filterNot { a -> a.userId == current }
            writeAccounts(it, rest)
            it.remove(K.token)
            it.remove(K.userId)
            it.remove(K.username)
            it.remove(K.privKey)
        }
        return rest
    }
}
