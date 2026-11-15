package com.unblocker.app.logic.analysis

import com.unblocker.app.logic.DomainName
import com.unblocker.app.domain.model.DecisionReason

/**
 * Collects explicit query observations and exposes separate diagnostic feature inspection.
 *
 * @param context Application context, or null for volatile stores and a JVM monotonic clock.
 * @param suffixRules Public suffix context for feature extraction.
 * @param policy Reputation policy receiving observations.
 * @param clock Monotonic clock in milliseconds.
 * @param isKnownTracker Predicate for known block rules.
 * @param protectedDomains Host protection checked before learning.
 */
class LocalNetworkLearner(
    context: android.content.Context? = null,
    private val suffixRules: PublicSuffixRules = PublicSuffixRules.from(context),
    private val policy: ReputationPolicy = if (context == null) ReputationPolicy(DeviceLearning.memoryEvidenceStore())
        else DeviceLearning.policy(context),
    private val clock: () -> Long = if (context == null) ({System.nanoTime()/1_000_000})
        else ({android.os.SystemClock.elapsedRealtime()}),
    private val isKnownTracker: (String) -> Boolean = { false },
    private val protectedDomains: ProtectedDomains = ProtectedDomains.DEFAULT
) {
    private val cadence = CadenceTracker()
    private val extractor = DomainFeatureExtractor(suffixRules)
    private val scorer = HeuristicScorer()
    private fun known(domain: String) = isKnownTracker(domain)

    /**
     * Returns the count of currently confirmed, unexpired evidence records.
     */
    fun getLearnedTrackersCount() = policy.confirmedCount()
    /**
     * Builds a diagnostic snapshot without recording a cadence observation or reputation update.
     *
     * @param raw Hostname to normalize; invalid input becomes an empty diagnostic name.
     * @return Structural and heuristic diagnostic snapshot.
     */
    fun extractDomainSignature(raw:String): DomainSignature {
        val domain=DomainName.normalize(raw).orEmpty()
        val f=extractor.extract(domain,cadence.peek(domain,clock()))
        return DomainSignature(domain,domain.count { it=='.' },f.lexical>.35f,known(domain),
            domain.endsWith(".duckdns.org") || domain.endsWith(".ddns.net"),f.entropy,f.cadence,
            emptyList(),if(known(domain)) .95f else scorer.score(f).score)
    }
    /**
     * Observes a query unless it is invalid, protected or matched by a known rule.
     *
     * @param raw DNS hostname to normalize and analyze.
     * @param threshold Compatibility parameter; unused by the evidence policy.
     * @return Analysis score and reputation state; confirmed state determines adaptive blocking.
     */
    @Suppress("UNUSED_PARAMETER")
    fun analyzeQuery(raw:String, threshold:Float=BLOCK_THRESHOLD): AnalysisScore {
        val domain=DomainName.normalize(raw) ?: return AnalysisScore(0f,"Invalid domain")
        if (NeverBlockPolicy.isNeverBlock(domain) || protectedDomains.isProtected(domain) || domain in FIRST_PARTY_TOOLING_HOSTS) {
            return AnalysisScore(0f, "Protected infrastructure", ReputationState.SUPPRESSED, DecisionReason.ALLOWED)
        }
        if(known(domain)) return AnalysisScore(.95f,"Known tracker rule",ReputationState.CONFIRMED,DecisionReason.STATIC_RULE)
        val now=clock()
        val features=extractor.extract(domain,cadence.observe(domain,now))
        val evidence=policy.observe(domain,features,now)
        val isHighConfidence = scorer.isHighConfidenceAd(features)
        val reason = if (isHighConfidence) "Autonomous on-device ad detection" else "Local evidence: ${evidence.state}"
        return AnalysisScore(if(evidence.confirmed) maxOf(evidence.score,.8f) else scorer.score(features).score,
            reason,evidence.state,DecisionReason.LEARNED_EVIDENCE)
    }
    /**
     * Records a trusted-alias signal in reputation policy.
     *
     * @param original Original hostname whose validated alias matched a block rule.
     * @return Updated reputation evidence.
     */
    fun observeTrustedAlias(original:String) = policy.trustedAlias(original)
    /**
     * Updates stored feedback only when the requested value differs.
     *
     * @param domain Hostname whose feedback is synchronized.
     * @param feedback Current user rule intent.
     */
    fun syncFeedback(domain:String,feedback:UserFeedback) {
        if(policy.peek(domain).feedback != feedback) policy.feedback(domain,feedback)
    }
    /**
     * Returns the lexical feature without recording an observation.
     *
     * @param domain Hostname to inspect.
     * @return Lexical score on a zero-to-one scale.
     */
    fun evaluateLexical(domain:String) = extractor.extract(domain).lexical
    /**
     * Returns the entropy feature without recording an observation.
     *
     * @param domain Hostname to inspect.
     * @return Heuristic entropy score on a zero-to-one scale.
     */
    fun evaluateEntropy(domain:String) = extractor.extract(domain).entropy
    /**
     * Analyzes and observes the query, then checks confirmed reputation.
     *
     * @param domain Hostname to analyze.
     * @return Whether the resulting reputation is confirmed.
     */
    fun isAdOrTracker(domain:String):Boolean = analyzeQuery(domain).state==ReputationState.CONFIRMED
    /**
     * Compatibility thresholds and host exceptions used by local learning.
     */
    companion object {
        /**
         * Default compatibility threshold supplied to analyzeQuery; that parameter is currently unused.
         */
        const val BLOCK_THRESHOLD=.65f
        /**
         * Compatibility capacity for volatile domain tracking, in domains.
         */
        const val MAX_TRACKED_DOMAINS=2000
        /**
         * Compatibility capacity for learned reputations, in records.
         */
        const val MAX_LEARNED_REPUTATIONS=20_000

        /**
         * Exact diagnostic and operational hosts excluded from query learning.
         */
        val FIRST_PARTY_TOOLING_HOSTS = setOf(
            "tracking.ups.com",
            "analytics.google.com",
            "analytics.twitter.com",
            "ads.twitter.com",
            "metrics.cloudflare.com",
            "pixel.google.com",
            "sentry.io",
            "app.datadoghq.com"
        )
    }
}

/**
 * Local query analysis result.
 *
 * @property score Heuristic or known-rule score on a zero-to-one scale.
 * @property reason Human-readable explanation.
 * @property state Reputation state used to authorize adaptive blocking.
 * @property reasonCode Machine-readable source of the analysis result.
 */
data class AnalysisScore(val score:Float,val reason:String,
    val state:ReputationState=ReputationState.UNKNOWN,
    val reasonCode:DecisionReason=DecisionReason.ALLOWED)
