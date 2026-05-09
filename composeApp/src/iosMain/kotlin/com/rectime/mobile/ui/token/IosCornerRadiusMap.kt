package com.rectime.mobile.ui.token

// Device identifier → corner radius (dp) mapping for iOS 25 and below.
// Sources: official teardown measurements and community-verified values.
// These are "display corner radius" values (the visible screen corner radius).
internal val iosCornerRadiusMap: Map<String, Float> = mapOf(
    // iPhone SE (1st gen) — flat corners
    "iPhone8,4" to 0f,

    // iPhone SE (2nd gen)
    "iPhone12,8" to 0f,

    // iPhone SE (3rd gen)
    "iPhone14,6" to 0f,

    // iPhone X
    "iPhone10,3" to 39f,
    "iPhone10,6" to 39f,

    // iPhone XS
    "iPhone11,2" to 39f,

    // iPhone XS Max
    "iPhone11,4" to 39f,
    "iPhone11,6" to 39f,

    // iPhone XR
    "iPhone11,8" to 41.5f,

    // iPhone 11
    "iPhone12,1" to 41.5f,

    // iPhone 11 Pro
    "iPhone12,3" to 39f,

    // iPhone 11 Pro Max
    "iPhone12,5" to 39f,

    // iPhone 12 mini
    "iPhone13,1" to 44f,

    // iPhone 12
    "iPhone13,2" to 47f,

    // iPhone 12 Pro
    "iPhone13,3" to 47f,

    // iPhone 12 Pro Max
    "iPhone13,4" to 53f,

    // iPhone 13 mini
    "iPhone14,4" to 44f,

    // iPhone 13
    "iPhone14,5" to 47.33f,

    // iPhone 13 Pro
    "iPhone14,2" to 47.33f,

    // iPhone 13 Pro Max
    "iPhone14,3" to 53.33f,

    // iPhone 14
    "iPhone14,7" to 47.33f,

    // iPhone 14 Plus
    "iPhone14,8" to 53.33f,

    // iPhone 14 Pro
    "iPhone15,2" to 55f,

    // iPhone 14 Pro Max
    "iPhone15,3" to 55f,

    // iPhone 15
    "iPhone15,4" to 47.33f,

    // iPhone 15 Plus
    "iPhone15,5" to 53.33f,

    // iPhone 15 Pro
    "iPhone16,1" to 55f,

    // iPhone 15 Pro Max
    "iPhone16,2" to 55f,

    // iPhone 16
    "iPhone17,3" to 50f,

    // iPhone 16 Plus
    "iPhone17,4" to 55f,

    // iPhone 16 Pro
    "iPhone17,1" to 62f,

    // iPhone 16 Pro Max
    "iPhone17,2" to 62f,

    // iPad Pro 11-inch (1st–4th gen)
    "iPad8,1" to 18f,
    "iPad8,2" to 18f,
    "iPad8,3" to 18f,
    "iPad8,4" to 18f,
    "iPad13,4" to 18f,
    "iPad13,5" to 18f,
    "iPad13,6" to 18f,
    "iPad13,7" to 18f,
    "iPad14,3" to 18f,
    "iPad14,4" to 18f,
    "iPad16,3" to 18f,
    "iPad16,4" to 18f,

    // iPad Pro 12.9-inch (3rd–6th gen)
    "iPad8,5" to 18f,
    "iPad8,6" to 18f,
    "iPad8,7" to 18f,
    "iPad8,8" to 18f,
    "iPad8,11" to 18f,
    "iPad8,12" to 18f,
    "iPad13,8" to 18f,
    "iPad13,9" to 18f,
    "iPad13,10" to 18f,
    "iPad13,11" to 18f,
    "iPad14,5" to 18f,
    "iPad14,6" to 18f,

    // iPad mini (6th gen)
    "iPad14,1" to 19.5f,
    "iPad14,2" to 19.5f,

    // iPad mini (7th gen)
    "iPad16,1" to 19.5f,
    "iPad16,2" to 19.5f,

    // iPad Air (4th gen)
    "iPad13,1" to 18f,
    "iPad13,2" to 18f,

    // iPad Air (5th gen)
    "iPad13,16" to 18f,
    "iPad13,17" to 18f,

    // iPad Air 11-inch M2
    "iPad14,8" to 18f,
    "iPad14,9" to 18f,

    // iPad Air 13-inch M2
    "iPad14,10" to 18f,
    "iPad14,11" to 18f,
)
