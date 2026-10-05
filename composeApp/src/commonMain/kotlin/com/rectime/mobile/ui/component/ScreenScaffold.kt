package com.rectime.mobile.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.theme.AppTheme

private val SnackbarBottomOffset = (-60).dp // ボトムナビゲーションとの重なりを避けるためのオフセット

@Composable
fun RootScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    lazyListState: LazyListState = rememberLazyListState(),
    horizontalPadding: Boolean = true,
    contentTopPadding: Boolean = true,
    contentBottomPadding: Boolean = true,
    headerEdgeFade: Boolean = true,
    onTrailingClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState? = null,
    isRefreshing: Boolean = false,
    refreshEnabled: Boolean = true,
    onRefresh: (() -> Unit)? = null,
    contentBackground: @Composable BoxScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val hPad = AppTheme.layout.screenHorizontalPadding
    val spacing = AppTheme.layout.headerSpacing
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerBottom = if (headerHeightPx > 0) {
        with(LocalDensity.current) { headerHeightPx.toDp() }
    } else {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + AppTheme.layout.headerAction
    }

    val topInset = if (contentTopPadding) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + spacing + AppTheme.layout.headerAction + spacing
    } else {
        0.dp
    }

    val bottomInset = if (contentBottomPadding) {
        AppTheme.layout.rootBottomNavigationInset
    } else {
        0.dp
    }

    Box(modifier = modifier.fillMaxSize()) {
        val listContent: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize()) {
                contentBackground()
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    overscrollEffect = if (onRefresh == null) rememberOverscrollEffect() else null,
                    contentPadding = PaddingValues(
                        top = topInset,
                        bottom = bottomInset,
                        start = if (horizontalPadding) hPad else 0.dp,
                        end = if (horizontalPadding) hPad else 0.dp,
                    ),
                    content = content,
                )
            }
        }
        if (onRefresh == null) {
            listContent()
        } else {
            PullToRefreshContainer(
                isRefreshing = isRefreshing,
                refreshEnabled = refreshEnabled,
                onRefresh = onRefresh,
                indicatorTopInset = headerBottom,
                modifier = Modifier.fillMaxSize(),
            ) {
                listContent()
            }
        }
        if (headerEdgeFade) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppTheme.layout.headerEdgeFade)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                AppTheme.colors.edgeFadeColor,
                                AppTheme.colors.edgeFadeColor.copy(alpha = 0f),
                            ),
                        ),
                    ),
            )
        }
        RootHeader(
            title = title,
            modifier = Modifier.onSizeChanged { headerHeightPx = it.height },
            onTrailingClick = onTrailingClick,
            trailing = trailing,
        )
        if (snackbarHostState != null){
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter).absoluteOffset(0.dp,SnackbarBottomOffset)
                    .padding(16.dp),
            )
        }

    }
}

@Composable
fun PushScreenScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Boolean = true,
    contentTopPadding: Boolean = true,
    headerEdgeFade: Boolean = true,
    onTrailingClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    bottomContent: @Composable (() -> Unit)? = null,
    contentBackground: @Composable BoxScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val hPad = AppTheme.layout.screenHorizontalPadding
    val spacing = AppTheme.layout.headerSpacing

    val topInset = if (contentTopPadding) {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + spacing + AppTheme.layout.headerAction + spacing
    } else {
        0.dp
    }

    Box(modifier = modifier
        .fillMaxSize()
        .background(AppTheme.colors.commonBackground),
    ) {
        contentBackground()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = topInset,
                start = if (horizontalPadding) hPad else 0.dp,
                end = if (horizontalPadding) hPad else 0.dp,
            ),
            content = content,
        )
        if (headerEdgeFade) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppTheme.layout.headerEdgeFade)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                AppTheme.colors.edgeFadeColor,
                                AppTheme.colors.edgeFadeColor.copy(alpha = 0f),
                            ),
                        ),
                    ),
            )
        }
        PushHeader(
            title = title,
            onBack = onBack,
            onTrailingClick = onTrailingClick,
            trailing = trailing,
        )
        bottomContent?.let {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 32.dp, vertical = 48.dp,),
            ) {
                it()
            }
        }
    }
}

@Composable
fun SheetScaffold(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    horizontalPadding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hPad = AppTheme.layout.screenHorizontalPadding
    val spacing = AppTheme.layout.headerSpacing

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(top = spacing + AppTheme.layout.headerAction + spacing, bottom = 24.dp)
                .then(if (horizontalPadding) Modifier.padding(horizontal = hPad) else Modifier),
            content = content,
        )
        SheetHeader(
            title = title,
            onClose = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = hPad)
                .padding(top = spacing),
        )
    }
}
