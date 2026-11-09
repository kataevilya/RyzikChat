package app.ryzik.chat.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
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
    private const val PREFS = "updates"

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun currentVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    fun currentVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

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
        _state.value = UpdateState.Downloading(info, 0f)
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "RyzikChat-${info.versionName}.apk")
        val result = withContext(Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url(info.apk).build()).execute().use { r ->
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

    /** Открывает установщик. Если Android ещё не разрешил ставить приложения — сначала ведёт в настройки. */
    fun install(context: Context) {
        val ready = _state.value as? UpdateState.Ready ?: return
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", ready.file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
