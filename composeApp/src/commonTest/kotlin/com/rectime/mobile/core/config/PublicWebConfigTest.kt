package com.rectime.mobile.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PublicWebConfigTest {
    @Test
    fun allowedWebOriginDependsOnBuildType() {
        assertEquals(productionWebOrigin, allowedWebOrigin(isDebug = false))
        assertEquals(developWebOrigin, allowedWebOrigin(isDebug = true))
    }

    @Test
    fun publicWebOriginMatchesCurrentBuildType() {
        assertEquals(allowedWebOrigin(isDebugBuild), publicWebOrigin)
    }

    @Test
    fun resolvesPathAgainstProductionOriginInRelease() {
        assertEquals(
            "https://recwatch.pages.dev/legal/terms.html",
            resolvePublicWebUrl(
                path = "/legal/terms.html",
                origin = productionWebOrigin,
                isDebug = false,
            ),
        )
    }

    @Test
    fun resolvesPathAgainstDevelopOriginInDebug() {
        assertEquals(
            "https://develop.recwatch.pages.dev/legal/terms.html",
            resolvePublicWebUrl(
                path = "/legal/terms.html",
                origin = developWebOrigin,
                isDebug = true,
            ),
        )
    }

    @Test
    fun resolvesAccountDeletionPathWithoutQueryOrFragment() {
        assertEquals(
            "https://recwatch.pages.dev/account-deletion",
            resolvePublicWebUrl(
                path = accountDeletionPath,
                origin = productionWebOrigin,
                isDebug = false,
            ),
        )
        assertEquals(
            "https://develop.recwatch.pages.dev/account-deletion",
            resolvePublicWebUrl(
                path = accountDeletionPath,
                origin = developWebOrigin,
                isDebug = true,
            ),
        )
    }

    @Test
    fun resolvesAccountDeletionPathWithDefaultOrigin() {
        assertEquals(
            "$publicWebOrigin/account-deletion",
            resolvePublicWebUrl(path = accountDeletionPath),
        )
    }

    @Test
    fun acceptsTrailingSlashOnApprovedOrigin() {
        assertEquals(
            "https://recwatch.pages.dev/legal/privacy.html",
            resolvePublicWebUrl(
                path = "/legal/privacy.html",
                origin = "$productionWebOrigin/",
                isDebug = false,
            ),
        )
        assertEquals(
            "https://develop.recwatch.pages.dev/legal/privacy.html",
            resolvePublicWebUrl(
                path = "/legal/privacy.html",
                origin = "$developWebOrigin/",
                isDebug = true,
            ),
        )
    }

    @Test
    fun releaseRejectsNonProductionOrigins() {
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
                    isDebug = false,
                ),
                "Release public web config must reject $origin",
            )
        }
    }

    @Test
    fun debugRejectsNonDevelopOrigins() {
        listOf(
            "",
            "http://develop.recwatch.pages.dev",
            "https://recwatch.pages.dev",
            "https://localhost",
            "https://127.0.0.1",
            "https://example.com",
            "https://other.pages.dev",
            "https://placeholder.invalid",
            "https://pr-150.recwatch.pages.dev",
            "https://develop.other.pages.dev",
            "https://staging.recwatch.pages.dev",
        ).forEach { origin ->
            assertNull(
                resolvePublicWebUrl(
                    path = "/legal/terms.html",
                    origin = origin,
                    isDebug = true,
                ),
                "Debug public web config must reject $origin",
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
            listOf(false, true).forEach { isDebug ->
                assertNull(
                    resolvePublicWebUrl(
                        path = path,
                        origin = allowedWebOrigin(isDebug),
                        isDebug = isDebug,
                    ),
                    "Public web config must reject $path (isDebug=$isDebug)",
                )
            }
        }
    }
}