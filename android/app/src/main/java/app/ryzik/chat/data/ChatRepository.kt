package app.ryzik.chat.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import app.ryzik.chat.crypto.E2E
import app.ryzik.chat.crypto.KeyVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class SendStatus { Sending, Sent, Failed }

/** Сообщение, уже расшифрованное и готовое к показу. */
data class UiMessage(
    val id: String,
    val chatId: String,
    val seq: Long,
    val senderId: String,
    val type: String,
    val content: Content?,
    val decryptFailed: Boolean,
    val replyTo: String?,
    val forwardedFrom: String?,
    val createdAt: Long,
    val editedAt: Long?,
    val deleted: Boolean,
    val reactions: List<Reaction>,
    val clientId: String?,
    val commentOf: String? = null,
    val views: Int? = null,
    val comments: Int = 0,
    val status: SendStatus = SendStatus.Sent,
    val uploadProgress: Float? = null,
    val localFile: File? = null,
)

sealed interface MediaState {
    data object Idle : MediaState
    data class Loading(val progress: Float) : MediaState
    data class Ready(val file: File) : MediaState
    data class Error(val message: String) : MediaState
}

/** События, о которых стоит показать уведомление (кроме новых сообщений). */
sealed interface Notice {
    data class AddedToChat(val chat: Chat, val by: User?) : Notice
    data class ReactionOnMine(val chat: Chat, val messageId: String, val user: User?, val emoji: String, val preview: String) : Notice
}

sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val me: User) : AuthState
}

class ChatRepository(private val context: Context, val prefs: Prefs) {
    val api = ApiClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _auth = MutableStateFlow<AuthState>(AuthState.Loading)
    val auth: StateFlow<AuthState> = _auth.asStateFlow()

    private var privateKey: ByteArray? = null

    /** Все аккаунты на этом телефоне. */
    val accounts: StateFlow<List<SavedAccount>> = prefs.accounts.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _addingAccount = MutableStateFlow(false)
    /** Пользователь входит во второй аккаунт, а первый остаётся сохранённым. */
    val addingAccount: StateFlow<Boolean> = _addingAccount.asStateFlow()
    private var accountBeforeAdding: SavedAccount? = null
    val myId: String? get() = (auth.value as? AuthState.LoggedIn)?.me?.id

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val _chatsLoading = MutableStateFlow(false)
    val chatsLoading: StateFlow<Boolean> = _chatsLoading.asStateFlow()

    /** Сезон, включённый администратором для всех. По умолчанию — зима. */
    private val _season = MutableStateFlow(SeasonInfo())
    val season: StateFlow<SeasonInfo> = _season.asStateFlow()

    private val messageFlows = ConcurrentHashMap<String, MutableStateFlow<List<UiMessage>>>()
    private val hasMore = ConcurrentHashMap<String, Boolean>()
    // ConcurrentHashMap не хранит null, поэтому неудачную расшифровку храним в обёртке.
    private class Decrypted(val content: Content?)
    private val decryptCache = ConcurrentHashMap<String, Decrypted>()

    /** Почта аккаунта (её видит только владелец). */
    private val _stickerPacks = MutableStateFlow<List<StickerPack>>(emptyList())
    val stickerPacks: StateFlow<List<StickerPack>> = _stickerPacks

    private val _contacts = MutableStateFlow<List<User>>(emptyList())
    val contacts: StateFlow<List<User>> = _contacts

    private val _users = MutableStateFlow<Map<String, User>>(emptyMap())
    val users: StateFlow<Map<String, User>> = _users.asStateFlow()

    /** chatId -> (userId -> время, до которого показываем «печатает…») */
    private val _typing = MutableStateFlow<Map<String, Map<String, Long>>>(emptyMap())
    val typing: StateFlow<Map<String, Map<String, Long>>> = _typing.asStateFlow()

    private val _readStates = MutableStateFlow<Map<String, Map<String, Long>>>(emptyMap())
    val readStates: StateFlow<Map<String, Map<String, Long>>> = _readStates.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _incoming = MutableSharedFlow<Pair<Chat, UiMessage>>(extraBufferCapacity = 32)
    /** Новые входящие сообщения — для уведомлений. */
    val incoming: SharedFlow<Pair<Chat, UiMessage>> = _incoming

    private val _notices = MutableSharedFlow<Notice>(extraBufferCapacity = 32)

