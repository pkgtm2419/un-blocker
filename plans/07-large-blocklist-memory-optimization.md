# Plan 07: Large Blocklist Memory and Performance Optimization

## Objective

Ensure the application scales to hundreds of thousands of DNS rules without exhausting Android heap memory, causing GC pauses, or slowing down network lookups.

### PERF-MEM-01: Compact immutable binary format
- UBR2 binary format with little-endian encoding and offset index table.
- Reversed-label binary search matching avoids creating intermediate String objects.
- Single mmap/ByteBuffer loading without duplicate in-memory Java objects.

### PERF-MEM-02: Zero runtime duplication
- Compile rules ahead of time via `compiler.py`.
- Clean up intermediate compiler sets during build.
- At runtime, only the compact binary buffer is retained.
- Hot path matching allocates at most one temporary reversed domain buffer.

### PERF-MEM-03: Bounded memory budgets
- Rule matching operates in `O(log N)` time.
- Memory usage scales linearly with unique domain bytes rather than Java object overhead (reducing memory footprint by >75%).
- Zero GC churn on the DNS forwarding hot path.
