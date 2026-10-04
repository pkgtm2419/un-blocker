package com.unblocker.app.di

import com.unblocker.data.cache.BloomFilterManager
import com.unblocker.data.repository.LogRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import com.unblocker.vpn.service.VpnDependencies

/**
 * App-level composition root.
 *
 * The `:vpn-engine` module declares its dependencies as an interface to avoid
 * a compile-time dependency on the Room/Guava classes in `:data-store`.
 *
 * This module sits at the top of the dependency graph (in `:app`) and wires the
 * `:data-store` concrete implementations into that expected interface.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideVpnDependencies(
        bloomFilterManager: BloomFilterManager,
        logRepository: LogRepository
    ): VpnDependencies {
        return object : VpnDependencies {
            override fun isBlocked(domain: String): Boolean {
                return bloomFilterManager.isBlocked(domain)
            }

            override suspend fun logDnsRequest(domain: String, blocked: Boolean) {
                logRepository.recordDnsRequest(domain, blocked)
            }

            override suspend fun initFilter() {
                bloomFilterManager.reloadFilterFromDatabase()
            }
        }
    }
}
