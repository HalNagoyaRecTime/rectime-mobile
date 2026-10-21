package com.rectime.mobile.core.config

/**
 * Public production origin shared by legal documents and account deletion.
 *
 * This value is public application configuration, not a secret. Change it only
 * after the production domain has been approved and deployed.
 */
const val productionWebOrigin: String = "https://recwatch.pages.dev"

/**
 * Public develop origin used only by debug builds so that pages deployed to the
 * develop environment (e.g. account deletion) can be verified from the app.
 */
const val developWebOrigin: String = "https://develop.recwatch.pages.dev"

/** Public staging origin used only by staging builds, paired with the staging API. */
const val stagingWebOrigin: String = "https://staging.recwatch.pages.dev"

/** Public entry point for starting the RE:CREATION account deletion process. */
const val accountDeletionPath: String = "/account-deletion"

/** Web origin for the current build type, paired with the API the build connects to. */
val publicWebOrigin: String
    get() = allowedWebOrigin(appEnvironment)

internal fun allowedWebOrigin(environment: AppEnvironment): String =
    when (environment) {
        AppEnvironment.Development -> developWebOrigin
        AppEnvironment.Staging -> stagingWebOrigin
        AppEnvironment.Production -> productionWebOrigin
    }

private val httpsOriginPattern = Regex(
    pattern = "^https://([A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?)(?::[1-9][0-9]{0,4})?/?$",
)

/**
 * Resolves a path against the public web origin.
 *
 * Each build accepts only the origin of its own environment. Preview, localhost,
 * example and placeholder origins are rejected in every build.
 */
fun resolvePublicWebUrl(
    path: String,
    origin: String = publicWebOrigin,
    environment: AppEnvironment = appEnvironment,
): String? {
    if (!path.startsWith('/') || path.startsWith("//")) return null
    if ('?' in path || '#' in path || '\\' in path) return null
    val originMatch = httpsOriginPattern.matchEntire(origin) ?: return null

    val normalizedOrigin = origin.trimEnd('/')
    val host = originMatch.groupValues[1].lowercase()
    val forbiddenHost = host == "localhost" ||
        host == "127.0.0.1" ||
        host.endsWith(".invalid") ||
        "placeholder" in host ||
        host == "example.com" ||
        host.endsWith(".example.com") ||
        host.startsWith("pr-") ||
        host.startsWith("preview.") ||
        (environment != AppEnvironment.Development && host.startsWith("develop.")) ||
        host.startsWith("development.") ||
        (environment != AppEnvironment.Staging && host.startsWith("staging."))
    if (forbiddenHost || normalizedOrigin != allowedWebOrigin(environment)) return null

    return normalizedOrigin + path
}
