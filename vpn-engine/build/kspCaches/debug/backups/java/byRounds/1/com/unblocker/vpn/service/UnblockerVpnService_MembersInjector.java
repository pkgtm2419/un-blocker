package com.unblocker.vpn.service;

import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class UnblockerVpnService_MembersInjector implements MembersInjector<UnblockerVpnService> {
  private final Provider<VpnDependencies> vpnDependenciesProvider;

  public UnblockerVpnService_MembersInjector(Provider<VpnDependencies> vpnDependenciesProvider) {
    this.vpnDependenciesProvider = vpnDependenciesProvider;
  }

  public static MembersInjector<UnblockerVpnService> create(
      Provider<VpnDependencies> vpnDependenciesProvider) {
    return new UnblockerVpnService_MembersInjector(vpnDependenciesProvider);
  }

  @Override
  public void injectMembers(UnblockerVpnService instance) {
    injectVpnDependencies(instance, vpnDependenciesProvider.get());
  }

  @InjectedFieldSignature("com.unblocker.vpn.service.UnblockerVpnService.vpnDependencies")
  public static void injectVpnDependencies(UnblockerVpnService instance,
      VpnDependencies vpnDependencies) {
    instance.vpnDependencies = vpnDependencies;
  }
}
