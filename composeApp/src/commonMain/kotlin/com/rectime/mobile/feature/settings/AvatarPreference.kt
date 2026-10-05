package com.rectime.mobile.feature.settings

import com.rectime.mobile.ui.component.SportAvatarPictogram
import kotlinx.coroutines.CancellationException

// 写真データは保存せず、ユーザー別に選択名だけを端末設定へ保存する。
internal data class AvatarSelection(val sport: SportAvatarPictogram? = null, val photo: Boolean = false) {
    val isAutomatic: Boolean get() = sport == null && !photo
}

internal class AvatarPreference(private val userId: String) {
    suspend fun load(): AvatarSelection = try {
        val value = readAvatarPreference(userId)
        if (value == "photo") AvatarSelection(photo = true)
        else AvatarSelection(sport = SportAvatarPictogram.entries.firstOrNull { it.name == value })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("[Avatar] 選択の読み込みに失敗: ${e::class.simpleName}")
        AvatarSelection()
    }

    suspend fun save(selection: AvatarSelection) {
        try {
            writeAvatarPreference(userId, selection.sport?.name ?: if (selection.photo) "photo" else "auto")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 保存できない場合も、この画面での選択は維持する。
            println("[Avatar] 選択の保存に失敗: ${e::class.simpleName}")
        }
    }
}

internal expect suspend fun readAvatarPreference(userId: String): String?
internal expect suspend fun writeAvatarPreference(userId: String, value: String)
