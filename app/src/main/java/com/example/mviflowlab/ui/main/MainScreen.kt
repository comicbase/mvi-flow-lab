package com.example.mviflowlab.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mviflowlab.theme.MviFlowLabTheme

@Composable
fun MviFlowLabRoute(
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  // LiveData 自己感知 LifecycleOwner；observeAsState 将最新值转换成 Compose State。
  val liveDataCount by viewModel.liveDataCount.observeAsState(initial = 0)
  val snackbarHostState = remember { SnackbarHostState() }
  val lifecycleOwner = LocalLifecycleOwner.current

  // SharedFlow 不感知生命周期；由 UI 决定何时开始/停止收集。
  LaunchedEffect(viewModel, lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
      viewModel.effects.collect { effect ->
        when (effect) {
          is MainUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
        }
      }
    }
  }

  MviFlowLabScreen(
    state = state,
    liveDataCount = liveDataCount,
    onIntent = viewModel::onIntent,
    snackbarHostState = snackbarHostState,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MviFlowLabScreen(
  state: MainUiState,
  liveDataCount: Int,
  onIntent: (MainIntent) -> Unit,
  snackbarHostState: SnackbarHostState,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize().safeDrawingPadding(),
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("MVI · LiveData & Flow", fontWeight = FontWeight.Bold)
            Text(
              "Intent → ViewModel → State → UI",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
  ) { contentPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(contentPadding),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item { LiveDataCard(count = liveDataCount, onIntent = onIntent) }
      item { StateFlowCard(state = state, onIntent = onIntent) }
      item { SharedFlowCard(onIntent = onIntent) }
      item { UserLoadingCard(state = state, onIntent = onIntent) }
      if (state.users.isNotEmpty()) {
        item {
          Text(
            "当前状态中的用户",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
        }
        items(state.users, key = User::id) { user -> UserRow(user) }
      }
      item { ArchitectureCard() }
    }
  }
}

@Composable
private fun LiveDataCard(count: Int, onIntent: (MainIntent) -> Unit) {
  LearningCard(
    title = "1. LiveData：生命周期感知状态",
    description = "ViewModel 只暴露 LiveData，内部使用 MutableLiveData。Compose 通过 observeAsState() 自动注册和移除观察者。",
    accent = true,
  ) {
    Text(
      text = "LiveData 当前值：$count",
      style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.tertiary,
      fontWeight = FontWeight.Bold,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = { onIntent(MainIntent.IncrementLiveData) }) {
        Text("LiveData +1")
      }
      OutlinedButton(onClick = { onIntent(MainIntent.ResetLiveData) }) {
        Text("LiveData 重置")
      }
    }
    Text(
      "XML 页面中的等价写法：liveData.observe(viewLifecycleOwner) { ... }",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun StateFlowCard(state: MainUiState, onIntent: (MainIntent) -> Unit) {
  LearningCard(
    title = "2. StateFlow：协程原生状态",
    description = "计数值保存在 UiState 中。Compose 用 collectAsStateWithLifecycle() 在活跃生命周期内收集。",
  ) {
    Text(
      text = state.count.toString(),
      style = MaterialTheme.typography.displayMedium,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Bold,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = { onIntent(MainIntent.Decrement) }) { Text("−1") }
      Button(onClick = { onIntent(MainIntent.Increment) }) { Text("+1") }
      OutlinedButton(onClick = { onIntent(MainIntent.Reset) }) { Text("重置") }
    }
  }
}

@Composable
private fun SharedFlowCard(onIntent: (MainIntent) -> Unit) {
  LearningCard(
    title = "3. SharedFlow：发送短暂效果",
    description = "点击后显示 Snackbar。此流 replay = 0，新订阅者不会收到已经发过的消息。",
    accent = true,
  ) {
    Button(onClick = { onIntent(MainIntent.ShowOneShotMessage) }) {
      Text("发送一次性事件")
    }
  }
}

@Composable
private fun UserLoadingCard(state: MainUiState, onIntent: (MainIntent) -> Unit) {
  LearningCard(
    title = "4. 异步请求：状态归约",
    description = "加载中、数据和错误都进入同一个 UiState，页面只负责渲染。",
  ) {
    if (state.isLoading) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        CircularProgressIndicator(modifier = Modifier.height(24.dp))
        Text("正在模拟请求…")
      }
    }
    state.errorMessage?.let {
      Text(
        text = it,
        color = MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.Medium,
      )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
        onClick = { onIntent(MainIntent.LoadUsers) },
        enabled = !state.isLoading,
      ) { Text("加载成功") }
      OutlinedButton(
        onClick = { onIntent(MainIntent.LoadUsersWithError) },
        enabled = !state.isLoading,
      ) { Text("模拟失败") }
    }
  }
}

@Composable
private fun UserRow(user: User) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier = Modifier
          .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
          .padding(horizontal = 14.dp, vertical = 10.dp),
      ) {
        Text(user.name.take(1), fontWeight = FontWeight.Bold)
      }
      Column {
        Text(user.name, fontWeight = FontWeight.SemiBold)
        Text(
          user.role,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun ArchitectureCard() {
  LearningCard(
    title = "LiveData 与 StateFlow 怎么选？",
    description = "二者都能保存并重放最新状态。LiveData 自动感知 Android 生命周期；StateFlow 属于协程生态，可组合且跨平台。",
  ) {
    HorizontalDivider()
    Text(
      "传统 XML / Java 项目：LiveData 仍然合适\nCompose / 协程项目：优先 StateFlow\n短暂 UI 效果：本 Demo 使用 SharedFlow",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun LearningCard(
  title: String,
  description: String,
  accent: Boolean = false,
  content: @Composable ColumnScope.() -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = if (accent) {
        MaterialTheme.colorScheme.secondaryContainer
      } else {
        MaterialTheme.colorScheme.surfaceContainer
      },
    ),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(18.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      Text(
        description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(2.dp))
      content()
    }
  }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 860)
@Composable
private fun MviFlowLabPreview() {
  MviFlowLabTheme {
    MviFlowLabScreen(
      state = MainUiState(
        count = 3,
        users = listOf(User(1, "Ada", "Android Engineer")),
      ),
      liveDataCount = 2,
      onIntent = {},
      snackbarHostState = remember { SnackbarHostState() },
    )
  }
}
