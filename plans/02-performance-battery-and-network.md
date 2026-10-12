# Plan 02: Performance, Battery, and Network Efficiency

## Objective

Make protection efficient during normal use, idle periods, network transitions, and high DNS-query volume.

### PERF-01: Establish a reproducible baseline
- CPU usage while idle and browsing.
- Java and native heap consumption.
- DNS forwarding latency and cache hit rate.
- Battery consumption under controlled testing.

### PERF-02: Protect the DNS hot path
- Keep packet classification allocation-light.
- Avoid disk writes during packet classification.
- Avoid blocking locks on the forwarding path.
- Reuse immutable rule structures (`RuleSet.kt` UBR2 format).
- Fast binary lookup without linear table sweeps.
- Zero extra allocations in packet loops via `ByteArrayPool`.

### PERF-03: Optimize caching
- DNS response TTL handling.
- Bounded LRU cache capacity.
- Prune expired entries lazily and at capacity rather than linear sweep on every insert.
- Invalidate cache promptly on network transitions.

### PERF-04: Reduce unnecessary background work
- Do not run protection recurring work when disabled.
- Cancel health check workers promptly when protection stops.
- Avoid continuous polling when Android network callbacks suffice.
- Respect reduced motion and system animation preferences in UI composables.

### PERF-05: Minimize network overhead & latency
- Prioritize last responsive DNS upstream resolver to eliminate repeated timeout delays.
- Do not drop DNS traffic on networks waiting for `NET_CAPABILITY_VALIDATED`.
- Zero remote analytics, tracking, or blocklist fetching over the network.
