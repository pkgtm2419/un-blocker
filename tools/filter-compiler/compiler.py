"""Deterministic, offline-only DNS rule compiler. No third-party Python dependencies."""
import argparse
import hashlib
import json
import pathlib
import re
import struct

LICENSES = {'Apache-2.0', 'MPL-2.0', 'MIT', 'CC0-1.0'}
HOST = re.compile(r'[a-z0-9_](?:[a-z0-9_-]{0,61}[a-z0-9_])?(?:\.[a-z0-9_](?:[a-z0-9_-]{0,61}[a-z0-9_])?)*\Z')

META_HOSTS = {
    'localhost', 'localhost.localdomain', 'local', 'broadcasthost',
    '0.0.0.0', '127.0.0.1', '::1', 'ip6-localhost', 'ip6-loopback',
    'ip6-localnet', 'ip6-mcastprefix', 'ip6-allnodes', 'ip6-allrouters', 'ip6-allhosts'
}
META_IPS = {
    '0.0.0.0', '127.0.0.1', '255.255.255.255', '::1', 'fe80::1%lo0',
    'ff00::0', 'ff02::1', 'ff02::2', 'ff02::3'
}


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


def reverse_domain(domain):
    return '.'.join(reversed(domain.split('.')))


def entry_sort_key(row):
    action, kind, domain, category, source_id = row
    rev_key = reverse_domain(domain)
    action_order = 0 if action == 'ALLOW' else 1
    kind_order = 0 if kind == 'EXACT' else (1 if kind == 'SUFFIX' else 2)
    cat_order = 0 if category == 'AD' else 1
    return (rev_key, action_order, kind_order, cat_order, int(source_id))


def build_binary_ruleset(rows):
    """
    Build UBR2 binary rule set (little-endian):
    Header (14 bytes):
      magic "UBR2" (4 bytes)
      version: u16 = 2
      ruleCount: u32
      blobLen: u32
    Offsets: ruleCount * u32 (offsets from start of blob)
    Blob entries:
      keyLen: u8
      key: keyLen bytes (reversed domain string in ASCII)
      flags: u8 (bit 0: action, bits 1..2: kind, bits 3..4: category)
      sourceId: u16
    """
    sorted_rows = sorted(rows, key=entry_sort_key)
    rule_count = len(sorted_rows)

    blob_parts = []
    offsets = []
    current_offset = 0

    for action, kind, domain, category, source_id in sorted_rows:
        rev_key = reverse_domain(domain)
        key_bytes = rev_key.encode('ascii')
        key_len = len(key_bytes)
        if key_len > 255:
            raise ValueError(f"Domain key too long for binary ruleset: {rev_key}")

        action_bit = 0 if action == 'ALLOW' else 1
        kind_bits = 0 if kind == 'EXACT' else (1 if kind == 'SUFFIX' else 2)
        cat_bits = 0 if category == 'AD' else 1
        flags = action_bit | (kind_bits << 1) | (cat_bits << 3)
        src_id = int(source_id)

        entry_bytes = struct.pack(f'<B{key_len}sBH', key_len, key_bytes, flags, src_id)
        offsets.append(current_offset)
        blob_parts.append(entry_bytes)
        current_offset += len(entry_bytes)

    blob_bytes = b''.join(blob_parts)
    offsets_bytes = struct.pack(f'<{rule_count}I', *offsets)
    header = struct.pack('<4sHII', b'UBR2', 2, rule_count, len(blob_bytes))
    return header + offsets_bytes + blob_bytes


