package com.unblocker.ml.worker;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class WorkScheduler_Factory implements Factory<WorkScheduler> {
  private final Provider<Context> contextProvider;

  public WorkScheduler_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public WorkScheduler get() {
    return newInstance(contextProvider.get());
  }

  public static WorkScheduler_Factory create(Provider<Context> contextProvider) {
    return new WorkScheduler_Factory(contextProvider);
  }

  public static WorkScheduler newInstance(Context context) {
    return new WorkScheduler(context);
  }
}
