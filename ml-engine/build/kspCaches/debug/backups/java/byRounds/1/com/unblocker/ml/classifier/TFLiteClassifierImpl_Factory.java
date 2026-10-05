package com.unblocker.ml.classifier;

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
public final class TFLiteClassifierImpl_Factory implements Factory<TFLiteClassifierImpl> {
  private final Provider<Context> contextProvider;

  public TFLiteClassifierImpl_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public TFLiteClassifierImpl get() {
    return newInstance(contextProvider.get());
  }

  public static TFLiteClassifierImpl_Factory create(Provider<Context> contextProvider) {
    return new TFLiteClassifierImpl_Factory(contextProvider);
  }

  public static TFLiteClassifierImpl newInstance(Context context) {
    return new TFLiteClassifierImpl(context);
  }
}
