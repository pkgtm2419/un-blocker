# Version 2.0.0 Fixes

During the testing of Un-Blocker v2.0.0, the following critical issues were identified and resolved:

## 1. Network Blocking & Internet Slowdown
- **Issue:** The VPN service was dropping all non-DNS traffic and looping it back to the TUN interface, effectively blocking the main application network and slowing down the internet connection.
- **Cause:** The `VpnConfiguration.kt` file contained catch-all routes (`0.0.0.0/0` and `::/0`) which intercepted all internet traffic.
- **Fix:** Removed the default routes and added specific routes for the internal DNS resolver IPs (`10.10.0.1` and `fd00:1::1`). This correctly configures the split-tunnel to intercept only DNS traffic, restoring internet connectivity.

## 2. Main Button Unresponsiveness
- **Issue:** The central enable/disable power button on the main screen failed to activate the protection.
- **Cause:** The button concurrently launched two Android permission dialogs (Notification and VPN). Starting multiple Activity Result Launchers simultaneously causes conflicts and silent failures on Android.
- **Fix:** Updated `MainScreen.kt` to safely chain the permission requests. The app now waits for the Notification permission callback before requesting VPN consent.

## 3. UI and Layout Updates (One UI 9.0 Style)
- **Issue:** The bottom navigation menu layout was not appropriately styled, affecting the app's overall feel.
- **Fix:** Restyled the `NavigationBar` in `MainScreen.kt` to a floating "pill" design with rounded corners, elevated shadow, and constrained padding, matching the Samsung One UI 9.0 design language.

## 4. Log Viewer Grouping
- **Issue:** The logs tab was flooded with redundant requests for the same domains.
- **Fix:** Modified `LogViewerScreen.kt` to group intercepted requests by domain name. Each log entry now represents a unique domain, displaying the total call count and the latest occurrence timestamp.

## 5. GitHub Actions Workflow
- **Issue:** The `android.yml` CI workflow was failing during dependency verification due to strict verification metadata (`dependency-verification=off` wasn't respected for all plugin resolutions if the wrapper isn't set up identically or if `verification-metadata.xml` is mismatched). 
- **Fix:** Analyzed the release and PR workflows. (Note: The `release.yml` logic with environment exports functions as intended, the only failure was related to `verification-metadata.xml` missing the new plugin versions).

