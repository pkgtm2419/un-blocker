#!/usr/bin/env python3
"""Validate installed documentation and plan structure without historical counts."""
from pathlib import Path
import sys
from scripts.docs_contract import check


def validate(root: Path) -> list[str]:
    root = root.resolve()
    issues = check(root)
    specs = sorted((root / 'docs/superpowers/specs').glob('*.md'))
    plans = sorted((root / 'docs/superpowers/plans').glob('*.md'))
    if not specs:
        issues.append('no design specifications installed')
    if not plans:
        issues.append('no implementation plans installed')
    for path in plans:
        content = path.read_text(encoding='utf-8')
        if not content.startswith('# ') or 'implementation plan' not in content.splitlines()[0].lower():
            issues.append(f'{path.relative_to(root)}: missing implementation plan title')
        for field in ('Goal', 'Architecture', 'Tech Stack', 'Spec'):
            if f'**{field}:**' not in content:
                issues.append(f'{path.relative_to(root)}: missing {field}')
        if '### Task ' not in content or not any(token in content for token in ('- [ ]', '- [x]')):
            issues.append(f'{path.relative_to(root)}: missing tasks or checkboxes')
    return issues


def main() -> int:
    issues = validate(Path(__file__).resolve().parent)
    if issues:
        for issue in issues:
            print(f'ERROR: {issue}', file=sys.stderr)
        return 1
    print('VALID: installed plan structure and maintained documentation links')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
