#!/usr/bin/env python3
"""Maintainer-run vendoring script for filter lists.
Downloads a list at a pinned 40-hex commit SHA, normalizes line endings to LF,
saves list.txt and LICENSE, and outputs the sha256 for sources.json.
Never runs at build or runtime.
"""
import argparse
import hashlib
import pathlib
import re
import sys
import urllib.request

COMMIT_RE = re.compile(r'^[0-9a-f]{40}$')


def vendor_source(name, commit, list_url, license_url, repo_url, category="AD", syntax="hosts", source_id=4):
    if not COMMIT_RE.match(commit):
        raise ValueError(f"Invalid commit hash: {commit}. Must be a 40-hex commit SHA.")

    out_dir = pathlib.Path(__file__).resolve().parent / "vendor" / name / commit
    out_dir.mkdir(parents=True, exist_ok=True)

    print(f"Fetching {name} at commit {commit}...")
    req = urllib.request.Request(list_url, headers={"User-Agent": "unblocker-vendor/1.0"})
    with urllib.request.urlopen(req) as resp:
        content = resp.read()

    # Normalize CRLF to LF
    content = content.replace(b'\r\n', b'\n')
    list_path = out_dir / "list.txt"
    list_path.write_bytes(content)
    digest = hashlib.sha256(content).hexdigest()

    print(f"Fetching LICENSE...")
    lic_req = urllib.request.Request(license_url, headers={"User-Agent": "unblocker-vendor/1.0"})
    with urllib.request.urlopen(lic_req) as resp:
        license_content = resp.read()
    license_content = license_content.replace(b'\r\n', b'\n')
    (out_dir / "LICENSE").write_bytes(license_content)

    rel_path = f"tools/filter-compiler/vendor/{name}/{commit}/list.txt"
    print("\nVendoring complete!")
    print(f"Saved: {list_path}")
    print(f"Lines: {len(content.splitlines())}")
    print(f"SHA-256: {digest}")
    print("\nPaste into sources.json:")
    entry = {
        "id": source_id,
        "path": rel_path,
        "license": "MIT",
        "revision": commit,
        "url": repo_url,
        "sha256": digest,
        "syntax": syntax,
        "category": category
    }
    import json
    print(json.dumps(entry, indent=2))
    return entry


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Vendor a remote blocklist at a pinned commit.")
    parser.add_argument("--name", default="stevenblack", help="Vendor directory name")
    parser.add_argument("--commit", default="21605ccaecf26941005d4a7a3c1267af234599cf", help="40-hex commit SHA")
    parser.add_argument("--list-url", default=None, help="Direct download URL for list")
    parser.add_argument("--license-url", default=None, help="Direct download URL for LICENSE")
    parser.add_argument("--repo-url", default="https://github.com/StevenBlack/hosts", help="Repo URL")
    parser.add_argument("--category", default="AD", help="Category: AD or ADULT_CONTENT")
    parser.add_argument("--syntax", default="hosts", help="Syntax: hosts or domains")
    parser.add_argument("--source-id", type=int, default=4, help="Numeric source ID")

    args = parser.parse_args()
    list_url = args.list_url or f"https://raw.githubusercontent.com/StevenBlack/hosts/{args.commit}/hosts"
    license_url = args.license_url or f"https://raw.githubusercontent.com/StevenBlack/hosts/{args.commit}/license.txt"

    vendor_source(args.name, args.commit, list_url, license_url, args.repo_url, args.category, args.syntax, args.source_id)
