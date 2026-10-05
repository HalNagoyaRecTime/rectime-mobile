package com.rectime.mobile.feature.splash

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.Choreographer
import android.view.HapticFeedbackConstants
import android.view.View
import com.rectime.mobile.core.haptics.HapticEnabledKey
import com.rectime.mobile.core.haptics.HapticPreference
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** iOSの確定案と同じ時刻・座標・紙の折り返しで描画するAndroid起動演出。 */
internal class RecreationSplashView(context: Context, private val onFinished: () -> Unit) : View(context), Choreographer.FrameCallback {
    private val playback = SplashPlayback()
    private val choreographer = Choreographer.getInstance()
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif-black", Typeface.NORMAL) }
    private val orange = Color.rgb(255, 64, 0)
    private val yellow = Color.rgb(252, 177, 0)
    private val teal = Color.rgb(42, 179, 191)
    private val dark = Color.rgb(34, 34, 34)
    private val logoColors = listOf(orange, yellow, teal)
    private val reduceMotion = !ValueAnimator.areAnimatorsEnabled()
    private val vibrationEnabled = runCatching {
        context.getSharedPreferences("rectime_haptics", Context.MODE_PRIVATE)
            .getString(HapticEnabledKey, null)?.toBooleanStrictOrNull() ?: HapticPreference.DefaultEnabled
    }.getOrDefault(HapticPreference.DefaultEnabled)
    private var callbackPosted = false
    private var completionDelivered = false
    private var letterWidths = FloatArray(SplashPlayback.Title.length)
    private var fontSize = 0f
    private val logo = listOf(
        Path().apply {
            moveTo(84.36969f, 537.4139f)
            cubicTo(367.24634f, 191.7316f, 1344.7816f, 165.36884f, 623.00415f, 641.17926f)
            cubicTo(823.81177f, 208.87148f, 184.198f, 424.79263f, 84.36969f, 537.4139f)
            close()
        },
        Path().apply { moveTo(620.7169f, 197.27536f); lineTo(147.88499f, 871.6322f); lineTo(400.04178f, 827.97144f); close() },
        Path().apply {
            moveTo(913.1463f, 614.44525f)
            cubicTo(529.8711f, 815.4336f, 341.62164f, 416.99454f, 543.9396f, 55.233986f)
            cubicTo(122.55553f, 577.10706f, 655.20575f, 1251.5168f, 913.1463f, 614.44525f)
            close()
        },
    )
    // SF SymbolsはAndroidにないため、同じ8競技を人型の線画として描く。
    private val athletes = listOf(
        Athlete(-160f, orange, floatArrayOf(-5f, -14f, 0f, 3f, -12f, 17f, -16f, 25f), floatArrayOf(0f, 3f, 12f, 12f, 15f, 25f), floatArrayOf(-5f, -12f, 6f, -19f, 15f, -29f), floatArrayOf(-3f, -11f, -15f, -3f, -19f, 8f), -5f, -24f, "basketball"),
        Athlete(-130f, teal, floatArrayOf(0f, -13f, 1f, 4f, -7f, 17f, -9f, 26f), floatArrayOf(1f, 4f, 11f, 18f, 14f, 26f), floatArrayOf(0f, -12f, -12f, -21f, -13f, -31f), floatArrayOf(0f, -12f, 12f, -22f, 15f, -31f), 0f, -23f, "volleyball"),
        Athlete(-100f, Color.DKGRAY, floatArrayOf(-3f, -13f, 2f, 4f, -8f, 14f, -13f, 26f), floatArrayOf(2f, 4f, 14f, 12f, 24f, 11f), floatArrayOf(-3f, -10f, -14f, -5f, -21f, 2f), floatArrayOf(-3f, -10f, 9f, -8f, 16f, -16f), -6f, -24f, "soccer"),
        Athlete(-70f, orange, floatArrayOf(-2f, -13f, 2f, 3f, -10f, 14f, -14f, 26f), floatArrayOf(2f, 3f, 13f, 15f, 20f, 26f), floatArrayOf(-2f, -11f, -14f, -19f, -18f, -30f), floatArrayOf(-2f, -11f, 10f, -18f, 17f, -29f), -4f, -24f, "badminton"),
        Athlete(-40f, teal, floatArrayOf(-7f, -10f, 1f, 4f, -9f, 16f, -14f, 26f), floatArrayOf(1f, 4f, 14f, 16f, 17f, 26f), floatArrayOf(-6f, -10f, 5f, -9f, 17f, -17f), floatArrayOf(-6f, -10f, -17f, 1f, -12f, 9f), -11f, -21f, "tableTennis"),
        Athlete(-10f, Color.DKGRAY, floatArrayOf(-2f, -12f, 1f, 4f, -11f, 17f, -17f, 25f), floatArrayOf(1f, 4f, 13f, 16f, 16f, 26f), floatArrayOf(-2f, -11f, 10f, -18f, 20f, -12f), floatArrayOf(-2f, -11f, -12f, -1f, -21f, -4f), -4f, -23f, "tennis"),
        Athlete(20f, orange, floatArrayOf(-2f, -12f, 1f, 3f, -11f, 16f, -14f, 26f), floatArrayOf(1f, 3f, 12f, 15f, 20f, 25f), floatArrayOf(-2f, -11f, 10f, -22f, 20f, -16f), floatArrayOf(-2f, -11f, 9f, -19f, 20f, -16f), -4f, -24f, "baseball"),
        Athlete(160f, teal, floatArrayOf(0f, -12f, -3f, 4f, -15f, 13f, -24f, 9f), floatArrayOf(-3f, 4f, 9f, 14f, 13f, 26f), floatArrayOf(0f, -10f, 13f, -4f, 21f, -15f), floatArrayOf(0f, -10f, -12f, -4f, -20f, -10f), 3f, -23f, "run"),
    )

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        // Android 8のCanvasでも紙の影を同じように表示する。
        if (Build.VERSION.SDK_INT < 28) setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setPlaybackActive(active: Boolean) {
        if (active == playback.isActive) return
        playback.setActive(active)
        if (active) postFrame() else removeFrame()
    }

