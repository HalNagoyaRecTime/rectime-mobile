package com.rectime.mobile.core.security

internal interface StringStore {
    fun get(key: String): String?
    fun put(key: String, value: String): Boolean
    fun remove(key: String): Boolean
    fun clearAll(): Boolean
}

internal interface ValueCipher {
    fun encrypt(plaintext: String): String?
    fun decrypt(value: String): String?
    fun discardKey()
}

// 削除できたかどうかは書き込みAPIの戻り値だけで判断する。SharedPreferencesは
// 永続化より先にメモリ上の値を消すため、消した直後に読み直しても、端末に
// 残っていることを検出できない。
internal class SecureValueStore(
    private val store: StringStore,
    private val cipher: ValueCipher,
) {
    fun read(key: String, legacyKey: String): String? {
        store.get(key)?.let { stored ->
            val plaintext = cipher.decrypt(stored)
            if (plaintext == null || !store.remove(legacyKey)) {
                discardAll()
                return null
            }
            return plaintext
        }

        val legacy = store.get(legacyKey) ?: return null
        if (!migrate(key, legacyKey, legacy)) {
            discardAll()
            return null
        }
        return legacy
    }

    fun write(key: String, legacyKey: String, plaintext: String): Boolean {
        val encrypted = cipher.encrypt(plaintext)
        if (encrypted == null || !store.put(key, encrypted) || !store.remove(legacyKey)) {
            discardAll()
            return false
        }
        return true
    }

    fun remove(key: String, legacyKey: String): Boolean {
        if (store.remove(key) and store.remove(legacyKey)) return true
        return store.clearAll()
    }

    private fun migrate(key: String, legacyKey: String, plaintext: String): Boolean {
        val encrypted = cipher.encrypt(plaintext) ?: return false
        if (!store.put(key, encrypted)) return false
        val stored = store.get(key) ?: return false
        if (cipher.decrypt(stored) != plaintext) return false
        return store.remove(legacyKey)
    }

    private fun discardAll(): Boolean {
        val cleared = store.clearAll()
        cipher.discardKey()
        return cleared
    }
}
