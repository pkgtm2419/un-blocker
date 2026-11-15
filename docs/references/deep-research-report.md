> Imported reference, 2026-11-14. Original: `unb/deep-research-report.md`. Historical analysis or proposal; claims and prior test results have not been independently rerun for this checkout. Original chat citation markers are not usable source links. See [reference index](README.md) for status.

# Can One Android App Block 100% of Ads Across Every App?

## Executive conclusion

No. On a normal, unrooted Android 16 or Android 17 phone, a single third-party app cannot reliably block **100% of advertisements across every other application**. Android's sandbox deliberately prevents one app from arbitrarily inspecting or rewriting another app's private state or UI, while modern encrypted networking prevents a local VPN from seeing most HTTPS request paths or response bodies without TLS interception. citeturn683880search0turn138143search3

Even with root, Magisk/Zygisk hooks, TLS interception, patched apps and per-app logic, **100% remains unattainable in the general case**. The decisive reason is that some advertising is not technically separable from the requested content: ads can come from the same host as normal content, be embedded in the same API response, or be stitched server-side into a video stream before it reaches the phone. In server-side ad insertion, the client can receive advertising and program content as portions of the same media stream; there may be no independent network request that an ad blocker can safely discard. citeturn415264search2turn415264search10

The practical goal should therefore be:

**Block the large majority of technically distinguishable advertising and tracking, while never breaking normal application traffic.**

For something like your Un-Blocker project, the strongest general-purpose design on an **unrooted** Android phone is a privacy-preserving local `VpnService` combined with DNS/domain filtering and selective metadata-level connection filtering, supplemented by browser-level content filtering where browser APIs permit it. That can work very well against conventional third-party ad SDKs and trackers, but it will not reliably remove YouTube in-stream ads, Instagram/Facebook sponsored posts, or ads embedded directly into first-party application responses. Android officially exposes `VpnService` for device-level traffic tunneling, but only one VPN service can be active for a user at a time. citeturn138143search0turn138143search1

## Why the obvious approaches have hard limits

### DNS blocking

DNS filtering is the safest and simplest system-wide technique.

Suppose a game loads normal content from:

`api.game.example`

but its advertisements from:

`ads.adnetwork.example`

A DNS filter can return a blocked response for the second hostname while allowing the first.

This works well because the blocker does not have to decrypt the user's HTTPS traffic. The application only needs to observe DNS requests and compare hostnames against lists.

Research into Android-wide VPN-based ad blocking has found that domain-based filtering can provide substantial effectiveness. A 2021 study reported domain filtering accuracy above 80% in its evaluated system and found URL-level filtering roughly 9.8–17 percentage points more effective than domain-only filtering in its evaluated datasets. Those numbers should be treated as historical experimental results, not as a universal 2026 success rate. citeturn415264search5turn415264search11

DNS blocking fails when the advertisement and desired content share a hostname.

For example:

```text
video.example.com/content/123
video.example.com/ad/456
```

DNS only sees:

```text
video.example.com
```

Blocking that hostname blocks both.

This is why DNS-based products explicitly acknowledge that YouTube, Twitch and social-network sponsored content cannot be reliably removed with DNS filtering alone. AdGuard documents that YouTube ads may share delivery domains with normal videos, making them indistinguishable at the DNS layer. citeturn415264search0turn415264search1

That same limitation applies to many Instagram, Facebook and other first-party sponsored-feed requests. citeturn415264search1

### Local VPN filtering

Android's `VpnService` is substantially more powerful than plain Private DNS. It creates a virtual network interface, allowing your application to receive network packets before forwarding them. Android documents this specifically as the supported API for user-space VPN applications. citeturn138143search0turn138143search2

Conceptually:

```text
Third-party app
      ↓
Android network stack
      ↓
Your VpnService/TUN
      ↓
Filtering engine
      ↓
Internet
```

From those packets, an unrooted blocker can potentially inspect information such as:

