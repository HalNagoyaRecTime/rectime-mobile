@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rectime.mobile.core.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.CoreHaptics.CHHapticEngine
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

private class IosAppHapticFeedback : AppHapticFeedback {
    private val generator by lazy {
        UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    }

    private val preferenceGenerator by lazy {
        UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    }

    override fun perform(event: AppHapticEvent) {
        if (!HapticCapability.status.value.canVibrate) return
        val feedback = when (event) {
            AppHapticEvent.RefreshThreshold -> generator
            AppHapticEvent.LogoTap, AppHapticEvent.PreferenceEnabled, AppHapticEvent.PreferenceDisabled -> preferenceGenerator
        }

        // ハードウェア対応を確認した後の出力は、OSの設定に従う。
        runCatching {
            feedback.prepare()
            feedback.impactOccurred()
        }
    }
}

@Composable
actual fun rememberPlatformHapticFeedback(): AppHapticFeedback =
    remember { IosAppHapticFeedback() }

internal actual fun loadPlatformHapticSupport(): Boolean {
    val directory = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
        .firstOrNull()?.toString()?.toPath()?.resolve("rectime-device-capabilities")
    val file = directory?.resolve("haptics-v1")
    return loadCachedHapticSupport(
        readCache = { file?.let { FileSystem.SYSTEM.read(it) { readUtf8() } } },
        detectSupport = { CHHapticEngine.capabilitiesForHardware().supportsHaptics },
        writeCache = { value ->
            checkNotNull(directory)
            checkNotNull(file)
            FileSystem.SYSTEM.createDirectories(directory)
            check(NSURL.fileURLWithPath(directory.toString()).setResourceValue(
                NSNumber(bool = true), NSURLIsExcludedFromBackupKey, null,
            ))
            FileSystem.SYSTEM.write(file) { writeUtf8(value) }
        },
    )
}
