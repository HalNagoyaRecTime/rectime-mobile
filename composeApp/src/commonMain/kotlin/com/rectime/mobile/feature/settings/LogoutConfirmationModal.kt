package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rectime.mobile.ui.theme.AppTheme

private val ButtonHeight = 48.dp
private val ButtonSpacing = 12.dp
private val HeaderToButtonSpacing = 32.dp // ← 見出しの下だけの余白
private val ModalHorizontalPadding = 32.dp
private val ModalTopPadding = 48.dp // ← 見出しの上だけの余白。ここを変えれば見出しの上だけ広がる
private val ModalBottomPadding = 32.dp

/**
 * ログアウト確認モーダル。
 * AppModal（設定画面用モーダルの共通土台）の上に、見出し・実行ボタン・キャンセルボタンを乗せる。
 */
@Composable
fun LogoutConfirmationModal(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppModal(
        onDismiss = onDismiss,
        // PaddingValuesで上下左右を個別に指定。上だけModalTopPaddingで別管理にしている
        contentPadding = PaddingValues(
            start = ModalHorizontalPadding,
            end = ModalHorizontalPadding,
            top = ModalTopPadding,
            bottom = ModalBottomPadding,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "ログアウトしますか？",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.colors.textSettingModalHeader,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(HeaderToButtonSpacing))

            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(AppTheme.radius.full),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.colors.themeColorFirst,
                    contentColor = AppTheme.colors.commonBackground,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonHeight),
            ) {
                Text(
                    text = "ログアウトする",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(ButtonSpacing))

            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(AppTheme.radius.full),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AppTheme.colors.textSettingModalBody,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ButtonHeight),
            ) {
                Text(
                    text = "キャンセル",
                    fontSize = 15.sp,
                )
            }
        }
    }
}