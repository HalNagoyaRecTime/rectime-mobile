package com.rectime.mobile.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.ic_app_logo

@Composable
fun AppLogoMark(
    modifier: Modifier = Modifier,
    size: Dp = 260.dp,
) {
    Image(
        painter = painterResource(Res.drawable.ic_app_logo),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}
