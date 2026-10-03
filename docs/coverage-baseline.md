# Coverage Baseline & Evaluation Report

## 1. Overview and Numbers Policy
In accordance with `ANTIGRAVITY_UNBLOCKER_SPEC.md` §0.3 and §6, Un-Blocker does not publish bare, unmeasured, or marketing-style effectiveness percentages. Blocking recall and false-positive rates are strictly grounded in deterministic evaluation of a reproducible, labeled corpus against specific list and code revisions.

## 2. Evaluation Metadata
- **Evaluated Commit**: `d0855c7` (Branch `main`, development `1.4.0`, `versionCode 7`)
- **Date**: 2026-10-04
- **Rule Engine**: Binary `RuleSet` (UBR2 format, little-endian reversed-label binary search)
- **Active Rule Count**: **73,213** rules in `dns-rules.bin` (2.0 MB compressed asset)

### Vendored List Revisions
| Dataset | Source / Provider | Pinned Revision / Commit | License | Rules Contributed |
|:---|:---|:---|:---|:---|
| **StevenBlack Hosts** | `StevenBlack/hosts` | `21605ccaecf26941005d4a7a3c1267af234599cf` | MIT | 72,233 entries |
| **Public Suffix List** | `publicsuffix/list` | `a179a48c465e818cfd8d626691cb317985da87fb` | MPL-2.0 | Registrable domain boundaries |
| **Project Seed Lists** | `ad_domains.txt` / `adult_domains.txt` | Repository `main` | Apache-2.0 | Curated seeds & category rules |
| **Allow & Never-Block Rules** | `allow.txt` / `never_block.txt` | Repository `main` | Apache-2.0 | Exception & safety guards |

---

## 3. Evaluation Corpus & Results

Evaluation was executed using `app/src/test/java/com/unblocker/app/quality/CoverageReportTest.kt` on the labeled dataset `app/src/test/resources/labeled-hosts.csv`.

### 3.1 Summary Metrics
| Metric | Result | Target Gate |
|:---|:---|:---|
| **AD Hosts Blocked** | 18 / 19 (94.7%) | Informational (reported) |
| **TRACKER Hosts Blocked** | 13 / 22 (59.1%)* | Informational (reported) |
| **Combined AD + TRACKER Recall** | **31 / 41 (75.61%)** | Informational (reported) |
| **False Positives on NEEDED Hosts** | **0 / 58 (0.0%)** | **Strict Gate: 0** (PASSED) |
| **OTHER (Neutral) Hosts Blocked** | 0 / 4 (0.0%) | Monitored |

*\*Note on Tracker Apexes:* Under PR-2 (§A.5, Workstream A), apex marketing and documentation domains for vendor services (e.g. `amplitude.com`, `branch.io`, `segment.io`, `mixpanel.com`) are explicitly unblocked to prevent breaking developer dashboards, whereas actual telemetry and tracking endpoints (`api.amplitude.com`, `api.branch.io`, `api.segment.io`, `api.mixpanel.com`) are strictly blocked.

---

## 4. Host Classification Breakdown

### 4.1 Advertising Hosts (`AD`)
| Host | Count | Status | Reason |
|:---|:---:|:---:|:---|
| `doubleclick.net` | 42 | BLOCKED | Static List Match (`doubleclick.net`) |
| `googleads.g.doubleclick.net` | 38 | BLOCKED | Static List Match (`googleads.g.doubleclick.net`) |
| `pagead2.googlesyndication.com` | 27 | BLOCKED | Static List Match (`pagead2.googlesyndication.com`) |
| `admob.com` | 15 | BLOCKED | Static List Match (`admob.com`) |
| `a.applovin.com` | 12 | BLOCKED | Static List Match (`a.applovin.com`) |
| `unityads.unity3d.com` | 22 | BLOCKED | Static List Match (`unityads.unity3d.com`) |
| `api.vungle.com` | 14 | BLOCKED | Static List Match (`api.vungle.com`) |
| `vungle.com` | 10 | BLOCKED | Static List Match (`vungle.com`) |
| `ads.inmobi.com` | 16 | BLOCKED | Static List Match (`ads.inmobi.com`) |
| `inmobi.com` | 8 | BLOCKED | Static List Match (`inmobi.com`) |
| `criteo.com` | 19 | BLOCKED | Static List Match (`criteo.com`) |
| `static.criteo.net` | 15 | BLOCKED | Static List Match (`static.criteo.net`) |
| `taboola.com` | 16 | BLOCKED | Static List Match (`taboola.com`) |
| `trc.taboola.com` | 20 | BLOCKED | Static List Match (`trc.taboola.com`) |
| `outbrain.com` | 13 | BLOCKED | Static List Match (`outbrain.com`) |
| `adnxs.com` | 18 | BLOCKED | Static List Match (`adnxs.com`) |
| `ib.adnxs.com` | 22 | BLOCKED | Static List Match (`ib.adnxs.com`) |
| `popads.net` | 11 | BLOCKED | Static List Match (`popads.net`) |
| `sdk.applovin.com` | 19 | ALLOWED | Shipped exception (`allow.txt`) |

