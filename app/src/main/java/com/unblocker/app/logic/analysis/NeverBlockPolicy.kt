package com.unblocker.app.logic.analysis

import com.unblocker.app.logic.DomainName

/**
 * Runtime safety net preventing core infrastructure, captive portals, OS connectivity
 * checks, and vital services from ever being blocked or tracked by the on-device learner.
 */
object NeverBlockPolicy {

    private val protectedHosts = setOf(
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
        "mtalk.google.com",
        "apple.com",
        "icloud.com",
        "microsoft.com",
        "login.microsoftonline.com",
        "github.com",
        "api.github.com",
        "raw.githubusercontent.com",
        "registry.npmjs.org",
        "pypi.org",
        "cdnjs.cloudflare.com",
        "cdn.jsdelivr.net",
        "fonts.googleapis.com",
        "cloudflare.com",
        "wikipedia.org",
        "whatsapp.com",
        "whatsapp.net",
        "telegram.org",
        "signal.org",
        "paypal.com",
        "amazon.com"
    )

    fun isNeverBlock(rawDomain: String): Boolean {
        val domain = DomainName.normalize(rawDomain) ?: return false
        if (domain in protectedHosts) return true
        for (host in protectedHosts) {
            if (domain.endsWith(".$host")) {
                return true
            }
        }
        return false
    }
}
