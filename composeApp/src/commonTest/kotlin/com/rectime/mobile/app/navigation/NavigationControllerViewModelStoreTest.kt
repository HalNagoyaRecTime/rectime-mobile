package com.rectime.mobile.app.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rectime.mobile.feature.schedule.ScheduleScreen
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationControllerViewModelStoreTest {

    private class DummyScreen(override val key: String) : Screen {
        @Composable
        override fun Content(navigationController: NavigationController) {
        }
    }

    private class TrackedViewModel : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }

    private fun PushEntry.trackedViewModel(): TrackedViewModel =
        ViewModelProvider.create(this, viewModelFactory { initializer { TrackedViewModel() } })[TrackedViewModel::class]

    private fun NavigationController.pushAndFinishEnter(screen: Screen): PushEntry {
        push(screen)
        val entry = state.pushStack.last()
        finishPushEnter(entry.key)
        return entry
    }

    @Test
    fun clearsViewModelsOfPoppedScreenOnly() {
        val controller = NavigationController()
        val below = controller.pushAndFinishEnter(DummyScreen("below")).trackedViewModel()
        val top = controller.pushAndFinishEnter(DummyScreen("top"))
        val topViewModel = top.trackedViewModel()

        controller.requestPop()
        controller.completePop(top.key)

        assertTrue(topViewModel.cleared)
        assertFalse(below.cleared)
    }

    @Test
    fun keepsViewModelsWhenSwipeBackIsCanceled() {
        val controller = NavigationController()
        val entry = controller.pushAndFinishEnter(DummyScreen("detail"))
        val viewModel = entry.trackedViewModel()

        val key = controller.beginBackGesture()!!
        controller.finishBackGesture(key, dismiss = false)
        controller.finishBackReturn(key)

        assertFalse(viewModel.cleared)
    }

    @Test
    fun clearsViewModelsOfAllPushedScreensOnReset() {
        val controller = NavigationController()
        val first = controller.pushAndFinishEnter(DummyScreen("first")).trackedViewModel()
        val second = controller.pushAndFinishEnter(DummyScreen("second")).trackedViewModel()

        controller.reset(ScheduleScreen)

        assertTrue(first.cleared)
        assertTrue(second.cleared)
    }

    @Test
    fun clearsViewModelsOfPushedScreensWhenNavigationIsCleared() {
        val owner = object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
        val controller = ViewModelProvider.create(
            owner,
            viewModelFactory { initializer { NavigationController() } },
        )[NavigationController::class]
        val pushed = controller.pushAndFinishEnter(DummyScreen("detail")).trackedViewModel()

        owner.viewModelStore.clear()

        assertTrue(pushed.cleared)
    }
}
