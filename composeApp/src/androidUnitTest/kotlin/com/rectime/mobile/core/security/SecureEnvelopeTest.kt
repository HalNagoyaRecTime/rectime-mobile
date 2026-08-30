package com.rectime.mobile.core.security

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SecureEnvelopeTest {

    private val nonce = ByteArray(SECURE_ENVELOPE_NONCE_SIZE) { it.toByte() }
    private val ciphertext = ByteArray(24) { (it + 100).toByte() }

    @Test
    fun `pack and unpack round trips nonce and ciphertext`() {
        val envelope = unpackSecureEnvelope(packSecureEnvelope(nonce, ciphertext))

        assertEquals(SECURE_ENVELOPE_VERSION, envelope?.version)
        assertContentEquals(nonce, envelope?.nonce)
        assertContentEquals(ciphertext, envelope?.ciphertext)
    }

    @Test
    fun `packed value carries the version in the first byte`() {
        val blob = Base64.getDecoder().decode(packSecureEnvelope(nonce, ciphertext))

        assertEquals(SECURE_ENVELOPE_VERSION, blob[0].toInt())
    }

    @Test
    fun `unpack rejects an unknown version`() {
        val blob = Base64.getDecoder().decode(packSecureEnvelope(nonce, ciphertext))
        blob[0] = (SECURE_ENVELOPE_VERSION + 1).toByte()

        assertNull(unpackSecureEnvelope(Base64.getEncoder().encodeToString(blob)))
    }

    @Test
    fun `unpack rejects a truncated blob`() {
        val blob = Base64.getDecoder().decode(packSecureEnvelope(nonce, ciphertext))
        val truncated = blob.copyOfRange(0, SECURE_ENVELOPE_NONCE_SIZE)

        assertNull(unpackSecureEnvelope(Base64.getEncoder().encodeToString(truncated)))
    }

    @Test
    fun `unpack rejects a value that is not base64`() {
        assertNull(unpackSecureEnvelope("not base64 at all !!"))
    }

    @Test
    fun `unpack rejects the legacy plaintext format`() {
        assertNull(unpackSecureEnvelope("dG9rZW4.cmVmcmVzaA.MzYwMA"))
    }

    @Test
    fun `pack rejects a nonce of the wrong size`() {
        assertFailsWith<IllegalArgumentException> {
            packSecureEnvelope(ByteArray(SECURE_ENVELOPE_NONCE_SIZE - 1), ciphertext)
        }
    }
}
