# 本项目 Kotlin 语法逐行导读

这份文档面向 Kotlin 初学者。目标不是完整介绍 Kotlin，而是帮助你读懂 MVI Flow Lab 中真正出现的语法。

配套源码：

- [`MviFlowLabApplication.kt`](app/src/main/java/com/example/mviflowlab/MviFlowLabApplication.kt)
- [`MainActivity.kt`](app/src/main/java/com/example/mviflowlab/MainActivity.kt)
- [`MainScreenViewModel.kt`](app/src/main/java/com/example/mviflowlab/ui/main/MainScreenViewModel.kt)
- [`MainScreen.kt`](app/src/main/java/com/example/mviflowlab/ui/main/MainScreen.kt)
- [`MainScreenViewModelTest.kt`](app/src/test/java/com/example/mviflowlab/ui/main/MainScreenViewModelTest.kt)
- [`MainScreenTest.kt`](app/src/androidTest/java/com/example/mviflowlab/ui/main/MainScreenTest.kt)

建议每读一节，就在 Android Studio 中打开对应文件。看到文档中的代码后，回到源码找到同一段并设置断点。

## 一、先认识 Kotlin 中最常见的符号

| 写法 | 含义 | 本项目示例 |
| --- | --- | --- |
| `val` | 只能赋值一次的引用 | `val uiState` |
| `var` | 可以重新赋值的变量 | 测试中的 `var receivedEffect` |
| `fun` | 声明函数 | `fun onIntent(...)` |
| `:` | 指定类型、实现接口或继承类 | `count: Int`、`: ViewModel()` |
| `?` | 这个类型允许为 null | `String?` |
| `?:` | 左边为 null 时使用右边 | `message ?: "未知错误"` |
| `->` | Lambda 参数分隔，或者 `when` 分支 | `{ state -> ... }` |
| `{ ... }` | 代码块或 Lambda | `launch { ... }` |
| `<T>` | 泛型，说明容器里的数据类型 | `StateFlow<MainUiState>` |
| `.` | 访问对象的属性或函数 | `state.count` |
| `?.` | 对象不为 null 才继续调用 | `errorMessage?.let` |
| `::` | 函数或属性引用 | `viewModel::onIntent` |
| `by` | 属性委托 | `val state by ...` |
| `@` | 注解 | `@Composable`、`@Test` |
| `_` 前缀 | 团队命名习惯：内部可变对象 | `_uiState` |

`_uiState` 中的下划线不是 Kotlin 特殊语法，只是常用命名习惯。本项目用 `_uiState` 表示 ViewModel 内部可以修改的状态，用 `uiState` 表示外部只能读取的状态。

## 二、理解 `val`：引用不变不等于对象内容永远不变

```kotlin
val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
```

`val` 表示 `uiState` 以后不能指向另一个 StateFlow：

```kotlin
// 不允许
uiState = anotherFlow
```

但 StateFlow 内部发布的值仍然可以变化。可以把它理解成：

```text
uiState 这根电线不能换
电线中传递的状态可以不断变化
```

类似地：

```kotlin
val users = mutableListOf<User>()
users.add(user) // 可以修改列表内容
// users = otherList // 不可以让 users 指向另一个列表
```

本项目的 UiState 使用不可变 `data class`，每次都创建新对象，避免到处修改同一个状态对象。

## 三、应用入口：逐行理解 MainActivity

源码位置：[`MainActivity.kt`](app/src/main/java/com/example/mviflowlab/MainActivity.kt)

### 1. package

```kotlin
package com.example.mviflowlab
```

这表示当前类属于 `com.example.mviflowlab` 包。包名主要用于组织代码和避免类名冲突。Android 会先创建 Manifest 中注册的 `MviFlowLabApplication`，随后再启动 `MainActivity`。

### 2. import

```kotlin
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
```

`import` 把其他包中的类或函数引入当前文件。这样后面可以写 `Bundle`，不必每次写完整名称 `android.os.Bundle`。

Kotlin 可以直接导入函数，例如 `setContent`。这类函数经常是扩展函数，看起来像对象自带的方法：

```kotlin
setContent { ... }
```

### 3. 声明 Activity

```kotlin
class MainActivity : ComponentActivity() {
```

拆开理解：

- `class`：声明一个类；
- `MainActivity`：类名；
- `:`：继承或实现；
- `ComponentActivity()`：父类构造函数；
- `{`：类的内容开始。

接近 Java 的写法：

```java
class MainActivity extends ComponentActivity {
}
```

