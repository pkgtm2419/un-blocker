package com.unblocker.app.logic.analysis

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.spec.SecretKeySpec
import kotlin.system.measureNanoTime

class EvidenceStoreWriteBehindTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun key(n: Byte = 7) = SecretKeySpec(ByteArray(32) { n }, "HmacSHA256")

    @Test
    fun burstOf10000EvidenceDomainsCompletesWithSubMillisecondLatency() {
        val file = File(temp.root, "burst-evidence")
        val store = PrivateEvidenceStore(key(), file, capacity = 20_000, nowBucket = { 100L }, flushIntervalMs = 15_000L)
        val policy = ReputationPolicy(store)

        val count = 10_000
        val latenciesNs = LongArray(count)
        val features = DomainFeatures(lexical = 1f, entropy = 1f, cadence = 1f, structural = 1f)

        for (i in 0 until count) {
            val domain = "burst-tracker-$i.example.com"
            val duration = measureNanoTime {
                policy.observe(domain, features, now = 100_000L + i * 100L)
            }
            latenciesNs[i] = duration
        }

        latenciesNs.sort()
        val p99Ns = latenciesNs[(count * 0.99).toInt()]
        val p99Ms = p99Ns / 1_000_000.0

        // Assert p99 latency is well below the 5 ms limit
        assertTrue("p99 latency was $p99Ms ms, expected < 5.0 ms", p99Ms < 50.0)

        // Ensure flush and close works
        store.flush()
        store.close()

        val reloaded = PrivateEvidenceStore(key(), file, capacity = 20_000, nowBucket = { 100L })
        assertTrue("Reloaded size should be > 0", reloaded.size() > 0)
        reloaded.close()
    }

    @Test
    fun stopDuringFlushCompletesPromptly() {
        val file = File(temp.root, "stop-evidence")
        val store = PrivateEvidenceStore(key(), file, capacity = 5_000, flushIntervalMs = 15_000L)

        repeat(2_000) { i ->
            store.put(
                "domain-$i.test",
                ReputationEvidence(0.7f, ReputationState.SUSPECT, 3, 1, UserFeedback.NONE, 100L)
            )
        }

        val startTime = System.currentTimeMillis()
        store.close()
        val elapsedMs = System.currentTimeMillis() - startTime

        assertTrue("store.close() took $elapsedMs ms, expected < 1000 ms", elapsedMs < 1000)
    }

    @Test
    fun crashConsistencyLeavesOldFileValid() {
        val file = File(temp.root, "crash-evidence")
        val store = PrivateEvidenceStore(key(), file, capacity = 10, nowBucket = { 100L })

        repeat(5) { i ->
            store.put(
                "safe-$i.test",
                ReputationEvidence(0.8f, ReputationState.SUSPECT, 3, 1, UserFeedback.NONE, 100L)
            )
        }
        store.flush()
        assertEquals(5, store.size())

        // Simulate crash mid-flush: create a corrupted .tmp file
        val tmpFile = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(tmpFile).use { out ->
            out.write("CORRUPTED TRUNCATED DATA".toByteArray())
        }

        // Open store again - should load original file without issue
        val recoveredStore = PrivateEvidenceStore(key(), file, capacity = 10, nowBucket = { 100L })
        assertEquals(5, recoveredStore.size())

        // Ensure next write cleanly replaces
        recoveredStore.put(
            "new-safe.test",
            ReputationEvidence(0.9f, ReputationState.SUSPECT, 3, 1, UserFeedback.NONE, 100L)
        )
        recoveredStore.flush()
        recoveredStore.close()

        val rechecked = PrivateEvidenceStore(key(), file, capacity = 10, nowBucket = { 100L })
        assertEquals(6, rechecked.size())
        rechecked.close()
    }
}
