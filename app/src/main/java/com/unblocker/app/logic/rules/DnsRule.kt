package com.unblocker.app.logic.rules

import com.unblocker.app.domain.model.BlockingCategory

enum class RuleAction { ALLOW, BLOCK }
enum class RuleKind { EXACT, SUFFIX, WILDCARD }

data class DnsRule(
    val action: RuleAction,
    val kind: RuleKind,
    val value: String,
    val category: BlockingCategory = BlockingCategory.AD,
    val sourceId: Int = 0
)

class CompiledRuleSet(rules: List<DnsRule>) {
    val rules: List<DnsRule> = rules.distinct().toList()
    companion object {
        fun fromTsv(reader: java.io.Reader): CompiledRuleSet {
            val rules=reader.buffered().lineSequence().filter { it.isNotBlank() }.map { line ->
                val fields=line.split('\t')
                require(fields.size==5) { "Invalid compiled DNS asset" }
                val source=fields[4].toInt()
                require(source>=0) { "Invalid rule source" }
                DnsRule(RuleAction.valueOf(fields[0]),RuleKind.valueOf(fields[1]),
                    requireNotNull(com.unblocker.app.logic.DomainName.normalize(fields[2])),
                    BlockingCategory.valueOf(fields[3]),source)
            }.toList()
            return CompiledRuleSet(rules)
        }
    }
}