    fun finish() {
        playback.finish()
        removeFrame()
        if (!completionDelivered) {
            completionDelivered = true
            onFinished()
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); postFrame() }
    override fun onDetachedFromWindow() { removeFrame(); super.onDetachedFromWindow() }

    private fun postFrame() {
        if (isAttachedToWindow && playback.isActive && !callbackPosted) {
            callbackPosted = true
            choreographer.postFrameCallback(this)
        }
    }

    private fun removeFrame() {
        choreographer.removeFrameCallback(this)
        callbackPosted = false
    }

    override fun doFrame(frameTimeNanos: Long) {
        callbackPosted = false
        val appeared = playback.frame(frameTimeNanos / 1_000_000)
        if (playback.finished) { finish(); return }
        if (vibrationEnabled && appeared.isNotEmpty()) {
            // フレームが大幅に遅れた場合は、振動を同時に大量再生しない。
            val letter = SplashPlayback.Title[appeared.last()]
            runCatching { performHapticFeedback(splashHapticConstant(letter)) }
        }
        invalidate()
        postFrame()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        fontSize = min(28f, w / density / 14f)
        textPaint.textSize = fontSize
        letterWidths = SplashPlayback.Title.map { textPaint.measureText(it.toString()) }.toFloatArray()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width / density
        val h = height / density
        if (w <= 0 || h <= 0) return
        val elapsed = playback.elapsedMillis / 1000f
        val turn = if (reduceMotion) 0f else ease((elapsed - 2f) / .75f)
        val crease = w + h - turn * (w + h + max(w, h) * .2f)
        canvas.save()
        canvas.scale(density, density)
        if (reduceMotion) canvas.saveLayerAlpha(0f, 0f, w, h, ((1 - clamp((elapsed - 2f) / .25f)) * 255).toInt())
        canvas.save()
        canvas.clipPath(polygon(clipSplashPage(w, h, crease, true)))
        canvas.drawColor(Color.WHITE)
        drawFront(canvas, w, h, elapsed)
        canvas.restore()
        if (turn > 0) drawFold(canvas, w, h, crease)
        if (reduceMotion) canvas.restore()
        canvas.restore()
    }

