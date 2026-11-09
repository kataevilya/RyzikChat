package android.content

import android.net.Uri
import app.ryzik.chat.platform.Platform
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File

/**
 * Небольшая замена android.content.Context для ПК: папки, настройки и «открыть ссылку/файл».
 * Нужна, чтобы экраны из Android-приложения работали почти без изменений.
 */
open class Context {
    val cacheDir: File get() = Platform.cacheDir
    val filesDir: File get() = Platform.dataDir
    val packageName: String = "app.ryzik.chat"
    val resources = Resources

    object Resources {
        object displayMetrics { const val density = 1f }
    }

    fun getSharedPreferences(name: String, @Suppress("UNUSED_PARAMETER") mode: Int): SharedPreferences = SharedPreferences.of(name)

    /** ACTION_VIEW открывает ссылку или файл, ACTION_SEND копирует текст. */
    fun startActivity(intent: Intent) {
        val i = intent.chooserTarget ?: intent
        when (i.action) {
            Intent.ACTION_VIEW -> i.data?.let { u ->
                if (u.scheme == "file") Platform.openFile(u.toFile()) else Platform.openUrl(u.toString())
            }
            Intent.ACTION_SEND -> (i.extras[Intent.EXTRA_TEXT] as? String)?.let { Platform.share(it) }
        }
    }

    companion object {
        const val MODE_PRIVATE = 0
        const val CLIPBOARD_SERVICE = "clipboard"
        val instance = Context()
    }
}

class Intent(var action: String? = null, var data: Uri? = null) {
    internal var chooserTarget: Intent? = null
    internal val extras = mutableMapOf<String, Any?>()
    var type: String? = null
    fun setType(t: String) = apply { type = t }
    fun setData(u: Uri) = apply { data = u }
    fun setDataAndType(u: Uri, t: String) = apply { data = u; type = t }
    fun putExtra(k: String, v: String?) = apply { extras[k] = v }
    fun putExtra(k: String, v: Long) = apply { extras[k] = v }
    fun putExtra(k: String, v: Boolean) = apply { extras[k] = v }
    fun addFlags(@Suppress("UNUSED_PARAMETER") f: Int) = this
    fun getStringExtra(k: String) = extras[k] as? String

    companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
        const val EXTRA_STREAM = "android.intent.extra.STREAM"
        const val FLAG_ACTIVITY_NEW_TASK = 0
        const val FLAG_GRANT_READ_URI_PERMISSION = 0
        fun createChooser(target: Intent, @Suppress("UNUSED_PARAMETER") title: CharSequence?) = Intent("chooser").also { it.chooserTarget = target }
    }
}

/** Простые настройки «ключ — значение» в отдельном json-файле. */
class SharedPreferences private constructor(private val file: File) {
    private val map: MutableMap<String, String> = runCatching {
        kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(file.readText()).toMutableMap()
    }.getOrDefault(mutableMapOf())

    fun getLong(k: String, d: Long) = map[k]?.toLongOrNull() ?: d
    fun getInt(k: String, d: Int) = map[k]?.toIntOrNull() ?: d
    fun getBoolean(k: String, d: Boolean) = map[k]?.toBooleanStrictOrNull() ?: d
    fun getString(k: String, d: String?) = map[k] ?: d
    fun getStringSet(k: String, d: Set<String>?): Set<String>? =
        map[k]?.let { kotlinx.serialization.json.Json.decodeFromString<List<String>>(it).toSet() } ?: d
    fun contains(k: String) = map.containsKey(k)

    inner class Editor {
        fun putLong(k: String, v: Long) = apply { map[k] = v.toString() }
        fun putInt(k: String, v: Int) = apply { map[k] = v.toString() }
        fun putBoolean(k: String, v: Boolean) = apply { map[k] = v.toString() }
        fun putString(k: String, v: String?) = apply { if (v == null) map.remove(k) else map[k] = v }
        fun putStringSet(k: String, v: Set<String>?) = apply {
            if (v == null) map.remove(k) else map[k] = kotlinx.serialization.json.Json.encodeToString(v.toList())
        }
        fun remove(k: String) = apply { map.remove(k) }
        fun apply() = synchronized(this@SharedPreferences) { file.writeText(kotlinx.serialization.json.Json.encodeToString(map.toMap())) }
        fun commit(): Boolean { apply(); return true }
    }

    fun edit() = Editor()

    companion object {
        private val cache = mutableMapOf<String, SharedPreferences>()
        fun of(name: String) = synchronized(cache) { cache.getOrPut(name) { SharedPreferences(File(Platform.dataDir, "prefs_$name.json")) } }
    }
}
