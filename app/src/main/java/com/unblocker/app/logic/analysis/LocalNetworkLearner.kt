package com.unblocker.app.logic.analysis

import com.unblocker.app.logic.DomainName
import com.unblocker.app.domain.model.DecisionReason

/** Observation is explicit; diagnostics never observe cadence or write reputation. */
class LocalNetworkLearner(
    context: android.content.Context? = null,
    private val suffixRules: PublicSuffixRules = PublicSuffixRules.from(context),
    private val policy: ReputationPolicy = if (context == null) ReputationPolicy(DeviceLearning.memoryEvidenceStore())
        else DeviceLearning.policy(context),
    private val clock: () -> Long = if (context == null) ({System.nanoTime()/1_000_000})
        else ({android.os.SystemClock.elapsedRealtime()}),
    private val isKnownTracker: (String) -> Boolean = { false }
) {
    private val cadence = CadenceTracker()
    private val extractor = DomainFeatureExtractor(suffixRules)
    private val scorer = HeuristicScorer()
    private fun known(domain: String) = isKnownTracker(domain)

    fun getLearnedTrackersCount() = policy.confirmedCount()
    fun extractDomainSignature(raw:String): DomainSignature {
        val domain=DomainName.normalize(raw).orEmpty()
        val f=extractor.extract(domain,cadence.peek(domain,clock()))
        return DomainSignature(domain,domain.count { it=='.' },f.lexical>.35f,known(domain),
            domain.endsWith(".duckdns.org") || domain.endsWith(".ddns.net"),f.entropy,f.cadence,
            emptyList(),if(known(domain)) .95f else scorer.score(f).score)
    }
    @Suppress("UNUSED_PARAMETER")
    fun analyzeQuery(raw:String, threshold:Float=BLOCK_THRESHOLD): AnalysisScore {
        val domain=DomainName.normalize(raw) ?: return AnalysisScore(0f,"Invalid domain")
        if (NeverBlockPolicy.isNeverBlock(domain)) return AnalysisScore(0f, "Protected infrastructure", ReputationState.SUPPRESSED, DecisionReason.ALLOWED)
        if(known(domain)) return AnalysisScore(.95f,"Known tracker rule",ReputationState.CONFIRMED,DecisionReason.STATIC_RULE)
        val now=clock()
        val features=extractor.extract(domain,cadence.observe(domain,now))
        val evidence=policy.observe(domain,features,now)
        val isHighConfidence = scorer.isHighConfidenceAd(features)
        val reason = if (isHighConfidence) "Autonomous on-device ad detection" else "Local evidence: ${evidence.state}"
        return AnalysisScore(if(evidence.confirmed) maxOf(evidence.score,.8f) else scorer.score(features).score,
            reason,evidence.state,DecisionReason.LEARNED_EVIDENCE)
    }
    fun observeTrustedAlias(original:String) = policy.trustedAlias(original)
    fun syncFeedback(domain:String,feedback:UserFeedback) {
        if(policy.peek(domain).feedback != feedback) policy.feedback(domain,feedback)
    }
    fun evaluateLexical(domain:String) = extractor.extract(domain).lexical
    fun evaluateEntropy(domain:String) = extractor.extract(domain).entropy
    fun isAdOrTracker(domain:String):Boolean = analyzeQuery(domain).state==ReputationState.CONFIRMED
    companion object {
        const val BLOCK_THRESHOLD=.65f
        const val MAX_TRACKED_DOMAINS=2000
        const val MAX_LEARNED_REPUTATIONS=20_000
    }
}

data class AnalysisScore(val score:Float,val reason:String,
    val state:ReputationState=ReputationState.UNKNOWN,
    val reasonCode:DecisionReason=DecisionReason.ALLOWED)
