package com.rectime.mobile.notification

import android.util.Log
import com.rectime.mobile.core.model.MockUser
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object FcmTokenRegistrar {
    private val executor = Executors.newSingleThreadExecutor()

    fun register(token: String) {
        val studentNumber = MockUser.me.studentId
        if (studentNumber.isNullOrBlank()) {
            Log.w(TAG, "FCM token registration skipped: studentNumber is missing")
            return
        }

        executor.execute {
            runCatching {
                postToken(studentNumber = studentNumber, token = token)
            }.onSuccess { responseCode ->
                Log.d(TAG, "FCM token registration completed: HTTP $responseCode")
            }.onFailure { error ->
                Log.w(TAG, "FCM token registration failed", error)
            }
        }
    }

    private fun postToken(studentNumber: String, token: String): Int {
        val body = """
            {
              "studentNumber": "${studentNumber.escapeJson()}",
              "platform": "android",
              "token": "${token.escapeJson()}"
            }
        """.trimIndent()

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            Log.d(TAG, "FCM token registration response: HTTP $responseCode $responseText")
            check(responseCode in 200..299) {
                "Unexpected FCM token registration response: HTTP $responseCode"
            }
            responseCode
        } finally {
            connection.disconnect()
        }
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
    private const val ENDPOINT = "https://rectime-api.rectime-project.workers.dev/api/v1/firebase-tokens"
    private const val TIMEOUT_MS = 10_000
}
