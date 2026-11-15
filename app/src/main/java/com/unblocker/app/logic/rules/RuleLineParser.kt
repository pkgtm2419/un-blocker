package com.unblocker.app.logic.rules

import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.logic.DomainName
import com.unblocker.app.logic.analysis.PublicSuffixRules

/**
 * Reasons unsupported or unsafe filter-list lines are excluded from DNS rules.
 */
enum class SkipReason {
    BLANK_OR_COMMENT,
    COSMETIC,
    PATH_OR_REGEX,
    UNSUPPORTED_OPTIONS,
    OPTIONS_ON_REGISTRABLE,
    INVALID_HOST,
    SINGLE_LABEL,
    PUBLIC_SUFFIX,
    IP_LITERAL,
    PROTECTED_HOST,
    QUARANTINED,
}

/**
 * Accepted DNS rule or explicit reason why a line was skipped.
 */
sealed interface ParsedLine {
    /**
     * Accepted DNS rule.
     *
     * @property rule Normalized and admitted rule.
     */
    data class Rule(
        val rule: DnsRule,
    ) : ParsedLine

    /**
     * Rejected or non-rule line.
     *
     * @property why Parsing or admission reason for skipping.
     */
    data class Skipped(
        val why: SkipReason,
    ) : ParsedLine
}

/**
 * Metadata controlling filter-line interpretation.
 *
 * @property sourceId Numeric source identifier attached to accepted rules.
 * @property security Whether document-level block options are permitted.
 * @property category Content category attached to accepted rules.
 */
data class ParseContext(
    val sourceId: Int = 0,
    val security: Boolean = false,
    val category: BlockingCategory = BlockingCategory.AD,
)

/**
 * Parses supported hosts/plain-domain and DNS-compatible filter-list syntax.
 *
 * @param admission Safety gate for candidate host rules.
 * @param psl Public suffix context for resource-option restrictions.
 */
class RuleLineParser(
    private val admission: RuleAdmission = DefaultRuleAdmission(),
    @Suppress("unused") private val psl: PublicSuffixRules = PublicSuffixRules.fallback,
) {
    private val cosmetic = arrayOf("##", "#@#", "#?#", "#$#", "#%#", "#@$#", "#@?#", "#@%#", "$$")
    private val hostsIps = setOf("0.0.0.0", "127.0.0.1", "::", "::1")
    private val metaHosts = setOf("localhost", "localhost.localdomain", "local", "broadcasthost")
    private val hostChars = Regex("[a-z0-9._-]+")

    /**
     * Parses and admits one line while rejecting unsupported cosmetic, path and option syntax.
     *
     * @param raw Untrusted filter-list line.
     * @param ctx Source metadata and option policy.
     * @return Accepted rule or a concrete skip reason.
     */
    fun parse(
        raw: String,
        ctx: ParseContext = ParseContext(),
    ): ParsedLine {
        val s = raw.trim()
        if (s.isEmpty() || s[0] == '!' || s[0] == '[' || (s[0] == '#' && !s.startsWith("##"))) {
            return skip(SkipReason.BLANK_OR_COMMENT)
        }
        if (cosmetic.any { s.contains(it) }) return skip(SkipReason.COSMETIC)

        // hosts / plain-domain syntax (only place where " #" or "\t#" begins a comment)
        val noComment = s.substringBefore(" #").substringBefore("\t#").trim()
        val parts = noComment.split(' ', '\t').filter { it.isNotEmpty() }
        if (parts.size >= 2 && parts[0] in hostsIps) {
            return admit(RuleAction.BLOCK, RuleKind.EXACT, parts[1], ctx)
        }
        if (parts.size == 1 && !s.startsWith("||") && !s.startsWith("@@") && !s.startsWith("*.") &&
            s.none { it in "/$*?|^=:" }
        ) {
            return admit(RuleAction.BLOCK, RuleKind.EXACT, s, ctx)
        }

        var rest = s
        var action = RuleAction.BLOCK
        if (rest.startsWith("@@")) {
            action = RuleAction.ALLOW
            rest = rest.substring(2)
        }
        if (rest.startsWith("*.") && rest.none { it in "$/|^=:" }) {
            return admit(action, RuleKind.WILDCARD, rest.substring(2), ctx)
        }
        if (!rest.startsWith("||")) return skip(SkipReason.PATH_OR_REGEX)

        val dollar = rest.indexOf('$')
        val pattern = (if (dollar >= 0) rest.substring(0, dollar) else rest).removePrefix("||").removeSuffix("^")
        val options =
            if (dollar >= 0) {
                rest
                    .substring(dollar + 1)
                    .split(',')
                    .map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
        val host = pattern.lowercase()
        if (!hostChars.matches(host)) return skip(SkipReason.PATH_OR_REGEX)

        // DNS filtering has no visibility into request type, initiator, or navigation
        // context. Turning rules scoped to images/scripts/third-party/documents into
        // domain-wide DNS blocks would break unrelated first-party functionality.
        // A sole $important on a BLOCK rule has no additional match restrictions.
        if (dollar >= 0 && (action != RuleAction.BLOCK || options.size != 1 || options[0] != "important")) {
            return skip(SkipReason.UNSUPPORTED_OPTIONS)
        }
        return admit(action, RuleKind.SUFFIX, host, ctx)
    }

    private fun admit(
        a: RuleAction,
        k: RuleKind,
        host: String,
        ctx: ParseContext,
    ): ParsedLine {
        val n = DomainName.normalizeUserEntry(host) ?: return skip(SkipReason.INVALID_HOST)
        if (n in metaHosts) return skip(SkipReason.INVALID_HOST)
        admission.check(a, k, n)?.let { return skip(it) }
        return ParsedLine.Rule(DnsRule(a, k, n, ctx.category, ctx.sourceId))
    }

    private fun skip(r: SkipReason) = ParsedLine.Skipped(r)
}
