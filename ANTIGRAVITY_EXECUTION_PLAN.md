# ANTIGRAVITY AI: Master Execution & Release Plan (`un-blocker` v2.2.0)

## 0. Mandatory Agent Execution Rules (STRICT)
You are operating as a Principal Android Systems & Network Engineer on `pkgtm2419/un-blocker`. Follow these rules without exception:
1. **Strict Phase-by-Phase Execution:** Execute **ONE phase at a time** in numerical order (Phase 1 -> Phase 5).
2. **Incremental File Modification:** Modify or create only the files scoped to the active phase. Do not break existing UI routes, package names, or Hilt dependency graphs.
3. **Mandatory Verification Gate:** At the end of each phase, execute the specified `./gradlew` verification command in the terminal. **STOP IMMEDIATELY** if compilation or tests fail, fix the root cause, re-verify, and only then move to the next phase.
4. **Zero Placeholders:** Every file you write or refactor must contain complete, compilable production code. Never leave `// TODO` blocks or truncated logic.

---

## Phase 1: Off-Heap Binary Rule Engine & Multi-Syntax Compiler
**Objective:** Eliminate the ~280 MB JVM heap spike and GC freezes caused by holding ~686,200 domain strings in memory. Normalize Hosts (`0.0.0.0`), Adblock/uBlock (`||domain^$third-party`), and plain-domain syntaxes into a sorted, deduplicated 64-bit FNV-1a binary index (`rules.bin`) mapped off-heap via `MappedByteBuffer`.

### 1.1 Create/Update `RuleCompiler.kt`
* **Path:** `data-store/src/main/java/com/unblocker/data/cache/RuleCompiler.kt` (or match existing package path for filter storage)
* **Implementation Requirements:**
  1. Write `extractCleanDomain(rawLine: String): String?`:
     - Ignore empty lines, comments (`#`, `!`, `[`), Adblock exceptions (`@@`), and cosmetic rules (`##`, `#@#`).
     - Strip inline comments (`#...`).
     - If the line starts with `||`, strip `||` and truncate at the first occurrence of `^`, `$`, or `/`.
     - If the line is in Hosts format (`0.0.0.0 domain.com`, `127.0.0.1 domain.com`, `::1 domain.com`), extract the second token.
     - Trim leading/trailing dots, convert to lowercase ASCII, reject `localhost` or strings without a dot (`.`), and validate characters (`a-z`, `0-9`, `.`, `-`, `_`).
  2. Write `fnv1a64(domain: CharSequence, start: Int = 0, end: Int = domain.length): Long`:
     - Compute 64-bit FNV-1a hash (`offsetBasis = -0x340d631b7bdddcdbL`, `prime = 0x100000001b3L`) directly over character indices `[start, end)` without allocating `Substring` objects, converting uppercase ASCII (`65..90`) to lowercase (`+32`) on the fly.
  3. Write `compileStreamsToBinaryFile(readers: List<BufferedReader>, outputFile: File): Int`:
     - Pre-seed mandatory DNS-over-HTTPS (DoH) bootstrap domains so browsers and WebViews downgrade to inspectable UDP port 53:
       `use-application-dns.net`, `dns.google`, `cloudflare-dns.com`, `dns.quad9.net`, `doh.opendns.com`.
     - Stream all active filter lists into a dynamically resizing primitive `LongArray` (initial capacity `262_144`). **Never store raw `String` objects in a `HashSet` or `ArrayList`.**
     - Sort the primitive array in-place (`Arrays.sort(hashes, 0, size)`), deduplicate adjacent identical hashes in-place, and write unique 8-byte big-endian values sequentially to `outputFile.tmp` via `FileChannel` and a `64 KB` direct `ByteBuffer`.
     - Atomically rename `outputFile.tmp` to `outputFile` (`rules.bin`) and return `uniqueCount`.