```text
destination IP
transport protocol
DNS requests that traverse the VPN
some TLS handshake metadata
connection timing
packet size patterns
```

but it normally cannot see:

```text
HTTPS URL path
HTTP headers inside TLS
JSON/API responses
video contents
HTML after encryption
application-specific protobuf payloads
```

Modern HTTPS encrypts those fields.

A local VPN therefore gives **better traffic control**, but does not automatically produce browser-extension-level content filtering.

It is still probably the best foundation for an Un-Blocker-style application because you can combine DNS blocking, destination-IP decisions, per-app policy, caching and firewall behavior without modifying other applications.

Android also supports always-on VPN and a system option to block connections that do not traverse the VPN. citeturn138143search1

Android 17 additionally exposes a system-managed VPN app-exclusion settings screen for selecting apps that bypass a VPN, which is useful for compatibility handling. citeturn201435search1

### TLS interception and MITM

The theoretically attractive next step is:

```text
App
 ↓ TLS
Your blocker
 ↓ new TLS connection
Server
```

The blocker dynamically issues a fake certificate for `example.com`, decrypts the traffic, filters advertisements, then re-encrypts it.

That gives access to:

```text
full URL
headers
JSON
HTML
JavaScript
media manifests
```

which could dramatically improve filtering.

However, it is **not generally viable across arbitrary Android apps**.

Since Android 7, applications targeting API 24 or later **do not trust user-installed certificate authorities by default**. Apps must explicitly opt in if they want to trust user CAs. citeturn971622search0turn971622search3

So your blocker may install a user CA, yet many apps will still reject your forged certificate.

Apps may also use certificate pinning, restricting accepted certificates or public keys. Android's Network Security Configuration explicitly supports pinning. citeturn971622search4

A simplified example is:

```text
Server presents:
Google/Public CA certificate → accepted

Your blocker presents:
Un-Blocker CA certificate → rejected

App says:
SSLHandshakeException
```

Even where an app normally accepts a custom CA, application-specific certificate validation can still prevent interception.

This makes universal HTTPS MITM unreliable on unrooted devices.

It is also a much larger security liability: if your private CA key or interception engine is compromised, the blocker becomes capable of decrypting sensitive traffic from applications that trust it.

For a privacy-oriented open-source product, I would **not make TLS MITM the default architecture**.

### QUIC, HTTP/3, DoH, DoT and ECH

Modern networking makes passive network classification harder.

Android's Cronet stack supports HTTP/3 over QUIC and enables QUIC by default; Google says Cronet is used in applications including YouTube, Google, Google Photos and Maps. citeturn971622search5turn971622search6

QUIC encrypts almost all transport metadata above UDP, so packet inspection yields less application-level information than older plaintext HTTP designs.

Encrypted DNS creates another problem.

An app can perform DNS resolution over:

```text
HTTPS — DoH
TLS — DoT
HTTP/3 — encrypted DNS transport
```

Android itself has supported secure DNS transport for years, and Android's security documentation explicitly warns that applications can implement customized DNS behavior that differs from the device resolver. citeturn138143search5

If an app performs its own DoH request:

```text
App
 ↓ HTTPS
dns-provider.example
```

your DNS filter does not necessarily see:

```text
ads.example.com?
```

It may see only a TLS connection to the resolver.

A full-device VPN can sometimes block known DoH providers, but applications can switch resolver endpoints, use their own infrastructure, hard-code addresses or bundle resolver logic.

ECH makes hostname observation harder again. RFC 9849 standardized TLS Encrypted Client Hello in March 2026. ECH encrypts sensitive ClientHello information including the real SNI, meaning an observer may know the client is talking to a provider but not necessarily which hidden hostname behind that provider is being accessed. citeturn971622search7turn971622search8

Consequently, a future-proof ad blocker should **not assume that SNI will always remain visible**.

### Server-side ad insertion

