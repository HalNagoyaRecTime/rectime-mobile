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

/** Public entry point for starting the RecTime account deletion process. */
const val accountDeletionPath: String = "/account-deletion"

/** Web origin for the current build type: develop for debug, production for release. */
val publicWebOrigin: String
    get() = allowedWebOrigin(isDebugBuild)

internal fun allowedWebOrigin(isDebug: Boolean): String =
    if (isDebug) developWebOrigin else productionWebOrigin

private val httpsOriginPattern = Regex(
    pattern = "^https://([A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?)(?::[1-9][0-9]{0,4})?/?$",
)

/**
 * Resolves a path against the public web origin.
 *
 * Release builds accept only the approved production origin. Debug builds accept
 * only the develop origin. Preview, staging, localhost, example and placeholder
 * origins are rejected in every build.
 */
fun resolvePublicWebUrl(
    path: String,
    origin: String = publicWebOrigin,
    isDebug: Boolean = isDebugBuild,
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
        (!isDebug && host.startsWith("develop.")) ||
        host.startsWith("development.") ||
        host.startsWith("staging.")
    if (forbiddenHost || normalizedOrigin != allowedWebOrigin(isDebug)) return null

    return normalizedOrigin + path
}
