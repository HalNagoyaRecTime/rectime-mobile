package com.rectime.mobile.notification

import android.util.Log
import com.rectime.mobile.BuildConfig
import com.rectime.mobile.core.model.MockUser
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object FcmTokenRegistrar {
    private val client = HttpClient(OkHttp)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun register(token: String) {
        val studentNumber = MockUser.me.studentId
        if (studentNumber.isNullOrBlank()) {
            Log.w(TAG, "FCM token registration skipped: studentNumber is missing")
            return
        }

        scope.launch {
            runCatching {
                postToken(studentNumber = studentNumber, token = token)
            }.onSuccess { responseCode ->
                Log.d(TAG, "FCM token registration completed: HTTP $responseCode")
            }.onFailure { error ->
                Log.w(TAG, "FCM token registration failed", error)
            }
        }
    }

    private suspend fun postToken(studentNumber: String, token: String): Int {
        val body = """
            {
              "studentNumber": "${studentNumber.escapeJson()}",
              "platform": "android",
              "token": "${token.escapeJson()}"
            }
        """.trimIndent()

        val response = client.post(ENDPOINT) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val responseCode = response.status.value
        val responseText = response.bodyAsText()

        Log.d(TAG, "FCM token registration response: HTTP $responseCode $responseText")
        check(responseCode in 200..299) {
            "Unexpected FCM token registration response: HTTP $responseCode"
        }
        return responseCode
    }

    private fun String.escapeJson(): String = buildString {
        this@escapeJson.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

    private const val TAG = "RectimeFCM"
    private val ENDPOINT = "${BuildConfig.API_BASE_URL}/api/v1/firebase-tokens"
}
