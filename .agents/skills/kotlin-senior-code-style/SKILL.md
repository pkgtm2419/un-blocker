---
name: kotlin-senior-code-style
description: Use when handling any prompt in the Un-Blocker workspace, especially reading, writing, refactoring or reviewing Kotlin, Android XML and Gradle Kotlin code.
---

# Kotlin Senior Code Style

Load this skill for every project prompt as required by root `AGENTS.md`. Apply the standards to code involved in the task; an unrelated question does not authorize unsolicited rewrites. Skill metadata permits implicit invocation, while `AGENTS.md` and the always-on workspace rule make loading mandatory for cooperating agents.

## Contract

| Area | Required practice |
| --- | --- |
| Format | Four spaces, no tabs, 120-character lines, official Kotlin spacing and ordered imports; remove unused imports. Use the repository's pinned formatter and EditorConfig. |
| Names | PascalCase classes/interfaces/objects, camelCase functions/properties/locals, UPPER_SNAKE_CASE constants. Use descriptive names; single letters only for loop indices. |
| Documentation | KDoc every source-declared public class, function and property. Explain purpose, parameter meaning/units, meaningful return values and actual exceptions. Use `@property` for public constructor properties and `@param` for other parameters. |
| Structure | One responsibility per class/function, short cohesive functions, early returns and shallow nesting. Keep UI, domain and data responsibilities separate; use existing injection boundaries. |
| Types | Prefer `val`, immutable exposed collections, explicit null handling, sealed outcomes and data classes where they express the domain. Use focused extension functions rather than utility dumping grounds. |
| Android | Respect lifecycle ownership, ViewModel/state hoisting and read-only StateFlow exposure. Keep blocking I/O off main and DNS hot paths. Use structured coroutines, propagate cancellation, and clean up resources. |
| Quality | Name protocol sizes, limits, units and thresholds instead of repeating magic literals. Preserve synchronization and buffer ownership. Handle failures with existing typed outcomes and documented policy. |

## Compatibility decisions

- Preserve public declarations **and parameter names**: Kotlin callers can use named arguments. Explain existing abbreviations in KDoc rather than silently renaming an API. Rename private/local identifiers only after checking all references, shadowing, labels and string templates.
- UI-emitting `@Composable` functions returning Unit retain PascalCase, following Compose conventions. Android overrides and externally required names retain their required spelling. Generated members are documented through their owning type rather than reimplemented for comments.
- Public constructor `val`/`var` parameters need meaningful `@property` documentation. A one-line “Gets X” comment is not adequate when units, ownership or failure semantics matter. Unit functions need no fictional return value; list only real exceptions and do not promise thread safety without evidence.
- Use `const val` only for values that are compile-time constants of supported types. Keep types, overflow, unsigned behavior, byte order and evaluation timing unchanged when extracting constants. Do not rename Room columns, serialized keys, preference keys, DNS formats, Android resources or manifest components.
- A long immutable URL, regex, SQL statement or fixture must not be split in a way that changes its value. Wrap surrounding syntax/comments first; document an unavoidable line-length exception explicitly instead of disabling style checks broadly.
- Remove code only after proving it unused. Hilt providers, Room declarations, Android lifecycle callbacks, reflection and public APIs can be used without an ordinary source call site.

## Workflow

Read the affected implementation/tests and project rules. Format with the pinned tool; review each changed file. Add accurate KDoc and concise comments explaining non-obvious invariants, removing redundant or outdated prose. Prefer narrow private refactors; do not alter filtering precedence, persistence, sockets, lifecycle or signing to satisfy style.

Run the style check, relevant JVM/Android tests, lint/build and documentation drift checks. Report exact outcomes and compatibility exceptions. Formatting does not prove behavior preservation; compare public signatures and verify executable paths.

## Common mistakes

Renaming a public parameter for aesthetics; camelCasing a Compose UI function; guessing `@throws`; replacing all numeric literals mechanically; changing a lock/coroutine scope during formatting; removing reflective entrypoints; claiming checks passed without executing them.

Official references: [Kotlin conventions](https://kotlinlang.org/docs/coding-conventions.html), [KDoc](https://kotlinlang.org/docs/kotlin-doc.html), [Compose API guidelines](https://github.com/androidx/androidx/blob/androidx-main/compose/docs/compose-api-guidelines.md).
