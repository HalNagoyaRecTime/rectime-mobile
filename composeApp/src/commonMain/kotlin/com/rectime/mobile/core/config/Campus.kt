package com.rectime.mobile.core.config

enum class Campus { NAGOYA, OSAKA, TOKYO }

internal fun campusApiBaseUrl(campus: Campus, environment: AppEnvironment, nagoyaUrl: String): String? =
    when (campus) {
        Campus.NAGOYA -> nagoyaUrl
        Campus.OSAKA -> when (environment) {
            AppEnvironment.Production -> "https://rectime-api.recreation-osaka.workers.dev"
            AppEnvironment.Staging -> "https://rectime-api-staging.recreation-osaka.workers.dev"
            AppEnvironment.Development -> null
        }
        Campus.TOKYO -> when (environment) {
            AppEnvironment.Production -> "https://rectime-api.recreation-tokyo.workers.dev"
            AppEnvironment.Staging -> "https://rectime-api-staging.recreation-tokyo.workers.dev"
            AppEnvironment.Development -> null
        }
    }

internal fun campusFromCode(code: String, environment: AppEnvironment, nagoyaUrl: String): Campus? =
    Campus.entries
        .firstOrNull { it.name == code }
        ?.takeIf { campusApiBaseUrl(it, environment, nagoyaUrl) != null }

object CurrentCampus {
    var campus: Campus = Campus.NAGOYA
        private set

    fun select(code: String): Boolean {
        campus = campusFromCode(code, appEnvironment, nagoyaApiBaseUrl) ?: return false
        return true
    }
}
