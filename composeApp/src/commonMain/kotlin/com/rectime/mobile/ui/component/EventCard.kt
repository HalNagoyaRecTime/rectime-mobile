package com.rectime.mobile.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rectime.mobile.ui.modifier.outerShadow
import kotlin.math.PI
import kotlin.math.sin

class EventCardDimensions(screenWidthDp: Dp) {
    val u = screenWidthDp.value / 100f

    // サイズ切り替わり閾値
    val compactHeightThreshold: Dp = (6f * u).dp
    val smallHeightThreshold: Dp = (9f * u).dp
    val mediumHeightThreshold: Dp = (17.1f * u).dp
    val mediumWidthThreshold: Dp = (35.0f * u).dp

    // 共通
    val cornerRadius: Dp = (2.07f * u).dp
    val borderExtend: Dp = (0.39f * u).dp
    val borderStroke: Dp = (0.5f * u).dp
    val waveAmplitude: Dp = (2f * u).dp
    val waveLength: Dp = (4f * u).dp
    val waveCenterOffset: Dp = (9.95f * u).dp
    val waveSlope: Float = -0.3f
    val glowRadius: Dp = (0.7f * u).dp

    // 大サイズ
    val largeCut: Dp = (10.08f * u).dp
    val largeBadgeFont: TextUnit = (3.26f * u).sp
    val largeBadgePad: Dp = (0.7f * u).dp
    val largeTimeFont: TextUnit = (3.1f * u).sp
    val largeTitleFont: TextUnit = (3.82f * u).sp
    val largeCourtFont: TextUnit = (3.1f * u).sp
    val largeArrowArm: Dp = (1.4f * u).dp
    val largeArrowStroke: Dp = (0.39f * u).dp
    val largeArrowOffsetX: Dp = (-3f * u).dp
    val largeArrowOffsetY: Dp = (4.5f * u).dp
    val largeInnerContentPadHorizontal: Dp = (3f * u).dp
    val largeInnerContentPadVertical: Dp = (0f * u).dp
    val largeTimeTitleSpacingMin: Dp = (0f * u).dp
    val largeTimeTitleSpacingMax: Dp = (1.8f * u).dp
    val largeTitleCourtSpacingMin: Dp = (0.3f * u).dp
    val largeTitleCourtSpacingMax: Dp = (2.2f * u).dp

    // 中サイズ
    val mediumCut: Dp = (7.49f * u).dp
    val mediumBadgeFont: TextUnit = (2.4f * u).sp
    val mediumBadgePad: Dp = (0.55f * u).dp
    val mediumTimeFont: TextUnit = (2.4f * u).sp
    val mediumTitleFont: TextUnit = (2.7f * u).sp
    val mediumCourtFont: TextUnit = (2.3f * u).sp
    val mediumArrowArm: Dp = (1.14f * u).dp
    val mediumArrowStroke: Dp = (0.32f * u).dp
    val mediumArrowOffsetX: Dp = (-3f * u).dp
    val mediumArrowOffsetY: Dp = (3f * u).dp
    val mediumInnerContentPadHorizontal: Dp = (2f * u).dp
    val mediumInnerContentPadVertical: Dp = (0f * u).dp
    val mediumTimeTitleSpacingMin: Dp = (0f * u).dp
    val mediumTimeTitleSpacingMax: Dp = (0.6f * u).dp
    val mediumTitleCourtSpacingMin: Dp = (0.2f * u).dp
    val mediumTitleCourtSpacingMax: Dp = (0.8f * u).dp

    // 小サイズ
    val smallCut: Dp = (7.49f * u).dp
    val smallBadgeFont: TextUnit = (2.4f * u).sp
    val smallBadgePad: Dp = (0.55f * u).dp
    val smallTimeFont: TextUnit = (2.3f * u).sp
    val smallTitleFont: TextUnit = (2.6f * u).sp
    val smallArrowArm: Dp = (1.14f * u).dp
    val smallArrowStroke: Dp = (0.32f * u).dp
    val smallArrowOffsetX: Dp = (-3f * u).dp
    val smallArrowOffsetY: Dp = (1.5f * u).dp
    val smallInnerContentPadHorizontal: Dp = (1.5f * u).dp
    val smallInnerContentPadVertical: Dp = (0f * u).dp
    val smallTimeTitleSpacingMin: Dp = (0f * u).dp
    val smallTimeTitleSpacingMax: Dp = (0.1f * u).dp

