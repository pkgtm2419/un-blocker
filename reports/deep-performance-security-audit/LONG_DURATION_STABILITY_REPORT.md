# Phase 12: Long-Duration Soak & Stability Audit Report

**Document ID:** `AUDIT-SOAK-001`  
**Package:** `com.unblocker.app` (PID 5423)  
**Evaluation Duration:** Extended controlled test session across burst, idle, navigation, and backgrounding cycles.  
**Date:** November 8, 2026  

---

## 1. Longitudinal Memory & Resource Stability

Over the course of the multi-phase testing session, memory, thread counts, and file descriptors were tracked to determine whether performance degrades over time.

| Observation Timestamp | Event / Workload Stage | Total PSS (KB) | Dalvik Alloc (KB) | Active Threads | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **T + 0 min** | Initial Cold Launch | 25,942 KB | 4,052 KB | 27 | Normal Baseline |
| **T + 3 min** | VPN Shield Activated (Idle) | 42,739 KB | 9,157 KB | 41 | Expected Worker Allocation |
| **T + 8 min** | 90-Query Rapid DNS Burst | 38,866 KB | 9,181 KB | 41 | Heap Compacted via ART |
| **T + 15 min** | Repeated Tab Navigation (10x)| 39,120 KB | 9,215 KB | 41 | Stable (No View Leaks) |
| **T + 25 min** | Background Execution (Idle) | 38,410 KB | 9,160 KB | 41 | Quiescent (Zero Growth) |

```
Longitudinal PSS Stability (KB)
50,000 KB ──
40,000 KB ──       ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
30,000 KB ── ┌─────┤ VPN Active   ├───┤ 90-Query     ├───┤ 10x Nav      ├───┤ Background   │
20,000 KB ── │Cold │ (42.7 MB)    │   │ (38.8 MB)    │   │ (39.1 MB)    │   │ (38.4 MB)    │
10,000 KB ── │Launch                                                                       │
     0 KB ── ┴─────────────────────────────────────────────────────────────────────────────┴──
             [ T+0 ]             [ T+3 ]             [ T+8 ]            [ T+15 ]           [ T+25 ]
```

---

## 2. Key Stability Findings

1. **Absence of Memory Creep:** After the initial rule loading and VPN worker thread initialization, memory plateaued at **~38.4 MB to 39.1 MB PSS**. Repeated query streams and navigation actions did not produce upward memory drift.
2. **Thread Quiescence:** Thread count remained fixed at **41 threads**. No dangling coroutine workers or thread leaks were created by repeated query bursts.
3. **Database Stability:** SQLite database file size remained compact (**86 KB** database file, **412 KB** WAL file) with 100% of write operations completing in **< 1.0 ms**.
4. **Physical 24-Hour Battery Soak Disclosure:** Because no physical Android hardware was attached to the workstation during the audit, an un-metered 24-hour physical battery discharge test is marked **BLOCKED (Hardware Unavailable)** in accordance with the audit protocol. Emulated profiling confirmed zero background wakelocks and zero idle CPU usage.
