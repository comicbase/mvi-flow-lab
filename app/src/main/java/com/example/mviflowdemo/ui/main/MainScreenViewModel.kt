package com.example.mviflowdemo.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI 发给 ViewModel 的所有意图。UI 只描述“发生了什么”，不直接修改状态。
 */
sealed interface MainIntent {
  data object IncrementLiveData : MainIntent
  data object ResetLiveData : MainIntent
  data object Increment : MainIntent
  data object Decrement : MainIntent
  data object Reset : MainIntent
  data object LoadUsers : MainIntent
  data object LoadUsersWithError : MainIntent
  data object ShowOneShotMessage : MainIntent
}

/** StateFlow 中保存的完整页面状态，新订阅者会立刻拿到最新值。 */
data class MainUiState(
  val count: Int = 0,
  val isLoading: Boolean = false,
  val users: List<User> = emptyList(),
  val errorMessage: String? = null,
)

/** SharedFlow 中发送的短暂 UI 效果，replay = 0，不会重放给新订阅者。 */
sealed interface MainUiEffect {
  data class ShowSnackbar(val message: String) : MainUiEffect
}

data class User(val id: Int, val name: String, val role: String)

interface UserRepository {
  suspend fun loadUsers(shouldFail: Boolean): List<User>
}

class DemoUserRepository : UserRepository {
  override suspend fun loadUsers(shouldFail: Boolean): List<User> {
    delay(900)
    if (shouldFail) error("模拟网络请求失败")
    return listOf(
      User(1, "Ada", "Android Engineer"),
      User(2, "Lin", "Product Designer"),
      User(3, "Mori", "Kotlin Developer"),
    )
  }
}

class MainScreenViewModel(
  private val repository: UserRepository = DemoUserRepository(),
) : ViewModel() {
  private val _liveDataCount = MutableLiveData(0)
  val liveDataCount: LiveData<Int> = _liveDataCount

  private val _uiState = MutableStateFlow(MainUiState())
  val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

  private val _effects = MutableSharedFlow<MainUiEffect>(extraBufferCapacity = 1)
  val effects: SharedFlow<MainUiEffect> = _effects.asSharedFlow()

  fun onIntent(intent: MainIntent) {
    when (intent) {
      MainIntent.IncrementLiveData ->
        _liveDataCount.value = (_liveDataCount.value ?: 0) + 1
      MainIntent.ResetLiveData -> _liveDataCount.value = 0
      MainIntent.Increment -> _uiState.update { it.copy(count = it.count + 1) }
      MainIntent.Decrement -> _uiState.update { it.copy(count = it.count - 1) }
      MainIntent.Reset -> {
        _uiState.value = MainUiState()
        sendEffect(MainUiEffect.ShowSnackbar("StateFlow 状态已重置"))
      }
      MainIntent.LoadUsers -> loadUsers(shouldFail = false)
      MainIntent.LoadUsersWithError -> loadUsers(shouldFail = true)
      MainIntent.ShowOneShotMessage ->
        sendEffect(MainUiEffect.ShowSnackbar("这是 SharedFlow 的一次性事件"))
    }
  }

  private fun loadUsers(shouldFail: Boolean) {
    if (_uiState.value.isLoading) return

    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      try {
        val users = repository.loadUsers(shouldFail)
        _uiState.update { it.copy(isLoading = false, users = users) }
        sendEffect(MainUiEffect.ShowSnackbar("加载成功：${users.size} 位用户"))
      } catch (error: Throwable) {
        if (error is CancellationException) throw error
        _uiState.update {
          it.copy(
            isLoading = false,
            errorMessage = error.message ?: "未知错误",
          )
        }
      }
    }
  }

  private fun sendEffect(effect: MainUiEffect) {
    _effects.tryEmit(effect)
  }
}