### 1.2 Create/Update `OffHeapRuleEngine.kt`
* **Path:** `data-store/src/main/java/com/unblocker/data/cache/OffHeapRuleEngine.kt`
* **Implementation Requirements:**
  1. Hold an `AtomicReference<MappedByteBuffer?>` pointing to `rules.bin` mapped in `FileChannel.MapMode.READ_ONLY` with `ByteOrder.BIG_ENDIAN`.
  2. Implement `reloadFromFile(indexFile: File)` to memory-map the updated binary file and hot-swap the `AtomicReference` with zero lock contention.
  3. Implement `isHashBlocked(domainHash: Long): Boolean` using an allocation-free binary search (`low`, `high`, `mid = (low + high) ushr 1`, `buffer.getLong(mid * 8)`) in $O(\log_2 N)$ time (~20 comparisons for 686,200 rules).
  4. Implement `isDomainBlocked(domain: CharSequence): Boolean`:
     - Walk labels from left to right by finding each `.` index (`startIdx`).
     - Compute `RuleCompiler.fnv1a64(domain, startIdx, domain.length)` and check `isHashBlocked(hash)`.
     - Ensure checking `a.b.doubleclick.net` tests `a.b.doubleclick.net`, `b.doubleclick.net`, and `doubleclick.net` with **zero `String` allocations**.

### 1.3 Phase 1 Verification Gate
```bash
./gradlew testDebugUnitTest
```
* **Pass Criteria:** Write and pass a unit test (`RuleCompilerTest.kt`) verifying that `||googleads.g.doubleclick.net^$third-party`, `0.0.0.0 pagead2.googlesyndication.com`, and subdomain `sub.pagead2.googlesyndication.com` compile into `rules.bin` and return `true` via `OffHeapRuleEngine.isDomainBlocked()`, while `google.com` returns `false`.

---

## Phase 2: Dual-Stack Split-Tunnel VPN & Checksum-Verified `NXDOMAIN` Engine

**Objective:** Fix the root causes why ads slip through on Wi-Fi, 5G, and LTE networks while keeping all non-DNS traffic on the hardware interface at full speed.

### 2.1 Refactor `DnsPacketEngine.kt`
* **Path:** `vpn-engine/src/main/java/com/unblocker/vpn/parser/DnsPacketEngine.kt`
* **Implementation Requirements:**
1. **Dual-Stack IPv4 & IPv6 Parsing (`parseOutgoingDnsPacket`):**
   - Inspect IP version `(packet[0].toInt() ushr 4) and 0x0F`.
   - Support **IPv4** (`version == 4`, `ipHeaderLen = (packet[0].toInt() and 0x0F) * 4`, `protocol = packet[9]`) and **IPv6** (`version == 6`, `ipHeaderLen = 40`, `protocol = packet[6]`).
   - Verify `protocol == 17` (UDP) and destination port `== 53`.
   - Verify DNS `QR` bit is `0` (Query) and `QDCOUNT > 0`.
   - Decode DNS `QNAME` labels safely with bounds checking (`pos + labelLen <= length`), rejecting pointer loops (`(labelLen and 0xC0) != 0`).

2. **Checksum-Valid `NXDOMAIN` Synthesis (`synthesizeNxDomainResponse`):**
   - Copy the request packet of `totalLength`.
   - Swap Source and Destination IP addresses (`bytes 12..15 <-> 16..19` for IPv4; `bytes 8..23 <-> 24..39` for IPv6).
   - Swap Source and Destination UDP ports (`udpOffset + 0..1 <-> udpOffset + 2..3`).
   - Set DNS response flags at `dnsOffset + 2..3` to `0x8183` (`QR=1`, `RD=1`, `RA=1`, `RCODE=3 NXDOMAIN`) and zero out `ANCOUNT`, `NSCOUNT`, and `ARCOUNT` (`dnsOffset + 6..11`).
   - **IPv4 Checksum:** Zero bytes `10..11`, compute the 16-bit one's complement sum over `0 until ipHeaderLen`, and write the big-endian checksum into bytes `10..11`.
   - **IPv6 UDP Checksum:** Compute the mandatory RFC 2460 16-bit one's complement UDP checksum over the IPv6 pseudo-header (Source IPv6, Dest IPv6, UDP Length, Next Header `17`) + UDP header + DNS payload, and write it to `udpOffset + 6..7`.

