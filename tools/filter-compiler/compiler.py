"""Deterministic, offline-only DNS rule compiler. No third-party Python dependencies."""
import argparse
import hashlib
import json
import pathlib
import re

LICENSES = {'Apache-2.0', 'MPL-2.0', 'MIT'}
HOST = re.compile(r'[a-z0-9_](?:[a-z0-9_-]{0,61}[a-z0-9_])?(?:\.[a-z0-9_](?:[a-z0-9_-]{0,61}[a-z0-9_])?)*\Z')


def parse_rule(line):
    action, kind = 'BLOCK', 'EXACT'
    if line.startswith('@@'):
        action = 'ALLOW'
        line = line[2:]
    if line.startswith('||') and line.endswith('^'):
        kind, line = 'SUFFIX', line[2:-1]
    elif line.startswith('*.'):
        kind, line = 'WILDCARD', line[2:]
    domain = line.rstrip('.').lower()
    if len(domain) > 253 or not HOST.fullmatch(domain):
        raise ValueError('Unsupported or invalid DNS rule: ' + line)
    return action, kind, domain


def check_never_block(rows, never_block_domains):
    violations = []
    for action, kind, rule_domain, category, source_id in sorted(rows):
        if action != 'BLOCK':
            continue
        for host in sorted(never_block_domains):
            matches = False
            if kind == 'EXACT' and host == rule_domain:
                matches = True
            elif kind == 'SUFFIX' and (host == rule_domain or host.endswith('.' + rule_domain)):
                matches = True
            elif kind == 'WILDCARD' and host.endswith('.' + rule_domain):
                matches = True
            if matches:
                violations.append(f"BLOCK {kind} {rule_domain} from source {source_id} matches never-block domain {host}")
    if violations:
        raise ValueError("Never-block violations detected:\n" + "\n".join(violations))


def compile_sources(root, manifest, output):
    root = pathlib.Path(root).resolve()
    rows, sources, ids = set(), [], set()
    for entry in manifest['sources']:
        if entry.get('license') not in LICENSES or not entry.get('revision') or not entry.get('url'):
            raise ValueError('Missing or unapproved source provenance')
        source = (root / entry['path']).resolve()
        if not source.is_relative_to(root):
            raise ValueError('Source path escapes repository')
        data = source.read_bytes()
        data = data.replace(b'\r\n', b'\n') # Canonical text bytes across Windows/Linux checkouts.
        digest = hashlib.sha256(data).hexdigest()
        if digest != entry.get('sha256'):
            raise ValueError('Source checksum mismatch: ' + entry['path'])
        if entry['id'] in ids:
            raise ValueError('Duplicate source ID')
        ids.add(entry['id'])
        if entry['syntax'] == 'psl':
            sources.append(dict(entry))
            continue
        if entry['syntax'] not in ('rules', 'suffix-seeds') or entry['category'] not in ('AD', 'ADULT_CONTENT'):
            raise ValueError('Unsupported source syntax/category')
        for line in data.decode('utf-8').splitlines():
            line = line.strip()
            if not line or line.startswith(('#', '!')):
                continue
            if entry['syntax'] == 'suffix-seeds':
                line = '||' + line + '^'
            action, kind, domain = parse_rule(line)
            rows.add((action, kind, domain, entry['category'], str(entry['id'])))
        sources.append(dict(entry))

    never_block_file = root / 'tools/filter-compiler/never_block.txt'
    if not never_block_file.is_file():
        never_block_file = root / 'never_block.txt'
    if never_block_file.is_file():
        never_block_domains = {
            line.strip().lower()
            for line in never_block_file.read_text(encoding='utf-8').splitlines()
            if line.strip() and not line.strip().startswith('#')
        }
        check_never_block(rows, never_block_domains)
    encoded = ''.join('\t'.join(row) + '\n' for row in sorted(rows)).encode('utf-8')
    result = {'format': 1, 'ruleCount': len(rows), 'rulesSha256': hashlib.sha256(encoded).hexdigest(),
              'sources': sorted(sources, key=lambda entry: entry['id'])}
    output = pathlib.Path(output)
    output.mkdir(parents=True, exist_ok=True)
    (output / 'dns-rules.tsv').write_bytes(encoded)
    (output / 'dns-rules-manifest.json').write_bytes((json.dumps(result, sort_keys=True, indent=2) + '\n').encode())


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=pathlib.Path, required=True)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    args = parser.parse_args()
    compile_sources(args.root, json.loads((args.root / 'tools/filter-compiler/sources.json').read_text()), args.output)
