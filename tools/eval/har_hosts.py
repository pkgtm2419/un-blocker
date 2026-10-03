#!/usr/bin/env python3
"""
HAR Hosts Extractor and Labeling Tool

Reads one or more HTTP Archive (.har) files, extracts unique hostnames with
request counts, and outputs a CSV with columns:
  host,count,label

Allowed labels:
  AD, TRACKER, NEEDED, OTHER, UNLABELED
"""

import argparse
import csv
import json
import os
import sys
from collections import Counter
from urllib.parse import urlparse

ALLOWED_LABELS = {"AD", "TRACKER", "NEEDED", "OTHER", "UNLABELED"}


def extract_hosts_from_har(har_path):
    """Extract hostnames and their occurrence counts from a HAR file."""
    counts = Counter()
    with open(har_path, "r", encoding="utf-8", errors="ignore") as f:
        try:
            data = json.load(f)
        except json.JSONDecodeError as e:
            sys.stderr.write(f"Warning: Failed to parse JSON in {har_path}: {e}\n")
            return counts

    entries = data.get("log", {}).get("entries", [])
    for entry in entries:
        url = entry.get("request", {}).get("url")
        if not url:
            continue
        try:
            parsed = urlparse(url)
            host = parsed.hostname
            if host:
                host = host.strip().lower().rstrip(".")
                if host:
                    counts[host] += 1
        except Exception:
            continue
    return counts


def load_existing_labels(csv_path):
    """Load existing host -> label mappings from a CSV file."""
    labels = {}
    if not os.path.exists(csv_path):
        return labels
    with open(csv_path, "r", encoding="utf-8") as f:
        reader = csv.reader(f)
        header = next(reader, None)
        for row in reader:
            if len(row) >= 3:
                host = row[0].strip().lower()
                label = row[2].strip().upper()
                if label in ALLOWED_LABELS:
                    labels[host] = label
    return labels


def main():
    parser = argparse.ArgumentParser(
        description="Extract unique hostnames from HAR files for coverage evaluation."
    )
    parser.add_argument(
        "har_files",
        nargs="+",
        help="Path to one or more .har files to process",
    )
    parser.add_argument(
        "-o", "--output",
        default=None,
        help="Output CSV path (default: stdout)",
    )
    parser.add_argument(
        "-m", "--merge",
        default=None,
        help="Path to existing labeled CSV to preserve labels",
    )
    parser.add_argument(
        "--default-label",
        default="UNLABELED",
        choices=ALLOWED_LABELS,
        help="Default label for newly discovered hosts",
    )

    args = parser.parse_args()

    total_counts = Counter()
    for har_path in args.har_files:
        if not os.path.exists(har_path):
            sys.stderr.write(f"Error: HAR file not found: {har_path}\n")
            continue
        counts = extract_hosts_from_har(har_path)
        total_counts.update(counts)

    existing_labels = {}
    if args.merge:
        existing_labels = load_existing_labels(args.merge)

    out = sys.stdout
    if args.output:
        out = open(args.output, "w", encoding="utf-8", newline="")

    try:
        writer = csv.writer(out)
        writer.writerow(["host", "count", "label"])
        for host, count in total_counts.most_common():
            label = existing_labels.get(host, args.default_label)
            writer.writerow([host, count, label])
    finally:
        if args.output and out is not sys.stdout:
            out.close()


if __name__ == "__main__":
    main()
