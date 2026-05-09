package com.rectime.mobile.ui.token

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.darwin.sysctlbyname
import platform.posix.size_tVar

// TODO: iOS 26+ では UIScreen.cornerRadius (公式 API) に切り替える。
//       現時点では Kotlin/Native バインディングに含まれていないためデバイスマップで代替。
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberDeviceCornerRadius(): Dp {
    return remember {
        val identifier = deviceIdentifier()
        (iosCornerRadiusMap[identifier] ?: 0f).dp
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun deviceIdentifier(): String {
    return memScoped {
        val size = alloc<size_tVar>()
        sysctlbyname("hw.machine", null, size.ptr, null, 0u)
        val buf = allocArray<ByteVar>(size.value.toInt())
        sysctlbyname("hw.machine", buf, size.ptr, null, 0u)
        buf.toKString()
    }
}