    /** Ошибки отправки с сервера (например, не хватает FLUX на платное сообщение): чат и текст. */
    private val _sendErrors = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 8)
    val sendErrors = _sendErrors.asSharedFlow()
    val notices: SharedFlow<Notice> = _notices

    private val media = ConcurrentHashMap<String, MutableStateFlow<MediaState>>()
    private val mediaDir = File(context.cacheDir, "media").apply { mkdirs() }

    @Volatile var openChatId: String? = null

    private val _callSignals = MutableSharedFlow<Pair<String, JsonObject>>(extraBufferCapacity = 64)
    /** Сигналы звонков: (от кого, данные). */
    val callSignals: SharedFlow<Pair<String, JsonObject>> = _callSignals

    private var socket: WebSocket? = null
    private var socketJob: Job? = null
    private val chatsMutex = Mutex()

    init {
        scope.launch { restore() }
        scope.launch {
            while (true) {
                delay(1000)
                val now = System.currentTimeMillis()
                _typing.update { m -> m.mapValues { (_, v) -> v.filterValues { it > now } }.filterValues { it.isNotEmpty() } }
            }
        }
    }

    // ================= Вход и регистрация =================

    private suspend fun restore() {
        val s = prefs.session.first()
        api.baseUrl = s.serverUrl
        scope.launch { loadSeason() }
        if (s.token == null || s.wrappedPrivateKey == null) {
            _auth.value = AuthState.LoggedOut
            return
        }
        api.token = s.token
        privateKey = runCatching { KeyVault.unwrap(s.wrappedPrivateKey) }.getOrNull()
        if (privateKey == null) {
            dropCurrentAndContinue()
            return
        }
        val me = runCatching { api.me() }.getOrElse { e ->
            if (e is ApiException && e.status == 401) {
                dropCurrentAndContinue()
                return
            }
            // Нет сети: пускаем в приложение с минимальными данными, всё догрузится позже.
            User(id = s.userId ?: "", username = s.username ?: "", displayName = s.username ?: "")
        }
        onLoggedIn(me)
    }

    /** Текущий аккаунт больше не работает: убираем его и открываем следующий, если он есть. */
    private suspend fun dropCurrentAndContinue() {
        val rest = prefs.clearSession()
        val next = rest.firstOrNull()
        if (next != null) {
            prefs.activate(next)
            restore()
        } else {
            api.token = null
            privateKey = null
            _auth.value = AuthState.LoggedOut
        }
    }

    private fun deviceName() = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    suspend fun setServer(@Suppress("UNUSED_PARAMETER") url: String) {
        // Сервер один на всех и встроен в приложение.
        val clean = app.ryzik.chat.BuildConfig.DEFAULT_SERVER.trimEnd('/')
        prefs.setServer(clean)
        api.baseUrl = clean
    }

    suspend fun checkServer(): Boolean = api.health()

    suspend fun register(username: String, displayName: String, password: String) = withContext(Dispatchers.Default) {
        val keys = E2E.derivePasswordKeys(username, password)
        val kp = E2E.generateKeyPair()
        val res = api.register(
            username = username,
            displayName = displayName,
            password = keys.authKey,
            publicKey = E2E.b64(kp.publicKey),
            encryptedPrivateKey = E2E.sealPrivateKey(kp.privateKey, keys.vaultKey),
            device = deviceName(),
        )
        finishAuth(res, kp.privateKey)
    }

    /** Вход ждёт пароль двухэтапной проверки: ключ для расшифровки держим только в памяти. */
    private var pendingLogin: Pair<String, ByteArray>? = null

    /** Вход. Возвращает null, если вход завершён, или ответ с need2fa, если нужен второй пароль. */
    suspend fun login(username: String, password: String): LoginResponse? = withContext(Dispatchers.Default) {
        val keys = E2E.derivePasswordKeys(username, password)
        val res = api.login(username, keys.authKey, deviceName())
        if (res.need2fa && res.challengeId != null) {
            pendingLogin = res.challengeId to keys.vaultKey
            return@withContext res
        }
        val priv = runCatching { E2E.openPrivateKey(res.encryptedPrivateKey!!, keys.vaultKey) }
            .getOrElse { throw IOException("Не удалось расшифровать ключи аккаунта") }
        finishAuth(AuthResponse(res.token!!, res.user!!, res.encryptedPrivateKey!!), priv)
        null
    }

    suspend fun confirmLogin2fa(password: String) = withContext(Dispatchers.Default) {
        val (challenge, vaultKey) = pendingLogin ?: throw IOException("Начните вход заново")
        val res = api.login2fa(challenge, password)
        val priv = runCatching { E2E.openPrivateKey(res.encryptedPrivateKey, vaultKey) }
            .getOrElse { throw IOException("Не удалось расшифровать ключи аккаунта") }
        pendingLogin = null
        finishAuth(res, priv)
    }

    fun cancelLoginCode() { pendingLogin = null }

    // ================= Контакты =================


    suspend fun refreshContacts() {
        val list = api.contacts()
        rememberUsers(list)
        _contacts.value = list
    }

    suspend fun setContact(userId: String, contact: Boolean) {
        val u = api.setContact(userId, contact)
        rememberUsers(listOf(u))
        _contacts.update { l -> if (contact) (l.filterNot { it.id == u.id } + u).sortedBy { it.displayName.lowercase() } else l.filterNot { it.id == u.id } }
        _chats.update { l -> l.map { c -> if (c.type == "direct" && c.members.any { it.user.id == userId }) c.copy(peerIsContact = contact) else c } }
    }

    suspend fun setBlocked(userId: String, blocked: Boolean) {
        val u = api.setBlocked(userId, blocked)
        rememberUsers(listOf(u))
        if (blocked) _contacts.update { l -> l.filterNot { it.id == userId } }
        _chats.update { l ->
            l.map { c -> if (c.type == "direct" && c.members.any { it.user.id == userId }) c.copy(peerBlocked = blocked, peerIsContact = c.peerIsContact && !blocked) else c }
        }
    }

    private suspend fun finishAuth(res: AuthResponse, priv: ByteArray) {
        if (_addingAccount.value) teardownLocal()
        api.token = res.token
        privateKey = priv
        prefs.saveSession(res.token, res.user.id, res.user.username, KeyVault.wrap(priv))
        _addingAccount.value = false
        accountBeforeAdding = null
        onLoggedIn(res.user)
    }

    private fun onLoggedIn(me: User) {
        rememberUsers(listOf(me))
        scope.launch { prefs.updateAccountInfo(me.id, me.displayName, me.avatarFileId) }
        _auth.value = AuthState.LoggedIn(me)
        scope.launch { refreshChats() }
        scope.launch { loadSeason() }
        scope.launch { runCatching { refreshContacts() } }
        scope.launch { runCatching { refreshStickers() } }
        connectSocket()
    }

    /**
     * Смена @юзернейма. Ключ входа и ключ, которым зашифрован приватный ключ, выводятся из
     * юзернейма и пароля, поэтому нужен текущий пароль: пересчитываем их для нового имени.
     */
    suspend fun changeUsername(newUsername: String, password: String) = withContext(Dispatchers.Default) {
        val me = (auth.value as AuthState.LoggedIn).me
        val name = newUsername.trim().removePrefix("@")
        val oldKeys = E2E.derivePasswordKeys(me.username, password)
        val newKeys = E2E.derivePasswordKeys(name, password)
        api.changeUsername(name, oldKeys.authKey, newKeys.authKey, E2E.sealPrivateKey(privateKey!!, newKeys.vaultKey))
        val s = prefs.session.first()
        if (s.token != null && s.userId != null && s.wrappedPrivateKey != null) prefs.saveSession(s.token, s.userId, name, s.wrappedPrivateKey)
        refreshMe()
    }

    suspend fun changePassword(old: String, new: String) = withContext(Dispatchers.Default) {
        val me = (auth.value as AuthState.LoggedIn).me
        val oldKeys = E2E.derivePasswordKeys(me.username, old)
        val newKeys = E2E.derivePasswordKeys(me.username, new)
        api.changePassword(oldKeys.authKey, newKeys.authKey, E2E.sealPrivateKey(privateKey!!, newKeys.vaultKey))
    }

    /** Удаляет аккаунт навсегда (нужен пароль) и выходит. */
    suspend fun deleteAccount(password: String) {
        val me = (auth.value as AuthState.LoggedIn).me
        val keys = withContext(Dispatchers.Default) { E2E.derivePasswordKeys(me.username, password) }
        api.deleteAccount(keys.authKey)
        resetLocal()
    }

    /** Удалить чат. Личный — у себя или у обоих (forAll), группу или канал — у всех (только владелец). */
    suspend fun deleteChat(chatId: String, forAll: Boolean = true) {
        api.deleteChat(chatId, forAll)
        _chats.update { l -> l.filterNot { it.id == chatId } }
        messageFlows[chatId]?.value = emptyList()
        hasMore.remove(chatId)
    }

    fun logout() {
        scope.launch {
            runCatching { api.logout() }
            resetLocal()
        }
    }

    /** Закрывает соединение и забывает данные текущего аккаунта в памяти (не на диске). */
    private fun teardownLocal() {
        socketJob?.cancel()
        socket?.close(1000, null)
        socket = null
        _connected.value = false
        _chats.value = emptyList()
        messageFlows.clear()
        hasMore.clear()
        decryptCache.clear()
        _users.value = emptyMap()
        _typing.value = emptyMap()
        _readStates.value = emptyMap()
        _contacts.value = emptyList()
    }

    private suspend fun resetLocal() {
        teardownLocal()
        api.token = null
        privateKey = null
        _auth.value = AuthState.Loading
        dropCurrentAndContinue()
    }

    fun switchAccount(acc: SavedAccount) {
        if (acc.userId == myId) return
        scope.launch {
            teardownLocal()
            _auth.value = AuthState.Loading
            prefs.activate(acc)
            restore()
        }
    }

    /** Вход во ещё один аккаунт. Текущий остаётся в списке. */
    fun beginAddAccount() {
        if (accounts.value.size >= MAX_ACCOUNTS) return
        accountBeforeAdding = accounts.value.firstOrNull { it.userId == myId }
        teardownLocal()
        _addingAccount.value = true
        _auth.value = AuthState.LoggedOut
    }

    fun cancelAddAccount() {
        if (!_addingAccount.value) return
        _addingAccount.value = false
        val prev = accountBeforeAdding ?: accounts.value.firstOrNull()
        accountBeforeAdding = null
        scope.launch {
            _auth.value = AuthState.Loading
            if (prev != null) prefs.activate(prev)
            restore()
        }
    }

    fun myFingerprint(): String = privateKey?.let { E2E.fingerprint(E2E.publicFromPrivate(it)) } ?: ""

    fun fingerprintOf(user: User): String = runCatching { E2E.fingerprint(E2E.unb64(user.publicKey)) }.getOrDefault("—")

    // ================= Пользователи =================

    /**
     * full = профиль из /api/me. Публичный профиль не знает о 2FA, FLUX и ограничениях —
     * для себя эти поля сохраняем из прежнего.
     */
    private fun rememberUsers(list: List<User>, full: Boolean = false) {
        if (list.isEmpty()) return
        _users.update { m -> m + list.associateBy { it.id } }
        val me = (auth.value as? AuthState.LoggedIn)?.me ?: return
        list.firstOrNull { it.id == me.id }?.let { u ->
            _auth.value = AuthState.LoggedIn(
                if (full) u else u.copy(
                    has2fa = me.has2fa, twofaHint = me.twofaHint, flux = me.flux, premiumUntil = me.premiumUntil,
                    restrictedUntil = me.restrictedUntil, restrictReason = me.restrictReason,
                ),
            )
        }
    }

    suspend fun searchUsers(q: String): List<User> = api.searchUsers(q).also { rememberUsers(it) }

    suspend fun loadUser(id: String): User = api.user(id).also { rememberUsers(listOf(it)) }

    suspend fun updateProfile(displayName: String? = null, bio: String? = null, avatarFileId: String? = null) {
        val me = api.updateMe(displayName, bio, avatarFileId)
        rememberUsers(listOf(me))
        prefs.updateAccountInfo(me.id, me.displayName, me.avatarFileId)
    }

    /** Аватарки не шифруются: их видят все, как и имя. */
    suspend fun uploadAvatar(uri: Uri): String = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "avatar_${UUID.randomUUID()}")
        context.contentResolver.openInputStream(uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
        try {
            api.upload(tmp, context.contentResolver.getType(uri) ?: "image/jpeg").id
        } finally {
            tmp.delete()
        }
    }

    fun avatarUrl(fileId: String?) = fileId?.let { api.avatarUrl(it) }

    /** Музыка в профиле: загрузить свой трек (null — убрать). */
    suspend fun setProfileMusic(uri: Uri?) {
        if (uri == null) { rememberUsers(listOf(api.setMusic(null, ""))); return }
        val cr = context.contentResolver
        var name = "Музыка"
        cr.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
        }
        val mime = cr.getType(uri)?.takeIf { it.startsWith("audio/") } ?: "audio/mpeg"
        val tmp = File(context.cacheDir, "music_${UUID.randomUUID()}")
        withContext(Dispatchers.IO) { cr.openInputStream(uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } } }
        try {
            rememberUsers(listOf(api.setMusic(api.upload(tmp, mime).id, name.substringBeforeLast('.').take(100))))
        } finally {
            tmp.delete()
        }
    }

    /** Трек из чужого профиля: скачиваем один раз и играем из кэша. */
    suspend fun profileMusicFile(fileId: String): File {
        val f = File(mediaDir, "music_$fileId")
        if (!f.exists()) api.download(fileId, f)
        return f
    }

    /** Показать свой канал в профиле под аватаркой (null — убрать). */
    suspend fun setFeaturedChannel(chatId: String?) = rememberUsers(listOf(api.setFeaturedChannel(chatId)))

    suspend fun addBot(chatId: String, botId: String) = upsertChat(api.addBot(chatId, botId))

    // ================= Двухэтапная проверка =================

    /** Включить или сменить дополнительный пароль. accountPassword — обычный пароль от аккаунта. */
    suspend fun set2fa(accountPassword: String, currentPassword: String?, password: String, hint: String) = withContext(Dispatchers.Default) {
        val me = (auth.value as AuthState.LoggedIn).me
        val authKey = E2E.derivePasswordKeys(me.username, accountPassword).authKey
        val updated = api.set2fa(authKey, currentPassword, password, hint.trim())
        _auth.value = AuthState.LoggedIn(updated)
    }

    suspend fun disable2fa(password: String) {
        _auth.value = AuthState.LoggedIn(api.disable2fa(password))
    }

    // ================= FLUX =================

    /** Перечитать свой профиль: баланс FLUX и Премиум меняются на сервере. */
    suspend fun refreshMe() { rememberUsers(listOf(api.me()), full = true) }

    suspend fun buyPremium(months: Int = 1) { api.buyPremium(months); refreshMe() }

    /** Дополнительный @юзернейм за FLUX. Сделать его основным — обычная смена юзернейма ([changeUsername]). */
    suspend fun buyUsername(name: String) { api.buyUsername(name.trim().removePrefix("@")); refreshMe() }
    suspend fun releaseUsername(name: String) { api.releaseUsername(name); refreshMe() }

    suspend fun buyGift(itemId: String, toUserId: String?, message: String) = api.buyGift(itemId, toUserId, message).also {
        refreshMe()
        app.ryzik.chat.ui.flux.Confetti.fire()
    }

    suspend fun setMessagePrice(price: Int) { api.setMessagePrice(price); refreshMe() }

    suspend fun setCallPrivacy(value: String) { api.setCallPrivacy(value); refreshMe() }

    /** Загружает картинку открыто (для подарков-NFT), уменьшив до 512 px. */
    suspend fun uploadGiftImage(uri: Uri): String = uploadImageScaled(uri, 512, "image/webp", square = true)

    // ================= Обои чата =================

    /** Ставит фото обоями чата для всех участников. null — убрать. */
    suspend fun setWallpaper(chatId: String, uri: Uri?) {
        val fileId = uri?.let { uploadImageScaled(it, 1600, "image/jpeg") }
        upsertChat(api.setWallpaper(chatId, fileId))
    }

    /** Картинка, уменьшенная по длинной стороне до maxSide. Загружается открыто. */
    private suspend fun uploadImageScaled(uri: Uri, maxSide: Int, mime: String, square: Boolean = false): String = withContext(Dispatchers.IO) {
        val src = context.contentResolver.openInputStream(uri)!!.use { android.graphics.BitmapFactory.decodeStream(it) }
            ?: throw IOException("Не удалось открыть картинку")
        val bmp = if (square) {
            // Стикер: вписываем в квадрат 512×512 с прозрачными полями.
            val out = android.graphics.Bitmap.createBitmap(maxSide, maxSide, android.graphics.Bitmap.Config.ARGB_8888)
            val k = maxSide.toFloat() / maxOf(src.width, src.height)
            val w = (src.width * k).toInt().coerceAtLeast(1)
            val h = (src.height * k).toInt().coerceAtLeast(1)
            android.graphics.Canvas(out).drawBitmap(
                src, null,
                android.graphics.Rect((maxSide - w) / 2, (maxSide - h) / 2, (maxSide - w) / 2 + w, (maxSide - h) / 2 + h),
                android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
            )
            out
        } else {
            val k = minOf(1f, maxSide.toFloat() / maxOf(src.width, src.height))
            if (k < 1f) android.graphics.Bitmap.createScaledBitmap(src, (src.width * k).toInt(), (src.height * k).toInt(), true) else src
        }
        val tmp = File(context.cacheDir, "img_${UUID.randomUUID()}")
        try {
            tmp.outputStream().use { o ->
                if (mime == "image/webp") {
                    @Suppress("DEPRECATION")
                    val fmt = if (android.os.Build.VERSION.SDK_INT >= 30) android.graphics.Bitmap.CompressFormat.WEBP_LOSSY else android.graphics.Bitmap.CompressFormat.WEBP
                    bmp.compress(fmt, 90, o)
                } else bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, o)
            }
            api.upload(tmp, mime).id
        } finally {
            tmp.delete()
        }
    }

    // ================= Стикеры =================

    suspend fun refreshStickers() {
        _stickerPacks.value = api.stickerPacks()
    }

    private fun putPack(p: StickerPack) {
        _stickerPacks.update { l ->
            when {
                !p.isAdded -> l.filterNot { it.id == p.id }
                l.any { it.id == p.id } -> l.map { if (it.id == p.id) p else it }
                else -> l + p
            }
        }
    }

    suspend fun createStickerPack(title: String): StickerPack = api.createStickerPack(title.trim()).also { putPack(it) }

    /** Делает стикер из картинки: квадрат 512×512 WebP, затем добавляет в набор. */
    suspend fun createSticker(packId: String, uri: Uri, emoji: String = ""): StickerPack {
        val fileId = uploadImageScaled(uri, 512, "image/webp", square = true)
        return api.addSticker(packId, fileId, emoji).also { putPack(it) }
    }

    suspend fun removeSticker(packId: String, stickerId: String) = api.removeSticker(packId, stickerId).also { putPack(it) }

    suspend fun deleteStickerPack(id: String) {
        api.deleteStickerPack(id)
        _stickerPacks.update { l -> l.filterNot { it.id == id } }
    }

    suspend fun stickerPack(id: String): StickerPack = api.stickerPack(id)

    suspend fun setPackAdded(id: String, added: Boolean): StickerPack = api.setPackAdded(id, added).also { putPack(it) }

    fun sendSticker(chatId: String, pack: StickerPack, sticker: Sticker, replyTo: String? = null) {
        sendContent(chatId, "sticker", Content(sticker = StickerRef(pack.id, sticker.id, sticker.fileId, sticker.emoji)), replyTo, null)
    }

    /** Поделиться набором: отправить ссылку на него в чат. */
    fun sharePack(chatId: String, pack: StickerPack) {
        sendText(chatId, "Набор стикеров «${pack.title}»: ${stickerPackLink(pack.id)}")
    }

    // ================= Истории =================

    private val _stories = MutableStateFlow<List<StoryGroup>>(emptyList())
    /** Лента историй: мои первыми, потом с непросмотренными. */
    val stories: StateFlow<List<StoryGroup>> = _stories.asStateFlow()

    suspend fun refreshStories() {
        runCatching { api.stories() }.onSuccess { list ->
            rememberUsers(list.map { it.user })
            _stories.value = list
        }
    }

    /** Опубликовать статус (text) или фото/видео. Фото уменьшается до 1600 px. */
    suspend fun postStory(kind: String, text: String, bg: String, emoji: String, media: Uri?, privacy: String): Story {
        val fileId = when {
            media == null -> null
            kind == "video" -> withContext(Dispatchers.IO) {
                val tmp = File(context.cacheDir, "story_${UUID.randomUUID()}")
                context.contentResolver.openInputStream(media)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                try { api.upload(tmp, context.contentResolver.getType(media) ?: "video/mp4").id } finally { tmp.delete() }
            }
            else -> uploadImageScaled(media, 1600, "image/jpeg")
        }
        return api.postStory(kind, text, bg, emoji, fileId, privacy).also { refreshStories() }
    }

    suspend fun deleteStory(id: String) { api.deleteStory(id); refreshStories() }

    /** Отмечает историю просмотренной (локально сразу, на сервере — в фоне). */
    fun markStorySeen(story: Story) {
        if (story.seen) return
        _stories.update { list ->
            list.map { g ->
                if (g.user.id != story.userId) g else {
                    val st = g.stories.map { if (it.id == story.id) it.copy(seen = true) else it }
                    g.copy(stories = st, hasUnseen = st.any { !it.seen })
                }
            }
        }
        scope.launch { runCatching { api.viewStory(story.id) } }
    }

    suspend fun reactStory(id: String, emoji: String?) {
        val st = api.reactStory(id, emoji)
        _stories.update { list -> list.map { g -> g.copy(stories = g.stories.map { if (it.id == id) st.copy(seen = true) else it }) } }
    }

    suspend fun storyViewers(id: String) = api.storyViewers(id)

    suspend fun userStories(userId: String) = api.userStories(userId)

    // ================= Чаты =================

    /** Текущий сезон с сервера. Если сети нет — остаётся прежний (по умолчанию зима). */
    suspend fun loadSeason() {
        runCatching { api.season() }.getOrNull()?.let { _season.value = it }
    }

    /** Только администратор: включить сезон для всех. */
    suspend fun setSeasonGlobally(style: SeasonStyle) {
        _season.value = api.setSeason(style.key)
    }

    suspend fun refreshChats() {
        _chatsLoading.value = true
        try {
            val tok = api.token
            val list = api.chats()
            if (api.token != tok) return // пока грузили, переключили аккаунт
            rememberUsers(list.flatMap { c -> c.members.map { it.user } })
            chatsMutex.withLock {
                // Каналы, которые человек просто смотрит (не подписан), сервер в списке не отдаёт — сохраняем их.
                val previews = _chats.value.filter { (it.type == "channel" || it.type == "group") && it.myRole == null && list.none { n -> n.id == it.id } }
                _chats.value = sortChats(list + previews)
            }
        } catch (_: Exception) {
        } finally {
            _chatsLoading.value = false
        }
    }

    private fun sortChats(list: List<Chat>) = list.sortedWith(
        compareByDescending<Chat> { it.pinned }.thenByDescending { it.lastMessage?.createdAt ?: it.createdAt }
    )

    private suspend fun upsertChat(chat: Chat) {
        rememberUsers(chat.members.map { it.user })
        chatsMutex.withLock {
            _chats.value = sortChats(_chats.value.filterNot { it.id == chat.id } + chat)
        }
    }

    fun chat(id: String): Chat? = _chats.value.firstOrNull { it.id == id }

    suspend fun loadChat(id: String): Chat = api.chat(id).also { upsertChat(it) }

    fun chatTitle(chat: Chat): String = when (chat.type) {
        "saved" -> "Избранное"
        "direct" -> peerOf(chat)?.displayName ?: "Чат"
        else -> chat.title
    }

    fun peerOf(chat: Chat): User? {
        if (chat.type != "direct") return null
        val id = chat.members.firstOrNull { it.user.id != myId }?.user?.id ?: return null
        return _users.value[id] ?: chat.members.first { it.user.id == id }.user
    }

    suspend fun openDirect(userId: String): Chat = api.openDirect(userId).also { upsertChat(it) }

    suspend fun createGroup(title: String, memberIds: List<String>, isPublic: Boolean = false, description: String = "", avatarFileId: String? = null, username: String? = null): Chat =
        api.createGroup(title, memberIds, isPublic, description, avatarFileId, username).also { upsertChat(it) }

    suspend fun setChatAvatar(chatId: String, avatarFileId: String?) = upsertChat(api.setChatAvatar(chatId, avatarFileId))

    suspend fun setChatPublic(chatId: String, isPublic: Boolean) = upsertChat(api.setChatPublic(chatId, isPublic))

    suspend fun resetInvite(chatId: String) = upsertChat(api.resetInvite(chatId))

    suspend fun invitePreview(code: String): Chat = api.invitePreview(code)

    suspend fun joinInvite(code: String): Chat = api.joinInvite(code).also { upsertChat(it) }

    /** Премиум: эмодзи-статус и оформление профиля. */
    suspend fun updatePremiumLook(emojiStatus: String, style: ProfileStyle?) {
        rememberUsers(listOf(api.updatePremiumLook(emojiStatus, style)))
    }

    suspend fun renameGroup(chatId: String, title: String) = upsertChat(api.updateGroup(chatId, title = title))

    suspend fun addMembers(chatId: String, ids: List<String>) = upsertChat(api.addMembers(chatId, ids))

    suspend fun removeMember(chatId: String, userId: String) {
        api.removeMember(chatId, userId)
        if (userId == myId) _chats.update { l -> l.filterNot { it.id == chatId } } else upsertChat(api.chat(chatId))
    }

    suspend fun setPinned(chatId: String, pinned: Boolean) = upsertChat(api.chatSettings(chatId, pinned = pinned))
    suspend fun setMuted(chatId: String, muted: Boolean) = upsertChat(api.chatSettings(chatId, muted = muted))
    suspend fun setArchived(chatId: String, archived: Boolean) = upsertChat(api.chatSettings(chatId, archived = archived))

    suspend fun createChannel(title: String, description: String, isPublic: Boolean = true, avatarFileId: String? = null, username: String? = null): Chat =
        api.createChannel(title, description, isPublic, avatarFileId, username).also { upsertChat(it) }

    suspend fun setChatUsername(chatId: String, username: String) = upsertChat(api.setChatUsername(chatId, username))

    /** Приглашение: код ссылки ryzik://join/<код> или «@юзернейм» открытой группы или канала. */
    suspend fun invitePreviewAny(code: String): Chat = if (code.startsWith("@")) api.chatByUsername(code) else invitePreview(code)
    suspend fun joinAny(code: String, chat: Chat): Chat =
        if (code.startsWith("@")) api.subscribe(chat.id).also { upsertChat(it) } else joinInvite(code)

    suspend fun searchChannels(q: String): List<Chat> = api.searchChannels(q)

    suspend fun subscribe(chatId: String) = upsertChat(api.subscribe(chatId))

    /** Отписаться от канала или выйти из группы. */
    suspend fun leave(chatId: String) {
        api.removeMember(chatId, myId ?: return)
        _chats.update { l -> l.filterNot { it.id == chatId } }
    }

    suspend fun updateChannel(chatId: String, title: String, description: String) = upsertChat(api.updateChannel(chatId, title, description))

    suspend fun setChannelAdmin(chatId: String, userId: String, admin: Boolean) = upsertChat(api.setChannelRole(chatId, userId, admin))

    fun savedChat(): Chat? = _chats.value.firstOrNull { it.type == "saved" }

    // ================= Сообщения =================

    fun messagesOf(chatId: String): StateFlow<List<UiMessage>> =
        messageFlows.getOrPut(chatId) { MutableStateFlow(emptyList()) }

    fun canLoadMore(chatId: String) = hasMore[chatId] != false

    suspend fun loadLatest(chatId: String) {
        val list = api.messages(chatId, limit = 50)
        if (!hasMore.containsKey(chatId)) hasMore[chatId] = list.size >= 50
        merge(chatId, list.map { decrypt(it) })
        loadReadState(chatId)
    }

    suspend fun loadOlder(chatId: String) {
        if (!canLoadMore(chatId)) return
        val first = messagesOf(chatId).value.firstOrNull { it.status == SendStatus.Sent }?.seq ?: return
        val list = api.messages(chatId, before = first, limit = 50)
        hasMore[chatId] = list.size >= 50
        merge(chatId, list.map { decrypt(it) })
    }

    private suspend fun loadReadState(chatId: String) {
        runCatching { api.readState(chatId) }.onSuccess { list ->
            _readStates.update { it + (chatId to list.associate { r -> r.userId to r.seq }) }
        }
    }

    private fun merge(chatId: String, incoming: List<UiMessage>) {
        val flow = messageFlows.getOrPut(chatId) { MutableStateFlow(emptyList()) }
        flow.update { current ->
            val byId = LinkedHashMap<String, UiMessage>()
            current.forEach { byId[it.id] = it }
            for (m in incoming) {
                // Удалённое сообщение пропадает из чата целиком.
                if (m.deleted) { byId.remove(m.id); continue }
                // Серверная версия заменяет «отправляется…» с тем же clientId
                m.clientId?.let { cid -> byId.values.firstOrNull { it.status != SendStatus.Sent && it.clientId == cid }?.let { p -> byId.remove(p.id) } }
                val old = byId[m.id]
                byId[m.id] = if (old?.localFile != null && m.localFile == null) m.copy(localFile = old.localFile) else m
            }
            byId.values.sortedWith(compareBy<UiMessage> { if (it.status == SendStatus.Sent) 0 else 1 }.thenBy { it.seq }.thenBy { it.createdAt })
        }
    }

    private fun decrypt(m: Message): UiMessage {
        val key = "${m.id}:${m.editedAt ?: 0}"
        val content: Content? = when {
            m.deleted || m.payload.isNullOrEmpty() -> null
            decryptCache.containsKey(key) -> decryptCache[key]?.content
            else -> {
                val c = runCatching {
                    val obj = AppJson.parseToJsonElement(m.payload!!).jsonObject
                    if (obj["v"]?.jsonPrimitive?.intOrNull == 0) {
                        // Каналы публичные: сообщения в них не шифруются.
                        AppJson.decodeFromJsonElement(Content.serializer(), obj["plain"]!!)
                    } else {
                        AppJson.decodeFromString(Content.serializer(), E2E.decrypt(m.payload!!, myId!!, privateKey!!))
                    }
                }.getOrNull()
                decryptCache[key] = Decrypted(c)
                c
            }
        }
        return UiMessage(
            id = m.id, chatId = m.chatId, seq = m.seq, senderId = m.senderId, type = m.type,
            content = content, decryptFailed = !m.deleted && content == null,
            replyTo = m.replyTo, forwardedFrom = m.forwardedFrom, createdAt = m.createdAt, editedAt = m.editedAt,
            deleted = m.deleted, reactions = m.reactions, clientId = m.clientId,
            commentOf = m.commentOf, views = m.views, comments = m.comments,
        )
    }

    // ================= Комментарии и просмотры =================

    private val _commentEvents = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 32)
    /** Новые комментарии (для открытого экрана комментариев). */
    val commentEvents: SharedFlow<RealtimeEvent> = _commentEvents

    suspend fun loadComments(postId: String): Pair<UiMessage, List<UiMessage>> {
        val page = api.comments(postId)
        rememberUsers(page.users)
        return decrypt(page.post) to page.comments.map { decrypt(it) }
    }

    fun decryptComment(m: Message): UiMessage = decrypt(m)

    /** Комментарии открытые, как посты канала: их читают все, кто видит канал. */
    suspend fun sendComment(postId: String, text: String, replyTo: String? = null): UiMessage {
        val payload = AppJson.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("v", 0)
            put("plain", AppJson.encodeToJsonElement(Content.serializer(), Content(text = text.trim())))
        })
        return decrypt(api.sendComment(postId, "text", payload, replyTo, java.util.UUID.randomUUID().toString()))
    }

    suspend fun setDiscussion(channelId: String, groupId: String?) = api.setDiscussion(channelId, groupId).also { upsertChat(it) }

    /** Создаёт группу «<канал> — обсуждение» и привязывает её к каналу. */
    suspend fun createDiscussion(channel: Chat): Chat {
        val group = createGroup("${channel.title} — обсуждение".take(128), emptyList(), isPublic = channel.isPublic)
        return setDiscussion(channel.id, group.id)
    }

    private val viewed = java.util.Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val pendingViews = ConcurrentHashMap<String, MutableSet<String>>()
    private var viewsJob: kotlinx.coroutines.Job? = null

    /** Пост канала показан на экране: копим и раз в пару секунд отправляем на сервер. */
    fun markViewed(chatId: String, messageId: String) {
        if (!viewed.add(messageId)) return
        pendingViews.getOrPut(chatId) { java.util.Collections.newSetFromMap(ConcurrentHashMap()) }.add(messageId)
        if (viewsJob?.isActive == true) return
        viewsJob = scope.launch {
            delay(1500)
            for (id in pendingViews.keys.toList()) {
                val ids = pendingViews.remove(id)?.toList().orEmpty()
                if (ids.isEmpty()) continue
                runCatching { api.markViews(id, ids) }
                    .onSuccess { r -> messageFlows[id]?.update { list -> list.map { m -> r.views[m.id]?.let { v -> m.copy(views = v) } ?: m } } }
                    .onFailure { viewed.removeAll(ids.toSet()) }
            }
        }
    }

    fun previewOf(m: Message?): String {
        if (m == null) return ""
        if (m.deleted) return "Сообщение удалено"
        val c = decrypt(m).content ?: return "Зашифрованное сообщение"
        return previewText(m.type, c)
    }

    fun previewText(type: String, c: Content): String = when (type) {
        "image" -> "Фото" + c.text.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        "video" -> if (c.file?.square == true) "Видеосообщение" else "Видео" + c.text.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        "file" -> "${c.file?.name ?: "Файл"}"
        "voice" -> "Голосовое сообщение"
        "square" -> "Видеосообщение"
        "sticker" -> "Стикер"
        "gift" -> "Подарок" + (c.gift?.title?.takeIf { it.isNotBlank() }?.let { " «$it»" } ?: "")
        "call" -> callText(c.call)
        else -> c.text
    }

    private fun recipientsOf(chatId: String): Map<String, ByteArray> {
        val chat = chat(chatId) ?: return emptyMap()
        val out = HashMap<String, ByteArray>()
        for (m in chat.members) {
            val u = _users.value[m.user.id] ?: m.user
            if (u.publicKey.isNotEmpty()) out[u.id] = E2E.unb64(u.publicKey)
        }
        // Себе — всегда, чтобы видеть свои сообщения на других устройствах.
        privateKey?.let { out[myId!!] = E2E.publicFromPrivate(it) }
        return out
    }

    private fun encryptFor(chatId: String, content: Content): String {
        // Каналы публичные, а бот не умеет расшифровывать — им шлём открытым текстом.
        if (chat(chatId)?.type == "channel" || isBotChat(chatId)) {
            return AppJson.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("v", 0)
                put("plain", AppJson.encodeToJsonElement(Content.serializer(), content))
            })
        }
        return E2E.encrypt(AppJson.encodeToString(Content.serializer(), content), recipientsOf(chatId))
    }

    fun isBotChat(chatId: String): Boolean {
        val c = chat(chatId) ?: return false
        // В группе с ботом сообщения тоже открытым текстом, иначе бот их не прочитает.
        return (c.type == "direct" || c.type == "group") && c.members.any { m -> m.user.id != myId && (_users.value[m.user.id] ?: m.user).isBot }
    }

    /** Запись о звонке в личном чате. Пишет её звонивший, когда звонок закончился. */
    fun logCall(peerId: String, call: CallRef) {
        scope.launch {
            runCatching {
                val chat = chats.value.firstOrNull { c -> c.type == "direct" && c.members.any { it.user.id == peerId } } ?: openDirect(peerId)
                sendContent(chat.id, "call", Content(call = call), null, null)
            }
        }
    }

    fun sendText(chatId: String, text: String, replyTo: String? = null) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        sendContent(chatId, "text", Content(text = trimmed), replyTo, null)
    }

    private fun sendContent(chatId: String, type: String, content: Content, replyTo: String?, forwardedFrom: String?, pendingId: String? = null) {
        val clientId = pendingId ?: UUID.randomUUID().toString()
        if (pendingId == null) merge(chatId, listOf(pending(chatId, clientId, type, content, replyTo, forwardedFrom)))
        scope.launch {
            try {
                val sent = postMessage(chatId, type, content, replyTo, forwardedFrom, clientId)
                decryptCache["${sent.id}:0"] = Decrypted(content)
                merge(chatId, listOf(decrypt(sent)))
                bumpChat(sent)
            } catch (e: Exception) {
                markFailed(chatId, clientId)
                if (e is ApiException) _sendErrors.tryEmit(chatId to (e.message ?: "Не удалось отправить"))
            }
        }
    }

    /**
     * Отправка на сервер. Старый сервер не знает тип «square» — тогда шлём квадратик
     * как обычное видео с пометкой square, и приложение всё равно покажет его квадратиком.
     */
    private suspend fun postMessage(chatId: String, type: String, content: Content, replyTo: String?, forwardedFrom: String?, clientId: String): Message {
        val c = if (type == "square" && content.file != null && !content.file.square) content.copy(file = content.file.copy(square = true)) else content
        return try {
            api.sendMessage(chatId, type, encryptFor(chatId, c), replyTo, forwardedFrom, clientId)
        } catch (e: ApiException) {
            if (type != "square" || e.code != "bad_type") throw e
            api.sendMessage(chatId, "video", encryptFor(chatId, c), replyTo, forwardedFrom, clientId)
        }
    }

    private fun pending(chatId: String, clientId: String, type: String, content: Content, replyTo: String?, forwardedFrom: String?, local: File? = null) =
        UiMessage(
            id = "pending:$clientId", chatId = chatId, seq = Long.MAX_VALUE, senderId = myId ?: "", type = type,
            content = content, decryptFailed = false, replyTo = replyTo, forwardedFrom = forwardedFrom,
            createdAt = System.currentTimeMillis(), editedAt = null, deleted = false, reactions = emptyList(),
            clientId = clientId, status = SendStatus.Sending, localFile = local,
        )

    private fun markFailed(chatId: String, clientId: String) {
        messageFlows[chatId]?.update { l -> l.map { if (it.clientId == clientId && it.status != SendStatus.Sent) it.copy(status = SendStatus.Failed, uploadProgress = null) else it } }
    }

    private fun setProgress(chatId: String, clientId: String, p: Float) {
        messageFlows[chatId]?.update { l -> l.map { if (it.clientId == clientId && it.status == SendStatus.Sending) it.copy(uploadProgress = p) else it } }
    }

    fun retry(msg: UiMessage) {
        val c = msg.content ?: return
        messageFlows[msg.chatId]?.update { l -> l.map { if (it.id == msg.id) it.copy(status = SendStatus.Sending) else it } }
        if (msg.localFile != null && c.file?.id.isNullOrEmpty()) {
            uploadAndSend(msg.chatId, msg.type, msg.localFile, c, msg.replyTo, msg.clientId!!)
        } else {
            sendContent(msg.chatId, msg.type, c, msg.replyTo, msg.forwardedFrom, msg.clientId)
        }
    }

    fun discard(msg: UiMessage) {
        messageFlows[msg.chatId]?.update { l -> l.filterNot { it.id == msg.id } }
    }

    /** Отправка фото, видео или любого файла. Файл шифруется на устройстве ещё до загрузки. */
    fun sendFile(chatId: String, uri: Uri, caption: String, asType: String? = null, replyTo: String? = null) {
        scope.launch {
            val cr = context.contentResolver
            val mime = cr.getType(uri) ?: "application/octet-stream"
            var name = "file"
            var size = 0L
            cr.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                    c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
                }
            }
            val type = asType ?: when {
                mime.startsWith("image/") && !mime.contains("svg") -> "image"
                mime.startsWith("video/") -> "video"
                else -> "file"
            }
            val clientId = UUID.randomUUID().toString()
            // Копия оригинала: показываем её сразу и не скачиваем потом своё же фото.
            val local = File(mediaDir, "local_$clientId")
            withContext(Dispatchers.IO) { cr.openInputStream(uri)!!.use { i -> local.outputStream().use { i.copyTo(it) } } }
            if (size <= 0) size = local.length()
            var w = 0
            var h = 0
            var duration = 0L
            if (type == "image") {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(local.path, o)
                w = o.outWidth; h = o.outHeight
            } else if (type == "video") {
                runCatching {
                    val r = MediaMetadataRetriever()
                    r.setDataSource(local.path)
                    w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0
                    h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0
                    val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toInt() ?: 0
                    if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
                    duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
                    r.release()
                }
            }
            val content = Content(text = caption.trim(), file = FileRef(id = "", key = "", name = name, size = size, mime = mime, width = w, height = h, durationMs = duration))
            merge(chatId, listOf(pending(chatId, clientId, type, content, replyTo, null, local)))
            uploadAndSend(chatId, type, local, content, replyTo, clientId)
        }
    }

    /** Отправка записанного на устройстве: голосового или квадратного видеосообщения. */
    fun sendRecorded(
        chatId: String, file: File, type: String, mime: String, durationMs: Long,
        waveform: String = "", width: Int = 0, height: Int = 0, replyTo: String? = null,
    ) {
        val clientId = UUID.randomUUID().toString()
        val local = File(mediaDir, "local_$clientId")
        if (!file.renameTo(local)) { file.copyTo(local, overwrite = true); file.delete() }
        val ext = if (type == "voice") "m4a" else "mp4"
        val content = Content(file = FileRef(
            id = "", key = "", name = "$type-${System.currentTimeMillis()}.$ext", size = local.length(), mime = mime,
            width = width, height = height, durationMs = durationMs, waveform = waveform, square = type == "square",
        ))
        merge(chatId, listOf(pending(chatId, clientId, type, content, replyTo, null, local)))
        uploadAndSend(chatId, type, local, content, replyTo, clientId)
    }

    fun recordingFile(ext: String): File = File(context.cacheDir, "rec_${UUID.randomUUID()}.$ext")

    private fun uploadAndSend(chatId: String, type: String, local: File, content: Content, replyTo: String?, clientId: String) {
        scope.launch(Dispatchers.IO) {
            val enc = File(context.cacheDir, "enc_$clientId")
            try {
                val total = local.length().coerceAtLeast(1)
                val key = local.inputStream().use { input ->
                    enc.outputStream().use { out -> E2E.encryptFile(input, out) { setProgress(chatId, clientId, 0.1f * it / total) } }
                }
                val up = api.upload(enc, "application/octet-stream") { setProgress(chatId, clientId, 0.1f + 0.9f * it) }
                // Отправленный файл уже лежит у нас в расшифрованном виде.
                val ready = File(mediaDir, up.id)
                local.copyTo(ready, overwrite = true)
                media.getOrPut(up.id) { MutableStateFlow(MediaState.Idle) }.value = MediaState.Ready(ready)
                val finalContent = content.copy(file = content.file!!.copy(id = up.id, key = key))
                val sent = postMessage(chatId, type, finalContent, replyTo, null, clientId)
                decryptCache["${sent.id}:0"] = Decrypted(finalContent)
                merge(chatId, listOf(decrypt(sent).copy(localFile = ready)))
                bumpChat(sent)
                local.delete()
            } catch (_: Exception) {
                markFailed(chatId, clientId)
            } finally {
                enc.delete()
            }
        }
    }

    suspend fun editMessage(msg: UiMessage, newText: String) {
        val c = (msg.content ?: return).copy(text = newText.trim())
        val updated = api.editMessage(msg.id, encryptFor(msg.chatId, c))
        decryptCache["${updated.id}:${updated.editedAt ?: 0}"] = Decrypted(c)
        merge(msg.chatId, listOf(decrypt(updated)))
    }

    suspend fun deleteMessage(msg: UiMessage) {
        if (msg.status != SendStatus.Sent) return discard(msg)
        merge(msg.chatId, listOf(decrypt(api.deleteMessage(msg.id))))
        if (chat(msg.chatId)?.lastMessage?.id == msg.id) runCatching { loadChat(msg.chatId) }
    }

    suspend fun react(msg: UiMessage, emoji: String) {
        val mine = msg.reactions.firstOrNull { it.userId == myId }?.emoji
        val updated = api.react(msg.id, if (mine == emoji) null else emoji)
        merge(msg.chatId, listOf(decrypt(updated)))
    }

    /** Пересылка: перешифровываем содержимое для участников другого чата. */
    fun forward(msg: UiMessage, toChatId: String) {
        val c = msg.content ?: return
        sendContent(toChatId, msg.type, c, null, msg.forwardedFrom ?: msg.senderId)
    }

    fun saveToFavorites(msg: UiMessage) {
        savedChat()?.let { forward(msg, it.id) }
    }

    fun markRead(chatId: String) {
        val last = messagesOf(chatId).value.lastOrNull { it.status == SendStatus.Sent } ?: return
        val chat = chat(chatId) ?: return
        if (chat.lastReadSeq >= last.seq && chat.unread == 0) return
        _chats.update { l -> l.map { if (it.id == chatId) it.copy(unread = 0, lastReadSeq = last.seq) else it } }
        scope.launch { runCatching { api.markRead(chatId, last.seq) } }
    }

    /** Отметить чат прочитанным без открытия (кнопка «Прочитано» в уведомлении). */
    suspend fun markChatRead(chatId: String) {
        val c = chat(chatId) ?: return
        val seq = c.lastMessage?.seq ?: return
        _chats.update { l -> l.map { if (it.id == chatId) it.copy(unread = 0, lastReadSeq = seq) else it } }
        runCatching { api.markRead(chatId, seq) }
    }

    private suspend fun bumpChat(m: Message) {
        val existing = chat(m.chatId)
        if (existing == null) {
            runCatching { loadChat(m.chatId) }
            return
        }
        val mine = m.senderId == myId
        val unread = when {
            mine -> 0
            openChatId == m.chatId -> 0
            else -> existing.unread + 1
        }
        upsertChat(existing.copy(lastMessage = m, unread = unread, lastReadSeq = if (mine) m.seq else existing.lastReadSeq))
    }

    // ================= Медиа =================

    fun mediaState(fileId: String): StateFlow<MediaState> {
        val flow = media.getOrPut(fileId) { MutableStateFlow(MediaState.Idle) }
        if (flow.value == MediaState.Idle) {
            val f = File(mediaDir, fileId)
            if (f.exists()) flow.value = MediaState.Ready(f)
        }
        return flow
    }

    fun download(ref: FileRef) {
        if (ref.id.isEmpty()) return
        val flow = media.getOrPut(ref.id) { MutableStateFlow(MediaState.Idle) }
        if (flow.value is MediaState.Loading || flow.value is MediaState.Ready) return
        flow.value = MediaState.Loading(0f)
        scope.launch(Dispatchers.IO) {
            val enc = File(context.cacheDir, "dl_${ref.id}")
            val out = File(mediaDir, ref.id)
            try {
                api.download(ref.id, enc)
                flow.value = MediaState.Loading(0.9f)
                val tmp = File(out.path + ".tmp")
                tmp.outputStream().use { E2E.decryptFile(enc, ref.key, it) }
                tmp.renameTo(out)
                flow.value = MediaState.Ready(out)
            } catch (e: Exception) {
                flow.value = MediaState.Error(e.message ?: "Ошибка")
            } finally {
                enc.delete()
            }
        }
    }

    fun cacheSize(): Long = (mediaDir.listFiles()?.sumOf { it.length() } ?: 0L)

    fun clearCache() {
        mediaDir.listFiles()?.forEach { it.delete() }
        media.clear()
    }

    // ================= Админка и бейджи =================

    suspend fun badges() = api.badges()
    suspend fun createBadge(emoji: String, title: String, description: String, color: String) = api.createBadge(emoji, title, description, color)
    suspend fun deleteBadge(id: String) = api.deleteBadge(id)
    suspend fun grantBadge(userId: String, badgeId: String) = api.grantBadge(userId, badgeId).also { rememberUsers(listOf(it)) }
    suspend fun revokeBadge(userId: String, badgeId: String) = api.revokeBadge(userId, badgeId).also { rememberUsers(listOf(it)) }
    suspend fun setAdmin(userId: String, isAdmin: Boolean) = api.setAdmin(userId, isAdmin).also { rememberUsers(listOf(it)) }
    suspend fun setPremium(userId: String, isPremium: Boolean) = api.setPremium(userId, isPremium).also { rememberUsers(listOf(it)) }
    suspend fun setVerified(userId: String, verified: Boolean) = api.setUserVerified(userId, verified).also { rememberUsers(listOf(it)) }
    /** Закрепить сообщение в чате (null — открепить). */
    suspend fun pinMessage(chatId: String, messageId: String?) { upsertChat(api.pinMessage(chatId, messageId)) }
    suspend fun setChatVerified(chatId: String, verified: Boolean) { api.setChatVerified(chatId, verified); runCatching { loadChat(chatId) } }

    // ================= Звонки =================

    suspend fun callsConfig(): JsonObject = api.callsConfig()

    fun sendCallSignal(to: String, data: JsonObject): Boolean {
        val msg = buildJsonObject {
            put("type", "call.signal")
            put("to", to)
            put("data", data)
        }
        return socket?.send(AppJson.encodeToString(JsonObject.serializer(), msg)) == true
    }

    // ================= Реальное время =================

    /** Приложение на экране: только тогда собеседники видят «в сети». */
    @Volatile private var appActive = false

    fun setAppActive(active: Boolean) {
        appActive = active
        socket?.send(if (active) "{\"type\":\"presence\",\"active\":true}" else "{\"type\":\"presence\",\"active\":false}")
    }

    fun sendTyping(chatId: String) {
        socket?.send("""{"type":"typing","chatId":"$chatId"}""")
    }

    private fun connectSocket() {
        socketJob?.cancel()
        socketJob = scope.launch {
            var backoff = 1000L
            while (auth.value is AuthState.LoggedIn) {
                val closed = kotlinx.coroutines.CompletableDeferred<Unit>()
                val wsUrl = api.baseUrl.replaceFirst("http", "ws") + "/ws?token=" + api.token + "&active=" + (if (appActive) 1 else 0)
                val ws = api.http.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                        _connected.value = true
                        backoff = 1000L
                        scope.launch { resync() }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val ev = runCatching { AppJson.decodeFromString(RealtimeEvent.serializer(), text) }.getOrNull() ?: return
                        scope.launch { handle(ev) }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                        if (code == 4001) scope.launch { resetLocal() }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        _connected.value = false
                        closed.complete(Unit)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                        _connected.value = false
                        closed.complete(Unit)
                    }
                })
                socket = ws
                try {
                    closed.await()
                } finally {
                    if (!closed.isCompleted) ws.cancel()
                }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(30_000)
            }
        }
    }

    private suspend fun resync() {
        refreshChats()
        scope.launch { refreshStories() }
        for (id in messageFlows.keys) runCatching { loadLatest(id) }
    }

    private suspend fun handle(ev: RealtimeEvent) {
        when (ev.type) {
            "comment.new" -> {
                ev.user?.let { rememberUsers(listOf(it)) }
                val chatId = ev.chatId ?: return
                val postId = ev.messageId ?: return
                messageFlows[chatId]?.update { list -> list.map { if (it.id == postId) it.copy(comments = ev.count ?: it.comments) else it } }
                _commentEvents.tryEmit(ev)
            }
            "message.new" -> {
                val m = ev.message ?: return
                if (_users.value[m.senderId] == null) scope.launch { runCatching { loadUser(m.senderId) } }
                val ui = decrypt(m)
                merge(m.chatId, listOf(ui))
                bumpChat(m)
                if (m.senderId != myId) {
                    _typing.update { t -> t + (m.chatId to (t[m.chatId].orEmpty() - m.senderId)) }
                    chat(m.chatId)?.let { _incoming.tryEmit(it to ui) }
                    if (m.type == "gift") app.ryzik.chat.ui.flux.Confetti.fire()
                }
            }
            "message.updated" -> {
                val m = ev.message ?: return
                merge(m.chatId, listOf(decrypt(m)))
                val c = chat(m.chatId)
                if (c?.lastMessage?.id == m.id) {
                    // Удалили последнее сообщение — в списке чатов показываем предыдущее.
                    if (m.deleted) runCatching { loadChat(m.chatId) } else upsertChat(c.copy(lastMessage = m))
                }
            }
            "gifts.changed" -> Unit
            "chat.new" -> ev.chat?.let { c ->
                upsertChat(c)
                val by = ev.by
                if (by != null && by != myId && (c.type == "group" || c.type == "channel")) {
                    val who = _users.value[by] ?: runCatching { loadUser(by) }.getOrNull()
                    _notices.tryEmit(Notice.AddedToChat(c, who))
                }
            }
            "reaction" -> {
                val chatId = ev.chatId ?: return
                val msgId = ev.messageId ?: return
                val userId = ev.userId ?: return
                val emoji = ev.emoji ?: return
                if (userId == myId) return
                val c = chat(chatId) ?: runCatching { loadChat(chatId) }.getOrNull() ?: return
                val who = _users.value[userId] ?: runCatching { loadUser(userId) }.getOrNull()
                val m = messageFlows[chatId]?.value?.firstOrNull { it.id == msgId }
                val preview = m?.content?.let { previewText(m.type, it) }
                    ?: c.lastMessage?.takeIf { it.id == msgId }?.let { previewOf(it) }
                    ?: ""
                _notices.tryEmit(Notice.ReactionOnMine(c, msgId, who, emoji, preview))
            }
            "chat.updated" -> ev.chatId?.let { id -> runCatching { loadChat(id) } }
            "season.updated" -> _season.value = SeasonInfo(ev.season ?: "winter", ev.updatedAt ?: 0)
            "chat.streak" -> {
                val id = ev.chatId ?: return
                chatsMutex.withLock { _chats.update { l -> l.map { if (it.id == id) it.copy(streak = ev.streak) else it } } }
            }
            "story.new", "story.deleted" -> refreshStories()
            "chat.removed" -> ev.chatId?.let { id -> _chats.update { l -> l.filterNot { it.id == id } } }
            "typing" -> {
                val chatId = ev.chatId ?: return
                val userId = ev.userId ?: return
                _typing.update { t -> t + (chatId to (t[chatId].orEmpty() + (userId to System.currentTimeMillis() + 5000))) }
            }
            "read" -> {
                val chatId = ev.chatId ?: return
                val userId = ev.userId ?: return
                _readStates.update { it + (chatId to (it[chatId].orEmpty() + (userId to (ev.seq ?: 0)))) }
                if (userId == myId) app.ryzik.chat.notify.Notifier.clearChat(context, chatId)
            }
            "presence" -> {
                val id = ev.userId ?: return
                _users.update { m -> m[id]?.let { u -> m + (id to u.copy(online = ev.online == true, lastSeen = ev.lastSeen ?: u.lastSeen)) } ?: m }
            }
            "user.updated" -> ev.user?.let { u ->
                // Про себя сервер шлёт урезанный профиль — перечитываем полный (2FA, ограничения).
                if (u.id == myId) scope.launch { runCatching { refreshMe() } } else rememberUsers(listOf(u))
            }
            "call.signal" -> {
                val from = ev.from ?: return
                val data = ev.data ?: return
                _callSignals.emit(from to data)
            }
        }
    }
}

fun Throwable.userMessage(): String = when (this) {
    is ApiException -> message ?: "Ошибка"
    is IOException -> message?.takeIf { it.any { ch -> ch in 'а'..'я' || ch in 'А'..'Я' } } ?: "Нет связи с сервером"
    else -> message ?: "Что-то пошло не так"
}
