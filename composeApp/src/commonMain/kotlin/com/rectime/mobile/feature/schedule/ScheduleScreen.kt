package com.rectime.mobile.feature.schedule

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.feature.event.EventDetailScreen
import com.rectime.mobile.ui.component.AppLoadingIndicator
import com.rectime.mobile.ui.component.AppModal
import com.rectime.mobile.ui.component.EventCard
import com.rectime.mobile.ui.component.EventCardDimensions
import com.rectime.mobile.ui.component.PullRefreshHoldDistance
import com.rectime.mobile.ui.component.RootScreenScaffold
import com.rectime.mobile.ui.component.resolveEventCardShadowSpec
import com.rectime.mobile.ui.modifier.outerShadow
import com.rectime.mobile.ui.theme.AppTheme

internal class ScheduleTimelineGeometry(
    private val topPadding: Dp,
    private val bottomPadding: Dp,
    private val hourHeight: Dp,
    private val visibleStartHour: Int,
    private val visibleEndHour: Int,
) {
    val contentHeight: Dp = hourHeight * (visibleEndHour - visibleStartHour)
    val totalHeight: Dp = topPadding + contentHeight + bottomPadding

    fun minuteY(minute: Int): Dp =
        topPadding + hourHeight * ((minute - visibleStartHour * 60) / 60f)

    fun pastHeight(minute: Int): Dp = minuteY(minute).coerceIn(0.dp, totalHeight)

    fun paintTop(viewportHeight: Dp): Dp = maxOf(viewportHeight, -minuteY(0))

    fun paintBottom(viewportHeight: Dp): Dp = maxOf(viewportHeight, minuteY(24 * 60) - totalHeight)

    fun pastAboveHeight(minute: Int, paintTop: Dp): Dp =
        (minuteY(minute) + paintTop).coerceIn(0.dp, paintTop)

    fun pastBelowHeight(minute: Int, paintBottom: Dp): Dp =
        (minuteY(minute) - totalHeight).coerceIn(0.dp, paintBottom)
}

object ScheduleScreen : Screen {
    override val key: String = "schedule"

    @Composable
    override fun Content(navigationController: NavigationController) {
        val viewModel = viewModel { ScheduleViewModel() }
        val nowMinute by viewModel.nowMinute.collectAsStateWithLifecycle()
        val events by viewModel.events

        LaunchedEffect(Unit) {
            viewModel.fetchEvents()
        }

        ScheduleScreenUI(
            nowMinute = nowMinute,
            onOpenEventDetail = { eventId -> navigationController.push(EventDetailScreen(eventId)) },
            events = events,
            isLoading = viewModel.isLoading,
            isRefreshing = viewModel.isRefreshing,
            isUpdating = viewModel.isUpdating,
            onRefresh = viewModel::refresh,
            error = viewModel.error,
        )
    }
}

