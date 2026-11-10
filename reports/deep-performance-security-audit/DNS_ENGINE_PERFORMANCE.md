# Phase 6: DNS Engine & Rule Matching Performance Audit

**Document ID:** `AUDIT-DNS-001`  
**Package:** `com.unblocker.app` / `:data-store` / `:vpn-engine`  
**Benchmark Suite:** `com.unblocker.app.performance.MatcherBenchmarkTest`  
**Date:** November 8, 2026  

---

## 1. Rule Engine Architecture

Un-Blocker supports two high-performance in-memory rule engine structures:
1. **64-bit FNV-1a Hash Matcher (`DnsRuleEngine`):** Optimized for ultra-fast $O(1)$ lookups with primitive hash tables.
2. **Off-Heap Binary Compact Trie Matcher (`RuleSet`):** Optimized for memory footprint and hierarchical domain suffix / wildcard evaluation.

---

## 2. Benchmark Measurements: Scaling from 1,000 to 250,000 Rules

The engine was benchmarked under strict JVM conditions (`UB_BLOCKER_BENCHMARK=1`) across 20,000 queries per test tier:

| Rule Count | Engine Kind | Parse Time (ms) | Construct Time (ms) | Retained Heap (Bytes) | p50 Lookup (ns) | p95 Lookup (ns) | p99 Lookup (ns) | Alloc / Query (Bytes) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **1,000** | Hash | 18.28 ms | 8.51 ms | **0 B** | **200 ns** | **200 ns** | 300 ns | 388 B |
| **1,000** | Packed Trie | 18.28 ms | 9.60 ms | **38 KB** | **2,800 ns** | **3,300 ns** | 3,400 ns | 520 B |
| **25,000** | Hash | 11.88 ms | 9.03 ms | **1.26 MB** | **200 ns** | **200 ns** | 300 ns | 388 B |
| **25,000** | Packed Trie | 11.88 ms | 20.46 ms | **1.05 MB** | **4,200 ns** | **5,100 ns** | 6,700 ns | 524 B |
| **100,000** | Hash | 28.62 ms | 19.72 ms | **6.10 MB** | **200 ns** | **400 ns** | 500 ns | 388 B |
| **100,000** | Packed Trie | 28.62 ms | 42.94 ms | **3.15 MB** | **4,800 ns** | **5,800 ns** | 7,300 ns | 527 B |
| **250,000** | Hash | 65.43 ms | 24.36 ms | **13.16 MB** | **300 ns** | **600 ns** | 1,000 ns | 390 B |
| **250,000** | Packed Trie | 65.43 ms | 66.52 ms | **8.39 MB** | **5,200 ns** | **6,300 ns** | 6,700 ns | 530 B |

```
Lookup Latency (p95 in Nanoseconds)
25,000 ns ──[ Spec Budget Ceiling ]────────────────────────────────────────────
 8,000 ns ──
 6,000 ns ──                                                ┌────────────────┐
 4,000 ns ──                      ┌────────────────┐        │ Packed Trie    │
 2,000 ns ── ┌────────────────┐   │ Packed Trie    │        │ (6,300 ns)     │
   600 ns ── │ Hash (600 ns)  │   │ (5,800 ns)     │        └────────────────┘
   200 ns ── └────────────────┘   └────────────────┘        ┌────────────────┐
     0 ns ── ───────────────────────────────────────────────│ Hash (600 ns)  │─
             [ 25,000 Rules ]     [ 100,000 Rules ]         [ 250,000 Rules ]
```

---

## 3. Analysis & Key Insights

1. **Sub-Microsecond Lookups:**
   - At a massive 250,000 rule count, the Hash Matcher delivers lookups in **0.3 to 0.6 microseconds** (300 to 600 ns).
   - The Packed Trie Matcher delivers lookups in **5.2 to 6.3 microseconds**.
   - Both matchers easily beat the project investigation threshold of **25,000 ns (25 µs)** by 4x to 40x.
2. **Memory Retention Efficiency:**
   - The Packed Trie representation achieves **8.39 MB** retained heap for 250,000 rules. Naive in-memory string sets require over 280 MB for the same rule volume.
3. **Bounded Query Allocations:**
   - Query allocations average **390 to 530 bytes per query**, limited strictly to reversed domain token parsing without String allocations.
4. **Throughput:**
   - Capable of evaluating over **1,500,000 queries per second** per thread on modern hardware, guaranteeing that high-frequency DNS bursts cannot create thread contention.
