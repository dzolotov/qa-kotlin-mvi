package tech.dzolotov.counterappmvi

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.Binds
import dagger.Module
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import javax.inject.Inject
import javax.inject.Singleton

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

class TestDescriptionRepository @Inject constructor() : IDescriptionRepository {
    override suspend fun getDescription(): String = "Data from test"
}

@HiltAndroidTest
@Config(
    application = HiltTestApplication::class,
    instrumentedPackages = ["androidx.loader.content"]
)
@RunWith(AndroidJUnit4::class)
class RobolectricTest {
    @get:Rule(order = 1)
    var scenario = ActivityScenarioRule(CounterActivity::class.java)

    @get:Rule(order = 0)
    var hiltRule = HiltAndroidRule(this)

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun testCounter() {
        scenario.scenario.onActivity {
            println(it.viewModel.store)
            IdlingRegistry.getInstance().register(it.viewModel.store.idlingResource)
            val increase_button = onView(withId(R.id.increase_button))
            onView(withId(R.id.description)).apply {
                check(matches(isDisplayed()))
                check(matches(withText("Loading")))
            }
            onView(withId(R.id.counter)).apply {
                check(matches(isDisplayed()))
                check(matches(withText("Click below for increment")))
                increase_button.perform(click())
                check(matches(withText("Counter: 1")))
                increase_button.perform(click())
                check(matches(withText("Counter: 2")))
                increase_button.perform(click())
                check(matches(withText("Counter: 3")))
            }
            assertEquals("Counter = 3", ShadowToast.getTextOfLatestToast())
            Thread.sleep(2000)
            onView(withId(R.id.description)).check(matches(withText("Data from test")))
        }
    }
}