### 4.2 Tracking & Analytics Hosts (`TRACKER`)
| Host | Count | Status | Reason |
|:---|:---:|:---:|:---|
| `app.adjust.com` | 25 | BLOCKED | Static List Match (`app.adjust.com`) |
| `api.appsflyer.com` | 30 | BLOCKED | Static List Match (`api.appsflyer.com`) |
| `t.appsflyer.com` | 28 | BLOCKED | Static List Match (`t.appsflyer.com`) |
| `api.branch.io` | 21 | BLOCKED | Static List Match (`api.branch.io`) |
| `api.kochava.com` | 18 | BLOCKED | Static List Match (`api.kochava.com`) |
| `data.flurry.com` | 17 | BLOCKED | Static List Match (`data.flurry.com`) |
| `api.mixpanel.com` | 24 | BLOCKED | Static List Match (`api.mixpanel.com`) |
| `api.segment.io` | 26 | BLOCKED | Static List Match (`api.segment.io`) |
| `api.amplitude.com` | 29 | BLOCKED | Static List Match (`api.amplitude.com`) |
| `script.hotjar.com` | 31 | BLOCKED | Static List Match (`script.hotjar.com`) |
| `c.clarity.ms` | 33 | BLOCKED | Static List Match (`c.clarity.ms`) |
| `api.singular.net` | 14 | BLOCKED | Static List Match (`api.singular.net`) |
| `mobile-collector.newrelic.com` | 20 | BLOCKED | Static List Match (`mobile-collector.newrelic.com`) |
| *Vendor apexes (`amplitude.com`, `branch.io`, `adjust.com`, `kochava.com`, `flurry.com`, `mixpanel.com`, `segment.io`, `hotjar.com`, `clarity.ms`)* | — | ALLOWED | Unblocked vendor portal/documentation sites |

### 4.3 Essential & Infrastructure Hosts (`NEEDED` — Zero False Positives Guarded)
Every host below evaluated to **ALLOWED (Allowed benign traffic)**:
- **Connectivity & OS Services**: `connectivitycheck.gstatic.com`, `connectivitycheck.android.com`, `clients3.google.com`, `time.android.com`, `time.google.com`, `captive.apple.com`, `www.msftconnecttest.com`
- **Core Consumer & Developer Platforms**: `google.com`, `www.google.com`, `accounts.google.com`, `gstatic.com`, `www.gstatic.com`, `apis.google.com`, `play.google.com`, `mtalk.google.com`, `apple.com`, `icloud.com`, `microsoft.com`, `login.microsoftonline.com`, `github.com`, `api.github.com`, `raw.githubusercontent.com`, `registry.npmjs.org`, `pypi.org`, `stackoverflow.com`, `amazon.com`, `aws.amazon.com`, `netflix.com`, `spotify.com`, `wikipedia.org`, `whatsapp.com`, `signal.org`, `telegram.org`, `paypal.com`, `stripe.com`, `api.stripe.com`
- **CDNs & Public Web Assets**: `cloudflare.com`, `cdnjs.cloudflare.com`, `cdn.jsdelivr.net`, `fonts.googleapis.com`
- **Universities & Educational Institutions**: `cam.ac.uk`, `www.cam.ac.uk`, `cambridge.org`, `adult.education.gov.au`, `adult-learning.org`, `sex-ed.example.org`, `essex.ac.uk`
- **Lexical False Positive Probes**: `comic-strip.com`, `strip-mall.example.com`, `fap.rs`, `adultswim.com`, `sextant.com`, `sexualhealth.org`
- **Vendor Portals & Dashboards**: `appsflyer.com`, `applovin.com`, `newrelic.com`, `singular.net`, `braze.com`

---

## 5. Harness Tooling
To reproduce or extend this evaluation:
1. Capture HTTP Archives (.har) from representative browsing sessions.
2. Run `tools/eval/har_hosts.py <file.har> -o custom-hosts.csv -m app/src/test/resources/labeled-hosts.csv` to extract and label unique hosts.
3. Execute `bash gradlew testDebugUnitTest --tests com.unblocker.app.quality.CoverageReportTest`.
4. Inspect output generated in `app/build/reports/coverage/coverage-report.txt`.