### 4. 重写 onCreate

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
```

- `override`：重写父类已经定义的函数；
- `fun`：声明函数；
- `savedInstanceState`：参数名；
- `Bundle?`：参数类型，问号表示允许为 null；
- 没有写返回类型，表示返回 `Unit`，近似 Java 的 `void`。

```kotlin
super.onCreate(savedInstanceState)
```

`super` 表示父类。Activity 重写 `onCreate()` 时先调用父类实现。

### 5. Compose 页面入口

```kotlin
setContent {
    MviFlowLabTheme {
        Surface {
            MviFlowLabRoute()
        }
    }
}
```

这里连续使用了三个“尾随 Lambda”。以下两种调用基本等价：

```kotlin
setContent(content = {
    MviFlowLabRoute()
})
```

```kotlin
setContent {
    MviFlowLabRoute()
}
```

当函数最后一个参数是函数类型时，Kotlin 允许把 Lambda 写到圆括号外。这种写法在 Compose 中非常常见。

应用启动顺序是：

```text
MainActivity.onCreate()
        ↓
setContent { ... }
        ↓
MviFlowLabRoute()
        ↓
MviFlowLabScreen()
```

## 四、MVI 的操作类型：sealed interface

源码位置：[`MainScreenViewModel.kt`](app/src/main/java/com/example/mviflowlab/ui/main/MainScreenViewModel.kt:21)

```kotlin
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
```

### 1. interface

`interface` 是接口。这里把所有页面操作统一抽象成 `MainIntent`。

### 2. sealed

`sealed` 表示这个类型允许的直接实现是受限制且可知的。编译器知道 `MainIntent` 一共有哪几种情况，因此后面的 `when` 可以检查是否处理完整。

### 3. data object

```kotlin
data object Increment : MainIntent
```

这个 Intent 不携带额外参数，所以整个程序只需要一个 `Increment` 实例，适合用 `object`。

它可以近似理解为一个枚举项，但 sealed interface 比 enum 更灵活，因为某些 Intent 可以携带数据：

```kotlin
data class Search(val keyword: String) : MainIntent
```

本项目暂时没有带参数的 Intent，但以后可以这样扩展。

## 五、页面状态：data class

```kotlin
data class MainUiState(
    val count: Int = 0,
    val isLoading: Boolean = false,
    val users: List<User> = emptyList(),
    val errorMessage: String? = null,
)
```

### 1. 主构造函数

类名后面的圆括号就是主构造函数参数：

```kotlin
MainUiState(count = 3, isLoading = true)
```

### 2. 默认参数

每个属性都有默认值，所以以下调用都合法：

```kotlin
MainUiState()
MainUiState(count = 3)
MainUiState(isLoading = true)
```

没有传入的参数使用默认值。

### 3. 命名参数

```kotlin
MainUiState(count = 3)
```

`count = 3` 是命名参数。它明确说明 3 传给 `count`，可读性比只写 `MainUiState(3)` 更好。

### 4. List<User>

```kotlin
val users: List<User>
```

`List<User>` 是泛型，表示这是一个 User 列表。

```kotlin
emptyList()
```

创建空列表。Kotlin 根据属性类型推断出这里应该是 `List<User>`，因此不必写：

```kotlin
emptyList<User>()
```

### 5. 可空类型

```kotlin
val errorMessage: String? = null
```

`String?` 可以保存 String 或 null。没有问号的 `String` 不允许赋值 null。

### 6. copy()

`data class` 自动生成 `copy()`：

```kotlin
val oldState = MainUiState(count = 2, isLoading = false)
val newState = oldState.copy(count = 3)
```

`newState` 的内容是：

```text
count = 3
isLoading = false
users = 原来的列表
errorMessage = 原来的错误
```

原来的 `oldState` 不会被修改。

## 六、带数据的效果类型：data class

```kotlin
sealed interface MainUiEffect {
    data class ShowSnackbar(val message: String) : MainUiEffect
}
```

`ShowSnackbar` 需要携带一段文字，所以使用 `data class`，而不是 `data object`：

```kotlin
MainUiEffect.ShowSnackbar("保存成功")
MainUiEffect.ShowSnackbar("加载失败")
```

两次创建的对象属于同一种效果类型，但 `message` 不同。

## 七、接口和实现类：UserRepository

```kotlin
interface UserRepository {
    suspend fun loadUsers(shouldFail: Boolean): List<User>
}
```

接口只规定能力，不关心具体怎样获取用户。

- `suspend`：这是挂起函数，需要在协程或其他挂起函数中调用；
- `shouldFail: Boolean`：布尔参数；
- `: List<User>`：函数返回 User 列表。

实现类：

```kotlin
class SampleUserRepository : UserRepository {
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
```

逐行理解：

- `: UserRepository`：实现这个接口；
- `override`：实现接口要求的函数；
- `delay(900)`：挂起 900 毫秒，但不会像 `Thread.sleep()` 那样阻塞线程；
- `if (shouldFail)`：如果要求模拟失败；
- `error(...)`：抛出 `IllegalStateException`；
- `listOf(...)`：创建只读 List；
- `return`：返回用户列表。

## 八、ViewModel 构造函数和依赖注入

```kotlin
class MainScreenViewModel(
    private val repository: UserRepository = SampleUserRepository(),
) : ViewModel() {
```

拆开理解：

- `repository` 是构造参数；
- `private val` 会同时把参数保存成私有属性；
- 属性类型是接口 `UserRepository`；
- 默认值是 `SampleUserRepository()`；
- `: ViewModel()` 表示继承 AndroidX ViewModel。

正常运行时可以使用默认实现：

```kotlin
MainScreenViewModel()
```

测试时可以传入假的 Repository：

```kotlin
MainScreenViewModel(FakeUserRepository())
```

这是一种简单的构造函数依赖注入。

## 九、LiveData、StateFlow、SharedFlow 的可变与只读封装

### 1. LiveData

```kotlin
private val _liveDataCount = MutableLiveData(0)
val liveDataCount: LiveData<Int> = _liveDataCount
```

第一行：

- `private`：只有 ViewModel 内部能访问；
- `MutableLiveData(0)`：创建初始值为 0 的可变 LiveData；
- Kotlin 推断 `_liveDataCount` 的类型为 `MutableLiveData<Int>`。

第二行：

- 对外声明为只读 `LiveData<Int>`；
- 实际对象仍然是同一个 `_liveDataCount`；
- 外部看不到 `setValue()` 等修改能力。

### 2. StateFlow

```kotlin
private val _uiState = MutableStateFlow(MainUiState())
val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
```

- `MainUiState()`：创建默认初始状态；
- `MutableStateFlow(...)`：创建可修改状态流；
- `asStateFlow()`：以只读 StateFlow 形式对外暴露。

### 3. SharedFlow

```kotlin
private val _effects =
    MutableSharedFlow<MainUiEffect>(extraBufferCapacity = 1)
val effects: SharedFlow<MainUiEffect> = _effects.asSharedFlow()
```

这里显式写了 `<MainUiEffect>`，告诉编译器这个流只能发送 MainUiEffect。

`extraBufferCapacity = 1` 使用命名参数，表示在存在订阅者但订阅者暂时来不及处理时，允许额外缓冲一个效果。它不等于为未来订阅者保存一个事件；`replay` 仍然是默认的 0。

## 十、when：集中处理所有 Intent

```kotlin
fun onIntent(intent: MainIntent) {
    when (intent) {
        MainIntent.Increment -> ...
        MainIntent.Decrement -> ...
        MainIntent.Reset -> ...
    }
}
```

`when` 类似更强大的 Java `switch`。

因为 `MainIntent` 是 sealed interface，编译器知道所有分支。全部处理后，不需要写 `else`。

### 单行分支

```kotlin
MainIntent.ResetLiveData -> _liveDataCount.value = 0
```

箭头左边是条件，右边是要执行的表达式。

### 多行分支

```kotlin
MainIntent.Reset -> {
    _uiState.value = MainUiState()
    sendEffect(MainUiEffect.ShowSnackbar("StateFlow 状态已重置"))
}
```

需要执行多行时使用 `{ ... }`。

## 十一、Elvis 操作符 `?:`

```kotlin
_liveDataCount.value = (_liveDataCount.value ?: 0) + 1
```

LiveData 的 `value` 类型可能是 `Int?`。这行代码表示：

```text
读取当前值
如果当前值不是 null，就使用当前值
如果当前值是 null，就使用 0
最后加 1
```

展开后：

```kotlin
val currentValue = _liveDataCount.value
val safeValue = if (currentValue != null) currentValue else 0
_liveDataCount.value = safeValue + 1
```

Java 风格近似写法：

```java
Integer currentValue = liveData.getValue();
liveData.setValue((currentValue != null ? currentValue : 0) + 1);
```

## 十二、最关键的 Lambda：update、it 和 copy

```kotlin
_uiState.update { it.copy(count = it.count + 1) }
```

### 1. update 接收一个函数

`update()` 需要你告诉它：根据旧状态怎样生成新状态。

### 2. it 是默认参数名

当 Lambda 只有一个参数时，可以省略参数声明，使用默认名字 `it`。

原写法：

```kotlin
_uiState.update { it.copy(count = it.count + 1) }
```

写出参数名：

```kotlin
_uiState.update { oldState ->
    oldState.copy(count = oldState.count + 1)
}
```

完全展开：

```kotlin
_uiState.update { oldState ->
    val newCount = oldState.count + 1
    val newState = oldState.copy(count = newCount)
    newState
}
```

Lambda 最后一行自动作为返回值，所以不写 `return`。

## 十三、提前返回

```kotlin
if (_uiState.value.isLoading) return
```

如果页面已经在加载，就直接结束 `loadUsers()`，避免重复请求。

展开：

```kotlin
if (_uiState.value.isLoading) {
    return
}
```

Kotlin 只有一行时允许省略花括号，但初学阶段也可以主动写出来。

## 十四、协程：viewModelScope.launch

```kotlin
viewModelScope.launch {
    // 异步流程
}
```

- `viewModelScope`：属于当前 ViewModel 的 CoroutineScope；
- `launch`：启动一个新协程；
- `{ ... }`：协程中执行的代码；
- ViewModel 被清除时，scope 中的任务会被取消。

可以先把它理解成：

```text
启动一个由 ViewModel 管理的异步任务
```

不要把它简单等同于“创建新线程”。协程可以挂起和恢复，具体在哪个线程执行由 CoroutineDispatcher 决定。

## 十五、try、catch 和协程取消

```kotlin
try {
    val users = repository.loadUsers(shouldFail)
    _uiState.update { it.copy(isLoading = false, users = users) }
} catch (error: Throwable) {
    if (error is CancellationException) throw error
    _uiState.update {
        it.copy(
            isLoading = false,
            errorMessage = error.message ?: "未知错误",
        )
    }
}
```

### 1. 类型判断 `is`

```kotlin
error is CancellationException
```

判断 `error` 是否属于 CancellationException，类似 Java `instanceof`。

### 2. throw

```kotlin
throw error
```

重新抛出异常。协程取消用 CancellationException 表达，不能把取消误当成普通加载失败。

### 3. error.message ?: "未知错误"

`Throwable.message` 可能是 null，因此用 Elvis 操作符提供默认文字。

## 十六、字符串模板 `$` 和 `${}`

```kotlin
"加载成功：${users.size} 位用户"
```

`${users.size}` 会把表达式计算结果插入字符串。

简单变量可以省略花括号：

```kotlin
val name = "Ada"
Text("你好，$name")
```

复杂表达式需要花括号：

```kotlin
"共有 ${users.size} 人"
```

## 十七、tryEmit 的返回值为什么没有使用？

```kotlin
private fun sendEffect(effect: MainUiEffect) {
    _effects.tryEmit(effect)
}
```

`tryEmit()` 返回 Boolean，表示是否成功发出或接受该值。本 Demo 没有使用返回值，因为 Snackbar 是尽力交付的短暂效果。

如果业务要求必须确认是否发送成功，就不能不加思考地忽略返回值，更不能把 SharedFlow 当作可靠业务队列。

## 十八、Compose 函数：@Composable

源码位置：[`MainScreen.kt`](app/src/main/java/com/example/mviflowlab/ui/main/MainScreen.kt)

```kotlin
@Composable
fun MviFlowLabRoute(...) {
}
```

`@Composable` 是注解，告诉 Compose 编译器：这个函数用于描述 UI，可以调用其他 Composable 函数。

普通函数不能直接调用 `Text()`、`Button()` 等 Composable：

```kotlin
fun normalFunction() {
    // Text("Hello") // 不允许
}
```

Composable 函数不是返回一个 View 对象，而是描述在当前状态下 UI 应该是什么样。

## 十九、函数参数、默认值和函数类型

```kotlin
fun MviFlowLabRoute(
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel(),
)
```

两个参数都有默认值，所以调用方可以直接写：

```kotlin
MviFlowLabRoute()
```

也可以覆盖默认值：

```kotlin
MviFlowLabRoute(modifier = Modifier.padding(16.dp))
```

页面函数中的回调类型：

```kotlin
onIntent: (MainIntent) -> Unit
```

它表示 `onIntent` 是一个函数：

- 接收一个 MainIntent；
- 返回 Unit；
- Unit 类似 Java `void`。

调用：

```kotlin
onIntent(MainIntent.Increment)
```

## 二十、属性委托 `by`

```kotlin
val state by viewModel.uiState.collectAsStateWithLifecycle()
```

`collectAsStateWithLifecycle()` 返回的不是直接的 `MainUiState`，而是 Compose 的 `State<MainUiState>`。

不用 `by` 时：

```kotlin
val stateHolder = viewModel.uiState.collectAsStateWithLifecycle()
val state = stateHolder.value
```

使用 `by` 后：

```kotlin
val state by viewModel.uiState.collectAsStateWithLifecycle()
```

以后读取 `state` 时，委托会帮你读取 `State.value`。Compose 也会记录哪些 Composable 读取了这个值，以便状态变化时触发重组。

LiveData 同理：

```kotlin
val liveDataCount by viewModel.liveDataCount.observeAsState(initial = 0)
```

`initial = 0` 是命名参数，用于 LiveData 尚未提供值时的初始显示。

## 二十一、remember

```kotlin
val snackbarHostState = remember { SnackbarHostState() }
```

如果直接写：

```kotlin
val snackbarHostState = SnackbarHostState()
```

Composable 每次重组时都可能创建新对象。`remember` 会在当前组合位置记住这个对象：

```text
第一次执行：创建 SnackbarHostState
之后重组：复用之前的 SnackbarHostState
离开组合：这个记忆被释放
```

`remember` 不能保证进程死亡或页面彻底重建后恢复数据。需要长期恢复的数据应使用 ViewModel、`rememberSaveable`、SavedStateHandle 或持久化存储。

## 二十二、LaunchedEffect 和 repeatOnLifecycle

```kotlin
LaunchedEffect(viewModel, lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.effects.collect { effect ->
            // 处理效果
        }
    }
}
```

### LaunchedEffect

在 Composable 进入组合时启动协程。`viewModel` 或 `lifecycleOwner` 改变时，旧协程取消并启动新协程；离开组合时协程取消。

### repeatOnLifecycle

- Lifecycle 达到 STARTED 时运行内部代码；
- 低于 STARTED 时取消内部收集；
- 再次达到 STARTED 时重新启动收集。

### collect Lambda

```kotlin
viewModel.effects.collect { effect ->
    ...
}
```

每收到一个 effect，就执行一次 Lambda。这里主动把默认 `it` 命名为 `effect`，可读性更好。

## 二十三、when 中的类型判断和智能转换

```kotlin
when (effect) {
    is MainUiEffect.ShowSnackbar ->
        snackbarHostState.showSnackbar(effect.message)
}
```

`is MainUiEffect.ShowSnackbar` 判断具体类型。

进入这个分支后，编译器自动知道 `effect` 是 ShowSnackbar，所以可以直接访问：

```kotlin
effect.message
```

这叫智能类型转换（smart cast），不需要像 Java 那样手动强制转换。

## 二十四、函数引用 `::`

```kotlin
onIntent = viewModel::onIntent
```

`::` 取得函数引用，把 ViewModel 的 `onIntent` 函数本身作为参数传给 UI。

近似展开：

```kotlin
onIntent = { intent ->
    viewModel.onIntent(intent)
}
```

注意区别：

```kotlin
viewModel.onIntent(intent) // 现在就调用函数
viewModel::onIntent       // 把函数作为值传递
```

## 二十五、Modifier 链式调用

```kotlin
modifier
    .fillMaxSize()
    .safeDrawingPadding()
```

`Modifier` 用来描述尺寸、间距、背景、点击等 UI 修饰。

多数 Modifier 函数返回一个新的 Modifier，因此可以继续调用下一个函数。顺序可能影响结果：

```kotlin
Modifier
    .background(color)
    .padding(16.dp)
```

和：

```kotlin
Modifier
    .padding(16.dp)
    .background(color)
```

背景覆盖的区域可能不同。

## 二十六、Scaffold 和尾随 Lambda

```kotlin
Scaffold(
    topBar = { TopAppBar(...) },
    snackbarHost = { SnackbarHost(snackbarHostState) },
) { contentPadding ->
    LazyColumn(
        modifier = Modifier.padding(contentPadding),
    ) {
        // 页面内容
    }
}
```

`Scaffold` 接收多个函数参数：

- `topBar`：如何绘制顶部栏；
- `snackbarHost`：如何显示 Snackbar；
- 最后的尾随 Lambda：主体内容。

主体 Lambda 会收到 `contentPadding`，避免内容被 TopAppBar 等区域遮挡。

## 二十七、LazyColumn 的 DSL

```kotlin
LazyColumn {
    item { LiveDataCard(...) }
    item { StateFlowCard(...) }
    items(state.users, key = User::id) { user ->
        UserRow(user)
    }
}
```

这是一种 Kotlin DSL 写法。

- `item { ... }`：增加单个列表项；
- `items(state.users)`：根据用户列表增加多个列表项；
- `key = User::id`：使用 User 的 id 属性作为稳定键；
- `{ user -> UserRow(user) }`：每个用户如何绘制。

`User::id` 是属性引用，接近：

```kotlin
key = { user -> user.id }
```

## 二十八、if 也是表达式

```kotlin
containerColor = if (accent) {
    MaterialTheme.colorScheme.secondaryContainer
} else {
    MaterialTheme.colorScheme.surfaceContainer
}
```

Kotlin 的 `if` 可以产生值，所以可以直接放在赋值右边。

近似展开：

```kotlin
val color: Color
if (accent) {
    color = MaterialTheme.colorScheme.secondaryContainer
} else {
    color = MaterialTheme.colorScheme.surfaceContainer
}
```

## 二十九、安全调用 `?.` 和 let

```kotlin
state.errorMessage?.let {
    Text(text = it)
}
```

这表示：只有 `errorMessage` 不为 null 时，才执行 `let` 中的代码。

展开：

```kotlin
val message = state.errorMessage
if (message != null) {
    Text(text = message)
}
```

这里 Lambda 的 `it` 就是非 null 的错误文字。

初学时如果觉得 `?.let` 难读，完全可以先写成普通 `if`。清晰比追求简短更重要。

## 三十、逻辑取反 `!`

```kotlin
enabled = !state.isLoading
```

`!` 表示逻辑取反：

```text
isLoading = true  → enabled = false
isLoading = false → enabled = true
```

因此加载期间按钮不可点击。

## 三十一、高阶 Composable：最难的一行

```kotlin
private fun LearningCard(
    title: String,
    description: String,
    accent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
)
```

最难的是：

```kotlin
content: @Composable ColumnScope.() -> Unit
```

从右向左理解：

- `Unit`：不返回业务数据；
- `() -> Unit`：这是一个无普通参数的函数；
- `ColumnScope.()`：执行时拥有 ColumnScope 接收者；
- `@Composable`：这个函数可以绘制 Compose UI；
- `content`：这个函数参数的名字。

通俗理解：调用者可以把一段“放在 Column 里面的 UI”传给 LearningCard。

调用：

```kotlin
LearningCard(
    title = "标题",
    description = "说明",
) {
    Text("自定义内容")
    Button(onClick = {}) {
        Text("按钮")
    }
}
```

LearningCard 内部执行：

```kotlin
Column {
    Text(title)
    Text(description)
    content()
}
```

这就是 Compose 大量使用的“插槽 API”：组件提供固定结构，同时允许调用者填入一部分自定义内容。

## 三十二、Preview 注解

```kotlin
@Preview(showBackground = true, widthDp = 390, heightDp = 860)
@Composable
private fun MviFlowLabPreview() {
    ...
}
```

- `@Preview`：让 Android Studio 尝试预览 Composable；
- 注解参数使用命名参数；
- `private`：只在当前文件中使用；
- Preview 手动传入假状态，不依赖真实 ViewModel。

```kotlin
onIntent = {}
```

这是一个什么都不做的 Lambda。Preview 中不需要真的处理按钮点击。

## 三十三、测试函数的简写

源码位置：[`MainScreenViewModelTest.kt`](app/src/test/java/com/example/mviflowlab/ui/main/MainScreenViewModelTest.kt)

```kotlin
@Test
fun increment_reducesIntentIntoState() = runTest {
    ...
}
```

普通函数体写法：

```kotlin
fun testSomething() {
    runTest {
        // 测试
    }
}
```

单表达式函数写法：

```kotlin
fun testSomething() = runTest {
    // 测试
}
```

等号表示整个函数的结果就是右边表达式的结果。

## 三十四、测试中的 Rule

```kotlin
@get:Rule
val instantTaskExecutorRule = InstantTaskExecutorRule()
```

`@get:Rule` 把 JUnit Rule 注解放到 Kotlin 属性生成的 getter 上。

`InstantTaskExecutorRule` 让 LiveData 等 Architecture Components 在本地 JVM 测试中同步执行。

```kotlin
@get:Rule
val mainDispatcherRule = MainDispatcherRule()
```

它把协程 Main Dispatcher 替换成测试 Dispatcher，避免本地 JVM 测试没有 Android 主线程的问题。

## 三十五、Fake 比真实网络更适合单元测试

```kotlin
private class FakeUserRepository : UserRepository {
    override suspend fun loadUsers(shouldFail: Boolean): List<User> {
        if (shouldFail) error("测试失败")
        return listOf(User(7, "Test", "Tester"))
    }
}
```

Fake 实现同一个接口，但行为简单、固定且可控制：

- 不访问网络；
- 不等待 900ms；
- 成功时永远返回固定用户；
- 失败时永远抛出固定错误。

这样测试只验证 ViewModel，不受外部系统影响。

## 三十六、SharedFlow 测试中的异步语法

```kotlin
var receivedEffect: MainUiEffect? = null

val collection = backgroundScope.launch(
    UnconfinedTestDispatcher(testScheduler)
) {
    receivedEffect = viewModel.effects.first()
}
```

- `var`：稍后会修改；
- `MainUiEffect?`：初始时还没有效果，所以允许 null；
- `backgroundScope.launch`：启动测试后台协程；
- `first()`：等待 Flow 的第一个值，然后结束这次收集；
- 必须先启动收集，再触发 `replay = 0` 的 SharedFlow。

```kotlin
viewModel.onIntent(MainIntent.LoadUsers)
advanceUntilIdle()
```

`advanceUntilIdle()` 推进测试协程调度器，直到已安排的任务执行完成。

## 三十七、Compose UI 测试中的 mutableStateOf

源码位置：[`MainScreenTest.kt`](app/src/androidTest/java/com/example/mviflowlab/ui/main/MainScreenTest.kt)

```kotlin
var state by remember {
    mutableStateOf(MainUiState(count = 2))
}
```

- `mutableStateOf(...)`：创建可修改的 Compose State；
- `remember`：重组时保留它；
- `var ... by`：允许直接读取和重新赋值状态值。

点击回调：

```kotlin
onIntent = {
    if (it == MainIntent.Increment) {
        state = state.copy(count = state.count + 1)
    }
}
```

这里 `it` 是收到的 MainIntent。更新 `state` 后 Compose 重组，页面从 2 显示为 3。

测试操作：

```kotlin
composeTestRule.onNodeWithText("+1").performClick()
composeTestRule.onNodeWithText("3").assertTextEquals("3")
```

可以像读句子一样理解：

```text
找到文字为 +1 的节点并点击
找到文字为 3 的节点并断言它的文字就是 3
```

## 三十八、build.gradle.kts 也是 Kotlin

源码位置：[`app/build.gradle.kts`](app/build.gradle.kts)

扩展名 `.kts` 表示 Kotlin Script。

```kotlin
android {
    compileSdk = 36
}
```

这是 Gradle 提供的 Kotlin DSL。`android { ... }` 看起来像语言关键字，实际上是 Gradle 插件提供的 API。

```kotlin
dependencies {
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
}
```

- `implementation`：应用运行和编译需要的依赖；
- `testImplementation`：本地单元测试需要的依赖；
- `androidTestImplementation`：设备或模拟器测试需要的依赖；
- `debugImplementation`：只在 debug 构建中需要。

```kotlin
val composeBom = platform(libs.androidx.compose.bom)
```

声明一个局部只读变量。Compose BOM 统一管理一组 Compose 库的兼容版本。

## 三十九、四条完整代码路径

### 1. LiveData +1

```text
LiveDataCard 中点击按钮
        ↓
onIntent(MainIntent.IncrementLiveData)
        ↓
_liveDataCount.value 加 1
        ↓
observeAsState() 收到值
        ↓
LiveDataCard 重新显示数字
```

### 2. StateFlow +1

```text
StateFlowCard 中点击按钮
        ↓
onIntent(MainIntent.Increment)
        ↓
_uiState.update { oldState.copy(...) }
        ↓
collectAsStateWithLifecycle() 收到新状态
        ↓
StateFlowCard 重新显示数字
```

### 3. 发送 Snackbar

```text
SharedFlowCard 中点击按钮
        ↓
onIntent(MainIntent.ShowOneShotMessage)
        ↓
_effects.tryEmit(ShowSnackbar(...))
        ↓
repeatOnLifecycle 中的 collect 收到效果
        ↓
SnackbarHostState.showSnackbar()
```

### 4. 加载用户

```text
UserLoadingCard 中点击加载
        ↓
onIntent(MainIntent.LoadUsers)
        ↓
viewModelScope.launch
        ↓
isLoading = true
        ↓
repository.loadUsers()
        ↓
成功：users 更新 + Snackbar
失败：errorMessage 更新
```

## 四十、初学者最容易误解的十件事

1. `val` 不是“所有内容都永远不变”，只是这个引用不能重新赋值。
2. `_uiState` 的下划线只是命名约定，不是特殊操作符。
3. `it` 只是单参数 Lambda 的默认参数名。
4. `by` 在这里帮你自动读取 Compose State 的 `value`。
5. `::` 传递的是函数本身，不是立刻调用函数。
6. `copy()` 创建新 data class 对象，不修改旧对象。
7. `launch` 启动协程，不等于一定创建新线程。
8. `remember` 只在当前组合范围内记住对象，不等于永久保存。
9. Composable 重组不等于整个 Activity 被重新创建。
10. `SharedFlow(replay = 0)` 不等于事件绝对不会丢失。

## 四十一、推荐的断点位置

在 Android Studio 中设置断点：

1. `MainActivity.onCreate()`：观察应用入口；
2. `MainScreenViewModel.onIntent()`：观察每次用户操作；
3. `_uiState.update`：观察旧状态和新状态；
4. `repository.loadUsers()`：观察挂起和返回；
5. `effects.collect`：观察 SharedFlow 效果；
6. `MviFlowLabScreen()`：观察 Compose 重组时参数怎样变化。

重点观察这些变量：

- `intent`
- `_uiState.value`
- `oldState` 或 `it`
- `users`
- `error`
- `effect`

## 四十二、动手改代码练习

### 练习 1：给 StateFlow 增加“+5”

需要修改：

1. `MainIntent` 增加 `IncrementByFive`；
2. `onIntent()` 增加 when 分支；
3. UI 增加按钮；
4. 单元测试断言 count 增加 5。

### 练习 2：让加载按钮携带用户数量

把无参数 Intent：

```kotlin
data object LoadUsers : MainIntent
```

改成带参数 data class：

```kotlin
data class LoadUsers(val count: Int) : MainIntent
```

然后在 `when` 分支中通过 `intent.count` 读取参数。

### 练习 3：把 `?.let` 改成普通 if

把：

```kotlin
state.errorMessage?.let {
    Text(it)
}
```

改成：

```kotlin
val message = state.errorMessage
if (message != null) {
    Text(message)
}
```

两种写法行为相同。这个练习有助于理解空安全和 Lambda。

### 练习 4：手动展开函数引用

把：

```kotlin
onIntent = viewModel::onIntent
```

改成：

```kotlin
onIntent = { intent ->
    viewModel.onIntent(intent)
}
```

运行应用确认行为没有变化。

## 四十三、阅读检查表

如果你能用自己的话回答下面的问题，就已经能够读懂本项目的大部分代码：

- `val` 和 `var` 有什么区别？
- `String?` 为什么可以是 null？
- `?:` 在 LiveData 计数中做了什么？
- `data class` 为什么适合 UiState？
- `sealed interface` 为什么适合 Intent？
- `_uiState.update { it.copy(...) }` 中的 it 是谁？
- `viewModelScope.launch` 为什么用于异步加载？
- `val state by ...` 中的 by 做了什么？
- `viewModel::onIntent` 和 `viewModel.onIntent(...)` 有什么区别？
- `state.errorMessage?.let` 什么时候执行？
- `content: @Composable ColumnScope.() -> Unit` 为什么允许 LearningCard 插入自定义 UI？
- FakeUserRepository 为什么让测试更稳定？

## 四十四、下一步学习顺序

建议按下面的顺序继续学习，不必一次全部掌握：

1. Kotlin 基础：变量、函数、类、接口、可空类型；
2. Kotlin 常用表达式：when、Lambda、`it`、`?.`、`?:`；
3. data class、sealed class/interface、泛型；
4. 高阶函数、扩展函数、属性委托；
5. 协程：suspend、CoroutineScope、launch、取消；
6. Flow：冷流、热流、collect、StateFlow、SharedFlow；
7. Compose：状态、重组、remember、Effect；
8. MVI：Intent、Reducer/状态更新、UiState、UiEffect；
9. 测试：Fake、runTest、Flow 测试、Compose UI 测试。

学习时始终回到这个 Demo 做一次小修改。能够运行、观察和测试的代码，比单独记忆语法更容易形成长期理解。
