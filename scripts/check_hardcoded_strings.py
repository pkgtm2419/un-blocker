#!/usr/bin/env python3
"""Verify that Jetpack Compose UI sources in app/src/main contain no hardcoded UI strings."""

from __future__ import annotations

import argparse
from bisect import bisect_right
from dataclasses import dataclass
from pathlib import Path
import re
import sys

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_SCAN_ROOT = REPO_ROOT / "app" / "src" / "main" / "java"

# Matches UI text arguments across single or multi-line Compose calls, e.g.:
#   Text("...")
#   Text(\n "...")
#   text = "...", title = "...", subtitle = "...", placeholder = "...", contentDescription = "..."
# Note: `label = "..."` is omitted because Compose animation APIs (animate*AsState,
# rememberInfiniteTransition) use string `label` as a tooling/inspection tag rather than UI copy.
_UI_STRING_PATTERN = re.compile(
    r"""(?:\bText\s*\(\s*(?:text\s*=\s*)?|\b(?:text|title|subtitle|placeholder|contentDescription)\s*=\s*)"((?:\\.|[^"\\])*)" """.strip(),
    re.MULTILINE,
)

_LINE_COMMENT_PATTERN = re.compile(r"//[^\n]*")
_BLOCK_COMMENT_PATTERN = re.compile(r"/\*.*?\*/", re.DOTALL)

# Purely non-translatable symbols/tokens that are intentionally UI-agnostic.
_ALLOWLISTED_LITERALS: frozenset[str] = frozenset(
    {
        "",
        "#",
        "/",
        "-",
        "·",
        "•",
        ":",
        "...",
    },
)


@dataclass(frozen=True)
class HardcodedStringViolation:
    """Single hardcoded string occurrence reported by the checker."""

    path: Path
    line: int
    literal: str


def _is_non_translatable(literal: str) -> bool:
    """Return True for empty, punctuation-only, or pure interpolation placeholders."""
    if literal in _ALLOWLISTED_LITERALS:
        return True
    stripped = re.sub(r"\$\{[^}]+\}|\$[A-Za-z_][A-Za-z0-9_]*", "", literal).strip()
    if not stripped:
        return True
    return not any(ch.isalpha() for ch in stripped)


def _blank_comments(text: str) -> str:
    """Replace comments with spaces of matching newline counts to preserve line numbers."""

    def _mask(match: re.Match[str]) -> str:
        return "".join("\n" if ch == "\n" else " " for ch in match.group(0))

    without_block = _BLOCK_COMMENT_PATTERN.sub(_mask, text)
    return _LINE_COMMENT_PATTERN.sub(_mask, without_block)


def scan_file(path: Path) -> list[HardcodedStringViolation]:
    """Scan a single Kotlin source file for hardcoded Compose UI string literals."""
    raw_text = path.read_text(encoding="utf-8")
    cleaned = _blank_comments(raw_text)
    line_starts = [0]
    for idx, ch in enumerate(cleaned):
        if ch == "\n":
            line_starts.append(idx + 1)

    violations: list[HardcodedStringViolation] = []
    for match in _UI_STRING_PATTERN.finditer(cleaned):
        literal = match.group(1)
        if _is_non_translatable(literal):
            continue
        line_no = bisect_right(line_starts, match.start(1))
        violations.append(
            HardcodedStringViolation(
                path=path,
                line=line_no,
                literal=literal,
            ),
        )
    return violations


def scan_tree(scan_root: Path = DEFAULT_SCAN_ROOT) -> list[HardcodedStringViolation]:
    """Scan all Kotlin files beneath `scan_root` sorted deterministically by path and line."""
    violations: list[HardcodedStringViolation] = []
    if not scan_root.exists():
        return violations
    for kt_file in sorted(scan_root.rglob("*.kt")):
        violations.extend(scan_file(kt_file))
    return violations


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--root",
        type=Path,
        default=DEFAULT_SCAN_ROOT,
        help="Root directory of Kotlin sources to check (defaults to app/src/main/java).",
    )
    args = parser.parse_args(argv)
    violations = scan_tree(args.root)
    if not violations:
        print(f"OK: 0 hardcoded UI string violations under {args.root}")
        return 0

    for item in violations:
        try:
            display = item.path.relative_to(REPO_ROOT).as_posix()
        except ValueError:
            display = str(item.path)
        print(f"{display}:{item.line}: hardcoded UI string literal {item.literal!r}")
    print(f"FAIL: {len(violations)} hardcoded UI string violation(s) found.", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
