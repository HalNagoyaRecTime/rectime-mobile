package com.rectime.mobile.feature.event

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import com.rectime.mobile.ui.modifier.outerShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.rectime.mobile.core.model.EventDetail
import com.rectime.mobile.core.model.Gathering
import com.rectime.mobile.feature.settings.SettingsCornerRadius
import com.rectime.mobile.ui.component.AppDivider
import com.rectime.mobile.ui.component.TimelineTimeLabel
import com.rectime.mobile.ui.graphics.drawTimelineSeparator
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.ChevronRight
import com.rectime.mobile.ui.component.AppTimeText
import com.rectime.mobile.ui.component.AppBottomSheet
import com.rectime.mobile.ui.component.AppIconButton
import com.rectime.mobile.ui.component.PlaceImageModal
import com.woowla.compose.icon.collections.fontawesome.fontawesome.SolidGroup
import com.woowla.compose.icon.collections.fontawesome.fontawesome.solid.Xmark
import com.rectime.mobile.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import org.jetbrains.compose.resources.painterResource
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.ic_location_outline

private data class RoundPreview(val number: Int, val rows: List<Gathering>) {
    private val gathering = rows.filter { minuteOf(it.gatheringTime) != null }.minByOrNull { minuteOf(it.gatheringTime)!! }
    val times = resolveRoundDisplayTimes(gathering?.gatheringTime, rows.firstNotNullOfOrNull { it.roundStartTime })
    val minute: Int? = times.startMinute ?: times.gatheringMinute
    fun isMine(ids: Set<Int>): Boolean = rows.any { it.gatheringId in ids }
    fun timesFor(gathering: Gathering): RoundDisplayTimes =
        resolveRoundDisplayTimes(gathering.gatheringTime, null)
            .copy(startMinute = times.startMinute)
}

/** 本人の集合情報と、ラウンドの時間軸を表示する。 */
@Composable
internal fun EventRoundPreviews(event: EventDetail, gatherings: List<Gathering>, attendingIds: Set<Int>, now: Int = -1) {
    if (gatherings.isEmpty()) return
    val rounds = remember(gatherings) {
        gatherings.byRound().map { RoundPreview(it.key, it.value) }
            .sortedWith(compareBy<RoundPreview> { it.minute == null }.thenBy { it.minute })
    }
    var sheetRound by remember(event.eventId) { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val mine = rounds.filter { it.isMine(attendingIds) }
        if (mine.isNotEmpty()) {
            Text("出場予定", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textDetailsScreenBody)
            mine.forEach { round ->
                round.rows.filter { it.gatheringId in attendingIds }.forEach { gathering ->
                    OwnGatheringCard(round, gathering)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("ラウンド", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textDetailsScreenBody)
            Text("全${rounds.size}回", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.colors.textSecondary)
        }
        CalendarPreview(event, rounds, attendingIds, now, onOpenRound = { sheetRound = it })
        OutlinedButton(onClick = { sheetRound = rounds.first().number }, modifier = Modifier.fillMaxWidth(), border = null,
            colors = ButtonDefaults.outlinedButtonColors(containerColor = AppTheme.colors.surfacePrimary)) {
            Text("ラウンド別の集合情報")
        }
        if (!event.ruleText.isNullOrBlank()) AppDivider()
    }
    sheetRound?.let { selected ->
        RoundGatheringsSheet(rounds, attendingIds, selected, onDismiss = { sheetRound = null })
    }

}

@Composable
private fun OwnGatheringCard(round: RoundPreview, gathering: Gathering) {
    val times = round.timesFor(gathering)
    val colors = AppTheme.colors
    GatheringCard {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            GatheringAccentTag("第${round.number}ラウンド・集合${gathering.gatheringId}番")
            times.startMinute?.let { RoundStartTime(it, color = colors.textMuted, modifier = Modifier.align(Alignment.CenterVertically)) }
        }
        GatheringDetails(gathering, times)
    }
}

@Composable
private fun RoundStartTime(minute: Int, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("開始", fontSize = 12.sp, color = color)
        AppTimeText(displayMinute(minute), fontSize = 14.sp, color = AppTheme.colors.textDetailsScreenBody,
            fontWeight = FontWeight.Medium)
        Text("~", fontSize = 14.sp, color = AppTheme.colors.textDetailsScreenBody)
    }
}

