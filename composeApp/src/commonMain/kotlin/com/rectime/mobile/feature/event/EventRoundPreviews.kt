package com.rectime.mobile.feature.event

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rectime.mobile.core.model.EventDetail
import com.rectime.mobile.core.model.Gathering
import com.rectime.mobile.feature.settings.SettingsCornerRadius
import com.rectime.mobile.feature.schedule.TimelineEvent
import com.rectime.mobile.feature.schedule.assignLanes
import com.rectime.mobile.ui.theme.AppTheme
import com.rectime.mobile.ui.component.EventCard
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.ceil
import kotlin.time.Clock

// APIの呼び出し時刻追加までの画面比較用。通信・保存データは変更しない。
private const val PrototypeCallLeadMinutes = 10

private data class RoundPreview(val number: Int, val rows: List<Gathering>) {
    val minute: Int? = rows.mapNotNull { minuteOf(it.gatheringTime) }.minOrNull()
    val time: String = minute?.let(::displayMinute) ?: "未定"
    val callMinute: Int? = minute?.minus(PrototypeCallLeadMinutes)
    val callTime: String = callMinute?.let(::displayMinute) ?: "未定"
    fun isMine(ids: Set<Int>): Boolean = rows.any { it.gatheringId in ids }
}

private fun minuteOf(value: String): Int? {
    val normalized = if (value.length == 4 && value.all { it.isDigit() }) value.take(2) + ":" + value.takeLast(2) else value
    val parts = normalized.split(':')
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
    return (hour * 60 + minute).takeIf { hour in 0..23 && minute in 0..59 }
}

/** 比較用の2案。登録時刻を使い、未取得の通知先行時間は推測しない。 */
@Composable
internal fun EventRoundPreviews(event: EventDetail, gatherings: List<Gathering>, attendingIds: Set<Int>, surroundingEvents: List<TimelineEvent>) {
    if (gatherings.isEmpty()) return
    val rounds = remember(gatherings) {
        gatherings.byRound().map { RoundPreview(it.key, it.value) }
            .sortedWith(compareBy<RoundPreview> { it.minute == null }.thenBy { it.minute })
    }
    var now by remember { mutableIntStateOf(currentMinute()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = currentMinute()
            delay(15_000)
        }
    }
    val end = minuteOf(event.endTime)
    val current = rounds.filter { it.minute?.let { time -> time <= now } == true }.maxByOrNull { it.minute ?: -1 }
    val status = when {
        rounds.all { it.minute == null } -> "開始時刻未定"
        end != null && now >= end -> "イベント終了"
        current != null -> "第${current.number}ラウンドの開始時刻を経過"
        else -> "開始前"
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("全${rounds.size}ラウンド", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textDetailsScreenBody)
        Text(status, color = AppTheme.colors.textDetailsScreenBody, fontSize = 14.sp)
        Text("${minuteOf(event.startTime)?.let(::displayMinute) ?: "未定"} 〜 ${end?.let(::displayMinute) ?: "未定"}", color = AppTheme.colors.textDetailsScreenBody, fontSize = 14.sp)
        val mine = rounds.filter { it.isMine(attendingIds) }
        if (mine.isNotEmpty()) {
            Text("自分の出場：" + mine.joinToString(" / ") { "第${it.number} · 呼出 ${it.callTime} / 開始 ${it.time}" }, color = AppTheme.colors.themeColorFirst, fontSize = 14.sp)
        }
        Text("案A", fontWeight = FontWeight.Bold, color = AppTheme.colors.textDetailsScreenBody)
        CalendarPreview(event, rounds, attendingIds, now, surroundingEvents)
        Text("案B", fontWeight = FontWeight.Bold, color = AppTheme.colors.textDetailsScreenBody)
        PreviewFrame {
            SnakePreview(rounds, attendingIds, now)
        }
    }
}

