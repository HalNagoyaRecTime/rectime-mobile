package com.rectime.mobile.core.network

import io.ktor.http.HttpStatusCode

class HttpStatusException(val status: HttpStatusCode) : Exception("HTTP ${status.value}")
