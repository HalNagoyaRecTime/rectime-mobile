package com.rectime.mobile.feature.event

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.core.model.EventDetail
import com.rectime.mobile.core.model.Gathering
import com.rectime.mobile.core.util.toFormattedTime
import com.rectime.mobile.feature.auth.LocalUserProfile
import com.rectime.mobile.ui.component.AppDivider
import com.rectime.mobile.ui.component.MapModal
import com.rectime.mobile.ui.component.PushScreenScaffold
import com.rectime.mobile.ui.component.headerTitleBarHeight
import com.rectime.mobile.ui.theme.AppTheme
import org.jetbrains.compose.resources.painterResource
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.ic_info_outline

// 集合時刻 99:59 は未設定を表すセンチネル値
private const val UndecidedGatheringTime = "99:59"

private val TitleBandVerticalPadding = 24.dp
private val SectionSpacing = 24.dp
private val SectionHeadingSpacing = 8.dp
private val BodyIndent = 8.dp
private val InfoIconSize = 16.dp
private val GatheringRoundWidth = 32.dp
private val GatheringTimeWidth = 60.dp
private val GatheringBadgeWidth = 44.dp
private val GatheringRowVerticalPadding = 6.dp
private val GatheringRowHorizontalPadding = 12.dp
private val ContentBottomSpacing = 48.dp

private val TitleFontSize = 28.sp
private val HeadingFontSize = 19.sp
private val BodyFontSize = 16.sp
private val GatheringFontSize = 15.sp
private val GatheringLineHeight = 22.sp

data class EventDetailScreen(val eventId: Int) : Screen {
    override val key: String = "event_detail_$eventId"

    @Composable
    override fun Content(navigationController: NavigationController) {
        val currentUserId = LocalUserProfile.current?.id?.toIntOrNull()
        val viewModel = viewModel(key = key) {
            EventDetailViewModel(eventId = eventId, currentUserId = currentUserId)
        }
        val uiState by viewModel.uiState.collectAsState()
        var isMapVisible by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize()) {
            PushScreenScaffold(
                title = "イベント詳細",
                onBack = { navigationController.requestPop() },
                horizontalPadding = false,
                contentTopPadding = false,
                headerEdgeFade = false,
            ) {
                item {
                    val error = uiState.error
                    val event = uiState.eventDetail

                    when {
                        uiState.isLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = headerTitleBarHeight() + 32.dp, bottom = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        error != null -> {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(
                                    start = AppTheme.layout.screenHorizontalPadding,
                                    end = AppTheme.layout.screenHorizontalPadding,
                                    top = headerTitleBarHeight() + 24.dp,
                                    bottom = 24.dp,
                                ),
                            )
                        }

                        event != null -> {
                            EventDetailContent(
                                event = event,
                                gatherings = uiState.gatherings,
                                attendingGatheringId = uiState.attendingGatheringId,
                                onOpenMap = { isMapVisible = true },
                            )
                        }
                    }
                }
            }

            if (isMapVisible) {
                MapModal(onDismiss = { isMapVisible = false })
            }
        }
    }
}

@Composable
private fun EventDetailContent(
    event: EventDetail,
    gatherings: List<Gathering>,
    attendingGatheringId: Int?,
    onOpenMap: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        EventTitleBand(title = event.eventName)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = AppTheme.layout.screenHorizontalPadding,
                    vertical = SectionSpacing,
                ),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            DetailSection(heading = "実施場所", onInfoClick = onOpenMap) {
                DetailBodyText(
                    text = event.venue,
                    modifier = Modifier.padding(start = BodyIndent),
                )
            }

            DetailSection(heading = "実施時間") {
                Text(
                    text = "${event.startTime.toFormattedTime().toDisplayTime()} 〜 " +
                        event.endTime.toFormattedTime().toDisplayTime(),
                    color = AppTheme.colors.textDetailsScreenBody,
                    fontSize = BodyFontSize,
                    modifier = Modifier.padding(start = BodyIndent),
                )
            }

            if (gatherings.isNotEmpty()) {
                DetailSection(heading = "集合時間・集合場所", onInfoClick = onOpenMap) {
                    GatheringList(
                        gatherings = gatherings,
                        attendingGatheringId = attendingGatheringId,
                    )
                }
            }

            event.ruleText?.takeIf { it.isNotBlank() }?.let { ruleText ->
                AppDivider()
                DetailBodyText(text = ruleText)
            }
        }

        Spacer(modifier = Modifier.height(ContentBottomSpacing))
    }
}

