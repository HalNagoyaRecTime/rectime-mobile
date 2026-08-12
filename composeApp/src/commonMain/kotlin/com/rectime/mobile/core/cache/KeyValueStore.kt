package com.rectime.mobile.core.cache

interface KeyValueStore {
    suspend fun getString(key: String): String?
    suspend fun putString(key: String, value: String)
}

expect class PlatformKeyValueStore() : KeyValueStore
