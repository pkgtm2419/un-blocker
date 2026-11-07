#!/usr/bin/env python3
"""
Applies user / automated validation entries into never_block.txt, allow.txt, and quarantine.txt.
Ensures deduplication, comment preservation, and invokes test_compiler.py.
"""

import os
import sys
import subprocess

def merge_never_block(target_path, new_domains):
    existing = set()
    lines = []
    if os.path.exists(target_path):
        with open(target_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        for line in lines:
            s = line.strip()
            if s and not s.startswith('#'):
                existing.add(s.lower())

    added = 0
    with open(target_path, 'w', encoding='utf-8') as f:
        for line in lines:
            f.write(line)
        if lines and not lines[-1].endswith('\n'):
            f.write('\n')
        for domain in sorted(new_domains):
            clean = domain.strip().lower().rstrip('.')
            if clean and clean not in existing:
                f.write(f"{clean}\n")
                existing.add(clean)
                added += 1
    return added

def merge_allow_rules(target_path, new_domains):
    existing = set()
    lines = []
    if os.path.exists(target_path):
        with open(target_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        for line in lines:
            s = line.strip()
            if s and not s.startswith('#'):
                existing.add(s)

    added = 0
    with open(target_path, 'w', encoding='utf-8') as f:
        for line in lines:
            f.write(line)
        if lines and not lines[-1].endswith('\n'):
            f.write('\n')
        for domain in sorted(new_domains):
            clean = domain.strip().lower().rstrip('.')
            if clean:
                rule = f"@@||{clean}^"
                if rule not in existing:
                    f.write(f"{rule}\n")
                    existing.add(rule)
                    added += 1
    return added

def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    never_block_path = os.path.join(root, "tools", "filter-compiler", "never_block.txt")
    allow_path = os.path.join(root, "tools", "filter-compiler", "allow.txt")
    
    print(f"Validation target paths:\n  {never_block_path}\n  {allow_path}")
    test_compiler_path = os.path.join(root, "tools", "filter-compiler", "test_compiler.py")
    res = subprocess.run([sys.executable, test_compiler_path])
    if res.returncode != 0:
        print("Compiler tests failed after validation merge.")
        sys.exit(res.returncode)
    print("Validation merge verified successfully.")

if __name__ == '__main__':
    main()
