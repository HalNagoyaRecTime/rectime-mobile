package com.rectime.mobile.feature.event

import com.rectime.mobile.core.model.Gathering

/** 登録されたラウンド番号をそのまま使い、飛び番を補完しない。 */
internal fun List<Gathering>.byRound(): Map<Int, List<Gathering>> =
    sortedWith(compareBy({ it.round }, { it.gatheringTime }, { it.gatheringId })).groupBy { it.round }
