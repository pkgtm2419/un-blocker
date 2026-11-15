# Kotlin senior code style — design specification

## Intent

Apply the user's repository-wide Kotlin/Android standards without removing functionality or changing public APIs, wire data, persisted names, rule precedence, Android component identity or build/signing behavior.

## Approach

Use a pinned Kotlin-aware formatter for Kotlin/Gradle, semantic per-file review for names/KDoc/quality, and XML-aware checks. A project skill provides judgment guidance; root AGENTS.md requires loading it on every project prompt and an always-on Antigravity rule reinforces discovery. EditorConfig and an executable style check enforce mechanical formatting. Normal skill discovery alone does not guarantee every-prompt activation.

Public names/parameter names are compatibility constraints, including named arguments and overrides. Compose Unit UI functions retain PascalCase as required by Compose conventions. Constants follow uppercase naming where private changes are safe. Protocol numeric values become named constants only with unchanged values/types/evaluation. KDoc describes actual purpose, parameter units, return contracts and real exceptions; no invented guarantees.

## Scope

All 233 checked-in Kotlin files (production, JVM and instrumentation), seven Gradle Kotlin scripts and 13 XML resources/manifests. Exclude generated build products, binary/data assets, vendored content and local SDK configuration. Audit unused code semantically before removing it; Android/Room/Hilt/reflection entrypoints are not dead code merely because text search finds no callers.

## Verification

Set up portable JDK 17 and Android SDK per user approval. Capture baseline tests before source rewrites. Run pinned formatting, syntax/API checks, Gradle JVM tests, lint and debug assembly. Device execution requires an available emulator; report unavailable verification honestly. Keep full changed code and per-file summaries in a local review artifact rather than duplicating tens of thousands of lines into chat.

## Alternatives

A bulk text-regex rewrite cannot safely interpret Kotlin strings/comments, APIs or constructor properties. A manual-only formatter is inconsistent at this scale. Use Kotlin-aware formatting plus code-grounded edits, retaining focused exceptions for source/API compatibility.