def compile_sources(root, manifest, output_assets_dir, output_test_dir):
    root = pathlib.Path(root).resolve()
    rows, sources, ids = set(), [], set()
    for entry in manifest['sources']:
        if entry.get('license') not in LICENSES or not entry.get('revision') or not entry.get('url'):
            raise ValueError('Missing or unapproved source provenance')
        source = (root / entry['path']).resolve()
        if not source.is_relative_to(root):
            raise ValueError('Source path escapes repository')
        data = source.read_bytes()
        data = data.replace(b'\r\n', b'\n')  # Canonical text bytes across Windows/Linux checkouts.
        digest = hashlib.sha256(data).hexdigest()
        if digest != entry.get('sha256'):
            raise ValueError('Source checksum mismatch: ' + entry['path'])
        if entry['id'] in ids:
            raise ValueError('Duplicate source ID')
        ids.add(entry['id'])
        if entry['syntax'] == 'psl':
            sources.append(dict(entry))
            continue
        if entry['syntax'] not in ('rules', 'suffix-seeds', 'hosts', 'domains') or entry['category'] not in ('AD', 'ADULT_CONTENT'):
            raise ValueError('Unsupported source syntax/category')

        source_info = dict(entry)
        rules_added_for_source = 0
        skipped_invalid = 0
        total_source_lines = 0

        for line in data.decode('utf-8').splitlines():
            clean_line = line.strip()
            if not clean_line or clean_line.startswith(('#', '!')):
                continue

            total_source_lines += 1

            if entry['syntax'] in ('rules', 'suffix-seeds'):
                # Strict parsing for own seed files
                if entry['syntax'] == 'suffix-seeds':
                    clean_line = '||' + clean_line + '^'
                action, kind, domain = parse_rule(clean_line)
                rows.add((action, kind, domain, entry['category'], str(entry['id'])))
                rules_added_for_source += 1

            elif entry['syntax'] == 'hosts':
                # Strip inline comment
                uncommented = clean_line.split('#', 1)[0].strip()
                if not uncommented:
                    continue
                parts = uncommented.split()
                if not parts:
                    continue
                ip = parts[0]
                if ip in ('0.0.0.0', '127.0.0.1'):
                    for h in parts[1:]:
                        h = h.lower().rstrip('.')
                        if h in META_HOSTS or h.startswith('ip6-'):
                            continue
                        if not HOST.fullmatch(h) or len(h) > 253:
                            if any(token in h for token in ('||', '@@', '*', '^', '$')):
                                raise ValueError(f"Hosts list contains rule-like syntax: {h}")
                            skipped_invalid += 1
                            continue
                        rows.add(('BLOCK', 'EXACT', h, entry['category'], str(entry['id'])))
                        rules_added_for_source += 1
                elif ip in META_IPS:
                    # Known loopback / broadcast IP line (e.g. ::1 or 255.255.255.255), ignore
                    continue
                else:
                    if any(token in uncommented for token in ('||', '@@', '*', '^', '$')):
                        raise ValueError(f"Hosts list contains rule-like syntax: {uncommented}")
                    skipped_invalid += 1

            elif entry['syntax'] == 'domains':
                uncommented = clean_line.split('#', 1)[0].split('!', 1)[0].strip()
                if not uncommented:
                    continue
                d = uncommented.lower().rstrip('.')
                if d in META_HOSTS or d.startswith('ip6-'):
                    continue
                if not HOST.fullmatch(d) or len(d) > 253:
                    if any(token in d for token in ('||', '@@', '*', '^', '$')):
                        raise ValueError(f"Domains list contains rule-like syntax: {d}")
                    skipped_invalid += 1
                    continue
                rows.add(('BLOCK', 'EXACT', d, entry['category'], str(entry['id'])))
                rules_added_for_source += 1

        if entry['syntax'] in ('hosts', 'domains'):
            if total_source_lines > 0 and (skipped_invalid / total_source_lines) > 0.001:
                raise ValueError(f"Invalid lines in bulk source {entry['id']} exceeded 0.1%: {skipped_invalid}/{total_source_lines}")

        source_info['ruleCount'] = rules_added_for_source
        source_info['skippedInvalid'] = skipped_invalid
        sources.append(source_info)

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
        output_assets = pathlib.Path(output_assets_dir)
        output_assets.mkdir(parents=True, exist_ok=True)
        (output_assets / 'never-block-hosts.txt').write_text('\n'.join(sorted(never_block_domains)) + '\n', encoding='utf-8')

    tsv_encoded = ''.join('\t'.join(row) + '\n' for row in sorted(rows)).encode('utf-8')
    bin_bytes = build_binary_ruleset(rows)

    result = {
        'format': 2,
        'ruleCount': len(rows),
        'rulesSha256': hashlib.sha256(tsv_encoded).hexdigest(),
        'binarySha256': hashlib.sha256(bin_bytes).hexdigest(),
        'sources': sorted(sources, key=lambda entry: entry['id'])
    }
    output_assets = pathlib.Path(output_assets_dir)
    output_assets.mkdir(parents=True, exist_ok=True)
    (output_assets / 'dns-rules.bin').write_bytes(bin_bytes)
    (output_assets / 'dns-rules-manifest.json').write_bytes((json.dumps(result, sort_keys=True, indent=2) + '\n').encode())
    
    output_test = pathlib.Path(output_test_dir)
    output_test.mkdir(parents=True, exist_ok=True)
    (output_test / 'dns-rules.tsv').write_bytes(tsv_encoded)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', type=pathlib.Path, required=True)
    parser.add_argument('--output-assets', type=pathlib.Path, required=True)
    parser.add_argument('--output-test', type=pathlib.Path, required=True)
    args = parser.parse_args()
    compile_sources(args.root, json.loads((args.root / 'tools/filter-compiler/sources.json').read_text()), args.output_assets, args.output_test)
