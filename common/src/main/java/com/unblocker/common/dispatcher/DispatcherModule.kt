package com.unblocker.common.dispatcher

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt bindings for the application coroutine dispatchers.
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    /**
     * Provides the shared dispatcher wrapper.
     *
     * @return Dispatchers backed by the kotlinx.coroutines defaults.
     */
    @Provides
    @Singleton
    fun provideCoroutineDispatchers(): CoroutineDispatchers = CoroutineDispatchers()
}
