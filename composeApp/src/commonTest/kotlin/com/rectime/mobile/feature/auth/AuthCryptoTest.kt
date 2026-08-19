package com.rectime.mobile.feature.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthCryptoTest {

    @Test
    fun sha256MatchesKnownDigestForEmptyInput() {
        assertEquals(
            "47DEQpj8HBSa-_TImW-5JCeuQeRkm5NMpJWZG3hSuFU",
            sha256(ByteArray(0)).toBase64Url(),
        )
    }

    @Test
    fun sha256MatchesKnownDigestForAsciiInput() {
        assertEquals(
            "ungWv48Bz-pBQUDeXa4iI7ADYaOWF3qctBD_YfIAFa0",
            sha256("abc".encodeToByteArray()).toBase64Url(),
        )
    }

    @Test
    fun sha256MatchesKnownDigestForInputLongerThanOneBlock() {
        val input = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"
        assertEquals(
            "JI1qYdIGOLjlwCaTDD5gOaM85Flk_yFn9uzt1BnbBsE",
            sha256(input.encodeToByteArray()).toBase64Url(),
        )
    }

    @Test
    fun generateCodeChallengeMatchesRfc7636ExampleVector() {
        val codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            generateCodeChallenge(codeVerifier),
        )
    }

    @Test
    fun toBase64UrlOmitsPaddingForEveryRemainderLength() {
        assertEquals("AQ", byteArrayOf(1).toBase64Url())
        assertEquals("AQI", byteArrayOf(1, 2).toBase64Url())
        assertEquals("AQID", byteArrayOf(1, 2, 3).toBase64Url())
        assertEquals("", ByteArray(0).toBase64Url())
    }

    @Test
    fun toBase64UrlUsesUrlSafeAlphabetForBytesThatWouldEncodeToPlusAndSlash() {
        assertEquals("-_8", byteArrayOf(0xfb.toByte(), 0xff.toByte()).toBase64Url())
        assertEquals("_-8", byteArrayOf(0xff.toByte(), 0xef.toByte()).toBase64Url())
    }

    @Test
    fun generateBase64UrlRandomProducesUrlSafeValueOfExpectedLength() {
        val value = generateBase64UrlRandom(32)

        assertEquals(43, value.length)
        assertTrue(value.all { it.isLetterOrDigit() || it == '-' || it == '_' }, value)
    }

    @Test
    fun generateBase64UrlRandomProducesDifferentValuesOnEachCall() {
        val values = List(20) { generateBase64UrlRandom(32) }

        assertEquals(values.size, values.toSet().size)
    }

    @Test
    fun generateCodeChallengeIsDeterministicAndDiffersPerVerifier() {
        val verifier = generateBase64UrlRandom(32)

        assertEquals(generateCodeChallenge(verifier), generateCodeChallenge(verifier))
        assertFalse(generateCodeChallenge(verifier) == generateCodeChallenge(verifier + "x"))
    }
}
