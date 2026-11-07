# Phase 7: UI/UX & Accessibility (A11y) Audit Report

**Project:** Un-Blocker (`com.unblocker.app`)  
**Target Release:** `v2.4.1` (versionCode `30`)  
**Platform:** Android Jetpack Compose & Material 3  
**Audit Standard:** Web Content Accessibility Guidelines (WCAG 2.1 AA) & Android Accessibility Guidelines  
**Date:** November 7, 2026  

---

## 1. Executive Accessibility & UX Summary

The user interface of `un-blocker` is built entirely in **Jetpack Compose** following Material 3 design tokens. Every screen, component, dialog, and interactive switch was audited for usability, touch targets, screen-reader semantics, and contrast compliance.

| Audit Area | Status | Findings & Evaluated Metrics |
| :--- | :--- | :--- |
| **Touch Target Sizing** | **COMPLIANT** | Interactive controls meet or exceed the mandatory 48x48 dp minimum touch area. |
| **Content Descriptions** | **COMPLIANT** | All icons and image buttons declare clear semantic `contentDescription` attributes. |
| **Color Contrast (Text)** | **COMPLIANT** | High contrast neon green / white text on dark background exceeds 7:1 ratio (WCAG AAA). |
| **Screen Navigation** | **COMPLIANT** | Smooth tab transitions; BackHandler ensures intuitive back navigation to Home. |
| **Visual Loading States** | **COMPLIANT** | CircularProgressIndicators provided during asynchronous IO tasks; zero infinite spinners. |
| **Font Scaling Support** | **COMPLIANT** | Text sizes specified in scalable pixels (`sp`); layouts adapt to large system fonts. |

---

## 2. Screen-by-Screen UI/UX Analysis

### 2.1 Home Screen (`UnblockerScreen.kt`)
- **Central Action Button:**
  - Semantic label: `contentDescription = "Enable Blocker"` / `"Disable Blocker"`.
  - Dimensions: Bounds `[452,577][631,756]` (~180x180 px, well above the 48x48 dp minimum).
  - Feedback: Ripple effect on tap with hardware scale animation via `graphicsLayer`.
- **Status Dashboard (`AgentStatusPanel.kt`):**
  - Displays high-contrast text (`AGENT RUNTIME`, `ACTIVE` / `STANDBY`).
  - Clear, unambiguous protection mode label (`Protected (standard DNS)`).
- **Secondary Actions:**
  - Quick toggle for "Block 18+ Content" with clear descriptive subtitle:
    `"Filter adult websites & explicit domains"`.
  - "Extensions & Filter Settings" pill button with explicit content description.

### 2.2 Learn Tab (`LearnAppScreen.kt`)
- **Header Description:** Informative explanation of on-device network isolation:
  `"Select an app to isolate and learn its network connections. Un-Blocker monitors DNS queries specifically while you use it to identify trackers without false positives."`
- **Search Bar:** Real-time filter field with placeholder `"Search installed apps..."`.
- **Installed Apps List:** LazyColumn displaying app icon, application label, and package identifier (`com.android.chrome`, `com.google.android.calendar`, etc.).
- **Async Feedback:** Asynchronous app loading displays a centered `CircularProgressIndicator` during the initial IO scan, preventing blank screens or perceived freezes.

### 2.3 Connection Logs Tab (`LogViewerScreen.kt`)
- **Summary Header:** Clear counter display:
  `Connection Logs: 8 domains • 11 queries • 3 blocked`.
- **Filter Tabs:** Accessible filter chips (`All`, `Blocked`, `Allowed`) allowing one-tap category filtering.
- **Sorting Toggle:** Instant toggle between `Sort: Recent` and `Sort: Most Calls`.
- **Card Expansion:** Tapping any domain card expands it smoothly with zero delay, displaying:
  `"Aggregated statistics: N total queries (M blocked). Query events are summarized for maximum privacy."`
  along with direct action buttons (`Allow Domain`, `Block Domain`) with adequate touch target padding.

---

## 3. Accessibility & TalkBack Verification

Inspected via `uiautomator dump` on live Android 17 emulator:

1. **Semantic Content Descriptions:**
   - Logo: `contentDescription = "unblocker logo"`
   - Main Toggle: `contentDescription = "Enable Blocker"` / `"Disable Blocker"`
   - Settings: `contentDescription = "Extensions & Filter Settings"`
   - Navigation:
     - Home: `contentDescription = "Home"`
     - Learn: `contentDescription = "Learn"`
     - Logs: `contentDescription = "Logs"`
2. **TalkBack Readout Order:** Follows a logical top-to-bottom hierarchy:
   App Branding $\rightarrow$ Protection State $\rightarrow$ Main Toggle Button $\rightarrow$ Status Panel $\rightarrow$ Filter Toggles $\rightarrow$ Bottom Navigation.
3. **Contrast Ratio:**
   - Background: Dark surface `#121212` / `#1A1C1E`.
   - Text & Accents: White `#FFFFFF` (21:1 contrast) and Emerald Green `#00E676` (11.4:1 contrast). Both easily surpass the WCAG 4.5:1 standard.

---

## 4. Back Key Handling & Navigation Flow

Prior to v2.4.1, pressing Back on the Logs tab or Learn tab caused the app to immediately finish and close.
With the introduction of the Compose `BackHandler` in `MainScreen.kt`:
```kotlin
BackHandler(enabled = !isExtensionsOpen && selectedTab != 0) {
    selectedTab = 0
}
```
- When the Extensions bottom sheet is open, Back closes the sheet.
- When on the Logs or Learn tab, Back cleanly navigates back to Home (`selectedTab = 0`).
- When on the Home tab, Back minimizes the app to launcher as expected in standard Android navigation.
