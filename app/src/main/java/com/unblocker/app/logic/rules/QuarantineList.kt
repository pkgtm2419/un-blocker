package com.unblocker.app.logic.rules

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Process-local denylist of rule kind/hostname pairs used during rule admission.
 */
object QuarantineList {
    private val quarantined = HashSet<Pair<RuleKind, String>>()

    /**
     * Adds quarantine entries from assets, ignoring malformed entries and read failures.
     *
     * @param context Asset context, or null to leave current entries unchanged.
     */
    @Synchronized
    fun load(context: Context?) {
        if (context == null) return
        try {
            context.assets.open("rule-quarantine.txt").use { stream ->
                BufferedReader(InputStreamReader(stream)).forEachLine { line ->
                    val s = line.trim()
                    if (s.isNotEmpty() && !s.startsWith('#')) {
                        val parts = s.split('\t')
                        if (parts.size >= 2) {
                            try {
                                val kind = RuleKind.valueOf(parts[0].trim().uppercase())
                                val host = parts[1].trim().lowercase()
                                quarantined.add(kind to host)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Checks the exact kind/hostname pair without additional normalization.
     *
     * @param kind Candidate rule scope.
     * @param host Normalized rule hostname.
     * @return Whether the candidate pair is quarantined.
     */
    fun contains(kind: RuleKind, host: String): Boolean {
        return (kind to host) in quarantined
    }

    /**
     * Adds a quarantine pair for tests.
     *
     * @param kind Rule scope.
     * @param host Hostname stored without normalization.
     */
    @androidx.annotation.VisibleForTesting
    fun addForTest(kind: RuleKind, host: String) {
        quarantined.add(kind to host)
    }

    /**
     * Removes all quarantine entries for tests.
     */
    @androidx.annotation.VisibleForTesting
    fun clearForTest() {
        quarantined.clear()
    }
}
