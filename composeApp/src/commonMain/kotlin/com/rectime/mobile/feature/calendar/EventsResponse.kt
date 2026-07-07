package com.rectime.mobile.feature.calendar

import io.ktor.events.Events
import kotlinx.serialization.Serializable

//return c.json({
//        events: result.events,
//        total: result.total,
//        limit: limit ? parseInt(limit) : 50,
//        offset: offset ? parseInt(offset) : 0,
//      });
@Serializable
data class EventsResponse(
    val events: List<EventEntity>,
    val total: Int,
    val limit: Int,
    val offset: Int
)

//export interface EventEntity {
//  f_event_id: number;
//  f_event_code: string;
//  f_event_name: string;
//  f_time: string; // 「0930」などHHMM文字列
//  f_duration: string; // 「20」等分単位文字列
//  f_place: string;
//  f_gather_time: string;
//  f_summary: string | null;
//}
@Serializable
data class EventEntity(
    val f_event_id: Int,
    val f_event_code: String,
    val f_event_name: String,
    val f_time: String,
    val f_duration: String,
    val f_place: String,
    val f_gather_time: String,
    val f_summary: String
)