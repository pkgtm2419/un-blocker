#!/usr/bin/env python3
"""Check agent Markdown contracts, mandatory pages and local Markdown links."""
from __future__ import annotations
import argparse
import re
import sys
from pathlib import Path
from urllib.parse import unquote

REQUIRED = [
    'AGENTS.md', 'PRODUCT.md', 'DESIGN.md', 'SECURITY.md', 'INSTALL_DOC_PACK.md',
    'docs/README.md', 'docs/ARCHITECTURE.md', 'docs/FEATURE_MATRIX.md',
    'docs/BEHAVIOR_CONTRACTS.md', 'docs/PROJECT_MODULES.md',
    'docs/PRIVACY_DATA_FLOW.md', 'docs/SECURITY_REVIEW.md',
    'docs/DOCUMENTATION_MAINTENANCE.md',
    'docs/modules/APP.md', 'docs/modules/COMMON.md',
    'docs/modules/DATA_STORE.md', 'docs/modules/VPN_ENGINE.md',
    'docs/modules/ML_ENGINE.md', 'docs/features/README.md',
]
LINK = re.compile(r'(?<!!)\[[^\]]+\]\(([^)]+)\)')

def markdown_files(root: Path) -> list[Path]:
    """Validate maintained docs, not imported inputs, caches or build reports."""
    files = list(root.glob('*.md'))
    for folder in ('docs', '.agents', '.github', '.gsd'):
        files.extend((root/folder).rglob('*.md'))
    return sorted(set(files))

def prose(content: str) -> str:
    """Exclude fenced examples from link checks while retaining real prose."""
    lines = []
    fence = None
    for line in content.splitlines():
        match = re.match(r'^\s{0,3}(`{3,}|~{3,})', line)
        if match:
            marker = match.group(1)
            if fence is None:
                fence = marker
            elif marker[0] == fence[0] and len(marker) >= len(fence):
                fence = None
            continue
        if fence is None:
            lines.append(line)
    return '\n'.join(lines)

def frontmatter(path: Path) -> tuple[dict[str,str],str]:
    raw=path.read_text(encoding='utf-8')
    m=re.match(r'\A---\s*\n(.*?)\n---(?:\n|\Z)',raw,re.S)
    if not m: return {},'missing YAML frontmatter'
    kv={}
    for ln in m.group(1).splitlines():
        if not ln.strip() or ln.lstrip().startswith('#'): continue
        match=re.match(r'^([a-zA-Z_-]+):\s*(.*?)\s*$',ln)
        if match: kv[match.group(1)]=match.group(2).strip().strip("\"'")
    return kv,''

def check(root: Path) -> list[str]:
    root = root.resolve()
    errors=[]
    for rel in REQUIRED:
        if not (root/rel).is_file(): errors.append('required file missing: '+rel)
    skill_paths=sorted((root/'.agents/skills').glob('*/SKILL.md'))
    rule_paths=sorted((root/'.agents/rules').glob('*.md'))
    if not skill_paths: errors.append('no Antigravity skills in .agents/skills/*/SKILL.md')
    if not rule_paths: errors.append('no scoped Antigravity rules in .agents/rules/*.md')
    for f in skill_paths:
        meta,issue=frontmatter(f)
        if issue: errors.append(str(f.relative_to(root))+': '+issue);continue
        if meta.get('name')!=f.parent.name:errors.append(str(f.relative_to(root))+': frontmatter name must equal directory')
        if not re.fullmatch(r'[a-z0-9]+(?:-[a-z0-9]+)*',meta.get('name','')):
            errors.append(str(f.relative_to(root))+': invalid skill name')
        if not meta.get('description','').startswith('Use when '):
            errors.append(str(f.relative_to(root))+': skill description must start "Use when "')
        if len(meta.get('description',''))>500:
            errors.append(str(f.relative_to(root))+': skill description too long')
    for f in rule_paths:
        meta,issue=frontmatter(f)
        if issue:errors.append(str(f.relative_to(root))+': '+issue);continue
        t=meta.get('trigger','')
        if t not in ('always_on','model_decision','glob','manual'):
            errors.append(str(f.relative_to(root))+': trigger must be always_on/model_decision/glob/manual')
        if t=='model_decision' and not meta.get('description'):
            errors.append(str(f.relative_to(root))+': model_decision requires description')
        if t=='glob' and not meta.get('globs'):
            errors.append(str(f.relative_to(root))+': glob requires globs')
    for f in markdown_files(root):
        if '/docs/generated/' in f.as_posix() and f.name!='README.md':continue
        try: content=f.read_text(encoding='utf-8')
        except UnicodeError:
            errors.append(str(f.relative_to(root))+': not valid UTF-8');continue
        if '\x00' in content:errors.append(str(f.relative_to(root))+': NUL character')
        for dest in LINK.findall(prose(content)):
            dest=dest.strip().strip('<>')
            if not dest or dest.startswith(('http:','https:','mailto:','#','app:','sandbox:')):continue
            rel=unquote(dest.split('#',1)[0].split('?',1)[0])
            if not rel:continue
            p=(f.parent/rel).resolve()
            if not p.is_relative_to(root):
                errors.append(str(f.relative_to(root))+': link escapes repository: '+rel)
            elif p.is_file():continue
            else:errors.append(str(f.relative_to(root))+': missing local link target: '+dest)
    return errors

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1])
    root=parser.parse_args(argv).root.resolve()
    issues=check(root)
    if issues:
        for issue in issues:print('ERROR:',issue,file=sys.stderr)
        print(f'docs_contract: {len(issues)} issue(s)',file=sys.stderr)
        return 1
    print(f'docs_contract: OK — {len(markdown_files(root))} maintained Markdown files; {len(list((root/".agents/skills").glob("*/SKILL.md")))} skills; {len(list((root/".agents/rules").glob("*.md")))} rules validated')
    return 0

if __name__=='__main__':
    raise SystemExit(main())
