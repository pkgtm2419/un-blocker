# 08 - Regression Safety, Feature Inventory & Compose UI Testing

## Goal
Establish a comprehensive regression safety verification framework ensuring 100% preservation of all existing core features (VPN start/stop, adult blocking, local domain overrides, query log buffering, CNAME flattening, Private DNS warnings) during and after UI/security refactoring.

## Current State
- The repository has 50+ unit test files covering DNS packets, caches, rule compiler, learning stores, and repository layers.
- Jetpack Compose UI screens (`UnblockerScreen`, `LogViewerScreen`, `MainScreen`) rely on manual verification and layout unit tests (`ResponsiveLayoutTest.kt`).
- No Compose screenshot or UI testing harness exists for regression verification of UI components.

## Steps
1. Catalog the exhaustive feature inventory across all UI screens and service layers (20 core capabilities documented in `implementation_plan.md`).
2. Add unit tests verifying:
   - State color transitions under all `ServiceStatus` variations.
   - Input normalization edge cases for domain overrides.
   - Agent status step calculations given VPN session states.
3. Establish pre-commit and post-commit verification gates (`testDebugUnitTest`, Python filter tests, release policy verification).
4. Run full feature verification matrix before pushing changes.

## Acceptance Criteria
- All 20 inventory features pass verification after redesign.
- Gradle unit tests remain 100% passing.
- Zero regressions in packet processing, rule evaluation, or database logging.

## Risks & Dependencies
- UI component changes must preserve existing ViewModel bindings, Coroutine scopes, and Hilt injections.
