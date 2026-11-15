package com.unblocker.app.logic.analysis

/**
 * Curated exact-host and suffix exceptions used to preserve selected mobile app dependencies.
 *
 * The decision pipeline applies user rules before these compatibility exceptions.
 */
object MobileUnbreakList {

    /**
     * Selected exact service hostnames exempted by the mobile compatibility policy.
     */
    private val essentialAppDomains = setOf(
        // Supercell Games (Clash of Clans, Brawl Stars, Clash Royale) - Game traffic & ID login
        "accounts.supercell.com",
        "supercell.com",
        "id.supercell.com",
        "cdn.id.supercell.com",
        "gamea.clashofclans.com",
        "clashofclans.inbox.supercell.com",
        "clash-static.inbox.supercell.com",
        "sdk.social.supercell.com",
        "assets.social.supercell.com",
        "images.store.supercell.com",
        "store.supercell.com",
        "event-assets.clashofclans.com",
        "game-assets.store.supercell.com",
        "security.id.supercell.com",

        // Spotify Authentication & Audio Transport
        "login5.spotify.com",
        "spclient.wg.spotify.com",
        "ap.spotify.com",
        "dealer.spotify.com",
        "apresolve.spotify.com",
        "audio-ak.spotifycdn.com",
        "gae2-spclient.spotify.com",
        "gae2-dealer.g2.spotify.com",

        // Messaging & WhatsApp CDN Delivery
        "static.whatsapp.net",
        "media.whatsapp.net",

        // Google Play Store & System Integrity
        "prod-lt-playstoregatewayadapter-pa.googleapis.com",
        "playstoregatewayadapter-pa.googleapis.com",
        "connectivitycheck.gstatic.com",
        "clients2.google.com",

        // Samsung Galaxy Store App Updates
        "img.samsungapps.com",
        "gtgapi.samsungapps.com",
        "api.glb.samsung-gamelauncher.com"
    )

    private val essentialSuffixes = listOf(
        ".cdn.whatsapp.net",
        ".akamaized.net"
    )

    /**
     * Reward ad networks and shopping redirect domains allowed strictly under BASIC mode
     * to enable in-game revives, video rewards, and affiliate deal links.
     */
    private val rewardAndShoppingDomains = setOf(
        "googleadservices.com",
        "clickserve.dartsearch.net",
        "click.linksynergy.com",
        "reward.unityads.unity3d.com",
        "reward.applovin.com",
        "reward-api.ironsrc.com",
        "api.vungle.com"
    )

    private val rewardAndShoppingSuffixes = listOf(
        ".googleadservices.com",
        ".dartsearch.net",
        ".linksynergy.com"
    )

    /**
     * Checks selected exact service hosts and strict suffix exceptions.
     *
     * @param domain Hostname to trim and lowercase.
     * @return Whether the mobile compatibility list matches.
     */
    fun isEssentialDomain(domain: String): Boolean {
        val lower = domain.trim().lowercase()
        if (lower in essentialAppDomains) return true
        for (suffix in essentialSuffixes) {
            if (lower.endsWith(suffix)) return true
        }
        return false
    }

    /**
     * Checks reward and shopping exceptions used by BASIC filtering.
     *
     * @param domain Hostname to trim and lowercase.
     * @return Whether the hostname matches an exact entry or strict suffix exception.
     */
    fun isRewardOrShoppingDomain(domain: String): Boolean {
        val lower = domain.trim().lowercase()
        if (lower in rewardAndShoppingDomains) return true
        for (suffix in rewardAndShoppingSuffixes) {
            if (lower.endsWith(suffix)) return true
        }
        return false
    }
}
