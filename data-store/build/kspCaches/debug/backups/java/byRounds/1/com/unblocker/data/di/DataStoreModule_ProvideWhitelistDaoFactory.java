package com.unblocker.data.di;

import com.unblocker.data.db.UnblockerDatabase;
import com.unblocker.data.db.dao.WhitelistDao;
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
public final class DataStoreModule_ProvideWhitelistDaoFactory implements Factory<WhitelistDao> {
  private final Provider<UnblockerDatabase> dbProvider;

  public DataStoreModule_ProvideWhitelistDaoFactory(Provider<UnblockerDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public WhitelistDao get() {
    return provideWhitelistDao(dbProvider.get());
  }

  public static DataStoreModule_ProvideWhitelistDaoFactory create(
      Provider<UnblockerDatabase> dbProvider) {
    return new DataStoreModule_ProvideWhitelistDaoFactory(dbProvider);
  }

  public static WhitelistDao provideWhitelistDao(UnblockerDatabase db) {
    return Preconditions.checkNotNullFromProvides(DataStoreModule.INSTANCE.provideWhitelistDao(db));
  }
}
