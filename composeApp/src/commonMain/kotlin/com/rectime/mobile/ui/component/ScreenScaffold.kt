package com.rectime.mobile.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rectime.mobile.core.model.UserProfile
import com.rectime.mobile.ui.theme.AppTheme

@Composable
fun RootScreenScaffold(
    title: String,
    profile: UserProfile,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Boolean = true,
    onTrailingClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val hPad = AppTheme.layout.screenHorizontalPadding
    val spacing = AppTheme.layout.headerSpacing

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + spacing + AppTheme.layout.headerAction + spacing,
                bottom = AppTheme.layout.rootBottomNavigationInset,
                start = if (horizontalPadding) hPad else 0.dp,
                end = if (horizontalPadding) hPad else 0.dp,
            ),
            content = content,
        )
        RootHeader(
            title = title,
            profile = profile,
            onOpenMenu = onOpenMenu,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = hPad)
                .padding(top = spacing),
            onTrailingClick = onTrailingClick,
            trailing = trailing,
        )
    }
}

@Composable
fun PushScreenScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Boolean = true,
    onTrailingClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    bottomContent: @Composable (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val hPad = AppTheme.layout.screenHorizontalPadding
    val spacing = AppTheme.layout.headerSpacing

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + spacing + AppTheme.layout.headerAction + spacing,
                start = if (horizontalPadding) hPad else 0.dp,
                end = if (horizontalPadding) hPad else 0.dp,
            ),
            content = content,
        )
        PushAppBar(
            title = title,
            onBack = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = hPad)
                .padding(top = spacing),
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
        SheetAppBar(
            title = title,
            onClose = onClose,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = hPad)
                .padding(top = spacing),
        )
    }
}
