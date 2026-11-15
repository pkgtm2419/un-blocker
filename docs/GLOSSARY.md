# Glossary

- **VPN/TUN:** Android virtual network interface used here to intercept DNS; not an anonymity/full-tunnel service.
- **DNS:** Domain Name System; hostname questions and resolver replies.
- **DoH / DoT:** DNS-over-HTTPS / DNS-over-TLS; encrypted paths can bypass local port-53 interception.
- **EDNS:** DNS extension flags/UDP payload negotiation; parser and response size constraints apply.
- **QTYPE/QCLASS/TxID:** DNS query type/class and transaction ID; required response validation data.
- **UBR2:** project's compact binary reversed-label rules format.
- **PSL:** Public Suffix List; domain-boundary reference, not a blocklist.
- **CNAME:** DNS canonical-name alias; must not let untrusted responses poison rule or learning decisions.
- **HMAC:** keyed hash used for pseudonymous, not anonymous, on-device learned/user rule identifiers.
- **SAF:** Android Storage Access Framework; user-directed export file selection.
- **Room:** SQLite-backed on-device database with entities, DAOs, migration and schema exports.
- **Hilt:** dependency-injection tooling for app/module/worker wiring.
- **FGS:** Android foreground service with user-visible notification and platform restrictions.
- **Source-observed:** reviewed in code, not necessarily executed.
- **Drift:** checked-in docs/generated contents differ from deterministic output at checkout HEAD.
