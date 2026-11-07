package com.unblocker.app.logic.analysis

import android.content.Context
import com.unblocker.app.logic.DomainName

/**
 * Runtime safety net preventing core infrastructure, captive portals, OS connectivity
 * checks, and vital services from ever being blocked or tracked by the on-device learner.
 */
object NeverBlockPolicy {

    private val STATIC_FALLBACK_HOSTS = setOf(
        "connectivitycheck.gstatic.com",
        "connectivitycheck.android.com",
        "clients3.google.com",
        "captive.apple.com",
        "www.msftconnecttest.com",
        "detectportal.firefox.com",
        "time.android.com",
        "time.google.com",
        "google.com",
        "www.google.com",
        "accounts.google.com",
        "gstatic.com",
        "www.gstatic.com",
        "googleapis.com",
        "play.google.com",
        "apple.com",
        "icloud.com",
        "microsoft.com",
        "android.com",
        "www.android.com",
        "duckduckgo.com",
        "www.duckduckgo.com",
        "bing.com",
        "www.bing.com",
        "yahoo.com",
        "www.yahoo.com",
        "whatsapp.com",
        "whatsapp.net",
        "web.whatsapp.com",
        "telegram.org",
        "signal.org",
        "wikipedia.org",
        "cloudflare.com",
        "github.com",
        // Extended gap hosts (WP3)
        "docs.google.com",
        "mail.google.com",
        "drive.google.com",
        "maps.google.com",
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "i.ytimg.com",
        "play.googleapis.com",
        "firebaseinstallations.googleapis.com",
        "facebook.com",
        "www.facebook.com",
        "instagram.com",
        "www.instagram.com",
        "x.com",
        "twitter.com",
        "reddit.com",
        "www.reddit.com",
        "old.reddit.com",
        "linkedin.com",
        "www.linkedin.com",
        "paypal.com",
        "www.paypal.com",
        "login.live.com",
        "outlook.live.com",
        "amazon.in",
        "www.amazon.in",
        "amazon.com",
        "flipkart.com",
        "www.flipkart.com",
        "paytm.com",
        "phonepe.com",
        "groww.in",
        "zerodha.com",
        "kite.zerodha.com",
        "claude.ai",
        "anthropic.com",
        "openai.com",
        "chatgpt.com",
        "stackoverflow.com",
        "npmjs.com"
    )

    @Volatile
    private var protectedHosts: Set<String> = STATIC_FALLBACK_HOSTS
    
    @Volatile
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                val lines = context.assets.open("never-block-hosts.txt").bufferedReader().use { it.readLines() }
                val loadedSet = lines.map { it.trim().lowercase() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
                protectedHosts = STATIC_FALLBACK_HOSTS + loadedSet
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

    fun getProtectedHosts(): Set<String> = protectedHosts

    fun isNeverBlock(rawDomain: String): Boolean {
        val domain = DomainName.normalize(rawDomain) ?: return false
        if (domain in protectedHosts) return true
        if (domain.startsWith("www.") && domain.removePrefix("www.") in protectedHosts) return true
        for (host in protectedHosts) {
            if (host.startsWith("*.")) {
                val suffix = host.substring(2)
                if (domain == suffix || domain.endsWith(".$suffix")) return true
            }
        }
        return false
    }
}
