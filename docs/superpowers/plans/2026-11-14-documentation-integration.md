# Documentation Integration Implementation Plan

**Goal:** Install the supplied documentation pack and reconcile it with the current project.

**Architecture:** Keep code-grounded contracts separate from generated inventories and labeled historical references. Preserve application behavior and release automation.

**Tech Stack:** Markdown, Python 3.11+ standard library, GitHub Actions, Antigravity workspace rules and skills.

**Spec:** [Supplied documentation design](../specs/2026-11-13-agent-docs-design.md) and the user's requested package structure.

## Global Constraints

- Preserve application source, Gradle, original README/privacy, `.gsd`, and release workflows.
- Retain saved inputs; do not apply proposed source patches or invent historical plans.
- Distinguish source observations, historical claims, and executed tests.

## Review Focus

Missing inventories fail CI; imported inputs do not break maintained-link checks; broken maintained links fail; malformed manifests produce actionable errors; tools parse with Python 3.11 grammar.

### Task 1: Install and reconcile

- [x] Verify archive CRC, path containment and existing-file conflicts.
- [x] Install all 98 entries on a local documentation branch.
- [x] Trace manifest, policy, persistence, resolver wiring and release workflow.
- [x] Organize supplied project Markdown as historical references; retain originals.

### Task 2: Repair validation integration

- [x] Run failing regression cases for missing outputs, imported links and malformed manifests.
- [x] Fix the documentation tools and rerun regression tests.
- [x] Replace obsolete fixed-count validation and start instructions.
- [x] Generate five inventories and check drift.

### Task 3: Verify and record

- [x] Run final maintained-link, frontmatter, plan and tooling checks.
- [x] Record executed results in the integration review.
- [ ] Verify skill/rule discovery in Antigravity's UI (not exercised locally).
- [ ] Execute new workflows on GitHub after maintainer review and push.

## Exit gates

Local documentation checks must pass. Android behavior and GitHub workflow execution remain explicitly unverified. Commit, push, PR and publication are outside this local installation.
