package com.rectime.mobile.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals

class MapModalTest {
    @Test
    fun buildsMapImageUrlFromBaseUrl() {
        assertEquals(
            "https://api.example.com/map/recmap.png",
            venueMapImageUrl(baseUrl = "https://api.example.com"),
        )
    }

    @Test
    fun buildsMapImageUrlWithoutDuplicatingSlash() {
        assertEquals(
            "https://api.example.com/map/recmap.png",
            venueMapImageUrl(baseUrl = "https://api.example.com/"),
        )
    }

    @Test
    fun keepsClassMeetingPointSeparateFromFacilityMaps() {
        val sections = venueMapSections(baseUrl = "https://api.example.com")

        assertEquals(2, sections.size)
        assertEquals(VenueMapSectionId.ClassMeetingPoint, sections[0].id)
        assertEquals(VenueMapSectionId.Facility, sections[1].id)
        assertEquals(1, sections[0].images.size)
        assertEquals(2, sections[1].images.size)
        assertEquals(
            "https://api.example.com/map/recmap.png",
            sections[0].images.single().remoteUrl,
        )
        assertEquals(null, sections[1].images[0].remoteUrl)
        assertEquals(null, sections[1].images[1].remoteUrl)
    }

    @Test
    fun usesTappedFacilityFloorAsViewerInitialIndex() {
        val facilityMaps = venueMapSections().single { it.id == VenueMapSectionId.Facility }.images

        assertEquals(
            0,
            initialIndexFor(facilityMaps, VenueMapImageId.FacilityFirstFloor),
        )
        assertEquals(
            1,
            initialIndexFor(facilityMaps, VenueMapImageId.FacilitySecondFloor),
        )
    }
}
