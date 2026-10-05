package com.rectime.mobile.feature.schedule

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

// iOSの既定Factoryはコンストラクタを呼べないため、画面と起動時処理で同じ生成方法を使う。
internal fun scheduleViewModelFactory(
    create: () -> ScheduleViewModel = { ScheduleViewModel() },
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create() }
}
