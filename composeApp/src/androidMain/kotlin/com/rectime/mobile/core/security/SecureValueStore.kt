package com.rectime.mobile.core.security

internal interface StringStore {
    fun get(key: String): String?
    fun put(key: String, value: String): Boolean
    fun remove(key: String): Boolean
    fun clearAll()
}

internal interface ValueCipher {
    fun encrypt(plaintext: String): String?
    fun decrypt(value: String): String?
    fun discardKey()
}

internal class SecureValueStore(
    private val store: StringStore,
    private val cipher: ValueCipher,
) {
    fun read(key: String, legacyKey: String): String? {
        store.get(key)?.let { stored ->
            val plaintext = cipher.decrypt(stored)
            if (plaintext == null) {
                discardAll()
                return null
            }
            store.remove(legacyKey)
            return plaintext
        }

        val legacy = store.get(legacyKey) ?: return null
        if (!migrate(key, legacyKey, legacy)) {
            discardAll()
            return null
        }
        return legacy
    }

    fun write(key: String, legacyKey: String, plaintext: String) {
        val encrypted = cipher.encrypt(plaintext)
        if (encrypted == null || !store.put(key, encrypted)) {
            discardAll()
            return
        }
        store.remove(legacyKey)
    }

    // 消せたことを読み直して確認する。commitの失敗を握り潰すと、
    // ログアウトしたのに端末へSessionが残り、再起動で前のアカウントへ戻ってしまう。
    fun remove(key: String, legacyKey: String): Boolean {
        val removed = store.remove(key) and store.remove(legacyKey)
        if (removed && isAbsent(key, legacyKey)) return true

        store.clearAll()
        return isAbsent(key, legacyKey)
    }

    private fun isAbsent(key: String, legacyKey: String): Boolean =
        store.get(key) == null && store.get(legacyKey) == null

    private fun migrate(key: String, legacyKey: String, plaintext: String): Boolean {
        val encrypted = cipher.encrypt(plaintext) ?: return false
        if (!store.put(key, encrypted)) return false
        val stored = store.get(key) ?: return false
        if (cipher.decrypt(stored) != plaintext) return false
        return store.remove(legacyKey)
    }

    private fun discardAll() {
        store.clearAll()
        cipher.discardKey()
    }
}
