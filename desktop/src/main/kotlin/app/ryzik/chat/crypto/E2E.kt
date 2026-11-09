package app.ryzik.chat.crypto

import com.google.crypto.tink.subtle.Hkdf
import com.google.crypto.tink.subtle.X25519
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Сквозное шифрование RyzikChat.
 *
 * - У каждого пользователя пара ключей X25519. Публичный ключ лежит на сервере,
 *   приватный — только на устройстве (и на сервере в виде, зашифрованном ключом из пароля,
 *   чтобы можно было войти с нового телефона; сервер этот ключ расшифровать не может,
 *   потому что получает от пароля только отдельный «ключ входа»).
 * - Каждое сообщение шифруется случайным ключом AES-256-GCM. Этот ключ упаковывается
 *   для каждого участника чата: X25519(одноразовый ключ, ключ участника) → HKDF → AES-GCM.
 * - Файлы шифруются потоково: AES-256-CTR + HMAC-SHA256 (как вложения в WhatsApp),
 *   ключ файла передаётся внутри зашифрованного сообщения.
 */
object E2E {
    private val random = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true }

    fun b64(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)
    fun unb64(s: String): ByteArray = java.util.Base64.getDecoder().decode(s)
    fun randomBytes(n: Int) = ByteArray(n).also { random.nextBytes(it) }

    // ---------- Ключи пользователя ----------

    class KeyPair(val privateKey: ByteArray, val publicKey: ByteArray)

    fun generateKeyPair(): KeyPair {
        val priv = X25519.generatePrivateKey()
        return KeyPair(priv, X25519.publicFromPrivate(priv))
    }

    fun publicFromPrivate(priv: ByteArray): ByteArray = X25519.publicFromPrivate(priv)

    /** Ключ входа (уходит на сервер вместо пароля) и ключ для приватного ключа (не покидает устройство). */
    class PasswordKeys(val authKey: String, val vaultKey: ByteArray)

    fun derivePasswordKeys(username: String, password: String): PasswordKeys {
        val salt = "ryzikchat:v1:${username.lowercase()}".toByteArray()
        val spec = PBEKeySpec(password.toCharArray(), salt, 150_000, 512)
        val master = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return PasswordKeys(
            authKey = master.copyOfRange(0, 32).joinToString("") { "%02x".format(it) },
            vaultKey = master.copyOfRange(32, 64),
        )
    }

    fun sealPrivateKey(priv: ByteArray, vaultKey: ByteArray): String = b64(aesGcmEncrypt(vaultKey, priv, AAD_VAULT))

    fun openPrivateKey(sealed: String, vaultKey: ByteArray): ByteArray = aesGcmDecrypt(vaultKey, unb64(sealed), AAD_VAULT)

    /** Короткий «отпечаток» ключа, чтобы собеседники могли сверить его голосом. */
    fun fingerprint(publicKey: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(publicKey)
        return (0 until 12).joinToString(" ") { i ->
            val v = ((d[i * 2].toInt() and 0xff) shl 8) or (d[i * 2 + 1].toInt() and 0xff)
            "%05d".format(v)
        }
    }

    // ---------- Сообщения ----------

    @Serializable
    data class Envelope(
        val v: Int = 1,
        val epk: String,
        val ct: String,
        val k: Map<String, String>,
    )

    /** recipients: userId -> публичный ключ X25519. */
    fun encrypt(plaintext: String, recipients: Map<String, ByteArray>): String {
        val messageKey = randomBytes(32)
        val ct = aesGcmEncrypt(messageKey, plaintext.toByteArray(), AAD_MESSAGE)
        val ephemeral = generateKeyPair()
        val wrapped = recipients.mapValues { (_, pub) ->
            b64(aesGcmEncrypt(wrapKey(ephemeral.privateKey, pub, ephemeral.publicKey), messageKey, AAD_WRAP))
        }
        return json.encodeToString(Envelope.serializer(), Envelope(epk = b64(ephemeral.publicKey), ct = b64(ct), k = wrapped))
    }

    fun decrypt(payload: String, myUserId: String, myPrivateKey: ByteArray): String {
        val env = json.decodeFromString(Envelope.serializer(), payload)
        val wrapped = env.k[myUserId] ?: throw SecurityException("Сообщение зашифровано не для этого аккаунта")
        val epk = unb64(env.epk)
        val messageKey = aesGcmDecrypt(wrapKey(myPrivateKey, epk, epk, X25519.publicFromPrivate(myPrivateKey)), unb64(wrapped), AAD_WRAP)
        return String(aesGcmDecrypt(messageKey, unb64(env.ct), AAD_MESSAGE))
    }

    private fun wrapKey(priv: ByteArray, peerPub: ByteArray, epk: ByteArray): ByteArray =
        wrapKey(priv, peerPub, epk, peerPub)

    private fun wrapKey(priv: ByteArray, peerPub: ByteArray, epk: ByteArray, recipientPub: ByteArray): ByteArray {
        val shared = X25519.computeSharedSecret(priv, peerPub)
        return Hkdf.computeHkdf("HMACSHA256", shared, epk + recipientPub, INFO_WRAP, 32)
    }

    private fun aesGcmEncrypt(key: ByteArray, plain: ByteArray, aad: ByteArray): ByteArray {
        val iv = randomBytes(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(aad)
        return iv + c.doFinal(plain)
    }

    private fun aesGcmDecrypt(key: ByteArray, data: ByteArray, aad: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, data, 0, 12))
        c.updateAAD(aad)
        return c.doFinal(data, 12, data.size - 12)
    }

    // ---------- Файлы ----------

    /** Шифрует поток в файл: [iv 16][шифротекст AES-CTR][HMAC 32]. Возвращает ключ (64 байта, base64). */
    fun encryptFile(input: InputStream, out: OutputStream, onProgress: (Long) -> Unit = {}): String {
        val key = randomBytes(64)
        val iv = randomBytes(16)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, 0, 32, "AES"), IvParameterSpec(iv))
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, 32, 32, "HmacSHA256")) }
        out.write(iv)
        mac.update(iv)
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            val enc = cipher.update(buf, 0, n)
            if (enc != null) {
                out.write(enc)
                mac.update(enc)
            }
            total += n
            onProgress(total)
        }
        cipher.doFinal()?.let { out.write(it); mac.update(it) }
        out.write(mac.doFinal())
        return b64(key)
    }

    /** Проверяет HMAC и расшифровывает файл, скачанный с сервера. */
    fun decryptFile(encrypted: File, keyB64: String, out: OutputStream) {
        val key = unb64(keyB64)
        val len = encrypted.length()
        if (len < 48) throw SecurityException("Файл повреждён")
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, 32, 32, "HmacSHA256")) }
        val buf = ByteArray(64 * 1024)
        encrypted.inputStream().use { input ->
            var left = len - 32
            while (left > 0) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) break
                mac.update(buf, 0, n)
                left -= n
            }
            val expected = ByteArray(32)
            java.io.DataInputStream(input).readFully(expected)
            if (!MessageDigest.isEqual(expected, mac.doFinal())) throw SecurityException("Файл подменён или повреждён")
        }
        encrypted.inputStream().use { input ->
            val iv = ByteArray(16)
            java.io.DataInputStream(input).readFully(iv)
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, 0, 32, "AES"), IvParameterSpec(iv))
            var left = len - 48
            while (left > 0) {
                val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) break
                cipher.update(buf, 0, n)?.let { out.write(it) }
                left -= n
            }
            cipher.doFinal()?.let { out.write(it) }
        }
    }

    private val AAD_MESSAGE = "ryzik/msg/v1".toByteArray()
    private val AAD_WRAP = "ryzik/wrap/v1".toByteArray()
    private val AAD_VAULT = "ryzik/vault/v1".toByteArray()
    private val INFO_WRAP = "ryzik-wrap-v1".toByteArray()
}
