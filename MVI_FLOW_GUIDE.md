# 从 LiveData 到 StateFlow：结合本项目理解 Android MVI

这是一份配合本项目阅读的入门教程。它不只介绍 API，还会回答几个更重要的问题：

如果阅读 Kotlin 源码时对 `it`、`by`、`::`、可空类型、Lambda 或协程语法感到陌生，请先看 [`KOTLIN_CODE_WALKTHROUGH.md`](KOTLIN_CODE_WALKTHROUGH.md)。

- 什么是 MVI 和单向数据流？
- LiveData、StateFlow、SharedFlow 分别解决什么问题？
- 为什么 Flow 不自动感知 Android 生命周期，反而更容易复用？
- 为什么“状态”和“一次性事件”不能随便混用？
- 原文章中哪些说法是便于入门的简化，实际开发时又要注意什么？

建议先运行应用，一边点击页面中的四个演示区域，一边阅读本文。

```shell
./gradlew installDebug
```

主要源码：

- [`MainScreenViewModel.kt`](app/src/main/java/com/example/mviflowlab/ui/main/MainScreenViewModel.kt)：Intent、UiState、UiEffect、LiveData、StateFlow 和 SharedFlow
- [`MainScreen.kt`](app/src/main/java/com/example/mviflowlab/ui/main/MainScreen.kt)：Compose 收集数据并渲染页面
- [`MainScreenViewModelTest.kt`](app/src/test/java/com/example/mviflowlab/ui/main/MainScreenViewModelTest.kt)：状态流和事件流的测试示例

## 一、先建立最简单的心智模型

可以把三种工具想成三个不同的东西：

| 工具 | 通俗比喻 | 主要用途 |
| --- | --- | --- |
| LiveData | 带 Android 生命周期开关的公告牌 | 传统 Android UI 状态 |
| StateFlow | 永远写着“当前情况”的电子屏 | 协程项目中的状态 |
| SharedFlow | 面向当前听众的广播 | 通知多个活跃订阅者 |

“状态”和“事件”是理解这篇文章的关键。

### 状态是什么？

状态描述的是“现在是什么样”。例如：

- 当前计数是 3；
- 页面正在加载；
- 用户列表中有 3 个人；
- 当前错误信息是“网络请求失败”。

新打开的页面应该马上知道这些信息，所以状态需要保存最新值。LiveData 和 StateFlow 都可以承担这个角色。

### 事件是什么？

事件描述的是“刚才发生了一次什么”。例如：

- 显示一次 Snackbar；
- 播放一次动画；
- 触发一次震动。

已经处理过的短暂效果通常不应该在屏幕旋转后再次执行。本项目用 SharedFlow 演示这种 UI 效果。

不过，“支付已经成功”“订单已经提交”这类信息不能只当成瞬时事件。它们是重要业务事实，应该保存到 UiState、数据库或其他可靠数据源中。

## 二、什么是 MVI？

MVI 常被展开为 Model、View、Intent。不同项目的命名可能不同，但核心都是单向数据流：

```text
用户操作
   ↓
Intent：描述用户想做什么
   ↓
ViewModel：处理业务并生成新状态
   ↓
UiState：描述整个页面现在是什么样
   ↓
View：根据状态渲染
```

本项目对应关系如下：

| MVI 概念 | 本项目代码 |
| --- | --- |
| Intent | `MainIntent` |
| 状态 Model | `MainUiState` |
| 短暂 UI 效果 | `MainUiEffect` |
| 状态处理者 | `MainScreenViewModel` |
| View | `MviFlowLabRoute`、`MviFlowLabScreen` |

### 点击“+1”时发生了什么？

第一步，按钮不直接修改页面上的数字，只发送 Intent：

```kotlin
Button(onClick = { onIntent(MainIntent.Increment) }) {
    Text("+1")
}
```

第二步，ViewModel 统一处理 Intent：

```kotlin
MainIntent.Increment ->
    _uiState.update { it.copy(count = it.count + 1) }
```

第三步，StateFlow 发布新的 `MainUiState`。Compose 收到新状态后重新执行依赖该状态的 UI：

```kotlin
val state by viewModel.uiState.collectAsStateWithLifecycle()
```

这样做的好处是，页面变化都有清晰来源。测试时不必真的点击屏幕，只要向 ViewModel 发送 Intent，再检查状态即可。

## 三、LiveData：生命周期感知的数据容器

### 1. 本项目怎样创建 LiveData？

