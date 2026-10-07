package com.rectime.mobile.feature.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rectime.mobile.core.config.appDisplayName
import com.rectime.mobile.core.haptics.AppHapticEvent
import com.rectime.mobile.core.haptics.LocalHapticPreference
import com.rectime.mobile.core.haptics.rememberPlatformHapticFeedback
import com.rectime.mobile.core.platform.openExternalUrl
import com.rectime.mobile.feature.legal.LegalDocument
import com.rectime.mobile.feature.legal.LegalDocumentLinks
import com.rectime.mobile.ui.component.AppBrandTitle
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

    if (state.isRestoringSession) {
        // 端末内の復元が終わるまでログイン画面を出さない。通信の完了は待たない。
        Box(Modifier.fillMaxSize().background(AppTheme.colors.commonBackground))
    } else if (session == null) {
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
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.getTop(density)
    val horizontalPadding = with(density) { AppTheme.layout.screenHorizontalPadding.roundToPx() }
    BoxWithConstraints(Modifier.fillMaxSize().background(AppTheme.colors.commonBackground)) {
        val viewportHeight = with(density) { maxHeight.roundToPx() }
        // ロゴの大きさは保ち、静止画面ではタイトルを実際のロゴ幅に近づける。
        val iconSize = minOf(220.dp, maxWidth * 0.57f)
        val titleSize = minOf(24f, maxWidth.value / 16f).sp
        Layout(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            content = {
                AppLogoSection(iconSize = iconSize, titleSize = titleSize)
                SignInSection(isLoading = state.isLoading, error = state.error, onLogin = onLogin)
                AuthFooter()
            },
        ) { measurables, constraints ->
            val contentWidth = (constraints.maxWidth - horizontalPadding * 2).coerceAtLeast(0)
            val childConstraints = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = contentWidth)
            val logo = measurables[0].measure(childConstraints)
            val signIn = measurables[1].measure(childConstraints.copy(maxWidth = min(contentWidth, 360.dp.roundToPx())))
            val footer = measurables[2].measure(childConstraints)
            val gap = 24.dp.roundToPx()
            val top = topInset + 16.dp.roundToPx()
            // エラーの高さに関係なくロゴ・ボタンの位置を保つ。
            val preferredLogoY = (viewportHeight * 0.36f).toInt() - iconSize.roundToPx() / 2
            val logoY = max(top, preferredLogoY)
            val signInY = logoY + logo.height + gap
            // 長いエラーは下方向へ伸ばし、画面に収まらない場合は画面全体をスクロールする。
            val height = max(viewportHeight, signInY + signIn.height + gap + footer.height)
            val footerY = height - footer.height
            layout(constraints.maxWidth, height) {
                logo.placeRelative((constraints.maxWidth - logo.width) / 2, logoY)
                signIn.placeRelative((constraints.maxWidth - signIn.width) / 2, signInY)
                footer.placeRelative((constraints.maxWidth - footer.width) / 2, footerY)
            }
        }
    }
}

@Composable
private fun AppLogoSection(
    iconSize: Dp,
    titleSize: TextUnit,
) {
    val hapticFeedback = rememberPlatformHapticFeedback()
    val vibrationEnabled by LocalHapticPreference.current.enabled.collectAsStateWithLifecycle()
    val jump = remember { Animatable(0f) }
    var tapCount by remember { mutableIntStateOf(0) }
    var jumpingLetterIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(tapCount) {
        if (tapCount == 0) return@LaunchedEffect
        // 再タップでは進行中のジャンプをキャンセルし、最初から跳ね直す。
        jump.snapTo(0f)
        jump.animateTo(-14f, tween(durationMillis = 110))
        jump.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 550f))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AppLogoMark(
            size = iconSize,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "タイトルを跳ねさせる",
            ) {
                if (vibrationEnabled) hapticFeedback.perform(AppHapticEvent.LogoTap)
                jumpingLetterIndex = Random.nextInt(appDisplayName.length)
                tapCount++
            },
        )
        Spacer(Modifier.height(8.dp))
        AppBrandTitle(
            fontSize = titleSize,
            jumpingLetterIndex = jumpingLetterIndex,
            jumpOffset = { jump.value.dp },
        )
    }
}

@Composable
private fun AuthFooter() {
    Column(
        modifier = Modifier.navigationBarsPadding().padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LegalDocumentLinks(openUrl = ::openExternalUrl) { enabled, open ->
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(enabled = enabled, onClick = { open(LegalDocument.Terms) }) {
                    Text("利用規約", fontSize = 13.sp, color = AppTheme.colors.textSecondary)
                }
                TextButton(enabled = enabled, onClick = { open(LegalDocument.PrivacyPolicy) }) {
                    Text("プライバシーポリシー", fontSize = 13.sp, color = AppTheme.colors.textSecondary)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        ProductionCredits(horizontalAlignment = Alignment.CenterHorizontally, lineHeight = 16.sp)
    }
}

@Composable
private fun SignInSection(
    isLoading: Boolean,
    error: String?,
    onLogin: () -> Unit,
) {
    val shape = RoundedCornerShape(AppTheme.radius.xs)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val baseStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // ボタン内のアイコンと余白を除いた幅で決め、エラーにも同じ文字サイズを使う。
        val textWidth = with(density) {
            (maxWidth.roundToPx() - AppTheme.spacing.xl.roundToPx() * 2 -
                MicrosoftSignInIconSize.roundToPx() - AppTheme.spacing.md.roundToPx()).coerceAtLeast(0)
        }
        val fontSize = (28 downTo 24).firstOrNull { halfSp ->
            textMeasurer.measure(
                text = MicrosoftSignInLabel,
                style = baseStyle.copy(fontSize = (halfSp / 2f).sp),
                softWrap = false,
            ).size.width <= textWidth
        }?.let { (it / 2f).sp } ?: 12.sp
        val textStyle = baseStyle.copy(fontSize = fontSize, lineHeight = (20f * fontSize.value / 14f).sp)
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            MicrosoftSignInButton(isLoading = isLoading, textStyle = textStyle, onClick = onLogin)
            Spacer(Modifier.height(8.dp))
            // 内容全体を表示し、上下に同じ余白を取る。ロゴとボタンの位置は親で固定する。
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                if (!error.isNullOrBlank()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape).background(Color(0xFFB51F32))
                            .padding(16.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = error,
                            style = textStyle.copy(fontWeight = FontWeight.Normal),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private const val MicrosoftSignInLabel = "Microsoft アカウントでサインイン"
private val MicrosoftSignInIconSize = 18.dp

@Composable
private fun MicrosoftSignInButton(
    isLoading: Boolean,
    textStyle: TextStyle,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(AppTheme.radius.xs)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .alpha(if (isLoading) 0.72f else 1f)
            .clip(shape)
            .background(AppTheme.colors.loginButtonBackground)
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = AppTheme.spacing.xl, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MicrosoftLogo(modifier = Modifier.size(MicrosoftSignInIconSize))
        Spacer(modifier = Modifier.size(AppTheme.spacing.md))
        Box(contentAlignment = Alignment.Center) {
            // 「サインイン中...」に切り替わってもボタン幅が変わらないよう、既定の文言で幅を確保しておく。
            Text(
                text = MicrosoftSignInLabel,
                style = textStyle,
                textAlign = TextAlign.Center,
                color = AppTheme.colors.textLoginButton,
                modifier = Modifier.alpha(if (isLoading) 0f else 1f),
            )
            if (isLoading) {
                Text(
                    text = "サインイン中...",
                    style = textStyle,
                    textAlign = TextAlign.Center,
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
