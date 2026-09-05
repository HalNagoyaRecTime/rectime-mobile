package com.rectime.mobile.feature.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rectime.mobile.feature.legal.LegalDocumentLinks
import com.rectime.mobile.ui.component.AppLogoMark
import com.rectime.mobile.ui.component.ProductionCredits
import com.rectime.mobile.ui.theme.AppTheme

@Composable
fun AuthGate(
    viewModel: AuthViewModel,
    content: @Composable (AuthSession, () -> Unit) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session = state.session

    if (session == null) {
        AuthLoginScreen(
            state = state,
            onLogin = viewModel::startLogin,
        )
    } else {
        content(session, viewModel::logout)
    }
}

@Composable
private fun AuthLoginScreen(
    state: AuthUiState,
    onLogin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.commonBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = AppTheme.layout.screenHorizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppLogoSection(modifier = Modifier.offset(y = (-20).dp))

                Spacer(modifier = Modifier.height(AppTheme.spacing.xxl))

                SignInSection(
                    isLoading = state.isLoading,
                    error = state.error,
                    onLogin = onLogin,
                )
            }
        }

        LegalDocumentLinks()

        Spacer(modifier = Modifier.height(AppTheme.spacing.md))

        ProductionCredits(
            modifier = Modifier.padding(bottom = AppTheme.spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        )
    }
}

@Composable
private fun AppLogoSection(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppLogoMark()

        Text(
            text = "RE:CREATION",
            modifier = Modifier.offset(y=(-20).dp),
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            color = AppTheme.colors.textAppLogo,
        )
    }
}

@Composable
private fun SignInSection(
    isLoading: Boolean,
    error: String?,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MicrosoftSignInButton(
            isLoading = isLoading,
            onClick = onLogin,
        )

        // エラーの有無でブロックの高さが変わると中央寄せの位置がずれるため、常に1行ぶん確保しておく。
        Spacer(modifier = Modifier.height(AppTheme.spacing.sm))

        Text(
            text = error.orEmpty(),
            fontSize = 13.sp,
            color = AppTheme.colors.textLoginError,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MicrosoftSignInButton(
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(AppTheme.radius.xs)

    Row(
        modifier = Modifier
            .height(48.dp)
            .alpha(if (isLoading) 0.72f else 1f)
            .clip(shape)
            .background(AppTheme.colors.loginButtonBackground)
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = AppTheme.spacing.xl),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MicrosoftLogo(modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.size(AppTheme.spacing.md))
        Box(contentAlignment = Alignment.Center) {
            // 「サインイン中...」に切り替わってもボタン幅が変わらないよう、既定の文言で幅を確保しておく。
            Text(
                text = "Microsoft アカウントでサインイン",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = AppTheme.colors.textLoginButton,
                modifier = Modifier.alpha(if (isLoading) 0f else 1f),
            )
            if (isLoading) {
                Text(
                    text = "サインイン中...",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = AppTheme.colors.textLoginButton,
                )
            }
        }
    }
}

@Composable
private fun MicrosoftLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val gap = size.minDimension * 0.095f
        val tile = (size.minDimension - gap) / 2f
        drawRect(
            color = Color(0xFFF25022),
            topLeft = Offset.Zero,
            size = Size(tile, tile),
        )
        drawRect(
            color = Color(0xFF7FBA00),
            topLeft = Offset(tile + gap, 0f),
            size = Size(tile, tile),
        )
        drawRect(
            color = Color(0xFF00A4EF),
            topLeft = Offset(0f, tile + gap),
            size = Size(tile, tile),
        )
        drawRect(
            color = Color(0xFFFFB900),
            topLeft = Offset(tile + gap, tile + gap),
            size = Size(tile, tile),
        )
    }
}
