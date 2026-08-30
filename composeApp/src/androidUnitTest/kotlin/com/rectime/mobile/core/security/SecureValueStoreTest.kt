package com.rectime.mobile.core.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val KEY = "session_v1"
private const val LEGACY_KEY = "session"

private open class FakeStringStore(initial: Map<String, String> = emptyMap()) : StringStore {
    val values = initial.toMutableMap()
    var putFails = false

    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String): Boolean {
        if (putFails) return false
        values[key] = value
        return true
    }

    override fun remove(key: String): Boolean {
        values.remove(key)
        return true
    }

    override fun clearAll() {
        values.clear()
    }
}

private class FakeCipher : ValueCipher {
    var keyDiscarded = false
    var encryptFails = false
    var decryptFails = false

    override fun encrypt(plaintext: String): String? =
        if (encryptFails) null else "enc($plaintext)"

    override fun decrypt(value: String): String? = when {
        decryptFails -> null
        value.startsWith("enc(") && value.endsWith(")") -> value.removeSurrounding("enc(", ")")
        else -> null
    }

    override fun discardKey() {
        keyDiscarded = true
    }
}

class SecureValueStoreTest {

    @Test
    fun `read decrypts a value that is already encrypted`() {
        val store = FakeStringStore(mapOf(KEY to "enc(payload)"))
        val cipher = FakeCipher()

        assertEquals("payload", SecureValueStore(store, cipher).read(KEY, LEGACY_KEY))
        assertFalse(cipher.keyDiscarded)
    }

    @Test
    fun `read migrates a legacy plaintext value and removes it`() {
        val store = FakeStringStore(mapOf(LEGACY_KEY to "payload"))

        assertEquals("payload", SecureValueStore(store, FakeCipher()).read(KEY, LEGACY_KEY))
        assertEquals("enc(payload)", store.values[KEY])
        assertFalse(store.values.containsKey(LEGACY_KEY))
    }

    @Test
    fun `read removes a leftover legacy value once the encrypted one exists`() {
        val store = FakeStringStore(mapOf(KEY to "enc(payload)", LEGACY_KEY to "payload"))

        assertEquals("payload", SecureValueStore(store, FakeCipher()).read(KEY, LEGACY_KEY))
        assertFalse(store.values.containsKey(LEGACY_KEY))
    }

    @Test
    fun `read fails closed when the value cannot be decrypted`() {
        val store = FakeStringStore(mapOf(KEY to "enc(payload)", "other" to "kept"))
        val cipher = FakeCipher().apply { decryptFails = true }

        assertNull(SecureValueStore(store, cipher).read(KEY, LEGACY_KEY))
        assertTrue(store.values.isEmpty())
        assertTrue(cipher.keyDiscarded)
    }

    @Test
    fun `read fails closed when migration cannot encrypt`() {
        val store = FakeStringStore(mapOf(LEGACY_KEY to "payload"))
        val cipher = FakeCipher().apply { encryptFails = true }

        assertNull(SecureValueStore(store, cipher).read(KEY, LEGACY_KEY))
        assertTrue(store.values.isEmpty())
        assertTrue(cipher.keyDiscarded)
    }

    @Test
    fun `read fails closed when migration cannot be written`() {
        val store = FakeStringStore(mapOf(LEGACY_KEY to "payload")).apply { putFails = true }
        val cipher = FakeCipher()

        assertNull(SecureValueStore(store, cipher).read(KEY, LEGACY_KEY))
        assertTrue(store.values.isEmpty())
        assertTrue(cipher.keyDiscarded)
    }

    @Test
    fun `read fails closed when the readback does not match`() {
        val store = FakeStringStore(mapOf(LEGACY_KEY to "payload"))
        val cipher = object : ValueCipher by FakeCipher() {
            override fun encrypt(plaintext: String): String = "enc(corrupted)"
        }

        assertNull(SecureValueStore(store, cipher).read(KEY, LEGACY_KEY))
        assertTrue(store.values.isEmpty())
    }

    @Test
    fun `read returns null when nothing is stored`() {
        assertNull(SecureValueStore(FakeStringStore(), FakeCipher()).read(KEY, LEGACY_KEY))
    }

    @Test
    fun `write encrypts and drops the legacy value`() {
        val store = FakeStringStore(mapOf(LEGACY_KEY to "old"))

        SecureValueStore(store, FakeCipher()).write(KEY, LEGACY_KEY, "payload")

        assertEquals("enc(payload)", store.values[KEY])
        assertFalse(store.values.containsKey(LEGACY_KEY))
    }

    @Test
    fun `write fails closed when encryption is unavailable`() {
        val store = FakeStringStore(mapOf(LEGACY_KEY to "old"))
        val cipher = FakeCipher().apply { encryptFails = true }

        SecureValueStore(store, cipher).write(KEY, LEGACY_KEY, "payload")

        assertTrue(store.values.isEmpty())
        assertTrue(cipher.keyDiscarded)
    }

    @Test
    fun `remove drops both the encrypted and the legacy value`() {
        val store = FakeStringStore(mapOf(KEY to "enc(payload)", LEGACY_KEY to "payload"))

        assertTrue(SecureValueStore(store, FakeCipher()).remove(KEY, LEGACY_KEY))
        assertTrue(store.values.isEmpty())
    }

    @Test
    fun `remove falls back to wiping everything when a delete fails`() {
        val store = object : FakeStringStore(mapOf(KEY to "enc(payload)", LEGACY_KEY to "payload")) {
            override fun remove(key: String): Boolean = false
        }

        assertTrue(SecureValueStore(store, FakeCipher()).remove(KEY, LEGACY_KEY))
        assertTrue(store.values.isEmpty())
    }

    @Test
    fun `remove reports failure when the value survives the wipe`() {
        val store = object : FakeStringStore(mapOf(KEY to "enc(payload)", LEGACY_KEY to "payload")) {
            override fun remove(key: String): Boolean = false
            override fun clearAll() = Unit
        }

        assertFalse(SecureValueStore(store, FakeCipher()).remove(KEY, LEGACY_KEY))
        assertEquals("enc(payload)", store.values[KEY])
    }

    @Test
    fun `remove reports failure when the legacy value survives`() {
        val store = object : FakeStringStore(mapOf(KEY to "enc(payload)", LEGACY_KEY to "payload")) {
            override fun remove(key: String): Boolean {
                if (key == LEGACY_KEY) return false
                values.remove(key)
                return true
            }

            override fun clearAll() = Unit
        }

        assertFalse(SecureValueStore(store, FakeCipher()).remove(KEY, LEGACY_KEY))
        assertEquals("payload", store.values[LEGACY_KEY])
    }
}
