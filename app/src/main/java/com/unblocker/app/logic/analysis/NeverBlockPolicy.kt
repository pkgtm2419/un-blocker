package com.unblocker.app.logic.analysis

import android.content.Context
import com.unblocker.app.logic.DomainName

/**
 * Runtime safety net preventing core infrastructure, captive portals, OS connectivity
 * checks, and vital services from ever being blocked or tracked by the on-device learner.
 */
object NeverBlockPolicy {

    @Volatile
    private var protectedHosts: Set<String> = emptySet()
    
    @Volatile
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                val lines = context.assets.open("never-block-hosts.txt").bufferedReader().use { it.readLines() }
                protectedHosts = lines.map { it.trim().lowercase() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
            } catch (_: Exception) {
            }
            loaded = true
        }
    }

    @androidx.annotation.VisibleForTesting
    fun injectForTest(hosts: Set<String>) {
        protectedHosts = hosts
        loaded = true
    }

    fun isNeverBlock(rawDomain: String): Boolean {
        val domain = DomainName.normalize(rawDomain) ?: return false
        val hosts = protectedHosts
        if (domain in hosts) return true
        for (host in hosts) {
            if (host.startsWith("*.")) {
                val suffix = host.substring(2)
                if (domain.endsWith(".$suffix") || domain == suffix) {
                    return true
                }
            }
        }
        return false
    }
}
