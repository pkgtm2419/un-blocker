package com.unblocker.app.data.export

import com.unblocker.app.data.preferences.BlockingMode
import com.unblocker.app.data.preferences.FilteringTier
import com.unblocker.data.db.entity.UserRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Unit tests verifying settings backup encoding, schema version validation, URL safety, and bounds (WP-22 / F31).
 */
class SettingsBackupCodecTest {
    @Test
    fun roundTripPreservesPreferencesExtensionsAndValidRulesWithoutSecrets() {
        val original =
            SettingsBackupSnapshot(
                adBlockingEnabled = true,
                adultBlockingEnabled = false,
                blockingMode = BlockingMode.APPROVED_ONLY,
                filteringTier = FilteringTier.COMPLETE,
                autoRestartOnBoot = false,
                healthCheckIntervalMinutes = 720,
                blockAnalyticsBreakage = false,
                isQueryLoggingPaused = true,
                enabledExtensionIds = setOf("adguard_dns_filter", "easyprivacy"),
                customExtensions =
                    listOf(
                        CustomExtensionBackup(
                            id = "custom_123",
                            name = "My Custom List",
                            url = "https://example.com/list.txt",
                            isEnabled = true,
                        ),
                    ),
                userRules =
                    listOf(
                        UserRuleBackup("api.example.com", UserRuleEntity.ACTION_ALLOW, isWholeSite = false),
                        UserRuleBackup("ads.example.net", UserRuleEntity.ACTION_BLOCK, isWholeSite = true),
                    ),
                myDomains = listOf("bank.example.org"),
            )

        val encoded = SettingsBackupCodec.encode(original)
        assertFalse(encoded.contains("hmac", ignoreCase = true))
        val decoded = SettingsBackupCodec.decode(ByteArrayInputStream(encoded.toByteArray(Charsets.UTF_8)))

        assertEquals(original, decoded)
    }

    @Test
    fun rejectUnsafeUrlsInvalidDomainsAndOversizedPayloads() {
        val maliciousJson =
            """
            {
              "version": 1,
              "preferences": { "adBlockingEnabled": true },
              "customExtensions": [
                { "id": "custom_1", "name": "Insecure HTTP", "url": "http://evil.example/list.txt", "isEnabled": true },
                { "id": "custom_2", "name": "JavaScript", "url": "javascript:alert(1)", "isEnabled": true },
                { "id": "custom_3", "name": "Valid HTTPS", "url": "https://lists.example.org/rules.txt", "isEnabled": true }
              ],
              "userRules": [
                { "domain": "not a domain!", "action": "BLOCK", "isWholeSite": false },
                { "domain": "tracker.example.com", "action": "BLOCK", "isWholeSite": false }
              ],
              "myDomains": ["bank.org", "bad domain"]
            }
            """.trimIndent()

        val decoded = SettingsBackupCodec.decodeString(maliciousJson)
        assertEquals(listOf("custom_3"), decoded.customExtensions.map { it.id })
        assertEquals(listOf("tracker.example.com"), decoded.userRules.map { it.domain })
        assertEquals(listOf("bank.org"), decoded.myDomains)

        val hugeBytes = ("{\"version\":1,\"preferences\":{},\"pad\":\"" + "a".repeat(1_050_000) + "\"}").toByteArray()
        val oversized = runCatching { SettingsBackupCodec.decode(ByteArrayInputStream(hugeBytes)) }
        assertTrue(oversized.isFailure)

        val badVersion = runCatching { SettingsBackupCodec.decodeString("{\"version\":99,\"preferences\":{}}") }
        assertTrue(badVersion.isFailure)
    }
}
