package com.rectime.mobile.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.DrawableResource
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.ic_sport_basketball
import rectime_mobile.composeapp.generated.resources.ic_sport_volleyball
import rectime_mobile.composeapp.generated.resources.ic_sport_soccer
import rectime_mobile.composeapp.generated.resources.ic_sport_badminton
import rectime_mobile.composeapp.generated.resources.ic_sport_table_tennis
import rectime_mobile.composeapp.generated.resources.ic_sport_tennis
import rectime_mobile.composeapp.generated.resources.ic_sport_baseball
import rectime_mobile.composeapp.generated.resources.ic_sport_run

// スプラッシュの8競技。iOSは同じSF Symbols、Android/JVMは既存の線画を使う。
internal enum class SportAvatarPictogram(val symbol: String, val drawable: DrawableResource) {
    Basketball("figure.basketball", Res.drawable.ic_sport_basketball),
    Volleyball("figure.volleyball", Res.drawable.ic_sport_volleyball),
    Soccer("figure.soccer", Res.drawable.ic_sport_soccer),
    Badminton("figure.badminton", Res.drawable.ic_sport_badminton),
    TableTennis("figure.table.tennis", Res.drawable.ic_sport_table_tennis),
    Tennis("figure.tennis", Res.drawable.ic_sport_tennis),
    Baseball("figure.baseball", Res.drawable.ic_sport_baseball),
    Run("figure.run", Res.drawable.ic_sport_run);
}

internal fun avatarSeed(userId: String): Int =
    userId.fold(0) { value, character -> (value * 31 + character.code) and Int.MAX_VALUE }

internal fun avatarSport(userId: String): SportAvatarPictogram =
    SportAvatarPictogram.entries[(avatarSeed(userId) / 6) % SportAvatarPictogram.entries.size]

@Composable
internal expect fun sportAvatarPainter(sport: SportAvatarPictogram): Painter
