package com.rectime.mobile.core.haptics

import androidx.compose.runtime.staticCompositionLocalOf
import com.rectime.mobile.core.cache.KeyValueStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException

internal const val HapticEnabledKey = "preference_haptic_enabled"

class HapticPreference(
    private val store: KeyValueStore = createHapticPreferenceStore(),
) {
    private val mutex = Mutex()
    private val _enabled = MutableStateFlow(DefaultEnabled)

    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    suspend fun load() = mutex.withLock {
        try {
            _enabled.value = store.getString(HapticEnabledKey)
                ?.toBooleanStrictOrNull()
                ?: DefaultEnabled
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 設定の読み込み失敗でアプリの起動を妨げない。
            println("[Haptics] 設定の読み込みに失敗: ${e::class.simpleName}")
        }
    }

    suspend fun setEnabled(enabled: Boolean) = mutex.withLock {
        _enabled.value = enabled
        try {
            store.putString(HapticEnabledKey, enabled.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 永続化できなくても、今回の操作は表示と振動に反映する。
            println("[Haptics] 設定の保存に失敗: ${e::class.simpleName}")
        }
    }

    companion object {
        const val DefaultEnabled = true
    }
}

val LocalHapticPreference = staticCompositionLocalOf { HapticPreference() }

// ログアウトでアカウントキャッシュを消しても端末設定は維持する。
internal expect fun createHapticPreferenceStore(): KeyValueStore
