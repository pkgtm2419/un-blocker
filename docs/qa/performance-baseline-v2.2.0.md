# Un-Blocker v2.2.0 Performance Baseline

**Audit Date:** 2026-11-02  
**Commit SHA:** `6fd537e34773e3f799e3685f68a0fbef761eb71c`  
**Package:** `releaseTest/ub-blocker-2.2.0.apk`

## 1. Measured Artifact Baseline
- **ReleaseTest APK Size:** 19,797,964 bytes (~18.88 MiB)
- **Native Shared Libraries:** 8 files (`libtensorflowlite_jni.so`), 14,693,752 uncompressed bytes (74.22% of APK)
- **Native Segment Alignment:** 4 KiB (`0x1000`) for ARM64 TFLite (flags warning on 16 KiB Android 15 kernels)
- **Packaged TFLite Models:** 0 files (dormant inference)

## 2. Resource Targets for Optimization
- **Post-Optimization APK Size:** <= 10 MiB universal APK (achieved by excluding unused native TFLite binaries)
- **Matcher Allocation Hot Path:** Eliminated substring splits and prefix arrays in `RuleSet.match`
- **Background Wakeup Reduction:** Replaced charging+idle WorkManager constraint warnings
