package com.unblocker.ml.di

import com.unblocker.ml.classifier.DomainClassifier
import com.unblocker.ml.classifier.TFLiteClassifierImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the `:ml-engine` module.
 *
 * Binds the abstract [DomainClassifier] interface to the concrete [TFLiteClassifierImpl].
 *
 * (Note: The `BatchDomainAnalysisWorker` is injected automatically via Hilt's `@HiltWorker`
 * and `@AssistedInject`, provided the app module initializes `HiltWorkerFactory`.)
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MlEngineModule {

    @Binds
    @Singleton
    abstract fun bindDomainClassifier(
        impl: TFLiteClassifierImpl
    ): DomainClassifier
}
