package app.ryzik.chat.platform

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Всё, что на ПК делается иначе, чем на телефоне: папки, ссылки, буфер обмена, всплывающие подсказки. */
object Platform {
    val isWindows = System.getProperty("os.name").orEmpty().startsWith("Windows")

    /** Чем рисуется окно: DIRECT3D/OPENGL — видеокарта, SOFTWARE_* — процессор (тогда всё заметно медленнее). */
    @Volatile var renderApi: String = ""

    /** Папка с данными приложения: %APPDATA%\RyzikChat на Windows, ~/.ryzikchat на остальных. */
    val dataDir: File by lazy {
        val base = System.getenv("RYZIKCHAT_DATA")?.let { File(it) }
            ?: System.getenv("APPDATA")?.takeIf { isWindows }?.let { File(it, "RyzikChat") }
            ?: File(System.getProperty("user.home"), ".ryzikchat")
        base.apply { mkdirs() }
    }

    val cacheDir: File by lazy { File(dataDir, "cache").apply { mkdirs() } }

    /** Папка «Загрузки» пользователя. */
    val downloadsDir: File by lazy {
        File(System.getProperty("user.home"), "Downloads").takeIf { it.isDirectory } ?: File(System.getProperty("user.home"))
    }

    fun deviceName(): String {
        val host = runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull().orEmpty()
        val os = System.getProperty("os.name").orEmpty()
        return listOf("ПК", os, host).filter { it.isNotBlank() }.joinToString(" · ").take(100)
    }

    fun openUrl(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }.onFailure {
            if (isWindows) runCatching { Runtime.getRuntime().exec(arrayOf("rundll32", "url.dll,FileProtocolHandler", url)) }
            else runCatching { Runtime.getRuntime().exec(arrayOf("xdg-open", url)) }
        }
    }

    /** Открывает файл программой по умолчанию. */
    fun openFile(file: File): Boolean = runCatching { Desktop.getDesktop().open(file); true }.getOrElse {
        runCatching {
            if (isWindows) Runtime.getRuntime().exec(arrayOf("cmd", "/c", "start", "", file.absolutePath))
            else Runtime.getRuntime().exec(arrayOf("xdg-open", file.absolutePath))
            true
        }.getOrDefault(false)
    }

    fun copy(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }

    /** Картинка или файлы из буфера обмена (для Ctrl+V в чате). null — там обычный текст. */
    fun pastedFiles(): List<File>? = runCatching {
        val cb = Toolkit.getDefaultToolkit().systemClipboard
        val t = cb.getContents(null) ?: return null
        when {
            t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.javaFileListFlavor) ->
                @Suppress("UNCHECKED_CAST") (t.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as List<File>).filter { it.isFile }.takeIf { it.isNotEmpty() }
            t.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.imageFlavor) -> {
                val img = t.getTransferData(java.awt.datatransfer.DataFlavor.imageFlavor) as java.awt.Image
                val w = img.getWidth(null); val h = img.getHeight(null)
                val bi = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                bi.createGraphics().apply { drawImage(img, 0, 0, null); dispose() }
                val f = File(cacheDir, "paste_${System.currentTimeMillis()}.png")
                javax.imageio.ImageIO.write(bi, "png", f)
                listOf(f)
            }
            else -> null
        }
    }.getOrNull()

    /** На ПК «Поделиться» = скопировать в буфер обмена. */
    fun share(text: String) {
        copy(text)
        toast("Скопировано в буфер обмена")
    }

    private val _toast = MutableStateFlow<Pair<Long, String>?>(null)
    val toastFlow: StateFlow<Pair<Long, String>?> = _toast
    fun toast(text: String) { _toast.value = System.nanoTime() to text }
}
