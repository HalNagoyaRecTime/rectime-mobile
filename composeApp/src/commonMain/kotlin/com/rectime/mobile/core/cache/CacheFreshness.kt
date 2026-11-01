package com.rectime.mobile.core.cache

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** 取得のきっかけ。保存値を再利用できる期間はきっかけごとに変わる。 */
internal enum class FetchTrigger {
    /** 保存値の鮮度にかかわらず必ず取得する。 */
    Required,

    /** 画面表示・前面復帰など、ユーザーが更新を求めていない取得。 */
    Automatic,

    /** 引っ張って更新などの手動更新。連打で通信しないようクールダウンだけ設ける。 */
    Manual,
}

/**
 * Workersの無料枠(1日10万回)を当日の利用者で使い切らないよう、データごとに保存値の再利用期間を決める。
 * 端末側で通信を省かない限り回数は減らないため、API側のキャッシュでは代替できない。
 */
internal data class CacheFreshness(
    val reuseFor: Duration,
    val manualCooldown: Duration,
) {
    companion object {
        /** 一覧と詳細の表示が食い違わないよう、/events と /events/{id} は同じ期間にする。 */
        val Events = CacheFreshness(reuseFor = 3.minutes, manualCooldown = 30.seconds)

        /** 本人の出場は欠席・異常事態でしか変わらないため長めに持つ。 */
        val Participation = CacheFreshness(reuseFor = 30.minutes, manualCooldown = 1.minutes)

        /** 終了時刻のずれや点数の修正も、最大でこの期間内に反映される。 */
        val Ranking = CacheFreshness(reuseFor = 5.minutes, manualCooldown = 1.minutes)
    }
}

internal fun FetchedCache<*>?.canReuse(trigger: FetchTrigger, freshness: CacheFreshness, clock: Clock): Boolean {
    val window = when (trigger) {
        FetchTrigger.Required -> return false
        FetchTrigger.Automatic -> freshness.reuseFor
        FetchTrigger.Manual -> freshness.manualCooldown
    }
    val fetched = this?.fetchedAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return false
    val age = clock.now() - fetched
    // 時刻不明・端末時計の巻き戻りでは鮮度を断定しない。
    return age >= Duration.ZERO && age < window
}
