#!/usr/bin/env python3
"""Deterministic, stdlib-only inventories for the Un-Blocker documentation pack.

Usage from repository root: python3 scripts/docs_sync.py --write | --check
Only files in docs/generated/ owned by this script are ever written.
"""
from __future__ import annotations
import argparse
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

OUTPUTS = ('MODULE_INDEX.md', 'BUILD_FACTS.md', 'EXTENSION_CATALOG.md',
           'SOURCE_SYMBOLS.md', 'TEST_CATALOG.md')
HEADER = '<!-- GENERATED: scripts/docs_sync.py. DO NOT EDIT BY HAND. -->\n'

def read(path: Path) -> str:
    return path.read_text(encoding='utf-8') if path.is_file() else ''

def code(value: object) -> str:
    return '`' + str(value).replace('`', "'").replace('|', '/') + '`'

def mdcell(value: object) -> str:
    return str(value).replace('|', r'\|').replace('\n', ' ').replace('\r', ' ').strip()

def modules(root: Path) -> list[str]:
    gradle = read(root / 'settings.gradle.kts')
    result = re.findall(r'include\s*\(\s*"(:[A-Za-z0-9_-]+)"\s*\)', gradle)
    # `include(":app", ":common")` also supported.
    result += [m for line in re.findall(r'include\s*\(([^)]*)\)', gradle)
               for m in re.findall(r'"(:[A-Za-z0-9_-]+)"', line)]
    return sorted(set(result))

