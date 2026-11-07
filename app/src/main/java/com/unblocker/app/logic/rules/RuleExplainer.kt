package com.unblocker.app.logic.rules

import com.unblocker.app.domain.model.BlockingDecision
import com.unblocker.app.domain.usecase.DecideBlockingUseCase
import com.unblocker.app.logic.analysis.ProtectedDomains
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class Explanation(
    val domain: String,
    val decision: BlockingDecision,
    val isProtected: Boolean,
    val matchingListIds: List<String>,
    val matchedRuleText: String?
)

class RuleExplainer(
    private val decideBlockingUseCase: DecideBlockingUseCase,
    private val protectedDomains: ProtectedDomains = ProtectedDomains.DEFAULT,
    private val extensionsDirSupplier: () -> File? = { null }
) {
    suspend fun explain(rawDomain: String): Explanation = withContext(Dispatchers.IO) {
        val decision = decideBlockingUseCase(rawDomain)
        val isProtected = protectedDomains.isProtected(rawDomain)
        val matchingLists = mutableListOf<String>()
        var matchedRuleText: String? = decision.ruleValue

        val extDir = extensionsDirSupplier()
        if (extDir != null && extDir.isDirectory) {
            val ruleFiles = extDir.listFiles { f -> f.extension == "rules" } ?: emptyArray()
            val parser = RuleLineParser()
            for (file in ruleFiles) {
                val listId = file.nameWithoutExtension
                var fileMatched = false
                try {
                    file.forEachLine { line ->
                        if (fileMatched) return@forEachLine
                        val parsed = parser.parse(line)
                        if (parsed is ParsedLine.Rule) {
                            val r = parsed.rule
                            val domain = rawDomain.lowercase()
                            val ruleHost = r.value.lowercase()
                            val matches = when (r.kind) {
                                RuleKind.EXACT -> domain == ruleHost
                                RuleKind.SUFFIX -> domain == ruleHost || domain.endsWith(".$ruleHost")
                                RuleKind.WILDCARD -> domain.endsWith(".$ruleHost")
                            }
                            if (matches) {
                                matchingLists.add(listId)
                                if (matchedRuleText == null) {
                                    matchedRuleText = line.trim()
                                }
                                fileMatched = true
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        Explanation(
            domain = rawDomain,
            decision = decision,
            isProtected = isProtected,
            matchingListIds = matchingLists,
            matchedRuleText = matchedRuleText ?: decision.ruleValue
        )
    }
}
