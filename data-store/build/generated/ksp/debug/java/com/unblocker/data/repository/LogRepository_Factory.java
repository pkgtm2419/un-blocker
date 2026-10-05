package com.unblocker.data.repository;

import com.unblocker.data.db.dao.DnsLogDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class LogRepository_Factory implements Factory<LogRepository> {
  private final Provider<DnsLogDao> dnsLogDaoProvider;

  public LogRepository_Factory(Provider<DnsLogDao> dnsLogDaoProvider) {
    this.dnsLogDaoProvider = dnsLogDaoProvider;
  }

  @Override
  public LogRepository get() {
    return newInstance(dnsLogDaoProvider.get());
  }

  public static LogRepository_Factory create(Provider<DnsLogDao> dnsLogDaoProvider) {
    return new LogRepository_Factory(dnsLogDaoProvider);
  }

  public static LogRepository newInstance(DnsLogDao dnsLogDao) {
    return new LogRepository(dnsLogDao);
  }
}