/** ラウンド間の余白を保てる縮尺で、開始・呼出時刻をイベント帯に表示する。 */
@Composable
private fun CalendarPreview(event: EventDetail, rounds: List<RoundPreview>, ids: Set<Int>, now: Int, surroundingEvents: List<TimelineEvent>) {
    var expanded by remember(event.eventId) { mutableStateOf(false) }
    val timed = rounds.filter { it.minute != null }
    val eventStart = minuteOf(event.startTime)
    val eventEnd = minuteOf(event.endTime)
    val timeline = remember(event, surroundingEvents) {
        if (eventStart == null || eventEnd == null || eventEnd <= eventStart) emptyList() else {
            val target = TimelineEvent(event.eventId, event.eventName, event.venues, eventStart,
                eventEnd - eventStart, 0, 1, displayMinute(eventStart), displayMinute(eventEnd))
            assignLanes(surroundingEvents.filter { it.eventId != event.eventId }
                .filter { it.durationMinutes > 0 } + target)
                .flatMap { item ->
                    if (item.overflowCount == 0) listOf(item)
                    else item.overflowEvents.map { it.copy(lane = item.lane, laneCount = item.laneCount) }
                }
        }
    }
    val target = timeline.firstOrNull { it.eventId == event.eventId }
    val first = eventStart ?: timed.firstOrNull()?.minute ?: 0
    val roundHeight = 44.dp
    val roundGap = 6.dp
    val last = maxOf(eventEnd ?: first + 60, timed.lastOrNull()?.minute ?: first + 60, first + 1)
    val axisStart = first / 30 * 30
    val axisEnd = ((last + 29) / 30 * 30).coerceAtLeast(axisStart + 30)
    val smallestGap = timed.mapNotNull { it.minute }.distinct().zipWithNext { a, b -> b - a }.minOrNull() ?: 30
    val minuteScale = maxOf(2f, (roundHeight + roundGap).value / smallestGap.coerceAtLeast(1))
    val topPadding = 72.dp
    val fullHeight = ((axisEnd - axisStart) * minuteScale).dp + topPadding + 20.dp
    val visibleHeight = if (expanded) fullHeight else 180.dp
    fun position(minute: Int) = topPadding + ((minute - axisStart) * minuteScale).dp
    val focus = timed.firstOrNull { it.isMine(ids) }?.minute ?: timed.firstOrNull()?.minute ?: first
    val shift by animateDpAsState(
        targetValue = if (expanded) 0.dp else (position(focus) - topPadding).coerceIn(0.dp, (fullHeight - 180.dp).coerceAtLeast(0.dp)),
        animationSpec = tween(250), label = "roundViewportOffset",
    )
    val grid = AppTheme.colors.textScheduleTimeLine
    val accent = AppTheme.colors.themeColorFirst
    val progress = Color.DarkGray.copy(alpha = 0.85f)
    val live = AppTheme.colors.themeColorSecond
    val band = live.copy(alpha = 0.3f)
    val current = timed.lastOrNull { it.minute!! <= now }?.takeIf { eventEnd == null || now < eventEnd }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(SettingsCornerRadius))
        .background(Color.White).border(1.dp, grid.copy(alpha = 0.35f), RoundedCornerShape(SettingsCornerRadius))
        .animateContentSize(animationSpec = tween(250)).height(visibleHeight).previewClick { expanded = !expanded }) {
        BoxWithConstraints(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).height(fullHeight).offset(y = -shift).padding(horizontal = 12.dp)) {
            val viewportWidth = maxWidth
            val labelWidth = 42.dp
            val targetLaneStart = target?.let { it.lane.toFloat() / it.laneCount } ?: 0f
            val targetLaneWidth = target?.let { 1f / it.laneCount } ?: 1f
            val sideEvents = timeline.filter { it.eventId != event.eventId && eventStart != null && eventEnd != null &&
                it.startMinuteOfDay < eventEnd && it.startMinuteOfDay + it.durationMinutes > eventStart }
            val leftPeek = if (sideEvents.any { (it.lane + 1f) / it.laneCount <= targetLaneStart }) 18.dp else 0.dp
            val rightPeek = if (sideEvents.any { it.lane.toFloat() / it.laneCount >= targetLaneStart + targetLaneWidth }) 18.dp else 0.dp
            val targetLeft = labelWidth + leftPeek
            val targetWidth = (viewportWidth - targetLeft - rightPeek).coerceAtLeast(1.dp)
            Canvas(Modifier.fillMaxSize()) {
                for (minute in axisStart..axisEnd step 30) {
                    val y = position(minute).toPx()
                    drawLine(grid.copy(alpha = 0.45f), Offset(labelWidth.toPx(), y), Offset(size.width, y), 1.dp.toPx())
                }
                if (eventStart != null && eventEnd != null && eventEnd > eventStart) {
                    drawRoundRect(band, topLeft = Offset(targetLeft.toPx(), position(eventStart).toPx()),
                        size = androidx.compose.ui.geometry.Size(targetWidth.toPx(), ((eventEnd - eventStart) * minuteScale).dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
                    drawRoundRect(live.copy(alpha = 0.65f), topLeft = Offset(targetLeft.toPx(), position(eventStart).toPx()),
                        size = androidx.compose.ui.geometry.Size(targetWidth.toPx(), ((eventEnd - eventStart) * minuteScale).dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()), style = Stroke(1.dp.toPx()))
                }
                if (now in axisStart..axisEnd) {
                    val y = position(now).toPx()
                    drawLine(live, Offset(labelWidth.toPx(), y), Offset(size.width, y), 2.dp.toPx())
                    drawCircle(live, 3.dp.toPx(), Offset(labelWidth.toPx(), y))
                }
            }
            // 予定カードは実サイズで配置し、上・左右にはみ出す部分を窓で切り抜く。
            Box(Modifier.offset(x = labelWidth).width((viewportWidth - labelWidth).coerceAtLeast(1.dp))
                .height(position(eventEnd ?: axisEnd).coerceAtLeast(1.dp)).clipToBounds()) {
                timeline.filter { it.eventId != event.eventId }.forEach { neighbor ->
                    if (target != null && neighbor.lane == target.lane && neighbor.laneCount == target.laneCount &&
                        neighbor.startMinuteOfDay < target.startMinuteOfDay + target.durationMinutes &&
                        neighbor.startMinuteOfDay + neighbor.durationMinutes > target.startMinuteOfDay) return@forEach
                    val top = position(neighbor.startMinuteOfDay)
                    val bottom = position(neighbor.startMinuteOfDay + neighbor.durationMinutes)
                    val left = targetLeft + targetWidth * ((neighbor.lane.toFloat() / neighbor.laneCount - targetLaneStart) / targetLaneWidth)
                    val width = targetWidth / (neighbor.laneCount * targetLaneWidth)
                    if (bottom <= 0.dp || top >= position(eventEnd ?: axisEnd) || left >= viewportWidth || left + width <= labelWidth) return@forEach
                    val cardWidth = width - roundGap
                    val cardHeight = bottom - top - roundGap
                    if (cardWidth <= 0.dp || cardHeight <= 0.dp) return@forEach
                    EventCard(
                        time = "${neighbor.startTimeLabel}-${neighbor.endTimeLabel}", title = neighbor.title,
                        venues = neighbor.venues.map { it.venueName },
                        isLive = now in neighbor.startMinuteOfDay..(neighbor.startMinuteOfDay + neighbor.durationMinutes),
                        isParticipating = neighbor.isParticipating,
                        onClick = { expanded = !expanded },
                        modifier = Modifier.offset(x = left - labelWidth + roundGap / 2, y = top + roundGap / 2)
                            .wrapContentSize(Alignment.TopStart, unbounded = true).size(cardWidth, cardHeight),
                    )
                }
            }
            for (minute in axisStart..axisEnd step 30) {
                Text(displayMinute(minute), modifier = Modifier.offset(y = position(minute) - 8.dp).width(labelWidth), fontSize = 10.sp, color = Color.DarkGray)
            }
            // 同時刻のラウンドは1つのタグにまとめる。
            timed.groupBy { it.minute!! }.forEach { (minute, group) ->
                val top = position(minute) - roundHeight / 2
                val mine = group.any { it.isMine(ids) }
                val isCurrent = group.any { it.number == current?.number }
                val markerColor = when {
                    mine -> accent
                    isCurrent -> live
                    minute <= now -> progress
                    else -> grid
                }
                val label = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("第${group.joinToString("・") { "${it.number}" }}ラウンド")
                    }
                    append("｜開始${displayMinute(minute)}〜")
                    if (mine) append(" / 呼出${displayMinute(minute - PrototypeCallLeadMinutes)}")
                }
                Row(Modifier.offset(x = targetLeft + roundGap, y = top)
                    .width((targetWidth - roundGap * 2).coerceAtLeast(1.dp)).height(roundHeight),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).background(markerColor, RoundedCornerShape(50)))
                    Spacer(Modifier.width(4.dp))
                    Row(Modifier.weight(1f).background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                        .border(1.dp, markerColor.copy(alpha = if (mine) 0.65f else 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp, color = AppTheme.colors.textDetailsScreenBody)
                        if (mine) {
                            Spacer(Modifier.width(6.dp))
                            Text("出場", modifier = Modifier.background(accent, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
    if (rounds.any { it.minute == null }) Text("時刻未定：" + rounds.filter { it.minute == null }.joinToString { "第${it.number}" }, fontSize = 12.sp, color = AppTheme.colors.textDetailsScreenBody)
}

/** 行ごとに方向を反転する連続経路。幅に収まる列数を選び横スクロールしない。 */
@Composable
private fun SnakePreview(rounds: List<RoundPreview>, ids: Set<Int>, now: Int) {
    val pending = AppTheme.colors.textDetailsScreenBody.copy(alpha = 0.18f)
    val progress = AppTheme.colors.textDetailsScreenBody.copy(alpha = 0.65f)
    val accent = AppTheme.colors.themeColorFirst
    val background = AppTheme.colors.commonBackground
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (maxWidth.value / 58f).toInt().coerceAtLeast(1)
        val rowCount = ceil(rounds.size.toFloat() / columns).toInt()
        val cellWidth = maxWidth / columns
        val cellHeight = 70.dp
        val top = 22.dp
        fun point(index: Int): Offset {
            val row = index / columns
            val column = if (row % 2 == 0) index % columns else columns - 1 - index % columns
            return Offset((column + 0.5f) * cellWidth.value, top.value + row * cellHeight.value)
        }
        Box(Modifier.fillMaxWidth().height(cellHeight * rowCount)) {
            Canvas(Modifier.fillMaxSize()) {
                fun pixels(index: Int) = point(index).let { Offset(it.x.dp.toPx(), it.y.dp.toPx()) }
                for (index in 0 until rounds.lastIndex) {
                    val a = pixels(index)
                    val b = pixels(index + 1)
                    val path = Path().apply {
                        moveTo(a.x, a.y)
                        if (index / columns != (index + 1) / columns) {
                            val turnX = if ((index / columns) % 2 == 0) size.width - 2.dp.toPx() else 2.dp.toPx()
                            cubicTo(turnX, a.y, turnX, b.y, b.x, b.y)
                        } else lineTo(b.x, b.y)
                    }
                    drawPath(path, pending, style = Stroke(5.dp.toPx()))
                    val fraction = intervalProgress(now, rounds[index].minute, rounds[index + 1].minute)
                    if (fraction > 0f) {
                        val measure = PathMeasure().apply { setPath(path, false) }
                        val filled = Path()
                        measure.getSegment(0f, measure.length * fraction, filled, true)
                        drawPath(filled, progress, style = Stroke(5.dp.toPx()))
                    }
                }
                rounds.forEachIndexed { index, round ->
                    val center = pixels(index)
                    val color = if (round.isMine(ids)) accent else if (round.minute?.let { now >= it } == true) progress else pending
                    drawCircle(background, 17.dp.toPx(), center)
                    drawCircle(color, 13.dp.toPx(), center)
                }
            }
            rounds.forEachIndexed { index, round ->
                val center = point(index)
                val elapsed = round.minute?.let { now >= it } == true
                val mine = round.isMine(ids)
                Column(Modifier.offset(x = center.x.dp - cellWidth / 2, y = center.y.dp - 22.dp)
                    .width(cellWidth).height(cellHeight), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(44.dp), contentAlignment = Alignment.Center) {
                        Text("${round.number}", color = if (mine || elapsed) Color.White else AppTheme.colors.textDetailsScreenBody, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(round.time, fontSize = 10.sp, color = AppTheme.colors.textDetailsScreenBody)
                }
            }
        }
    }
}

private fun intervalProgress(now: Int, start: Int?, end: Int?): Float = when {
    start == null || end == null -> 0f
    end <= start -> if (now >= end) 1f else 0f
    else -> ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)
}

private fun displayMinute(minute: Int): String {
    val dayMinute = ((minute % 1440) + 1440) % 1440
    return "${(dayMinute / 60).toString().padStart(2, '0')}:${(dayMinute % 60).toString().padStart(2, '0')}"
}
private fun currentMinute(): Int = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).let { it.hour * 60 + it.minute }

@Composable
private fun PreviewFrame(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp, AppTheme.colors.textDetailsScreenBody.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** 図の展開・折りたたみは波紋を出さずに切り替える。 */
@Composable
private fun Modifier.previewClick(onClick: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)
