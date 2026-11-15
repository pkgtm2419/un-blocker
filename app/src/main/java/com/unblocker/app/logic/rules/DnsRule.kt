package com.unblocker.app.logic.rules

import com.unblocker.app.domain.model.BlockingCategory

/**
 * Rule action evaluated with allow-rule precedence within a rule source.
 */
enum class RuleAction { ALLOW, BLOCK }

/**
 * Hostname scope: exact host, host plus descendants, or strict descendants only.
 */
enum class RuleKind { EXACT, SUFFIX, WILDCARD }

/**
 * Normalized domain rule and source metadata.
 *
 * @property action Allow or block action.
 * @property kind Hostname matching scope.
 * @property value Rule hostname without syntax prefixes.
 * @property category Content category attached to matches.
 * @property sourceId Numeric source identifier; zero is the default/unattributed source.
 */
data class DnsRule(
    val action: RuleAction,
    val kind: RuleKind,
    val value: String,
    val category: BlockingCategory = BlockingCategory.AD,
    val sourceId: Int = 0,
)

/**
 * Deduplicated rule collection prepared for matching or binary encoding.
 *
 * @param rules Rules to copy and deduplicate.
 */
class CompiledRuleSet(
    rules: List<DnsRule>,
) {
    /**
     * Distinct rules in first-occurrence order.
     */
    val rules: List<DnsRule> = rules.distinct().toList()

    /**
     * Parses compiled textual rule assets.
     */
    companion object {
        /**
         * Parses five-column TSV records and applies hostname admission.
         *
         * @param reader Source containing action, kind, host, category and source ID columns.
         * @return Deduplicated admitted rules; invalid hostnames and unsafe rules are skipped.
         * @throws IllegalArgumentException If columns, enum values or source IDs are invalid.
         * @throws java.io.IOException If reading fails.
         */
        fun fromTsv(reader: java.io.Reader): CompiledRuleSet {
            val admission = DefaultRuleAdmission()
            val rules =
                reader
                    .buffered()
                    .lineSequence()
                    .filter { it.isNotBlank() }
                    .mapNotNull { line ->
                        val fields = line.split('\t')
                        require(fields.size == 5) { "Invalid compiled DNS asset" }
                        val source = fields[4].toInt()
                        require(source >= 0) { "Invalid rule source" }
                        val action = RuleAction.valueOf(fields[0])
                        val kind = RuleKind.valueOf(fields[1])
                        val domain =
                            com.unblocker.app.logic.DomainName
                                .normalize(fields[2]) ?: return@mapNotNull null
                        if (admission.check(action, kind, domain) != null) return@mapNotNull null
                        DnsRule(action, kind, domain, BlockingCategory.valueOf(fields[3]), source)
                    }.toList()
            return CompiledRuleSet(rules)
        }
    }
}
