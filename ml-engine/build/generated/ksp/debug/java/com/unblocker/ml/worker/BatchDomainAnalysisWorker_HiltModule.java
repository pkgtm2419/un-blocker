package com.unblocker.ml.worker;

import androidx.hilt.work.WorkerAssistedFactory;
import androidx.work.ListenableWorker;
import dagger.Binds;
import dagger.Module;
import dagger.hilt.InstallIn;
import dagger.hilt.codegen.OriginatingElement;
import dagger.hilt.components.SingletonComponent;
import dagger.multibindings.IntoMap;
import dagger.multibindings.StringKey;
import javax.annotation.processing.Generated;

@Generated("androidx.hilt.AndroidXHiltProcessor")
@Module
@InstallIn(SingletonComponent.class)
@OriginatingElement(
    topLevelClass = BatchDomainAnalysisWorker.class
)
public interface BatchDomainAnalysisWorker_HiltModule {
  @Binds
  @IntoMap
  @StringKey("com.unblocker.ml.worker.BatchDomainAnalysisWorker")
  WorkerAssistedFactory<? extends ListenableWorker> bind(
      BatchDomainAnalysisWorker_AssistedFactory factory);
}
