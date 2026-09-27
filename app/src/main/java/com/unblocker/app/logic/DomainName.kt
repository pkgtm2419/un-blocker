package com.unblocker.app.logic

import java.util.Locale

/** Canonical validation boundary for DNS names accepted by the filtering engine. */
object DomainName {
    fun normalize(raw: String): String? {
        val domain = raw.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (domain.isEmpty() || domain.length > 253) return null

        val labels = domain.split('.')
        if (labels.any { label ->
                label.isEmpty() || label.length > 63 || label.first() == '-' || label.last() == '-' ||
                    label.any { it !in 'a'..'z' && it !in '0'..'9' && it != '-' && it != '_' }
            }) return null

        return domain
    }
}
