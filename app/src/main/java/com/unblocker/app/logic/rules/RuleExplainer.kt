package com.unblocker.app.logic.rules

import com.unblocker.app.domain.model.BlockingDecision
import com.unblocker.app.domain.usecase.DecideBlockingUseCase
import com.unblocker.app.logic.analysis.ProtectedDomains
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Policy result with best-effort matching extension-file provenance.
 *
 * @property domain Original caller-supplied hostname.
 * @property decision Current pipeline decision.
 * @property isProtected Whether the supplied protection policy matches.
 * @property matchingListIds Names of rule files containing a matching parsed rule.
 * @property matchedRuleText Decision rule value, otherwise the first matching raw rule line, if available.
 */
data class Explanation(
    val domain: String,
    val decision: BlockingDecision,
    val isProtected: Boolean,
    val matchingListIds: List<String>,
    val matchedRuleText: String?,
)

/**
 * Evaluates policy and scans installed rule files for diagnostic provenance.
 *
 * @param decideBlockingUseCase Current decision pipeline; evaluation may update local evidence.
 * @param protectedDomains Host protection policy for diagnostics.
 * @param extensionsDirSupplier Directory containing .rules files, or null to omit provenance.
 */
class RuleExplainer(
    private val decideBlockingUseCase: DecideBlockingUseCase,
    private val protectedDomains: ProtectedDomains = ProtectedDomains.DEFAULT,
    private val extensionsDirSupplier: () -> File? = { null },
) {
    /**
     * Evaluates the hostname and scans matching extension lines on the IO dispatcher.
     *
     * @param rawDomain Hostname retained in the explanation.
     * @return Decision and best-effort provenance; individual file-read failures are ignored.
     */
    suspend fun explain(rawDomain: String): Explanation =
        withContext(Dispatchers.IO) {
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
                                val matches =
                                    when (r.kind) {
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
                    } catch (_: Exception) {
                    }
                }
            }

            Explanation(
                domain = rawDomain,
                decision = decision,
                isProtected = isProtected,
                matchingListIds = matchingLists,
                matchedRuleText = matchedRuleText ?: decision.ruleValue,
            )
        }
}
