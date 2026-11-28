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
    val lastSeenEpochMs: Long = System.currentTimeMillis(),
)

/**
 * Volatile recording target, duration, and activity timestamps.
 *
 * @property targetPackage Package name displayed as the recording target and used for VPN builder isolation.
 * @property targetUid Target Android UID; -1 accepts any known UID.
 * @property startTimeEpochMs Session start in Unix epoch milliseconds.
 * @property maxDurationMs Allowed elapsed wall-clock duration in milliseconds.
 * @property lastActivityEpochMs Most recent DNS query observation timestamp in Unix epoch milliseconds.
 */
data class RecordingSession(
    val targetPackage: String,
    val targetUid: Int = -1,
    val startTimeEpochMs: Long = System.currentTimeMillis(),
    val maxDurationMs: Long = 10 * 60 * 1000L, // 10 minutes default
    val lastActivityEpochMs: Long = startTimeEpochMs,
) {
    /**
     * Returns whether wall-clock elapsed time is strictly greater than the configured duration.
     */
    fun isExpired(): Boolean = System.currentTimeMillis() - startTimeEpochMs > maxDurationMs

    /**
     * Returns whether the session has seen zero DNS activity for at least [durationMs].
     *
     * @param durationMs Milliseconds of inactivity to qualify as idle. Defaults to 60,000 (1 minute).
     * @return True if elapsed time since last activity is greater than or equal to [durationMs].
     */
    fun isIdleFor(durationMs: Long = 60_000L): Boolean =
        System.currentTimeMillis() - lastActivityEpochMs >= durationMs
}

/**
 * Owns one process-local recording session and readable domain aggregates.
 *
 * In isolated mode, all DNS traffic entering the VPN is from the target package by construction,
 * so attribution is guaranteed without UID heuristics.
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
     * @param targetPackage Package name used to isolate the target in the VPN builder and identify it to UI.
     * @param targetUid Android UID for diagnostics; -1 if unknown.
     * @param maxDurationMs Maximum recording duration in milliseconds.
     * @return Newly active recording session.
     */
    @Synchronized
    fun startSession(
        targetPackage: String,
        targetUid: Int = -1,
        maxDurationMs: Long = 10 * 60 * 1000L,
    ): RecordingSession {
        sessionDomains.clear()
        _domainsFlow.value = emptyList()
        val now = System.currentTimeMillis()
        val session =
            RecordingSession(
                targetPackage = targetPackage,
                targetUid = targetUid,
                startTimeEpochMs = now,
                maxDurationMs = maxDurationMs,
                lastActivityEpochMs = now,
            )
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
     * Checks whether the active recording session has seen no DNS queries for at least [durationMs].
     *
     * @param durationMs Milliseconds of inactivity to qualify as idle. Defaults to 60,000 (1 minute).
     * @return True if an active session exists and is idle; false if inactive or recent activity was observed.
     */
    fun isSessionIdle(durationMs: Long = 60_000L): Boolean {
        val session = _activeSession.value ?: return false
        return session.isIdleFor(durationMs)
    }

    /**
     * Records a valid domain for an active, nonexpired session.
     *
     * In the isolated VPN tunnel, all intercepted DNS originates from the target package by construction;
     * UID filtering is therefore not applied.
     *
     * @param domain Readable hostname to normalize.
     * @param uid Attributed Android UID for diagnostics; ignored for isolation filtering.
     * @return Whether the observation was recorded; new hosts beyond the capacity check are rejected.
     */
    fun recordDomain(
        domain: String,
        uid: Int? = null,
    ): Boolean {
        val session = _activeSession.value ?: return false
        if (session.isExpired()) {
            stopSession()
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
        _activeSession.value = session.copy(lastActivityEpochMs = now)
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
    fun getRecordedDomains(): List<SessionDomain> = sessionDomains.values.sortedByDescending { it.count }

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
