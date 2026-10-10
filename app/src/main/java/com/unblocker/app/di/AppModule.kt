package com.unblocker.app.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * App-level composition root.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
