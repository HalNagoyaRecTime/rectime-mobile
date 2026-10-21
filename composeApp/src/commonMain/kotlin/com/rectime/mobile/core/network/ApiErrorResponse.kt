package com.rectime.mobile.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class ApiErrorResponse(
    val error: ApiErrorDetail,
)

@Serializable
internal data class ApiErrorDetail(
    val code: String,
    val message: String,
    val details: JsonElement? = null,
)
