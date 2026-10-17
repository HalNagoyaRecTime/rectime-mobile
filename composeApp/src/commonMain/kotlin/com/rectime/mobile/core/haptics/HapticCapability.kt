package com.rectime.mobile.core.haptics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class HapticSupportStatus {
    Loading, Supported, Unsupported, Failed;

    val showSetting: Boolean get() = this == Supported || this == Failed
    val canVibrate: Boolean get() = this == Supported
}

/** 成功した判定は固定し、失敗した場合だけ次の呼び出しで再試行する。 */
internal class HapticCapabilityState(private val readSupport: suspend () -> Boolean) {
    private val mutex = Mutex()
    private val currentStatus = MutableStateFlow(HapticSupportStatus.Loading)
    val status = currentStatus.asStateFlow()

    suspend fun load() {
        mutex.withLock {
            if (status.value == HapticSupportStatus.Supported || status.value == HapticSupportStatus.Unsupported) return
            try {
                currentStatus.value = if (readSupport()) HapticSupportStatus.Supported else HapticSupportStatus.Unsupported
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentStatus.value = HapticSupportStatus.Failed
            }
        }
    }
}

internal object HapticCapability {
    private val state = HapticCapabilityState {
        withContext(Dispatchers.Default) { loadPlatformHapticSupport() }
    }
    val status = state.status
    suspend fun load() = state.load()
}

/** キャッシュの障害は判定を妨げず、判定の失敗は非対応として保存しない。 */
internal fun loadCachedHapticSupport(
    readCache: () -> String?,
    detectSupport: () -> Boolean,
    writeCache: (String) -> Unit,
): Boolean {
    optionalCacheOperation { readCache()?.toBooleanStrictOrNull() }?.let { return it }
    val supported = detectSupport()
    optionalCacheOperation { writeCache(supported.toString()) }
    return supported
}

private inline fun <T> optionalCacheOperation(action: () -> T): T? = try {
    action()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

internal expect fun loadPlatformHapticSupport(): Boolean
