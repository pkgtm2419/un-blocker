package com.unblocker.app.services

import com.unblocker.app.logic.DomainName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class SessionDomain(
    val domain: String,
    val count: Int = 1,
    val firstSeenEpochMs: Long = System.currentTimeMillis(),
    val lastSeenEpochMs: Long = System.currentTimeMillis()
)

data class RecordingSession(
    val targetPackage: String,
    val targetUid: Int,
    val startTimeEpochMs: Long = System.currentTimeMillis(),
    val maxDurationMs: Long = 10 * 60 * 1000L // 10 minutes default
) {
    fun isExpired(): Boolean = System.currentTimeMillis() - startTimeEpochMs > maxDurationMs
}

class RecordingSessionManager private constructor() {

    private val _activeSession = MutableStateFlow<RecordingSession?>(null)
    val activeSession: StateFlow<RecordingSession?> = _activeSession.asStateFlow()

    private val _domainsFlow = MutableStateFlow<List<SessionDomain>>(emptyList())
    val domainsFlow: StateFlow<List<SessionDomain>> = _domainsFlow.asStateFlow()

    private val sessionDomains = ConcurrentHashMap<String, SessionDomain>()

    @Synchronized
    fun startSession(targetPackage: String, targetUid: Int, maxDurationMs: Long = 10 * 60 * 1000L): RecordingSession {
        sessionDomains.clear()
        _domainsFlow.value = emptyList()
        val session = RecordingSession(targetPackage, targetUid, System.currentTimeMillis(), maxDurationMs)
        _activeSession.value = session
        return session
    }

    fun isRecordingActive(): Boolean {
        val session = _activeSession.value ?: return false
        if (session.isExpired()) {
            stopSession()
            return false
        }
        return true
    }

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

    @Synchronized
    fun stopSession(): List<SessionDomain> {
        _activeSession.value = null
        val results = sessionDomains.values.sortedByDescending { it.count }
        _domainsFlow.value = results
        return results
    }

    fun getRecordedDomains(): List<SessionDomain> {
        return sessionDomains.values.sortedByDescending { it.count }
    }

    companion object {
        const val MAX_SESSION_DOMAINS = 500
        val instance by lazy { RecordingSessionManager() }
    }
}
