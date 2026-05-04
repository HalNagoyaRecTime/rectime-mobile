package com.rectime.mobile.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.rectime.mobile.feature.calendar.CalendarScreen
import com.rectime.mobile.feature.home.HomeScreen
import com.rectime.mobile.feature.result.PushCardScreen
import com.rectime.mobile.feature.result.SampleSheet
import com.rectime.mobile.feature.theme.ThemeSheet
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
            onPushFromMenu = { route ->
                navigationController.push(
                    route = route,
                    source = PushTransitionSource.SideMenu,
                )
            },
            onPresentThemeSheet = { navigationController.presentSheet(SheetRoute.ThemeSheet) },
            modifier = Modifier.background(AppTheme.colors.navigationBackground),
        )

        // Layer 1: Root (Home / Calendar)
        RootLayer(
            state = state,
            navigationController = navigationController,
            revealWidthPx = revealWidthPx,
        ) {
            // --- UI Content Selection (Root) ---
            when (state.rootRoute) {
                RootRoute.Home -> {
                    HomeScreen(
                        onOpenMenu = { navigationController.openMenu() },
                        onOpenNotifications = { navigationController.push(PushRoute.Notifications) },
                        onOpenMatchInfo = { navigationController.push(PushRoute.MatchInfo) },
                        onOpenDetail = { navigationController.push(PushRoute.Detail) },
                        onPresentTicket = { navigationController.presentSheet(SheetRoute.TicketSheet) },
                        onOpenOtherQuickAction = { navigationController.push(PushRoute.Notifications) },
                    )
                }
                RootRoute.Calendar -> {
                    CalendarScreen(
                        onOpenMenu = { navigationController.openMenu() },
                        onOpenNotifications = { navigationController.push(PushRoute.Notifications) },
                        onOpenEventDetail = { navigationController.push(PushRoute.Detail) },
                    )
                }
            }
        }

        // Layer 2: Push Stack (Detail screens)
        PushLayer(
            state = state,
            navigationController = navigationController,
            containerWidthPx = containerWidthPx,
        ) { route ->
            // --- UI Content Selection (Push) ---
            PushCardScreen(
                route = route,
                onRequestPop = { navigationController.requestPop() },
                onPresentSampleSheet = { navigationController.presentSheet(SheetRoute.SampleSheet) },
            )
        }

        // Layer 3: Sheet (Modals)
        SheetLayer(
            state = state,
            navigationController = navigationController,
            containerHeightPx = containerHeightPx,
        ) { route ->
            // --- UI Content Selection (Sheet) ---
            when (route) {
                SheetRoute.ThemeSheet -> ThemeSheet(themeStateHolder = themeStateHolder)
                SheetRoute.TicketSheet -> {
                    SampleSheet(
                        title = "マイQR",
                        description = "チケット情報のワイヤー表示",
                        onPrimaryAction = { navigationController.requestDismissSheet() },
                    )
                }
                SheetRoute.SampleSheet -> {
                    SampleSheet(
                        title = "SampleSheet",
                        description = "fullレイアウトのボトムシート",
                        onPrimaryAction = { navigationController.requestDismissSheet() },
                    )
                }
            }
        }
    }
}
