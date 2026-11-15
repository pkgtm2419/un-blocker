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
    /**
     * Binds the device-backed gateway for the application lifetime.
     *
     * @param impl Injected device rule gateway.
     * @return Gateway exposed through the user-rule interface.
     */
    @Binds
    @Singleton
    abstract fun bindUserRuleGateway(impl: DeviceUserRuleGateway): UserRuleGateway

    /**
     * Provides application preference bindings.
     */
    companion object {
        /**
         * Returns the process preference singleton.
         *
         * @param context Application context for private preferences.
         * @return Shared filtering preferences.
         */
        @Provides
        @Singleton
        fun provideFilteringPreferences(@ApplicationContext context: Context): FilteringPreferences =
            FilteringPreferences.getInstance(context)

        /**
         * Exposes preferences through the narrower settings interface.
         *
         * @param prefs Shared concrete preferences.
         * @return The supplied instance as a preference store.
         */
        @Provides
        @Singleton
        fun provideFilteringPreferenceStore(prefs: FilteringPreferences): com.unblocker.app.data.preferences.FilteringPreferenceStore =
            prefs
    }
}
