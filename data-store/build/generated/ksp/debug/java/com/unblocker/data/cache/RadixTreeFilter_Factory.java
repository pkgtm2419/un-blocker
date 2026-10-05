package com.unblocker.data.cache;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
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
public final class RadixTreeFilter_Factory implements Factory<RadixTreeFilter> {
  @Override
  public RadixTreeFilter get() {
    return newInstance();
  }

  public static RadixTreeFilter_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static RadixTreeFilter newInstance() {
    return new RadixTreeFilter();
  }

  private static final class InstanceHolder {
    private static final RadixTreeFilter_Factory INSTANCE = new RadixTreeFilter_Factory();
  }
}
