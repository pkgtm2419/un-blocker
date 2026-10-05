package com.unblocker.data.di;

import com.unblocker.data.db.UnblockerDatabase;
import com.unblocker.data.db.dao.DnsLogDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
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
public final class DataStoreModule_ProvideDnsLogDaoFactory implements Factory<DnsLogDao> {
  private final Provider<UnblockerDatabase> dbProvider;

  public DataStoreModule_ProvideDnsLogDaoFactory(Provider<UnblockerDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public DnsLogDao get() {
    return provideDnsLogDao(dbProvider.get());
  }

  public static DataStoreModule_ProvideDnsLogDaoFactory create(
      Provider<UnblockerDatabase> dbProvider) {
    return new DataStoreModule_ProvideDnsLogDaoFactory(dbProvider);
  }

  public static DnsLogDao provideDnsLogDao(UnblockerDatabase db) {
    return Preconditions.checkNotNullFromProvides(DataStoreModule.INSTANCE.provideDnsLogDao(db));
  }
}
