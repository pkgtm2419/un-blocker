package com.unblocker.app.logic.rules

import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.logic.DomainName

/**
 * Counts from parsing and admitting an extension-rule snapshot.
 *
 * @property totalRules Distinct admitted rule count.
 * @property skipCounters Number of rejected lines/rules for each skip reason.
 */
data class RuleStats(
    val totalRules: Int,
    val skipCounters: Map<SkipReason, Int>
) {
    /**
     * Number of lines skipped for cosmetic filtering syntax.
     */
    val skippedCosmetic: Int get() = skipCounters[SkipReason.COSMETIC] ?: 0
    /**
     * Number skipped for unsupported options or options on a registrable domain.
     */
    val skippedOptions: Int get() = (skipCounters[SkipReason.UNSUPPORTED_OPTIONS] ?: 0) + (skipCounters[SkipReason.OPTIONS_ON_REGISTRABLE] ?: 0)
    /**
     * Number skipped for single-label, public-suffix, IP, protected-host or quarantine restrictions.
     */
    val skippedUnsafe: Int get() = (skipCounters[SkipReason.SINGLE_LABEL] ?: 0) +
        (skipCounters[SkipReason.PUBLIC_SUFFIX] ?: 0) +
        (skipCounters[SkipReason.IP_LITERAL] ?: 0) +
        (skipCounters[SkipReason.PROTECTED_HOST] ?: 0) +
        (skipCounters[SkipReason.QUARANTINED] ?: 0)
    /**
     * Sum of all recorded skip counters.
     */
    val totalSkipped: Int get() = skipCounters.values.sum()
}

/**
 * Publishes in-memory extension-rule snapshots; matching performs no network I/O.
 *
 * Allow entries are checked before block entries, including ancestor suffix lookups.
 */
object ExtensionRuleStore {

    internal class RuleSnapshot(
        val allowExact: Set<String>,
        val allowSuffix: Set<String>,
        val allowWildcard: Set<String>,
        val blockExact: Set<String>,
        val blockSuffix: Set<String>,
        val blockWildcard: Set<String>,
        val totalCount: Int
    ) {
        companion object {
            val EMPTY = RuleSnapshot(
                emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet(), 0
            )
        }
    }

    @Volatile
    private var snapshot: RuleSnapshot = RuleSnapshot.EMPTY

    @Volatile
    private var lastStats: RuleStats = RuleStats(0, emptyMap())

    /**
     * Distinct rule count in the currently published snapshot.
     */
    val ruleCount: Int
        get() = snapshot.totalCount

    /**
     * Statistics from the most recently applied builder.
     */
    val stats: RuleStats
        get() = lastStats

    private val lineParser = RuleLineParser()
    private val ruleAdmission: RuleAdmission = DefaultRuleAdmission()

    /**
     * Matches normalized names against the current snapshot with allow precedence.
     *
     * @param rawDomain Hostname to normalize and match.
     * @return Matching rule, or null for invalid names and unmatched input.
     */
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

