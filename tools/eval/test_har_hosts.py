#!/usr/bin/env python3
import json
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(__file__))
from har_hosts import extract_hosts_from_har, load_existing_labels


class HarHostsTest(unittest.TestCase):
    def test_extract_hosts_from_har(self):
        har_data = {
            "log": {
                "entries": [
                    {"request": {"url": "https://doubleclick.net/ad?id=1"}},
                    {"request": {"url": "https://googleads.g.doubleclick.net/pagead"}},
                    {"request": {"url": "https://doubleclick.net/other"}},
                    {"request": {"url": "https://www.example.com/index.html"}},
                    {"request": {"url": "invalid-url"}},
                ]
            }
        }
        with tempfile.NamedTemporaryFile("w", suffix=".har", delete=False) as f:
            json.dump(har_data, f)
            temp_path = f.name

        try:
            counts = extract_hosts_from_har(temp_path)
            self.assertEqual(2, counts["doubleclick.net"])
            self.assertEqual(1, counts["googleads.g.doubleclick.net"])
            self.assertEqual(1, counts["www.example.com"])
            self.assertNotIn("invalid-url", counts)
        finally:
            os.unlink(temp_path)

    def test_load_existing_labels(self):
        csv_content = "host,count,label\ndoubleclick.net,10,AD\nexample.com,5,NEEDED\n"
        with tempfile.NamedTemporaryFile("w", suffix=".csv", delete=False) as f:
            f.write(csv_content)
            temp_path = f.name

        try:
            labels = load_existing_labels(temp_path)
            self.assertEqual("AD", labels["doubleclick.net"])
            self.assertEqual("NEEDED", labels["example.com"])
        finally:
            os.unlink(temp_path)


if __name__ == "__main__":
    unittest.main()
