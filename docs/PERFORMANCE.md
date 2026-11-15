# Performance goals, test method and limits

## Performance-sensitive paths

- VPN TUN read/parse/decide/write, domain normalization, UBR2 rules and off-heap rule lookup; avoid expensive allocations or blocking disk work in packet worker.
- Resolver network handling, bounded coalescing/connection timeout, TTL cache, UDP truncation/TCP fallback and network changes.
- `QueryLogSink`: bounded non-blocking producer queue, IO batching and clear-generation barrier; monitor drop/error behavior separately from GUI responsiveness.
- Compose log list, `LogViewModel` flow combination, reduced-motion UI and recomposition after statistics updates.
- Extension downloads, parse/update and snapshot swap; bounded file sizes, temp files, partial failure rollback.

## Benchmark rules

1. Define fixture sets and run an unchanged baseline and candidate on identical device, API, network, rule set and warmup.
2. Measure p50/p95 packet decision time, allocations, throughput, memory pressure/GC, app startup and UI frames; measure network-facing DNS separately from local policy time.
3. Run multiple iterations; record sample size, environment, variance, failure counts and raw results.
4. Use synthetic domain inputs; avoid uploading personal DNS captures.
5. Don't introduce invented percentage improvements or claim zero allocation globally from isolated comments. Test proposed improvements with the existing `MatcherBenchmarkTest`, `RuleSetAllocationBenchmarkTest` and concurrency fixtures.

The repository README links to `docs/qa/performance-baseline-v2.2.0.md` but the referenced file was absent at snapshot. This pack restores an **unverified-data placeholder**, not a fabricated benchmark.
