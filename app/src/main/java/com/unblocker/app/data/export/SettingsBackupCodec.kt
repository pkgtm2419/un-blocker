package com.unblocker.app.data.export

import com.unblocker.app.data.preferences.BlockingMode
import com.unblocker.app.data.preferences.FilteringTier
import com.unblocker.app.logic.DomainName
import com.unblocker.app.services.HealthCheckPolicy
import com.unblocker.data.db.entity.UserRuleEntity
import java.io.InputStream
import java.net.URI
import java.util.Locale

/**
 * Custom filter list metadata included in a user-controlled settings backup (no HMAC secrets, no logs).
 */
data class CustomExtensionBackup(
    val id: String,
    val name: String,
    val url: String,
    val isEnabled: Boolean,
)

/**
 * Readable user exception entry restored via [UserRuleGateway].
 */
data class UserRuleBackup(
    val domain: String,
    val action: Int,
    val isWholeSite: Boolean,
)

/**
 * Validated schema v1 settings and rules backup snapshot (WP-22 / F31).
 */
data class SettingsBackupSnapshot(
    val adBlockingEnabled: Boolean,
    val adultBlockingEnabled: Boolean,
    val blockingMode: BlockingMode,
    val filteringTier: FilteringTier,
    val autoRestartOnBoot: Boolean,
    val healthCheckIntervalMinutes: Int,
    val blockAnalyticsBreakage: Boolean,
    val isQueryLoggingPaused: Boolean,
    val enabledExtensionIds: Set<String>,
    val customExtensions: List<CustomExtensionBackup>,
    val userRules: List<UserRuleBackup>,
    val myDomains: List<String>,
    val excludedPackages: Set<String> = emptySet(),
)

/**
 * Encodes and strictly validates non-secret Un-Blocker settings & rule backups.
 *
 * Security invariants:
 * - Never includes Keystore keys, HMAC sets, learned reputation scores, or query logs.
 * - Rejects payloads exceeding [MAX_BACKUP_BYTES] (1 MiB) or mismatched schema versions.
 * - Re-validates all domains via [DomainName.normalizeUserEntry] and custom URLs (`https://` only).
 */
object SettingsBackupCodec {
    /** Maximum accepted backup payload size in bytes (1 MiB). */
    const val MAX_BACKUP_BYTES: Int = 1_048_576

    /** Current schema format version. */
    const val SCHEMA_VERSION: Int = 1

    private const val MAX_CUSTOM_EXTENSIONS = 100
    private const val MAX_USER_RULES = 2_000
    private const val MAX_MY_DOMAINS = 500
    private const val MAX_EXCLUDED_PACKAGES = 500
    private const val MAX_NAME_LEN = 120
    private const val MAX_URL_LEN = 512
    private val PACKAGE_NAME_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    /**
     * Serializes [snapshot] into formatted UTF-8 JSON.
     */
    fun encode(snapshot: SettingsBackupSnapshot): String {
        val prefsMap =
            linkedMapOf<String, Any?>(
                "adBlockingEnabled" to snapshot.adBlockingEnabled,
                "adultBlockingEnabled" to snapshot.adultBlockingEnabled,
                "blockingMode" to snapshot.blockingMode.name,
                "filteringTier" to snapshot.filteringTier.name,
                "autoRestartOnBoot" to snapshot.autoRestartOnBoot,
                "healthCheckIntervalMinutes" to snapshot.healthCheckIntervalMinutes,
                "blockAnalyticsBreakage" to snapshot.blockAnalyticsBreakage,
                "isQueryLoggingPaused" to snapshot.isQueryLoggingPaused,
            )

        val customList =
            snapshot.customExtensions.map { ext ->
                linkedMapOf<String, Any?>(
                    "id" to ext.id,
                    "name" to ext.name,
                    "url" to ext.url,
                    "isEnabled" to ext.isEnabled,
                )
            }
        val rulesList =
            snapshot.userRules.map { rule ->
                linkedMapOf<String, Any?>(
                    "domain" to rule.domain,
                    "action" to if (rule.action == UserRuleEntity.ACTION_BLOCK) "BLOCK" else "ALLOW",
                    "isWholeSite" to rule.isWholeSite,
                )
            }

        val root =
            linkedMapOf<String, Any?>(
                "version" to SCHEMA_VERSION,
                "preferences" to prefsMap,
                "enabledExtensionIds" to snapshot.enabledExtensionIds.sorted(),
                "customExtensions" to customList,
                "userRules" to rulesList,
                "myDomains" to snapshot.myDomains,
                "excludedPackages" to snapshot.excludedPackages.sorted(),
            )
        return PureJsonCodec.stringifyObject(root)
    }

