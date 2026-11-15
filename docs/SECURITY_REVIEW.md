# Security review and threat model checklist

## Trust boundaries

1. **External network -> packet parser**: bounds checks, DNS compression/name/label limits, opcode and RCODE interpretation, IPv4/v6 packet/checksum validity.
2. **External resolver -> response**: match tx-ID/name/QTYPE/QCLASS, reject unsolicited and malformed replies, protect outbound sockets, verify TCP retry and response size caps.
3. **External filter URL -> local files/rule engine**: HTTPS only, redirects, DNS rebinding, private/loopback targets, length/quantity limits, source integrity and license, transactional replacement/rollback.
4. **App -> Android VPN**: verified consent, no accidentally exported entrypoints, foreground lifecycle, release signing, boot restrictions, no accidental full-device traffic interception.
5. **Query/feedback -> persistent storage**: private keys, HMAC identifiers, bounded plaintext local logs, opt-in raw events, non-destructive migrations, backup and CSV export rules.
6. **CI -> published artifacts**: secrets isolation, pinned tools, verified release signatures and version/tag alignment.

## Review table for a PR

| Question | Evidence required |
| --- | --- |
| Could a crafted DNS packet crash/hang/allocate unbounded memory? | Malformed/fuzz regression, code bounds and wall-time/resource result |
| Could spoofed resolver response be accepted? | Validated ID/question/class/type plus negative tests |
| Could remote list download reach private hosts? | Redirect/DNS-rebinding cases and actual socket behavior |
| Could essential sites become blocked? | Synthetic protected-host and independent false-positive cases |
| Could user DNS names leak? | Review Logcat/network/file/backup/CSV paths and regression |
| Does change enable global DNS fallback accidentally? | Concrete callers for app service and reusable module |
| Could legacy user rules or schema become unreadable? | Upgrade test and schema diff |
| Are dependencies/licensing/release signing correct? | SBOM/provenance and existing CI evidence |

## Evidence categories

- **Verified**: a command ran or dynamic device test executed, with date and result.
- **Source-observed**: code and test files reviewed, not executed.
- **Hypothesis**: plausible risk requiring proof, not a confirmed vulnerability.
- **Unverified**: unavailable tests/network/build environment; record as such.

Do not publish exploitable details before responsible disclosure; no special assertion of audit certification. Initial source-level findings are tracked in [initial review](audits/INITIAL_REVIEW_2026-11-13.md).
