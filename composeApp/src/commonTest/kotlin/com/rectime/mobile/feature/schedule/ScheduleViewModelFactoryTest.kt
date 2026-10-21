package com.rectime.mobile.feature.schedule

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelFactoryTest {
    private val store = ViewModelStore()
    private val client = HttpClient(MockEngine { error("生成だけでは通信しない") })

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        store.clear()
        client.close()
        Dispatchers.resetMain()
    }

    @Test
    fun factorySupportsMultiplatformCreationWithExtras() {
        val expected = ScheduleViewModel(client = client)
        val factory = scheduleViewModelFactory { expected }
        val actual = factory.create(ScheduleViewModel::class, CreationExtras.Empty)
        assertSame(expected, actual)
        // Factoryから直接生成した場合も、テスト終了時にScopeを破棄する。
        store.put("schedule", actual)
    }

    @Test
    fun appAndScreenProvidersShareOneViewModelInTheSameStore() {
        var creations = 0
        val create = { creations++; ScheduleViewModel(client = client) }
        val appProvider = ViewModelProvider.create(store, scheduleViewModelFactory(create))
        val screenProvider = ViewModelProvider.create(store, scheduleViewModelFactory(create))

        assertSame(appProvider[ScheduleViewModel::class], screenProvider[ScheduleViewModel::class])
        assertEquals(1, creations)
    }
}
