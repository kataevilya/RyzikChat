package app.ryzik.chat.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class E2ETest {
    private fun assertFails(block: () -> Unit) {
        try { block() } catch (_: Exception) { return }
        fail("Ожидалась ошибка")
    }

    @Test fun messageRoundTrip() {
        val a = E2E.generateKeyPair(); val b = E2E.generateKeyPair(); val c = E2E.generateKeyPair()
        val payload = E2E.encrypt("Привет 👋", mapOf("a" to a.publicKey, "b" to b.publicKey))
        assertEquals("Привет 👋", E2E.decrypt(payload, "a", a.privateKey))
        assertEquals("Привет 👋", E2E.decrypt(payload, "b", b.privateKey))
        assertFails { E2E.decrypt(payload, "b", c.privateKey) }
        assertFails { E2E.decrypt(payload, "c", c.privateKey) }
    }

    @Test fun vault() {
        val k = E2E.derivePasswordKeys("Alice", "secret123")
        val k2 = E2E.derivePasswordKeys("alice", "secret123")
        assertEquals(k.authKey, k2.authKey)
        val kp = E2E.generateKeyPair()
        val sealed = E2E.sealPrivateKey(kp.privateKey, k.vaultKey)
        assertArrayEquals(kp.privateKey, E2E.openPrivateKey(sealed, k2.vaultKey))
        assertFails { E2E.openPrivateKey(sealed, E2E.derivePasswordKeys("alice", "wrong").vaultKey) }
    }

    @Test fun fileRoundTripAndTamper() {
        val data = ByteArray(300_001) { (it * 31).toByte() }
        val enc = File.createTempFile("enc", ".bin")
        val key = enc.outputStream().use { E2E.encryptFile(data.inputStream(), it) }
        val out = ByteArrayOutputStream()
        E2E.decryptFile(enc, key, out)
        assertArrayEquals(data, out.toByteArray())
        val bytes = enc.readBytes(); bytes[100] = (bytes[100] + 1).toByte(); enc.writeBytes(bytes)
        assertFails { E2E.decryptFile(enc, key, ByteArrayOutputStream()) }
    }
}
