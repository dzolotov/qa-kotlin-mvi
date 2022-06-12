package tech.dzolotov.counterappmvi

import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

//@Qualifier
//annotation class CoroutineDispatcherOverride

@Qualifier
annotation class BackgroundDispatcherOverride

@InstallIn(SingletonComponent::class)
@Module
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindDescription(impl: DescriptionRepository): IDescriptionRepository
}

@InstallIn(SingletonComponent::class)
@Module
abstract class ViewModelModule {
    @Binds
    @Singleton
    abstract fun bindFactory(factory: ViewModelFactory): ViewModelProvider.Factory
}

@InstallIn(SingletonComponent::class)
@Module
object ScopeModule {
    @Provides
    @Singleton
    @BackgroundDispatcherOverride
    fun provideDispatcher(): CoroutineDispatcher = Dispatchers.Default
}

@InstallIn(SingletonComponent::class)
@Module
abstract class MVIModule {
    @Binds
    @Singleton
    abstract fun bindStore(store: Store): IStore

    @Binds
    @Singleton
    abstract fun bindMiddleware(middleware: Middleware): IMiddleware

    @Binds
    @Singleton
    abstract fun bindReducer(reducer: Reducer): IReducer
}