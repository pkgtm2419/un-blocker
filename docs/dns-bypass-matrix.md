# DNS bypass and coverage matrix

| Connection type | Intercepted? | User messaging / test |
| --- | --- | --- |
| Port-53 DNS routed through local VPN | Intended yes | Validate IPv4/IPv6, UDP/TCP, real service |
| Android strict Private DNS (DoT) | May bypass local port-53 capture | `ProtectionUiStateMapper` limited coverage warning; device test |
| Browser or app-specific DoH | May bypass | Clearly disclose; test chosen browser settings |
| Hardcoded server IP, no DNS | No DNS query to intercept | Do not claim blocked traffic |
| Existing DNS cache/previous result | Not necessarily re-queried | Test after cache flush and reconnection |
| Shared host carrying ads + wanted content | DNS cannot separate resources | Avoid broad rule that breaks wanted media |
| Captive portal / network resolver change | Depends on usable underlying network | NeverBlock, resolver epoch and fail-safe tests |
| OEM restrictions, other VPN, force stop | Session may not run | Status and recovery path, no false green indication |

This matrix is a **qualitative capability explanation**, not an executed platform device certification. Review [DNS pipeline](DNS_PIPELINE.md) and [privacy flow](PRIVACY_DATA_FLOW.md).
