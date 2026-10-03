package com.unblocker.app.privacy

import com.unblocker.app.logic.analysis.PrivateDomainSet
import java.io.File
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrivateDomainSetTest {
    @get:Rule val temp = TemporaryFolder()
    private fun key(byte: Byte = 11) = SecretKeySpec(ByteArray(32) { byte }, "HmacSHA256")

    @Test fun persistsCanonicalMembershipWithoutRawDomains() {
        val file = File(temp.root, "allowlist")
        val rules = PrivateDomainSet(key(), file)

        assertTrue(rules.add("Ads.Example.COM."))
        assertTrue(rules.contains("ads.example.com"))
        assertFalse(file.readText().contains("example", ignoreCase = true))
        assertTrue(PrivateDomainSet(key(), file).contains("ADS.EXAMPLE.COM."))
        assertFalse(PrivateDomainSet(key(12), file).contains("ads.example.com"))
    }

    @Test fun invalidDomainsAreRejectedAndRulesCanBeRemoved() {
        val rules = PrivateDomainSet(key(), File(temp.root, "allowlist"))

        assertFalse(rules.add("https://ads.example.com"))
        assertFalse(rules.add("ads..example.com"))
        assertEquals(0, rules.size())
        assertTrue(rules.add("ads.example.com"))
        assertTrue(rules.remove("ADS.EXAMPLE.COM."))
        assertFalse(rules.contains("ads.example.com"))
    }

    @Test fun capacityEvictsOldestRuleAndClearPersists() {
        val file = File(temp.root, "allowlist")
        val rules = PrivateDomainSet(key(), file, capacity = 2)
        rules.add("one.example")
        rules.add("two.example")
        rules.add("three.example")

        assertFalse(rules.contains("one.example"))
        assertTrue(rules.contains("two.example"))
        assertTrue(rules.contains("three.example"))
        rules.clear()
        assertEquals(0, PrivateDomainSet(key(), file).size())
    }
}
