#!/usr/bin/env python3
import unittest
import sys
import os

# Add tools/eval to sys.path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from list_audit import parse_rule_line, check_admission, audit_lines

class TestListAudit(unittest.TestCase):

    def test_parse_rule_line_adblock(self):
        self.assertEqual(parse_rule_line("||tracker.com^"), "tracker.com")
        self.assertEqual(parse_rule_line("||adservice.net^$third-party"), "adservice.net")
        self.assertIsNone(parse_rule_line("! Comment"))
        self.assertIsNone(parse_rule_line("###ad-banner"))
        self.assertIsNone(parse_rule_line("@@||allowed.com^"))

    def test_parse_rule_line_hosts(self):
        self.assertEqual(parse_rule_line("0.0.0.0 badsite.com"), "badsite.com")
        self.assertEqual(parse_rule_line("127.0.0.1 badsite2.com # inline comment"), "badsite2.com")
        self.assertIsNone(parse_rule_line("127.0.0.1 localhost"))

    def test_check_admission(self):
        never_block = {"google.com", "docs.google.com"}
        primary_hosts = {"youtube.com"}

        self.assertEqual(check_admission("com", never_block, primary_hosts), "SINGLE_LABEL")
        self.assertEqual(check_admission("co.uk", never_block, primary_hosts), "PUBLIC_SUFFIX")
        self.assertEqual(check_admission("127.0.0.1", never_block, primary_hosts), "IP_LITERAL")
        self.assertEqual(check_admission("google.com", never_block, primary_hosts), "COLLISION_NEVER_BLOCK")
        self.assertEqual(check_admission("youtube.com", never_block, primary_hosts), "COLLISION_NEVER_BLOCK")
        self.assertEqual(check_admission("normalad.com", never_block, primary_hosts), "OK")

    def test_audit_lines(self):
        lines = [
            "||ad.com^",
            "||co.uk^",
            "||com^",
            "||google.com^",
            "0.0.0.0 tracker.net"
        ]
        never_block = {"google.com"}
        primary = set()
        stats = audit_lines(lines, never_block, primary)
        self.assertEqual(stats["valid_rules"], 2)
        self.assertEqual(stats["rejected_single_label"], 1)
        self.assertEqual(stats["rejected_public_suffix"], 1)
        self.assertEqual(len(stats["collisions"]), 1)

if __name__ == '__main__':
    unittest.main()
