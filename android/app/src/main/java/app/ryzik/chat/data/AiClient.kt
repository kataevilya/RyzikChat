package app.ryzik.chat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Что сделать с текстом. */
enum class AiAction(val title: String, val emoji: String, val prompt: String) {
    Style(
        "Стиль текста", "✨",
        "Приведи текст в порядок: исправь орфографию, пунктуацию и грамматику, сделай формулировки естественными и аккуратными. Сохрани смысл, язык и обращение. Верни только готовый текст без пояснений.",
    ),
    Emoji(
        "Добавить эмодзи", "😊",
        "Расставь в текст подходящие по смыслу эмодзи. Не меняй слова и смысл, не добавляй больше одного эмодзи подряд и не превращай текст в набор смайликов. Верни только готовый текст.",
    ),
    Shorten(
        "Сократить текст", "✂️",
        "Сократи текст примерно на треть, сохранив главный смысл, факты и тон. Убери повторы и лишние слова. Верни только готовый текст без пояснений.",
    ),
}

/**
 * Клиент любого OpenAI-совместимого API. Ключ хранится только на устройстве
 * и отправляется напрямую на адрес, который указал пользователь.
 */
object AiClient {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Из «https://api.example.com» или «…/v1» делает полный адрес /chat/completions. */
    fun endpoint(raw: String): String {
        val base = raw.trim().trimEnd('/')
        if (base.isEmpty()) return ""
        if (base.endsWith("/chat/completions")) return base
        if (base.endsWith("/v1")) return "$base/chat/completions"
        return "$base/v1/chat/completions"
    }

    suspend fun run(baseUrl: String, key: String, model: String, action: AiAction, text: String): String = withContext(Dispatchers.IO) {
        val url = endpoint(baseUrl)
        if (url.isEmpty()) throw IOException("Укажите адрес API в настройках AI")
        if (key.isBlank()) throw IOException("Укажите API-ключ в настройках AI")
        val body = buildJsonObject {
            put("model", model.ifBlank { "gpt-4o-mini" })
            put("temperature", 0.3)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", "Ты помощник в мессенджере RyzikChat. Отвечай на языке пользователя. ${action.prompt}")
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", text)
                })
            })
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${key.trim()}")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(jsonType))
            .build()
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw IOException("Нет связи с AI-сервисом. Проверьте адрес и интернет", e)
        }
        response.use {
            val raw = it.body?.string().orEmpty()
            val parsed = runCatching { AppJson.parseToJsonElement(raw).jsonObject }.getOrNull()
            if (!it.isSuccessful) {
                val message = parsed?.get("error")?.let { e ->
                    runCatching { e.jsonObject["message"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                }
                throw IOException(
                    when {
                        it.code == 401 || it.code == 403 -> "AI-сервис отклонил ключ. Проверьте API-ключ"
                        it.code == 404 -> "AI-сервис не найден по этому адресу. Проверьте ссылку"
                        it.code == 429 -> "Слишком много запросов к AI. Подождите минуту"
                        !message.isNullOrBlank() -> message
                        else -> "Ошибка AI-сервиса (${it.code})"
                    },
                )
            }
            val content = parsed
                ?.get("choices")?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.contentOrNull
                ?.trim()
            if (content.isNullOrBlank()) throw IOException("AI вернул пустой ответ. Попробуйте ещё раз")
            content
        }
    }

    /** Разбор ошибки ответа, если сервис прислал не JSON. */
    private fun JsonObject.errorText(): String? =
        runCatching { this["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull }.getOrNull()
}
