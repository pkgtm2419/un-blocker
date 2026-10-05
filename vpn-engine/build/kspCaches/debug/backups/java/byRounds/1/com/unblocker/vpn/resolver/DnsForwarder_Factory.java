package com.unblocker.vpn.resolver;

import com.unblocker.vpn.builder.UpstreamDnsServer;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import java.net.DatagramSocket;
import java.net.Socket;
import java.util.List;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import kotlin.jvm.functions.Function1;

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
public final class DnsForwarder_Factory implements Factory<DnsForwarder> {
  private final Provider<Function1<? super DatagramSocket, Boolean>> protectSocketProvider;

  private final Provider<Function1<? super Socket, Boolean>> protectTcpProvider;

  private final Provider<List<UpstreamDnsServer>> serversProvider;

  public DnsForwarder_Factory(
      Provider<Function1<? super DatagramSocket, Boolean>> protectSocketProvider,
      Provider<Function1<? super Socket, Boolean>> protectTcpProvider,
      Provider<List<UpstreamDnsServer>> serversProvider) {
    this.protectSocketProvider = protectSocketProvider;
    this.protectTcpProvider = protectTcpProvider;
    this.serversProvider = serversProvider;
  }

  @Override
  public DnsForwarder get() {
    return newInstance(protectSocketProvider.get(), protectTcpProvider.get(), serversProvider.get());
  }

  public static DnsForwarder_Factory create(
      Provider<Function1<? super DatagramSocket, Boolean>> protectSocketProvider,
      Provider<Function1<? super Socket, Boolean>> protectTcpProvider,
      Provider<List<UpstreamDnsServer>> serversProvider) {
    return new DnsForwarder_Factory(protectSocketProvider, protectTcpProvider, serversProvider);
  }

  public static DnsForwarder newInstance(Function1<? super DatagramSocket, Boolean> protectSocket,
      Function1<? super Socket, Boolean> protectTcp, List<UpstreamDnsServer> servers) {
    return new DnsForwarder(protectSocket, protectTcp, servers);
  }
}
