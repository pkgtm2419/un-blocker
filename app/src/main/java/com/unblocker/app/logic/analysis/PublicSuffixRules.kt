package com.unblocker.app.logic.analysis

import com.unblocker.app.logic.DomainName
import java.net.IDN

/** Offline PSL is structural context, never a block/allow authority. Includes private rules. */
class PublicSuffixRules(lines: Sequence<String>) {
    private val exact = HashSet<String>()
    private val wildcard = HashSet<String>()
    private val exceptions = HashSet<String>()

    init {
        lines.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("//") }.forEach { raw ->
            val target = when {
                raw.startsWith('!') -> exceptions
                raw.startsWith("*.") -> wildcard
                else -> exact
            }
            val value = raw.removePrefix("!").removePrefix("*.").split('.')
                .joinToString(".") { IDN.toASCII(it, IDN.ALLOW_UNASSIGNED).lowercase(java.util.Locale.ROOT) }
            target.add(value)
        }
    }

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

    fun subdomain(raw: String): String {
        val domain = DomainName.normalize(raw) ?: return ""
        val base = registrableDomain(domain) ?: return ""
        return if (domain == base) "" else domain.removeSuffix(".$base")
    }

    companion object {
        val fallback = PublicSuffixRules("com\nnet\norg\nco.uk\ncom.au\nco.in\nblogspot.com\n*.ck\n!www.ck".lineSequence())
        fun from(context: android.content.Context?): PublicSuffixRules = if (context == null) fallback else
            context.assets.open("public_suffix_list.dat").bufferedReader().use { PublicSuffixRules(it.lineSequence()) }
    }
}
