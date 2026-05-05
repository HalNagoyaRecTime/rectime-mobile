package com.rectime.mobile.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.rectime.mobile.feature.home.HomeScreen
import com.rectime.mobile.ui.component.SideMenu
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.theme.ThemeStateHolder
import com.rectime.mobile.ui.token.GestureTokens

@Composable
fun NavigationHost(
    navigationController: NavigationController,
    themeStateHolder: ThemeStateHolder,
) {
    val state = navigationController.state

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.surfacePrimary),
    ) {
        val layout = AppTheme.layout
        val revealWidthDp = maxOf(
            layout.sideMenuRevealMin,
            minOf(maxWidth * GestureTokens.sideMenuRevealRatio, layout.sideMenuRevealMax),
        )
        val densityScale = LocalDensity.current.density
        val revealWidthPx = revealWidthDp.value * densityScale
        val containerWidthPx = maxWidth.value * densityScale
        val containerHeightPx = maxHeight.value * densityScale

        // Background Side Menu
        SideMenu(
            revealWidthDp = revealWidthDp,
            onPushFromMenu = { screen ->
                navigationController.push(screen)
            },
            onPresentThemeSheet = { sheet -> 
                navigationController.presentSheet(sheet) 
            },
            themeStateHolder = themeStateHolder,
            modifier = Modifier.background(AppTheme.colors.navigationBackground),
        )

        // Layer 1: Root (Home / Calendar)
        RootLayer(
            state = state,
            navigationController = navigationController,
            revealWidthPx = revealWidthPx,
        )

        // Layer 2: Push Stack (Detail screens)
        PushLayer(
            state = state,
            navigationController = navigationController,
            containerWidthPx = containerWidthPx,
        )

        // Layer 3: Sheet (Modals)
        SheetLayer(
            state = state,
            navigationController = navigationController,
            containerHeightPx = containerHeightPx,
        )
    }
}
