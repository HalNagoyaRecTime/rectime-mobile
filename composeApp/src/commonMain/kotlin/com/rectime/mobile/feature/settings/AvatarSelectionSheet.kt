package com.rectime.mobile.feature.settings

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
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
                    .border(2.dp, if (selection.isAutomatic) AppTheme.colors.themeColorSecond else AppTheme.colors.borderSubtle,
                        RoundedCornerShape(16.dp))
                    .selectable(selected = selection.isAutomatic, role = Role.RadioButton,
                        onClick = { onSelect(AvatarSelection()) }).padding(14.dp),
            ) {
                UserAvatar(userId, photoBytes, Modifier.size(48.dp))
                Text(if (selection.isAutomatic) "✓ 自動" else "自動", color = AppTheme.colors.textPrimary,
                    fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(16.dp))
            val choices = buildList {
                if (photoBytes != null) add(AvatarSelection(photo = true))
                addAll(SportAvatarPictogram.entries.map { AvatarSelection(sport = it) })
            }
            choices.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { choice ->
                        val selected = !selection.isAutomatic && selection.sport == choice.sport && selection.photo == choice.photo
                        Column(
                            modifier = Modifier.weight(1f)
                                .border(2.dp, if (selected) AppTheme.colors.themeColorSecond else Color.Transparent,
                                    RoundedCornerShape(16.dp))
                                .selectable(selected = selected, role = Role.RadioButton, onClick = {
                                    onSelect(if (choice.photo) choice else choice.copy(colorIndex = selection.colorIndex))
                                })
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            UserAvatar(userId, photoBytes, Modifier.widthIn(max = 64.dp).fillMaxWidth().aspectRatio(1f),
                                sportOverride = choice.sport, usePhoto = choice.photo, colorIndex = selection.colorIndex)
                            Spacer(Modifier.height(6.dp))
                            Text(choice.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = AppTheme.colors.textPrimary, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (selection.sport != null || photoBytes == null) {
                Spacer(Modifier.height(16.dp))
                Text("背景色", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    color = AppTheme.colors.textPrimary)
                val currentColor = selection.colorIndex ?: avatarSeed(userId) % AvatarBackgrounds.size
                BoxWithConstraints(Modifier.fillMaxWidth().selectableGroup()) {
                    val columns = if (maxWidth >= 288.dp) 6 else 3
                    Column {
                        AvatarBackgrounds.indices.chunked(columns).forEach { colors ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                colors.forEach { index ->
                                    Box(
                                        modifier = Modifier.weight(1f).height(48.dp)
                                            .semantics { contentDescription = "背景色：${AvatarColorNames[index]}" }
                                            .selectable(selected = currentColor == index, role = Role.RadioButton,
                                                onClick = {
                                                    onSelect(AvatarSelection(sport = selection.sport ?: avatarSport(userId),
                                                        colorIndex = index))
                                                }),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Box(
                                            modifier = Modifier.size(36.dp).background(AvatarBackgrounds[index], CircleShape)
                                                .border(2.dp, if (currentColor == index) AppTheme.colors.themeColorSecond
                                                    else AppTheme.colors.borderSubtle, CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (currentColor == index) Text("✓", color = Color(0xFF354657))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            TextButton(onClick = { onSelect(randomAvatarSelection(userId, selection)) }) {
                Text("ランダムで選ぶ", color = AppTheme.colors.themeColorSecond)
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
