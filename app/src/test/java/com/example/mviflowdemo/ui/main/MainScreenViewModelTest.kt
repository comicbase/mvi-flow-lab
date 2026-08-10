package com.example.mviflowdemo.ui.main

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {
  @get:Rule val instantTaskExecutorRule = InstantTaskExecutorRule()
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun increment_reducesIntentIntoState() = runTest {
    val viewModel = MainScreenViewModel(FakeUserRepository())

    viewModel.onIntent(MainIntent.Increment)

    assertEquals(1, viewModel.uiState.value.count)
  }

  @Test
  fun incrementLiveData_updatesCurrentValue() = runTest {
    val viewModel = MainScreenViewModel(FakeUserRepository())

    viewModel.onIntent(MainIntent.IncrementLiveData)

    assertEquals(1, viewModel.liveDataCount.value)
  }

  @Test
  fun successfulLoad_updatesStateAndEmitsEffect() = runTest {
    val viewModel = MainScreenViewModel(FakeUserRepository())
    var receivedEffect: MainUiEffect? = null
    val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      receivedEffect = viewModel.effects.first()
    }

    viewModel.onIntent(MainIntent.LoadUsers)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.isLoading)
    assertEquals(listOf(User(7, "Test", "Tester")), viewModel.uiState.value.users)
    assertEquals(MainUiEffect.ShowSnackbar("加载成功：1 位用户"), receivedEffect)
    collection.cancel()
  }

  @Test
  fun failedLoad_putsErrorInState() = runTest {
    val viewModel = MainScreenViewModel(FakeUserRepository())

    viewModel.onIntent(MainIntent.LoadUsersWithError)
    advanceUntilIdle()

    assertEquals("测试失败", viewModel.uiState.value.errorMessage)
    assertFalse(viewModel.uiState.value.isLoading)
  }
}

private class FakeUserRepository : UserRepository {
  override suspend fun loadUsers(shouldFail: Boolean): List<User> {
    if (shouldFail) error("测试失败")
    return listOf(User(7, "Test", "Tester"))
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
  private val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
  override fun starting(description: Description) = Dispatchers.setMain(testDispatcher)

  override fun finished(description: Description) = Dispatchers.resetMain()
}
