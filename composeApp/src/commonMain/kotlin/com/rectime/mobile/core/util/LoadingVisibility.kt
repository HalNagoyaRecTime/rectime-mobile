package com.rectime.mobile.core.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest

internal enum class LoadingMode { Hidden, Immediate, Delayed }

/** データの更新では待ち時間をリセットせず、取得状態の変化だけに追従する。 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<LoadingMode>.loadingVisibility(): Flow<Boolean> =
    distinctUntilChanged().transformLatest { mode ->
        when (mode) {
            LoadingMode.Hidden -> emit(false)
            LoadingMode.Immediate -> emit(true)
            LoadingMode.Delayed -> {
                emit(false)
                delay(1_000)
                emit(true)
            }
        }
    }
