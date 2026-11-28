# F-Droid Submission Checklist — Un-Blocker

> Owner action required: submit the form at https://gitlab.com/fdroid/fdroiddata after completing this checklist.
> IzzyOnDroid alternative: submit via https://github.com/IzzySoft/fdroiddata (simpler, faster review).

## Metadata locations (Fastlane / F-Droid standard)

| File | Status |
|---|---|
| `fastlane/metadata/android/en-US/title.txt` | ✅ Created |
| `fastlane/metadata/android/en-US/short_description.txt` | ✅ Created (≤80 chars) |
| `fastlane/metadata/android/en-US/full_description.txt` | ✅ Created |
| `fastlane/metadata/android/en-US/changelogs/38.txt` | ✅ Created (versionCode 38) |
| `fastlane/metadata/android/en-US/images/phoneScreenshots/` | ⚠ Placeholder dir created — owner must add ≥3 screenshots |
| `fastlane/Appfile` | ✅ Created |
| `fastlane/Fastfile` | ✅ Created |

## Anti-feature review

F-Droid requires declaring anti-features. Review each before submission:

| Anti-feature | Applies? | Notes |
|---|---|---|
| `Ads` | ❌ No | No ad SDK or ad network code |
| `Tracking` | ❌ No | No analytics/crash SDK; verified in AGENTS.md rule 1 |
| `NonFreeNet` | ❌ No | All network calls are user-initiated rule-list downloads over HTTPS |
| `NonFreeAssets` | ❌ No | All assets are project-authored or license-compatible |
| `NonFreeDep` | ⚠ Verify | No proprietary Google Play Services API is required; uses standard `VpnService` and `AndroidX`. Run: `./gradlew dependencies | grep -i 'play-services'` and confirm empty |
| `UpstreamNonFree` | ❌ No | Apache 2.0 license throughout |
| `KnownVuln` | ❌ No | Verify at submission time against latest CVE advisories for declared deps |
| `NoSourceSince` | ❌ No | Source is on GitHub and matches the declared version |

## Reproducible build check

Two clean builds **should** produce APKs with identical dex content (unsigned). Determinism caveats:

- AGP 9.5.1 embeds build timestamps in resources by default.
  To improve reproducibility set in `app/build.gradle.kts`:
  ```kotlin
  // Add inside android { } block:
  buildFeatures { buildConfig = false } // avoids build timestamp in BuildConfig
  ```
  Or supply `-PbuildTime=0` via CI to pin timestamps.
- R8/ProGuard mapping files must be archived alongside each release APK.
- **Result (NOT RUN):** reproducibility check requires two independent clean builds and binary diff — not performed by this agent. Owner: run `./gradlew clean assembleRelease && cp app/build/outputs/apk/release/*.apk /tmp/build1.apk && ./gradlew clean assembleRelease && diff /tmp/build1.apk app/build/outputs/apk/release/*.apk` and document any differences in `docs/qa/`.

## Proprietary dependency scan

```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath | \
  grep -iE 'play-services|firebase|crashlytics|analytics|ads|fabric'
```

Expected result: **no matches** (all these are forbidden by AGENTS.md rule 1).

## Draft F-Droid metadata YAML (for fdroiddata MR)

```yaml
Categories:
  - Security
  - Internet

License: Apache-2.0

AuthorName: pkgtm2419
SourceCode: https://github.com/pkgtm2419/un-blocker
IssueTracker: https://github.com/pkgtm2419/un-blocker/issues

Name: Un-Blocker
AutoName: Un-Blocker

RepoType: git
Repo: https://github.com/pkgtm2419/un-blocker

Builds:
  - versionName: 2.7.2
    versionCode: 38
    commit: v2.7.2
    subdir: app
    gradle:
      - release

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 2.7.2
CurrentVersionCode: 38
```

> Adjust `commit:` to the exact tag or SHA of the release commit before submitting.

## Owner checklist before submission

- [ ] Add ≥3 phone screenshots to `fastlane/metadata/android/en-US/images/phoneScreenshots/`
- [ ] Verify no proprietary dependency (run command above)
- [ ] Tag the release commit as `v2.7.2` on GitHub
- [ ] Run reproducible-build check and document result
- [ ] Confirm `AndroidManifest.xml` has no `uses-sdk minSdkVersion` below 26
- [ ] Sign and upload the release APK to GitHub Releases
- [ ] Submit MR to fdroiddata or IzzyOnDroid repo with the YAML above
