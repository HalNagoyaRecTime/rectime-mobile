package com.rectime.mobile.feature.event

import com.rectime.mobile.core.model.Gathering

/** ラウンド番号・集合番号の順で並べ、飛び番を補完しない。 */
internal fun List<Gathering>.byRound(): Map<Int, List<Gathering>> =
    sortedWith(compareBy({ it.round }, { it.gatheringId })).groupBy { it.round }
