package com.unblocker.vpn.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Reserved Hilt module for library VPN bindings.
 *
 * The object currently declares no providers. The active app composes its own filtering and
 * resolver dependencies; this declaration does not prove library forwarding is on that path.
 */
@Module
@InstallIn(SingletonComponent::class)
object VpnEngineModule {
    // Bindings provided here in the future:
    //  - DnsForwarder (if we need to inject upstream servers from settings)
    //  - VpnConfiguration (if server list becomes user-configurable)
}
