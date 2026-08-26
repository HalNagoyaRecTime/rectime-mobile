package com.rectime.mobile.core.config

/**
 * Public production origin shared by legal documents and account deletion.
 *
 * This value is public application configuration, not a secret. Change it only
 * after the production domain has been approved and deployed.
 */
const val productionWebOrigin: String = "https://recwatch.pages.dev"

/** Public entry point for starting the RecTime account deletion process. */
const val accountDeletionPath: String = "/account-deletion"

private val httpsOriginPattern = Regex(
    pattern = "^https://([A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?)(?::[1-9][0-9]{0,4})?/?$",
)

/**
 * Resolves a path against the public web origin.
 *
 * Every build accepts only the single approved production origin, preventing
 * preview, staging, localhost, example and placeholder origins from being used.
 */
fun resolvePublicWebUrl(
    path: String,
    origin: String = productionWebOrigin,
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
        host.startsWith("develop.") ||
        host.startsWith("development.") ||
        host.startsWith("staging.")
    if (forbiddenHost || normalizedOrigin != productionWebOrigin) return null

    return normalizedOrigin + path
}
