package androidx.activity.result.contract

import android.net.Uri
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Выбор файлов на ПК вместо системных окон Android. */
abstract class ActivityResultContract<I, O> {
    abstract fun run(input: I): O
}

object DesktopFiles {
    /** Главное окно: диалоги выбора файлов открываются поверх него. */
    @Volatile var window: Frame? = null

    val images = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
    val videos = setOf("mp4", "mov", "webm", "mkv", "avi", "m4v")

    fun choose(title: String, multiple: Boolean, exts: Set<String>?): List<File> {
        val d = FileDialog(window, title, FileDialog.LOAD)
        d.isMultipleMode = multiple
        if (exts != null) {
            d.setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in exts }
            // Windows не понимает FilenameFilter: маска в поле имени файла.
            if (System.getProperty("os.name").startsWith("Windows")) d.file = exts.joinToString(";") { "*.$it" }
        }
        d.isVisible = true
        return d.files?.toList().orEmpty()
    }

    fun save(title: String, name: String): File? {
        val d = FileDialog(window, title, FileDialog.SAVE)
        d.file = name
        d.isVisible = true
        val dir = d.directory ?: return null
        val f = d.file ?: return null
        return File(dir, f)
    }
}

object ActivityResultContracts {
    class PickVisualMedia : ActivityResultContract<androidx.activity.result.PickVisualMediaRequest, Uri?>() {
        sealed interface VisualMediaType
        object ImageOnly : VisualMediaType
        object VideoOnly : VisualMediaType
        object ImageAndVideo : VisualMediaType

        override fun run(input: androidx.activity.result.PickVisualMediaRequest): Uri? =
            DesktopFiles.choose(input.title(), false, input.exts()).firstOrNull()?.let { Uri.fromFile(it) }
    }

    class PickMultipleVisualMedia(val max: Int = 10) : ActivityResultContract<androidx.activity.result.PickVisualMediaRequest, List<Uri>>() {
        override fun run(input: androidx.activity.result.PickVisualMediaRequest): List<Uri> =
            DesktopFiles.choose(input.title(), true, input.exts()).take(max).map { Uri.fromFile(it) }
    }

    class OpenDocument : ActivityResultContract<Array<String>, Uri?>() {
        override fun run(input: Array<String>): Uri? = DesktopFiles.choose("Выберите файл", false, null).firstOrNull()?.let { Uri.fromFile(it) }
    }

    class OpenMultipleDocuments : ActivityResultContract<Array<String>, List<Uri>>() {
        override fun run(input: Array<String>): List<Uri> = DesktopFiles.choose("Выберите файлы", true, null).map { Uri.fromFile(it) }
    }

    class GetContent : ActivityResultContract<String, Uri?>() {
        override fun run(input: String): Uri? = DesktopFiles.choose("Выберите файл", false, null).firstOrNull()?.let { Uri.fromFile(it) }
    }

    /** На ПК разрешения не нужны: всегда «разрешено». */
    class RequestPermission : ActivityResultContract<String, Boolean>() {
        override fun run(input: String) = true
    }

    class RequestMultiplePermissions : ActivityResultContract<Array<String>, Map<String, Boolean>>() {
        override fun run(input: Array<String>) = input.associateWith { true }
    }
}
