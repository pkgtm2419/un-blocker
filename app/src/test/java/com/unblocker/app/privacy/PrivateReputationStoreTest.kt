package com.unblocker.app.privacy

import com.unblocker.app.logic.analysis.PrivateReputationStore
import java.io.File
import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKey
import java.security.InvalidKeyException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrivateReputationStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private fun key(byte: Byte = 7) = SecretKeySpec(ByteArray(32) { byte }, "HmacSHA256")

    @Test fun reloadPreservesScoresWithoutPersistingDomains() {
        val file = File(temp.root, "reputations")
        PrivateReputationStore(key(), file).put("Ads.Example.com", 0.9f)
        assertFalse(file.readText().contains("example", ignoreCase = true))
        assertEquals(0.9f, PrivateReputationStore(key(), file).get("ads.example.com")!!, 0f)
        assertNull(PrivateReputationStore(key(8), file).get("ads.example.com"))
    }

    @Test fun migrationRemovesPlaintextAndRejectsInvalidScores() {
        val legacy = File(temp.root, "learned_trackers.txt").apply {
            writeText("ads.example.com:0.9\nbad.example:NaN\nbad2.example:4\n")
        }
        val file = File(temp.root, "reputations")
        val store = PrivateReputationStore(key(), file)
        store.migrate(legacy)
        assertFalse(legacy.exists())
        assertEquals(1, store.size())
        assertEquals(0.9f, PrivateReputationStore(key(), file).get("ads.example.com")!!, 0f)
        assertFalse(file.readText().contains("example"))
    }

    @Test fun concurrentUpdatesStayBoundedAndResetRemovesLearning() {
        val file = File(temp.root, "reputations")
        val store = PrivateReputationStore(key(), file, 8)
        val threads = (1..4).map { worker -> Thread {
            repeat(12) { store.put("ads.$worker.$it.example", 0.9f) }
        }.apply { start() } }
        threads.forEach { it.join() }
        assertEquals(8, store.size())
        assertEquals(8, PrivateReputationStore(key(), file, 8).size())
        store.clear()
        assertEquals(0, PrivateReputationStore(key(), file).size())
    }

    @Test fun corruptSnapshotDoesNotBreakFiltering() {
        val file = File(temp.root, "reputations").apply { writeText("garbage\nexample.com:NaN\n") }
        val store = PrivateReputationStore(key(), file)
        assertEquals(0, store.size())
        store.put("ads.example.com", 0.8f)
        assertEquals(0.8f, store.get("ads.example.com")!!, 0f)
    }

    @Test fun unavailableKeyDuringLookupDoesNotInterruptFiltering() {
        var available = true
        val flakyKey = object : SecretKey {
            override fun getAlgorithm() = "HmacSHA256"
            override fun getFormat() = "RAW"
            override fun getEncoded(): ByteArray {
                if (!available) throw IllegalStateException("Key unavailable")
                return ByteArray(32) { 7 }
            }
        }
        val store = PrivateReputationStore(flakyKey)
        store.put("ads.example.com", 0.9f)
        available = false
        assertNull(store.get("ads.example.com"))
        store.put("ads.new.example", 0.8f)
        available = true
        assertEquals(0.9f, store.get("ads.example.com")!!, 0f)
    }

    @Test fun defaultCapacityRetainsMoreThanLegacyFiveThousandLimit() {
        val store = PrivateReputationStore(key())
        repeat(5_001) { index -> store.put("ads-$index.example.com", 0.9f) }

        assertEquals(5_001, store.size())
        assertEquals(0.9f, store.get("ads-0.example.com")!!, 0f)
    }

    @Test fun untouchedScoreDecaysFivePercentPerCompletedWeek() {
        var now = 1_700_000_000_000L
        val store = PrivateReputationStore(key(), nowMillis = { now })
        store.put("stale-ads.example.com", 1.0f)

        now += 6 * 24 * 60 * 60 * 1_000L
        assertEquals(1.0f, store.get("stale-ads.example.com")!!, 0.0001f)

        now += 24 * 60 * 60 * 1_000L
        assertEquals(0.95f, store.get("stale-ads.example.com")!!, 0.0001f)

        now += 7 * 24 * 60 * 60 * 1_000L
        assertEquals(0.9025f, store.get("stale-ads.example.com")!!, 0.0001f)
    }
}
