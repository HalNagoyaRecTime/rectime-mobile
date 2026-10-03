package com.rectime.mobile.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.config.isDebugBuild
import com.rectime.mobile.core.platform.openExternalUrl
import com.rectime.mobile.feature.notifications.PushTokenLifecycle
import com.rectime.mobile.feature.notifications.platformPushTokenLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
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
    private val _uiState = MutableStateFlow(AuthUiState(isRestoringSession = true))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    private val refreshMutex = Mutex()
    private val sessionTransitionMutex = Mutex()
    private var refreshAttemptCount = 0
    private var refreshWindowStartedAt = 0L
    private var photoFetchJob: Job? = null
    private var previousAccessToken: String? = null
    private var loggingOut = false
    private var refreshJob: Deferred<String?>? = null
    private var refreshRequestToken: String? = null
    private var sessionCheckJob: Job? = null

    init {
        AuthSessionInvalidationHandler.register(this, ::refreshAfterUnauthorized, ::handleAccountDeactivated)
        restoreSession()

        viewModelScope.launch {
            AuthDeepLinkHandler.callbacks.collect { callbackUrl ->
                handleCallbackUrl(callbackUrl)
            }
        }

    }

    private fun restoreSession() {
        sessionCheckJob = viewModelScope.launch {
            try {
                if (devAuthBypassEnabled) {
                    SessionTokenHolder.accessToken = "dev-bypass-token"
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRestoringSession = false,
                            session = createDevSession(),
                            message = "DEV_BYPASS_AUTH enabled",
                        )
                    }
                    return@launch
                }

                _uiState.update { it.copy(isLoading = true, message = "Restoring session...") }

                // 認証途中で終了した場合も、起動後のコールバックを受け付けられるよう復元する。
                val storedPending = sessionStore.loadPendingAuth()

                val stored = sessionStore.load()
                if (stored == null) {
                    sessionTransitionMutex.withLock {
                        // cold start中にOAuth callbackが先に新Sessionを保存した場合、
                        // 古いrestore処理で新ユーザーの写真を消さない。
                        if (sessionStore.load() == null) {
                            SessionTokenHolder.accessToken = null
                            photoFetchJob?.cancel()
                            photoRepository?.clear()
                            _uiState.update {
                                it.copy(isLoading = false, isRestoringSession = false, message = "", pendingAuth = storedPending)
                            }
                        }
                    }
                    return@launch
                }

                sessionTransitionMutex.withLock {
                    // callback等でSessionが切り替わっていない場合だけ保存済み写真を復元する。
                    if (!loggingOut && sessionStore.load()?.let {
                            it.refreshTokenId == stored.refreshTokenId && it.accessToken == stored.accessToken
                        } == true) {
                        photoRepository?.restore(stored.user.id)
                        if (loggingOut) return@withLock
                        SessionTokenHolder.accessToken = stored.accessToken
                        _uiState.update {
                            it.copy(isLoading = false, isRestoringSession = false, session = stored, pendingAuth = storedPending, message = "")
                        }
                    }
                }

                _uiState.update { it.copy(isRestoringSession = false) }
                checkStoredSession(stored, storedPending, refreshPhoto = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                error.printStackTrace()
                // 端末内の読み込みが失敗しても、起動画面に固定しない。
                _uiState.update {
                    it.copy(isLoading = false, error = if (it.session == null) AUTH_FAILED_MESSAGE else null)
                }
            } finally {
                _uiState.update { it.copy(isRestoringSession = false) }
            }
        }
    }

    /** 前面復帰時も画面を維持し、起動時と同じ確認を裏で行う。 */
    fun onForeground() {
        if (devAuthBypassEnabled || loggingOut || sessionCheckJob?.isActive == true) return
        val stored = _uiState.value.session ?: return
        sessionCheckJob = viewModelScope.launch {
            try {
                checkStoredSession(stored, sessionStore.loadPendingAuth())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // 端末内の読み込み失敗も、前面復帰時のログイン解除の理由にしない。
                error.printStackTrace()
            }
        }
    }

    private suspend fun checkStoredSession(stored: AuthSession, storedPending: PendingAuth?, refreshPhoto: Boolean = false) {
        if (loggingOut || _uiState.value.session?.accessToken != stored.accessToken) return
        try {
            val user = api.currentUser(stored.accessToken)
            val session = stored.copy(user = user)
            sessionTransitionMutex.withLock {
                if (!loggingOut && sessionStore.load()?.let {
                    it.refreshTokenId == stored.refreshTokenId && it.accessToken == stored.accessToken
                } == true) {
                    if (user.id != stored.user.id) {
                        photoFetchJob?.cancel()
                        photoRepository?.clear()
                        photoRepository?.restore(user.id)
                    }
                    sessionStore.save(session)
                    if (loggingOut) return@withLock
                    SessionTokenHolder.accessToken = session.accessToken
                    if (storedPending != null && sessionStore.loadPendingAuth() == storedPending) {
                        sessionStore.clearPendingAuth()
                    }
                    pushTokenLifecycle.updateSession(session)
                    _uiState.update { it.copy(isLoading = false, session = session, message = "Logged in") }
                    if (refreshPhoto) fetchPhotoIfDue(session)
                }
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (error is AuthApiException && error.errorCode == USER_DEACTIVATED_CODE && error.statusCode == 401) {
                handleAccountDeactivated(stored.accessToken)
                return
            }
            // 一時的な通信・Server障害では保存済みSessionとPKCE情報を維持する。
            if (!error.isUnauthorizedAuthError()) {
                sessionTransitionMutex.withLock {
                    if (!loggingOut && sessionStore.load()?.let {
                            it.refreshTokenId == stored.refreshTokenId && it.accessToken == stored.accessToken
                        } == true) {
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
                return
            }
            // 一覧等の401と同じ更新処理を共有し、二重にrefreshしない。
            refreshAfterUnauthorized(stored.accessToken)
        }
    }

    internal suspend fun handleAccountDeactivated(accessToken: String) {
        val current = _uiState.value.session ?: return
        // 別ユーザーのログイン後に届いた、古い通信の拒否では締め出さない。
        if (accessToken != current.accessToken && accessToken != previousAccessToken) return
        val pending = _uiState.value.pendingAuth
        // 通信元の画面が閉じても、認証情報の削除は最後まで実行する。
        viewModelScope.async {
            invalidateSession(
                AUTH_DEACTIVATED_MESSAGE,
                expectedAccessToken = current.accessToken,
                expectedSession = current,
                expectedPendingAuth = pending,
            )
        }.await()
    }

    fun startLogin() {
        viewModelScope.launch {
            if (devAuthBypassEnabled) {
                SessionTokenHolder.accessToken = "dev-bypass-token"
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
                        // 前ユーザーの内容を消せた場合だけ、新しいログインを永続化する。
                        // 保存を先に行うと、削除失敗後の再起動で別ユーザーのキャッシュが見えてしまう。
                        cache.clearAll()
                        sessionStore.save(session)
                        loggingOut = false
                        previousAccessToken = null
                        refreshAttemptCount = 0
                        refreshWindowStartedAt = 0L
                        SessionTokenHolder.accessToken = session.accessToken
                        sessionStore.clearPendingAuth()
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
        if (loggingOut) return
        loggingOut = true
        previousAccessToken = null
        SessionTokenHolder.accessToken = null
        try {
            pushTokenLifecycle.beginLogout(targetSession)
        } catch (error: Exception) {
            error.printStackTrace()
        }
        // 通信や端末内の削除を待たず、現在のログイン済み画面を閉じる。
        _uiState.value = AuthUiState(isLoading = true)
        viewModelScope.launch {
            var preparationSucceeded = true
            var pendingAtLogoutStart: PendingAuth? = null
            try {
                try {
                    pendingAtLogoutStart = sessionStore.loadPendingAuth()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    preparationSucceeded = false
                    error.printStackTrace()
                }
                sessionTransitionMutex.withLock {
                    val stored = readStoredSessionForCleanup()
                    if (stored.getOrNull()?.let { !it.belongsTo(targetSession) } != true) {
                        photoFetchJob?.cancel()
                        // サーバーの応答待ちの間も写真を表示・復元しない。
                        preparationSucceeded = cleanupAction {
                            check(photoRepository?.clear() != false) { "保存済み写真を削除できませんでした" }
                        } && preparationSucceeded && stored.isSuccess
                    }
                }
                if (!devAuthBypassEnabled) {
                    pushTokenLifecycle.logout(targetSession) { fcmToken ->
                        api.logout(targetSession, fcmToken)
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                // Push解除やサーバーログアウトに失敗しても端末内の削除を続ける。
                error.printStackTrace()
            } finally {
                // 呼び出し元が終了しても、端末内のログイン情報の削除は完了させる。
                withContext(NonCancellable) {
                    try {
                        sessionTransitionMutex.withLock {
                            val stored = readStoredSessionForCleanup()
                            val newer = stored.getOrNull()?.takeUnless { it.belongsTo(targetSession) }
                            val current = _uiState.value.session
                            if (newer != null || current?.let { !it.belongsTo(targetSession) } == true) {
                                // 古いログアウトの完了で、後からログインしたアカウントを消さない。
                                val session = current?.takeUnless { it.belongsTo(targetSession) } ?: newer
                                if (session != null) {
                                    loggingOut = false
                                    SessionTokenHolder.accessToken = session.accessToken
                                    _uiState.update { it.copy(session = session, isLoading = false, error = null, message = "Logged in") }
                                }
                                return@withLock
                            }
                            finishLocalSession(
                                message = null,
                                expectedPendingAuth = pendingAtLogoutStart,
                                clearEmptyPendingAuth = true,
                                preparationSucceeded = preparationSucceeded && stored.isSuccess,
                            )
                        }
                    } finally {
                        pushTokenLifecycle.completeLogout(targetSession)
                    }
                }
            }
        }
    }

    internal suspend fun refreshAfterUnauthorized(accessToken: String): String? {
        val job = refreshMutex.withLock {
            if (loggingOut) return null
            val current = _uiState.value.session ?: return null
            if (current.accessToken != accessToken) {
                return current.accessToken.takeIf { previousAccessToken == accessToken }
            }
            refreshJob?.takeIf { it.isActive && refreshRequestToken == accessToken }
                ?: viewModelScope.async { performRefresh(accessToken) }.also {
                    refreshRequestToken = accessToken
                    refreshJob = it
                }
        }
        // 呼び出し元の画面が閉じても更新を継続し、同時401は同じ結果を待つ。
        val refreshed = job.await()
        return refreshed.takeIf { !loggingOut && _uiState.value.session?.accessToken == it }
    }

    private suspend fun performRefresh(accessToken: String): String? =
        run refresh@{
            if (loggingOut) return@refresh null
            val current = _uiState.value.session ?: return@refresh null
            if (current.accessToken != accessToken) {
                // 同じセッションの更新を待った通信は、直前のトークンだけを引き継ぐ。
                return@refresh current.accessToken.takeIf { previousAccessToken == accessToken }
            }
            val pendingAtRefreshStart = try {
                sessionStore.loadPendingAuth()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                error.printStackTrace()
                return@refresh null
            }

            val now = nowMillis()
            if (refreshWindowStartedAt == 0L || now - refreshWindowStartedAt > REFRESH_WINDOW_MILLIS) {
                refreshWindowStartedAt = now
                refreshAttemptCount = 0
            }
            // 通信失敗が続いても、試行回数だけで保存済みログインを消さない。
            if (refreshAttemptCount >= MAX_REFRESH_ATTEMPTS) return@refresh null
            refreshAttemptCount++

            try {
                val refreshed = api.refresh(current)
                sessionTransitionMutex.withLock {
                    val latest = _uiState.value.session
                    val stored = sessionStore.load()
                    if (
                        !loggingOut && latest?.accessToken == accessToken &&
                        latest.refreshTokenId == current.refreshTokenId &&
                        stored?.refreshTokenId == current.refreshTokenId
                    ) {
                        sessionStore.save(refreshed)
                        if (loggingOut) return@withLock null
                        previousAccessToken = accessToken
                        SessionTokenHolder.accessToken = refreshed.accessToken
                        pushTokenLifecycle.updateSession(refreshed)
                        _uiState.update {
                            it.copy(
                                session = refreshed,
                                pendingAuth = it.pendingAuth.takeUnless { saved -> saved == pendingAtRefreshStart },
                                error = null,
                                message = "Logged in",
                            )
                        }
                        if (pendingAtRefreshStart != null && sessionStore.loadPendingAuth() == pendingAtRefreshStart) {
                            sessionStore.clearPendingAuth()
                        }
                        fetchPhotoIfDue(refreshed)
                        refreshed.accessToken
                    } else {
                        null
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (error is AuthApiException && error.statusCode == 401 && error.errorCode == USER_DEACTIVATED_CODE) {
                    handleAccountDeactivated(accessToken)
                } else if (error.isUnauthorizedAuthError()) {
                    invalidateSession(
                        AUTH_EXPIRED_MESSAGE,
                        expectedAccessToken = accessToken,
                        expectedSession = current,
                        expectedPendingAuth = pendingAtRefreshStart,
                    )
                } else {
                    sessionTransitionMutex.withLock {
                        if (!loggingOut && _uiState.value.session?.accessToken == accessToken) {
                            _uiState.update { it.copy(isLoading = false, message = "Offline") }
                        }
                    }
                }
                null
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
            if (loggingOut) return@withLock
            if (expectedAccessToken != null && current?.accessToken != expectedAccessToken) return@withLock
            if (expectedSession != null && current != null && current.refreshTokenId != expectedSession.refreshTokenId) return@withLock
            val stored = readStoredSessionForCleanup()
            if (expectedSession != null && stored.getOrNull()?.let { !it.belongsTo(expectedSession) } == true) return@withLock
            finishLocalSession(
                message = message,
                expectedPendingAuth = expectedPendingAuth,
                preparationSucceeded = stored.isSuccess,
            )
        }
    }

    /** 呼び出し元がsessionTransitionMutexを保持し、対象のログインを確認した後に使う。 */
    private suspend fun finishLocalSession(
        message: String?,
        expectedPendingAuth: PendingAuth?,
        clearEmptyPendingAuth: Boolean = false,
        preparationSucceeded: Boolean = true,
    ) {
        previousAccessToken = null
        SessionTokenHolder.accessToken = null
        photoFetchJob?.cancel()
        _uiState.value = AuthUiState(isLoading = message == null, error = message)
        // 失効・無効化・ログアウトで同じ削除処理を使い、一箇所の失敗で残りを止めない。
        val sessionCleared = cleanupAction {
            check(sessionStore.clear()) { "保存済みログインを削除できませんでした" }
        }
        val photoCleared = cleanupAction {
            check(photoRepository?.clear() != false) { "保存済み写真を削除できませんでした" }
        }
        val pendingCleared = cleanupAction {
            val pending = sessionStore.loadPendingAuth()
            if ((expectedPendingAuth != null && pending == expectedPendingAuth) ||
                (clearEmptyPendingAuth && expectedPendingAuth == null && pending == null)) {
                check(sessionStore.clearPendingAuth()) { "保存済み認証処理を削除できませんでした" }
            }
        }
        val cacheCleared = cleanupAction { cache.clearAll() }
        if (message == null) {
            val cleared = preparationSucceeded && sessionCleared && photoCleared && pendingCleared && cacheCleared
            _uiState.update {
                it.copy(isLoading = false, error = if (cleared) null else LOGOUT_FAILED_MESSAGE, message = if (cleared) "Logged out" else "")
            }
        }
    }

    private suspend fun cleanupAction(action: suspend () -> Unit): Boolean = try {
        action()
        true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        error.printStackTrace()
        false
    }

    private suspend fun readStoredSessionForCleanup(): Result<AuthSession?> = try {
        Result.success(sessionStore.load())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        error.printStackTrace()
        Result.failure(error)
    }

    private fun AuthSession.belongsTo(other: AuthSession): Boolean =
        refreshTokenId == other.refreshTokenId && user.id == other.user.id

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
        AuthSessionInvalidationHandler.unregister(this)
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
