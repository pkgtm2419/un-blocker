# Behavioral contracts — current policy precedence

Source: `app/src/main/java/com/unblocker/app/domain/usecase/DecideBlockingUseCase.kt` at `8934519`; tests: `app/src/test/java/com/unblocker/app/domain/DecideBlockingUseCaseTest.kt`. Do **not** reinterpret names as feature promises.

## Current decision chain (short-circuit order)

| Order | Gate | Result |
| --- | --- | --- |
| Exception around evaluation | unexpected error | `ALLOW` with `ENGINE_ERROR` (fail open) |
| 1 | invalid normalized DNS domain | `ALLOW(INVALID)` |
| 2 | user protection pause active | `ALLOW(PAUSED)` |
| 3 | user allow rule (exact or site ancestor) | `ALLOW(USER_ALLOW)`; sync learner feedback |
| 4 | user block rule | `BLOCK(USER_BLOCK)`; sync feedback |
| 5 | `APPROVED_ONLY` | `ALLOW(NO_MATCH)` for anything not covered by steps 3–4 |
| 6 | protected infrastructure / NeverBlock policy | `ALLOW(PROTECTED)` |
| 6b | `MobileUnbreakList.isEssentialDomain` | `ALLOW(ALLOWED)` |
| 7 | ad filtering enabled: BASIC reward/shop exception | `ALLOW(ALLOWED)` |
| 7b | admitted static/extension allow | `ALLOW(ALLOWED)` |
| 7c | admitted static/extension block / seed | `BLOCK(STATIC_RULE)` |
| 8 | adaptive evidence crosses threshold | `BLOCK(LEARNED or engine-specific reason)` |
| 9 | optional adult-content detector match | `BLOCK` with adult reason |
| 10 | no match | `ALLOW(NO_MATCH)` |

**Important edge facts:** user block is evaluated **before** NeverBlock and protected checks; changes to that ordering are a product/security decision. `APPROVED_ONLY` currently allows unmatched hosts, *including hosts that otherwise might match adult/extension policy*, because it returns before later stages. `BASIC` relaxes particular reward/shopping domains; inspect actual functions before describing broader tier differences. `SMART` is the normal evaluation path. If filtering is disabled, adult filtering may still be enabled and evaluated later.

## Invariants for future edits

- Preserve precedence of user intent, explicit safety policy and static/adaptive classifiers until requirements and tests explicitly authorize an alteration.
- Reject malformed rule hosts (PSL/single-label/IP/protected-ancestor concerns) during list admission; never equate syntactically valid DNS with block-permitted policy.
- Preserve blocked/allowed response wire correctness; unrelated traffic must remain outside DNS-only tunnel routing.
- A false-positive fix should be narrowly scoped and supported by regression fixtures; no blanket allow of a registrable domain without impact review.
- Never rely on chronological install age as evidence to autonomously block a domain.
- Debug policy decisions with synthetic domains; avoid logs containing real captured names.

## Minimum regression cases

Allow beats block; exact versus suffix/whole-site; paused; error/invalid; protected infrastructure; user-block/protected conflict; `APPROVED_ONLY` unmatched allow; `BASIC` compatibility exceptions; adult toggle independent of ad toggle; extension allow priority; learned evidence expiry; memory reset; restart migration. See [test matrix](TEST_MATRIX.md).
