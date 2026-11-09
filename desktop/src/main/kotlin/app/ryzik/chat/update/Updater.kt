package app.ryzik.chat.update

import android.content.Context
import app.ryzik.chat.BuildConfig
import app.ryzik.chat.platform.Platform
import app.ryzik.chat.data.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Описание свежей сборки: update.json рядом с APK в релизе ryzikchat-latest на GitHub. */
@Serializable
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val notes: String = "",
    val apk: String = Updater.APK_URL,
    /** Установщик для Windows. */
    val windows: String = Updater.WINDOWS_URL,
    val size: Long = 0,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Обновление по воздуху без участия сервера RyzikChat: приложение само спрашивает GitHub
 * о самой новой сборке, скачивает APK оттуда и открывает установщик Android. Все сборки
 * подписаны одним ключом, поэтому новая встаёт поверх старой.
 */
object Updater {
    private const val BASE = "https://github.com/kataevilya/RyzikChat/releases/download/ryzikchat-latest"
    const val INFO_URL = "$BASE/update.json"
    const val APK_URL = "$BASE/RyzikChat.apk"
    const val WINDOWS_URL = "$BASE/RyzikChat-Setup.exe"
    /** На Linux сами не ставим: открываем страницу релиза, там AppImage и .deb. */
    const val RELEASE_PAGE = "https://github.com/kataevilya/RyzikChat/releases/tag/ryzikchat-latest"
    private const val PREFS = "updates"

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    @Suppress("UNUSED_PARAMETER")
    fun currentVersionCode(context: Context): Long = BuildConfig.VERSION_CODE.toLong()

    @Suppress("UNUSED_PARAMETER")
    fun currentVersionName(context: Context): String = BuildConfig.VERSION_NAME

    /** Проверить, есть ли новая версия. silent — не показывать «у вас последняя версия». */
    suspend fun check(context: Context, silent: Boolean = true) {
        val s = _state.value
        if (s is UpdateState.Downloading || s is UpdateState.Ready) return
        if (!silent) _state.value = UpdateState.Checking
        val result = withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url(INFO_URL + "?t=" + System.currentTimeMillis()).header("Cache-Control", "no-cache").build()).execute().use { r ->
                    if (!r.isSuccessful) error("GitHub ответил ${r.code}")
                    AppJson.decodeFromString(UpdateInfo.serializer(), r.body!!.string())
                }
            }
        }
        _state.value = result.fold(
            onSuccess = { info -> if (info.versionCode > currentVersionCode(context)) UpdateState.Available(info) else if (silent) UpdateState.Idle else UpdateState.UpToDate },
            onFailure = { if (silent) UpdateState.Idle else UpdateState.Failed(it.message ?: "Не удалось проверить обновления") },
        )
    }

    /** Пользователь нажал «Позже» — не напоминаем об этой версии при запуске. */
    fun dismiss(context: Context, info: UpdateInfo) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("dismissed", info.versionCode).apply()
        _state.value = UpdateState.Idle
    }

    fun wasDismissed(context: Context, info: UpdateInfo) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("dismissed", 0) >= info.versionCode

    suspend fun download(context: Context, info: UpdateInfo) {
        if (!Platform.isWindows) {
            Platform.openUrl(RELEASE_PAGE)
            _state.value = UpdateState.Available(info)
            return
        }
        _state.value = UpdateState.Downloading(info, 0f)
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "RyzikChat-Setup-${info.versionName}.exe")
        val result = withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url(info.windows).build()).execute().use { r ->
                    if (!r.isSuccessful) error("Не удалось скачать обновление (${r.code})")
                    val body = r.body!!
                    val total = body.contentLength().takeIf { it > 0 } ?: info.size.takeIf { it > 0 } ?: -1L
                    body.byteStream().use { input ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            var lastShown = 0f
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val p = done.toFloat() / total
                                    if (p - lastShown >= 0.01f) { lastShown = p; _state.value = UpdateState.Downloading(info, p) }
                                }
                            }
                        }
                    }
                }
                file
            }
        }
        _state.value = result.fold(
            onSuccess = { UpdateState.Ready(info, it) },
            onFailure = { file.delete(); UpdateState.Failed(it.message ?: "Не удалось скачать обновление") },
        )
        if (result.isSuccess) install(context)
    }

    /** Запускает установщик Windows и закрывает приложение: установщик заменит старую версию. */
    @Suppress("UNUSED_PARAMETER")
    fun install(context: Context) {
        val ready = _state.value as? UpdateState.Ready ?: return
        if (Platform.isWindows) {
            runCatching { ProcessBuilder(ready.file.absolutePath).start() }
                .onSuccess { kotlin.system.exitProcess(0) }
                .onFailure { _state.value = UpdateState.Failed("Не удалось запустить установщик: ${it.message}") }
        } else Platform.openFile(ready.file)
    }
}
