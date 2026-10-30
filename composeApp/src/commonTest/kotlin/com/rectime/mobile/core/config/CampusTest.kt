package com.rectime.mobile.core.config

import com.rectime.mobile.feature.auth.SessionTokenHolder
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CampusTest {
    @AfterTest
    fun clearToken() {
        SessionTokenHolder.accessToken = null
    }

    @Test
    fun nagoyaUsesTheBuildConfiguredApiInEveryEnvironment() {
        for (environment in AppEnvironment.entries) {
            assertEquals(NAGOYA_URL, campusApiBaseUrl(Campus.NAGOYA, environment, NAGOYA_URL))
        }
    }

    @Test
    fun osakaAndTokyoUseTheirOwnApiInProductionAndStaging() {
        assertEquals(
            "https://rectime-api.recreation-osaka.workers.dev",
            campusApiBaseUrl(Campus.OSAKA, AppEnvironment.Production, NAGOYA_URL),
        )
        assertEquals(
            "https://rectime-api-staging.recreation-osaka.workers.dev",
            campusApiBaseUrl(Campus.OSAKA, AppEnvironment.Staging, NAGOYA_URL),
        )
        assertEquals(
            "https://rectime-api.recreation-tokyo.workers.dev",
            campusApiBaseUrl(Campus.TOKYO, AppEnvironment.Production, NAGOYA_URL),
        )
        assertEquals(
            "https://rectime-api-staging.recreation-tokyo.workers.dev",
            campusApiBaseUrl(Campus.TOKYO, AppEnvironment.Staging, NAGOYA_URL),
        )
    }

    @Test
    fun developmentHasOnlyNagoya() {
        assertEquals(Campus.NAGOYA, campusFromCode("NAGOYA", AppEnvironment.Development, NAGOYA_URL))
        assertNull(campusFromCode("OSAKA", AppEnvironment.Development, NAGOYA_URL))
        assertNull(campusFromCode("TOKYO", AppEnvironment.Development, NAGOYA_URL))
    }

    @Test
    fun rejectsCodesNotInTheTable() {
        for (code in listOf("", "Osaka", "osaka", "KYOTO")) {
            assertNull(campusFromCode(code, AppEnvironment.Production, NAGOYA_URL))
        }
    }

    @Test
    fun keepsTheCurrentCampusAndTokenWhenTheCodeIsRejected() {
        val before = CurrentCampus.campus
        SessionTokenHolder.accessToken = "session-token"
        assertEquals(false, CurrentCampus.select("KYOTO"))
        assertEquals(before, CurrentCampus.campus)
        assertEquals("session-token", SessionTokenHolder.accessToken)
    }

    @Test
    fun keepsTheTokenWhenTheSameCampusIsSelected() {
        SessionTokenHolder.accessToken = "session-token"
        assertEquals(true, CurrentCampus.select(CurrentCampus.campus.name, AppEnvironment.Production, NAGOYA_URL))
        assertEquals("session-token", SessionTokenHolder.accessToken)
    }

    private companion object {
        const val NAGOYA_URL = "https://nagoya.example.test"
    }
}
