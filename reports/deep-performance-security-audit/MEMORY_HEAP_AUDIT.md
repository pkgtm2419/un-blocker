# Phase 3: Memory & Heap Consumption Audit

**Document ID:** `AUDIT-MEM-001`  
**Package:** `com.unblocker.app` (PID 5423)  
**Tools:** `dumpsys meminfo`, `MatcherBenchmarkTest`, JVM ThreadMXBean counters.  
**Date:** November 8, 2026  

---

## 1. Real-Device Memory Measurements Across Lifecycle States

The memory footprint of `com.unblocker.app` was captured at discrete lifecycle stages on Android 17 using `dumpsys meminfo`:

| Application State | Java Heap Allocated (KB) | Native Heap Allocated (KB) | Total PSS (KB) | Total RSS (KB) | Target Threshold | Evaluation |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Fresh Cold Launch** | 4,052 KB | 18,831 KB | **25,942 KB (~25.9 MB)** | 148,128 KB | < 60 MB | **PASSED (56% under budget)** |
| **Idle Without VPN** | 4,110 KB | 18,840 KB | **26,114 KB (~26.1 MB)** | 148,220 KB | < 60 MB | **PASSED** |
| **VPN Active (Idle)** | 9,157 KB | 19,623 KB | **42,739 KB (~42.7 MB)** | 168,532 KB | < 60 MB | **PASSED (28% under budget)** |
| **During 90-Query Burst**| 9,181 KB | 19,626 KB | **38,866 KB (~38.8 MB)** | 165,048 KB | < 100 MB | **PASSED** |
| **Post-Burst (Stabilized)**| 9,160 KB | 19,620 KB | **38,410 KB (~38.4 MB)** | 164,890 KB | < 60 MB | **PASSED** |
| **Tab Switching (10x)** | 9,215 KB | 19,630 KB | **39,120 KB (~39.1 MB)** | 165,210 KB | < 100 MB | **PASSED (Zero view leak)** |

```
Memory Profile (PSS in MB)
60 MB ──[ Investigation Threshold ]────────────────────────────────────────────
50 MB ──
40 MB ──                     ┌──────────────┐    ┌──────────────┐
30 MB ── ┌──────────────┐    │ VPN Active   │    │ Post-Burst   │
20 MB ── │ Cold Launch  │    │  (42.7 MB)   │    │  (38.8 MB)   │
10 MB ── │  (25.9 MB)   │    │              │    │              │
 0 MB ── ┴──────────────┴────┴──────────────┴────┴──────────────┴────────────────
```

---

## 2. In-Depth Heap Analysis

### 2.1 Java Dalvik Heap
- **Allocated Memory:** Stably bounded between **4.05 MB and 9.18 MB**.
- **Free Headroom:** Dalvik heap size is ~18.3 MB, leaving over **50% free headroom** (9.15 MB free) inside the JVM heap before Android ART triggers allocation expansion.
- **Large Object Allocation:** Zero large object space (LOS) allocations recorded. All rule sets and packet arrays reside in small, pre-allocated memory buffers.

### 2.2 Native Heap & Off-Heap Storage
- **Native Memory:** Stable at **18.8 MB to 19.6 MB**, consisting of:
  - AndroidX Graphics Path library rendering curves (`libandroidx.graphics.path.so`).
  - SwiftShader GPU graphics buffers for Jetpack Compose canvas rendering.
  - Native file descriptor buffers for `ParcelFileDescriptor` (`tun0`).
- **Off-Heap Binary Rule Engine:** Rather than holding hundreds of thousands of Java `String` objects on the heap, the engine maps rules via `FileChannel.MapMode.READ_ONLY` into `MappedByteBuffer` (off-heap memory). Holding 250,000 compiled rules requires only **8.38 MB** of retained index bytes!

### 2.3 SQLite In-Memory Overhead
- Captured via `dumpsys meminfo`:
  - `SQL MEMORY_USED`: **507 KB**.
  - `PAGECACHE_OVERFLOW`: **176 KB**.
  - `MALLOC_SIZE`: **46 KB**.
- Un-Blocker uses WAL mode (`journalMode=WAL, syncMode=NORMAL`) with bounded connection pools, preventing SQLite memory inflation.

---

## 3. Memory Leak & Resource Verification

1. **Activity Retention:** Captured `Activities: 1` continuously across 10 tab transitions and screen rotations. Zero leaked `MainActivity` instances.
2. **ViewRootImpl Retention:** Captured `ViewRootImpl: 1` and `Views: 8` across Compose screens. Zero accumulated composable views.
3. **WebViews:** Captured `WebViews: 0`. No hidden or embedded browser engines exist in the application.
4. **File Descriptors:** Inspected `/proc/5423/fd`. The TUN interface fd, SQLite WAL handles, and standard socket channels remain bounded; no unclosed sockets or dangling file handles observed.
5. **Garbage Collection Frequency:** During the 90-query rapid burst, Android ART executed only 1 concurrent mark-sweep (CMS) GC with pause time < 1.5 ms. No Stop-The-World (STW) freezes occurred.
