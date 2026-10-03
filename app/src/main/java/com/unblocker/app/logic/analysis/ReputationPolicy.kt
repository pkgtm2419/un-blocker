package com.unblocker.app.logic.analysis

/** Only this policy converts observations to persistent reputation. */
class ReputationPolicy(private val store: PrivateEvidenceStore) {
    private val scorer=HeuristicScorer()
    private val lastPositive = LinkedHashMap<String,Long>(16,.75f,true)
    fun peek(domain: String) = store.get(domain) ?: ReputationEvidence()
    fun confirmedCount() = store.confirmedCount()
    @Synchronized fun observe(domain: String, features: DomainFeatures, now: Long): ReputationEvidence {
        if (NeverBlockPolicy.isNeverBlock(domain)) return ReputationEvidence(state = ReputationState.SUPPRESSED)
        val old=peek(domain)
        val scored=scorer.score(features)
        if (scored.score < .65f) return old
        val last=lastPositive[domain]
        if (last != null && now < last) {
            lastPositive[domain]=now
            return old
        }
        if (last != null && now >= last && now-last < 60_000) return old
        // A process restart has no monotonic history: first observation only starts a fresh window.
        val windows=if (last == null && old.positiveWindows > 0) old.positiveWindows else
            (old.positiveWindows+1).coerceAtMost(255)
        lastPositive[domain] = now
        val it = lastPositive.iterator()
        while (lastPositive.size > 2000 && it.hasNext()) {
            it.next()
            it.remove()
        }
        val updated=old.copy(score=maxOf(old.score,scored.score),mask=old.mask or scored.mask,
            positiveWindows=windows,dayBucket=store.currentBucket())
        val state=if(updated.feedback == UserFeedback.ALLOW) ReputationState.SUPPRESSED else normalState(updated)
        return updated.copy(state=state).also { store.put(domain,it) }
    }
    @Synchronized fun trustedAlias(domain: String): ReputationEvidence {
        val old=peek(domain)
        return old.copy(score=.98f,mask=old.mask or 16,dayBucket=store.currentBucket(),
            state=if(old.feedback==UserFeedback.ALLOW) ReputationState.SUPPRESSED else ReputationState.CONFIRMED)
            .also { store.put(domain,it) }
    }
    @Synchronized fun feedback(domain: String, feedback: UserFeedback): ReputationEvidence {
        val old=peek(domain)
        val state=if(feedback==UserFeedback.ALLOW) ReputationState.SUPPRESSED else normalState(old)
        return old.copy(state=state,feedback=feedback,dayBucket=store.currentBucket()).also { store.put(domain,it) }
    }
    companion object {
        fun normalState(r: ReputationEvidence) = when {
            r.mask and 16 != 0 || (r.positiveWindows >= 2 && r.mask and 1 != 0 && Integer.bitCount(r.mask and 15)>=2) -> ReputationState.CONFIRMED
            r.positiveWindows > 0 -> ReputationState.SUSPECT
            r.mask != 0 -> ReputationState.OBSERVING
            else -> ReputationState.UNKNOWN
        }
    }
}
