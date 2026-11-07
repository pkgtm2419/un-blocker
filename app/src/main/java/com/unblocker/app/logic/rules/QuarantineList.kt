package com.unblocker.app.logic.rules

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

object QuarantineList {
    private val quarantined = HashSet<Pair<RuleKind, String>>()

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

    fun contains(kind: RuleKind, host: String): Boolean {
        return (kind to host) in quarantined
    }

    @androidx.annotation.VisibleForTesting
    fun addForTest(kind: RuleKind, host: String) {
        quarantined.add(kind to host)
    }

    @androidx.annotation.VisibleForTesting
    fun clearForTest() {
        quarantined.clear()
    }
}