    // 極小サイズ
    val compactCut: Dp = (6.1f * u).dp
    val compactTitleMaxFontSize: TextUnit = (2.4f * u).sp
    val compactArrowArm: Dp = (0.9f * u).dp
    val compactArrowStroke: Dp = (0.25f * u).dp
    val compactArrowOffsetX: Dp = (-1.5f * u).dp
    val compactInnerContentPadHorizontal: Dp = (1.5f * u).dp
    val compactInnerContentPadVertical: Dp = (0f * u).dp
}

@Composable
private fun tightlySpacedTextStyle(
    fontSize: TextUnit,
    fontWeight: FontWeight,
    fontFamily: FontFamily?,
    color: Color = Color.White
): TextStyle {
    return TextStyle(
        color = color,
        fontSize = fontSize,
        lineHeight = fontSize,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )
}

class EventCardCutShape(
    private val cornerRadius: Dp,
    private val cutSize: Dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return Outline.Generic(Path())

        val r = with(density) { cornerRadius.toPx() }.coerceAtMost(minOf(w, h) / 2f)
        val c = with(density) { cutSize.toPx() }

        val baseRoundedRectPath = Path().apply {
            addRoundRect(RoundRect(0f, 0f, w, h, r, r))
        }

        if (c <= 0f) {
            return Outline.Generic(baseRoundedRectPath)
        }

        val cutTrianglePath = Path().apply {
            moveTo(w, h - c)
            lineTo(w + 10f, h - c)
            lineTo(w + 10f, h + 10f)
            lineTo(w - c, h + 10f)
            lineTo(w - c, h)
            close()
        }

        val finalPath = Path()
        finalPath.op(baseRoundedRectPath, cutTrianglePath, PathOperation.Difference)

        return Outline.Generic(finalPath)
    }
}

enum class EventCardSizeVariant {
    Compact, // 極小: タイトルのみ最大表示
    Small,   // 小: 時間 + タイトルのみ縦並び
    Medium,  // 中: フル表示
    Large    // 大: フル表示
}

