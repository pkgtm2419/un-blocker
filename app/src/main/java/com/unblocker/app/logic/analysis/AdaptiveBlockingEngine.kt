package com.unblocker.app.logic.analysis

import com.unblocker.app.data.preferences.FilteringPreferences
import com.unblocker.app.domain.model.*

enum class BlockingStrategy { EVIDENCE_BASED }
data class LearningPhase(val day:Int,val confidenceThreshold:Float,
    val blockingStrategy:BlockingStrategy,val description:String)

/** Compatibility facade: installation day cannot authorize blocking. */
class AdaptiveBlockingEngine(
    @Suppress("UNUSED_PARAMETER") preferences:FilteringPreferences?=null,
    val networkLearner:LocalNetworkLearner=LocalNetworkLearner()
) {
    val learningCurve=listOf(LearningPhase(1,.8f,BlockingStrategy.EVIDENCE_BASED,"Independent local evidence"))
    @Suppress("UNUSED_PARAMETER") fun getCurrentPhase(customDay:Int?=null)=learningCurve.single()
    @Suppress("UNUSED_PARAMETER") fun evaluateDomain(domain:String,customDay:Int?=null):BlockingDecision {
        val analysis=networkLearner.analyzeQuery(domain)
        return if(analysis.state==ReputationState.CONFIRMED) BlockingDecision(
            BlockingAction.BLOCK,analysis.reason,analysis.score,BlockingCategory.TRACKER,
            reasonCode=analysis.reasonCode)
        else BlockingDecision.allow("Insufficient independent evidence",1f-analysis.score)
    }
}
