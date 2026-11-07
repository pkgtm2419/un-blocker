package com.unblocker.app.logic.rules

import com.unblocker.app.domain.model.BlockingCategory
import com.unblocker.app.logic.DomainName
import com.unblocker.app.logic.analysis.PublicSuffixRules

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
    QUARANTINED
}

sealed interface ParsedLine {
    data class Rule(val rule: DnsRule) : ParsedLine
    data class Skipped(val why: SkipReason) : ParsedLine
}

data class ParseContext(
    val sourceId: Int = 0,
    val security: Boolean = false,
    val category: BlockingCategory = BlockingCategory.AD
)

class RuleLineParser(
    private val admission: RuleAdmission = DefaultRuleAdmission(),
    private val psl: PublicSuffixRules = PublicSuffixRules.fallback
) {
    private val cosmetic = arrayOf("##", "#@#", "#?#", "#$#", "#%#", "#@$#", "#@?#", "#@%#", "$$")
    private val partyResource = setOf(
        "third-party", "3p", "script", "image", "stylesheet", "css", "font", "media",
        "xmlhttprequest", "xhr", "subdocument", "frame", "ping", "popup", "object", "other", "websocket"
    )
    private val docLevel = setOf("doc", "document", "all")
    private val hostsIps = setOf("0.0.0.0", "127.0.0.1", "::", "::1")
    private val metaHosts = setOf("localhost", "localhost.localdomain", "local", "broadcasthost")
    private val hostChars = Regex("[a-z0-9._-]+")

    fun parse(raw: String, ctx: ParseContext = ParseContext()): ParsedLine {
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
            s.none { it in "/$*?|^=:" }) {
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
        val options = if (dollar >= 0) rest.substring(dollar + 1).split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() } else emptyList()
        val host = pattern.lowercase()
        if (!hostChars.matches(host)) return skip(SkipReason.PATH_OR_REGEX)

        val opts = options.filter { it != "important" }
        if (opts.isNotEmpty()) {
            if (action == RuleAction.ALLOW) return skip(SkipReason.UNSUPPORTED_OPTIONS)
            when {
                opts.all { it in partyResource } -> {
                    val reg = psl.registrableDomain(host)
                    if (reg == null || reg == host) return skip(SkipReason.OPTIONS_ON_REGISTRABLE)
                }
                opts.all { it in docLevel } && ctx.security && action == RuleAction.BLOCK -> Unit
                else -> return skip(SkipReason.UNSUPPORTED_OPTIONS)
            }
        }
        return admit(action, RuleKind.SUFFIX, host, ctx)
    }

    private fun admit(a: RuleAction, k: RuleKind, host: String, ctx: ParseContext): ParsedLine {
        val n = DomainName.normalizeUserEntry(host) ?: return skip(SkipReason.INVALID_HOST)
        if (n in metaHosts) return skip(SkipReason.INVALID_HOST)
        admission.check(a, k, n)?.let { return skip(it) }
        return ParsedLine.Rule(DnsRule(a, k, n, ctx.category, ctx.sourceId))
    }

    private fun skip(r: SkipReason) = ParsedLine.Skipped(r)
}
