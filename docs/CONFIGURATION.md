# Build-time and runtime configuration

## Build-time

- `settings.gradle.kts`: module inclusion; `gradle/libs.versions.toml`: dependency catalog.
- `app/build.gradle.kts`: app ID `com.unblocker.app`, SDK levels, version, signing variants, generated assets, Python rule compiler hook, shrinker/R8.
- `app/src/main/AndroidManifest.xml`: permissions, non-exported VPN service/boot receiver, backup prevention and foreground-service subtype.
- `tools/filter-compiler/sources.json`: pinned dataset source/license/checksum metadata.
- `app/src/main/assets/extensions/extensions_manifest.json`: preset and custom extension metadata (manifest v8, 67 entries).
- `app/src/main/assets/default_config.json`: shipped feature defaults; first-run handling in `QuickStartManager` and `FilteringPreferences`.

## Runtime

- `FilteringPreferences` owns ad/adult blocking, user protection intent, auto-boot, health intervals, mode, tier, 5-minute pause, logging paused and related options.
- `ExtensionRepository` tracks extension enablement/auto-updates and custom filter sources; auto-update defaults off at examined source.
- `QueryLogSink` default raw-history mode is `AGGREGATE_ONLY`, and query logging paused is default false in preferences.
- `DeviceLearning` stores private HMAC keyed identifiers, not visible hostname records.
- `ResolverRegistry` selects Android-network resolver sets, not a user configuration for public DNS fallback.

## Secrets and environment

`UB_BLOCKER_KEYSTORE_FILE/PASSWORD`, `UB_BLOCKER_KEY_ALIAS/PASSWORD` may select stable test signing; `UB_RELEASE_KEYSTORE_FILE/PASSWORD`, `UB_RELEASE_KEY_ALIAS/PASSWORD` are production signing inputs. **All values required together** when signing used. `UB_BLOCKER_PYTHON` can specify Python interpreter for Gradle DNS compiler. Never commit secrets; `local.properties` is local/untracked.

Any new configurable flag needs: documented default, migration policy, UI behavior, source of truth, tests, privacy impact and feature matrix update.
