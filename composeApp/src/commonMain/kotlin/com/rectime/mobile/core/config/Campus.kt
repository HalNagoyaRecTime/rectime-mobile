package com.rectime.mobile.core.config

import com.rectime.mobile.feature.auth.SessionTokenHolder

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

/** 現在の環境でこの学校のAPIが無ければnull */
internal fun Campus.apiBaseUrlOrNull(): String? = campusApiBaseUrl(this, appEnvironment, nagoyaApiBaseUrl)

internal fun campusFromCode(code: String, environment: AppEnvironment, nagoyaUrl: String): Campus? =
    Campus.entries
        .firstOrNull { it.name == code }
        ?.takeIf { campusApiBaseUrl(it, environment, nagoyaUrl) != null }

object CurrentCampus {
    var campus: Campus = Campus.NAGOYA
        private set

    internal fun select(
        code: String,
        environment: AppEnvironment = appEnvironment,
        nagoyaUrl: String = nagoyaApiBaseUrl,
    ): Boolean {
        val selected = campusFromCode(code, environment, nagoyaUrl) ?: return false
        if (selected != campus) SessionTokenHolder.accessToken = null
        campus = selected
        return true
    }
}