3. **Upstream Response Rewriter (`buildForwardedDnsResponse`):**
   - Construct a valid IP + UDP packet wrapping the upstream DNS reply, swapping original source/destination IPs and ports, updating IP total length and UDP length fields, and recomputing IPv4 header / IPv6 UDP checksums.

### 2.2 Refactor `UnblockerVpnService.kt`
* **Path:** `vpn-engine/src/main/java/com/unblocker/vpn/service/UnblockerVpnService.kt`
* **Implementation Requirements:**
1. **Dual-Stack Virtual Sinkhole & Split-Tunnel Routing:**
   ```kotlin
   val builder = Builder()
       .setSession("Un-Blocker DNS Shield")
       .setMtu(1500)
       .addAddress("10.111.222.1", 24)
       .addAddress("fd00:1:fd00:1:fd00:1:fd00:1", 128)
       .addDnsServer("10.111.222.2")
       .addDnsServer("fd00:1:fd00:1:fd00:1:fd00:2")
   ```

2. **Route ONLY DNS Server IPs into `tun0` (Do NOT route `0.0.0.0/0`):**
   - Add IPv4 `/32` routes for `10.111.222.2`, `8.8.8.8`, `8.8.4.4`, `1.1.1.1`, `1.0.0.1`, `9.9.9.9`.
   - Add IPv6 `/128` routes for `fd00:1:fd00:1:fd00:1:fd00:2`, `2001:4860:4860::8888`, `2001:4860:4860::8844`, `2606:4700:4700::1111`, `2606:4700:4700::1001`.

3. **Automatic Physical Network Tracking:**
   - Call `builder.setUnderlyingNetworks(null)` so Android automatically binds the VPN across Wi-Fi, 5G, and LTE switches.
   - Call `builder.addDisallowedApplication(packageName)` to prevent infinite loopback.

4. **Protected Upstream Forwarding:**
   - For allowed domains, forward the raw DNS payload (`query.dnsOffset` to `query.totalLength`) over a `DatagramSocket` to `1.1.1.1:53` (with fallback to `8.8.8.8:53`).
   - Always call `protect(socket)` before `socket.send(...)`.

### 2.3 Phase 2 Verification Gate
```bash
./gradlew :vpn-engine:assembleDebug :vpn-engine:testDebugUnitTest
```
* **Pass Criteria:** Unit test verifies IPv4 and IPv6 DNS query parsing, valid IPv4 header checksum (`0xFFFF` verification), and `NXDOMAIN` (`0x8183`) flag synthesis.

---

## Phase 3: Batched Database Logging & Deferred On-Device Learning

**Objective:** Prevent SQLite lock contention and UI frame drops during heavy network bursts while maintaining the 4:00 AM on-device learning pipeline.

### 3.1 Buffered Log Channel in `LogRepository.kt`
* **Path:** `data-store/src/main/java/com/unblocker/data/repository/LogRepository.kt`
* **Implementation Requirements:**
1. Replace per-packet `dnsLogDao.insertLog(...)` calls with a non-blocking `Channel<DnsLogEntity>(capacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)`.
2. Expose `fun enqueueDnsLog(domain: String, isBlocked: Boolean)` using `logChannel.trySend(...)` so the VPN thread never suspends or blocks on disk I/O.
3. Run a single background consumer coroutine on `Dispatchers.IO` that collects up to `100` logs or waits `2000ms` before executing a single `@Insert` batch transaction `dnsLogDao.insertLogsBatch(batch)`.

### 3.2 Update `BatchDomainAnalysisWorker.kt` & UI Warning Banner
* Ensure `BatchDomainAnalysisWorker` appends newly flagged ML rules (`score >= 0.88f`) to the database, triggers `RuleCompiler` to rebuild `rules.bin`, and calls `offHeapRuleEngine.reloadFromFile(rulesFile)`.
* Remove `android:largeHeap="true"` from `app/src/main/AndroidManifest.xml` (or keep it only as a fallback while removing the high-memory bottleneck warning once off-heap indexing is active).

