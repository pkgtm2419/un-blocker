package com.unblocker.data.repository;

import com.unblocker.data.cache.BloomFilterManager;
import com.unblocker.data.db.dao.BlockRuleDao;
import com.unblocker.data.db.dao.WhitelistDao;
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
public final class FilterRepository_Factory implements Factory<FilterRepository> {
  private final Provider<BlockRuleDao> blockRuleDaoProvider;

  private final Provider<WhitelistDao> whitelistDaoProvider;

  private final Provider<BloomFilterManager> bloomFilterManagerProvider;

  public FilterRepository_Factory(Provider<BlockRuleDao> blockRuleDaoProvider,
      Provider<WhitelistDao> whitelistDaoProvider,
      Provider<BloomFilterManager> bloomFilterManagerProvider) {
    this.blockRuleDaoProvider = blockRuleDaoProvider;
    this.whitelistDaoProvider = whitelistDaoProvider;
    this.bloomFilterManagerProvider = bloomFilterManagerProvider;
  }

  @Override
  public FilterRepository get() {
    return newInstance(blockRuleDaoProvider.get(), whitelistDaoProvider.get(), bloomFilterManagerProvider.get());
  }

  public static FilterRepository_Factory create(Provider<BlockRuleDao> blockRuleDaoProvider,
      Provider<WhitelistDao> whitelistDaoProvider,
      Provider<BloomFilterManager> bloomFilterManagerProvider) {
    return new FilterRepository_Factory(blockRuleDaoProvider, whitelistDaoProvider, bloomFilterManagerProvider);
  }

  public static FilterRepository newInstance(BlockRuleDao blockRuleDao, WhitelistDao whitelistDao,
      BloomFilterManager bloomFilterManager) {
    return new FilterRepository(blockRuleDao, whitelistDao, bloomFilterManager);
  }
}