ViewModel 内部保存可修改版本，对外只暴露只读版本：

```kotlin
private val _liveDataCount = MutableLiveData(0)
val liveDataCount: LiveData<Int> = _liveDataCount
```

这种“内部可变、外部只读”的封装很重要。UI 可以观察数据，但不能绕过 ViewModel 随意修改它。

点击 `LiveData +1` 后，Intent 最终执行：

```kotlin
_liveDataCount.value = (_liveDataCount.value ?: 0) + 1
```

`value` 应在主线程修改；如果确实从工作线程更新，可以使用 `postValue()`。需要注意，连续快速调用 `postValue()` 时，中间值可能被合并，因此它不适合表达“每一条都必须处理”的消息队列。

### 2. Compose 怎样观察 LiveData？

本项目使用：

```kotlin
val liveDataCount by viewModel.liveDataCount.observeAsState(initial = 0)
```

`observeAsState()` 做了两件事：

1. 以当前 `LifecycleOwner` 观察 LiveData；
2. 把 LiveData 的值转换成 Compose `State`，值变化时触发重组。

项目因此引入了下面的适配器依赖：

```kotlin
implementation("androidx.compose.runtime:runtime-livedata")
```

### 3. XML + Fragment 中怎么写？

传统 View 系统中通常这样观察：

```kotlin
viewModel.liveDataCount.observe(viewLifecycleOwner) { count ->
    binding.countTextView.text = count.toString()
}
```

LiveData 只向处于 `STARTED` 或 `RESUMED` 状态的观察者发送更新。Fragment 的 View 被销毁时，绑定到 `viewLifecycleOwner` 的观察者会自动移除。

Fragment 中通常应该使用 `viewLifecycleOwner`，而不是直接使用 Fragment 自己作为 LifecycleOwner，因为 Fragment 实例可能还活着，但它的 View 已经销毁。

### 4. LiveData 的优点和限制

优点：

- 自动感知 Android 生命周期；
- API 简单，XML、Java 老项目中使用方便；
- 与 ViewModel、Data Binding、Room 等传统 Jetpack 组件配合成熟；
- 新的活跃观察者能得到已经设置过的当前值。

限制：

- 依赖 AndroidX Lifecycle，不适合作为纯 Kotlin 或 KMP 公共层 API；
- 数据组合和异步处理能力不如 Flow 操作符丰富；
- 和协程结合时经常需要在 LiveData 与 Flow 之间转换；
- 不建议让 Repository 长期在主线程中通过 LiveData 做复杂数据变换。

LiveData 并没有“被禁止”。老 XML 项目继续使用完全合理；新 Compose、协程或跨平台项目通常更适合 StateFlow。

## 四、StateFlow：永远持有当前状态的热流

### 1. 什么叫“热流”？

热流不需要等某个收集者出现才存在。ViewModel 创建 StateFlow 后，它就有自己的生命周期和当前值。

本项目的 StateFlow 是这样创建的：

```kotlin
private val _uiState = MutableStateFlow(MainUiState())
val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
```

这段代码体现了 StateFlow 的几个特点：

- 必须提供初始值；
- 随时可以通过 `_uiState.value` 读取当前状态；
- 新收集者会立即收到当前状态；
- 对外暴露只读 `StateFlow`，只有 ViewModel 能修改状态。

文章把这种行为叫作“粘性”。更准确地说，StateFlow 会向新订阅者重放一个最新值。

### 2. 为什么把整个页面放进一个 UiState？

本项目的状态如下：

```kotlin
data class MainUiState(
    val count: Int = 0,
    val isLoading: Boolean = false,
    val users: List<User> = emptyList(),
    val errorMessage: String? = null,
)
```

页面只要拿到一个 `MainUiState`，就知道应该如何完整渲染：

- `isLoading == true`：显示进度条；
- `users` 非空：显示用户列表；
- `errorMessage` 非空：显示错误；
- `count`：显示当前计数。

这就是常说的 `UI = render(State)`。UI 不需要猜测 ViewModel 内部执行到了哪一步。

### 3. 状态怎么更新？

不要直接修改旧对象，而是复制出一个新状态：

```kotlin
_uiState.update { oldState ->
    oldState.copy(count = oldState.count + 1)
}
```

`update` 会以原子方式基于旧值计算新值。在存在并发更新时，它比“先读 value、计算、再赋值”更稳妥。