@Composable
private fun GatheringDetails(gathering: Gathering, times: RoundDisplayTimes) {
    val colors = AppTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Top) {
        run {
            Column {
                Text("集合時刻", fontSize = 12.sp, color = colors.textMuted)
                AppTimeText(times.gatheringMinute?.let(::displayMinute) ?: "未定", fontSize = 22.sp, lineHeight = 26.sp,
                    fontWeight = FontWeight.Bold, color = colors.textDetailsScreenBody)
            }
        }
        var isImageVisible by remember { mutableStateOf(false) }
        Column(Modifier.weight(1f).clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null,
            role = Role.Button, onClickLabel = "画像を開く",
        ) { isImageVisible = true }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(painterResource(Res.drawable.ic_location_outline), contentDescription = null,
                    tint = colors.textMuted, modifier = Modifier.size(14.dp))
                Text("集合場所", fontSize = 12.sp, color = colors.textMuted)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(gathering.gatheringSpotName.ifBlank { "未定" }, fontSize = 14.sp, lineHeight = 20.sp,
                    color = colors.textDetailsScreenBody, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                Icon(SolidGroup.ChevronRight, contentDescription = null, tint = colors.textDetailsScreenBody,
                    modifier = Modifier.size(10.dp))
            }
        }
        if (isImageVisible) {
            PlaceImageModal(gathering.gatheringSpotName, gathering.gatheringSpotImageUrl,
                onDismiss = { isImageVisible = false })
        }
    }
}

