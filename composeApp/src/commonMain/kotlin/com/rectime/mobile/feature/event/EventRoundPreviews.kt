package com.rectime.mobile.feature.event

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rectime.mobile.core.model.EventDetail
import com.rectime.mobile.core.model.Gathering
import com.rectime.mobile.feature.settings.SettingsCornerRadius
import com.rectime.mobile.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.ceil
import kotlin.time.Clock
import org.jetbrains.compose.resources.painterResource
import rectime_mobile.composeapp.generated.resources.Res
import rectime_mobile.composeapp.generated.resources.ic_notification_outline

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
internal fun EventRoundPreviews(event: EventDetail, gatherings: List<Gathering>, attendingIds: Set<Int>) {
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
    var selected by remember(event.eventId) { mutableStateOf<Int?>(null) }
    val selectedRound = rounds.firstOrNull { it.number == selected } ?: current ?: rounds.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("全${rounds.size}ラウンド", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = AppTheme.colors.textDetailsScreenBody)
        Text(status, color = AppTheme.colors.textDetailsScreenBody, fontSize = 14.sp)
        Text("${minuteOf(event.startTime)?.let(::displayMinute) ?: "未定"} 〜 ${end?.let(::displayMinute) ?: "未定"}", color = AppTheme.colors.textDetailsScreenBody, fontSize = 14.sp)
        val mine = rounds.filter { it.isMine(attendingIds) }
        if (mine.isNotEmpty()) {
            Text("自分の出場：" + mine.joinToString(" / ") { "第${it.number} · 呼出 ${it.callTime} / 開始 ${it.time}" }, color = AppTheme.colors.themeColorFirst, fontSize = 14.sp)
        }
        selectedRound?.let { round ->
            val timeLabel = if (round.isMine(attendingIds)) "呼出 ${round.callTime} / 開始 ${round.time}" else "開始 ${round.time}"
            Text("第${round.number}ラウンド · $timeLabel", fontWeight = FontWeight.Bold, color = AppTheme.colors.textDetailsScreenBody)
        }
        Text("案A", fontWeight = FontWeight.Bold, color = AppTheme.colors.textDetailsScreenBody)
        CalendarPreview(event, rounds, attendingIds, now) { selected = it }
        Text("案B", fontWeight = FontWeight.Bold, color = AppTheme.colors.textDetailsScreenBody)
        PreviewFrame {
            SnakePreview(rounds, attendingIds, now) { selected = it }
        }
    }
}

