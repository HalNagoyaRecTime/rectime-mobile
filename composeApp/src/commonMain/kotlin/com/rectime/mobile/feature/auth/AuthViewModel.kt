package com.rectime.mobile.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthViewModel(
    private val api: AuthApi = AuthApi(),
    private val sessionStore: AuthSessionStore = AuthSessionStore(),
) : ViewModel() {
    private val devAuthBypassEnabled = isDevAuthBypassEnabled()
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
            val stored = sessionStore.load()
            if (stored == null) {
                _uiState.update { it.copy(isLoading = false, message = "") }
                return@launch
            }

            try {
                val user = api.currentUser(stored.accessToken)
                val session = stored.copy(user = user)
                sessionStore.save(session)
                _uiState.update { it.copy(isLoading = false, session = session, message = "Logged in") }
            } catch (_: Throwable) {
                try {
                    val refreshed = api.refresh(stored)
                    sessionStore.save(refreshed)
                    _uiState.update { it.copy(isLoading = false, session = refreshed, message = "Logged in") }
                } catch (_: Throwable) {
                    sessionStore.clear()
                    _uiState.update { AuthUiState(error = "Session expired. Please login again.") }
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
                val opened = openExternalUrl(authUrl)
                if (!opened) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Could not open a browser for login.",
                            message = "",
                        )
                    }
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pendingAuth = PendingAuth(state = state, codeVerifier = codeVerifier),
                        message = "Continue login in your browser",
                    )
                }
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to start login",
                    )
                }
            }
        }
    }

    fun handleCallbackUrl(url: String) {
        viewModelScope.launch {
            val pending = _uiState.value.pendingAuth
            if (pending == null) {
                _uiState.update { it.copy(error = "No login request is in progress.") }
                return@launch
            }

            val code = readQueryValue(url, "code")
            val state = readQueryValue(url, "state")
            if (code.isNullOrBlank() || state.isNullOrBlank()) {
                _uiState.update { it.copy(error = "Invalid callback URL.") }
                return@launch
            }
            if (state != pending.state) {
                _uiState.update { it.copy(error = "Auth state does not match.") }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null, message = "Completing login...") }
            try {
                val session = api.exchangeCode(code, state, pending.codeVerifier)
                sessionStore.save(session)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        session = session,
                        pendingAuth = null,
                        message = "Login successful",
                    )
                }
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to complete login",
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
            } catch (_: Throwable) {
                // Prefer local sign-out even if server logout fails.
            } finally {
                sessionStore.clear()
                _uiState.update { AuthUiState(message = "Logged out") }
            }
        }
    }
}

private fun createDevSession() = AuthSession(
    accessToken = "dev-bypass-token",
    refreshTokenId = "dev-bypass-refresh",
    expiresIn = 3600L,
    user = AuthUser(
        id = "dev-user",
        email = "dev@local",
        displayName = "Dev User",
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
