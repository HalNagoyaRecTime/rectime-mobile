package com.rectime.mobile.core.security

import java.util.Base64

internal const val SECURE_ENVELOPE_VERSION = 1
internal const val SECURE_ENVELOPE_NONCE_SIZE = 12

private const val GCM_TAG_SIZE = 16
private const val HEADER_SIZE = 1
private const val MIN_BLOB_SIZE = HEADER_SIZE + SECURE_ENVELOPE_NONCE_SIZE + GCM_TAG_SIZE

internal class SecureEnvelope(
    val version: Int,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
)

internal fun packSecureEnvelope(nonce: ByteArray, ciphertext: ByteArray): String {
    require(nonce.size == SECURE_ENVELOPE_NONCE_SIZE) { "unexpected nonce size" }
    require(ciphertext.size >= GCM_TAG_SIZE) { "unexpected ciphertext size" }

    val blob = ByteArray(HEADER_SIZE + nonce.size + ciphertext.size)
    blob[0] = SECURE_ENVELOPE_VERSION.toByte()
    nonce.copyInto(blob, HEADER_SIZE)
    ciphertext.copyInto(blob, HEADER_SIZE + nonce.size)
    return Base64.getEncoder().encodeToString(blob)
}

internal fun unpackSecureEnvelope(value: String): SecureEnvelope? {
    val blob = runCatching { Base64.getDecoder().decode(value) }.getOrNull() ?: return null
    if (blob.size < MIN_BLOB_SIZE) return null

    val version = blob[0].toInt() and 0xff
    if (version != SECURE_ENVELOPE_VERSION) return null

    return SecureEnvelope(
        version = version,
        nonce = blob.copyOfRange(HEADER_SIZE, HEADER_SIZE + SECURE_ENVELOPE_NONCE_SIZE),
        ciphertext = blob.copyOfRange(HEADER_SIZE + SECURE_ENVELOPE_NONCE_SIZE, blob.size),
    )
}
