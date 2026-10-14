package com.rectime.mobile.feature.notifications

import com.rectime.mobile.core.cache.CacheGeneration
import com.rectime.mobile.core.cache.LocalCache

/** 一覧・追加ページ・詳細・削除で同じ取得順を使う。完了順で古い本文を採用しない。 */
internal class NotificationUpdateOrder(cache: LocalCache) {
    private val key = cache.requests
    private fun state(): UpdateState = states.getOrPut(key) { UpdateState(CacheGeneration.value) }.let {
        if (it.generation == CacheGeneration.value) it
        else UpdateState(CacheGeneration.value).also { fresh -> states[key] = fresh }
    }
    fun beginRequest(): Long = ++state().revision
    fun remove(id: Int, request: Long = beginRequest()): Boolean {
        if ((state().writes[id]?.revision ?: 0L) > request) return false
        state().writes[id] = Write(request, null)
        return true
    }
    fun newerValue(id: Int, request: Long): UserNotification? =
        state().writes[id]?.takeIf { it.revision > request }?.value
    fun acceptDetail(value: UserNotification, request: Long): UserNotification? {
        val saved = state().writes[value.id]
        if (saved != null && saved.revision > request) return saved.value
        state().writes[value.id] = Write(request, value)
        return value
    }
    fun acceptList(values: List<UserNotification>, request: Long): List<UserNotification> =
        values.mapNotNull { acceptDetail(it, request) }
    fun reconcile(values: List<UserNotification>): List<UserNotification> = values.mapNotNull {
        val saved = state().writes[it.id]
        if (saved == null) it else saved.value
    }
    private data class Write(val revision: Long, val value: UserNotification?)
    private class UpdateState(val generation: Int) {
        var revision = 0L
        val writes = mutableMapOf<Int, Write>()
    }
    companion object { private val states = mutableMapOf<Any, UpdateState>() }
}
