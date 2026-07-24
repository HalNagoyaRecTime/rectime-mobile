package com.rectime.mobile.feature.notifications

data class IncomingPushNotification(
    val title: String,
    val body: String,
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
            )
        }
    }
}

private const val DEFAULT_TITLE = "REC TIME"
private const val DEFAULT_BODY = "新しい通知があります。"