This is the strongest technical argument against any claim of "100% device-wide ad blocking."

Imagine the server sends:

```text
segment 1: movie
segment 2: movie
segment 3: advertisement
segment 4: advertisement
segment 5: movie
```

all from:

```text
stream.example.com
```

and possibly from the exact same authenticated video session.

With server-side ad insertion, the server can construct the stream before delivery. IETF streaming guidance describes SSAI specifically as a model where advertising segments may remain on the same server and become part of the media stream. citeturn415264search2turn415264search9

The blocker cannot simply deny the ad hostname because there is no separate ad hostname.

To distinguish those frames, you would need application-specific knowledge of manifests, timing metadata or media semantics. If the advertising is fully stitched and indistinguishable from program content, even that may not be possible.

This means **100% blocking is mathematically not a meaningful guarantee for arbitrary applications**.

## What works for different kinds of apps

A useful way to think about effectiveness is by advertising delivery model rather than by app brand.

| Ad delivery type | DNS | Local VPN | TLS MITM | Root/hooks | Browser content blocker |
|---|---:|---:|---:|---:|---:|
| Separate ad domain | Excellent | Excellent | Excellent | Excellent | Excellent |
| Separate ad IP but no useful DNS | Weak | Good | Good | Good | Good |
| Same domain, different URL | No | Usually no | Good if interceptable | Potentially good | Excellent |
| Ad in first-party JSON response | No | No | Possible | Potentially excellent | Sometimes |
| Native sponsored feed item | No | No | Difficult | App-specific | Not for native app |
| YouTube official app video ad | Poor | Poor | Unreliable | App-specific | N/A |
| Browser YouTube ads | Poor | Poor | Possible | N/A | Stronger |
| Server-side stitched video ad | No | No | Often no | Application-specific/limited | Limited |
| WebView third-party ad requests | Good if separate host | Good | Possible | Possible | Host app only |
| Game ad SDK on separate domains | Often strong | Strong | Strong if interceptable | Strong | N/A |

These are architectural assessments rather than universal percentages.

There is evidence that sophisticated cross-app traffic classifiers can perform substantially better than simple blocklists. The NoMoAds research system reported an F-score of 87.6% using network-layer features, 93.7% when URL features were added, 96.3% with additional HTTP-header features, and up to 97.8% with additional app/context features on its research dataset. It also reported average classification under three milliseconds per packet/request classification operation. citeturn415264search12

Those results are important because they show what **richer traffic visibility can achieve**, but they do not imply that a modern Android 17 application can reproduce the same visibility. Much of today's traffic is more strongly encrypted, and browser/app networking has changed considerably since that study.

Therefore, I would **not publish a single "95% blocked" claim** unless you create a contemporary reproducible benchmark.

## What root changes—and what it still cannot solve

Root dramatically changes what is technically possible.

Magisk provides superuser access, module-based filesystem modification and Zygisk, which can execute custom code inside Android application processes. citeturn683880search5turn683880search4

With Zygisk/Xposed-style hooking, you can potentially intercept functions such as:

```text
OkHttp request creation
WebView loading
ad SDK methods
JSON parsing
view inflation
video-player state
certificate verification
```

A framework such as LSPosed is specifically designed to inject hooks that modify app behavior in memory. citeturn683880search6turn683880search8

That enables strategies impossible for a normal application.

For example:

```text
YouTube app
  ↓
hook internal ad response parser
  ↓
identify ad object
  ↓
skip ad object before UI/player uses it
```

or:

```text
Instagram
  ↓
hook feed model
  ↓
find sponsored=true
  ↓
remove item before RecyclerView rendering
```

This approach can be extremely powerful.

But now your blocker is no longer one generic ad-blocking engine.

It becomes:

```text
Generic network blocker
+
YouTube-specific module
+
Instagram-specific module
+
Facebook-specific module
+
TikTok-specific module
+
game-SDK hooks
+
constant compatibility maintenance
```