StateFlow 使用相等性判断抑制重复值。如果新旧值通过 `equals()` 判断相等，收集者通常不会再次收到它。因此 UiState 很适合使用不可变 `data class`。

### 4. Compose 怎样安全收集？

项目使用：

```kotlin
val state by viewModel.uiState.collectAsStateWithLifecycle()
```

它会根据 Android 生命周期开始或停止收集，并把 Flow 转成 Compose `State`。

不要在 Compose 页面中为了收集 UI 状态而随意写一个永不停止的协程。对于 Android UI，优先使用 `collectAsStateWithLifecycle()`；非 Android 的 Compose Multiplatform 场景再根据平台选择普通 `collectAsState()` 等方式。

## 五、为什么 Flow 把生命周期交给 UI？

Flow 本身不知道 Activity、Fragment 或 Lifecycle 是什么。它只是 Kotlin 数据流，因此可以在以下环境中复用：

- Android；
- 普通 JVM；
- Kotlin Multiplatform；
- Compose Desktop；
- 服务端 Kotlin。

生命周期只与“谁正在显示页面”有关，所以由 UI 决定何时收集更符合职责分离：

```text
ViewModel / Repository：负责产生数据
UI：负责根据自己的生命周期收集数据
```

这不意味着 Flow 项目可以忽略生命周期，而是生命周期控制从数据容器内部变成了 UI 侧的组合操作。

## 六、SharedFlow：向当前订阅者广播

### 1. 本项目怎样定义 SharedFlow？

```kotlin
private val _effects =
    MutableSharedFlow<MainUiEffect>(extraBufferCapacity = 1)
val effects: SharedFlow<MainUiEffect> = _effects.asSharedFlow()
```

效果类型目前只有 Snackbar：

```kotlin
sealed interface MainUiEffect {
    data class ShowSnackbar(val message: String) : MainUiEffect
}
```

点击“发送一次性事件”后，ViewModel 执行：

```kotlin
sendEffect(MainUiEffect.ShowSnackbar("这是 SharedFlow 的一次性事件"))
```

### 2. UI 怎样收集 SharedFlow？

SharedFlow 不自动感知 Android 生命周期，所以本项目由 UI 明确控制：

```kotlin
LaunchedEffect(viewModel, lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MainUiEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(effect.message)
            }
        }
    }
}
```

`repeatOnLifecycle(STARTED)` 的含义是：

- 页面达到 `STARTED` 时启动内部收集协程；
- 页面低于 `STARTED` 时取消内部协程；
- 页面再次达到 `STARTED` 时创建新的收集协程。

### 3. “默认非粘性”不等于“绝对可靠的一次性事件”

默认 `MutableSharedFlow()` 的 `replay` 是 0。新订阅者不会收到订阅之前已经发出的值，所以文章把它称为“非粘性”。

但这也意味着：如果发送时没有活跃订阅者，这个值不会留给未来的订阅者。即便本项目设置了 `extraBufferCapacity = 1`，在完全没有订阅者且 `replay = 0` 时，它也不会替未来订阅者保存消息。

所以 SharedFlow 更准确的定位是广播工具，不是天然的“只消费一次且绝不丢失”的事件总线。

适合放入 SharedFlow 的内容：

- 可接受偶尔不显示的提示；
- 只对当前可见页面有意义的动画、震动等效果；
- 多个当前订阅者都应该收到的实时广播。

不应该只放入 SharedFlow 的内容：

- 支付是否完成；
- 文件是否保存成功；
- 用户是否登录；
- 订单是否已经提交。

这些信息应该进入可恢复状态或持久化数据源，UI 再根据状态决定下一步。

## 七、SharedFlow 和 Channel 到底有什么区别？

文章把 SharedFlow 简化成“广播”，把 Channel 简化成“一对一队列”。广播与队列的方向是对的，但“一对一”并不严谨。

| 对比 | SharedFlow | Channel |
| --- | --- | --- |
| 基本模型 | 广播 | 队列 |
| 多个接收者 | 每个活跃订阅者都可收到同一个值 | 一个元素只会交给其中一个接收者 |
| 是否能配置重放 | 可以，通过 `replay` | 不是 replay 模型 |
| 没有接收者时 | 取决于 replay；默认不会保留给未来订阅者 | 取决于 Channel 容量，发送可能挂起或进入缓冲区 |
| 典型用途 | 当前订阅者都要知道的广播 | 工作任务分发、元素逐个消费 |

Channel 可以有多个接收者，只是同一个元素不会广播给所有接收者，而是由其中一个接收者取得。

