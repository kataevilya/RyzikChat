package app.ryzik.chat.crypto

import app.ryzik.chat.platform.Platform
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Прячет приватный ключ на компьютере.
 * На Windows — через DPAPI: расшифровать может только этот пользователь Windows.
 * На других системах — ключом из файла, доступного только владельцу.
 */
object KeyVault {
    private const val DPAPI = "dpapi:"

    fun wrap(secret: ByteArray): String {
        if (Platform.isWindows) runCatching {
            return DPAPI + E2E.b64(com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(secret))
        }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(fileKey(), "AES"))
        return E2E.b64(c.iv + c.doFinal(secret))
    }

    fun unwrap(wrapped: String): ByteArray {
        if (wrapped.startsWith(DPAPI)) return com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(E2E.unb64(wrapped.removePrefix(DPAPI)))
        val data = E2E.unb64(wrapped)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(fileKey(), "AES"), GCMParameterSpec(128, data, 0, 12))
        return c.doFinal(data, 12, data.size - 12)
    }

    private fun fileKey(): ByteArray {
        val f = File(Platform.dataDir, "vault.key")
        if (f.length() == 32L) return f.readBytes()
        val k = E2E.randomBytes(32)
        f.writeBytes(k)
        runCatching {
            f.setReadable(false, false); f.setReadable(true, true)
            f.setWritable(false, false); f.setWritable(true, true)
        }
        return k
    }
}