@Composable
private fun GatheringCard(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(SettingsCornerRadius)
    Column(
        Modifier.fillMaxWidth()
            .outerShadow(shape = shape, color = AppTheme.colors.dropShadow.copy(alpha = 0.06f), blurRadius = 5.dp, offsetY = 2.dp)
            .clip(shape)
            .background(Color(0xFFFFF8E1))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun GatheringAccentTag(label: String, fontSize: TextUnit = 14.sp) {
    Text(label, color = AppTheme.colors.textThemeColorFirst, fontSize = fontSize, fontWeight = FontWeight.Bold,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.background(AppTheme.colors.themeColorFirst, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp))
}

/** ラウンド間の余白を保てる時間軸で、対象イベントだけを表示する。 */
@Composable
private fun CalendarPreview(event: EventDetail, rounds: List<RoundPreview>, ids: Set<Int>, now: Int, onOpenRound: (Int) -> Unit) {
    var expanded by remember(event.eventId) { mutableStateOf(false) }
    val timed = rounds.filter { it.minute != null }
    val eventStart = minuteOf(event.startTime)
    val eventEnd = minuteOf(event.endTime)
    val first = listOfNotNull(eventStart, timed.firstOrNull()?.minute).minOrNull() ?: 0
    val roundHeight = (48f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
    val roundGap = 6.dp
    val last = maxOf(eventEnd ?: first + 60, timed.lastOrNull()?.minute ?: first + 60, first + 1)
    val smallestGap = timed.mapNotNull { it.minute }.distinct().zipWithNext { a, b -> b - a }.minOrNull() ?: 30
    val minuteScale = maxOf(2f, (roundHeight + roundGap).value / smallestGap.coerceAtLeast(1))
    val axisStart = first
    val axisEnd = ((last + 29) / 30 * 30).coerceAtLeast(axisStart + 30)
    val topPadding = roundHeight / 2 + roundGap
    val fullHeight = ((axisEnd - axisStart) * minuteScale).dp + topPadding * 2
    val visibleHeight = if (expanded) fullHeight else minOf(fullHeight, 180.dp)
    fun position(minute: Int) = topPadding + ((minute - axisStart) * minuteScale).dp
    val focus = timed.firstOrNull { it.isMine(ids) }?.minute
        ?: timed.firstOrNull { it.number == 1 }?.minute ?: timed.firstOrNull()?.minute ?: first
    val density = LocalDensity.current
    val indicator = (if (eventEnd != null && now >= eventEnd) eventEnd else now)
        .takeIf { it in axisStart..axisEnd }
    fun viewportOffset(): Int = with(density) {
        val anchor = indicator ?: focus
        val inset = if (indicator != null) visibleHeight / 2 else topPadding
        roundViewportOffset(position(anchor).value, inset.value, fullHeight.value, visibleHeight.value).dp.roundToPx()
    }
    val scrollState = rememberScrollState(initial = viewportOffset())
    val animatedHeight by animateDpAsState(visibleHeight, animationSpec = tween(250))
    val animatedScroll = remember { Animatable(scrollState.value.toFloat()) }
    LaunchedEffect(expanded, focus, fullHeight, visibleHeight) {
        animatedScroll.snapTo(scrollState.value.toFloat())
        val target = viewportOffset()
        val scrolling = launch {
            snapshotFlow { animatedScroll.value }.collect { scrollState.scrollTo(it.toInt()) }
        }
        animatedScroll.animateTo(target.toFloat(), tween(250))
        withFrameNanos { }
        scrollState.scrollTo(target)
        scrolling.cancel()
    }
    val grid = AppTheme.colors.textScheduleTimeLine
    val accent = AppTheme.colors.themeColorFirst
    val progress = AppTheme.colors.surfaceAccentStrong
    val live = AppTheme.colors.themeColorSecond
    val band = live.copy(alpha = 0.3f)
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(SettingsCornerRadius))
        .background(AppTheme.colors.surfacePrimary)
        .height(animatedHeight)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            role = Role.Button, onClickLabel = if (expanded) "カレンダーを縮小" else "カレンダーを拡大") { expanded = !expanded }
        .verticalScroll(scrollState, enabled = expanded)) {
        BoxWithConstraints(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).height(fullHeight).padding(horizontal = 12.dp)) {
            val labelWidth = 42.dp
            val targetLeft = labelWidth
            val targetWidth = (maxWidth - labelWidth).coerceAtLeast(1.dp)
            val annotationLeft = targetLeft + roundGap
            val annotationWidth = (targetWidth - roundGap * 2).coerceAtLeast(1.dp)
            Canvas(Modifier.fillMaxSize()) {
                if (eventStart != null && eventEnd != null && eventEnd > eventStart) {
                    val bounds = androidx.compose.ui.geometry.Size(targetWidth.toPx(), ((eventEnd - eventStart) * minuteScale).dp.toPx())
                    val origin = Offset(targetLeft.toPx(), position(eventStart).toPx())
                    val radius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                    drawRoundRect(band, topLeft = origin, size = bounds, cornerRadius = radius)
                    drawRoundRect(live.copy(alpha = 0.65f), topLeft = origin, size = bounds,
                        cornerRadius = radius, style = Stroke(1.dp.toPx()))
                }
                for (minute in ((axisStart + 29) / 30 * 30)..axisEnd step 30) {
                    val y = position(minute).toPx()
                    drawTimelineSeparator(y, labelWidth.toPx(), grid)
                }
                val ended = eventEnd?.let { now >= it } == true
                val indicatorMinute = if (ended) eventEnd else now
                if (indicatorMinute in axisStart..axisEnd) {
                    val y = position(indicatorMinute).toPx()
                    val indicatorColor = if (ended) progress else accent
                    drawLine(indicatorColor, Offset(labelWidth.toPx(), y), Offset(size.width, y), 2.dp.toPx())
                    drawCircle(indicatorColor, 4.dp.toPx(), Offset(labelWidth.toPx(), y))
                }
            }
            for (minute in ((axisStart + 29) / 30 * 30)..axisEnd step 30) {
                TimelineTimeLabel(displayMinute(minute), modifier = Modifier.offset(y = position(minute) - 10.dp).width(labelWidth))
            }
            // 同時刻のラウンドは1つのタグにまとめる。
            timed.groupBy { it.minute!! }.forEach { (minute, group) ->
                val top = position(minute) - roundHeight / 2
                val mine = group.any { it.isMine(ids) }
                val hasStarted = group.any { it.times.startMinute?.let { start -> start <= now } == true }
                val title = "第${group.joinToString("・") { "${it.number}" }}ラウンド"
                val start = group.firstNotNullOfOrNull { it.times.startMinute }
                val tagColor = if (hasStarted) progress else AppTheme.colors.surfacePrimary
                val textColor = if (hasStarted) Color.White else AppTheme.colors.textDetailsScreenBody
                val pill = RoundedCornerShape(50)
                Row(Modifier.offset(x = annotationLeft, y = top)
                    .width(annotationWidth).height(roundHeight), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false).heightIn(min = 36.dp)
                            .clip(pill)
                            .then(if (expanded) Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() }, indication = null,
                                role = Role.Button, onClickLabel = "$title の集合情報を見る",
                            ) { onOpenRound(group.first().number) } else Modifier)
                            .background(tagColor)
                            .then(if (hasStarted) Modifier else Modifier.border(1.dp, live.copy(alpha = 0.65f), pill))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val markerColor = when {
                            mine -> accent
                            hasStarted -> textColor
                            else -> grid
                        }
                        Box(Modifier.size(8.dp).background(markerColor, RoundedCornerShape(50)))
                        Text(title, modifier = Modifier.weight(1f, fill = false),
                            fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, color = textColor,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        start?.let {
                            Text("/", fontSize = 14.sp, color = textColor)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppTimeText(displayMinute(it), fontSize = 14.sp, color = textColor, fontWeight = FontWeight.Bold)
                                Text(" ~", fontSize = 14.sp, color = textColor)
                            }
                        }
                        Icon(SolidGroup.ChevronRight, contentDescription = null, tint = textColor, modifier = Modifier.size(10.dp))
                    }
                }
            }
        }
    }
    if (rounds.any { it.minute == null }) Text("時刻未定：" + rounds.filter { it.minute == null }.joinToString { "第${it.number}ラウンド" }, fontSize = 12.sp, color = AppTheme.colors.textDetailsScreenBody)
}