An app update can rename classes, change protocol formats, move code to native libraries or detect hooks.

Root also changes device security posture. Android's Play Integrity documentation states that `MEETS_DEVICE_INTEGRITY` on Android 13+ includes hardware-backed evidence that the bootloader is locked and the OS is a certified manufacturer image; rooted devices or environments showing API hooking may fail normal device-integrity verdicts. citeturn513599search0turn513599search1

That can cause incompatibility with:

```text
banking apps
streaming DRM
enterprise apps
games with anti-cheat
wallets
some Google Play-protected services
```

depending on each application's enforcement.

Unlocking the bootloader also weakens the original Verified Boot trust model; AOSP explicitly warns that software integrity cannot be guaranteed in the unlocked state. citeturn513599search2turn513599search3

So root gives you **more ad-blocking power in exchange for less platform trust and substantially more maintenance**.

Even root still cannot generally remove indistinguishable server-side advertising without service-specific logic.

## What I would build for an Un-Blocker successor

For a safe open-source project, I would separate the product into layers.

### Core filtering layer

Use `VpnService` as the transport foundation.

```text
                   ┌─────────────────────┐
Third-party apps → │ Android VpnService  │
                   └─────────┬───────────┘
                             │
                ┌────────────▼────────────┐
                │ Packet / flow classifier│
                └────────────┬────────────┘
                             │
              ┌──────────────┴──────────────┐
              │                             │
        DNS inspection               IP/flow policy
              │                             │
       hostname engine               reputation/cache
              │                             │
              └──────────────┬──────────────┘
                             │
                     Internet upstream
```

Android requires explicit user consent before an app becomes the active VPN, and `VpnService.protect()` lets the blocker exempt its own upstream sockets to avoid routing loops. citeturn138143search0turn138143search2

Use a foreground service because persistent VPN operation is long-lived. Android's foreground-service documentation explicitly recognizes configured VPN apps as a supported long-running category. citeturn138143search8

### DNS engine

Implement:

```text
A / AAAA
CNAME chain handling
HTTPS/SVCB awareness
NXDOMAIN or synthetic-null responses
allowlist
blocklist
wildcards
per-app override
cache
TTL handling
IPv6 parity
```

Use multiple curated lists rather than an enormous unreviewed merge.

Internally compile rules into efficient structures such as:

```text
suffix trie
compressed radix tree
hash set for exact hosts
Bloom filter as pre-check
```

Flow:

```text
query → cache → allowlist → exact rules → suffix rules → upstream
```

Never make a network request for every rule evaluation.

### Encrypted-DNS handling

Offer modes rather than silently trying to defeat all encrypted DNS.

For example:

```text
Standard
Block only visible DNS

Strict
Block known public DoH/DoT endpoints

Compatibility
Allow app-provided encrypted DNS
```

Do not pretend you can universally intercept arbitrary DoH.

ECH and encrypted DNS increasingly hide signals that passive filtering historically relied on. citeturn971622search7turn138143search5

### Per-app filtering

`VpnService.Builder` supports per-application inclusion/exclusion models, and Android's VPN platform is designed around the concept of permitted/disallowed applications. citeturn138143search1

Expose:

```text
YouTube       Compatibility mode
Banking app   Bypass
Browser       Strict
Games         Strict
WhatsApp      Normal
```

This is important because aggressive filtering that is acceptable for a game could break authentication or payments in another app.

### Browser companion

For browser traffic, network filtering alone is not enough.

Where the browser supports content blockers/extensions, use request- and DOM-level rules such as:

```text
network URL filtering
scriptlet rules
element hiding
CSS selectors
DOM mutation handling
```

That is why browser blockers can remove same-domain webpage advertisements that DNS cannot.

