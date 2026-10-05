package com.rectime.mobile.feature.liveactivity

import androidx.compose.runtime.Composable
import com.rectime.mobile.feature.schedule.TimelineEvent

/** 一覧と同じ予定・出場情報をOSへ渡す。専用のAPI取得は行わない。 */
@Composable
internal expect fun ScheduleLiveActivityEffect(events: List<TimelineEvent>, accountId: String, ready: Boolean)
