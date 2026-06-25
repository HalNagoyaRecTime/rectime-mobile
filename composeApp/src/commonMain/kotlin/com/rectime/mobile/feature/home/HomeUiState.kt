package com.rectime.mobile.feature.home

data class HomeUiState(
    val isLoading: Boolean = false,
    val isHealthy: Boolean? = null,
    val error: String? = null,
)
