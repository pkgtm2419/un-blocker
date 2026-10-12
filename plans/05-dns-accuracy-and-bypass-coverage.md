# Plan 05: DNS Accuracy, Primary Domain Protection, and Bypass Handling

## Objective

Guarantee that legitimate primary domains, search engines, and captive portals are never blocked, while explaining filtering boundaries truthfully.

### DNS-01: Primary domain & apex protection
- `never_block.txt` contains critical OS connectivity, search engines, and major internet primary domains.
- `NeverBlockPolicy` protects both apex and `www.` variants of registered primary domains.
- Compile-time check in `compiler.py` fails if any shipped rule matches a never-block domain.
- Runtime evaluation ensures `NeverBlockPolicy` shields primary domains before adaptive learners or adult filter patterns.

### DNS-02: Zero internet slowdown
- Avoid dropping DNS queries on networks before `NET_CAPABILITY_VALIDATED` is reached.
- Cache preferred upstream DNS resolver to avoid repeated timeouts on multi-resolver networks.
- Bounded DNS cache lookup and fast failover.

### DNS-03: Bypass detection & transparency
- Transparent Private DNS warning when Android Private DNS (DoT) is enabled.
- Clear communication that direct-IP connections and DoH inside browsers cannot be intercepted without root/CA tampering.
