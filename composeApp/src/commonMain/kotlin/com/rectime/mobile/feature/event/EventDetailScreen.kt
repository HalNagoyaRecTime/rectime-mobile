package com.rectime.mobile.feature.event

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ChevronRight
import com.rectime.mobile.ui.component.AppTimeText
import com.rectime.mobile.ui.component.AppMarkdown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.core.model.EventDetail
import com.rectime.mobile.core.model.Gathering
import com.rectime.mobile.core.model.venueDisplayText
import com.rectime.mobile.core.util.toFormattedTime
import com.rectime.mobile.ui.component.AppLoadingIndicator
import com.rectime.mobile.ui.component.MapModal
import com.rectime.mobile.ui.component.PushScreenScaffold
import com.rectime.mobile.ui.component.BounceBackground
import com.rectime.mobile.ui.component.headerTitleBarHeight
import com.rectime.mobile.ui.theme.AppTheme

private val TitleBandVerticalPadding = 12.dp
private val SectionSpacing = 24.dp
private val ContentBottomSpacing = 48.dp

private val TitleFontSize = 28.sp
private val BodyFontSize = 16.sp

data class EventDetailScreen(val eventId: Int) : Screen {
    override val key: String = "event_detail_$eventId"

    @Composable
    override fun Content(navigationController: NavigationController) {
        val viewModel = viewModel(key = key) {
            EventDetailViewModel(eventId = eventId, scheduleStore = EventScheduleStore.shared)
        }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onForeground() }
        val uiState by viewModel.uiState.collectAsState()
        var isMapVisible by remember { mutableStateOf(false) }
        var titleBandHeightPx by remember { mutableIntStateOf(0) }
        var titleHeightPx by remember { mutableIntStateOf(0) }
        val listState = rememberLazyListState()
        val titleInsetPx = with(LocalDensity.current) {
            TitleBandVerticalPadding.roundToPx()
        }
        val headerTitleVisible by remember(listState, titleInsetPx) {
            derivedStateOf {
                titleHeightPx > 0 && (listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset >= titleInsetPx + titleHeightPx)
            }
        }
        val titleBandColor = AppTheme.colors.themeColorSecond
        val bodyBackgroundColor = AppTheme.colors.screenBackground