For **your own WebView**, Android exposes `WebViewClient.shouldInterceptRequest()`, allowing the host application to intercept individual resource requests. But that only controls WebViews belonging to your application; you cannot attach that callback to arbitrary third-party apps' WebViews because Android's sandbox isolates apps. citeturn201435search3turn683880search0

### Optional root module

If you want maximum capability, make this a **separate optional project**, not part of the normal APK.

Architecture:

```text
Un-Blocker Core
    │
    ├── non-root VPN engine
    │
    └── optional Zygisk module
            │
            ├── YouTube adapter
            ├── Instagram adapter
            └── app-specific hooks
```

Do not require root for ordinary users.

This keeps the primary app:

```text
safer
easier to audit
Play-compatible
device-integrity friendly
```

while power users can deliberately choose the higher-risk module.

## Security, privacy and Play Store constraints

A device-wide network blocker occupies a privileged privacy position because it can observe network metadata for other applications.

Google Play's current VPN policy allows `VpnService` where VPN/network/security functionality is a core feature or one of the permitted categories, but requires clear disclosure and forbids collecting sensitive information without disclosure and consent or manipulating another app's traffic for monetization. citeturn138143search6turn138143search7

For an open-source blocker I would impose these requirements:

```text
No analytics SDK
No advertising SDK
No account requirement
No cloud request logs by default
No remote traffic proxy by default
No uploaded DNS history
Deterministic/reproducible builds
Signed releases
SBOM
CodeQL/Android lint
Dependency scanning
Fuzz DNS parser
Strict input limits
```

Logging should be:

```text
OFF by default
or
aggregate-only
```

and users should be able to delete it immediately.

Blocklists should be signed or hash-verified.

Updates should use:

```text
HTTPS
maximum file size
format validation
atomic replace
rollback on invalid rule file
```

The VPN parser is particularly security-sensitive because it processes untrusted network packets continuously.

Do not use Accessibility Service merely to scan every application's UI for words like:

```text
Sponsored
Ad
Promoted
```

Android allows accessibility services to inspect window content and draw overlays, but Google Play tightly restricts Accessibility API usage, requires narrow purposes and disclosures for non-accessibility tools, and prohibits using it to bypass platform security controls. citeturn201435search7turn193814search0turn193814search1

It is also technically fragile:

```text
text may be obfuscated
native surfaces may expose no accessibility tree
video ads are not UI nodes
labels change by language
false positives could hide real content
```

So Accessibility should not be your main ad-blocking engine.

## Performance and battery engineering

A well-designed DNS-first VPN can be lightweight because most decisions happen at connection setup or DNS-query time rather than deep-inspecting every payload.

Important performance goals should include:

```text
DNS decision p50 < 1 ms
DNS decision p99 < 5 ms
zero per-query disk writes
bounded memory
bounded worker queues
no busy polling
no packet copies unless required
cache common results
batch statistics
```

The older NoMoAds research system demonstrated that on-device classification can be practical, reporting roughly three milliseconds average per classification in its environment. citeturn415264search12

For your application, however, do not publish battery claims without physical-device testing.

You should benchmark at least:

```text
screen-on browsing
screen-off idle
Wi-Fi
5G
weak-network conditions
video streaming
gaming
DoH applications
QUIC applications
IPv6
network switching
airplane-mode recovery
```

Measure:

```text
mAh/hour
CPU time
wakeups/hour
memory RSS/PSS
DNS latency
TCP connection latency
page load time
packet loss
VPN restarts
```

A reasonable test design would compare:

```text
Baseline: VPN disabled
Mode A: VPN + no rules
Mode B: DNS blocking
Mode C: aggressive flow filtering
```

That separation tells you how much overhead comes from the VPN itself versus the filtering logic.

Android 16 also tightened local-network access rules for some application networking behaviors, so Android 16/17 need to be explicitly represented in the compatibility matrix rather than inferred from older API levels. citeturn201435search2

## Realistic effectiveness targets

I would not give users one universal percentage.

