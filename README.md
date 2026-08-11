# MVI Flow Lab

一个用 Jetpack Compose 演示 Android MVI、`LiveData`、`StateFlow` 与 `SharedFlow` 的最小项目。

- Android 包名 / Application ID：`com.example.mviflowlab`
- Application 类：`MviFlowLabApplication`
- Compose 主题：`MviFlowLabTheme`

完整的通俗讲解、源码导读和动手实验请阅读：[`MVI_FLOW_GUIDE.md`](MVI_FLOW_GUIDE.md)。

如果对 Kotlin 语法不熟悉，请先阅读：[`KOTLIN_CODE_WALKTHROUGH.md`](KOTLIN_CODE_WALKTHROUGH.md)。它会逐段解释本项目中的 `it`、`by`、`::`、`copy()`、协程、Compose 和测试语法。

## 数据流

```text
用户操作 -> MainIntent -> MainScreenViewModel
                         |-> LiveData<Int> -> observeAsState() -> Compose UI
                         |-> StateFlow<MainUiState> -> Compose UI
                         `-> SharedFlow<MainUiEffect> -> Snackbar
```

## 可以观察什么

- 点击 LiveData 计数按钮：`MutableLiveData` 在 ViewModel 内更新，UI 通过生命周期感知的 `observeAsState()` 自动重组。
- 点击计数按钮后旋转屏幕：`StateFlow` 会向新订阅者提供当前状态。
- 点击“一次性事件”：`SharedFlow` 显示 Snackbar，`replay = 0`，不会重放旧消息。
- 点击“加载成功”或“模拟失败”：loading、数据、错误统一归约进 `MainUiState`。
- Compose 使用 `observeAsState()` 观察 LiveData、使用 `collectAsStateWithLifecycle()` 收集 StateFlow；短暂效果由 UI 在 `STARTED` 生命周期内收集。

## LiveData 的传统 XML 写法

```kotlin
viewModel.liveDataCount.observe(viewLifecycleOwner) { count ->
    countTextView.text = count.toString()
}
```

LiveData 只通知处于 `STARTED` 或 `RESUMED` 状态的观察者，并在对应 Lifecycle 被销毁时自动移除观察者。本 Demo 为了方便比较，同时展示了 LiveData 和 StateFlow；实际页面通常选择一种作为主要 UI 状态源，避免维护重复状态。

## 运行

```shell
./gradlew test
./gradlew assembleDebug
```

## 关于一次性事件

此项目按照文章主题展示 `SharedFlow` 的瞬时 Snackbar 效果。`SharedFlow(replay = 0)` 是尽力交付：没有活跃订阅者时，事件可能不会被 UI 收到。必须保证处理的业务结果（例如支付完成）不应只存在于瞬时事件中，应进入可恢复的状态或数据层。
