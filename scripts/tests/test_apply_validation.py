#!/usr/bin/env python3
import unittest
import tempfile
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from apply_validation import merge_never_block, merge_allow_rules

class TestApplyValidation(unittest.TestCase):

    def test_merge_never_block(self):
        with tempfile.NamedTemporaryFile('w+', delete=False, encoding='utf-8') as f:
            f.write("# Existing comments\nexample.com\n")
            f_path = f.name

        try:
            added = merge_never_block(f_path, ["example.com", "newsite.org", "TEST.net"])
            self.assertEqual(added, 2)
            with open(f_path, 'r', encoding='utf-8') as f:
                content = f.read()
            self.assertIn("example.com", content)
            self.assertIn("newsite.org", content)
            self.assertIn("test.net", content)
            self.assertIn("# Existing comments", content)
        finally:
            os.remove(f_path)

    def test_merge_allow_rules(self):
        with tempfile.NamedTemporaryFile('w+', delete=False, encoding='utf-8') as f:
            f.write("@@||existing.com^\n")
            f_path = f.name

        try:
            added = merge_allow_rules(f_path, ["existing.com", "newsite.org"])
            self.assertEqual(added, 1)
            with open(f_path, 'r', encoding='utf-8') as f:
                content = f.read()
            self.assertIn("@@||existing.com^", content)
            self.assertIn("@@||newsite.org^", content)
        finally:
            os.remove(f_path)

if __name__ == '__main__':
    unittest.main()
