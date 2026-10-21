package com.rectime.mobile.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PublicWebConfigTest {
    @Test
    fun allowedWebOriginDependsOnBuildType() {
        assertEquals(productionWebOrigin, allowedWebOrigin(AppEnvironment.Production))
        assertEquals(stagingWebOrigin, allowedWebOrigin(AppEnvironment.Staging))
        assertEquals(developWebOrigin, allowedWebOrigin(AppEnvironment.Development))
    }

    @Test
    fun publicWebOriginMatchesCurrentBuildType() {
        assertEquals(allowedWebOrigin(appEnvironment), publicWebOrigin)
    }

    @Test
    fun resolvesPathAgainstProductionOriginInRelease() {
        assertEquals(
            "https://recwatch.pages.dev/legal/terms.html",
            resolvePublicWebUrl(
                path = "/legal/terms.html",
                origin = productionWebOrigin,
                environment = AppEnvironment.Production,
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
                environment = AppEnvironment.Development,
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
                environment = AppEnvironment.Production,
            ),
        )
        assertEquals(
            "https://staging.recwatch.pages.dev/account-deletion",
            resolvePublicWebUrl(
                path = accountDeletionPath,
                origin = stagingWebOrigin,
                environment = AppEnvironment.Staging,
            ),
        )
        assertEquals(
            "https://develop.recwatch.pages.dev/account-deletion",
            resolvePublicWebUrl(
                path = accountDeletionPath,
                origin = developWebOrigin,
                environment = AppEnvironment.Development,
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
                environment = AppEnvironment.Production,
            ),
        )
        assertEquals(
            "https://develop.recwatch.pages.dev/legal/privacy.html",
            resolvePublicWebUrl(
                path = "/legal/privacy.html",
                origin = "$developWebOrigin/",
                environment = AppEnvironment.Development,
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
                    environment = AppEnvironment.Production,
                ),
                "Release public web config must reject $origin",
            )
        }
    }

    @Test
    fun stagingRejectsNonStagingOrigins() {
        listOf(
            "",
            "http://staging.recwatch.pages.dev",
            "https://recwatch.pages.dev",
            "https://develop.recwatch.pages.dev",
            "https://localhost",
            "https://example.com",
            "https://pr-150.recwatch.pages.dev",
            "https://staging.other.pages.dev",
        ).forEach { origin ->
            assertNull(
                resolvePublicWebUrl(
                    path = accountDeletionPath,
                    origin = origin,
                    environment = AppEnvironment.Staging,
                ),
                "Staging public web config must reject $origin",
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
                    environment = AppEnvironment.Development,
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
            AppEnvironment.entries.forEach { environment ->
                assertNull(
                    resolvePublicWebUrl(
                        path = path,
                        origin = allowedWebOrigin(environment),
                        environment = environment,
                    ),
                    "Public web config must reject $path ($environment)",
                )
            }
        }
    }
}