@Composable
private fun EventTitleBand(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.colors.themeColorSecond)
            .padding(
                start = AppTheme.layout.screenHorizontalPadding,
                end = AppTheme.layout.screenHorizontalPadding,
                top = headerTitleBarHeight() + TitleBandVerticalPadding,
                bottom = TitleBandVerticalPadding,
            ),
    ) {
        Text(
            text = title,
            modifier = Modifier.offset(y=10.dp),
            color = AppTheme.colors.textThemeColorSecond,
            fontSize = TitleFontSize,
            lineHeight = TitleFontSize * 1.3f,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun DetailSection(
    heading: String,
    onInfoClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SectionHeadingSpacing)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = heading,
                color = AppTheme.colors.textDetailsScreenBody,
                fontSize = HeadingFontSize,
                fontWeight = FontWeight.Bold,
            )
            if (onInfoClick != null) {
                Icon(
                    painter = painterResource(Res.drawable.ic_info_outline),
                    contentDescription = "会場マップを開く",
                    tint = AppTheme.colors.themeColorFirst,
                    modifier = Modifier
                        .size(InfoIconSize)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onInfoClick,
                        ),
                )
            }
        }
        content()
    }
}

@Composable
private fun DetailBodyText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = AppTheme.colors.textDetailsScreenBody,
        fontSize = BodyFontSize,
        lineHeight = BodyFontSize * 1.6f,
        modifier = modifier,
    )
}

@Composable
private fun GatheringList(gatherings: List<Gathering>, attendingGatheringId: Int?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(gatheringRowRadius()))
            .background(AppTheme.colors.detailsScreenListBackground),
    ) {
        gatherings.forEach { gathering ->
            GatheringRow(
                gathering = gathering,
                isAttending = gathering.gatheringId == attendingGatheringId,
            )
        }
    }
}

@Composable
private fun GatheringRow(gathering: Gathering, isAttending: Boolean) {
    val textColor = if (isAttending) {
        AppTheme.colors.textThemeColorFirst
    } else {
        AppTheme.colors.textDetailsScreenBody
    }
    val fontWeight = if (isAttending) FontWeight.Bold else FontWeight.Normal

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(if (isAttending) AppTheme.colors.themeColorFirst else Color.Transparent)
            .padding(
                horizontal = GatheringRowHorizontalPadding,
                vertical = GatheringRowVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${gathering.round}.",
            color = textColor,
            fontSize = GatheringFontSize,
            lineHeight = GatheringLineHeight,
            fontWeight = fontWeight,
            modifier = Modifier.width(GatheringRoundWidth),
        )
        Text(
            text = gathering.displayTime(),
            color = textColor,
            fontSize = GatheringFontSize,
            lineHeight = GatheringLineHeight,
            fontWeight = fontWeight,
            modifier = Modifier.width(GatheringTimeWidth),
        )
        Text(
            text = gathering.gatheringSpotName,
            color = textColor,
            fontSize = GatheringFontSize,
            lineHeight = GatheringLineHeight,
            fontWeight = fontWeight,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Box(modifier = Modifier.width(GatheringBadgeWidth)) {
            if (isAttending) {
                Text(
                    text = "出場",
                    color = textColor,
                    fontSize = GatheringFontSize,
                    lineHeight = GatheringLineHeight,
                    fontWeight = fontWeight,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// 行はCircleShapeで「高さの半分」に丸まるため、大枠もその値に揃える
@Composable
private fun gatheringRowRadius(): Dp =
    GatheringRowVerticalPadding + with(LocalDensity.current) { GatheringLineHeight.toDp() } / 2

private fun Gathering.displayTime(): String =
    if (gatheringTime == UndecidedGatheringTime) "未定" else gatheringTime.toDisplayTime()

private fun String.toDisplayTime(): String = removePrefix("0")