### 3.3 Phase 3 Verification Gate
```bash
./gradlew assembleDebug testDebugUnitTest
```

---

## Phase 4: R8 Keep Rules, Lint Stabilization & GitHub Actions CI/CD Repair

**Objective:** Fix all failures in `Android checks`, `CodeQL Advanced`, and `Publish release APK` (`Test, lint and R8 smoke build` and `Sign, verify and publish`).

### 4.1 Fix R8 Minification (`app/proguard-rules.pro`)
Append these explicit keep rules so R8 smoke and release builds do not strip Room entities, JNI/TFLite bindings, or coroutines:
```proguard
# Keep Room Database & Entities
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Keep Kotlinx Serialization / Models
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers class com.unblocker.** {
    <fields>;
    <init>(...);
}

# Keep TensorFlow Lite (if present)
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
```

### 4.2 Stabilize Lint & Bump Version in `app/build.gradle.kts`
1. Update `defaultConfig`:
   - Increment `versionCode` by `1` (`versionCode = 27`).
   - Set `versionName = "2.2.0"`.
2. Configure `lint` inside the `android { ... }` block so non-fatal warnings do not break CI:
   ```kotlin
   lint {
       abortOnError = false
       checkReleaseBuilds = false
       disable += setOf("ProtectedPermissions", "MissingTranslation", "ExpiredTargetSdkVersion")
   }
   ```

### 4.3 Fix `.github/workflows/` Pipelines
1. **In all workflow YAML files (`android-checks.yml`, `codeql.yml`, `release.yml`):**
   - Use `actions/checkout@v4` and `actions/setup-java@v4` with `distribution: 'temurin'` and `java-version: '17'`.
   - Add `run: chmod +x gradlew` immediately before any `./gradlew` step.
2. **In `codeql.yml` (if present):**
   - Replace `github/codeql-action/autobuild` with explicit step:
     `run: ./gradlew assembleDebug --no-daemon`
3. **In `.github/workflows/release.yml` (`Publish release APK`):**
   - Add top-level permissions so the runner is allowed to create releases and upload assets:
     ```yaml
     permissions:
       contents: write
     ```
   - Ensure the build, sign, verify, and publish steps run in a unified release job (or explicitly pass the unsigned/signed APK via `actions/upload-artifact@v4` and `actions/download-artifact@v4` between jobs so the file exists during `Sign, verify and publish`).
   - Support both signed release builds (when `KEYSTORE_BASE64` secret is configured) and fallback signed debug/release builds so the step never crashes.

### 4.4 Phase 4 Verification Gate
```bash
./gradlew testDebugUnitTest lintDebug assembleRelease
```
* **Pass Criteria:** Unit tests pass, lint completes without aborting, and R8 `assembleRelease` produces the release APK without missing-class errors.

---

## Phase 5: Repository Hygiene, Commit, Tag & Push Release `v2.2.0`

**Objective:** Commit all verified changes, merge into `main`, tag `v2.2.0`, and push to GitHub to trigger the automated APK release.

### 5.1 Pre-Push Security & Hygiene Check
1. Verify `.gitignore` excludes `*.jks`, `*.keystore`, `local.properties`, `.gradle/`, and `build/`.
2. Run `git status` to ensure no temporary test binary files (`rules.bin`) or signing keys are staged.

### 5.2 Git Commit, Tag & Push Commands
Execute the following commands sequentially once Phase 1–4 verification gates pass:
```bash
git checkout main
git pull origin main

git add .
git commit -m "feat(engine): off-heap 64-bit rule index, dual-stack DNS sinkhole, and CI release fix (v2.2.0)"

git tag -a v2.2.0 -m "Release v2.2.0: Off-heap memory engine (~5.5MB RAM for 686k rules), IPv4/IPv6 NXDOMAIN ad-blocking, and CI/CD pipeline fixes"
git push origin main
git push origin v2.2.0
```
