package com.rectime.mobile.core.image

// 認証付き画像のURLは利用者を区別しない(avatar_urlは全利用者共通の
// /api/v1/auth/me/photo)。URLだけをCacheKeyにすると共有端末で前の利用者の画像が
// 出るため、利用者IDで分ける。IDが無い場合はnullを返し、呼び出し側でCacheを使わせない。
fun authenticatedImageCacheKey(imageUrl: String, userId: String?): String? =
    if (userId.isNullOrBlank()) null else "$userId|$imageUrl"