        Box(modifier = Modifier.fillMaxSize()) {
            PushScreenScaffold(
                title = uiState.eventDetail?.eventName.orEmpty(),
                titleVisible = headerTitleVisible && !uiState.isLoading && uiState.error == null,
                lazyListState = listState,
                onBack = { navigationController.requestPop() },
                horizontalPadding = false,
                contentTopPadding = false,
                headerEdgeFade = false,
                contentBackground = {
                    if (uiState.eventDetail != null && uiState.error == null) {
                        BounceBackground(titleBandColor, bodyBackgroundColor, titleBandHeightPx.toFloat())
                    }
                },
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
                                AppLoadingIndicator()
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
                                attendingGatheringIds = uiState.attendingGatheringIds,
                                onOpenMap = { isMapVisible = true },
                                onTitleBandHeightChanged = { titleBandHeightPx = it },
                                onTitleHeightChanged = { titleHeightPx = it },
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
    attendingGatheringIds: Set<Int>,
    onOpenMap: () -> Unit,
    onTitleBandHeightChanged: (Int) -> Unit,
    onTitleHeightChanged: (Int) -> Unit,
) {
    var now by remember { mutableIntStateOf(overviewMinute()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = overviewMinute()
            delay(60_000)
        }
    }
    val status = when {
        minuteOf(event.endTime)?.let { now >= it } == true -> "終了"
        minuteOf(event.startTime)?.let { now >= it } == true -> "開催中"
        else -> "開催前"
    }
    // 高さを固定せず、本文全体をLazyColumnのスクロール範囲に含める。
    // 短い本文の下側はScaffoldのcontentBackgroundが描画する。
    Column(modifier = Modifier.fillMaxWidth().background(AppTheme.colors.screenBackground)) {
        EventTitleBand(
            title = event.eventName,
            onHeightChanged = onTitleBandHeightChanged,
            onTitleHeightChanged = onTitleHeightChanged,
            status = status,
            overview = { EventOverview(event, onOpenMap) },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = AppTheme.layout.screenHorizontalPadding,
                    vertical = SectionSpacing,
                ),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            if (gatherings.isNotEmpty()) {
                EventRoundPreviews(event, gatherings, attendingGatheringIds)
            }

            event.ruleText?.takeIf { it.isNotBlank() }?.let { ruleText ->
                AppMarkdown(
                    content = ruleText,
                    imageTitle = event.eventName,
                    bodyStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                        fontSize = BodyFontSize, lineHeight = BodyFontSize * 1.6f,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(ContentBottomSpacing))
    }
}

@Composable
private fun EventTitleBand(
    title: String,
    onHeightChanged: (Int) -> Unit,
    onTitleHeightChanged: (Int) -> Unit,
    status: String,
    overview: @Composable () -> Unit,
) {
    val bottomLineColor = AppTheme.colors.themeColorFirst
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightChanged(it.height) }
            .background(AppTheme.colors.themeColorSecond)
            .drawBehind {
                val thickness = 2.dp.toPx()
                drawRect(bottomLineColor, topLeft = Offset(0f, size.height - thickness), size = Size(size.width, thickness))
            }
            .padding(
                start = AppTheme.layout.screenHorizontalPadding,
                end = AppTheme.layout.screenHorizontalPadding,
                top = headerTitleBarHeight() + TitleBandVerticalPadding,
                bottom = TitleBandVerticalPadding,
            ),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = title,
                modifier = Modifier.weight(1f).onSizeChanged { onTitleHeightChanged(it.height) },
                color = AppTheme.colors.textThemeColorSecond,
                fontSize = TitleFontSize,
                lineHeight = TitleFontSize * 1.3f,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            OverviewStatus(status)
        }
        Spacer(Modifier.height(8.dp))
        overview()
    }
}

@Composable
private fun EventOverview(event: EventDetail, onOpenMap: () -> Unit) {
    val foreground = AppTheme.colors.textThemeColorSecond
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.width(IntrinsicSize.Max)) {
            Text("開催時間", color = foreground.copy(alpha = 0.8f), fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AppTimeText(event.startTime.toFormattedTime(padHour = false), fontSize = 20.sp, lineHeight = 24.sp,
                    color = foreground, fontWeight = FontWeight.Bold)
                Text("~", color = foreground, fontSize = 20.sp)
                AppTimeText(event.endTime.toFormattedTime(padHour = false), fontSize = 20.sp, lineHeight = 24.sp,
                    color = foreground, fontWeight = FontWeight.Bold)
            }
        }
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.End,
        ) {
            Text("開催場所", color = foreground.copy(alpha = 0.8f), fontSize = 13.sp)
            Row(
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onOpenMap,
                    )
                    .semantics { contentDescription = "会場マップを開く" },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    venueDisplayText(event.venues), color = foreground, fontSize = BodyFontSize,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                Icon(SolidGroup.ChevronRight, contentDescription = null, tint = foreground, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun OverviewStatus(status: String) {
    val colors = AppTheme.colors
    val background = when (status) {
        "開催中" -> colors.themeColorFirst
        "終了" -> colors.screenBackground
        else -> colors.surfacePrimary
    }
    val foreground = when (status) {
        "開催中" -> colors.textThemeColorFirst
        "終了" -> colors.textMuted
        else -> colors.themeColorSecond
    }
    Text(
        status, color = foreground, fontSize = 13.sp, maxLines = 1, softWrap = false,
        modifier = Modifier.background(background, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

private fun overviewMinute(): Int = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).let { it.hour * 60 + it.minute }
