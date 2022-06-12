package tech.dzolotov.counterappmvi

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.spyk
import junit.framework.TestCase.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import javax.inject.Provider

class CounterStoreTest {
    private lateinit var repository: IDescriptionRepository

    @Before
    fun setup() {
        repository = mockk()
        coEvery { repository.getDescription() } returns "Message from test"
    }

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalStdlibApi::class)
    @Test
    fun checkReducer() = runTest {
        lateinit var store: IStore
        //здесь мы можем ссылаться на lateinit переменную, потому что при первом обращении к reducer/
        //middleware значение в store уже будет
        val reducer = Provider<IReducer> { Reducer(store) }
        val middleware = Provider<IMiddleware> { Middleware(store, repository) }
        store = Store(reducer, middleware, this.coroutineContext[CoroutineDispatcher]!!)
        store.subscribe()
        val stateFlow = store.stateFlow
        //workaround for idling resource synchronization
        val viewMockCoroutine = launch {
            stateFlow.collect {
                store.idlingResource.decrement()
            }
        }

        assertNull(stateFlow.value.counter)
        assertNull(stateFlow.value.message)

        store.wait {
            dispatch(Intent.Increment)
        }
        assertEquals(1, stateFlow.value.counter)

        store.wait {
            dispatch(Intent.Increment)
        }
        assertEquals(2, stateFlow.value.counter)

        store.wait {
            dispatch(Intent.Increment)
        }
        assertEquals(3, stateFlow.value.counter)

        store.sideEffectsFlow.test {
            assertEquals(SideEffect.ShowToast, awaitItem())
        }
        launch {
            store.dispatch(Intent.Load)
        }
        runCurrent()

        assertEquals(DescriptionResult.Loading, stateFlow.value.message)
        advanceTimeBy(2000)
        store.wait {
            runCurrent()
        }
        assertEquals(DescriptionResult.Success("Message from test"), stateFlow.value.message)
        viewMockCoroutine.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun checkReducerWithStateMock() = runTest {
        lateinit var store: IStore
        val reducer = Provider<IReducer> { Reducer(store) }
        val middleware = Provider<IMiddleware> { Middleware(store, repository) }
        store = spyk(Store(reducer, middleware, Dispatchers.Default))
        store.subscribe()

        coEvery { store.effect(any()) } returns Unit

        store.dispatch(Intent.Increment)
        store.dispatch(Intent.Increment)
        store.dispatch(Intent.Increment)
        runCurrent()
        coVerify(exactly = 1) { store.effect(SideEffect.ShowToast) }

        //запускаем действие в контексте StandardTestDispatcher
        launch {
            store.dispatch(Intent.Load)
        }
        //выполняем действия до delay
        runCurrent()
        store.intentsFlow.test {
            //проверяем последовательность событий
            //сначала было 3 инкремента (можно их пропустить)
            assertEquals(Intent.Increment, awaitItem())
            assertEquals(Intent.Increment, awaitItem())
            assertEquals(Intent.Increment, awaitItem())
            //затем исходное действие
            assertEquals(Intent.Load, awaitItem())
            //и результат его преобразования в Middleware
            assertEquals(Intent.LoadingData, awaitItem())
            advanceTimeBy(2000)
            runCurrent()
            //после delay должны получить Intent DataIsLoaded
            val item = awaitItem()
            assertTrue(item is Intent.DataIsLoaded)
            assertEquals("Message from test", (item as Intent.DataIsLoaded).data)
        }
    }
}
