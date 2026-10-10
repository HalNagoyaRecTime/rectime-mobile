package com.rectime.mobile.feature.liveactivity

import androidx.compose.runtime.Composable
import com.rectime.mobile.feature.schedule.TimelineEvent

@Composable
internal actual fun ScheduleLiveActivityEffect(events: List<TimelineEvent>, accountId: String, ready: Boolean) = Unit
