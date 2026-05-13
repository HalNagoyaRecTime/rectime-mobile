package com.rectime.mobile.core.model

data class UserProfile(
    val id: String,
    val name: String,
    val initials: String,
    val imageUrl: String? = null,
    val department: String? = null,
    val studentId: String? = null,
)

object MockUser {
    val me = UserProfile(
        id = "me",
        name = "HAL 太郎",
        initials = "RT",
        imageUrl = "https://cataas.com/cat",
        department = "IA12B",
        studentId = "12345"
    )
}
