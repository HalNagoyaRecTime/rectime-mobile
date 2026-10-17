package com.rectime.mobile.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rectime.mobile.core.config.appDisplayName
import com.rectime.mobile.core.haptics.AppHapticEvent
import com.rectime.mobile.core.haptics.LocalHapticPreference
import com.rectime.mobile.core.haptics.rememberPlatformHapticFeedback
import kotlinx.coroutines.launch

/** 文字のジャンプとタップ時の振動だけを共有し、表示は呼び出し側で決める。 */
internal class BrandTitleJump(
    val onTap: () -> Unit,
    val offset: (Int) -> Dp,
)

@Composable
internal fun rememberBrandTitleJump(): BrandTitleJump {
    val hapticFeedback = rememberPlatformHapticFeedback()
    val vibrationEnabled by LocalHapticPreference.current.enabled.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val jumps = remember { List(appDisplayName.length) { Animatable(0f) } }
    val faceIndex = appDisplayName.indexOf(":C")
    val letterGroups = remember {
        appDisplayName.indices.filter { faceIndex < 0 || it != faceIndex + 1 }
    }
    val activeLetters = remember { mutableStateListOf<Int>() }
    var lastLetter by remember { mutableIntStateOf(-1) }
    return BrandTitleJump(
        onTap = onTap@{
            if (vibrationEnabled) hapticFeedback.perform(AppHapticEvent.LogoTap)
            // 動いている文字と直前の文字を除き、連打でも各ジャンプを最後まで続ける。
            val available = letterGroups.filter { it !in activeLetters && it != lastLetter }
            val index = available.randomOrNull() ?: return@onTap
            activeLetters.add(index)
            lastLetter = index
            scope.launch {
                try {
                    jumps[index].animateTo(-14f, tween(durationMillis = 110))
                    jumps[index].animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 550f))
                } finally {
                    activeLetters.remove(index)
                }
            }
        },
        offset = { index ->
            // 顔の「:C」は同じアニメーションで一緒に跳ねる。
            val group = if (faceIndex >= 0 && index == faceIndex + 1) faceIndex else index
            jumps[group].value.dp
        },
    )
}
