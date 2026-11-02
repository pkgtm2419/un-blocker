package com.unblocker.app.di

import android.content.Context
import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.data.repository.DeviceUserRuleGateway
import com.unblocker.app.data.repository.UserRuleGateway
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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

    companion object {
        @Provides
        @Singleton
        fun provideFilteringPreferences(@ApplicationContext context: Context): FilteringPreferences =
            FilteringPreferences.getInstance(context)
    }
}
