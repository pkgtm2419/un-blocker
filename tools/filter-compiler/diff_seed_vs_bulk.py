#!/usr/bin/env python3
"""Compares seed domains against bulk vendored lists."""
import pathlib

root = pathlib.Path(__file__).resolve().parent.parent.parent
seed_file = root / "app/src/main/assets/ad_domains.txt"
steven_file = root / "tools/filter-compiler/vendor/stevenblack/21605ccaecf26941005d4a7a3c1267af234599cf/list.txt"

seeds = set()
for line in seed_file.read_text(encoding="utf-8").splitlines():
    l = line.strip().lower()
    if l and not l.startswith(('#', '!')):
        seeds.add(l)

bulk = set()
for line in steven_file.read_text(encoding="utf-8").splitlines():
    l = line.split('#')[0].strip()
    parts = l.split()
    if len(parts) >= 2 and parts[0] in ('0.0.0.0', '127.0.0.1'):
        for h in parts[1:]:
            bulk.add(h.lower().rstrip('.'))

unique_seeds = sorted(seeds - bulk)
covered_seeds = sorted(seeds & bulk)

print(f"Total seeds: {len(seeds)}")
print(f"Seeds covered in StevenBlack: {len(covered_seeds)}")
print(f"Seeds unique to un-blocker (not in StevenBlack): {len(unique_seeds)}")
