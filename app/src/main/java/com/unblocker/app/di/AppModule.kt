package com.unblocker.app.di

import com.unblocker.data.cache.BloomFilterManager
import com.unblocker.data.repository.LogRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * App-level composition root.
 *
 * The `:vpn-engine` module declares its dependencies as raw Kotlin lambdas to avoid
 * a compile-time dependency on the Room/Guava classes in `:data-store`.
 *
 * This module sits at the top of the dependency graph (in `:app`) and wires the
 * `:data-store` concrete implementations into those expected lambdas.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provides the non-blocking Bloom Filter check lambda expected by `UnblockerVpnService`.
     */
    @Provides
    @Singleton
    fun provideIsBlockedLambda(
        bloomFilterManager: BloomFilterManager
    ): (String) -> Boolean {
        return { domain -> bloomFilterManager.isBlocked(domain) }
    }

    /**
     * Provides the fire-and-forget DB logging lambda expected by `UnblockerVpnService`.
     */
    @Provides
    @Singleton
    fun provideLogDnsRequestLambda(
        logRepository: LogRepository
    ): suspend (String, Boolean) -> Unit {
        return { domain, isBlocked -> 
            logRepository.recordDnsRequest(domain, isBlocked)
        }
    }

    /**
     * Provides the initialization lambda to load the Bloom filter when the VPN starts.
     */
    @Provides
    @Singleton
    fun provideInitFilterLambda(
        bloomFilterManager: BloomFilterManager
    ): suspend () -> Unit {
        return {
            bloomFilterManager.reloadFilterFromDatabase()
        }
    }
}
