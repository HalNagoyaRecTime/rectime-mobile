package com.rectime.mobile.core.network

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiErrorParserTest {
    @Test
    fun parsesCommonApiErrorResponse() {
        val error = apiErrorException(
            status = HttpStatusCode.BadRequest,
            body = """
                {
                  "error": {
                    "code": "VALIDATION_FAILED",
                    "message": "Invalid request",
                    "details": { "field": "name" }
                  }
                }
            """.trimIndent(),
        )

        assertEquals(HttpStatusCode.BadRequest, error.status)
        assertEquals("VALIDATION_FAILED", error.code)
        assertEquals("Invalid request", error.message)
        assertEquals(
            JsonObject(mapOf("field" to JsonPrimitive("name"))),
            error.details,
        )
    }

    @Test
    fun rejectsLegacyStringErrorResponse() {
        val error = apiErrorException(
            status = HttpStatusCode.Forbidden,
            body = """{"error":"Forbidden"}""",
            fallbackMessage = "Request failed",
        )

        assertEquals("UNKNOWN_API_ERROR", error.code)
        assertEquals("Request failed", error.message)
    }

    @Test
    fun preservesUnauthorizedStatusWhenLegacyResponseCannotBeParsed() {
        val error = apiErrorException(
            status = HttpStatusCode.Unauthorized,
            body = "{\"error\":\"Unauthorized\"}",
            fallbackMessage = "Request failed",
        )

        assertEquals("UNAUTHORIZED", error.code)
        assertEquals("Request failed", error.message)
    }

    @Test
    fun preservesNotFoundStatusWhenErrorResponseIsMalformed() {
        val error = apiErrorException(
            status = HttpStatusCode.NotFound,
            body = "not json",
        )

        assertEquals("NOT_FOUND", error.code)
        assertEquals("HTTP 404", error.message)
    }

    @Test
    fun rejectsIncompleteCommonErrorResponse() {
        val error = apiErrorException(
            status = HttpStatusCode.InternalServerError,
            body = """{"error":{"message":"Missing code"}}""",
        )

        assertEquals("UNKNOWN_API_ERROR", error.code)
        assertEquals("HTTP 500", error.message)
    }
}
