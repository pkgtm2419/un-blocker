package com.unblocker.vpn.service;

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
public final class NetworkChangeReceiver_Factory implements Factory<NetworkChangeReceiver> {
  private final Provider<Context> contextProvider;

  public NetworkChangeReceiver_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public NetworkChangeReceiver get() {
    return newInstance(contextProvider.get());
  }

  public static NetworkChangeReceiver_Factory create(Provider<Context> contextProvider) {
    return new NetworkChangeReceiver_Factory(contextProvider);
  }

  public static NetworkChangeReceiver newInstance(Context context) {
    return new NetworkChangeReceiver(context);
  }
}
