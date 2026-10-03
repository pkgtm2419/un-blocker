package com.unblocker.app.logic.rules

import com.unblocker.app.logic.DomainName

/** Sorted reference table: shared canonical strings, no per-domain hash nodes. */
class PackedRuleMatcher(ruleSet:CompiledRuleSet) {
    private val rules=ruleSet.rules.sortedBy { it.value }.toTypedArray()
    fun match(raw:String):DnsRule? {
        val domain=DomainName.normalize(raw) ?: return null
        for(action in RuleAction.entries) {
            var suffix=domain
            while(true) {
                find(suffix,action,if(suffix==domain) RuleKind.EXACT else RuleKind.WILDCARD)?.let {
                    // Exact takes precedence; on subdomains SUFFIX takes precedence over wildcard.
                    if(suffix==domain) return it
                    return find(suffix,action,RuleKind.SUFFIX) ?: it
                }
                find(suffix,action,RuleKind.SUFFIX)?.let {return it}
                val dot=suffix.indexOf('.')
                if(dot<0) break
                suffix=suffix.substring(dot+1)
            }
        }
        return null
    }
    private fun find(value:String,action:RuleAction,kind:RuleKind):DnsRule? {
        var low=0
        var high=rules.size
        while(low<high) {
            val mid=(low+high) ushr 1
            if(rules[mid].value<value) low=mid+1 else high=mid
        }
        var match:DnsRule?=null
        while(low<rules.size && rules[low].value==value) {
            val r=rules[low++]
            if(r.action==action && r.kind==kind) match=r
        }
        return match
    }
}
