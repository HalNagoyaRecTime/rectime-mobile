package com.rectime.mobile.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.cache.LocalCache
import com.rectime.mobile.core.config.isDebugBuild
import com.rectime.mobile.core.network.HttpStatusException
import com.rectime.mobile.core.platform.openExternalUrl
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val AUTH_FAILED_MESSAGE = "ログインに失敗しました"
private const val LOGOUT_FAILED_MESSAGE = "ログアウトに失敗しました"

// 原因にはAPIのホスト名やIPが含まれうるため、画面には出さずデバッグビルドのログにだけ残す。
private fun logAuthFailure(reason: String?) {
    if (isDebugBuild && !reason.isNullOrBlank()) {
        println("AuthViewModel: $reason")
    }
}

private fun authFailed(reason: String?): String {
    logAuthFailure(reason)
    return AUTH_FAILED_MESSAGE
}

class AuthViewModel(
    private val api: AuthApi = AuthApi(),
    private val sessionStore: AuthSessionStorage = PlatformAuthSessionStorage(),
    private val cache: LocalCache = LocalCache(),
    private val devAuthBypassEnabled: Boolean = isDevAuthBypassEnabled(),
    private val openUrl: suspend (String) -> Boolean = { openExternalUrl(it) },
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        restoreSession()

        viewModelScope.launch {
            AuthDeepLinkHandler.callbacks.collect { callbackUrl ->
                handleCallbackUrl(callbackUrl)
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
                _uiState.update {
                    it.copy(isLoading = false, message = "", pendingAuth = storedPending)
                }
                return@launch
            }

            try {
                val user = api.currentUser(stored.accessToken)
                val session = stored.copy(user = user)
                sessionStore.save(session)
                sessionStore.clearPendingAuth()
                _uiState.update { it.copy(isLoading = false, session = session, message = "Logged in") }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                try {
                    val refreshed = api.refresh(stored)
                    sessionStore.save(refreshed)
                    sessionStore.clearPendingAuth()
                    _uiState.update { it.copy(isLoading = false, session = refreshed, message = "Logged in") }
                } catch (refreshError: Throwable) {
                    if (refreshError is CancellationException) throw refreshError
                    logAuthFailure(refreshError.describe())
                    if (refreshError is HttpStatusException && refreshError.status == HttpStatusCode.Unauthorized) {
                        // HttpStatusExceptionは非2xx全般(500/503等の一時的な
                        // サーバーエラーも含む)で投げられるため、ステータスコードまで
                        // 見て判定する。401(refreshTokenId自体が無効・失効)の場合のみ
                        // セッションが本当に無効と判断し、セッション・キャッシュを
                        // クリアする。他画面(Calendar/Event等)のセッション切れ
                        // 判定も同様に401のみを見ている。
                        sessionStore.clear()
                        sessionStore.clearPendingAuth()
                        cache.clearAll()
                        _uiState.update {
                            AuthUiState(error = "Session expired. Please login again.")
                        }
                    } else {
                        // 圏外・オフライン等、通信自体が失敗した場合。セッションが
                        // 無効だとは判断できないため、セッション・オフラインキャッシュは
                        // 保持し、保存済みの(古い)セッションでアプリを継続させる
                        // (オフラインでもキャッシュ済みデータで各画面を使えるように)。
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
            try {
                val codeVerifier = generateBase64UrlRandom(32)
                val codeChallenge = generateCodeChallenge(codeVerifier)
                val state = generateBase64UrlRandom(32)
                val authUrl = api.requestAuthUrl(state, codeChallenge)
                val opened = openUrl(authUrl)
                if (!opened) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = authFailed("ブラウザを開けませんでした"),
                            message = "",
                        )
                    }
                    return@launch
                }
                val pending = PendingAuth(state = state, codeVerifier = codeVerifier)
                sessionStore.savePendingAuth(pending)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pendingAuth = pending,
                        message = "Continue login in your browser",
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = authFailed("認証URL取得: ${error.describe()}"),
                    )
                }
            }
        }
    }

    fun handleCallbackUrl(url: String) {
        viewModelScope.launch {
            val pending = _uiState.value.pendingAuth
            if (pending == null) {
                _uiState.update { it.copy(error = authFailed("認証待ち情報がありません")) }
                return@launch
            }

            val code = readQueryValue(url, "code")
            val state = readQueryValue(url, "state")
            if (code.isNullOrBlank() || state.isNullOrBlank()) {
                val missing = listOfNotNull(
                    "code".takeIf { code.isNullOrBlank() },
                    "state".takeIf { state.isNullOrBlank() },
                ).joinToString("/")
                _uiState.update { it.copy(error = authFailed("コールバックに $missing がありません")) }
                return@launch
            }
            if (state != pending.state) {
                _uiState.update { it.copy(error = authFailed("state が一致しません")) }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null, message = "Completing login...") }
            try {
                val session = api.exchangeCode(code, state, pending.codeVerifier)
                sessionStore.save(session)
                sessionStore.clearPendingAuth()
                // 共有端末で前のユーザーがログアウトせずにアプリを離れていた場合、
                // キャッシュキーはユーザーIDで分離されていないため、新規ログイン時にも
                // 明示的にクリアしておかないと前ユーザーのデータが見えてしまう。
                cache.clearAll()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        session = session,
                        pendingAuth = null,
                        message = "Login successful",
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = authFailed("トークン交換: ${error.describe()}"),
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            val session = _uiState.value.session
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                if (session != null && !devAuthBypassEnabled) {
                    api.logout(session)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                // Prefer local sign-out even if server logout fails.
            } finally {
                // API側でログアウトしてもaccess tokenは期限まで有効なため、端末から
                // 消せたことを確認できない限りログアウト成功として扱わない。
                val cleared = sessionStore.clear() and sessionStore.clearPendingAuth()
                cache.clearAll()
                _uiState.update {
                    if (cleared) {
                        AuthUiState(message = "Logged out")
                    } else {
                        it.copy(isLoading = false, error = LOGOUT_FAILED_MESSAGE)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        api.close()
        super.onCleared()
    }
}

// ネットワーク例外はmessageがnullになることがあり、その場合は型名だけが手がかりになる。
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
