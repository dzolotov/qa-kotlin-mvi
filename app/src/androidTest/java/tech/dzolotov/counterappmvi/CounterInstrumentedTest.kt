package tech.dzolotov.counterappmvi

import android.app.Application
import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.IdlingRegistry
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.kakaocup.kakao.screen.Screen
import io.github.kakaocup.kakao.text.KButton
import io.github.kakaocup.kakao.text.KTextView
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject
import javax.inject.Singleton

@RunWith(AndroidJUnit4::class)
class TestAutomator {

    lateinit var device: UiDevice
    lateinit var packageName: String

    @Before
    fun setup() {
        packageName = BuildConfig.APPLICATION_ID

        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        //ждем запуска Launcher-процесса (для передачи интента)
        val launcherPage = device.launcherPackageName
        device.wait(Until.hasObject(By.pkg(launcherPage).depth(0)), 5000L)
        //создаем контекст (для доступа к сервисам) и запускаем наше приложение
        val context = ApplicationProvider.getApplicationContext<Context>()
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)       //каждый запуск сбрасывает предыдущее состояние
            }
        context.startActivity(launchIntent)
        device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 5000L)
    }

    private fun setDeviceAnimationsValue(uiAutomation: UiAutomation, value: Float) {
        listOf(
            "settings put global animator_duration_scale $value",
            "settings put global transition_animation_scale $value",
            "settings put global window_animation_scale $value"
        ).forEach { command ->
            uiAutomation.executeShellCommand(command).run {
                checkError() // throws IOException on error
                close()
            }
        }
    }

    @Test
    fun testCounterE2E() {
        //отключить анимацию
        setDeviceAnimationsValue(InstrumentationRegistry.getInstrumentation().uiAutomation, 0f)
        //надпись со счетчиком
        val counter = device.findObject(By.res(packageName, "counter"))
        assertEquals("Click below for increment", counter.text)
        //кнопка увеличения счетчика
        val button = device.findObject(By.res(packageName, "increase_button"))
        assertEquals("+", button.text)
        //текст с данными из внешней системы
        val description = device.findObject(By.res(packageName, "description"))
        //при запуске там индикатор загрузки
        assertEquals("Loading", description.text)
        //ждем 2 секунды (до загрузки данных)
        Thread.sleep(2000)
        //проверяем появление строки из внешней системы
        assertEquals("Text from external data source", description.text)
        //проверяем работу счетчика нажатий
        button.click()
        device.wait(Until.hasObject(By.text("Counter: 1")), 1000)
        button.click()
        device.wait(Until.hasObject(By.text("Counter: 2")), 1000)
        //проверяем сохранение состояния и корректность работы после поворота экрана
        device.setOrientationLeft()
        //ссылка на объекты в UiAutomator2 устаревают при пересоздании/изменении Activity, ищем заново
        val button2 = device.findObject(By.res(packageName, "increase_button"))
        val description2 = device.findObject(By.res(packageName, "description"))

        device.wait(Until.hasObject(By.text("Counter: 2")), 1000)
        button2.click()
        device.wait(Until.hasObject(By.text("Counter: 3")), 1000)
        assertEquals("Text from external data source", description2.text)
    }
}

class CustomTestRunner : AndroidJUnitRunner() {

    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}

open class CounterActivityScreen : Screen<CounterActivityScreen>() {
    val counter = KTextView { withId(R.id.counter) }
    val increaseButton = KButton { withId(R.id.increase_button) }
    val description = KTextView { withId(R.id.description) }
}

@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [RepositoryModule::class]
)
@Module
abstract class TestRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindDescription(impl: TestDescriptionRepository): IDescriptionRepository
}

@TestInstallIn(components = [SingletonComponent::class], replaces = [ScopeModule::class])
@Module
object ScopeTestModule {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides
    @BackgroundDispatcherOverride
    @Singleton
    fun provideDispatcher(): CoroutineDispatcher = StandardTestDispatcher()
}

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CounterTest @Inject constructor() {
    @get:Rule
    val rule = ActivityScenarioRule(CounterActivity::class.java)

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var factory: ViewModelProvider.Factory

    @Inject
    @BackgroundDispatcherOverride
    lateinit var backgroundDispatcher: CoroutineDispatcher

    val counterScreen = CounterActivityScreen()

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun checkCounter() {
        val viewModel = factory.create(MainViewModel::class.java)
        IdlingRegistry.getInstance().register(viewModel.store.idlingResource)
        counterScreen {
            counter.hasText("Click below for increment")
            (backgroundDispatcher as TestDispatcher).scheduler.run {
                runCurrent()                        //отправляем интент Intent.LoadingData
                description.hasText("Loading")    //синхронизацию делает IdlingResource
                advanceTimeBy(2000)                            //изменяем виртуальное время
                runCurrent()                                    //отправляем интент DataIsLoaded
                description.hasText("Data from test")
            }
            increaseButton.click()
            counter.hasText("Counter: 1")
            increaseButton.click()
            counter.hasText("Counter: 2")
            increaseButton.click()
            counter.hasText("Counter: 3")
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.setOrientationLeft()
            counter.hasText("Counter: 3")
            increaseButton.click()
            counter.hasText("Counter: 4")
            description.hasText("Data from test")
        }
    }
}


class TestDescriptionRepository @Inject constructor() : IDescriptionRepository {
    override suspend fun getDescription(): String = "Data from test"
}