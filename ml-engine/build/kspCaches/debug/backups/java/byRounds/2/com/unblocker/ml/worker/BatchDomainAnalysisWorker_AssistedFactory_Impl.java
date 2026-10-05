package com.unblocker.ml.worker;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.InstanceFactory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class BatchDomainAnalysisWorker_AssistedFactory_Impl implements BatchDomainAnalysisWorker_AssistedFactory {
  private final BatchDomainAnalysisWorker_Factory delegateFactory;

  BatchDomainAnalysisWorker_AssistedFactory_Impl(
      BatchDomainAnalysisWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public BatchDomainAnalysisWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<BatchDomainAnalysisWorker_AssistedFactory> create(
      BatchDomainAnalysisWorker_Factory delegateFactory) {
    return InstanceFactory.create(new BatchDomainAnalysisWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<BatchDomainAnalysisWorker_AssistedFactory> createFactoryProvider(
      BatchDomainAnalysisWorker_Factory delegateFactory) {
    return InstanceFactory.create(new BatchDomainAnalysisWorker_AssistedFactory_Impl(delegateFactory));
  }
}
