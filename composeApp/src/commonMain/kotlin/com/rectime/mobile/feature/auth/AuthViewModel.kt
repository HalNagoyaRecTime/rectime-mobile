package com.rectime.mobile.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.config.isDebugBuild
import com.rectime.mobile.core.platform.openExternalUrl
import com.rectime.mobile.feature.notifications.PushTokenLifecycle
import com.rectime.mobile.feature.notifications.platformPushTokenLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val LOGOUT_FAILED_MESSAGE = "ログアウトに失敗しました"

@OptIn(ExperimentalTime::class)
class AuthViewModel(
    private val api: AuthApi = AuthApi(),
    private val sessionStore: AuthSessionStorage = PlatformAuthSessionStorage(),
    private val cache: LocalCache = LocalCache(),
    val photoRepository: ProfilePhotoRepository? = null,
    private val devAuthBypassEnabled: Boolean = isDevAuthBypassEnabled(),
    private val openUrl: suspend (String) -> Boolean = { openExternalUrl(it) },
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val pushTokenLifecycle: PushTokenLifecycle = platformPushTokenLifecycle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    private val refreshMutex = Mutex()
    private val sessionTransitionMutex = Mutex()
    private var refreshAttemptCount = 0
    private var refreshWindowStartedAt = 0L
    private var photoFetchJob: Job? = null

    init {
        restoreSession()

        viewModelScope.launch {
            AuthDeepLinkHandler.callbacks.collect { callbackUrl ->
                handleCallbackUrl(callbackUrl)
            }
        }
        viewModelScope.launch {
            AuthSessionInvalidationHandler.events.collect { accessToken ->
                refreshAfterUnauthorized(accessToken)
            }
        }
    }

    private fun restoreSession() {
        viewModelScope.launch {
            if (devAuthBypassEnabled) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        session = createDevSession(),
                        message = "DEV_BYPASS_AUTH enabled",
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, message = "Restoring session...") }

            // Restore pending auth so cold-start deep links (process killed mid-flow) still work.
            val storedPending = sessionStore.loadPendingAuth()

            val stored = sessionStore.load()
            if (stored == null) {
                sessionTransitionMutex.withLock {
                    // cold start中にOAuth callbackが先に新Sessionを保存した場合、
                    // 古いrestore処理で新ユーザーの写真を消さない。
                    if (sessionStore.load() == null) {
                        photoFetchJob?.cancel()
                        photoRepository?.clear()
                        _uiState.update {
                            it.copy(isLoading = false, message = "", pendingAuth = storedPending)
                        }
                    }
                }
                return@launch
            }

            sessionTransitionMutex.withLock {
                // callback等でSessionが切り替わっていない場合だけ保存済み写真を復元する。
                if (sessionStore.load()?.refreshTokenId == stored.refreshTokenId) {
                    photoRepository?.restore(stored.user.id)
                }
            }

            try {
                val user = api.currentUser(stored.accessToken)
                val session = stored.copy(user = user)
                sessionTransitionMutex.withLock {
                    if (sessionStore.load()?.refreshTokenId == stored.refreshTokenId) {
                        if (user.id != stored.user.id) {
                            photoFetchJob?.cancel()
                            photoRepository?.clear()
                            photoRepository?.restore(user.id)
                        }
                        sessionStore.save(session)
                        if (storedPending != null && sessionStore.loadPendingAuth() == storedPending) {
                            sessionStore.clearPendingAuth()
                        }
                        pushTokenLifecycle.updateSession(session)
                        _uiState.update { it.copy(isLoading = false, session = session, message = "Logged in") }
                        fetchPhotoIfDue(session)
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                // 一時的な通信・Server障害では保存済みSessionとPKCE情報を維持する。
                if (!error.isUnauthorizedAuthError()) {
                    sessionTransitionMutex.withLock {
                        if (sessionStore.load()?.refreshTokenId == stored.refreshTokenId) {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    session = stored,
                                    pendingAuth = storedPending,
                                    message = "Offline",
                                    error = null,
                                )
                            }
                        }
                    }
                    return@launch
                }
                try {
                    val refreshed = refreshMutex.withLock { api.refresh(stored) }
                    sessionTransitionMutex.withLock {
                        if (sessionStore.load()?.refreshTokenId == stored.refreshTokenId) {
                            sessionStore.save(refreshed)
                            if (storedPending != null && sessionStore.loadPendingAuth() == storedPending) {
                                sessionStore.clearPendingAuth()
                            }
                            pushTokenLifecycle.updateSession(refreshed)
                            _uiState.update { it.copy(isLoading = false, session = refreshed, message = "Logged in") }
                            fetchPhotoIfDue(refreshed)
                            updateUserAfterRefresh(refreshed)
                        }
                    }
                } catch (refreshError: Throwable) {
                    if (refreshError is CancellationException) throw refreshError
                    if (refreshError.isUnauthorizedAuthError()) {
                        invalidateSession(
                            AUTH_EXPIRED_MESSAGE,
                            expectedSession = stored,
                            expectedPendingAuth = storedPending,
                        )
                    } else {
                        val detail = if (isDebugBuild) {
                            " (${authErrorMessage(refreshError, debugDetailsEnabled = true)})"
                        } else {
                            ""
                        }
                        sessionTransitionMutex.withLock {
                            if (sessionStore.load()?.refreshTokenId == stored.refreshTokenId) {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        session = stored,
                                        message = "Offline",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun startLogin() {
        viewModelScope.launch {
            if (devAuthBypassEnabled) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        session = createDevSession(),
                        message = "DEV_BYPASS_AUTH enabled",
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(isLoading = true, error = null, message = "Opening Microsoft login...")
            }
            var pendingForAttempt: PendingAuth? = null
            try {
                val codeVerifier = generateBase64UrlRandom(32)
                val codeChallenge = generateCodeChallenge(codeVerifier)
                val state = generateBase64UrlRandom(32)
                val pending = PendingAuth(state = state, codeVerifier = codeVerifier)
                val authUrl = api.requestAuthUrl(state, codeChallenge)

                // Microsoftで認証済みの場合も即時コールバックを処理できるよう、
                // ブラウザーへ制御を渡す前に今回のPKCE情報を保存する。
                sessionTransitionMutex.withLock { sessionStore.savePendingAuth(pending) }
                pendingForAttempt = pending
                _uiState.update { it.copy(pendingAuth = pending) }
                val opened = openUrl(authUrl)
                if (!opened) {
                    clearPendingAuthForAttempt(pending)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            pendingAuth = null,
                            error = debugAuthMessage("ブラウザを開けませんでした", isDebugBuild),
                            message = "",
                        )
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "Continue login in your browser",
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                pendingForAttempt?.let { clearPendingAuthForAttempt(it) }
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        pendingAuth = current.pendingAuth.takeUnless { it == pendingForAttempt },
                        error = authErrorMessage(error, isDebugBuild),
                        message = "",
                    )
                }
            }
        }
    }

    fun handleCallbackUrl(url: String) {
        viewModelScope.launch {
            // コールドスタート時はセッション復元より先にディープリンクが届くことがあるため、
            // 画面状態が未復元なら永続化済みPKCE情報を直接参照する。
            val pending = _uiState.value.pendingAuth ?: sessionStore.loadPendingAuth()
            if (pending == null) {
                _uiState.update {
                    it.copy(error = debugAuthMessage("認証待ち情報がありません", isDebugBuild))
                }
                return@launch
            }

            val callbackError = readQueryValue(url, "error")
            if (!callbackError.isNullOrBlank()) {
                // OAuth callbackの失敗時はPKCE/stateを破棄し、再試行時に新しい認証を開始する。
                clearPendingAuthForAttempt(pending)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pendingAuth = it.pendingAuth.takeUnless { saved -> saved == pending },
                        message = "",
                        error = if (callbackError == "access_denied") {
                            AUTH_CANCELED_MESSAGE
                        } else {
                            debugAuthMessage("Microsoft callback error", isDebugBuild)
                        },
                    )
                }
                return@launch
            }

            val code = readQueryValue(url, "code")
            val state = readQueryValue(url, "state")
            if (code.isNullOrBlank() || state.isNullOrBlank()) {
                val missing = listOfNotNull(
                    "code".takeIf { code.isNullOrBlank() },
                    "state".takeIf { state.isNullOrBlank() },
                ).joinToString("/")
                clearPendingAuthForAttempt(pending)
                _uiState.update {
                    it.copy(
                        pendingAuth = it.pendingAuth.takeUnless { saved -> saved == pending },
                        error = debugAuthMessage("コールバックに $missing がありません", isDebugBuild),
                    )
                }
                return@launch
            }
            if (state != pending.state) {
                clearPendingAuthForAttempt(pending)
                _uiState.update {
                    it.copy(
                        pendingAuth = it.pendingAuth.takeUnless { saved -> saved == pending },
                        error = debugAuthMessage("state が一致しません", isDebugBuild),
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null, message = "Completing login...") }
            try {
                val session = api.exchangeCode(code, state, pending.codeVerifier)
                val committed = sessionTransitionMutex.withLock {
                    if (sessionStore.loadPendingAuth() != pending) {
                        false
                    } else {
                        photoFetchJob?.cancel()
                        check(photoRepository?.clear() != false) { "保存済みのプロフィール写真を削除できませんでした" }
                        sessionStore.save(session)
                        sessionStore.clearPendingAuth()
                        // 共有端末では、新規ログイン時に前ユーザーのキャッシュを消去する。
                        cache.clearAll()
                        pushTokenLifecycle.updateSession(session)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                session = session,
                                pendingAuth = null,
                                message = "Login successful",
                            )
                        }
                        true
                    }
                }
                if (!committed) return@launch
                fetchPhotoIfDue(session, force = true)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (error is AuthApiException) {
                    clearPendingAuthForAttempt(pending)
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pendingAuth = if (error is AuthApiException) {
                            it.pendingAuth.takeUnless { saved -> saved == pending }
                        } else {
                            it.pendingAuth
                        },
                        error = authErrorMessage(error, isDebugBuild),
                        message = "",
                    )
                }
            }
        }
    }

    fun logout() {
        val targetSession = _uiState.value.session ?: return
        pushTokenLifecycle.beginLogout(targetSession)
        viewModelScope.launch {
            val photoCleared = sessionTransitionMutex.withLock {
                val stored = sessionStore.load()
                val storedSessionIsTarget = stored == null || (
                    stored.refreshTokenId == targetSession.refreshTokenId &&
                        stored.user.id == targetSession.user.id
                    )
                if (!storedSessionIsTarget) {
                    // logout開始前に新しいSessionへ切り替わっていた場合は、
                    // 新ユーザーの取得中/保存済み写真を古いlogoutで触らない。
                    true
                } else {
                    photoFetchJob?.cancel()
                    // サーバーのlogout完了を待たず、対象Sessionの写真は端末から消す。
                    photoRepository?.clear() ?: true
                }
            }
            _uiState.update { it.copy(isLoading = true, error = null) }
            val pendingAtLogoutStart = runCatching { sessionStore.loadPendingAuth() }.getOrNull()
            try {
                if (!devAuthBypassEnabled) {
                    pushTokenLifecycle.logout(targetSession) { fcmToken ->
                        api.logout(targetSession, fcmToken)
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                // Push解除やサーバーログアウトに失敗してもlocal logoutを続ける。
            } finally {
                sessionTransitionMutex.withLock {
                    val stored = sessionStore.load()
                    val storedSessionIsTarget = stored == null || (
                        stored.refreshTokenId == targetSession.refreshTokenId &&
                            stored.user.id == targetSession.user.id
                        )
                    if (!storedSessionIsTarget) {
                        _uiState.update { current ->
                            val currentSession = current.session
                            if (currentSession == null || currentSession.refreshTokenId == targetSession.refreshTokenId) {
                                current.copy(
                                    isLoading = false,
                                    session = stored,
                                    error = null,
                                    message = "Logged in",
                                )
                            } else {
                                current.copy(isLoading = false)
                            }
                        }
                        return@withLock
                    }

                    // 新しいSessionが保存済みなら、古いlogoutでSessionやcacheを消さない。
                    val sessionCleared = sessionStore.clear()
                    val currentPending = sessionStore.loadPendingAuth()
                    val pendingCleared = when {
                        pendingAtLogoutStart == null && currentPending == null -> sessionStore.clearPendingAuth()
                        currentPending != pendingAtLogoutStart -> true
                        else -> sessionStore.clearPendingAuth()
                    }
                    cache.clearAll()
                    val cleared = sessionCleared && pendingCleared && photoCleared
                    _uiState.value = if (cleared) {
                        AuthUiState(message = "Logged out")
                    } else {
                        AuthUiState(error = LOGOUT_FAILED_MESSAGE)
                    }
                }
                // 古いSessionの復元禁止はSessionStoreとcacheのcleanup完了後に確定する。
                pushTokenLifecycle.completeLogout(targetSession)
            }
        }
    }

    internal suspend fun refreshAfterUnauthorized(accessToken: String) {
        refreshMutex.withLock {
            val current = _uiState.value.session ?: return
            if (current.accessToken != accessToken) return
            val pendingAtRefreshStart = sessionStore.loadPendingAuth()

            val now = nowMillis()
            if (refreshWindowStartedAt == 0L || now - refreshWindowStartedAt > REFRESH_WINDOW_MILLIS) {
                refreshWindowStartedAt = now
                refreshAttemptCount = 0
            }
            if (refreshAttemptCount >= MAX_REFRESH_ATTEMPTS) {
                invalidateSession(
                    AUTH_EXPIRED_MESSAGE,
                    expectedAccessToken = accessToken,
                    expectedSession = current,
                    expectedPendingAuth = pendingAtRefreshStart,
                )
                return
            }
            refreshAttemptCount++

            try {
                val refreshed = api.refresh(current)
                sessionTransitionMutex.withLock {
                    val latest = _uiState.value.session
                    val stored = sessionStore.load()
                    if (
                        latest?.accessToken == accessToken &&
                        latest.refreshTokenId == current.refreshTokenId &&
                        stored?.refreshTokenId == current.refreshTokenId
                    ) {
                        sessionStore.save(refreshed)
                        pushTokenLifecycle.updateSession(refreshed)
                        _uiState.update {
                            it.copy(session = refreshed, error = null, message = "Logged in")
                        }
                        updateUserAfterRefresh(refreshed)
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (error.isUnauthorizedAuthError()) {
                    invalidateSession(
                        AUTH_EXPIRED_MESSAGE,
                        expectedAccessToken = accessToken,
                        expectedSession = current,
                        expectedPendingAuth = pendingAtRefreshStart,
                    )
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = authErrorMessage(error, isDebugBuild),
                            message = "Offline",
                        )
                    }
                }
            }
        }
    }

    /** 更新済みトークンを先に保存し、所属情報の取得失敗では巻き戻さない。 */
    private fun updateUserAfterRefresh(refreshed: AuthSession) {
        viewModelScope.launch {
            try {
                val user = api.currentUser(refreshed.accessToken)
                sessionTransitionMutex.withLock {
                    val stored = sessionStore.load()
                    val current = _uiState.value.session
                    if (stored?.accessToken != refreshed.accessToken ||
                        stored.refreshTokenId != refreshed.refreshTokenId ||
                        current?.accessToken != refreshed.accessToken
                    ) return@withLock
                    // 別アカウントへの切替として扱わず、同じ利用者の所属情報を補完する。
                    if (user.id != refreshed.user.id) return@withLock
                    val updated = refreshed.copy(user = user)
                    sessionStore.save(updated)
                    pushTokenLifecycle.updateSession(updated)
                    _uiState.update { it.copy(session = updated) }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                // 一時障害でも更新済み認証を維持し、次回の確認でユーザー情報を取得する。
            }
        }
    }

    private suspend fun invalidateSession(
        message: String,
        expectedAccessToken: String? = null,
        expectedSession: AuthSession? = null,
        expectedPendingAuth: PendingAuth? = null,
    ) {
        sessionTransitionMutex.withLock {
            val current = _uiState.value.session
            if (expectedAccessToken != null && current?.accessToken != expectedAccessToken) return@withLock
            if (expectedSession != null && current != null && current.refreshTokenId != expectedSession.refreshTokenId) return@withLock
            val stored = sessionStore.load()
            if (expectedSession != null && stored?.refreshTokenId != expectedSession.refreshTokenId) return@withLock
            photoFetchJob?.cancel()
            photoRepository?.clear()
            sessionStore.clear()
            if (expectedPendingAuth != null && sessionStore.loadPendingAuth() == expectedPendingAuth) {
                sessionStore.clearPendingAuth()
            }
            cache.clearAll()
            _uiState.value = AuthUiState(error = message)
        }
    }

    /** ログイン時、または起動時に24時間経過している場合だけ写真を取得する。 */
    private fun fetchPhotoIfDue(session: AuthSession, force: Boolean = false) {
        val repository = photoRepository ?: return
        photoFetchJob?.cancel()
        photoFetchJob = viewModelScope.launch {
            repository.restore(session.user.id)
            repository.refresh(session.user.id, session.accessToken, force)
        }
    }

    private suspend fun clearPendingAuthForAttempt(pending: PendingAuth) {
        sessionTransitionMutex.withLock {
            if (sessionStore.loadPendingAuth() == pending) {
                sessionStore.clearPendingAuth()
            }
        }
    }

    override fun onCleared() {
        photoFetchJob?.cancel()
        api.close()
        photoRepository?.close()
        super.onCleared()
    }

    private companion object {
        const val MAX_REFRESH_ATTEMPTS = 2
        const val REFRESH_WINDOW_MILLIS = 60_000L
    }
}

private fun Throwable.isUnauthorizedAuthError(): Boolean =
    this is AuthApiException && statusCode == 401

// 例外messageが空の場合も、デバッグログに最低限の原因を残す。
private fun Throwable.describe(): String =
    message?.takeIf { it.isNotBlank() } ?: this::class.simpleName ?: "unknown error"

private fun createDevSession() = AuthSession(
    accessToken = "dev-bypass-token",
    refreshTokenId = "dev-bypass-refresh",
    expiresIn = 3600L,
    user = AuthUser(
        id = "dev-user",
        email = "dev@local",
        displayName = "Dev User",
        studentIdNumber = "55000",
        classRoomName = "IA12A203",
        classCode = "IA12A203",
    ),
)

private fun readQueryValue(url: String, key: String): String? {
    val queryStart = url.indexOf('?')
    if (queryStart < 0) return null
    val fragmentStart = url.indexOf('#', startIndex = queryStart + 1).let { if (it < 0) url.length else it }
    val query = url.substring(queryStart + 1, fragmentStart)
    return query.split('&')
        .asSequence()
        .mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator < 0) null else part.substring(0, separator) to part.substring(separator + 1)
        }
        .firstOrNull { (candidateKey, _) -> decodeUrlComponent(candidateKey) == key }
        ?.second
        ?.let(::decodeUrlComponent)
}

private fun decodeUrlComponent(value: String): String {
    val bytes = ArrayList<Byte>(value.length)
    var index = 0
    while (index < value.length) {
        when (val char = value[index]) {
            '%' -> {
                if (index + 2 < value.length) {
                    val hex = value.substring(index + 1, index + 3)
                    bytes += hex.toInt(16).toByte()
                    index += 3
                } else {
                    bytes += char.code.toByte()
                    index += 1
                }
            }
            '+' -> {
                bytes += ' '.code.toByte()
                index += 1
            }
            else -> {
                bytes += char.code.toByte()
                index += 1
            }
        }
    }
    return bytes.toByteArray().decodeToString()
}