@Composable
fun EventCard(
    time: String,
    title: String,
    court: String,
    isLive: Boolean,
    isParticipating: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily? = null,
) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val screenWidthDp = remember(windowInfo.containerSize.width) {
        with(density) { windowInfo.containerSize.width.toDp() }
    }
    val dim = remember(screenWidthDp) { EventCardDimensions(screenWidthDp) }

    val infiniteTransition = rememberInfiniteTransition(label = "waveTransition")
    val waveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveProgress"
    )

    BoxWithConstraints(modifier = modifier) {

        val currentHeight = maxHeight
        val currentWidth = maxWidth

        val variant = when {
            currentHeight < dim.compactHeightThreshold -> EventCardSizeVariant.Compact
            currentHeight < dim.smallHeightThreshold -> EventCardSizeVariant.Small
            currentHeight < dim.mediumHeightThreshold || currentWidth < dim.mediumWidthThreshold -> EventCardSizeVariant.Medium
            else -> EventCardSizeVariant.Large
        }

        val timeToTitleSpacing: Dp
        val titleToCourtSpacing: Dp

        when (variant) {
            EventCardSizeVariant.Large -> {
                val ratio =
                    ((currentHeight - dim.mediumHeightThreshold) / (20f * dim.u).dp).coerceIn(0f, 1f)
                timeToTitleSpacing =
                    dim.largeTimeTitleSpacingMin + (dim.largeTimeTitleSpacingMax - dim.largeTimeTitleSpacingMin) * ratio
                titleToCourtSpacing =
                    dim.largeTitleCourtSpacingMin + (dim.largeTitleCourtSpacingMax - dim.largeTitleCourtSpacingMin) * ratio
            }

            EventCardSizeVariant.Medium -> {
                val ratio =
                    ((currentHeight - dim.smallHeightThreshold) / (dim.mediumHeightThreshold - dim.smallHeightThreshold)).coerceIn(
                        0f,
                        1f
                    )
                timeToTitleSpacing =
                    dim.mediumTimeTitleSpacingMin + (dim.mediumTimeTitleSpacingMax - dim.mediumTimeTitleSpacingMin) * ratio
                titleToCourtSpacing =
                    dim.mediumTitleCourtSpacingMin + (dim.mediumTitleCourtSpacingMax - dim.mediumTitleCourtSpacingMin) * ratio
            }

            EventCardSizeVariant.Small -> {
                val ratio =
                    ((currentHeight - dim.compactHeightThreshold) / (dim.smallHeightThreshold - dim.compactHeightThreshold)).coerceIn(
                        0f,
                        1f
                    )
                timeToTitleSpacing =
                    dim.smallTimeTitleSpacingMin + (dim.smallTimeTitleSpacingMax - dim.smallTimeTitleSpacingMin) * ratio
                titleToCourtSpacing = 0.dp
            }

            EventCardSizeVariant.Compact -> {
                timeToTitleSpacing = 0.dp
                titleToCourtSpacing = 0.dp
            }
        }

        val cutSize = when {
            !isParticipating -> 0.dp
            variant == EventCardSizeVariant.Large -> dim.largeCut
            variant == EventCardSizeVariant.Medium -> dim.mediumCut
            variant == EventCardSizeVariant.Small -> dim.smallCut
            else -> dim.compactCut
        }

        val badgePad = when {
            !isParticipating -> 0.dp
            variant == EventCardSizeVariant.Large -> dim.largeBadgePad
            variant == EventCardSizeVariant.Medium -> dim.mediumBadgePad
            variant == EventCardSizeVariant.Small -> dim.smallBadgePad
            else -> 0.dp
        }

        val innerContentPadHorizontal = when (variant) {
            EventCardSizeVariant.Large -> dim.largeInnerContentPadHorizontal
            EventCardSizeVariant.Medium -> dim.mediumInnerContentPadHorizontal
            EventCardSizeVariant.Small -> dim.smallInnerContentPadHorizontal
            EventCardSizeVariant.Compact -> dim.compactInnerContentPadHorizontal
        }

        val innerContentPadVertical = when (variant) {
            EventCardSizeVariant.Large -> dim.largeInnerContentPadVertical
            EventCardSizeVariant.Medium -> dim.mediumInnerContentPadVertical
            EventCardSizeVariant.Small -> dim.smallInnerContentPadVertical
            EventCardSizeVariant.Compact -> dim.compactInnerContentPadVertical
        }

        val orangeShape = when {
            isLive -> RoundedCornerShape(dim.cornerRadius + dim.borderExtend)
            else -> RoundedCornerShape(dim.cornerRadius)
        }
        val contentShape = EventCardCutShape(dim.cornerRadius, cutSize)

        val hasOrangeBase = isLive || isParticipating
        val orangeColor = Color(0xFFFF4000)

        PressSurface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { clip = false }
                .then(
                    if (isLive) {
                        Modifier.outerShadow(
                            shape = orangeShape,
                            color = orangeColor,
                            blurRadius = dim.glowRadius,
                            offsetX = 0.dp,
                            offsetY = 0.dp,
                            spread = dim.glowRadius * 0.1f
                        )
                    } else Modifier
                ),
            color = Color.Transparent,
            shape = RoundedCornerShape(dim.cornerRadius),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        when {
                            isLive -> Modifier.background(orangeColor, shape = orangeShape)
                            isParticipating -> Modifier
                                .padding(dim.borderExtend)
                                .background(orangeColor, shape = orangeShape)
                            else -> Modifier.padding(dim.borderExtend)
                        }
                    )
                    .clip(orangeShape)
            ) {
                if (isParticipating && variant != EventCardSizeVariant.Compact) {
                    val badgeFontSize = when (variant) {
                        EventCardSizeVariant.Large -> dim.largeBadgeFont
                        EventCardSizeVariant.Medium -> dim.mediumBadgeFont
                        else -> dim.smallBadgeFont
                    }
                    Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Text(
                            text = "出場",
                            style = tightlySpacedTextStyle(
                                fontSize = badgeFontSize,
                                fontWeight = FontWeight.Black,
                                fontFamily = fontFamily
                            ),
                            modifier = Modifier
                                .padding(
                                    bottom = badgePad * 1.6f + if (isLive) dim.borderExtend * 0.5f else 0.dp,
                                    end = badgePad + if (isLive) dim.borderExtend * 0.5f else 0.dp
                                )
                                .rotate(-45f)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isLive) Modifier.padding(dim.borderExtend)
                            else Modifier
                        )
                        .outerShadow(
                            shape = contentShape,
                            color = Color.Black.copy(alpha = 0.15f),
                            blurRadius = (1.5f * dim.u).dp,
                            offsetX = 0.dp,
                            offsetY = (0.5f * dim.u).dp
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(contentShape)
                            .background(Color(0xFF2AB3BF))
                            .drawWithCache {
                                val w = size.width
                                val h = size.height

                                val gradientBrush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.30f),
                                        Color(0xFF2AB3BF).copy(alpha = 0.30f)
                                    ),
                                    start = Offset(0f, 0f),
                                    end = Offset(w, h)
                                )

                                val centerBaseX = w - dim.waveCenterOffset.toPx()
                                val slope = dim.waveSlope
                                val amplitude = dim.waveAmplitude.toPx()
                                val waveLength = dim.waveLength.toPx()
                                val strokeWidthPx = dim.borderStroke.toPx()
                                val cutPx = cutSize.toPx()
                                val centerY = h / 2f

                                onDrawWithContent {
                                    drawContent()

                                    if (isLive) {
                                        val wavePath = Path()
                                        var y = h
                                        var first = true

                                        while (y >= 0) {
                                            val lineX = centerBaseX + slope * (y - centerY)
                                            val phase = (y / waveLength) + (waveProgress * 2f * PI.toFloat())
                                            val waveX = lineX + sin(phase) * amplitude

                                            if (first) {
                                                wavePath.moveTo(waveX, y)
                                                first = false
                                            } else {
                                                wavePath.lineTo(waveX, y)
                                            }
                                            y -= 2f
                                        }

                                        wavePath.lineTo(w, 0f)
                                        wavePath.lineTo(w, h)
                                        wavePath.close()

                                        drawPath(wavePath, Color.White.copy(alpha = 0.25f))
                                    } else {
                                        drawRect(Color.White.copy(alpha = 0.15f))
                                    }

                                    drawRect(gradientBrush, blendMode = BlendMode.Multiply)

                                    if (isLive) {
                                        val outline = contentShape.createOutline(size, layoutDirection, this)
                                        if (outline is Outline.Generic) {
                                            drawPath(
                                                outline.path,
                                                Color.White.copy(alpha = 0.50f),
                                                style = Stroke(width = strokeWidthPx)
                                            )
                                        }
                                    } else if (isParticipating && cutPx > 0f) {
                                        val cutLinePath = Path().apply {
                                            moveTo(w, (h - cutPx))
                                            lineTo((w - cutPx), h)
                                        }
                                        drawPath(
                                            cutLinePath,
                                            Color.White.copy(alpha = 0.50f),
                                            style = Stroke(
                                                width = strokeWidthPx,
                                                cap = StrokeCap.Round
                                            )
                                        )
                                    }
                                }
                            }
                            .padding(
                                horizontal = innerContentPadHorizontal,
                                vertical = innerContentPadVertical
                            )
                    ) {
                        EventCardInnerContent(
                            time = time,
                            title = title,
                            court = court,
                            variant = variant,
                            isLive = isLive,
                            cutSize = cutSize,
                            timeToTitleSpacing = timeToTitleSpacing,
                            titleToCourtSpacing = titleToCourtSpacing,
                            dim = dim,
                            fontFamily = fontFamily
                        )
                    }
                }

                val armLength = when (variant) {
                    EventCardSizeVariant.Large -> dim.largeArrowArm
                    EventCardSizeVariant.Medium -> dim.mediumArrowArm
                    EventCardSizeVariant.Small -> dim.smallArrowArm
                    EventCardSizeVariant.Compact -> dim.compactArrowArm
                }
                val stroke = when (variant) {
                    EventCardSizeVariant.Large -> dim.largeArrowStroke
                    EventCardSizeVariant.Medium -> dim.mediumArrowStroke
                    EventCardSizeVariant.Small -> dim.smallArrowStroke
                    EventCardSizeVariant.Compact -> dim.compactArrowStroke
                }
                val offsetX = when (variant) {
                    EventCardSizeVariant.Large -> dim.largeArrowOffsetX
                    EventCardSizeVariant.Medium -> dim.mediumArrowOffsetX
                    EventCardSizeVariant.Small -> dim.smallArrowOffsetX
                    EventCardSizeVariant.Compact -> dim.compactArrowOffsetX
                }
                val offsetY = when (variant) {
                    EventCardSizeVariant.Large -> dim.largeArrowOffsetY
                    EventCardSizeVariant.Medium -> dim.mediumArrowOffsetY
                    EventCardSizeVariant.Small -> dim.smallArrowOffsetY
                    EventCardSizeVariant.Compact -> -1f.dp
                }

                val isCompact = variant == EventCardSizeVariant.Compact

                Box(
                    modifier = Modifier
                        .align(if (isCompact) Alignment.CenterEnd else Alignment.TopEnd)
                        .offset(x = offsetX, y = offsetY)
                        .size(armLength * 1.5f),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val arm = armLength.toPx()
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(arm, arm)
                            lineTo(0f, arm * 2f)
                        }
                        drawPath(
                            path = path,
                            color = Color.White,
                            style = Stroke(
                                width = stroke.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCardInnerContent(
    time: String,
    title: String,
    court: String,
    variant: EventCardSizeVariant,
    isLive: Boolean,
    cutSize: Dp,
    timeToTitleSpacing: Dp,
    titleToCourtSpacing: Dp,
    dim: EventCardDimensions,
    fontFamily: FontFamily?,
) {
    val timeWeight = if (isLive) FontWeight.Medium else FontWeight.Normal
    val titleWeight = if (isLive) FontWeight.Black else FontWeight.Bold
    val courtWeight = if (isLive) FontWeight.Bold else FontWeight.Medium

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterStart
    ) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        val rightReservedSpace = if (variant == EventCardSizeVariant.Compact || variant == EventCardSizeVariant.Small) {
            cutSize * 0.5f
        } else {
            0.dp
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = rightReservedSpace),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            when (variant) {
                EventCardSizeVariant.Large -> {
                    Text(
                        text = time,
                        style = tightlySpacedTextStyle(
                            fontSize = dim.largeTimeFont,
                            fontWeight = timeWeight,
                            fontFamily = fontFamily
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(timeToTitleSpacing))
                    Text(
                        text = title,
                        style = tightlySpacedTextStyle(
                            fontSize = dim.largeTitleFont,
                            fontWeight = titleWeight,
                            fontFamily = fontFamily
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(titleToCourtSpacing))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.15f))
                            .padding(
                                horizontal = (2.3f * dim.u).dp,
                                vertical = (0.1f * dim.u).dp
                            )
                    ) {
                        Text(
                            text = court,
                            style = tightlySpacedTextStyle(
                                fontSize = dim.largeCourtFont,
                                fontWeight = courtWeight,
                                fontFamily = fontFamily
                            )
                        )
                    }
                }
                EventCardSizeVariant.Medium -> {
                    Text(
                        text = time,
                        style = tightlySpacedTextStyle(
                            fontSize = dim.mediumTimeFont,
                            fontWeight = timeWeight,
                            fontFamily = fontFamily
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(timeToTitleSpacing))
                    Text(
                        text = title,
                        style = tightlySpacedTextStyle(
                            fontSize = dim.mediumTitleFont,
                            fontWeight = titleWeight,
                            fontFamily = fontFamily
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(titleToCourtSpacing))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.15f))
                            .padding(
                                horizontal = (2f * dim.u).dp,
                                vertical = (0.1f * dim.u).dp
                            )
                    ) {
                        Text(
                            text = court,
                            style = tightlySpacedTextStyle(
                                fontSize = dim.mediumCourtFont,
                                fontWeight = courtWeight,
                                fontFamily = fontFamily
                            )
                        )
                    }
                }
                EventCardSizeVariant.Small -> {
                    Text(
                        text = time,
                        style = tightlySpacedTextStyle(
                            fontSize = dim.smallTimeFont,
                            fontWeight = timeWeight,
                            fontFamily = fontFamily
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(timeToTitleSpacing))
                    Text(
                        text = title,
                        style = tightlySpacedTextStyle(
                            fontSize = dim.smallTitleFont,
                            fontWeight = titleWeight,
                            fontFamily = fontFamily
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                EventCardSizeVariant.Compact -> {
                    val density = LocalDensity.current
                    val heightBasedSp = with(density) { (availableHeight * 0.75f).toSp() }
                    val finalFontSize = if (heightBasedSp.value > dim.compactTitleMaxFontSize.value) {
                        dim.compactTitleMaxFontSize
                    } else {
                        heightBasedSp.value.coerceAtLeast(4f).sp
                    }

                    Text(
                        text = title,
                        style = tightlySpacedTextStyle(
                            fontSize = finalFontSize,
                            fontWeight = titleWeight,
                            fontFamily = fontFamily
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}