package com.rectime.mobile.feature.auth

import com.rectime.mobile.core.platform.foregroundKeyWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorCodeCanceledLogin
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorDomain
import platform.Foundation.NSURL
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

// Info.plistのCFBundleURLTypesと、APIのMICROSOFT_MOBILE_REDIRECT_URIのスキームに合わせる。
private const val AUTH_CALLBACK_SCHEME = "com.rectime.mobile"

// presentationContextProviderは弱参照のため、表示中のセッションと一緒に保持する。
private var activeSession: ASWebAuthenticationSession? = null
private var activeAnchor: AuthPresentationAnchor? = null

/**
 * 外部ブラウザへの遷移はApp Store審査(Guideline 4)で不可とされたため、
 * ASWebAuthenticationSessionでアプリ内に表示する。
 * コールバックURLはonOpenURLを経由せず、完了ハンドラーで受け取る。
 */
internal actual suspend fun openPlatformAuthBrowser(url: String): Boolean = withContext(Dispatchers.Main) {
    val nsUrl = NSURL.URLWithString(url) ?: return@withContext false
    val window = foregroundKeyWindow() ?: return@withContext false
    val anchor = AuthPresentationAnchor(window)
    lateinit var session: ASWebAuthenticationSession
    session = ASWebAuthenticationSession(nsUrl, AUTH_CALLBACK_SCHEME) { callbackUrl, error ->
        // 完了ハンドラーの実行スレッドに依存せず、保持中のセッションをメインスレッドで更新する。
        dispatch_async(dispatch_get_main_queue()) {
            // 再試行で置き換えた古いセッションの完了は、新しいログインに影響させない。
            if (activeSession !== session) return@dispatch_async
            activeSession = null
            activeAnchor = null
            val callback = callbackUrl?.absoluteString
            if (callback != null) {
                AuthDeepLinkHandler.handle(callback)
            } else {
                val canceledByUser = error != null &&
                    error.domain == ASWebAuthenticationSessionErrorDomain &&
                    error.code == ASWebAuthenticationSessionErrorCodeCanceledLogin
                AuthDeepLinkHandler.dismiss(canceledByUser)
            }
        }
    }
    session.presentationContextProvider = anchor
    val previous = activeSession
    activeSession = session
    activeAnchor = anchor
    previous?.cancel()
    if (!session.start()) {
        activeSession = null
        activeAnchor = null
        return@withContext false
    }
    true
}

private class AuthPresentationAnchor(
    private val window: UIWindow,
) : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(
        session: ASWebAuthenticationSession,
    ): ASPresentationAnchor = window
}
