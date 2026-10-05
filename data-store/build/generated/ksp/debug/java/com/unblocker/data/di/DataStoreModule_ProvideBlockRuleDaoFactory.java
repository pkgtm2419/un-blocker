package com.unblocker.data.di;

import com.unblocker.data.db.UnblockerDatabase;
import com.unblocker.data.db.dao.BlockRuleDao;
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
public final class DataStoreModule_ProvideBlockRuleDaoFactory implements Factory<BlockRuleDao> {
  private final Provider<UnblockerDatabase> dbProvider;

  public DataStoreModule_ProvideBlockRuleDaoFactory(Provider<UnblockerDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public BlockRuleDao get() {
    return provideBlockRuleDao(dbProvider.get());
  }

  public static DataStoreModule_ProvideBlockRuleDaoFactory create(
      Provider<UnblockerDatabase> dbProvider) {
    return new DataStoreModule_ProvideBlockRuleDaoFactory(dbProvider);
  }

  public static BlockRuleDao provideBlockRuleDao(UnblockerDatabase db) {
    return Preconditions.checkNotNullFromProvides(DataStoreModule.INSTANCE.provideBlockRuleDao(db));
  }
}
