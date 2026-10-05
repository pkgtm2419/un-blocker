package com.unblocker.data.cache;

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
public final class BloomFilterManager_Factory implements Factory<BloomFilterManager> {
  private final Provider<BlockRuleDao> blockRuleDaoProvider;

  private final Provider<WhitelistDao> whitelistDaoProvider;

  public BloomFilterManager_Factory(Provider<BlockRuleDao> blockRuleDaoProvider,
      Provider<WhitelistDao> whitelistDaoProvider) {
    this.blockRuleDaoProvider = blockRuleDaoProvider;
    this.whitelistDaoProvider = whitelistDaoProvider;
  }

  @Override
  public BloomFilterManager get() {
    return newInstance(blockRuleDaoProvider.get(), whitelistDaoProvider.get());
  }

  public static BloomFilterManager_Factory create(Provider<BlockRuleDao> blockRuleDaoProvider,
      Provider<WhitelistDao> whitelistDaoProvider) {
    return new BloomFilterManager_Factory(blockRuleDaoProvider, whitelistDaoProvider);
  }

  public static BloomFilterManager newInstance(BlockRuleDao blockRuleDao,
      WhitelistDao whitelistDao) {
    return new BloomFilterManager(blockRuleDao, whitelistDao);
  }
}