不要仅凭“导航事件用 SharedFlow，其他事件用 Channel”机械选择。先明确真正需要的交付语义：允许丢失吗、需要重放吗、是广播还是竞争消费、结果是否应该进入持久状态？

## 八、异步加载如何体现 MVI？

点击“加载成功”时，完整过程如下：

```text
MainIntent.LoadUsers
        ↓
isLoading = true，errorMessage = null
        ↓
Repository 延迟 900ms 并返回用户
        ↓
isLoading = false，users = 新列表
        ↓
SharedFlow 发送“加载成功”Snackbar
```

对应代码：

```kotlin
viewModelScope.launch {
    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
    try {
        val users = repository.loadUsers(shouldFail)
        _uiState.update { it.copy(isLoading = false, users = users) }
        sendEffect(MainUiEffect.ShowSnackbar("加载成功：${users.size} 位用户"))
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        _uiState.update {
            it.copy(isLoading = false, errorMessage = error.message ?: "未知错误")
        }
    }
}
```

这里有两个值得注意的细节：

1. 加载、数据、错误都进入 StateFlow，因为它们决定页面“现在是什么样”；
2. 成功提示进入 SharedFlow，因为 Snackbar 只是当前页面上的短暂效果。

代码对 `CancellationException` 重新抛出也很重要。协程取消不是普通业务失败，不应被转换成“网络错误”显示给用户。

## 九、三者核心对比

| 对比项 | LiveData | StateFlow | SharedFlow |
| --- | --- | --- | --- |
| 主要角色 | 状态容器 | 状态流 | 广播流 |
| 是否必须有初始值 | 否 | 是 | 否 |
| 新订阅者是否收到旧值 | 已设置过值时可收到当前值 | 立即收到最新值 | 默认不收到；由 replay 决定 |
| Android 生命周期感知 | 内置 | 不内置，UI 侧控制 | 不内置，UI 侧控制 |
| 是否协程原生 | 否 | 是 | 是 |
| Flow 操作符 | 不能直接完整使用 | 可以 | 可以 |
| 跨平台能力 | AndroidX Lifecycle 体系 | Kotlin 协程体系 | Kotlin 协程体系 |
| 本项目用途 | 传统计数状态演示 | 完整 UiState | Snackbar 效果 |

LiveData 和 StateFlow 都适合表达状态。本项目同时使用它们是为了对比，实际页面不要把同一份状态复制到两个容器中，否则容易出现两个值不一致的问题。

## 十、为什么现代 Android 更偏向 Flow？

重点不是“StateFlow 比 LiveData 新”，而是 Kotlin 协程已经形成了完整的数据处理生态。

Flow 可以自然组合：

```kotlin
searchQuery
    .debounce(300)
    .distinctUntilChanged()
    .combine(filterFlow) { query, filter -> query to filter }
    .flatMapLatest { repository.search(it.first, it.second) }
```

同一套 suspend、Coroutine、Flow API 可以贯穿 Repository、UseCase、ViewModel 和 UI。StateFlow 还可以通过 `stateIn()` 把上游冷 Flow 转换成有当前值的 UI 状态。

LiveData 更擅长“让 Android UI 简单安全地观察一个值”；Flow 更擅长“构建、组合和转换异步数据管道”。这才是迁移趋势背后的主要原因。

## 十一、Compose 为什么天然适合 StateFlow？

Compose 的核心也是“状态驱动 UI”：

```text
StateFlow 更新
     ↓
collectAsStateWithLifecycle 转成 Compose State
     ↓
读取该 State 的 Composable 重新执行
     ↓
界面显示最新状态
```

注意，“重新执行 Composable”不等于把整个屏幕 View 全部销毁重建。Compose 会根据状态读取位置和组合结构，只更新需要更新的部分。

LiveData 也能通过 `observeAsState()` 接入 Compose，但如果数据层和 ViewModel 本来已经大量使用协程，继续保留 LiveData 往往只增加一次类型转换。

## 十二、跟着 Demo 做实验

### 实验 1：观察 LiveData 状态

1. 点击几次 `LiveData +1`；
2. 观察 `LiveData 当前值` 立即变化；
3. 旋转屏幕；
4. ViewModel 在配置变更期间保留，新的观察者会获得当前值。

这里“旋转后仍有值”不只归功于 LiveData，也依赖 ViewModel 在配置变更期间保留实例。进程被系统杀死后，普通 ViewModel 中的内存状态仍可能丢失；需要恢复的状态应使用 `SavedStateHandle` 或持久化存储。