@Composable
private fun RoundGatheringsSheet(rounds: List<RoundPreview>, ids: Set<Int>, selected: Int, onDismiss: () -> Unit) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = rounds.indexOfFirst { it.number == selected }.coerceAtLeast(0))
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val maxHeight = (with(density) { windowInfo.containerSize.height.toDp() } * 0.9f - 40.dp).coerceAtLeast(1.dp)
    AppBottomSheet(onDismiss = onDismiss) { closeSheet ->
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ラウンド別の集合情報", modifier = Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textDetailsScreenBody, maxLines = 1, overflow = TextOverflow.Ellipsis)
                AppIconButton(onClick = closeSheet, color = Color.Transparent) {
                    Icon(SolidGroup.Xmark, contentDescription = "閉じる",
                        tint = AppTheme.colors.textDetailsScreenHeader, modifier = Modifier.size(22.dp))
                }
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)) {
                items(rounds, key = { it.number }) { round ->
                    val accent = AppTheme.colors.themeColorFirst
                    val line = AppTheme.colors.commonSeparatorLine
                    val body = AppTheme.colors.textDetailsScreenBody
                    val muted = AppTheme.colors.textMuted
                    val sheetBackground = AppTheme.colors.screenBackground
                    Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.width(24.dp))
                        Column(Modifier.weight(1f).padding(bottom = 24.dp)
                            .drawBehind {
                                val x = -14.dp.toPx()
                                val lastRound = round == rounds.last()
                                val endY = size.height + (if (lastRound) 12.dp else 36.dp).toPx()
                                drawLine(line, Offset(x, 12.dp.toPx()), Offset(x, endY), 2.dp.toPx())
                                if (lastRound) {
                                    drawCircle(sheetBackground, 5.dp.toPx(), Offset(x, endY))
                                    drawCircle(line, 5.dp.toPx(), Offset(x, endY), style = Stroke(2.dp.toPx()))
                                }
                                drawCircle(if (round.isMine(ids)) accent else line, 5.dp.toPx(), Offset(x, 12.dp.toPx()))
                            }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("第${round.number}ラウンド", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                round.times.startMinute?.let { RoundStartTime(it, color = muted, modifier = Modifier.align(Alignment.CenterVertically)) }
                            }
                            round.rows.forEach { gathering ->
                                val mine = gathering.gatheringId in ids
                                val times = round.timesFor(gathering)
                                GatheringCard {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("集合${gathering.gatheringId}番", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                            color = body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        if (mine) GatheringAccentTag("出場", fontSize = 12.sp)
                                    }
                                    GatheringDetails(gathering, times)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
