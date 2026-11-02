package com.unblocker.app.di

import com.unblocker.app.data.repository.DeviceUserRuleGateway
import com.unblocker.app.data.repository.UserRuleGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * App-level composition root.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindUserRuleGateway(impl: DeviceUserRuleGateway): UserRuleGateway
}
