package com.rectime.mobile.ui.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.withSave
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.rectime.mobile.ui.graphics.clearBlur
import com.rectime.mobile.ui.graphics.setBlur

fun Modifier.outerShadow(
    shape: Shape? = null,
    color: Color = Color.Black.copy(alpha = 0.25f),
    blurRadius: Dp = 12.dp,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 4.dp,
    spread: Dp = 0.dp,
    blendMode: BlendMode = BlendMode.SrcOver
): Modifier = this then OuterShadowElement(
    shape = shape,
    color = color,
    blurRadius = blurRadius,
    offsetX = offsetX,
    offsetY = offsetY,
    spread = spread,
    blendMode = blendMode
)

private data class OuterShadowElement(
    val shape: Shape?,
    val color: Color,
    val blurRadius: Dp,
    val offsetX: Dp,
    val offsetY: Dp,
    val spread: Dp,
    val blendMode: BlendMode
) : ModifierNodeElement<OuterShadowNode>() {

    override fun create(): OuterShadowNode = OuterShadowNode(
        shape = shape,
        color = color,
        blurRadius = blurRadius,
        offsetX = offsetX,
        offsetY = offsetY,
        spread = spread,
        blendMode = blendMode
    )

    override fun update(node: OuterShadowNode) {
        node.update(shape, color, blurRadius, offsetX, offsetY, spread, blendMode)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "outerShadow"
        properties["shape"] = shape
        properties["color"] = color
        properties["blurRadius"] = blurRadius
        properties["offsetX"] = offsetX
        properties["offsetY"] = offsetY
        properties["spread"] = spread
        properties["blendMode"] = blendMode
    }
}

