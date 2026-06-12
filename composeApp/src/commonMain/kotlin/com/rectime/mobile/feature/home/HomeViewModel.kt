package com.rectime.mobile.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rectime.mobile.core.config.apiBaseUrl
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        fetchResponse()
    }

    private fun fetchResponse() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val client = HttpClient()
                val text = client.get(apiBaseUrl).bodyAsText()
                client.close()
                _uiState.update { it.copy(isLoading = false, response = text) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, response = "エラー: ${e.message}") }
            }
        }
    }
}
