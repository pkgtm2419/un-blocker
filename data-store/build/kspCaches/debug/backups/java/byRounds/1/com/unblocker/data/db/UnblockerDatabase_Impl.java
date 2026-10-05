package com.unblocker.data.db;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.unblocker.data.db.dao.BlockRuleDao;
import com.unblocker.data.db.dao.BlockRuleDao_Impl;
import com.unblocker.data.db.dao.DnsLogDao;
import com.unblocker.data.db.dao.DnsLogDao_Impl;
import com.unblocker.data.db.dao.WhitelistDao;
import com.unblocker.data.db.dao.WhitelistDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class UnblockerDatabase_Impl extends UnblockerDatabase {
  private volatile DnsLogDao _dnsLogDao;

  private volatile BlockRuleDao _blockRuleDao;

  private volatile WhitelistDao _whitelistDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `dns_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `domain` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `is_blocked` INTEGER NOT NULL, `is_analyzed` INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dns_logs_timestamp` ON `dns_logs` (`timestamp`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dns_logs_domain` ON `dns_logs` (`domain`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dns_logs_is_analyzed` ON `dns_logs` (`is_analyzed`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `block_rules` (`domain` TEXT NOT NULL, `confidence_score` REAL NOT NULL, `created_at` INTEGER NOT NULL, `rule_source` TEXT NOT NULL, PRIMARY KEY(`domain`))");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_block_rules_domain` ON `block_rules` (`domain`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `whitelist` (`domain` TEXT NOT NULL, `added_at` INTEGER NOT NULL, PRIMARY KEY(`domain`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'c08091e8cde80e5b90fcb48f27a328b3')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `dns_logs`");
        db.execSQL("DROP TABLE IF EXISTS `block_rules`");
        db.execSQL("DROP TABLE IF EXISTS `whitelist`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsDnsLogs = new HashMap<String, TableInfo.Column>(5);
        _columnsDnsLogs.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDnsLogs.put("domain", new TableInfo.Column("domain", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDnsLogs.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDnsLogs.put("is_blocked", new TableInfo.Column("is_blocked", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsDnsLogs.put("is_analyzed", new TableInfo.Column("is_analyzed", "INTEGER", true, 0, "0", TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysDnsLogs = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesDnsLogs = new HashSet<TableInfo.Index>(3);
        _indicesDnsLogs.add(new TableInfo.Index("index_dns_logs_timestamp", false, Arrays.asList("timestamp"), Arrays.asList("ASC")));
        _indicesDnsLogs.add(new TableInfo.Index("index_dns_logs_domain", false, Arrays.asList("domain"), Arrays.asList("ASC")));
        _indicesDnsLogs.add(new TableInfo.Index("index_dns_logs_is_analyzed", false, Arrays.asList("is_analyzed"), Arrays.asList("ASC")));
        final TableInfo _infoDnsLogs = new TableInfo("dns_logs", _columnsDnsLogs, _foreignKeysDnsLogs, _indicesDnsLogs);
        final TableInfo _existingDnsLogs = TableInfo.read(db, "dns_logs");
        if (!_infoDnsLogs.equals(_existingDnsLogs)) {
          return new RoomOpenHelper.ValidationResult(false, "dns_logs(com.unblocker.data.db.entity.DnsLogEntity).\n"
                  + " Expected:\n" + _infoDnsLogs + "\n"
                  + " Found:\n" + _existingDnsLogs);
        }
        final HashMap<String, TableInfo.Column> _columnsBlockRules = new HashMap<String, TableInfo.Column>(4);
        _columnsBlockRules.put("domain", new TableInfo.Column("domain", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBlockRules.put("confidence_score", new TableInfo.Column("confidence_score", "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBlockRules.put("created_at", new TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBlockRules.put("rule_source", new TableInfo.Column("rule_source", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysBlockRules = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesBlockRules = new HashSet<TableInfo.Index>(1);
        _indicesBlockRules.add(new TableInfo.Index("index_block_rules_domain", true, Arrays.asList("domain"), Arrays.asList("ASC")));
        final TableInfo _infoBlockRules = new TableInfo("block_rules", _columnsBlockRules, _foreignKeysBlockRules, _indicesBlockRules);
        final TableInfo _existingBlockRules = TableInfo.read(db, "block_rules");
        if (!_infoBlockRules.equals(_existingBlockRules)) {
          return new RoomOpenHelper.ValidationResult(false, "block_rules(com.unblocker.data.db.entity.BlockRuleEntity).\n"
                  + " Expected:\n" + _infoBlockRules + "\n"
                  + " Found:\n" + _existingBlockRules);
        }
        final HashMap<String, TableInfo.Column> _columnsWhitelist = new HashMap<String, TableInfo.Column>(2);
        _columnsWhitelist.put("domain", new TableInfo.Column("domain", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWhitelist.put("added_at", new TableInfo.Column("added_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysWhitelist = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesWhitelist = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoWhitelist = new TableInfo("whitelist", _columnsWhitelist, _foreignKeysWhitelist, _indicesWhitelist);
        final TableInfo _existingWhitelist = TableInfo.read(db, "whitelist");
        if (!_infoWhitelist.equals(_existingWhitelist)) {
          return new RoomOpenHelper.ValidationResult(false, "whitelist(com.unblocker.data.db.entity.WhitelistEntity).\n"
                  + " Expected:\n" + _infoWhitelist + "\n"
                  + " Found:\n" + _existingWhitelist);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "c08091e8cde80e5b90fcb48f27a328b3", "5b16cc2d19d7955316c526fc2b52b76f");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "dns_logs","block_rules","whitelist");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `dns_logs`");
      _db.execSQL("DELETE FROM `block_rules`");
      _db.execSQL("DELETE FROM `whitelist`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(DnsLogDao.class, DnsLogDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(BlockRuleDao.class, BlockRuleDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(WhitelistDao.class, WhitelistDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public DnsLogDao dnsLogDao() {
    if (_dnsLogDao != null) {
      return _dnsLogDao;
    } else {
      synchronized(this) {
        if(_dnsLogDao == null) {
          _dnsLogDao = new DnsLogDao_Impl(this);
        }
        return _dnsLogDao;
      }
    }
  }

  @Override
  public BlockRuleDao blockRuleDao() {
    if (_blockRuleDao != null) {
      return _blockRuleDao;
    } else {
      synchronized(this) {
        if(_blockRuleDao == null) {
          _blockRuleDao = new BlockRuleDao_Impl(this);
        }
        return _blockRuleDao;
      }
    }
  }

  @Override
  public WhitelistDao whitelistDao() {
    if (_whitelistDao != null) {
      return _whitelistDao;
    } else {
      synchronized(this) {
        if(_whitelistDao == null) {
          _whitelistDao = new WhitelistDao_Impl(this);
        }
        return _whitelistDao;
      }
    }
  }
}