def generated(root: Path) -> dict[str, str]:
    ms = modules(root)
    if not ms or not (root / 'app' / 'build.gradle.kts').is_file():
        raise ValueError('No Gradle app checkout found. Run from un-blocker repository root (containing settings.gradle.kts and app/build.gradle.kts).')
    doc: dict[str, str] = {}
    mod_rows=[]
    all_symbols=[]
    all_tests=[]
    for name in ms:
        folder=root/name[1:]
        main_files=sorted((folder/'src/main').rglob('*.kt')) if (folder/'src/main').exists() else []
        unit_files=sorted((folder/'src/test').rglob('*Test.kt')) if (folder/'src/test').exists() else []
        inst_files=sorted((folder/'src/androidTest').rglob('*Test.kt')) if (folder/'src/androidTest').exists() else []
        mod_rows.append(f'| {code(name)} | {len(main_files)} | {len(unit_files)} | {len(inst_files)} | {code(name[1:]+"/build.gradle.kts")} |')
        for src in main_files:
            rel=src.relative_to(root).as_posix()
            text=read(src)
            declarations=re.findall(r'(?m)^(?:(?:public|internal|private|protected|open|abstract)\s+)*(?:(?:data|sealed|enum|annotation|value)\s+)*(class|interface|object)\s+([A-Za-z_][A-Za-z_0-9]*)', text)
            for kind,sym in declarations:
                all_symbols.append(f'| {code(rel)} | {mdcell(kind)} | {code(sym)} |')
        for src in unit_files+inst_files:
            rel=src.relative_to(root).as_posix()
            kind='instrumentation' if 'androidTest' in src.parts else 'unit'
            all_tests.append(f'| {code(rel)} | {kind} |')
    doc['MODULE_INDEX.md'] = ('# Gradle module inventory\n\n'
      'This is a syntactic inventory, **not runtime reachability analysis**.\n\n'
      '| Module | Production Kotlin files | Unit `*Test.kt` | Instrumentation `*Test.kt` | Build file |\n'
      '| --- | ---: | ---: | ---: | --- |\n'+'\n'.join(mod_rows)+'\n')
    gradle=read(root/'app/build.gradle.kts')
    props=('applicationId','minSdk','targetSdk','compileSdk','versionCode','versionName')
    facts=[]
    for p in props:
        m=re.search(r'(?m)^\s*'+re.escape(p)+r'\s*=\s*("[^"]+"|\d+)',gradle)
        value = m.group(1).strip('"') if m else 'not found'
        facts.append(f'| {code(p)} | {code(value)} |')
    catalog=read(root/'gradle/libs.versions.toml')
    ver=[]
    section=''
    for line in catalog.splitlines():
        if line.strip().startswith('['):
            section=line.strip()
        elif section=='[versions]' and '=' in line and not line.lstrip().startswith('#'):
            key,val=line.split('=',1)
            ver.append(f'| {code(key.strip())} | {code(val.strip())} |')
    doc['BUILD_FACTS.md'] = ('# Source build facts\n\nRead directly from **checked-out** Gradle, not README version claims.\n\n'
      '| App property | Value |\n| --- | --- |\n'+'\n'.join(facts)+'\n\n'
      '## Version catalog `[versions]`\n\n| Key | Value |\n| --- | --- |\n'+'\n'.join(ver)+'\n')
    manifest=root/'app/src/main/assets/extensions/extensions_manifest.json'
    try:
        data=json.loads(read(manifest))
        if not isinstance(data, dict): raise ValueError('manifest must be an object')
        exts=data.get('extensions',[])
        if not isinstance(exts,list): raise ValueError('extensions is not an array')
        if any(not isinstance(entry, dict) for entry in exts):
            raise ValueError('Extension item must be object')
    except (json.JSONDecodeError,ValueError) as e:
        raise ValueError(f'Invalid extension manifest {manifest}: {e}') from e
    items=[]
    for entry in sorted(exts, key=lambda x:str(x.get('id',''))):
        if not isinstance(entry,dict): raise ValueError('Extension item must be object')
        items.append('| '+' | '.join(mdcell(entry.get(k,'')) for k in
          ('id','name','category','enabled','custom','url'))+' |')
    doc['EXTENSION_CATALOG.md'] = (f'# Bundled extension catalog\n\nManifest version: {code(data.get("version","unknown"))}; '
      f'record count: **{len(exts)}**. Settings and actual active rules can differ from this catalog.\n\n'
      '| ID | Name | Category | Enabled by manifest | Custom | Source URL |\n'
      '| --- | --- | --- | --- | --- | --- |\n'+'\n'.join(items)+'\n')
    doc['SOURCE_SYMBOLS.md'] = ('# Top-level Kotlin declarations\n\nOnly syntactically matched top-level class/interface/object declarations '
      '(not semantic call graphs). Nested and multiline declarations may be omitted.\n\n'
      '| Source path | Kind | Declaration |\n| --- | --- | --- |\n'+'\n'.join(sorted(all_symbols))+'\n')
    doc['TEST_CATALOG.md'] = ('# Kotlin test file inventory\n\nFiles matching `*Test.kt`; presence does not indicate tests passed.\n\n'
      '| Source file | Test scope |\n| --- | --- |\n'+'\n'.join(sorted(all_tests))+'\n')
    return {p:HEADER+'\n'+s for p,s in doc.items()}

def run(root: Path, mode: str) -> int:
    content=generated(root)
    folder=root/'docs/generated'
    if mode=='write':
        folder.mkdir(parents=True,exist_ok=True)
        for p,s in content.items():
            out=folder/p
            if read(out)!=s:
                out.write_text(s,encoding='utf-8')
                print('updated:',out.relative_to(root).as_posix())
        print('generated inventory: up to date')
        return 0
    stale=[p for p,s in content.items() if read(folder/p)!=s]
    if stale:
        print('STALE generated documentation: '+', '.join(stale),file=sys.stderr)
        print('Fix: python3 scripts/docs_sync.py --write; inspect and commit the generated diff.',file=sys.stderr)
        return 1
    print('generated inventory matches current source')
    return 0

def main(argv: list[str]|None=None) -> int:
    parser=argparse.ArgumentParser(description=__doc__)
    g=parser.add_mutually_exclusive_group(required=True)
    g.add_argument('--write',action='store_true')
    g.add_argument('--check',action='store_true')
    parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[1])
    ns=parser.parse_args(argv)
    try: return run(ns.root.resolve(),'write' if ns.write else 'check')
    except (ValueError,OSError) as e:
        print('docs_sync: '+str(e),file=sys.stderr)
        return 2

if __name__=='__main__':
    raise SystemExit(main())