    private fun drawFront(canvas: Canvas, w: Float, h: Float, elapsed: Float) {
        val cx = w / 2
        val cy = h / 2 - 35
        val icon = min(220f, w * .57f)
        if (!reduceMotion) drawAthletes(canvas, w, h, cx, cy, icon, elapsed)
        val age = elapsed - .28f
        val appearance = if (reduceMotion) 1f else spring(age / .7f)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(icon / 1024 * appearance, icon / 1024 * appearance)
        canvas.translate(-512f, -512f)
        paint.style = Paint.Style.FILL
        paint.shader = null
        for ((index, path) in logo.withIndex()) {
            paint.color = logoColors[index]
            paint.alpha = (clamp(age / .12f) * 204).toInt()
            canvas.drawPath(path, paint)
        }
        canvas.restore()
        val spacing = fontSize * .5f / 17
        var cursor = cx - (letterWidths.sum() + spacing * (letterWidths.size - 1)) / 2
        for ((index, letter) in SplashPlayback.Title.withIndex()) {
            val x = cursor + letterWidths[index] / 2
            cursor += letterWidths[index] + spacing
            if (index >= playback.appearances.size) continue
            val progress = clamp((playback.elapsedMillis - playback.appearances[index]) / 340f)
            val growing = 1 - (1 - clamp(progress / .55f)).pow(3)
            val settling = ease((progress - .55f) / .45f)
            val scale = if (reduceMotion) 1f else if (progress < .55f) .2f + 1.08f * growing else 1.28f - .28f * settling
            val rise = if (reduceMotion) 0f else if (progress < .55f) -10 + 8 * growing else -2 + 2 * settling
            canvas.save()
            canvas.translate(x, cy + icon / 2 + 46 + rise)
            canvas.scale(scale, scale)
            val alpha = if (reduceMotion) 255 else (clamp(progress / .15f) * 255).toInt()
            if (letter == ':') {
                paint.style = Paint.Style.FILL
                paint.alpha = alpha
                val unit = fontSize / 24
                paint.color = teal; paint.alpha = alpha
                canvas.drawCircle(0f, -5 * unit, 3 * unit, paint)
                paint.color = yellow; paint.alpha = alpha
                canvas.drawCircle(0f, 5 * unit, 3 * unit, paint)
            } else {
                textPaint.color = if (index == 3) orange else dark
                textPaint.alpha = alpha
                val baseline = -(textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2
                canvas.drawText(letter.toString(), -letterWidths[index] / 2, baseline, textPaint)
            }
            canvas.restore()
        }
    }

    private fun drawAthletes(canvas: Canvas, w: Float, h: Float, cx: Float, cy: Float, icon: Float, elapsed: Float) {
        val symbol = min(60f, w * .15f)
        val radiusX = min(w / 2 - symbol * .7f, icon * .76f)
        for ((index, athlete) in athletes.withIndex()) {
            val age = elapsed - .06f - index * .055f
            if (age <= 0) continue
            val angle = athlete.angle * PI.toFloat() / 180
            val dx = cos(angle); val dy = sin(angle)
            val arrival = ease((age - .08f) / .85f)
            val outside = min((if (dx > 0) w - cx else cx) / max(kotlin.math.abs(dx), .001f),
                (if (dy > 0) h - cy else cy) / max(kotlin.math.abs(dy), .001f)) + symbol * 2
            val pop = spring(age / .38f)
            canvas.save()
            canvas.translate(cx + dx * (outside + (radiusX - outside) * arrival),
                cy + dy * (outside + (icon * .87f - outside) * arrival) + (1 - pop) * 28)
            val lean = (if (index % 2 == 0) -1 else 1) * (1 - arrival) * .25f + sin(clamp(age / .3f) * PI.toFloat()) * .08f
            canvas.rotate(lean * 180 / PI.toFloat())
            val scale = (.62f + .38f * pop) * symbol / athlete.height
            canvas.scale(scale, scale)
            canvas.translate(0f, -athlete.centerY)
            paint.color = athlete.color
            paint.alpha = (clamp(age / .12f) * 255).toInt()
            paint.strokeWidth = 6f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
            paint.style = Paint.Style.FILL
            canvas.drawCircle(athlete.headX, athlete.headY, 5f, paint)
            paint.style = Paint.Style.STROKE
            athlete.paths.forEach { canvas.drawPath(it, paint) }
            paint.strokeWidth = 2f
            when (athlete.sport) {
                "basketball" -> canvas.drawCircle(-20f, 17f, 6f, paint)
                "volleyball" -> canvas.drawCircle(17f, -40f, 5f, paint)
                "soccer" -> canvas.drawCircle(28f, 18f, 5f, paint)
                "badminton" -> { canvas.drawOval(17f, -45f, 29f, -30f, paint); canvas.drawLine(17f, -29f, 23f, -32f, paint) }
                "tableTennis" -> { canvas.drawOval(16f, -27f, 27f, -16f, paint); canvas.drawLine(20f, -16f, 17f, -11f, paint) }
                "tennis" -> { canvas.drawOval(24f, -23f, 40f, -2f, paint); canvas.drawLine(20f, -12f, 25f, -13f, paint) }
                "baseball" -> { paint.strokeWidth = 4f; canvas.drawLine(20f, -16f, 4f, -43f, paint) }
            }
            canvas.restore()
        }
        paint.style = Paint.Style.FILL; paint.alpha = 255
    }

    private fun drawFold(canvas: Canvas, w: Float, h: Float, crease: Float) {
        val back = clipSplashPage(w, h, crease, false)
        val paper = polygon(back.map { SplashPoint(crease - it.y, crease - it.x) })
        val centerX = min(w, max(0f, crease / 2))
        val centerY = crease - centerX
        val depth = min(70f, max(1f, (w + h - crease) * .16f))
        paint.style = Paint.Style.FILL; paint.alpha = 255; paint.color = Color.WHITE
        paint.setShadowLayer(14f, 7f, 7f, Color.argb(51, 0, 0, 0))
        paint.shader = LinearGradient(centerX - depth, centerY - depth, centerX + 3, centerY + 3,
            intArrayOf(Color.rgb(245, 245, 245), Color.WHITE, Color.rgb(230, 230, 230), Color.rgb(179, 179, 179), Color.rgb(250, 250, 250)),
            floatArrayOf(0f, .42f, .78f, .94f, 1f), Shader.TileMode.CLAMP)
        canvas.drawPath(paper, paint)
        paint.clearShadowLayer(); paint.shader = null
        val edge = listOf(SplashPoint(crease, 0f), SplashPoint(w, crease - w), SplashPoint(crease - h, h), SplashPoint(0f, crease))
            .filter { it.x in 0f..w && it.y in 0f..h }.distinct()
        if (edge.size == 2) {
            val lip = Path().apply { moveTo(edge[0].x, edge[0].y); quadTo((edge[0].x + edge[1].x) / 2 - depth * .12f,
                (edge[0].y + edge[1].y) / 2 - depth * .12f, edge[1].x, edge[1].y) }
            paint.color = Color.argb(217, 255, 255, 255); paint.strokeWidth = 1.5f; paint.style = Paint.Style.STROKE
            canvas.drawPath(lip, paint)
        }
        paint.style = Paint.Style.FILL; paint.alpha = 255
    }

    private class Athlete(val angle: Float, val color: Int, torso: FloatArray, leg: FloatArray, arm: FloatArray, otherArm: FloatArray,
        val headX: Float, val headY: Float, val sport: String) {
        private val top = when (sport) {
            "volleyball" -> -45f
            "badminton" -> -46f
            "baseball" -> -45f
            "basketball" -> -32f
            else -> -29f
        }
        val height = 31f - top
        val centerY = (31f + top) / 2
        val paths = listOf(torso, leg, arm, otherArm).map { points -> Path().apply {
            moveTo(points[0], points[1]); for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
        } }
    }
}

private fun clamp(value: Float) = value.coerceIn(0f, 1f)
private fun ease(value: Float): Float { val p = clamp(value); return p * p * (3 - 2 * p) }
private fun spring(value: Float): Float { val p = clamp(value); return if (p == 1f) 1f else 1 - exp(-7 * p) * cos(11 * p) }
private fun polygon(points: List<SplashPoint>) = Path().apply {
    if (points.isNotEmpty()) { moveTo(points.first().x, points.first().y); points.drop(1).forEach { lineTo(it.x, it.y) }; close() }
}

// 通常文字は単発クリック、コロンは長押しの触覚を使う。強度は端末に依存する。
internal fun splashHapticConstant(letter: Char): Int =
    if (letter == ':') HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY
