package com.rectime.mobile.feature.auth

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryCreateMutableCopy
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

internal interface SecureStringStore {
    fun read(key: String): String?
    fun write(key: String, value: String): Boolean
    fun delete(key: String): Boolean
}

@OptIn(ExperimentalForeignApi::class)
internal class KeychainStringStore(
    private val service: String,
) : SecureStringStore {
    override fun read(key: String): String? = withQuery(key) { query ->
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)

        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            if (status != errSecSuccess) {
                if (status != errSecItemNotFound) {
                    println("KeychainStringStore: read failed (OSStatus: $status)")
                }
                return@withQuery null
            }

            val data: CFDataRef = result.value?.reinterpret() ?: return@withQuery null
            try {
                val length = CFDataGetLength(data).toInt()
                CFDataGetBytePtr(data)?.readBytes(length)?.decodeToString()
            } finally {
                CFRelease(data)
            }
        }
    }

    override fun write(key: String, value: String): Boolean = withQuery(key) { query ->
        val data = value.toCFData()
        try {
            val addQuery = CFDictionaryCreateMutableCopy(kCFAllocatorDefault, 0, query)
                ?: error("Failed to create secure storage query.")
            val addStatus = try {
                CFDictionarySetValue(
                    addQuery,
                    kSecAttrAccessible,
                    kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                )
                CFDictionarySetValue(addQuery, kSecValueData, data)
                SecItemAdd(addQuery, null)
            } finally {
                CFRelease(addQuery)
            }
            if (addStatus == errSecSuccess) return@withQuery true
            if (addStatus != errSecDuplicateItem) {
                println("KeychainStringStore: write failed (OSStatus: $addStatus)")
                return@withQuery false
            }

            val attributes = newDictionary()
            val updateStatus = try {
                CFDictionarySetValue(attributes, kSecValueData, data)
                SecItemUpdate(query, attributes)
            } finally {
                CFRelease(attributes)
            }
            if (updateStatus != errSecSuccess) {
                println("KeychainStringStore: update failed (OSStatus: $updateStatus)")
            }
            updateStatus == errSecSuccess
        } finally {
            CFRelease(data)
        }
    }

    override fun delete(key: String): Boolean = withQuery(key) { query ->
        val status = SecItemDelete(query)
        if (status != errSecSuccess && status != errSecItemNotFound) {
            println("KeychainStringStore: delete failed (OSStatus: $status)")
        }
        status == errSecSuccess || status == errSecItemNotFound
    }

    private inline fun <T> withQuery(key: String, block: (CFMutableDictionaryRef) -> T): T {
        val query = newDictionary()
        val serviceValue = service.toCFString()
        val accountValue = key.toCFString()
        try {
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, serviceValue)
            CFDictionarySetValue(query, kSecAttrAccount, accountValue)
            return block(query)
        } finally {
            CFRelease(accountValue)
            CFRelease(serviceValue)
            CFRelease(query)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun newDictionary(): CFMutableDictionaryRef =
    CFDictionaryCreateMutable(
        kCFAllocatorDefault,
        0,
        kCFTypeDictionaryKeyCallBacks.ptr,
        kCFTypeDictionaryValueCallBacks.ptr,
    ) ?: error("Failed to create secure storage dictionary.")

@OptIn(ExperimentalForeignApi::class)
private fun String.toCFString() =
    CFStringCreateWithCString(kCFAllocatorDefault, this, kCFStringEncodingUTF8)
        ?: error("Failed to encode secure storage key.")

@OptIn(ExperimentalForeignApi::class)
private fun String.toCFData(): CFDataRef {
    val bytes = encodeToByteArray()
    return bytes.usePinned { pinned ->
        CFDataCreate(
            kCFAllocatorDefault,
            pinned.addressOf(0).reinterpret<UByteVar>(),
            bytes.size.toLong(),
        ) ?: error("Failed to encode auth session.")
    }
}
