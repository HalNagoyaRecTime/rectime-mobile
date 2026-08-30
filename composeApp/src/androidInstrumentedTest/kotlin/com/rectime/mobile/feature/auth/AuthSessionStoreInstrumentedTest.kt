package com.rectime.mobile.feature.auth

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.rectime.mobile.core.platform.initializePlatformContext
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PREFS_NAME = "rectime_auth"
private const val SESSION_KEY = "session_v1"
private const val PENDING_AUTH_KEY = "pending_auth_v1"
private const val LEGACY_SESSION_KEY = "session"
private const val LEGACY_PENDING_AUTH_KEY = "pending_auth"

private const val ACCESS_TOKEN = "access-token-9f2a41c8"
private const val REFRESH_TOKEN_ID = "refresh-token-id-73bd05e6"
private const val CODE_VERIFIER = "code-verifier-5c1e88af"
private const val STUDENT_ID = "55123"
private const val EMAIL = "student@example.com"

class AuthSessionStoreInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val store = AuthSessionStore()

    private val session = AuthSession(
        accessToken = ACCESS_TOKEN,
        refreshTokenId = REFRESH_TOKEN_ID,
        expiresIn = 3600L,
        user = AuthUser(
            id = "user-1",
            email = EMAIL,
            displayName = "テスト 学生",
            studentIdNumber = STUDENT_ID,
            classRoomName = "IA12A203",
            role = Role.Student,
        ),
    )

    private val pending = PendingAuth(state = "state-4471aa", codeVerifier = CODE_VERIFIER)

    @BeforeTest
    fun setUp() {
        initializePlatformContext(context)
        prefs.edit().clear().commit()
    }

    @Test
    fun savedSessionIsStoredAsVersionedCiphertext() = runBlocking {
        store.save(session)

        val stored = assertNotNull(prefs.getString(SESSION_KEY, null))
        val blob = Base64.getDecoder().decode(stored)

        assertEquals(1, blob[0].toInt())
        assertTrue(blob.size >= 1 + 12 + 16)
        assertNotEqualNonces(stored)
    }

    @Test
    fun savedSessionDoesNotLeakSecretsIntoPreferences() = runBlocking {
        store.save(session)
        store.savePendingAuth(pending)

        val dump = prefs.all.entries.joinToString { "${it.key}=${it.value}" }

        assertFalse(dump.contains(ACCESS_TOKEN))
        assertFalse(dump.contains(REFRESH_TOKEN_ID))
        assertFalse(dump.contains(CODE_VERIFIER))
        assertFalse(dump.contains(STUDENT_ID))
        assertFalse(dump.contains(EMAIL))
        assertFalse(dump.contains(encodeAuthSession(session)))
        assertFalse(dump.contains(encodePendingAuth(pending)))
    }

    @Test
    fun savedSessionRoundTripsThroughTheRealKeystore() = runBlocking {
        store.save(session)
        store.savePendingAuth(pending)

        assertEquals(session, store.load())
        assertEquals(pending, store.loadPendingAuth())
    }

    @Test
    fun legacyPlaintextIsMigratedAndRemoved() = runBlocking {
        prefs.edit()
            .putString(LEGACY_SESSION_KEY, encodeAuthSession(session))
            .putString(LEGACY_PENDING_AUTH_KEY, encodePendingAuth(pending))
            .commit()

        assertEquals(session, store.load())
        assertEquals(pending, store.loadPendingAuth())

        assertFalse(prefs.contains(LEGACY_SESSION_KEY))
        assertFalse(prefs.contains(LEGACY_PENDING_AUTH_KEY))
        assertNotNull(prefs.getString(SESSION_KEY, null))
        assertNotNull(prefs.getString(PENDING_AUTH_KEY, null))

        val dump = prefs.all.entries.joinToString { "${it.key}=${it.value}" }
        assertFalse(dump.contains(ACCESS_TOKEN))
        assertFalse(dump.contains(CODE_VERIFIER))
    }

    @Test
    fun migratedSessionSurvivesAnInterruptedMigration() = runBlocking {
        store.save(session)
        prefs.edit().putString(LEGACY_SESSION_KEY, encodeAuthSession(session)).commit()

        assertEquals(session, store.load())
        assertFalse(prefs.contains(LEGACY_SESSION_KEY))
    }

    @Test
    fun corruptedCiphertextDiscardsEverything() = runBlocking {
        store.save(session)
        store.savePendingAuth(pending)

        val blob = Base64.getDecoder().decode(assertNotNull(prefs.getString(SESSION_KEY, null)))
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()
        prefs.edit().putString(SESSION_KEY, Base64.getEncoder().encodeToString(blob)).commit()

        assertNull(store.load())
        assertTrue(prefs.all.isEmpty())
        assertNull(store.loadPendingAuth())
    }

    @Test
    fun unknownVersionIsRejected() = runBlocking {
        store.save(session)

        val blob = Base64.getDecoder().decode(assertNotNull(prefs.getString(SESSION_KEY, null)))
        blob[0] = 9
        prefs.edit().putString(SESSION_KEY, Base64.getEncoder().encodeToString(blob)).commit()

        assertNull(store.load())
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun clearRemovesBothCurrentAndLegacyEntries() = runBlocking {
        store.save(session)
        prefs.edit().putString(LEGACY_SESSION_KEY, encodeAuthSession(session)).commit()

        store.clear()

        assertFalse(prefs.contains(SESSION_KEY))
        assertFalse(prefs.contains(LEGACY_SESSION_KEY))
        assertNull(store.load())
    }

    private suspend fun assertNotEqualNonces(first: String) {
        store.save(session)
        val second = assertNotNull(prefs.getString(SESSION_KEY, null))
        assertFalse(first == second, "同じ平文が同じ暗号文になっている（nonceが再利用されている）")
        assertContains(listOf(1), Base64.getDecoder().decode(second)[0].toInt())
    }
}
