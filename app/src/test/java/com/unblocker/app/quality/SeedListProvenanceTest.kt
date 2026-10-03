package com.unblocker.app.quality

import com.unblocker.app.logic.DomainName
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedListProvenanceTest {
    @Test fun bundledSeedListsDeclareLicenseAndContainOnlyCanonicalDomains() {
        listOf("ad_domains.txt", "adult_domains.txt").forEach { name ->
            val file = File("src/main/assets", name)
            val lines = file.readLines()
            assertTrue("$name must declare its license",
                lines.firstOrNull() == "# SPDX-License-Identifier: Apache-2.0")

            val domains = lines.filter { it.isNotBlank() && !it.startsWith("#") }
            assertTrue("$name needs a useful local seed set", domains.size >= 15)
            assertEquals("$name contains duplicate entries", domains.size, domains.toSet().size)
            domains.forEach { domain ->
                assertEquals("$name contains a non-canonical domain", domain, DomainName.normalize(domain))
            }
        }
    }
}
