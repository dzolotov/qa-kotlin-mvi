package tech.dzolotov.counterappmvi

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.test.espresso.IdlingResource
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Provider

sealed class Intent {
    object Increment : Intent()
    object LoadingData : Intent()
    class DataIsLoaded(val data: String) : Intent()
    object DataError : Intent()
    object Load : Intent()
}

data class State(val counter: Int?, val message: DescriptionResult?)

sealed class SideEffect {
    object ShowToast : SideEffect()
}

class FlowObserver<T>(
    lifecycleOwner: LifecycleOwner,
    private val flow: Flow<T>,
    private val collector: suspend (T) -> Unit
) {

    private var job: Job? = null

    init {
        lifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { owner: LifecycleOwner, event: Lifecycle.Event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    job = owner.lifecycleScope.launch {
                        flow.collect { collector(it) }
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    job?.cancel()
                    job = null
                }
                else -> {}
            }
        })
    }
}

inline fun <reified T> Flow<T>.observeOnLifecycle(
    lifecycleOwner: LifecycleOwner,
    noinline collector: suspend (T) -> Unit
) = FlowObserver(lifecycleOwner, this, collector)

interface IStore {
    val sideEffectsFlow: Flow<SideEffect>
    val stateFlow: StateFlow<State>
    val intentsFlow: SharedFlow<Intent>
    suspend fun effect(sideEffect: SideEffect)
    suspend fun dispatch(intent: Intent)
    fun subscribe()
    val idlingResource: CoroutineIdlingResource
    suspend fun wait(action: suspend IStore.() -> Unit)
}

class CoroutineIdlingResource(val resourceName: String) : IdlingResource {

    var counter = 0
    var completableDeferred: CompletableDeferred<Unit> = CompletableDeferred()

    var callback: IdlingResource.ResourceCallback? = null

    override fun getName() = resourceName

    override fun isIdleNow() = counter == 0

    override fun registerIdleTransitionCallback(callback: IdlingResource.ResourceCallback?) {
        this.callback = callback
    }

    fun reset() {
        counter = 0
        completableDeferred = CompletableDeferred()
    }

    fun increment() {
        counter++
    }

    fun decrement() {
        counter--
        if (counter == 0) {
            completableDeferred.complete(Unit)
            callback?.onTransitionToIdle()
        }
    }

    suspend fun wait() = completableDeferred.await()
}

class Store @Inject constructor(
    private val reducer: Provider<IReducer>,
    private val middleware: Provider<IMiddleware>,
    @BackgroundDispatcherOverride private val backgroundDispatcherOverride: CoroutineDispatcher
) : IStore {

    override val idlingResource: CoroutineIdlingResource = CoroutineIdlingResource("store")

    override suspend fun wait(action: suspend IStore.() -> Unit) {
        idlingResource.reset()
        action()
        idlingResource.wait()
    }

    val backgroundScope = CoroutineScope(Dispatchers.Default)

    private val _intentsFlow = MutableSharedFlow<Intent>(replay = 8)
    override val intentsFlow = _intentsFlow.asSharedFlow()

    private val _sideEffectsChannel = Channel<SideEffect>(Channel.BUFFERED)
    override val sideEffectsFlow = _sideEffectsChannel.receiveAsFlow()

    private var _stateFlow: MutableStateFlow<State> = MutableStateFlow(State(null, null))
    override val stateFlow: StateFlow<State> = _stateFlow.asStateFlow()

    override fun subscribe() {
        backgroundScope.launch {
            intentsFlow.collect { intent ->
                _stateFlow.emit(reducer.get().reduce(stateFlow.value, intent))
            }
        }
    }

    override suspend fun effect(sideEffect: SideEffect) {
        _sideEffectsChannel.send(sideEffect)
    }

    override suspend fun dispatch(intent: Intent) {
        idlingResource.increment()
        _intentsFlow.emit(intent)
        CoroutineScope(backgroundDispatcherOverride).launch {
            middleware.get().process(intent)
        }
    }
}

interface IMiddleware {
    suspend fun process(intent: Intent)
}

class Middleware @Inject constructor(
    private val store: IStore,
    private val repository: IDescriptionRepository
) : IMiddleware {

    override suspend fun process(intent: Intent) {
        when (intent) {
            is Intent.Load -> {
                store.dispatch(Intent.LoadingData)
                delay(2000)
                store.dispatch(Intent.DataIsLoaded(repository.getDescription()))
            }
            else -> {}
        }
    }
}

interface IReducer {
    suspend fun reduce(state: State, intent: Intent): State
}

class Reducer @Inject constructor(private val store: IStore) : IReducer {
    override suspend fun reduce(state: State, intent: Intent): State {
        val newState = when (intent) {
            is Intent.Increment -> {
                if (state.counter?.inc() == 3) {
                    store.effect(SideEffect.ShowToast)
                }
                state.copy(counter = state.counter?.inc() ?: 1)
            }
            is Intent.LoadingData -> state.copy(message = DescriptionResult.Loading)
            is Intent.DataIsLoaded -> state.copy(message = DescriptionResult.Success(intent.data))
            is Intent.DataError -> state.copy(message = DescriptionResult.Error)
            else -> state
        }
        return newState
    }
}
