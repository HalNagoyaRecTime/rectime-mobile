package com.rectime.mobile.feature.notifications

internal object NotificationJson {
    fun parseNotifications(json: String): List<AppNotification> {
        val source = extractArray(json, "notifications") ?: json.trim()
        if (!source.startsWith("[")) return emptyList()

        return splitObjects(source).mapNotNull { objectJson ->
            val id = stringValue(objectJson, "notification_id")
                ?: stringValue(objectJson, "id")
                ?: return@mapNotNull null
            val title = stringValue(objectJson, "title").orEmpty()
            val message = stringValue(objectJson, "message").orEmpty()
            val readAt = nullableStringValue(objectJson, "read_at")
            AppNotification(
                id = id,
                type = stringValue(objectJson, "type").orEmpty(),
                title = title,
                message = message,
                linkUrl = nullableStringValue(objectJson, "link_url"),
                severity = severityValue(stringValue(objectJson, "severity")),
                isRead = boolValue(objectJson, "is_read") ?: (readAt != null),
                createdAt = stringValue(objectJson, "created_at").orEmpty(),
                sentAt = nullableStringValue(objectJson, "sent_at"),
                readAt = readAt,
            )
        }
    }

    fun parseUnreadCount(json: String): Int {
        return intValue(json, "unread_count") ?: intValue(json, "count") ?: 0
    }

    fun parseReadResult(json: String, fallbackId: String): AppNotificationReadResult {
        return AppNotificationReadResult(
            notificationId = stringValue(json, "notification_id") ?: stringValue(json, "id") ?: fallbackId,
            isRead = boolValue(json, "is_read") ?: true,
            readAt = nullableStringValue(json, "read_at"),
        )
    }

    fun parseMarkAllReadResult(json: String): MarkAllReadResult {
        return MarkAllReadResult(
            updatedCount = intValue(json, "updated_count") ?: 0,
            readAt = nullableStringValue(json, "read_at"),
        )
    }

    private fun severityValue(value: String?): NotificationSeverity = when (value?.lowercase()) {
        "success" -> NotificationSeverity.Success
        "warning", "warn" -> NotificationSeverity.Warning
        "error" -> NotificationSeverity.Error
        else -> NotificationSeverity.Info
    }

    private fun extractArray(json: String, key: String): String? {
        val keyIndex = json.indexOf("\"$key\"")
        if (keyIndex < 0) return null

        val arrayStart = json.indexOf('[', startIndex = keyIndex)
        if (arrayStart < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (index in arrayStart until json.length) {
            val char = json[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == '"') {
                    inString = false
                }
                continue
            }

            when (char) {
                '"' -> inString = true
                '[' -> depth += 1
                ']' -> {
                    depth -= 1
                    if (depth == 0) return json.substring(arrayStart, index + 1)
                }
            }
        }
        return null
    }

    private fun splitObjects(arrayJson: String): List<String> {
        val objects = mutableListOf<String>()
        var objectStart = -1
        var depth = 0
        var inString = false
        var escaped = false

        arrayJson.forEachIndexed { index, char ->
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == '"') {
                    inString = false
                }
                return@forEachIndexed
            }

            when (char) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) objectStart = index
                    depth += 1
                }
                '}' -> {
                    depth -= 1
                    if (depth == 0 && objectStart >= 0) {
                        objects += arrayJson.substring(objectStart, index + 1)
                        objectStart = -1
                    }
                }
            }
        }

        return objects
    }

    private fun stringValue(json: String, key: String): String? {
        val raw = rawValue(json, key) ?: return null
        if (!raw.startsWith('"')) return raw
        return unescapeJsonString(raw.substring(1, raw.length - 1))
    }

    private fun nullableStringValue(json: String, key: String): String? {
        val raw = rawValue(json, key) ?: return null
        if (raw == "null") return null
        return if (raw.startsWith('"')) unescapeJsonString(raw.substring(1, raw.length - 1)) else raw
    }

    private fun boolValue(json: String, key: String): Boolean? = when (rawValue(json, key)) {
        "true" -> true
        "false" -> false
        else -> null
    }

    private fun intValue(json: String, key: String): Int? = rawValue(json, key)?.toIntOrNull()

    private fun rawValue(json: String, key: String): String? {
        val keyIndex = json.indexOf("\"$key\"")
        if (keyIndex < 0) return null

        val colonIndex = json.indexOf(':', startIndex = keyIndex)
        if (colonIndex < 0) return null

        var index = colonIndex + 1
        while (index < json.length && json[index].isWhitespace()) {
            index += 1
        }
        if (index >= json.length) return null

        if (json[index] == '"') {
            val start = index
            index += 1
            var escaped = false
            while (index < json.length) {
                val char = json[index]
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == '"') {
                    return json.substring(start, index + 1)
                }
                index += 1
            }
            return null
        }

        val start = index
        while (index < json.length && json[index] != ',' && json[index] != '}') {
            index += 1
        }
        return json.substring(start, index).trim()
    }

    private fun unescapeJsonString(value: String): String {
        val result = StringBuilder()
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char != '\\' || index == value.lastIndex) {
                result.append(char)
                index += 1
                continue
            }

            val escaped = value[index + 1]
            if (escaped == 'u' && index + 5 < value.length) {
                val code = value.substring(index + 2, index + 6).toIntOrNull(16)
                if (code != null) {
                    result.append(code.toChar())
                    index += 6
                    continue
                }
            }

            result.append(
                when (escaped) {
                    '"' -> '"'
                    '\\' -> '\\'
                    '/' -> '/'
                    'b' -> '\b'
                    'f' -> '\u000C'
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    else -> escaped
                }
            )
            index += 2
        }
        return result.toString()
    }
}