@Composable
private fun ScheduleScreenUI(
    nowMinute: Int,
    onOpenEventDetail: (Int) -> Unit,
    events: List<TimelineEvent>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    isUpdating: Boolean,
    onRefresh: () -> Unit,
    error: String?,
) {
    val timelineTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + AppTheme.layout.headerAction + 20.dp
    val navigationBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val timelineBottomPadding = 120.dp + navigationBottomInset + 24.dp
    val hourStart = 9
    val hourEnd = 20
    val hourHeight = 72.dp
    val timeBarWidth = 45.dp

    val timelineGeometry = ScheduleTimelineGeometry(
        timelineTopPadding, timelineBottomPadding, hourHeight, hourStart, hourEnd,
    )
    val timelineContentHeight = timelineGeometry.contentHeight
    val totalTimelineHeight = timelineGeometry.totalHeight

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedOverflowEvents by remember { mutableStateOf<List<TimelineEvent>?>(null) }

    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    // Keep the scrollable range at 9:00–20:00 while painting the full 0:00–24:00 day.
    val viewportHeight = with(density) { windowInfo.containerSize.height.toDp() }
    val bouncePaintTop = timelineGeometry.paintTop(viewportHeight + PullRefreshHoldDistance)
    val bouncePaintBottom = timelineGeometry.paintBottom(viewportHeight + PullRefreshHoldDistance)
    val screenWidthDp = remember(windowInfo.containerSize.width) {
        with(density) { windowInfo.containerSize.width.toDp() }
    }
    val dim = remember(screenWidthDp) { EventCardDimensions(screenWidthDp) }

    val overflowCardWidth = (10f * dim.u).dp

    LaunchedEffect(error) {
        if (error != null) {
            snackbarHostState.showSnackbar(error)
        }
    }

    var hasAutoScrolled by remember { mutableStateOf(false) }
    val isDragged by lazyListState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(isDragged) {
        if (isDragged) hasAutoScrolled = true
    }

    LaunchedEffect(isLoading) {
        if (!isLoading && !hasAutoScrolled && !lazyListState.isScrollInProgress) {
            val totalHours = hourEnd - hourStart
            val clampedNowMinute = nowMinute.coerceIn(hourStart * 60, hourEnd * 60)
            val nowOffsetPx = with(density) { timelineGeometry.minuteY(clampedNowMinute).toPx() }

            val viewportHeightPx = lazyListState.layoutInfo.viewportSize.height.toFloat()
            val totalHeightPx = with(density) { totalTimelineHeight.toPx() }

            val targetScrollPx = (nowOffsetPx - (viewportHeightPx / 2f))
                .coerceIn(0f, (totalHeightPx - viewportHeightPx).coerceAtLeast(0f))

            lazyListState.scrollToItem(index = 0, scrollOffset = targetScrollPx.toInt())
            hasAutoScrolled = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(AppTheme.colors.commonBackground)) {
        RootScreenScaffold(
            title = "スケジュール",
            lazyListState = lazyListState,
            isRefreshing = isRefreshing,
            refreshEnabled = !isUpdating,
            onRefresh = onRefresh,
            contentBackground = {
                // This surface and the list share the same bounce/refresh transform.
                // Scroll changes timeline coordinates; pulling never changes them.
                val pastColor = AppTheme.colors.pastAreaBackground
                val backgroundColor = AppTheme.colors.commonBackground
                val nowY = with(density) { timelineGeometry.minuteY(nowMinute).toPx() } -
                    lazyListState.firstVisibleItemScrollOffset
                Canvas(Modifier.fillMaxSize()) {
                    val top = -bouncePaintTop.toPx()
                    val bottom = size.height + bouncePaintBottom.toPx()
                    drawRect(backgroundColor, Offset(0f, top), Size(size.width, bottom - top))
                    val pastEnd = nowY.coerceIn(top, bottom)
                    drawRect(pastColor, Offset(0f, top), Size(size.width, pastEnd - top))
                }
                val separatorLineColor = AppTheme.colors.textScheduleTimeLine
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val totalHours = hourEnd - hourStart
                    val step = hourHeight.toPx()
                    val topPadPx = timelineTopPadding.toPx()
                    val contentLeft = timeBarWidth.toPx()

                    repeat(totalHours + 1) { index ->
                        val y = topPadPx + (index * step) - lazyListState.firstVisibleItemScrollOffset
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    separatorLineColor.copy(alpha = 0.8f),
                                    separatorLineColor.copy(alpha = 0.8f),
                                    Color.Transparent
                                ),
                                startX = contentLeft,
                                endX = size.width
                            ),
                            topLeft = Offset(contentLeft, y),
                            size = Size(size.width - contentLeft, 1.dp.toPx())
                        )
                    }
                }

                Box(
                    Modifier.wrapContentSize(unbounded = true, align = Alignment.TopStart)
                        .width(timeBarWidth)
                        .height(viewportHeight + bouncePaintTop + bouncePaintBottom)
                        .offset(y = -bouncePaintTop)
                        .outerShadow(
                            shape = RectangleShape,
                            color = AppTheme.colors.dropShadow,
                            blurRadius = 8.dp,
                            offsetX = 2.dp,
                            offsetY = 0.dp,
                        )
                        .background(AppTheme.colors.scheduleTimeBarBackground),
                )
                if (nowMinute in 0..(24 * 60)) {
                    val accentColor = AppTheme.colors.themeColorFirst
                    Box(
                        Modifier.fillMaxWidth().height(16.dp)
                            .offset(y = with(density) { nowY.toDp() } - 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(2.dp).padding(start = timeBarWidth)
                                .outerShadow(
                                    shape = RectangleShape,
                                    color = accentColor.copy(alpha = 0.6f),
                                    blurRadius = dim.glowRadius,
                                    spread = dim.glowRadius * 0.1f,
                                    offsetY = 0.dp,
                                )
                                .background(accentColor),
                        )
                        Box(
                            Modifier.offset(x = timeBarWidth - 5.dp).size(10.dp)
                                .background(accentColor, CircleShape),
                        )
                    }
                }
            },
            horizontalPadding = false,
            contentTopPadding = false,
            contentBottomPadding = false,
            snackbarHostState = snackbarHostState,
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(totalTimelineHeight)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = timeBarWidth + 6f.dp, end = 6f.dp)
                    ) {
                        val containerWidth = maxWidth

                        events.forEach { event ->
                            if (event.overflowCount == 0) {
                                val (xOffset, laneWidth) = if (event.laneCount >= 4) {
                                    if (event.overflowCount > 0) {
                                        (containerWidth - overflowCardWidth) to overflowCardWidth
                                    } else {
                                        val singleLaneWidth =
                                            (containerWidth - overflowCardWidth) / 3
                                        (singleLaneWidth * event.lane) to singleLaneWidth
                                    }
                                } else {
                                    val singleLaneWidth = containerWidth / event.laneCount
                                    (singleLaneWidth * event.lane) to singleLaneWidth
                                }

                                val yOffset = timelineGeometry.minuteY(event.startMinuteOfDay)
                                val eventHeight = hourHeight * (event.durationMinutes / 60f)

                                if (event.overflowCount > 0) {
                                    val shadowWidth = laneWidth - (dim.borderExtend * 2)
                                    val shadowHeight = eventHeight - (dim.borderExtend * 2)

                                    Box(
                                        modifier = Modifier
                                            .size(shadowWidth, shadowHeight)
                                            .offset(
                                                x = xOffset + dim.borderExtend,
                                                y = yOffset + dim.borderExtend
                                            )
                                            .outerShadow(
                                                shape = RoundedCornerShape(dim.cornerRadius),
                                                color = AppTheme.colors.dropShadow,
                                                blurRadius = 4.dp,
                                                offsetY = 2.dp
                                            )
                                    )
                                } else {

                                    val endMinuteOfDay =
                                        event.startMinuteOfDay + event.durationMinutes
                                    val isLive = nowMinute in event.startMinuteOfDay..endMinuteOfDay

                                    val shadowSpec = resolveEventCardShadowSpec(
                                        assignedWidth = laneWidth,
                                        assignedHeight = eventHeight,
                                        isLive = isLive,
                                        isParticipating = event.isParticipating,
                                        dim = dim
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(shadowSpec.width, shadowSpec.height)
                                            .offset(
                                                x = xOffset + shadowSpec.offsetX,
                                                y = yOffset + shadowSpec.offsetY
                                            )
                                            .outerShadow(
                                                shape = shadowSpec.shape,
                                                color = AppTheme.colors.dropShadow,
                                                blurRadius = 4.dp,
                                                offsetY = 2.dp
                                            )
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(timeBarWidth)
                            .height(totalTimelineHeight)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = timelineTopPadding)
                                .height(timelineContentHeight)
                        ) {
                            val totalHours = hourEnd - hourStart
                            val step = timelineContentHeight / totalHours

                            for (hour in hourStart..hourEnd) {
                                val index = hour - hourStart
                                val yOffset = step * index

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .offset(y = yOffset - 10.dp)
                                        .padding(end = 6.dp),
                                    contentAlignment = Alignment.TopEnd
                                ) {
                                    Text(
                                        text = "$hour:00",
                                        color = AppTheme.colors.textScheduleTimeBar,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = timeBarWidth + 6f.dp, end = 6f.dp)
                    ) {
                        val containerWidth = maxWidth

                        events.forEach { event ->
                            val (xOffset, laneWidth) = if (event.laneCount >= 4) {
                                if (event.overflowCount > 0) {
                                    (containerWidth - overflowCardWidth) to overflowCardWidth
                                } else {
                                    val singleLaneWidth = (containerWidth - overflowCardWidth) / 3
                                    (singleLaneWidth * event.lane) to singleLaneWidth
                                }
                            } else {
                                val singleLaneWidth = containerWidth / event.laneCount
                                (singleLaneWidth * event.lane) to singleLaneWidth
                            }

                            val yOffset = timelineGeometry.minuteY(event.startMinuteOfDay)
                            val eventHeight = hourHeight * (event.durationMinutes / 60f)

                            if (event.overflowCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(laneWidth, eventHeight)
                                        .offset(x = xOffset, y = yOffset)
                                        .padding(dim.borderExtend) // 外側に dim.borderExtend 分の余白
                                        .clip(RoundedCornerShape(dim.cornerRadius))
                                        .background(AppTheme.colors.eventOverflowBackground)
                                        .clickable { selectedOverflowEvents = event.overflowEvents }
                                ) {
                                    BoxWithConstraints(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 2.dp, vertical = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val text = "+${event.overflowCount}"
                                        val maxSpByHeight = with(density) { (maxHeight * 0.7f).toSp() }
                                        val maxSpByWidth = with(density) {
                                            val charCount = text.length.coerceAtLeast(1)
                                            ((maxWidth / charCount) * 1.2f).toSp()
                                        }

                                        val calculatedSp = if (maxSpByHeight.value < maxSpByWidth.value) maxSpByHeight else maxSpByWidth
                                        val optimalFontSize = if (calculatedSp.value > 12f) 12.sp else calculatedSp.value.coerceAtLeast(4f).sp

                                        Text(
                                            text = text,
                                            color = Color.White,
                                            fontSize = optimalFontSize,
                                            lineHeight = optimalFontSize,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            } else {
                                val endMinuteOfDay = event.startMinuteOfDay + event.durationMinutes
                                val isLive = nowMinute in event.startMinuteOfDay..endMinuteOfDay

                                EventCard(
                                    time = "${event.startTimeLabel}-${event.endTimeLabel}",
                                    title = event.title,
                                    venues = event.venues.map { it.venueName },
                                    isLive = isLive,
                                    isParticipating = event.isParticipating,
                                    onClick = { onOpenEventDetail(event.eventId) },
                                    modifier = Modifier
                                        .size(laneWidth, eventHeight)
                                        .offset(x = xOffset, y = yOffset)
                                )
                            }
                        }
                    }
                }
            }
        }

        selectedOverflowEvents?.let { hiddenEvents ->
            AppModal(onDismiss = { selectedOverflowEvents = null }) {
                Text(
                    text = "その他のイベント (${hiddenEvents.size}件)",
                    color = AppTheme.colors.textDetailsScreenTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                hiddenEvents.forEach { event ->
                    val endMin = event.startMinuteOfDay + event.durationMinutes
                    val isLive = nowMinute in event.startMinuteOfDay..endMin

                    EventCard(
                        time = "${event.startTimeLabel}-${event.endTimeLabel}",
                        title = event.title,
                        venues = event.venues.map { it.venueName },
                        isLive = isLive,
                        isParticipating = event.isParticipating,
                        onClick = {
                            selectedOverflowEvents = null
                            onOpenEventDetail(event.eventId)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                    )
                }
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                AppLoadingIndicator(
                    modifier = Modifier.size(56.dp),
                    strokeWidth = 5.dp,
                )
            }
        }
    }
}