    /**
     * Reads at most [MAX_BACKUP_BYTES] + 1 bytes from [inputStream] and decodes/validates a backup snapshot.
     *
     * @throws IllegalArgumentException When the payload is oversized, malformed JSON, unsupported version,
     *   or missing required blocks.
     */
    fun decode(inputStream: InputStream): SettingsBackupSnapshot {
        val bytes = readBounded(inputStream)
        val raw = bytes.toString(Charsets.UTF_8)
        return decodeString(raw)
    }

    /**
     * Parses and validates UTF-8 JSON text into [SettingsBackupSnapshot].
     */
    fun decodeString(rawJson: String): SettingsBackupSnapshot {
        if (rawJson.toByteArray(Charsets.UTF_8).size > MAX_BACKUP_BYTES) {
            throw IllegalArgumentException("Backup payload exceeds 1 MiB limit")
        }
        val root =
            runCatching { PureJsonCodec.parseObject(rawJson) }.getOrElse {
                throw IllegalArgumentException("Malformed backup JSON")
            }
        val version = PureJsonCodec.optInt(root, "version", -1)
        require(version == SCHEMA_VERSION) { "Unsupported backup schema version: $version" }

        val prefsObj =
            PureJsonCodec.optObject(root, "preferences")
                ?: throw IllegalArgumentException("Missing preferences object in backup")

        val mode =
            runCatching {
                BlockingMode.valueOf(PureJsonCodec.optString(prefsObj, "blockingMode", BlockingMode.SMART.name))
            }.getOrDefault(BlockingMode.SMART)

        val tier =
            runCatching {
                FilteringTier.valueOf(PureJsonCodec.optString(prefsObj, "filteringTier", FilteringTier.OPTIMAL.name))
            }.getOrDefault(FilteringTier.OPTIMAL)

        val interval =
            PureJsonCodec
                .optInt(prefsObj, "healthCheckIntervalMinutes", HealthCheckPolicy.DEFAULT_INTERVAL_MINUTES)
                .coerceIn(HealthCheckPolicy.DEFAULT_INTERVAL_MINUTES, 1_440)

        val enabledIds = linkedSetOf<String>()
        val idsArray = PureJsonCodec.optArray(root, "enabledExtensionIds")
        if (idsArray != null) {
            for (rawItem in idsArray.take(500)) {
                val id = (rawItem as? String).orEmpty().trim()
                if (id.matches(Regex("^[a-z0-9_\\-]{1,80}$"))) {
                    enabledIds.add(id)
                }
            }
        }

        val customExtensions = mutableListOf<CustomExtensionBackup>()
        val customArray = PureJsonCodec.optArray(root, "customExtensions")
        if (customArray != null) {
            for ((i, rawItem) in customArray.take(MAX_CUSTOM_EXTENSIONS).withIndex()) {
                @Suppress("UNCHECKED_CAST")
                val item = rawItem as? Map<String, Any?> ?: continue
                val name = PureJsonCodec.optString(item, "name").trim().take(MAX_NAME_LEN)
                val id = PureJsonCodec.optString(item, "id").trim()
                val rawUrl = PureJsonCodec.optString(item, "url").trim()
                if (name.isEmpty()) continue
                val validatedUrl = validateCustomUrlOrEmpty(rawUrl) ?: continue
                val safeId =
                    if (id.matches(Regex("^custom_[A-Za-z0-9_\\-]{1,64}$"))) {
                        id
                    } else {
                        "custom_restored_${i}_${System.currentTimeMillis()}"
                    }
                customExtensions.add(
                    CustomExtensionBackup(
                        id = safeId,
                        name = name,
                        url = validatedUrl,
                        isEnabled = PureJsonCodec.optBoolean(item, "isEnabled", true),
                    ),
                )
            }
        }

        val userRules = mutableListOf<UserRuleBackup>()
        val rulesArray = PureJsonCodec.optArray(root, "userRules")
        if (rulesArray != null) {
            for (rawItem in rulesArray.take(MAX_USER_RULES)) {
                @Suppress("UNCHECKED_CAST")
                val item = rawItem as? Map<String, Any?> ?: continue
                val normalized =
                    DomainName.normalizeUserEntry(PureJsonCodec.optString(item, "domain")) ?: continue
                val actionStr = PureJsonCodec.optString(item, "action", "ALLOW").uppercase(Locale.ROOT)
                val action =
                    when (actionStr) {
                        "ALLOW" -> UserRuleEntity.ACTION_ALLOW
                        "BLOCK" -> UserRuleEntity.ACTION_BLOCK
                        else -> continue
                    }
                userRules.add(
                    UserRuleBackup(
                        domain = normalized,
                        action = action,
                        isWholeSite = PureJsonCodec.optBoolean(item, "isWholeSite", false),
                    ),
                )
            }
        }

        val myDomains = linkedSetOf<String>()
        val myDomainsArray = PureJsonCodec.optArray(root, "myDomains")
        if (myDomainsArray != null) {
            for (rawItem in myDomainsArray.take(MAX_MY_DOMAINS)) {
                val normalized = DomainName.normalizeUserEntry((rawItem as? String).orEmpty()) ?: continue
                myDomains.add(normalized)
            }
        }

        val excludedPackages = linkedSetOf<String>()
        val excludedArray = PureJsonCodec.optArray(root, "excludedPackages")
        if (excludedArray != null) {
            for (rawItem in excludedArray.take(MAX_EXCLUDED_PACKAGES)) {
                val pkg = (rawItem as? String).orEmpty().trim()
                if (pkg.length <= 200 && PACKAGE_NAME_REGEX.matches(pkg)) {
                    excludedPackages.add(pkg)
                }
            }
        }

        return SettingsBackupSnapshot(
            adBlockingEnabled = PureJsonCodec.optBoolean(prefsObj, "adBlockingEnabled", true),
            adultBlockingEnabled = PureJsonCodec.optBoolean(prefsObj, "adultBlockingEnabled", true),
            blockingMode = mode,
            filteringTier = tier,
            autoRestartOnBoot = PureJsonCodec.optBoolean(prefsObj, "autoRestartOnBoot", true),
            healthCheckIntervalMinutes = interval,
            blockAnalyticsBreakage = PureJsonCodec.optBoolean(prefsObj, "blockAnalyticsBreakage", true),
            isQueryLoggingPaused = PureJsonCodec.optBoolean(prefsObj, "isQueryLoggingPaused", false),
            enabledExtensionIds = enabledIds,
            customExtensions = customExtensions,
            userRules = userRules,
            myDomains = myDomains.toList(),
            excludedPackages = excludedPackages,
        )
    }

    private fun validateCustomUrlOrEmpty(rawUrl: String): String? {
        if (rawUrl.isEmpty()) return ""
        if (rawUrl.length > MAX_URL_LEN) return null
        val parsed = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        return if (parsed.scheme?.lowercase(Locale.ROOT) == "https" && !parsed.host.isNullOrBlank()) {
            rawUrl
        } else {
            null
        }
    }

    private fun readBounded(inputStream: InputStream): ByteArray {
        val buf = ByteArray(8192)
        val out = java.io.ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = inputStream.read(buf)
            if (read <= 0) break
            total += read
            if (total > MAX_BACKUP_BYTES) {
                throw IllegalArgumentException("Backup payload exceeds $MAX_BACKUP_BYTES bytes")
            }
            out.write(buf, 0, read)
        }
        return out.toByteArray()
    }
}