Instead publish category-specific benchmark results.

A realistic measurement matrix could look like this:

| Category | What should be measured |
|---|---|
| Third-party game advertising | Number of ad impressions successfully suppressed |
| Web ads | Requests + visible ad slots removed |
| Tracking | Known tracking endpoints blocked |
| YouTube native | Pre-roll/mid-roll impressions |
| Instagram/Facebook | Sponsored feed elements removed |
| Streaming SSAI | Ad minutes removed |
| App startup breakage | Apps failing after filtering |
| False positive rate | Legitimate requests incorrectly blocked |

There is independent historical evidence that domain-based Android blockers can exceed roughly 80% classification accuracy in selected datasets, while richer URL/header-based systems have reached above 90% F-score in research environments. citeturn415264search5turn415264search12

But those findings should **not be translated into "this app blocks 90% of all Android ads"**.

For your product I would report something like:

```text
Third-party-domain ads: measured benchmark result
Tracking requests: measured benchmark result
YouTube native ads: unsupported / best-effort
Instagram/Facebook sponsored feed: unsupported
Server-side stitched ads: unsupported
Browser ads: companion blocker recommended
```

That is both technically honest and more useful to users.

## Recommended approach for your project

For an app like `pkgtm2419/un-blocker`, I would build toward this stack:

```text
                 Un-Blocker Next

 ┌──────────────────────────────────────────┐
 │ UI / policy engine                       │
 │ per-app modes, statistics, allowlists    │
 └────────────────────┬─────────────────────┘
                      │
 ┌────────────────────▼─────────────────────┐
 │ Android VpnService                       │
 │ TUN + protected upstream sockets         │
 └────────────────────┬─────────────────────┘
                      │
     ┌────────────────┼───────────────────┐
     │                │                   │
 ┌───▼────┐     ┌─────▼─────┐      ┌─────▼─────┐
 │ DNS    │     │ IP/flow   │      │ App policy│
 │ engine │     │ classifier│      │ mapping   │
 └───┬────┘     └─────┬─────┘      └───────────┘
     │                │
 ┌───▼────────────────▼─────────────────────┐
 │ Curated local rules + local cache        │
 │ No telemetry / no remote proxy           │
 └──────────────────────────────────────────┘

Optional:
Browser companion
Optional root/Zygisk extensions
```

I would explicitly **avoid** making universal HTTPS MITM part of the default system.

Your feature levels could be:

```text
Level 1 — DNS protection
Safest
Lowest battery cost

Level 2 — Full local VPN
DNS + flow/IP rules + per-app controls

Level 3 — Browser companion
DOM + URL content filtering

Level 4 — Root module
App-specific hooks for advanced users
```

That architecture gives the user progressively stronger blocking without pretending that one technique can solve every advertising model.

The security model of Android 16 and Android 17 actually makes this layered strategy more important: apps remain sandboxed from each other, user CAs are not universally trusted, certificate pinning remains possible, QUIC/HTTP3 are widespread, encrypted DNS can bypass naive DNS interception, and ECH is now standardized. citeturn683880search0turn971622search0turn971622search4turn971622search6turn971622search7

The practical conclusion is:

**Unrooted Android:** a single privacy-preserving app can block a large fraction of conventional third-party ads and trackers device-wide, but cannot reliably remove first-party, same-origin, native sponsored-feed or server-side-stitched advertising.

**Rooted Android:** app-specific hooks and deeper TLS/network manipulation can raise effectiveness substantially, but at the cost of device-integrity failures, increased security risk, per-app maintenance and incompatibility—and still cannot provide a truthful universal 100% guarantee. citeturn513599search0turn683880search4

For a public open-source project, I would therefore define the product goal not as **"block 100% of ads"**, but as **"maximum privacy-preserving device-wide blocking without breaking applications or decrypting user content."** That is feasible, technically defensible, and compatible with the way Android's security architecture is designed.
