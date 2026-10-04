package com.unblocker.vpn.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt module for the `:vpn-engine` module.
 *
 * Note: The [isBlocked] and [logDnsRequest] lambdas injected into [UnblockerVpnService] are
 * provided by the `:app` module's [AppModule], which bridges the `:data-store` Bloom Filter
 * and Room repository into the VPN service's dependency graph.
 *
 * This module is intentionally minimal — the VPN engine's design goal is to remain decoupled
 * from data-store internals at compile time. The app module acts as the composition root.
 */
@Module
@InstallIn(SingletonComponent::class)
object VpnEngineModule {
    // Bindings provided here in the future:
    //  - DnsForwarder (if we need to inject upstream servers from settings)
    //  - VpnConfiguration (if server list becomes user-configurable)
}
