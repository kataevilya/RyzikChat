package app.ryzik.chat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val status: Int, val code: String, message: String) : IOException(message)

/** Временная ошибка: сервер перезапускается или ngrok не достучался до него. */
private fun isTemporary(r: okhttp3.Response): Boolean =
    r.code in 502..504 || r.header("ngrok-error-code") != null && r.code >= 400

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

/** Постоянный ID устройства (ANDROID_ID): по нему админ может заблокировать устройство. */
object DeviceId {
    @Volatile var value: String = ""
}

/** Клиент нашего собственного API (см. server/README.md). */
class ApiClient(
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.MINUTES)
        .pingInterval(25, TimeUnit.SECONDS)
        // Сервер работает через ngrok: этот заголовок убирает его страницу-предупреждение.
        .addInterceptor { chain ->
            val b = chain.request().newBuilder().header("ngrok-skip-browser-warning", "1")
            if (DeviceId.value.isNotEmpty()) b.header("X-Device-Id", DeviceId.value)
            chain.proceed(b.build())
        }
        // Сервер перезапускается (обновление) или ngrok на миг потерял связь: повторяем чтение ещё пару раз.
        .addInterceptor { chain ->
            val req = chain.request()
            if (req.method != "GET" || req.header("Upgrade") != null) return@addInterceptor chain.proceed(req)
            repeat(2) { attempt ->
                try {
                    val response = chain.proceed(req)
                    if (!isTemporary(response)) return@addInterceptor response
                    response.close()
                } catch (e: IOException) {
                    if (chain.call().isCanceled()) throw e
                }
                Thread.sleep(1000L * (attempt + 1))
            }
            chain.proceed(req)
        }
        .addInterceptor(ServerFallback.interceptor)
        .build(),
) {
    @Volatile var baseUrl: String = ""
    @Volatile var token: String? = null

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun request(path: String): Request.Builder {
        val b = Request.Builder().url(baseUrl + path)
        token?.let { b.header("Authorization", "Bearer $it") }
        return b
    }

    private suspend fun execute(req: Request): String = withContext(Dispatchers.IO) {
        val response = http.newCall(req).execute()
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) {
                val err = runCatching { AppJson.decodeFromString(ApiError.serializer(), body) }.getOrNull()
                // Ответ не от нашего сервера (ngrok, прокси): объясняем, что случилось, а не просто код.
                val fallback = when {
                    it.header("ngrok-error-code") == "ERR_NGROK_3200" || it.code == 404 && err == null -> "Сервер сейчас выключен. Попробуйте чуть позже"
                    it.code in 502..504 -> "Сервер перезапускается. Попробуйте через минуту"
                    it.code >= 500 -> "Ошибка на сервере (${it.code})"
                    else -> "Ошибка сети (${it.code})"
                }
                throw ApiException(it.code, err?.error ?: "http_${it.code}", err?.message?.ifBlank { null } ?: fallback)
            }
            body
        }
    }

    private suspend fun <T> call(method: String, path: String, body: JsonElement?, ser: KSerializer<T>): T {
        val rb = body?.toString()?.toRequestBody(jsonType) ?: if (method == "GET" || method == "DELETE") null else "{}".toRequestBody(jsonType)
        val text = execute(request(path).method(method, rb).build())
        return AppJson.decodeFromString(ser, text)
    }

    private suspend fun callUnit(method: String, path: String, body: JsonElement? = null) {
        val rb = body?.toString()?.toRequestBody(jsonType) ?: if (method == "GET" || method == "DELETE") null else "{}".toRequestBody(jsonType)
        execute(request(path).method(method, rb).build())
    }

    // ---------- auth ----------

    suspend fun health(): Boolean = runCatching { execute(request("/api/health").get().build()); true }.getOrDefault(false)

    suspend fun register(username: String, displayName: String, password: String, publicKey: String, encryptedPrivateKey: String, device: String) =
        call("POST", "/api/auth/register", buildJsonObject {
            put("username", username)
            put("displayName", displayName)
            put("password", password)
            put("publicKey", publicKey)
            put("encryptedPrivateKey", encryptedPrivateKey)
            put("device", device)
        }, AuthResponse.serializer())

    suspend fun login(username: String, password: String, device: String) =
        call("POST", "/api/auth/login", buildJsonObject {
            put("username", username)
            put("password", password)
            put("device", device)
        }, LoginResponse.serializer())

    suspend fun login2fa(challengeId: String, password: String) =
        call("POST", "/api/auth/login/2fa", buildJsonObject {
            put("challengeId", challengeId)
            put("password", password)
        }, AuthResponse.serializer())

    // ---------- двухэтапная проверка ----------

    suspend fun set2fa(accountPassword: String, currentPassword: String?, password: String, hint: String) =
        call("PUT", "/api/me/2fa", buildJsonObject {
            put("accountPassword", accountPassword)
            if (currentPassword != null) put("currentPassword", currentPassword)
            put("password", password)
            put("hint", hint)
        }, User.serializer())

    suspend fun disable2fa(password: String) =
        call("DELETE", "/api/me/2fa", buildJsonObject { put("password", password) }, User.serializer())

    // ---------- обои и стикеры ----------

    suspend fun setWallpaper(chatId: String, fileId: String?) =
        call("PUT", "/api/chats/$chatId/wallpaper", buildJsonObject { put("fileId", fileId) }, Chat.serializer())

    suspend fun stickerPacks() = call("GET", "/api/stickers", null, ListSerializer(StickerPack.serializer()))
    suspend fun stickerPack(id: String) = call("GET", "/api/stickers/packs/$id", null, StickerPack.serializer())
    suspend fun createStickerPack(title: String) =
        call("POST", "/api/stickers/packs", buildJsonObject { put("title", title) }, StickerPack.serializer())
    suspend fun deleteStickerPack(id: String) = call("DELETE", "/api/stickers/packs/$id", null, kotlinx.serialization.json.JsonObject.serializer())
    suspend fun addSticker(packId: String, fileId: String, emoji: String) =
        call("POST", "/api/stickers/packs/$packId/stickers", buildJsonObject { put("fileId", fileId); put("emoji", emoji) }, StickerPack.serializer())
    suspend fun removeSticker(packId: String, stickerId: String) =
        call("DELETE", "/api/stickers/packs/$packId/stickers/$stickerId", null, StickerPack.serializer())
    suspend fun setPackAdded(id: String, added: Boolean) =
        call(if (added) "PUT" else "DELETE", "/api/stickers/packs/$id/added", null, StickerPack.serializer())

    // ---------- почта ----------


    // ---------- контакты ----------

    suspend fun contacts() = call("GET", "/api/contacts", null, ListSerializer(User.serializer()))
    suspend fun setContact(userId: String, contact: Boolean) =
        call(if (contact) "PUT" else "DELETE", "/api/contacts/$userId", null, User.serializer())
    suspend fun blocks() = call("GET", "/api/blocks", null, ListSerializer(User.serializer()))
    suspend fun setBlocked(userId: String, blocked: Boolean) =
        call(if (blocked) "PUT" else "DELETE", "/api/blocks/$userId", null, User.serializer())

    suspend fun logout() = callUnit("POST", "/api/auth/logout")
    suspend fun deleteAccount(authKey: String) = callUnit("POST", "/api/me/delete", buildJsonObject { put("password", authKey) })
    suspend fun deleteChat(chatId: String, forAll: Boolean = true) = callUnit("DELETE", "/api/chats/$chatId?forAll=$forAll")
    suspend fun sessions() = call("GET", "/api/sessions", null, ListSerializer(SessionInfo.serializer()))
    suspend fun terminateOtherSessions() = callUnit("POST", "/api/sessions/terminate-others")

    suspend fun changeUsername(username: String, oldAuth: String, newAuth: String, encryptedPrivateKey: String) =
        call("POST", "/api/me/username", buildJsonObject {
            put("username", username); put("password", oldAuth); put("newPassword", newAuth); put("encryptedPrivateKey", encryptedPrivateKey)
        }, User.serializer())

    suspend fun changePassword(oldAuth: String, newAuth: String, encryptedPrivateKey: String) =
        callUnit("POST", "/api/me/password", buildJsonObject {
            put("oldPassword", oldAuth)
            put("newPassword", newAuth)
            put("encryptedPrivateKey", encryptedPrivateKey)
        })

    // ---------- users ----------

    suspend fun me() = call("GET", "/api/me", null, User.serializer())

    suspend fun updateMe(displayName: String? = null, bio: String? = null, avatarFileId: String? = null) =
        call("PATCH", "/api/me", JsonObject(buildMap {
            displayName?.let { put("displayName", JsonPrimitive(it)) }
            bio?.let { put("bio", JsonPrimitive(it)) }
            avatarFileId?.let { put("avatarFileId", JsonPrimitive(it)) }
        }), User.serializer())

    /** Премиум: эмодзи-статус ("" — убрать) и оформление профиля (null — сбросить). */
    suspend fun updatePremiumLook(emojiStatus: String, style: ProfileStyle?) =
        call("PATCH", "/api/me", buildJsonObject {
            put("emojiStatus", emojiStatus)
            put("profileStyle", style?.let { AppJson.encodeToJsonElement(ProfileStyle.serializer(), it) } ?: kotlinx.serialization.json.JsonNull)
        }, User.serializer())

    suspend fun searchUsers(q: String) =
        call("GET", "/api/users/search?q=" + java.net.URLEncoder.encode(q, "UTF-8"), null, ListSerializer(User.serializer()))

    suspend fun user(id: String) = call("GET", "/api/users/$id", null, User.serializer())

    // ---------- chats ----------

    suspend fun chats() = call("GET", "/api/chats", null, ListSerializer(Chat.serializer()))
    suspend fun chat(id: String) = call("GET", "/api/chats/$id", null, Chat.serializer())

    suspend fun openDirect(userId: String) =
        call("POST", "/api/chats/direct", buildJsonObject { put("userId", userId) }, Chat.serializer())

    suspend fun createGroup(title: String, memberIds: List<String>, isPublic: Boolean = false, description: String = "", avatarFileId: String? = null, username: String? = null) =
        call("POST", "/api/chats/group", JsonObject(buildMap {
            username?.takeIf { it.isNotBlank() }?.let { put("username", JsonPrimitive(it)) }
            put("title", JsonPrimitive(title))
            put("memberIds", kotlinx.serialization.json.JsonArray(memberIds.map { JsonPrimitive(it) }))
            put("isPublic", JsonPrimitive(isPublic))
            put("description", JsonPrimitive(description))
            avatarFileId?.let { put("avatarFileId", JsonPrimitive(it)) }
        }), Chat.serializer())

    suspend fun setChatAvatar(chatId: String, avatarFileId: String?) =
        call("PATCH", "/api/chats/$chatId", buildJsonObject { put("avatarFileId", avatarFileId ?: "") }, Chat.serializer())

    suspend fun setChatPublic(chatId: String, isPublic: Boolean) =
        call("PATCH", "/api/chats/$chatId", buildJsonObject { put("isPublic", isPublic) }, Chat.serializer())

    suspend fun resetInvite(chatId: String) = call("POST", "/api/chats/$chatId/invite/reset", null, Chat.serializer())
    suspend fun invitePreview(code: String) = call("GET", "/api/invite/${java.net.URLEncoder.encode(code, "UTF-8")}", null, Chat.serializer())
    suspend fun joinInvite(code: String) = call("POST", "/api/invite/${java.net.URLEncoder.encode(code, "UTF-8")}/join", null, Chat.serializer())

    suspend fun updateGroup(chatId: String, title: String? = null, avatarFileId: String? = null) =
        call("PATCH", "/api/chats/$chatId", JsonObject(buildMap {
            title?.let { put("title", JsonPrimitive(it)) }
            avatarFileId?.let { put("avatarFileId", JsonPrimitive(it)) }
        }), Chat.serializer())

    suspend fun chatSettings(chatId: String, pinned: Boolean? = null, muted: Boolean? = null, archived: Boolean? = null) =
        call("PATCH", "/api/chats/$chatId/settings", JsonObject(buildMap {
            pinned?.let { put("pinned", JsonPrimitive(it)) }
            muted?.let { put("muted", JsonPrimitive(it)) }
            archived?.let { put("archived", JsonPrimitive(it)) }
        }), Chat.serializer())

    suspend fun createChannel(title: String, description: String, isPublic: Boolean = true, avatarFileId: String? = null, username: String? = null) =
        call("POST", "/api/chats/channel", buildJsonObject {
            username?.takeIf { it.isNotBlank() }?.let { put("username", it) }
            put("title", title)
            put("description", description)
            put("isPublic", isPublic)
            avatarFileId?.let { put("avatarFileId", it) }
        }, Chat.serializer())

    suspend fun setChatUsername(chatId: String, username: String) =
        call("PATCH", "/api/chats/$chatId", buildJsonObject { put("username", username) }, Chat.serializer())
    suspend fun chatByUsername(name: String) =
        call("GET", "/api/chats/by-username/" + java.net.URLEncoder.encode(name.removePrefix("@"), "UTF-8"), null, Chat.serializer())
    suspend fun checkChatUsername(name: String, chatId: String?) =
        call("GET", "/api/chat-username-check?username=" + java.net.URLEncoder.encode(name, "UTF-8") + (chatId?.let { "&chatId=$it" } ?: ""), null, UsernameCheck.serializer())

    suspend fun searchChannels(q: String) =
        call("GET", "/api/channels/search?q=" + java.net.URLEncoder.encode(q, "UTF-8"), null, ListSerializer(Chat.serializer()))

    suspend fun subscribe(chatId: String) = call("POST", "/api/chats/$chatId/subscribe", null, Chat.serializer())

    suspend fun setChannelRole(chatId: String, userId: String, admin: Boolean) =
        call("PUT", "/api/chats/$chatId/members/$userId/role", buildJsonObject { put("role", if (admin) "admin" else "subscriber") }, Chat.serializer())

    suspend fun updateChannel(chatId: String, title: String, description: String) =
        call("PATCH", "/api/chats/$chatId", buildJsonObject {
            put("title", title)
            put("description", description)
        }, Chat.serializer())

    suspend fun callsConfig() = call("GET", "/api/calls/config", null, JsonObject.serializer())

    suspend fun addMembers(chatId: String, userIds: List<String>) =
        call("POST", "/api/chats/$chatId/members", JsonObject(mapOf(
            "userIds" to kotlinx.serialization.json.JsonArray(userIds.map { JsonPrimitive(it) }),
        )), Chat.serializer())

    suspend fun removeMember(chatId: String, userId: String) = callUnit("DELETE", "/api/chats/$chatId/members/$userId")

    suspend fun markRead(chatId: String, seq: Long) =
        callUnit("POST", "/api/chats/$chatId/read", buildJsonObject { put("seq", seq) })

    suspend fun readState(chatId: String) =
        call("GET", "/api/chats/$chatId/read-state", null, ListSerializer(ReadState.serializer()))

    // ---------- messages ----------

    suspend fun messages(chatId: String, before: Long? = null, limit: Int = 50) =
        call("GET", "/api/chats/$chatId/messages?limit=$limit" + (before?.let { "&before=$it" } ?: ""), null,
            ListSerializer(Message.serializer()))

    suspend fun sendMessage(chatId: String, type: String, payload: String, replyTo: String?, forwardedFrom: String?, clientId: String) =
        call("POST", "/api/chats/$chatId/messages", JsonObject(buildMap {
            put("type", JsonPrimitive(type))
            put("payload", JsonPrimitive(payload))
            put("clientId", JsonPrimitive(clientId))
            replyTo?.let { put("replyTo", JsonPrimitive(it)) }
            forwardedFrom?.let { put("forwardedFrom", JsonPrimitive(it)) }
        }), Message.serializer())

    suspend fun setDiscussion(channelId: String, groupId: String?) =
        call("PUT", "/api/chats/$channelId/discussion", JsonObject(buildMap { put("groupId", groupId?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull) }), Chat.serializer())

    suspend fun comments(postId: String) = call("GET", "/api/messages/$postId/comments", null, CommentsPage.serializer())

    suspend fun sendComment(postId: String, type: String, payload: String, replyTo: String?, clientId: String) =
        call("POST", "/api/messages/$postId/comments", JsonObject(buildMap {
            put("type", JsonPrimitive(type))
            put("payload", JsonPrimitive(payload))
            put("clientId", JsonPrimitive(clientId))
            replyTo?.let { put("replyTo", JsonPrimitive(it)) }
        }), Message.serializer())

    suspend fun markViews(chatId: String, ids: List<String>) =
        call("POST", "/api/chats/$chatId/views", buildJsonObject { put("ids", kotlinx.serialization.json.JsonArray(ids.map { JsonPrimitive(it) })) }, ViewsResult.serializer())

    suspend fun editMessage(id: String, payload: String) =
        call("PATCH", "/api/messages/$id", buildJsonObject { put("payload", payload) }, Message.serializer())

    suspend fun readers(messageId: String) = call("GET", "/api/messages/$messageId/readers", null, Readers.serializer())
    suspend fun deleteMessage(id: String) = call("DELETE", "/api/messages/$id", null, Message.serializer())

    suspend fun react(id: String, emoji: String?) =
        call("PUT", "/api/messages/$id/reaction", buildJsonObject { put("emoji", emoji ?: "") }, Message.serializer())

    // ---------- files ----------

    suspend fun upload(file: File, mime: String, onProgress: (Float) -> Unit = {}): UploadedFile {
        val total = file.length().coerceAtLeast(1)
        val fileBody = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = file.length()
            override fun writeTo(sink: BufferedSink) {
                file.source().use { src ->
                    var sent = 0L
                    while (true) {
                        val n = src.read(sink.buffer, 64 * 1024L)
                        if (n < 0) break
                        sent += n
                        sink.flush()
                        onProgress(sent.toFloat() / total)
                    }
                }
            }
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("mime", mime)
            .addFormDataPart("file", "blob", fileBody)
            .build()
        val text = execute(request("/api/files").post(body).build())
        return AppJson.decodeFromString(UploadedFile.serializer(), text)
    }

    suspend fun download(fileId: String, dest: File) = withContext(Dispatchers.IO) {
        val response = http.newCall(request("/api/files/$fileId").get().build()).execute()
        response.use {
            if (!it.isSuccessful) throw ApiException(it.code, "download_failed", "Не удалось скачать файл")
            val tmp = File(dest.path + ".part")
            tmp.outputStream().use { out -> it.body!!.byteStream().copyTo(out) }
            tmp.renameTo(dest)
        }
    }

    fun fileUrl(fileId: String) = "$baseUrl/api/files/$fileId"

    /** Аватарки и обложки отдаются без токена, поэтому их может загрузить Coil. */
    fun avatarUrl(fileId: String) = "$baseUrl/api/avatars/$fileId"

    // ---------- истории ----------

    suspend fun stories() = call("GET", "/api/stories", null, ListSerializer(StoryGroup.serializer()))
    suspend fun userStories(userId: String) = call("GET", "/api/users/$userId/stories", null, ListSerializer(Story.serializer()))
    suspend fun postStory(kind: String, text: String, bg: String, emoji: String, fileId: String?, privacy: String) =
        call("POST", "/api/stories", buildJsonObject {
            put("kind", kind); put("text", text); put("bg", bg); put("emoji", emoji); put("privacy", privacy)
            if (fileId != null) put("fileId", fileId)
        }, Story.serializer())
    suspend fun deleteStory(id: String) = callUnit("DELETE", "/api/stories/$id")
    suspend fun viewStory(id: String) = call("POST", "/api/stories/$id/view", null, Story.serializer())
    suspend fun reactStory(id: String, emoji: String?) =
        call("PUT", "/api/stories/$id/reaction", buildJsonObject { put("emoji", emoji) }, Story.serializer())
    suspend fun storyViewers(id: String) = call("GET", "/api/stories/$id/viewers", null, ListSerializer(StoryViewer.serializer()))

    // ---------- badges / admin ----------

    suspend fun badges() = call("GET", "/api/badges", null, ListSerializer(Badge.serializer()))

    suspend fun createBadge(emoji: String, title: String, description: String, color: String) =
        call("POST", "/api/admin/badges", buildJsonObject {
            put("emoji", emoji)
            put("title", title)
            put("description", description)
            put("color", color)
        }, Badge.serializer())

    suspend fun deleteBadge(id: String) = callUnit("DELETE", "/api/admin/badges/$id")
    suspend fun grantBadge(userId: String, badgeId: String) = call("PUT", "/api/admin/users/$userId/badges/$badgeId", null, User.serializer())
    suspend fun revokeBadge(userId: String, badgeId: String) = call("DELETE", "/api/admin/users/$userId/badges/$badgeId", null, User.serializer())
    suspend fun setPremium(userId: String, isPremium: Boolean) =
        call("PUT", "/api/admin/users/$userId/premium", buildJsonObject { put("isPremium", isPremium) }, User.serializer())

    // ---------- FLUX и подарки ----------

    suspend fun flux() = call("GET", "/api/flux", null, FluxInfo.serializer())
    suspend fun buyPremium(months: Int) = call("POST", "/api/premium/buy", buildJsonObject { put("months", months) }, User.serializer())
    suspend fun checkUsername(username: String) =
        call("GET", "/api/usernames/check?username=" + java.net.URLEncoder.encode(username, "UTF-8"), null, UsernameOffer.serializer())
    suspend fun usernamePrices() = call("GET", "/api/usernames/prices", null, UsernamePrices.serializer())
    suspend fun buyUsername(username: String) = call("POST", "/api/me/usernames", buildJsonObject { put("username", username) }, User.serializer())
    suspend fun releaseUsername(username: String) =
        call("DELETE", "/api/me/usernames/" + java.net.URLEncoder.encode(username, "UTF-8"), null, User.serializer())
    suspend fun giftShop() = call("GET", "/api/gifts/shop", null, ListSerializer(GiftItem.serializer()))
    suspend fun buyGift(itemId: String, toUserId: String?, message: String) =
        call("POST", "/api/gifts/buy", buildJsonObject { put("itemId", itemId); put("toUserId", toUserId); put("message", message) }, OwnedGift.serializer())
    suspend fun userGifts(userId: String) = call("GET", "/api/users/$userId/gifts", null, ListSerializer(OwnedGift.serializer()))
    suspend fun transferGift(giftId: String, toUserId: String, message: String) =
        call("POST", "/api/gifts/$giftId/transfer", buildJsonObject { put("toUserId", toUserId); put("message", message) }, OwnedGift.serializer())
    suspend fun sellGift(giftId: String) = call("POST", "/api/gifts/$giftId/sell", null, GiftSale.serializer())
    suspend fun setGiftHidden(giftId: String, hidden: Boolean) =
        call("PATCH", "/api/gifts/$giftId", buildJsonObject { put("hidden", hidden) }, OwnedGift.serializer())
    suspend fun setGiftPinned(giftId: String, pinned: Boolean) =
        call("PATCH", "/api/gifts/$giftId", buildJsonObject { put("pinned", pinned) }, OwnedGift.serializer())
    /** Выставить подарок на продажу другим за [price] FLUX; null — снять с продажи. */
    suspend fun setGiftResale(giftId: String, price: Long?) =
        call("PATCH", "/api/gifts/$giftId", buildJsonObject { put("resalePrice", price) }, OwnedGift.serializer())
    suspend fun giftMarket() = call("GET", "/api/gifts/market", null, ListSerializer(OwnedGift.serializer()))
    suspend fun buyResaleGift(giftId: String, price: Long) =
        call("POST", "/api/gifts/$giftId/buy", buildJsonObject { put("price", price) }, OwnedGift.serializer())
    suspend fun setMusic(fileId: String?, title: String) =
        call("PATCH", "/api/me", buildJsonObject { put("musicFileId", fileId); put("musicTitle", title) }, User.serializer())
    suspend fun setFeaturedChannel(chatId: String?) = call("PATCH", "/api/me", buildJsonObject { put("featuredChatId", chatId) }, User.serializer())
    suspend fun addBot(chatId: String, botId: String) = call("POST", "/api/chats/$chatId/bots", buildJsonObject { put("botId", botId) }, Chat.serializer())
    suspend fun setCallPrivacy(value: String) = call("PATCH", "/api/me", buildJsonObject { put("callPrivacy", value) }, User.serializer())
    suspend fun setMessagePrice(price: Int) = call("PATCH", "/api/me", buildJsonObject { put("messagePrice", price) }, User.serializer())
    suspend fun grantFlux(userId: String, amount: Long, note: String) =
        call("POST", "/api/admin/users/$userId/flux", buildJsonObject { put("amount", amount); put("note", note) }, User.serializer())
    suspend fun adminGiftItems() = call("GET", "/api/admin/gift-items", null, ListSerializer(GiftItem.serializer()))
    suspend fun createGiftItem(title: String, description: String, fileId: String, price: Long, supply: Int?, caption: String = "", animation: String = "none") =
        call("POST", "/api/admin/gift-items", buildJsonObject {
            put("title", title); put("description", description); put("fileId", fileId); put("price", price); put("supply", supply)
            put("caption", caption); put("animation", animation)
        }, GiftItem.serializer())
    suspend fun deleteGiftItem(id: String) = callUnit("DELETE", "/api/admin/gift-items/$id")
    suspend fun updateGiftItem(id: String, active: Boolean? = null, price: Long? = null) =
        call("PATCH", "/api/admin/gift-items/$id", buildJsonObject {
            if (active != null) put("active", active)
            if (price != null) put("price", price)
        }, GiftItem.serializer())

    // ---------- модерация ----------

    private fun q(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    suspend fun adminUsers(query: String, all: Boolean = false) =
        call("GET", "/api/admin/users?q=" + q(query) + (if (all) "&all=1" else ""), null, ListSerializer(User.serializer()))
    suspend fun banUser(userId: String, days: Double, reason: String) =
        call("POST", "/api/admin/users/$userId/ban", buildJsonObject { put("days", days); put("reason", reason) }, User.serializer())
    suspend fun unbanUser(userId: String) = call("DELETE", "/api/admin/users/$userId/ban", null, User.serializer())
    suspend fun restrictUser(userId: String, days: Double, reason: String) =
        call("POST", "/api/admin/users/$userId/restrict", buildJsonObject { put("days", days); put("reason", reason) }, User.serializer())
    suspend fun unrestrictUser(userId: String) = call("DELETE", "/api/admin/users/$userId/restrict", null, User.serializer())
    suspend fun setUserVerified(userId: String, verified: Boolean) =
        call("PUT", "/api/admin/users/$userId/verified", buildJsonObject { put("verified", verified) }, User.serializer())
    suspend fun pinMessage(chatId: String, messageId: String?) =
        call("PUT", "/api/chats/$chatId/pin", buildJsonObject { put("messageId", messageId) }, Chat.serializer())
    suspend fun setChatVerified(chatId: String, verified: Boolean) =
        call("PUT", "/api/admin/chats/$chatId/verified", buildJsonObject { put("verified", verified) }, AdminChat.serializer())
    suspend fun adminChats(query: String, all: Boolean = false, type: String? = null) =
        call("GET", "/api/admin/chats?q=" + q(query) + (if (all) "&all=1" else "") + (type?.let { "&type=$it" } ?: ""), null, ListSerializer(AdminChat.serializer()))
    suspend fun banChat(chatId: String, reason: String) =
        call("POST", "/api/admin/chats/$chatId/ban", buildJsonObject { put("reason", reason) }, AdminChat.serializer())
    suspend fun unbanChat(chatId: String) = call("DELETE", "/api/admin/chats/$chatId/ban", null, AdminChat.serializer())
    suspend fun deleteChatAsAdmin(chatId: String, reason: String) =
        call("DELETE", "/api/admin/chats/$chatId", buildJsonObject { put("reason", reason) }, JsonObject.serializer())
    suspend fun serverStatus() = call("GET", "/api/admin/server", null, ServerStatus.serializer())
    suspend fun updateServer() = call("POST", "/api/admin/server/update", null, ServerUpdateResult.serializer())
    suspend fun massReset(flux: Boolean, admins: Boolean, verified: Boolean, badges: Boolean, confirm: String) =
        call("POST", "/api/admin/reset", buildJsonObject {
            put("flux", flux); put("admins", admins); put("verified", verified); put("badges", badges); put("confirm", confirm)
        }, MassResetResult.serializer())
    suspend fun moderationLog() = call("GET", "/api/admin/log", null, ListSerializer(ModerationLogEntry.serializer()))
    suspend fun deleteUserAsAdmin(userId: String, reason: String) =
        call("POST", "/api/admin/users/$userId/delete", buildJsonObject { put("deleteMessages", true); put("reason", reason) }, AdminDeleteResult.serializer())
    suspend fun deviceBanUser(userId: String, reason: String, banDevice: Boolean, banIp: Boolean) =
        call("POST", "/api/admin/users/$userId/device-ban", buildJsonObject {
            put("reason", reason); put("banDevice", banDevice); put("banIp", banIp)
        }, DeviceBanResult.serializer())
    suspend fun deviceBans() = call("GET", "/api/admin/device-bans", null, ListSerializer(DeviceBan.serializer()))
    suspend fun removeDeviceBan(id: String) = call("DELETE", "/api/admin/device-bans/$id", null, JsonObject.serializer())

    suspend fun setAdmin(userId: String, isAdmin: Boolean) =
        call("PUT", "/api/admin/users/$userId/admin", buildJsonObject { put("isAdmin", isAdmin) }, User.serializer())
}
