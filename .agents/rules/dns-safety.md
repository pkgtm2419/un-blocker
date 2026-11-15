---
trigger: glob
globs: "app/src/main/java/com/unblocker/app/logic/dns/**,app/src/main/java/com/unblocker/app/services/**,vpn-engine/src/main/java/**"
---

# Dns Safety

Any TUN, DNS, forwarding, caching or resolver work must read `docs/DNS_PIPELINE.md`, `docs/BEHAVIOR_CONTRACTS.md` and use `/dns-protocol-change`. Respect IPv4/IPv6 UDP/TCP, untrusted wire bounds, DNS compression, ID/QTYPE/QCLASS response validation, protected sockets, Android underlying resolvers, no loop/no public fallback in active app path. Prove behavior with malformed, concurrent, transition and device tests.
