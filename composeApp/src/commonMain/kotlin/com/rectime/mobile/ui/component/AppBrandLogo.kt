package com.rectime.mobile.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rectime.mobile.core.config.appDisplayName
import com.rectime.mobile.core.haptics.AppHapticEvent
import com.rectime.mobile.core.haptics.LocalHapticPreference
import com.rectime.mobile.core.haptics.rememberPlatformHapticFeedback
import kotlinx.coroutines.launch

/** ロゴのタップでランダムな文字を跳ねさせる、共通のブランド表示。 */
@Composable
fun AppBrandLogo(
    iconSize: Dp,
    titleSize: TextUnit,
    titleSpacing: Dp = 8.dp,
) {
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AppLogoMark(
            size = iconSize,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "タイトルを跳ねさせる",
            ) {
                if (vibrationEnabled) hapticFeedback.perform(AppHapticEvent.LogoTap)
                // 動いている文字と直前の文字を除き、連打でも各ジャンプを最後まで続ける。
                val available = letterGroups.filter { it !in activeLetters && it != lastLetter }
                val index = available.randomOrNull() ?: return@clickable
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
        )
        Spacer(Modifier.height(titleSpacing))
        AppBrandTitle(
            fontSize = titleSize,
            jumpOffset = { index ->
                // 顔の「:C」は同じアニメーションで一緒に跳ねる。
                val group = if (faceIndex >= 0 && index == faceIndex + 1) faceIndex else index
                jumps[group].value.dp
            },
        )
    }
}