private class OuterShadowNode(
    var shape: Shape?,
    var color: Color,
    var blurRadius: Dp,
    var offsetX: Dp,
    var offsetY: Dp,
    var spread: Dp,
    var blendMode: BlendMode
) : Modifier.Node(), DrawModifierNode {

    private val fillPaint = Paint().apply {
        style = PaintingStyle.Fill
    }
    private val strokePaint = Paint().apply {
        style = PaintingStyle.Stroke
        strokeJoin = StrokeJoin.Round
        strokeCap = StrokeCap.Round
    }
    private val layerPaint = Paint()

    private val originalPath = Path()
    private val shapePath = Path()
    private val tempLayerBounds = MutableRect(0f, 0f, 0f, 0f)

    private var lastSize: Size = Size.Unspecified
    private var lastLayoutDirection: LayoutDirection? = null
    private var lastDensity: Float = 0f
    private var isPathDirty = true
    private var isPaintDirty = true

    fun update(
        shape: Shape?,
        color: Color,
        blurRadius: Dp,
        offsetX: Dp,
        offsetY: Dp,
        spread: Dp,
        blendMode: BlendMode
    ) {
        if (this.shape != shape) {
            isPathDirty = true
        }
        if (this.color != color || this.blurRadius != blurRadius ||
            this.spread != spread || this.blendMode != blendMode) {
            isPaintDirty = true
        }

        this.shape = shape
        this.color = color
        this.blurRadius = blurRadius
        this.offsetX = offsetX
        this.offsetY = offsetY
        this.spread = spread
        this.blendMode = blendMode

        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        if (size.width <= 0f || size.height <= 0f || color.alpha <= 0f) {
            drawContent()
            return
        }

        val currentDensity = density
        val currentSize = size
        val currentDirection = layoutDirection

        if (lastDensity != currentDensity) {
            isPaintDirty = true
            isPathDirty = true
            lastDensity = currentDensity
        }

        if (isPathDirty || lastSize != currentSize || lastLayoutDirection != currentDirection) {
            recalculatePaths(currentSize, currentDirection, this)
            lastSize = currentSize
            lastLayoutDirection = currentDirection
            isPathDirty = false
        }

        val spreadPx = spread.toPx().coerceAtLeast(0f)
        val blurPx = blurRadius.toPx().coerceAtLeast(0f)
        val offsetXPx = offsetX.toPx()
        val offsetYPx = offsetY.toPx()

        if (isPaintDirty) {
            val opaqueColor = color.copy(alpha = 1.0f)
            fillPaint.color = opaqueColor
            strokePaint.color = opaqueColor
            strokePaint.strokeWidth = spreadPx * 2f

            if (blurPx > 0f) {
                fillPaint.setBlur(blurPx)
                strokePaint.setBlur(blurPx)
            } else {
                fillPaint.clearBlur()
                strokePaint.clearBlur()
            }

            layerPaint.alpha = color.alpha
            layerPaint.blendMode = blendMode
            layerPaint.clearBlur()

            isPaintDirty = false
        }

        val extra = spreadPx + blurPx * 3f
        val minX = minOf(0f, offsetXPx) - extra
        val maxX = maxOf(size.width, size.width + offsetXPx) + extra
        val minY = minOf(0f, offsetYPx) - extra
        val maxY = maxOf(size.height, size.height + offsetYPx) + extra

        tempLayerBounds.set(
            left = minX,
            top = minY,
            right = maxX,
            bottom = maxY
        )

        drawIntoCanvas { canvas ->
            canvas.withSave {
                if (shape != null) {
                    canvas.clipPath(originalPath, clipOp = ClipOp.Difference)
                }

                canvas.saveLayer(tempLayerBounds.toRect(), layerPaint)

                canvas.translate(offsetXPx, offsetYPx)
                canvas.drawPath(shapePath, fillPaint)

                if (spreadPx > 0f) {
                    canvas.drawPath(shapePath, strokePaint)
                }

                canvas.restore()
            }
        }

        drawContent()
    }

    private fun recalculatePaths(
        currentSize: Size,
        direction: LayoutDirection,
        density: Density
    ) {
        originalPath.reset()
        shape?.let {
            val baseOutline = it.createOutline(currentSize, direction, density)
            val insetPx = with(density) { 0.5.dp.toPx() }
            shrinkOutlineAndAddToPath(baseOutline, currentSize, insetPx, originalPath)
        }

        shapePath.reset()
        val targetShape = shape ?: RectangleShape
        val baseOutline = targetShape.createOutline(currentSize, direction, density)
        shapePath.addOutline(baseOutline)
    }

    private fun shrinkOutlineAndAddToPath(
        outline: Outline,
        size: Size,
        insetPx: Float,
        targetPath: Path
    ) {
        when (outline) {
            is Outline.Rectangle -> {
                val left = insetPx.coerceAtMost(size.width / 2f)
                val top = insetPx.coerceAtMost(size.height / 2f)
                val right = (size.width - insetPx).coerceAtLeast(left)
                val bottom = (size.height - insetPx).coerceAtLeast(top)
                targetPath.addRect(Rect(left, top, right, bottom))
            }
            is Outline.Rounded -> {
                val rr = outline.roundRect
                val left = insetPx.coerceAtMost(size.width / 2f)
                val top = insetPx.coerceAtMost(size.height / 2f)
                val right = (size.width - insetPx).coerceAtLeast(left)
                val bottom = (size.height - insetPx).coerceAtLeast(top)

                targetPath.addRoundRect(
                    RoundRect(
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom,
                        topLeftCornerRadius = rr.topLeftCornerRadius.shrinkBy(insetPx),
                        topRightCornerRadius = rr.topRightCornerRadius.shrinkBy(insetPx),
                        bottomRightCornerRadius = rr.bottomRightCornerRadius.shrinkBy(insetPx),
                        bottomLeftCornerRadius = rr.bottomLeftCornerRadius.shrinkBy(insetPx)
                    )
                )
            }
            is Outline.Generic -> {
                targetPath.addPath(outline.path)
            }
        }
    }
}

private fun CornerRadius.shrinkBy(insetPx: Float): CornerRadius =
    CornerRadius(
        x = (x - insetPx).coerceAtLeast(0f),
        y = (y - insetPx).coerceAtLeast(0f)
    )