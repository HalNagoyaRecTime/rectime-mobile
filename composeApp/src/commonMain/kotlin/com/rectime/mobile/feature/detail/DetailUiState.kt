package com.rectime.mobile.feature.detail

import com.rectime.mobile.core.model.EventDetail

data class DetailUiState(
    val isLoading: Boolean = false,
    val eventDetail: EventDetail? = null,
    val error: String? = null,
)