        if (current.allowSuffix.isNotEmpty() || current.allowWildcard.isNotEmpty()) {
            var dotIndex = domain.indexOf('.')
            while (dotIndex != -1) {
                val nextDot = domain.indexOf('.', dotIndex + 1)
                // WP2: Never test a single-label parent (e.g., 'com')
                if (nextDot == -1) break

                val parentSuffix = domain.substring(dotIndex + 1)
                if (parentSuffix in current.allowSuffix) {
                    return DnsRule(RuleAction.ALLOW, RuleKind.SUFFIX, parentSuffix, BlockingCategory.AD)
                }
                if (parentSuffix in current.allowWildcard) {
                    return DnsRule(RuleAction.ALLOW, RuleKind.WILDCARD, parentSuffix, BlockingCategory.AD)
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

    /**
     * Accumulates admitted extension rules before publication.
     *
     * @param parser Line parser for supported DNS filter syntax.
     * @param admission Safety policy applied to supplied rules.
     */
    class Builder(
        private val parser: RuleLineParser = lineParser,
        private val admission: RuleAdmission = ruleAdmission
    ) {
        /**
         * Mutable exact-host allow entries accumulated by this builder.
         */
        val allowExact = HashSet<String>()
        /**
         * Mutable allow entries covering the root hostname and descendants.
         */
        val allowSuffix = HashSet<String>()
        /**
         * Mutable allow entries covering strict descendants of the root hostname.
         */
        val allowWildcard = HashSet<String>()
        /**
         * Mutable exact-host block entries accumulated by this builder.
         */
        val blockExact = HashSet<String>()
        /**
         * Mutable block entries covering the root hostname and descendants.
         */
        val blockSuffix = HashSet<String>()
        /**
         * Mutable block entries covering strict descendants of the root hostname.
         */
        val blockWildcard = HashSet<String>()
        /**
         * Mutable rejection counters indexed by SkipReason ordinal.
         */
        val skipCounters = IntArray(SkipReason.values().size)
        /**
         * Number of distinct entries accepted through addRule; direct set mutation does not adjust it.
         */
        var count = 0

        /**
         * Parses one line and either admits its rule or counts its skip reason.
         *
         * @param line Raw filter-list line.
         * @param category Default content category attached to a parsed rule.
         */
        fun addLine(line: String, category: BlockingCategory = BlockingCategory.AD) {
            val ctx = ParseContext(category = category)
            when (val res = parser.parse(line, ctx)) {
                is ParsedLine.Rule -> addRule(res.rule)
                is ParsedLine.Skipped -> skipCounters[res.why.ordinal]++
            }
        }

        /**
         * Applies admission and inserts a rule into its action/scope set.
         *
         * @param rule Rule to admit; rejected rules increment their skip counter.
         */
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
                        RuleKind.SUFFIX -> allowSuffix.add(rule.value)
                        RuleKind.WILDCARD -> allowWildcard.add(rule.value)
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
                allowWildcard = allowWildcard,
                blockExact = blockExact,
                blockSuffix = blockSuffix,
                blockWildcard = blockWildcard,
                totalCount = count
            )
        }

        /**
         * Returns a snapshot of admitted count and all skip counters.
         */
        fun toStats(): RuleStats {
            val map = SkipReason.values().associateWith { skipCounters[it.ordinal] }
            return RuleStats(count, map)
        }
    }

    /**
     * Publishes the builder sets and statistics; callers must stop mutating the builder afterward.
     *
     * @param builder Completed builder whose set references become the active snapshot.
     */
    @Synchronized
    fun applyBuilder(builder: Builder) {
        snapshot = builder.build()
        lastStats = builder.toStats()
    }

    /**
     * Builds and publishes an admitted rule snapshot.
     *
     * @param rules Candidate rules, deduplicated by action/scope membership.
     */
    @Synchronized
    fun setRules(rules: Collection<DnsRule>) {
        val builder = Builder()
        for (rule in rules) {
            builder.addRule(rule)
        }
        applyBuilder(builder)
    }

    /**
     * Parses one supported rule line without publishing it.
     *
     * @param rawLine Raw filter-list line.
     * @param defaultCategory Category applied to an accepted rule.
     * @return Parsed rule, or null when skipped.
     */
    fun parseLine(rawLine: String, defaultCategory: BlockingCategory = BlockingCategory.AD): DnsRule? {
        val parsed = lineParser.parse(rawLine, ParseContext(category = defaultCategory))
        return (parsed as? ParsedLine.Rule)?.rule
    }

    /**
     * Parses one line while preserving its skip reason.
     *
     * @param rawLine Raw filter-list line.
     * @param ctx Source, security-list and category context.
     * @return Accepted rule or explicit skipped result.
     */
    fun parseLine(rawLine: String, ctx: ParseContext): ParsedLine {
        return lineParser.parse(rawLine, ctx)
    }

    /**
     * Clears the active snapshot and statistics for tests.
     */
    fun clearForTesting() {
        snapshot = RuleSnapshot.EMPTY
        lastStats = RuleStats(0, emptyMap())
    }
}
