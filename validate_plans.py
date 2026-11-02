#!/usr/bin/env python3
"""Validate structure and link integrity of the Un-Blocker improvement plans."""
import os
import re
from pathlib import Path

root=Path(__file__).resolve().parent
specs=list((root/'docs/superpowers/specs').glob('*.md'))
plans=sorted((root/'docs/superpowers/plans').glob('*.md'))
audits=list((root/'docs/qa').glob('*audit*.md'))
qa_docs=list((root/'docs/qa').glob('*.md'))
assert len(specs)==1, f'Expected exactly one spec, got {len(specs)}'
assert len(plans)==6, f'Expected six plans, got {len(plans)}'
assert len(audits)==1, f'Expected one audit, got {len(audits)}'
for file in plans:
 txt=file.read_text(encoding='utf-8')
 assert txt.startswith('# ') and 'Implementation Plan' in txt.splitlines()[0]
 assert '**Goal:**' in txt and '**Architecture:**' in txt and '**Tech Stack:**' in txt and '**Spec:**' in txt
 assert '## Global Constraints' in txt and '## Review Focus' in txt and '## Exit gates' in txt
 assert txt.count('### Task ')>=3, f'Not enough independently verifiable tasks: {file.name}'
 assert txt.count('- [ ]')>=12, f'Not enough checkable actions: {file.name}'
 assert 'Write' in txt and 'Run' in txt and 'Commit' in txt
 assert 'TBD' not in txt and '[BRANCH_NAME' not in txt and '[GITHUB' not in txt
 print('VALID',file.name,'tasks',txt.count('### Task '),'checkable steps',txt.count('- [ ]'))
for f in [*specs,*plans,*qa_docs,root/'README.md']:
 text=f.read_text(encoding='utf-8')
 for url in re.findall(r'\]\(([^)]+\.md)\)',text):
  if url.startswith('https:') or url.startswith('http:'): continue
  path=(f.parent/url).resolve()
  assert path.is_file(), f'Broken relative link: {f.name} -> {url}'
print('VALID',len(specs),'spec,',len(plans),'plans,',len(audits),'audit; relative plan links resolved')
