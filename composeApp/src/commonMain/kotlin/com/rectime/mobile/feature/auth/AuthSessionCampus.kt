package com.rectime.mobile.feature.auth

import com.rectime.mobile.core.config.CurrentCampus

/** 保存済みログインの学校をAPIの接続先にする。現在の環境で使えない学校ならfalse。 */
fun restoreCampus(session: AuthSession): Boolean = CurrentCampus.select(session.campus.name)