### 实验 2：观察 StateFlow 状态

1. 点击 `+1` 或 `−1`；
2. 观察 StateFlow 卡片中的数字；
3. 点击“加载成功”；
4. 观察 `isLoading` 让进度条出现，然后用户列表更新。

### 实验 3：区分状态和效果

1. 点击“发送一次性事件”；
2. Snackbar 出现一次；
3. 旋转屏幕；
4. 旧 Snackbar 事件不会因为新订阅者出现而从 SharedFlow 重放。

### 实验 4：观察错误状态

1. 点击“模拟失败”；
2. 请求期间 `isLoading = true`；
3. 请求失败后 `isLoading = false`；
4. `errorMessage` 进入 UiState，因此页面能够稳定显示错误。

## 十三、怎样测试这些代码？

MVI 的一个优势是 ViewModel 很容易测试。例如计数测试只需：

```kotlin
viewModel.onIntent(MainIntent.Increment)
assertEquals(1, viewModel.uiState.value.count)
```

StateFlow 是状态容器，所以很多测试可以直接断言 `.value`，不必为了一个当前值启动收集协程。

SharedFlow 测试则需要先启动收集者，再触发 Intent：

```kotlin
val collection = backgroundScope.launch {
    receivedEffect = viewModel.effects.first()
}

viewModel.onIntent(MainIntent.LoadUsers)
advanceUntilIdle()
```

LiveData 的本地 JVM 测试使用 `InstantTaskExecutorRule`，让 Architecture Components 的任务同步执行：

```kotlin
@get:Rule
val instantTaskExecutorRule = InstantTaskExecutorRule()
```

运行测试：

```shell
./gradlew testDebugUnitTest
```

## 十四、文章中几个需要补充的地方

### 1. “一次性事件必须使用 SharedFlow”并不绝对

SharedFlow 是一种可选工具，不是一次性事件的可靠性保证。关键业务结果优先进入状态；短暂 UI 效果才考虑 SharedFlow。

### 2. “Channel 只有一个消费者”不准确

Channel 可以有多个接收者，只是每个元素由其中一个接收者消费，而不是广播给全部接收者。

### 3. “Flow 没有生命周期感知”只说了一半

Flow 本身确实不知道 Android 生命周期，但 Android UI 可以通过 `collectAsStateWithLifecycle()` 或 `repeatOnLifecycle()` 安全收集。这是组合式设计，不是让开发者完全手动管理所有取消逻辑。

### 4. “页面旋转后状态一定存在”也需要条件

LiveData 和 StateFlow 保存当前值，但值放在 ViewModel 中才能自然跨越配置变更。它们本身都不能保证进程死亡后的恢复。

### 5. “全链路都应该返回 Flow”不是硬性规则

持续变化的数据适合 `Flow<T>`，例如数据库观察、搜索条件、设置变化。只执行一次并返回一个结果的操作通常用 `suspend fun` 更简单。本项目的 `loadUsers()` 就是一次性请求，因此定义成 suspend 函数：

```kotlin
suspend fun loadUsers(shouldFail: Boolean): List<User>
```

## 十五、实际项目怎么选？

可以使用下面的判断顺序：

1. 数据描述“当前是什么样”吗？使用状态容器。
2. 项目以 Compose、协程或 KMP 为主吗？优先 StateFlow。
3. 是传统 XML、Java 或已有大量 LiveData 的稳定项目吗？继续使用 LiveData 没问题。
4. 数据只是短暂 UI 效果吗？先判断能否由状态推导；确实需要广播时再考虑 SharedFlow。
5. 每个元素必须由某一个工作者处理吗？考虑 Channel 或更可靠的任务系统。
6. 结果在进程死亡后也必须存在吗？使用数据库、DataStore、SavedStateHandle 或服务端作为事实来源。

## 十六、面试速记版

### LiveData

Android 生命周期感知的可观察状态容器。适合传统 View 系统；只通知活跃观察者，并在 Lifecycle 销毁后移除观察者。

### StateFlow

协程原生、必须有初始值、永远持有最新值的热流。适合 ViewModel 暴露 UiState，Android UI 用生命周期感知 API 收集。

### SharedFlow

可配置 replay 和缓冲策略的热广播流。默认 replay 为 0，只向当前订阅者广播，不天然保证一次性事件绝不丢失或重复。

