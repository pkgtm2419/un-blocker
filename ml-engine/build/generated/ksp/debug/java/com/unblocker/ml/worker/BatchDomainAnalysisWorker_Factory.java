package com.unblocker.ml.worker;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.unblocker.data.repository.FilterRepository;
import com.unblocker.data.repository.LogRepository;
import com.unblocker.ml.classifier.DomainClassifier;
import dagger.internal.DaggerGenerated;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata
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
public final class BatchDomainAnalysisWorker_Factory {
  private final Provider<DomainClassifier> classifierProvider;

  private final Provider<LogRepository> logRepositoryProvider;

  private final Provider<FilterRepository> filterRepositoryProvider;

  public BatchDomainAnalysisWorker_Factory(Provider<DomainClassifier> classifierProvider,
      Provider<LogRepository> logRepositoryProvider,
      Provider<FilterRepository> filterRepositoryProvider) {
    this.classifierProvider = classifierProvider;
    this.logRepositoryProvider = logRepositoryProvider;
    this.filterRepositoryProvider = filterRepositoryProvider;
  }

  public BatchDomainAnalysisWorker get(Context appContext, WorkerParameters workerParams) {
    return newInstance(appContext, workerParams, classifierProvider.get(), logRepositoryProvider.get(), filterRepositoryProvider.get());
  }

  public static BatchDomainAnalysisWorker_Factory create(
      Provider<DomainClassifier> classifierProvider, Provider<LogRepository> logRepositoryProvider,
      Provider<FilterRepository> filterRepositoryProvider) {
    return new BatchDomainAnalysisWorker_Factory(classifierProvider, logRepositoryProvider, filterRepositoryProvider);
  }

  public static BatchDomainAnalysisWorker newInstance(Context appContext,
      WorkerParameters workerParams, DomainClassifier classifier, LogRepository logRepository,
      FilterRepository filterRepository) {
    return new BatchDomainAnalysisWorker(appContext, workerParams, classifier, logRepository, filterRepository);
  }
}
