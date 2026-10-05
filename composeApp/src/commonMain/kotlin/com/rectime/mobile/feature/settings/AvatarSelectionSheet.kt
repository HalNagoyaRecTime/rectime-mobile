package com.rectime.mobile.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rectime.mobile.ui.component.AvatarBackgrounds
import com.rectime.mobile.ui.component.avatarSeed
import com.rectime.mobile.ui.component.avatarSport
import com.rectime.mobile.ui.component.SportAvatarPictogram
import com.rectime.mobile.ui.component.UserAvatar
import com.rectime.mobile.ui.theme.AppTheme
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AvatarSelectionSheet(
    userId: String,
    photoBytes: ByteArray?,
    selection: AvatarSelection,
    onSelect: (AvatarSelection) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.colors.commonBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp).selectableGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("プロフィールアイコン", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textPrimary)
            Spacer(Modifier.height(16.dp))
            // 候補の色はシートを開いた時だけ選び、再描画では変えない。
            val candidateColors = remember(userId) {
                SportAvatarPictogram.entries.associateWith { Random.nextInt(AvatarBackgrounds.size) }
            }
            val choices = buildList {
                add(if (photoBytes != null) AvatarSelection(photo = true) else AvatarSelection())
                addAll(SportAvatarPictogram.entries.map { sport ->
                    AvatarSelection(sport = sport, colorIndex = candidateColors.getValue(sport))
                })
            }
            choices.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { choice ->
                        val automaticAction = choice.isAutomatic
                        val selected = !automaticAction && (
                            if (choice.photo) selection.sport == null && photoBytes != null
                            else selection.sport == choice.sport
                        )
                        val displayedChoice = if (selected && !choice.photo) choice.copy(colorIndex = selection.colorIndex)
                            else choice
                        val action = if (automaticAction) Modifier.clickable(role = Role.Button,
                            onClick = { onSelect(randomAvatarSelection(userId, selection)) })
                        else Modifier.selectable(selected = selected, role = Role.RadioButton,
                            onClick = { onSelect(displayedChoice) })
                        Column(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                .border(2.dp, if (selected) AppTheme.colors.themeColorSecond else Color.Transparent,
                                    RoundedCornerShape(16.dp))
                                .then(action)
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            UserAvatar(userId, photoBytes, Modifier.widthIn(max = 64.dp).fillMaxWidth().aspectRatio(1f),
                                sportOverride = if (automaticAction) selection.sport else displayedChoice.sport,
                                usePhoto = choice.photo,
                                colorIndex = if (automaticAction) selection.colorIndex else displayedChoice.colorIndex)
                            Spacer(Modifier.height(6.dp))
                            Text(choice.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = AppTheme.colors.textPrimary, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            val colorsEnabled = selection.sport != null || photoBytes == null
            Spacer(Modifier.height(16.dp))
            Text("背景色", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                color = if (colorsEnabled) AppTheme.colors.textPrimary else AppTheme.colors.textMuted)
            val disabledSlashColor = AppTheme.colors.textMuted.copy(alpha = 0.65f)
            val currentColor = selection.colorIndex ?: avatarSeed(userId) % AvatarBackgrounds.size
            BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
                val columns = if (maxWidth >= 288.dp) 6 else 3
                Column {
                    AvatarBackgrounds.indices.chunked(columns).forEach { colors ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            colors.forEach { index ->
                                Box(
                                    modifier = Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp))
                                        .semantics {
                                            contentDescription = "背景色：${AvatarColorNames[index]}"
                                            if (!colorsEnabled) stateDescription = "プロフィール写真では変更できません"
                                        }
                                        .selectable(enabled = colorsEnabled, selected = colorsEnabled && currentColor == index,
                                            role = Role.RadioButton,
                                            onClick = {
                                                onSelect(AvatarSelection(sport = selection.sport ?: avatarSport(userId),
                                                    colorIndex = index))
                                            }),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier.size(36.dp)
                                            .background(if (colorsEnabled) AvatarBackgrounds[index]
                                                else AvatarBackgrounds[index].copy(alpha = 0.35f), CircleShape)
                                            .border(2.dp, if (colorsEnabled && currentColor == index) AppTheme.colors.themeColorSecond
                                                else AppTheme.colors.borderSubtle, CircleShape)
                                            .drawWithContent {
                                                drawContent()
                                                if (!colorsEnabled) drawLine(
                                                    color = disabledSlashColor,
                                                    start = Offset(size.width * 0.22f, size.height * 0.78f),
                                                    end = Offset(size.width * 0.78f, size.height * 0.22f),
                                                    strokeWidth = 1.5.dp.toPx(),
                                                )
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (colorsEnabled && currentColor == index) Text("✓", color = Color(0xFF354657))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private val AvatarColorNames = listOf("水色", "ピンク", "黄色", "青", "紫", "緑")

// 毎回の再描画では抽選せず、ボタンを押した時だけ結果を選んで保存する。
internal fun randomAvatarSelection(userId: String, selection: AvatarSelection, random: Random = Random.Default): AvatarSelection {
    val colorCount = AvatarBackgrounds.size
    val sport = selection.sport ?: avatarSport(userId)
    val color = selection.colorIndex ?: avatarSeed(userId) % colorCount
    val previous = sport.ordinal * colorCount + color
    val draw = random.nextInt(SportAvatarPictogram.entries.size * colorCount - 1)
    val next = if (draw >= previous) draw + 1 else draw
    return AvatarSelection(sport = SportAvatarPictogram.entries[next / colorCount], colorIndex = next % colorCount)
}

private val AvatarSelection.label: String get() = if (photo) "プロフィール写真" else when (sport) {
    SportAvatarPictogram.Basketball -> "バスケットボール"
    SportAvatarPictogram.Volleyball -> "バレーボール"
    SportAvatarPictogram.Soccer -> "サッカー"
    SportAvatarPictogram.Badminton -> "バドミントン"
    SportAvatarPictogram.TableTennis -> "卓球"
    SportAvatarPictogram.Tennis -> "テニス"
    SportAvatarPictogram.Baseball -> "野球"
    SportAvatarPictogram.Run -> "ランニング"
    null -> "自動"
}
