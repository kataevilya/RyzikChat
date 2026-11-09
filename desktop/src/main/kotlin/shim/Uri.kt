package android.net

import java.io.File

/** Аналог android.net.Uri для ПК: ссылка на файл на диске или адрес в интернете. */
class Uri private constructor(private val value: String) {
    val scheme: String? get() = value.substringBefore(':', "").ifEmpty { null }
    val path: String? get() = if (value.startsWith("file:")) File(java.net.URI(value)).path else java.net.URI(value).path
    val lastPathSegment: String? get() = path?.trimEnd('/')?.substringAfterLast('/')
    val host: String? get() = runCatching { java.net.URI(value).host }.getOrNull()
    fun toFile(): File = File(java.net.URI(value))
    override fun toString() = value
    override fun equals(other: Any?) = other is Uri && other.value == value
    override fun hashCode() = value.hashCode()

    companion object {
        fun parse(s: String) = Uri(s)
        fun fromFile(f: File) = Uri(f.toURI().toString())
    }
}