/** イベント帯の上に、開始の丸と呼び出しのベルを実時刻で重ねる。 */
@Composable
private fun CalendarPreview(event: EventDetail, rounds: List<RoundPreview>, ids: Set<Int>, now: Int, onSelect: (Int) -> Unit) {
    var expanded by remember(event.eventId) { mutableStateOf(false) }
    val timed = rounds.filter { it.minute != null }
    val eventStart = minuteOf(event.startTime)
    val eventEnd = minuteOf(event.endTime)
    val firstStart = eventStart ?: timed.firstOrNull()?.minute ?: 0
    val firstCall = timed.filter { it.isMine(ids) }.mapNotNull { it.callMinute }.minOrNull()
    val first = minOf(firstStart, firstCall ?: firstStart)
    var tagWidths by remember(event.eventId) { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    val last = maxOf(eventEnd ?: first + 60, timed.lastOrNull()?.minute ?: first + 60, first + 1)
    val axisStart = first / 30 * 30
    val axisEnd = ((last + 29) / 30 * 30).coerceAtLeast(axisStart + 30)
    val smallestGap = timed.mapNotNull { it.minute }.distinct().zipWithNext { a, b -> b - a }.minOrNull() ?: 30
    val minuteScale = maxOf(2f, 44f / smallestGap.coerceAtLeast(1))
    val fullHeight = ((axisEnd - axisStart) * minuteScale + 40).dp
    val visibleHeight = if (expanded) fullHeight else 180.dp
    fun position(minute: Int) = (20 + (minute - axisStart) * minuteScale).dp
    val focus = timed.firstOrNull { it.isMine(ids) }?.callMinute ?: timed.firstOrNull()?.minute ?: first
    val shift = if (expanded) 0.dp else (position(focus) - 42.dp).coerceIn(0.dp, (fullHeight - 180.dp).coerceAtLeast(0.dp))
    val grid = AppTheme.colors.textScheduleTimeLine
    val accent = AppTheme.colors.themeColorFirst
    val progress = Color.DarkGray.copy(alpha = 0.85f)
    val pending = Color.DarkGray.copy(alpha = 0.5f)
    val live = AppTheme.colors.themeColorSecond
    val band = live.copy(alpha = 0.22f)
    val current = timed.lastOrNull { it.minute!! <= now }?.takeIf { eventEnd == null || now < eventEnd }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(SettingsCornerRadius))
        .background(Color.White).border(1.dp, grid.copy(alpha = 0.35f), RoundedCornerShape(SettingsCornerRadius))
        .animateContentSize().height(visibleHeight).previewClick { expanded = !expanded }) {
        BoxWithConstraints(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).height(fullHeight).offset(y = -shift).padding(horizontal = 12.dp)) {
            val labelWidth = 42.dp
            val laneWidth = ((maxWidth - labelWidth) / 2).coerceAtLeast(1.dp)
            val tagWidth = (laneWidth - 6.dp).coerceAtLeast(1.dp)
            Canvas(Modifier.fillMaxSize()) {
                for (minute in axisStart..axisEnd step 30) {
                    val y = position(minute).toPx()
                    drawLine(grid.copy(alpha = 0.45f), Offset(labelWidth.toPx(), y), Offset(size.width, y), 1.dp.toPx())
                }
                if (eventStart != null && eventEnd != null && eventEnd > eventStart) {
                    drawRoundRect(band, topLeft = Offset(labelWidth.toPx(), position(eventStart).toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width - labelWidth.toPx(), ((eventEnd - eventStart) * minuteScale).dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
                }
                // 呼び出しから対象の開始タグへ、時刻の差を保った線をつなぐ。
                timed.filter { it.isMine(ids) }.groupBy { it.minute!! }.forEach { (minute, group) ->
                    val color = accent
                    val callY = position(minute - PrototypeCallLeadMinutes).toPx()
                    val startY = position(minute).toPx()
                    val callX = (labelWidth + laneWidth).toPx()
                    val startX = labelWidth.toPx() + (tagWidths[minute]?.toFloat() ?: tagWidth.toPx())
                    val path = Path().apply {
                        moveTo(callX, callY)
                        cubicTo(startX, callY, callX, startY, startX, startY)
                    }
                    drawPath(path, color.copy(alpha = 0.55f), style = Stroke(2.dp.toPx()))
                }
                if (now in axisStart..axisEnd) {
                    val y = position(now).toPx()
                    drawLine(live, Offset(labelWidth.toPx(), y), Offset(size.width, y), 2.dp.toPx())
                    drawCircle(live, 3.dp.toPx(), Offset(labelWidth.toPx(), y))
                }
            }
            for (minute in axisStart..axisEnd step 30) {
                Text(displayMinute(minute), modifier = Modifier.offset(y = position(minute) - 8.dp).width(labelWidth), fontSize = 10.sp, color = Color.DarkGray)
            }
            // 同時刻のラウンドは1つのタグにまとめる。
            timed.groupBy { it.minute!! }.forEach { (minute, group) ->
                val mine = group.any { it.isMine(ids) }
                val label = group.joinToString("・") { "${it.number}" } + "R"
                val isCurrent = group.any { it.number == current?.number }
                val tagColor = when {
                    mine -> accent
                    isCurrent -> live
                    minute <= now -> progress
                    else -> Color(0xFFE1E5E7)
                }
                val tagText = if (mine || isCurrent || minute <= now) Color.White else Color.DarkGray
                val onTap = { onSelect(group.first().number); expanded = true }
                Box(Modifier.offset(x = labelWidth, y = position(minute) - 22.dp)
                    .width(tagWidth).height(44.dp).clip(RoundedCornerShape(8.dp)).previewClick(onClick = onTap), contentAlignment = Alignment.CenterStart) {
                    Text("$label ${displayMinute(minute)}〜", modifier = Modifier
                        .onSizeChanged { if (tagWidths[minute] != it.width) tagWidths = tagWidths + (minute to it.width) }
                        .background(tagColor, CircleShape).padding(horizontal = 7.dp, vertical = 3.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = tagText, fontWeight = FontWeight.Bold)
                }
                if (mine) {
                    val callColor = accent
                    Row(Modifier.offset(x = labelWidth + laneWidth, y = position(minute - PrototypeCallLeadMinutes) - 22.dp)
                        .width(tagWidth).height(44.dp).clip(RoundedCornerShape(8.dp)).previewClick(onClick = onTap), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.background(Color.White, CircleShape).border(1.dp, callColor, CircleShape)
                            .padding(horizontal = 5.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(Res.drawable.ic_notification_outline), contentDescription = "呼び出し", modifier = Modifier.size(13.dp), tint = callColor)
                            Spacer(Modifier.width(3.dp))
                            Text("呼出 ${displayMinute(minute - PrototypeCallLeadMinutes)}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontSize = 10.sp, color = callColor, fontWeight = FontWeight.Bold)
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
private fun SnakePreview(rounds: List<RoundPreview>, ids: Set<Int>, now: Int, onSelect: (Int) -> Unit) {
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
                    .width(cellWidth).height(cellHeight).clip(RoundedCornerShape(8.dp)).previewClick { onSelect(round.number) }, horizontalAlignment = Alignment.CenterHorizontally) {
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

/** 比較用の図は波紋を出さず、選択内容の変化で反応を示す。 */
@Composable
private fun Modifier.previewClick(onClick: () -> Unit): Modifier = clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onClick,
)
