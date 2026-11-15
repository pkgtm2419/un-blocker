package com.unblocker.ml.di

import com.unblocker.ml.classifier.DomainClassifier
import com.unblocker.ml.classifier.HeuristicDomainClassifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt binding from the domain scoring contract to the deterministic heuristic implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MlEngineModule {
    /**
     * Binds the on-device heuristic classifier.
     *
     * @param impl Injected singleton classifier implementation.
     * @return The same instance exposed through the classification contract.
     */
    @Binds
    @Singleton
    abstract fun bindDomainClassifier(impl: HeuristicDomainClassifier): DomainClassifier
}
