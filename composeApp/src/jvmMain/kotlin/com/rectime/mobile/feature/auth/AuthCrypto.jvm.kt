package com.rectime.mobile.feature.auth

import java.security.SecureRandom

actual fun secureRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    SecureRandom().nextBytes(bytes)
    return bytes
}
