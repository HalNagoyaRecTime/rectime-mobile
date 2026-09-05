package com.rectime.mobile.ui.component

import androidx.compose.runtime.Composable

/**
 * ダイアログの背後にOSが自動で敷く「暗幕」の濃さを調整する。
 * Android以外（Desktop/iOS）は元々OS標準の暗幕がほぼ無い/薄いため、何もしない実装にしている。
 *
 * @param dimAmount 0f(暗幕なし) 〜 1f(真っ黒) の濃さ
 */
@Composable
expect fun ModalScrimController(dimAmount: Float)