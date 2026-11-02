package com.unblocker.ml.di

import com.unblocker.ml.classifier.DomainClassifier
import com.unblocker.ml.classifier.HeuristicDomainClassifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the `:ml-engine` module.
 *
 * Binds [DomainClassifier] to [HeuristicDomainClassifier], providing deterministic
 * zero-overhead domain inference without native TensorFlow Lite shared libraries.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MlEngineModule {

    @Binds
    @Singleton
    abstract fun bindDomainClassifier(
        impl: HeuristicDomainClassifier
    ): DomainClassifier
}
