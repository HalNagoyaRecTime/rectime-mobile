package com.rectime.mobile.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rectime.mobile.ui.theme.AppTheme
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.rectime_logo

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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp, vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppMark()

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "RecTime",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.textPrimary,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            MicrosoftLoginButton(
                isLoading = state.isLoading,
                onClick = onLogin,
            )

            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = AppTheme.colors.textPrimary,
                )
            }

            if (state.message.isNotBlank()) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AppMark() {
    androidx.compose.foundation.Image(
        painter = org.jetbrains.compose.resources.painterResource(Res.drawable.rectime_logo),
        contentDescription = null,
        modifier = Modifier.size(84.dp, 60.dp),
    )
}

@Composable
private fun MicrosoftLoginButton(
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .alpha(if (isLoading) 0.72f else 1f)
            .clip(shape)
            .background(AppTheme.colors.navigationSurface)
            .border(
                border = BorderStroke(1.dp, AppTheme.colors.borderStrong),
                shape = shape,
            )
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MicrosoftLogo(modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = if (isLoading) {
                "ログイン中..."
            } else {
                "Microsoft アカウントでログイン"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
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
