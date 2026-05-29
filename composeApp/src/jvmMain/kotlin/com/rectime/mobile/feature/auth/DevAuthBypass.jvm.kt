package com.rectime.mobile.feature.auth

private const val BYPASS_KEY = "DEV_BYPASS_AUTH"

actual fun isDevAuthBypassEnabled(): Boolean {
    val env = System.getenv(BYPASS_KEY)
    val prop = System.getProperty(BYPASS_KEY)
    val value = env ?: prop ?: return false
    return value.equals("true", ignoreCase = true) || value == "1"
}
