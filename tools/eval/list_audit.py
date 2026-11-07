#!/usr/bin/env python3
"""
List audit tool for Un-Blocker filter rules.
Evaluates filter lists against RuleAdmission guards:
1. Rejects single-label rules (e.g. ||com^, ||in^)
2. Rejects public suffix rules (e.g. ||co.uk^, ||github.io^)
3. Flags collisions with protected never-block domains and primary-hosts.tsv
"""

import sys
import os
import re
import json

def load_primary_hosts(path):
    hosts = set()
    if os.path.exists(path):
        with open(path, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#'):
                    for h in line.split():
                        hosts.add(h.lower().rstrip('.'))
    return hosts

def load_never_block(path):
    hosts = set()
    if os.path.exists(path):
        with open(path, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#'):
                    hosts.add(line.lower().rstrip('.'))
    return hosts

PUBLIC_SUFFIXES = {
    "co.uk", "gov.uk", "ac.uk", "org.uk",
    "co.in", "gov.in", "ac.in", "org.in", "net.in",
    "com.au", "net.au", "org.au", "edu.au",
    "co.jp", "ne.jp", "ac.jp",
    "com.br", "org.br",
    "github.io", "gitlab.io", "pages.dev", "web.app", "firebaseapp.com"
}

def parse_rule_line(raw_line):
    line = raw_line.strip()
    if not line or line.startswith(('#', '!', '[', '@@')) or '##' in line or '#@#' in line or '#?#' in line:
        return None

    # Strip inline comments
    if '#' in line:
        line = line.split('#')[0].strip()

    # Adblock / uBlock syntax
    if line.startswith('||'):
        rule = line[2:]
        # Strip options if any ($)
        if '$' in rule:
            opts = rule.split('$', 1)[1]
            # If options contain non-DNS modifiers, rule cannot be represented as pure DNS
            opt_list = opts.split(',')
            for opt in opt_list:
                clean_opt = opt.lstrip('~').split('=')[0].strip()
                if clean_opt not in {'', 'all', 'badfilter', 'document', 'domain', 'important', 'popup', 'third-party', '1p', '3p'}:
                    return None
            rule = rule.split('$', 1)[0]
        # Truncate at separator ^ or /
        for sep in ('^', '/', '$'):
            if sep in rule:
                rule = rule.split(sep, 1)[0]
        rule = rule.strip().strip('.')
        return rule.lower() if rule else None

    # Hosts file format
    tokens = line.split()
    if len(tokens) >= 2 and tokens[0] in {'0.0.0.0', '127.0.0.1', '::1', '::'}:
        host = tokens[1].strip().strip('.')
        return host.lower() if host and host != 'localhost' else None

    # Plain domain
    if '.' in line and not line.startswith('.') and ' ' not in line and '/' not in line and '$' not in line:
        return line.strip().strip('.').lower()

    return None

def check_admission(domain, never_block, primary_hosts):
    if not domain or '.' not in domain:
        return "SINGLE_LABEL"
    if domain in PUBLIC_SUFFIXES:
        return "PUBLIC_SUFFIX"
    # IP literal check
    if re.match(r'^\d+\.\d+\.\d+\.\d+$', domain) or ':' in domain:
        return "IP_LITERAL"
    # Never block collision
    if domain in never_block or domain in primary_hosts:
        return "COLLISION_NEVER_BLOCK"
    for parent in never_block:
        if domain.endswith('.' + parent):
            # Subdomain of protected host
            pass
    return "OK"

def audit_lines(lines, never_block, primary_hosts):
    stats = {
        "total_lines": len(lines),
        "valid_rules": 0,
        "skipped_lines": 0,
        "rejected_single_label": 0,
        "rejected_public_suffix": 0,
        "rejected_ip": 0,
        "collisions": []
    }
    for line in lines:
        domain = parse_rule_line(line)
        if not domain:
            stats["skipped_lines"] += 1
            continue
        admission = check_admission(domain, never_block, primary_hosts)
        if admission == "OK":
            stats["valid_rules"] += 1
        elif admission == "SINGLE_LABEL":
            stats["rejected_single_label"] += 1
        elif admission == "PUBLIC_SUFFIX":
            stats["rejected_public_suffix"] += 1
        elif admission == "IP_LITERAL":
            stats["rejected_ip"] += 1
        elif admission == "COLLISION_NEVER_BLOCK":
            stats["collisions"].append(domain)
    return stats

def main():
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    primary_tsv = os.path.join(root, "app", "src", "test", "resources", "primary-hosts.tsv")
    never_block_txt = os.path.join(root, "tools", "filter-compiler", "never_block.txt")

    primary_hosts = load_primary_hosts(primary_tsv)
    never_block = load_never_block(never_block_txt)

    filters_dir = os.path.join(root, "filters")
    print(f"Loaded {len(primary_hosts)} primary hosts, {len(never_block)} never-block domains.")
    total_violations = 0

    if os.path.exists(filters_dir):
        for fname in os.listdir(filters_dir):
            if fname.endswith(('.txt', '.rules', '.hosts')):
                fpath = os.path.join(filters_dir, fname)
                with open(fpath, 'r', encoding='utf-8', errors='ignore') as f:
                    lines = f.readlines()
                stats = audit_lines(lines, never_block, primary_hosts)
                print(f"File {fname}: {stats['valid_rules']} valid, {stats['rejected_single_label']} single-label, {stats['rejected_public_suffix']} public-suffix, {len(stats['collisions'])} collisions")
                if stats['collisions']:
                    print(f"  Collisions in {fname}: {stats['collisions']}")
                    total_violations += len(stats['collisions'])

    if total_violations > 0:
        print(f"AUDIT FAILED: {total_violations} collision violations found.")
        sys.exit(1)
    else:
        print("AUDIT PASSED: No forbidden collisions found.")
        sys.exit(0)

if __name__ == '__main__':
    main()
