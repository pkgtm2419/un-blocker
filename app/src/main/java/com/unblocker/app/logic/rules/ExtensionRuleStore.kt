package com.unblocker.app.logic.rules

import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.logic.DomainName

/**
 * Thread-safe, ultra-low latency in-memory rule store for dynamic extensions and filter lists.
 * Evaluates rules with zero network delay and provides O(1) set-based lookups.
 */
object ExtensionRuleStore {

    internal class RuleSnapshot(
        val allowExact: Set<String>,
        val allowSuffix: Set<String>,
        val blockExact: Set<String>,
        val blockSuffix: Set<String>,
        val blockWildcard: Set<String>,
        val totalCount: Int
    ) {
        companion object {
            val EMPTY = RuleSnapshot(
                emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), 0
            )
        }
    }

    @Volatile
    private var snapshot: RuleSnapshot = RuleSnapshot.EMPTY

    val ruleCount: Int
        get() = snapshot.totalCount

    fun matchRule(rawDomain: String): DnsRule? {
        val domain = DomainName.normalize(rawDomain) ?: return null
        val current = snapshot
        if (current.totalCount == 0) return null

        // 1. Check Allow Rules first
        if (domain in current.allowExact) {
            return DnsRule(RuleAction.ALLOW, RuleKind.EXACT, domain, BlockingCategory.AD)
        }

        val parts = domain.split('.')
        val labelCount = parts.size

        for (i in 0 until labelCount) {
            val suffix = parts.subList(i, labelCount).joinToString(".")
            if (suffix in current.allowSuffix) {
                return DnsRule(RuleAction.ALLOW, RuleKind.SUFFIX, suffix, BlockingCategory.AD)
            }
        }

        // 2. Check Block Rules
        if (domain in current.blockExact) {
            return DnsRule(RuleAction.BLOCK, RuleKind.EXACT, domain, BlockingCategory.AD)
        }

        for (i in 0 until labelCount) {
            val suffix = parts.subList(i, labelCount).joinToString(".")
            if (suffix in current.blockSuffix) {
                return DnsRule(RuleAction.BLOCK, RuleKind.SUFFIX, suffix, BlockingCategory.AD)
            }
            if (i > 0 && suffix in current.blockWildcard) {
                return DnsRule(RuleAction.BLOCK, RuleKind.WILDCARD, suffix, BlockingCategory.AD)
            }
        }

        return null
    }

    class Builder {
        val allowExact = HashSet<String>()
        val allowSuffix = HashSet<String>()
        val blockExact = HashSet<String>()
        val blockSuffix = HashSet<String>()
        val blockWildcard = HashSet<String>()
        var count = 0

        fun addLine(line: String, category: BlockingCategory = BlockingCategory.AD) {
            val rule = parseLine(line, category) ?: return
            addRule(rule)
        }

        fun addRule(rule: DnsRule) {
            count++
            when (rule.action) {
                RuleAction.ALLOW -> {
                    when (rule.kind) {
                        RuleKind.EXACT -> allowExact.add(rule.value)
                        RuleKind.SUFFIX, RuleKind.WILDCARD -> allowSuffix.add(rule.value)
                    }
                }
                RuleAction.BLOCK -> {
                    when (rule.kind) {
                        RuleKind.EXACT -> blockExact.add(rule.value)
                        RuleKind.SUFFIX -> blockSuffix.add(rule.value)
                        RuleKind.WILDCARD -> blockWildcard.add(rule.value)
                    }
                }
            }
        }

        internal fun build(): RuleSnapshot {
            return RuleSnapshot(
                allowExact = allowExact,
                allowSuffix = allowSuffix,
                blockExact = blockExact,
                blockSuffix = blockSuffix,
                blockWildcard = blockWildcard,
                totalCount = count
            )
        }
    }

    @Synchronized
    fun applyBuilder(builder: Builder) {
        snapshot = builder.build()
    }

    @Synchronized
    fun setRules(rules: Collection<DnsRule>) {
        val builder = Builder()
        for (rule in rules) {
            builder.addRule(rule)
        }
        applyBuilder(builder)
    }

    fun parseLine(rawLine: String, defaultCategory: BlockingCategory = BlockingCategory.AD): DnsRule? {
        var line = rawLine.trim()
        if (line.isEmpty() || line.startsWith('!') || line.startsWith('#')) return null

        // Strip options e.g. $third-party,script,important
        val dollarIdx = line.indexOf('$')
        if (dollarIdx >= 0) {
            line = line.substring(0, dollarIdx).trim()
        }
        if (line.isEmpty()) return null

        // Strip hosts prefix (0.0.0.0 or 127.0.0.1)
        if (line.startsWith("0.0.0.0 ") || line.startsWith("0.0.0.0\t")) {
            line = line.substring(8).trim()
        } else if (line.startsWith("127.0.0.1 ") || line.startsWith("127.0.0.1\t")) {
            line = line.substring(10).trim()
        }

        var action = RuleAction.BLOCK
        if (line.startsWith("@@")) {
            action = RuleAction.ALLOW
            line = line.substring(2)
        }

        val kind: RuleKind
        if (line.startsWith("||")) {
            line = line.substring(2)
            if (line.endsWith('^')) {
                line = line.substring(0, line.length - 1)
            }
            kind = RuleKind.SUFFIX
        } else if (line.startsWith("*.")) {
            line = line.substring(2)
            kind = RuleKind.WILDCARD
        } else {
            if (line.endsWith('^')) {
                line = line.substring(0, line.length - 1)
            }
            kind = RuleKind.EXACT
        }

        val normalized = DomainName.normalize(line) ?: return null
        return DnsRule(
            action = action,
            kind = kind,
            value = normalized,
            category = defaultCategory
        )
    }

    fun clearForTesting() {
        snapshot = RuleSnapshot.EMPTY
    }
}
