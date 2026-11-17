package com.unblocker.app.logic.analysis

import com.unblocker.app.logic.DomainName
import java.net.IDN

/**
 * Offline public suffix context, including supplied private rules; it does not authorize blocking.
 *
 * @param lines PSL-format exact, wildcard and exception entries.
 * @throws IllegalArgumentException If an entry cannot be converted to ASCII.
 */
class PublicSuffixRules(
    lines: Sequence<String>,
) {
    private val exact = HashSet<String>()
    private val wildcard = HashSet<String>()
    private val exceptions = HashSet<String>()

    init {
        lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("//") }.forEach { raw ->
            val target =
                when {
                    raw.startsWith('!') -> exceptions
                    raw.startsWith("*.") -> wildcard
                    else -> exact
                }
            val value =
                raw
                    .removePrefix("!")
                    .removePrefix("*.")
                    .split('.')
                    .joinToString(".") { IDN.toASCII(it, IDN.ALLOW_UNASSIGNED).lowercase(java.util.Locale.ROOT) }
            target.add(value)
        }
    }

    /**
     * Finds the registrable hostname using exact, wildcard and exception suffix rules.
     *
     * @param raw Hostname to normalize.
     * @return Registrable name, or null for invalid names or suffix-only inputs.
     */
    fun registrableDomain(raw: String): String? {
        val domain = DomainName.normalize(raw) ?: return null
        val labels = domain.split('.')
        var suffixLength = 1 // PSL implicit wildcard for unknown TLDs.
        for (i in labels.indices) {
            val suffix = labels.drop(i).joinToString(".")
            if (suffix in exceptions) {
                suffixLength = labels.size - i - 1
                break
            }
            if (suffix in exact) suffixLength = maxOf(suffixLength, labels.size - i)
            if (i > 0 && suffix in wildcard) suffixLength = maxOf(suffixLength, labels.size - i + 1)
        }
        return if (labels.size <= suffixLength) null else labels.takeLast(suffixLength + 1).joinToString(".")
    }

    /**
     * Returns the labels below the registrable domain.
     *
     * @param raw Hostname to normalize.
     * @return Subdomain labels, or empty when invalid or no labels remain.
     */
    fun subdomain(raw: String): String {
        val domain = DomainName.normalize(raw) ?: return ""
        val base = registrableDomain(domain) ?: return ""
        return if (domain == base) "" else domain.removeSuffix(".$base")
    }

    /**
     * Checks explicit and wildcard suffixes, then the implicit single-label suffix rule.
     *
     * @param raw Hostname to normalize.
     * @return Whether the hostname is a suffix; invalid input returns false.
     */
    fun isPublicSuffix(raw: String): Boolean {
        val domain = DomainName.normalize(raw) ?: return false
        if (domain in exact) return true
        val dot = domain.indexOf('.')
        if (dot != -1) {
            val parent = domain.substring(dot + 1)
            if (parent in wildcard && domain !in exceptions) return true
        }
        return registrableDomain(domain) == null
    }

    /**
     * Creates suffix context from packaged assets or a small fallback set.
     */
    companion object {
        /**
         * Small embedded suffix context for tests and callers without Android assets.
         */
        val fallback =
            PublicSuffixRules(
                "com\nnet\norg\nco.uk\ncom.au\nco.in\nblogspot.com\ngithub.io\n*.ck\n!www.ck".lineSequence(),
            )

        @Volatile
        private var cachedInstance: PublicSuffixRules? = null

        /**
         * Loads packaged public suffix rules, caching the parsed rules for subsequent callers,
         * or using fallback when context is null.
         *
         * @param context Asset context, or null to select the fallback set.
         * @return Parsed suffix context.
         * @throws java.io.IOException If opening or reading the asset fails.
         */
        fun from(context: android.content.Context?): PublicSuffixRules {
            if (context == null) return fallback
            cachedInstance?.let { return it }
            return synchronized(this) {
                cachedInstance ?: run {
                    val loaded =
                        context.assets
                            .open(
                                "public_suffix_list.dat",
                            ).bufferedReader()
                            .use { PublicSuffixRules(it.lineSequence()) }
                    cachedInstance = loaded
                    loaded
                }
            }
        }
    }
}
