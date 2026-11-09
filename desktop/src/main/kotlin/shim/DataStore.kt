package androidx.datastore.preferences.core

import app.ryzik.chat.platform.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** Настройки как в DataStore на Android, но в файле settings.json в папке приложения. */
open class Preferences internal constructor(internal val map: Map<String, Any>) {
    class Key<T> internal constructor(val name: String, internal val kind: Char)
    operator fun <T> get(key: Key<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return map[key.name] as T?
    }
}

class MutablePreferences internal constructor(internal val m: MutableMap<String, Any>) : Preferences(m) {
    operator fun <T> set(key: Key<T>, value: T) { m[key.name] = value as Any }
    fun remove(key: Key<*>) { m.remove(key.name) }
}

fun booleanPreferencesKey(name: String) = Preferences.Key<Boolean>(name, 'b')
fun intPreferencesKey(name: String) = Preferences.Key<Int>(name, 'i')
fun floatPreferencesKey(name: String) = Preferences.Key<Float>(name, 'f')
fun longPreferencesKey(name: String) = Preferences.Key<Long>(name, 'l')
fun stringPreferencesKey(name: String) = Preferences.Key<String>(name, 's')

class DesktopDataStore(private val file: File) {
    private val mutex = Mutex()
    private val state = MutableStateFlow(Preferences(load()))
    val data: Flow<Preferences> = state

    private fun load(): Map<String, Any> = runCatching {
        val obj = Json.parseToJsonElement(file.readText()) as JsonObject
        obj.mapNotNull { (k, v) ->
            val tag = k.substringBefore(':'); val name = k.substringAfter(':')
            val p = v.jsonPrimitive
            val value: Any? = when (tag) {
                "b" -> p.booleanOrNull; "i" -> p.intOrNull; "f" -> p.floatOrNull
                "l" -> p.content.toLongOrNull(); else -> p.content
            }
            value?.let { name to it }
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun save(map: Map<String, Any>) {
        val obj = JsonObject(map.map { (k, v) ->
            val tag = when (v) { is Boolean -> "b"; is Int -> "i"; is Float -> "f"; is Long -> "l"; else -> "s" }
            "$tag:$k" to when (v) { is Boolean -> JsonPrimitive(v); is Number -> JsonPrimitive(v); else -> JsonPrimitive(v.toString()) }
        }.toMap())
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(obj.toString())
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    suspend fun edit(block: suspend (MutablePreferences) -> Unit): Preferences = mutex.withLock {
        val m = MutablePreferences(state.value.map.toMutableMap())
        block(m)
        val p = Preferences(m.m.toMap())
        withContext(Dispatchers.IO) { save(p.map) }
        state.value = p
        p
    }

    companion object {
        val main by lazy { DesktopDataStore(File(Platform.dataDir, "settings.json")) }
    }
}

suspend fun DesktopDataStore.edit(block: suspend (MutablePreferences) -> Unit) = this.edit(block)
