package com.unblocker.app.logic.rules

import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.logic.DomainName

data class RuleStats(
    val totalRules: Int,
    val skipCounters: Map<SkipReason, Int>
) {
    val skippedCosmetic: Int get() = skipCounters[SkipReason.COSMETIC] ?: 0
    val skippedOptions: Int get() = (skipCounters[SkipReason.UNSUPPORTED_OPTIONS] ?: 0) + (skipCounters[SkipReason.OPTIONS_ON_REGISTRABLE] ?: 0)
    val skippedUnsafe: Int get() = (skipCounters[SkipReason.SINGLE_LABEL] ?: 0) +
        (skipCounters[SkipReason.PUBLIC_SUFFIX] ?: 0) +
        (skipCounters[SkipReason.IP_LITERAL] ?: 0) +
        (skipCounters[SkipReason.PROTECTED_HOST] ?: 0) +
        (skipCounters[SkipReason.QUARANTINED] ?: 0)
    val totalSkipped: Int get() = skipCounters.values.sum()
}

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

    @Volatile
    private var lastStats: RuleStats = RuleStats(0, emptyMap())

    val ruleCount: Int
        get() = snapshot.totalCount

    val stats: RuleStats
        get() = lastStats

    private val lineParser = RuleLineParser()
    private val ruleAdmission: RuleAdmission = DefaultRuleAdmission()

    fun matchRule(rawDomain: String): DnsRule? {
        val domain = DomainName.normalize(rawDomain) ?: return null
        val current = snapshot
        if (current.totalCount == 0) return null

        // 1. Check Allow Rules first (exact and full domain suffix)
        if (domain in current.allowExact) {
            return DnsRule(RuleAction.ALLOW, RuleKind.EXACT, domain, BlockingCategory.AD)
        }
        if (domain in current.allowSuffix) {
            return DnsRule(RuleAction.ALLOW, RuleKind.SUFFIX, domain, BlockingCategory.AD)
        }

        if (current.allowSuffix.isNotEmpty()) {
            var dotIndex = domain.indexOf('.')
            while (dotIndex != -1) {
                val nextDot = domain.indexOf('.', dotIndex + 1)
                // WP2: Never test a single-label parent (e.g., 'com')
                if (nextDot == -1) break

                val parentSuffix = domain.substring(dotIndex + 1)
                if (parentSuffix in current.allowSuffix) {
                    return DnsRule(RuleAction.ALLOW, RuleKind.SUFFIX, parentSuffix, BlockingCategory.AD)
                }
                dotIndex = nextDot
            }
        }

        // 2. Check Block Rules (exact and full domain suffix)
        if (domain in current.blockExact) {
            return DnsRule(RuleAction.BLOCK, RuleKind.EXACT, domain, BlockingCategory.AD)
        }
        if (domain in current.blockSuffix) {
            return DnsRule(RuleAction.BLOCK, RuleKind.SUFFIX, domain, BlockingCategory.AD)
        }

        // Parent domain checks for suffix and wildcard
        if (current.blockSuffix.isNotEmpty() || current.blockWildcard.isNotEmpty()) {
            var dotIndex = domain.indexOf('.')
            while (dotIndex != -1) {
                val nextDot = domain.indexOf('.', dotIndex + 1)
                // WP2: Never test a single-label parent (e.g., 'com')
                if (nextDot == -1) break

                val parentSuffix = domain.substring(dotIndex + 1)
                if (parentSuffix in current.blockSuffix) {
                    return DnsRule(RuleAction.BLOCK, RuleKind.SUFFIX, parentSuffix, BlockingCategory.AD)
                }
                if (parentSuffix in current.blockWildcard) {
                    return DnsRule(RuleAction.BLOCK, RuleKind.WILDCARD, parentSuffix, BlockingCategory.AD)
                }
                dotIndex = nextDot
            }
        }

        return null
    }

    class Builder(
        private val parser: RuleLineParser = lineParser,
        private val admission: RuleAdmission = ruleAdmission
    ) {
        val allowExact = HashSet<String>()
        val allowSuffix = HashSet<String>()
        val blockExact = HashSet<String>()
        val blockSuffix = HashSet<String>()
        val blockWildcard = HashSet<String>()
        val skipCounters = IntArray(SkipReason.values().size)
        var count = 0

        fun addLine(line: String, category: BlockingCategory = BlockingCategory.AD) {
            val ctx = ParseContext(category = category)
            when (val res = parser.parse(line, ctx)) {
                is ParsedLine.Rule -> addRule(res.rule)
                is ParsedLine.Skipped -> skipCounters[res.why.ordinal]++
            }
        }

        fun addRule(rule: DnsRule) {
            val skip = admission.check(rule.action, rule.kind, rule.value)
            if (skip != null) {
                skipCounters[skip.ordinal]++
                return
            }
            val added = when (rule.action) {
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
            if (added) {
                count++
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

        fun toStats(): RuleStats {
            val map = SkipReason.values().associateWith { skipCounters[it.ordinal] }
            return RuleStats(count, map)
        }
    }

    @Synchronized
    fun applyBuilder(builder: Builder) {
        snapshot = builder.build()
        lastStats = builder.toStats()
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
        val parsed = lineParser.parse(rawLine, ParseContext(category = defaultCategory))
        return (parsed as? ParsedLine.Rule)?.rule
    }

    fun parseLine(rawLine: String, ctx: ParseContext): ParsedLine {
        return lineParser.parse(rawLine, ctx)
    }

    fun clearForTesting() {
        snapshot = RuleSnapshot.EMPTY
        lastStats = RuleStats(0, emptyMap())
    }
}