### 从 LiveData 转向 Flow 的本质

不是 LiveData 失效了，而是现代 Android 的异步数据处理已经统一到协程与 Flow 生态。数据层专注产生和组合数据，UI 层根据自己的生命周期决定如何收集。

## 十七、官方资料

- [Android 架构建议](https://developer.android.com/topic/architecture/recommendations)
- [StateFlow 与 SharedFlow](https://developer.android.com/kotlin/flow/stateflow-and-sharedflow)
- [LiveData 概览](https://developer.android.com/topic/libraries/architecture/livedata)
- [在 Compose 中使用其他库和数据流](https://developer.android.com/develop/ui/compose/libraries)
- [Android 中的协程与生命周期](https://developer.android.com/topic/libraries/architecture/coroutines)

最后记住一句话：

> 状态回答“现在是什么”，事件描述“刚才发生了什么”；先弄清语义，再选择 LiveData、StateFlow、SharedFlow 或 Channel。

---

## 附录：原文章整理版

> 说明：以下内容是在尽量保留原文章观点和表达意图的基础上，对标题、段落、重复内容、代码和表格所做的编辑整理。为了便于阅读，少量过于绝对的句子改成了更符合工程实际的表达。关于 SharedFlow 的交付边界、Channel 的多接收者语义以及重要业务结果如何建模，请以前文“文章中几个需要补充的地方”为准。

### 为什么 Android 正在从 LiveData 转向 StateFlow？一篇讲透 LiveData、StateFlow 与 SharedFlow

在 Android 开发中，LiveData、StateFlow 和 SharedFlow 既是常见的面试知识，也是现代 Android 架构的重要组成部分。

很多开发者会使用这些 API，也知道 StateFlow 更“现代”，却未必真正理解一个核心问题：为什么现代 Android 项目越来越倾向于使用 Flow，而不再只依赖 LiveData？

要回答这个问题，需要从生命周期、架构思想、协程生态、Compose、状态管理和一次性事件等角度，理解三者的本质区别。

### 一、三者最核心的定义

#### 1. LiveData

LiveData 是 Android 在早期 MVVM 架构中推出的生命周期感知数据容器。

典型写法：

```kotlin
val liveData = MutableLiveData<String>()

liveData.observe(viewLifecycleOwner) { value ->
    updateUi(value)
}
```

主要特点：

- 自动感知 LifecycleOwner 的生命周期；
- Lifecycle 被销毁后自动移除观察者；
- 页面重新进入活跃状态时可以得到最新值；
- 属于 AndroidX Lifecycle 体系；
- 适合保存并观察 UI 状态。

#### 2. StateFlow

StateFlow 是 Kotlin 协程提供的状态流。

典型写法：

```kotlin
private val _uiState = MutableStateFlow(UiState())
val uiState: StateFlow<UiState> = _uiState.asStateFlow()
```

在传统 View 页面中，可以结合生命周期收集：

```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect { state ->
            updateUi(state)
        }
    }
}
```

主要特点：

- 必须提供初始值；
- 始终持有一个当前状态；
- 新订阅者会立即得到最新状态；
- 属于 Kotlin 协程体系；
- 本身不依赖 Android。

#### 3. SharedFlow

SharedFlow 是 Kotlin 协程提供的共享热流，适合向多个当前订阅者广播数据。

典型写法：

```kotlin
private val _events = MutableSharedFlow<UiEvent>()
val events: SharedFlow<UiEvent> = _events.asSharedFlow()
```

发送：

```kotlin
_events.emit(UiEvent.ShowToast("保存成功"))
```

收集：

```kotlin
events.collect { event ->
    handleEvent(event)
}
```

主要特点：

- 不要求初始值；
- 默认 `replay = 0`，不会向新订阅者重放旧值；
- 支持一对多广播；
- 可以配置 replay、缓冲容量和溢出策略；
- 常用于短暂 UI 效果或实时广播。

### 二、三者的核心区别

| 对比项 | LiveData | StateFlow | SharedFlow |
| --- | --- | --- | --- |
| 本质 | 生命周期感知数据容器 | 状态流 | 共享广播流 |
| 新订阅者获得旧值 | 可获得已经设置的当前值 | 立即获得最新值 | 默认不获得，可配置 replay |
| 是否要求初始值 | 不要求 | 必须 | 不要求 |
| 生命周期感知 | 内置 | 由 UI 侧控制 | 由 UI 侧控制 |
| 是否协程原生 | 否 | 是 | 是 |
| 是否依赖 Android | 是 | 否 | 否 |
| 是否支持 Flow 操作符 | 不能直接完整使用 | 支持 | 支持 |
| 常见场景 | 传统 MVVM、XML 页面 | UI 状态 | 短暂 UI 效果、广播 |

### 三、关于生命周期的常见误区

不少开发者第一次看到 `repeatOnLifecycle` 时会产生疑问：Flow 本身没有生命周期感知，是不是反而退步了？

其实并非如此。LiveData 和 Flow 采用的是两种不同的职责划分方式。

### 四、LiveData 的架构思想

使用 LiveData 时，观察行为直接与 LifecycleOwner 绑定：

```kotlin
liveData.observe(viewLifecycleOwner) { value ->
    updateUi(value)
}
```

LiveData 内部根据生命周期判断观察者是否活跃，并在生命周期被销毁后移除观察者。

这种方式的优点是：

- 使用简单；
- 生命周期处理直观；
- 对 Android 初学者友好。

它的限制也很明显：LiveData 属于 AndroidX Lifecycle 体系，天然带有 Android 平台属性，不适合直接作为普通 Kotlin 或跨平台公共层的数据抽象。

### 五、为什么现代 Android 越来越倾向于 Flow？

根本原因是 Kotlin 协程生态已经形成了一套相对完整的异步编程体系：

- `suspend` 负责一次性异步任务；
- Coroutine 负责结构化并发；
- Flow 负责异步数据流；
- StateFlow 负责状态；
- SharedFlow 负责共享广播；
- Compose 负责响应式 UI。

相比之下，LiveData 不是协程原生类型，在复杂的数据组合场景中不如 Flow 灵活。

例如 Flow 可以自然使用：

```kotlin
flow
    .debounce(300)
    .distinctUntilChanged()
    .combine(otherFlow) { first, second ->
        first to second
    }
    .collectLatest { value ->
        handle(value)
    }
```

这类防抖、去重、组合、切换和取消旧任务的操作，正是现代应用经常需要的数据处理能力。

### 六、StateFlow 的真正含义

StateFlow 不只是“另一个可观察变量”，它的核心定义是：始终持有最新状态的热流。

它非常适合表达 `UI = State` 的思想。例如：

```kotlin
data class UiState(
    val loading: Boolean = false,
    val users: List<User> = emptyList(),
    val error: String? = null,
)
```

页面不再依赖零散的回调决定如何变化，而是根据完整状态进行渲染：

```text
状态发生变化
    ↓
UI 收到新状态
    ↓
UI 根据状态重新渲染
```

这也是 Compose 和 MVI 架构的核心思想之一。

### 七、为什么生命周期被放到了 UI 层？

LiveData 的生命周期控制包含在观察 API 中，而 Flow 只负责数据流。什么时候开始或停止收集，由使用数据的 UI 决定：

```kotlin
repeatOnLifecycle(Lifecycle.State.STARTED) {
    flow.collect { value ->
        updateUi(value)
    }
}
```

本质上，UI 自己决定：

- 什么时候需要数据；
- 什么时候停止接收；
- 需要在哪个生命周期状态下工作。

这是一种职责拆分。

### 八、为什么这种职责拆分更通用？

生命周期属于 Activity、Fragment 或其他 UI 宿主，不属于数据本身。

Flow 的职责是产生和传递数据，UI 的职责是根据自己的生命周期收集数据。两者解耦后，Flow 不必依赖：

- Activity；
- Fragment；
- Android Lifecycle。

因此，同一套 Flow API 可以应用在 Android、普通 JVM、Kotlin Multiplatform、Compose Desktop 等环境中。

### 九、StateFlow 为什么适合状态？

因为 StateFlow 永远持有一个最新值。

例如：

```kotlin
_uiState.value = UserState.Success(users)
```

当页面因为配置变更重新创建并开始收集时，它可以马上获得当前状态，而不是只能等待下一次数据变化。

这正是 UI 状态需要的行为。

### 十、SharedFlow 为什么常被用于短暂 UI 效果？

SharedFlow 默认 `replay = 0`，不会把已经发送的值重放给未来的新订阅者。

例如：

```kotlin
_events.emit(UiEvent.NavigateToHome)
```

如果导航只对当前活跃页面有意义，不希望页面重建后自动重放旧命令，那么 `replay = 0` 的 SharedFlow 可以用于表达这种短暂效果。

不过，这并不等于 SharedFlow 天然保证事件“绝不丢失、只消费一次”。如果发送时没有活跃订阅者，默认配置下事件可能不会被未来订阅者收到。因此，重要业务结果仍然应该进入可恢复状态或数据层。

### 十一、为什么不应该直接用 StateFlow 表达一次性命令？

StateFlow 会向新订阅者提供最新值。如果把导航命令直接当成状态：

```kotlin
_state.value = UiCommand.NavigateToHome
```

新页面重新订阅后，仍可能再次得到同一个命令，从而重复导航。

这说明：

- 状态应该描述“页面当前是什么样”；
- 短暂命令描述“现在执行一次什么”。

实际项目中，应该优先考虑把重要结果建模为状态。确实只属于当前 UI 的短暂效果，可以使用合理配置的 SharedFlow 或其他合适机制。

### 十二、SharedFlow 与 Channel

SharedFlow 更接近广播模型：

```text
一个发送者
    ↓
多个当前订阅者都可以收到同一个值
```

它常用于：

- WebSocket 或 MQTT 消息广播；
- 应用内实时消息；
- 当前页面的 Toast、Snackbar 或动画通知。

Channel 更接近队列模型：

```text
发送一个元素
    ↓
由某一个接收者取得并消费
```

Channel 并不是只能存在一个接收者；它也可以有多个接收者，但同一个元素通常只会交给其中一个接收者，而不是广播给全部接收者。

### 十三、Compose 时代为什么经常使用 StateFlow？

Compose 本身就是状态驱动的响应式 UI。

推荐在 Android Compose 页面中使用生命周期感知的方式收集：

```kotlin
val state by viewModel.uiState.collectAsStateWithLifecycle()
```

整个过程可以概括为：

```text
Flow
  ↓
Compose State
  ↓
Recomposition
  ↓
显示最新 UI
```

StateFlow 和 Compose 的状态模型能够自然衔接，因此它们经常一起使用。

### 十四、现代 Android 的常见架构链路

现代 Android 项目通常会采用类似下面的分层方式：

```text
Compose / View UI
        ↓
    ViewModel
        ↓
Repository / UseCase
        ↓
Room / Retrofit / DataStore
```

层与层之间可以根据数据语义使用：

- `suspend fun`：执行一次并返回结果；
- `Flow<T>`：持续观察变化；
- `StateFlow<UiState>`：向 UI 暴露当前状态。

这样可以让异步处理统一在协程体系中，同时保持清晰的分层和单一数据源。

### 十五、实际项目应该怎样选择？

#### 传统 XML 或 Java 项目

继续使用 LiveData 完全没有问题，尤其是已有稳定架构且没有强烈协程组合需求的项目。

#### Kotlin + 协程项目

可以优先考虑 StateFlow 表达状态，按实际交付语义选择 SharedFlow、Channel 或持久状态表达短暂行为和业务结果。

#### Compose 项目

通常更适合使用 Flow 和 StateFlow，并通过 `collectAsStateWithLifecycle()` 生命周期安全地收集状态。

#### KMP 或跨平台项目

Flow 不依赖 Android UI 类型，更适合作为公共业务层的数据抽象。

### 十六、一句话理解三者

#### LiveData

Android 生命周期感知的可观察状态容器。

#### StateFlow

协程时代始终持有当前值的状态流。

#### SharedFlow

协程时代可配置重放与缓冲策略的共享广播流。

### 十七、真正的架构升级点

从 LiveData 转向 Flow，重点并不是简单地把一个类替换成另一个类，也不是认为“没有内置生命周期就是退步”。

更深层的变化在于：

```text
LiveData：数据观察 API 内置 Android 生命周期控制

Flow：数据流专注数据，UI 组合生命周期控制
```

这代表 Android 架构从平台专用的数据观察方式，逐渐走向协程生态中的组合式数据处理方式。

### 十八、工程实践建议

- UI 状态：优先使用 StateFlow；
- 短暂 UI 效果：根据交付语义考虑 SharedFlow；
- 重要业务结果：进入 UiState 或持久化数据源；
- Java 或传统 XML 项目：LiveData 仍然是合理选择；
- Compose、KMP、协程项目：优先构建统一的 Flow 数据链路；
- 一次性请求：不必强行返回 Flow，使用 `suspend fun` 往往更直接。

最终应该记住：技术选型不是为了追逐“新 API”，而是为了让状态、事件、生命周期和数据职责更加清晰。
