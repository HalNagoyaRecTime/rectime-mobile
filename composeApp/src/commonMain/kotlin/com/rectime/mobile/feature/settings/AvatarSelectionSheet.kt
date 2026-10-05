package com.rectime.mobile.feature.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import com.rectime.mobile.ui.component.SportAvatarPictogram
import com.rectime.mobile.ui.component.UserAvatar
import com.rectime.mobile.ui.theme.AppTheme

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
                        val selected = selection == choice
                        Column(
                            modifier = Modifier.weight(1f)
                                .border(2.dp, if (selected) AppTheme.colors.themeColorSecond else Color.Transparent,
                                    RoundedCornerShape(16.dp))
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(choice) })
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            UserAvatar(userId, photoBytes, Modifier.widthIn(max = 64.dp).fillMaxWidth().aspectRatio(1f),
                                sportOverride = choice.sport, usePhoto = choice.photo)
                            Spacer(Modifier.height(6.dp))
                            Text(choice.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = AppTheme.colors.textPrimary, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
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
