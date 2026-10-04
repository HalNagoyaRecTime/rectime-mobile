package com.rectime.mobile.feature.splash

/** 起動演出の時刻と文字の出現を管理する。通信の完了には依存しない。 */
internal class SplashPlayback {
    var elapsedMillis = 0L
        private set
    var finished = false
        private set
    val appearances = mutableListOf<Long>()
    private var previousFrame: Long? = null

    fun setActive(active: Boolean) {
        // 権限ダイアログや停止中の時間を次のフレームへ加算しない。
        previousFrame = null
        isActive = active && !finished
    }

    var isActive = false
        private set

    fun frame(nowMillis: Long): List<Int> {
        if (!isActive || finished) return emptyList()
        previousFrame?.let { elapsedMillis += (nowMillis - it).coerceAtLeast(0) }
        previousFrame = nowMillis
        if (elapsedMillis >= DurationMillis) {
            finish()
            return emptyList()
        }
        val scheduled = LetterStartMillis + appearances.size * LetterIntervalMillis
        val nextAppearance = maxOf(scheduled, appearances.lastOrNull()?.plus(LetterIntervalMillis) ?: scheduled)
        // フレーム停止後も文字と振動をまとめて再生せず、一文字ずつ出す。
        if (appearances.size < Title.length && elapsedMillis >= nextAppearance) {
            val index = appearances.size
            appearances += elapsedMillis
            return listOf(index)
        }
        return emptyList()
    }

    fun finish() {
        finished = true
        isActive = false
        previousFrame = null
    }

    companion object {
        const val Title = "RE:CREATION"
        const val LetterStartMillis = 700L
        const val LetterIntervalMillis = 75L
        const val TurnStartMillis = 2000L
        const val DurationMillis = 2750L
    }
}

internal data class SplashPoint(val x: Float, val y: Float)

/** 折り目 x+y=crease で紙の表／裏の多角形を切り出す。 */
internal fun clipSplashPage(width: Float, height: Float, crease: Float, front: Boolean): List<SplashPoint> {
    val corners = listOf(SplashPoint(0f, 0f), SplashPoint(width, 0f), SplashPoint(width, height), SplashPoint(0f, height))
    return buildList {
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            val da = a.x + a.y - crease
            val db = b.x + b.y - crease
            val insideA = if (front) da <= 0 else da >= 0
            val insideB = if (front) db <= 0 else db >= 0
            if (insideA) add(a)
            if (insideA != insideB) {
                val fraction = da / (da - db)
                add(SplashPoint(a.x + (b.x - a.x) * fraction, a.y + (b.y - a.y) * fraction))
            }
        }
    }
}
