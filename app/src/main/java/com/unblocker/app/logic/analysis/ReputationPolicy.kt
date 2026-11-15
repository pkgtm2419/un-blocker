package com.unblocker.app.logic.analysis

/**
 * Converts corroborated observations and feedback into coarse persistent reputation.
 *
 * @param store Private evidence store owning identifiers, expiry and persistence.
 */
class ReputationPolicy(private val store: PrivateEvidenceStore) {
    private val scorer=HeuristicScorer()
    private val lastPositive = LinkedHashMap<String,Long>(16,.75f,true)
    /**
     * Returns current evidence without recording an observation.
     *
     * @param domain Hostname to look up.
     * @return Stored evidence, or an empty unknown record.
     */
    fun peek(domain: String) = store.get(domain) ?: ReputationEvidence()
    /**
     * Returns the number of currently confirmed, unexpired records.
     */
    fun confirmedCount() = store.confirmedCount()
    /**
     * Accumulates corroborated evidence in separated monotonic observation windows.
     *
     * @param domain Hostname used for evidence and volatile window tracking.
     * @param features Extracted feature strengths.
     * @param now Monotonic observation time in milliseconds.
     * @return Previous or updated evidence; protected names yield a suppressed empty record.
     */
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
    /**
     * Adds the trusted-alias bit and confirms evidence unless user feedback suppresses it.
     *
     * @param domain Original hostname with a trusted blocked alias.
     * @return Updated evidence submitted to the store.
     */
    @Synchronized fun trustedAlias(domain: String): ReputationEvidence {
        val old=peek(domain)
        return old.copy(score=.98f,mask=old.mask or 16,dayBucket=store.currentBucket(),
            state=if(old.feedback==UserFeedback.ALLOW) ReputationState.SUPPRESSED else ReputationState.CONFIRMED)
            .also { store.put(domain,it) }
    }
    /**
     * Updates explicit feedback while retaining accumulated feature evidence.
     *
     * @param domain Hostname whose user intent changed.
     * @param feedback Requested allow, block or absent intent.
     * @return Updated evidence submitted to the store.
     */
    @Synchronized fun feedback(domain: String, feedback: UserFeedback): ReputationEvidence {
        val old=peek(domain)
        val state=if(feedback==UserFeedback.ALLOW) ReputationState.SUPPRESSED else normalState(old)
        return old.copy(state=state,feedback=feedback,dayBucket=store.currentBucket()).also { store.put(domain,it) }
    }
    /**
     * Derives reputation state from accumulated evidence.
     */
    companion object {
        /**
         * Derives state from alias and corroborated window evidence without applying feedback.
         *
         * @param r Evidence whose mask and positive-window count are inspected.
         * @return Confirmed, suspect, observing or unknown state.
         */
        fun normalState(r: ReputationEvidence) = when {
            r.mask and 16 != 0 || (r.positiveWindows >= 2 && r.mask and 1 != 0 && Integer.bitCount(r.mask and 15)>=2) -> ReputationState.CONFIRMED
            r.positiveWindows > 0 -> ReputationState.SUSPECT
            r.mask != 0 -> ReputationState.OBSERVING
            else -> ReputationState.UNKNOWN
        }
    }
}
