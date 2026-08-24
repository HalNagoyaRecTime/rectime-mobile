package com.rectime.mobile.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PublicWebConfigTest {
    @Test
    fun resolvesPathAgainstProductionOrigin() {
        assertEquals(
            "https://recwatch.pages.dev/legal/terms.html",
            resolvePublicWebUrl(
                path = "/legal/terms.html",
                origin = productionWebOrigin,
            ),
        )
    }

    @Test
    fun acceptsTrailingSlashOnApprovedProductionOrigin() {
        assertEquals(
            "https://recwatch.pages.dev/legal/privacy.html",
            resolvePublicWebUrl(
                path = "/legal/privacy.html",
                origin = "$productionWebOrigin/",
            ),
        )
    }

    @Test
    fun rejectsNonProductionOrigins() {
        listOf(
            "",
            "http://recwatch.pages.dev",
            "https://localhost",
            "https://127.0.0.1",
            "https://example.com",
            "https://other.pages.dev",
            "https://placeholder.invalid",
            "https://pr-150.recwatch.pages.dev",
            "https://develop.recwatch.pages.dev",
            "https://staging.recwatch.pages.dev",
        ).forEach { origin ->
            assertNull(
                resolvePublicWebUrl(
                    path = "/legal/terms.html",
                    origin = origin,
                ),
                "Public web config must reject $origin",
            )
        }
    }

    @Test
    fun rejectsUnsafePaths() {
        listOf(
            "legal/terms.html",
            "//example.com/terms",
            "/legal/terms.html?token=value",
            "/legal/terms.html#section",
            "/legal\\terms.html",
        ).forEach { path ->
            assertNull(resolvePublicWebUrl(path = path))
        }
    }
}
