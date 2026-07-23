package com.rectime.mobile.feature.notifications

data class IncomingPushNotification(
    val title: String,
    val body: String,
    val importance: Int,
) {
    companion object {
        fun from(
            notificationTitle: String?,
            notificationBody: String?,
            data: Map<String, String>,
        ): IncomingPushNotification {
            return IncomingPushNotification(
                title = notificationTitle
                    ?.takeIf(String::isNotBlank)
                    ?: data["title"]?.takeIf(String::isNotBlank)
                    ?: DEFAULT_TITLE,
                body = notificationBody
                    ?.takeIf(String::isNotBlank)
                    ?: data["body"]?.takeIf(String::isNotBlank)
                    ?: DEFAULT_BODY,
                importance = data["importance"]
                    ?.toIntOrNull()
                    ?.takeIf { it == MVP_IMPORTANCE }
                    ?: MVP_IMPORTANCE,
            )
        }
    }
}

private const val MVP_IMPORTANCE = 2
private const val DEFAULT_TITLE = "REC TIME"
private const val DEFAULT_BODY = "新しい通知があります。"
