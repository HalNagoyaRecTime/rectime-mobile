package com.rectime.mobile.feature.settings

import com.rectime.mobile.core.cache.CacheGeneration
import com.rectime.mobile.core.cache.KeyValueStore
import com.rectime.mobile.core.cache.PlatformKeyValueStore
import com.rectime.mobile.ui.component.AvatarBackgrounds
import com.rectime.mobile.ui.component.SportAvatarPictogram
import kotlinx.coroutines.CancellationException

// 写真データは保存せず、競技と色の選択だけをアカウントキャッシュに保存する。
internal data class AvatarSelection(
    val sport: SportAvatarPictogram? = null,
    val photo: Boolean = false,
    val colorIndex: Int? = null,
) {
    val isAutomatic: Boolean get() = sport == null && !photo
}

internal class AvatarPreference(
    private val userId: String,
    private val store: KeyValueStore = PlatformKeyValueStore(),
) {
    private val generation = CacheGeneration.value
    private val key = "avatar_selection_v2_$userId"

    suspend fun load(): AvatarSelection = try {
        val value = store.getString(key)
        if (generation != CacheGeneration.value) AvatarSelection()
        else if (value == "photo") AvatarSelection(photo = true)
        else {
            val parts = value?.split(':').orEmpty()
            val sport = SportAvatarPictogram.entries.firstOrNull { it.name == parts.firstOrNull() }
            AvatarSelection(sport = sport, colorIndex = if (sport == null) null else
                parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in AvatarBackgrounds.indices })
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("[Avatar] 選択の読み込みに失敗: ${e::class.simpleName}")
        AvatarSelection()
    }

    suspend fun save(selection: AvatarSelection) {
        // ログアウト後に残った操作で削除済みの選択を復活させない。
        if (generation != CacheGeneration.value) return
        try {
            val value = selection.sport?.let { "${it.name}:${selection.colorIndex ?: -1}" }
                ?: if (selection.photo) "photo" else "auto"
            store.putString(key, value)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 保存できない場合も、この画面での選択は維持する。
            println("[Avatar] 選択の保存に失敗: ${e::class.simpleName}")
        }
    }
}
