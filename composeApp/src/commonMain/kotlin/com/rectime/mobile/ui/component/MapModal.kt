package com.rectime.mobile.ui.component

import com.rectime.mobile.core.config.apiBaseUrl

internal fun venueMapImageUrl(baseUrl: String = apiBaseUrl): String =
    "${baseUrl.trimEnd('/')}/map/recmap.png"
