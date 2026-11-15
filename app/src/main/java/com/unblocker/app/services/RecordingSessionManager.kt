package com.unblocker.app.services

import com.unblocker.app.logic.DomainName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Volatile per-domain aggregate collected during a Learn-an-App session.
 *
 * @property domain Normalized readable DNS hostname.
 * @property count Number of recorded observations.
 * @property firstSeenEpochMs First observation time in Unix epoch milliseconds.
 * @property lastSeenEpochMs Latest observation time in Unix epoch milliseconds.
 */
data class SessionDomain(
    val domain: String,
    val count: Int = 1,
    val firstSeenEpochMs: Long = System.currentTimeMillis(),
    val lastSeenEpochMs: Long = System.currentTimeMillis()
)

/**
 * Volatile recording target and duration.
 *
 * @property targetPackage Package name displayed as the recording target.
 * @property targetUid Target Android UID; -1 accepts any known UID.
 * @property startTimeEpochMs Session start in Unix epoch milliseconds.
 * @property maxDurationMs Allowed elapsed wall-clock duration in milliseconds.
 */
data class RecordingSession(
    val targetPackage: String,
    val targetUid: Int,
    val startTimeEpochMs: Long = System.currentTimeMillis(),
    val maxDurationMs: Long = 10 * 60 * 1000L // 10 minutes default
) {
    /**
     * Returns whether wall-clock elapsed time is strictly greater than the configured duration.
     */
    fun isExpired(): Boolean = System.currentTimeMillis() - startTimeEpochMs > maxDurationMs
}

/**
 * Owns one process-local recording session and readable domain aggregates.
 */
class RecordingSessionManager private constructor() {

    private val _activeSession = MutableStateFlow<RecordingSession?>(null)
    /**
     * Current recording session, or null when stopped.
     */
    val activeSession: StateFlow<RecordingSession?> = _activeSession.asStateFlow()

    private val _domainsFlow = MutableStateFlow<List<SessionDomain>>(emptyList())
    /**
     * Latest domain aggregates sorted by descending observation count.
     */
    val domainsFlow: StateFlow<List<SessionDomain>> = _domainsFlow.asStateFlow()

    private val sessionDomains = ConcurrentHashMap<String, SessionDomain>()

    /**
     * Replaces the recording target and clears previous domain aggregates.
     *
     * @param targetPackage Package name used to identify the target to the UI.
     * @param targetUid Android UID filter; -1 accepts all UIDs.
     * @param maxDurationMs Maximum recording duration in milliseconds.
     * @return Newly active recording session.
     */
    @Synchronized
    fun startSession(targetPackage: String, targetUid: Int, maxDurationMs: Long = 10 * 60 * 1000L): RecordingSession {
        sessionDomains.clear()
        _domainsFlow.value = emptyList()
        val session = RecordingSession(targetPackage, targetUid, System.currentTimeMillis(), maxDurationMs)
        _activeSession.value = session
        return session
    }

    /**
     * Returns whether a nonexpired session exists, stopping an expired session as a side effect.
     */
    fun isRecordingActive(): Boolean {
        val session = _activeSession.value ?: return false
        if (session.isExpired()) {
            stopSession()
            return false
        }
        return true
    }

    /**
     * Records a valid domain for an active, nonexpired session when attribution is eligible.
     *
     * @param domain Readable hostname to normalize.
     * @param uid Attributed Android UID; null is accepted even for a specific target.
     * @return Whether the observation was recorded; new hosts beyond the capacity check are rejected.
     */
    fun recordDomain(domain: String, uid: Int?): Boolean {
        val session = _activeSession.value ?: return false
        if (session.isExpired()) {
            stopSession()
            return false
        }
        // If uid is specified and does not match targetUid, skip
        if (uid != null && session.targetUid != -1 && uid != session.targetUid) {
            return false
        }
        val clean = DomainName.normalize(domain) ?: return false
        if (sessionDomains.size >= MAX_SESSION_DOMAINS && !sessionDomains.containsKey(clean)) {
            return false
        }
        val now = System.currentTimeMillis()
        sessionDomains.compute(clean) { _, existing ->
            if (existing == null) {
                SessionDomain(clean, count = 1, firstSeenEpochMs = now, lastSeenEpochMs = now)
            } else {
                existing.copy(count = existing.count + 1, lastSeenEpochMs = now)
            }
        }
        _domainsFlow.value = sessionDomains.values.sortedByDescending { it.count }
        return true
    }

    /**
     * Stops recording while retaining aggregates for review.
     *
     * @return Recorded domains sorted by descending count.
     */
    @Synchronized
    fun stopSession(): List<SessionDomain> {
        _activeSession.value = null
        val results = sessionDomains.values.sortedByDescending { it.count }
        _domainsFlow.value = results
        return results
    }

    /**
     * Returns the currently retained readable aggregates sorted by descending count.
     */
    fun getRecordedDomains(): List<SessionDomain> {
        return sessionDomains.values.sortedByDescending { it.count }
    }

    /**
     * Shared recording instance and admission bound.
     */
    companion object {
        /**
         * Distinct-domain admission threshold for a recording session.
         */
        const val MAX_SESSION_DOMAINS = 500
        /**
         * Lazily initialized process recording coordinator.
         */
        val instance by lazy { RecordingSessionManager() }
    }
}
