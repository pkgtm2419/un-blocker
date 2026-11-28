"""Unit tests for scripts/check_hardcoded_strings.py."""

from __future__ import annotations

from pathlib import Path
import sys
import tempfile
import unittest

SCRIPTS_DIR = Path(__file__).resolve().parent.parent
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

import check_hardcoded_strings  # noqa: E402


class CheckHardcodedStringsTest(unittest.TestCase):
    def test_flags_hardcoded_compose_text_and_content_description(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            sample = root / "Sample.kt"
            sample.write_text(
                """
                @Composable
                fun Sample() {
                    Text("Hello World")
                    Icon(contentDescription = "Close dialog")
                    TopBar(title = "Settings")
                }
                """.strip(),
                encoding="utf-8",
            )
            violations = check_hardcoded_strings.scan_tree(root)
            self.assertEqual([v.literal for v in violations], ["Hello World", "Close dialog", "Settings"])

    def test_ignores_string_resources_and_non_translatable_symbols(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            sample = root / "Clean.kt"
            sample.write_text(
                """
                @Composable
                fun Clean() {
                    val anim by animateFloatAsState(1f, label = "pulse")
                    Text(stringResource(R.string.app_name))
                    Text("#")
                    Text("${count}")
                    Icon(contentDescription = null)
                }
                """.strip(),
                encoding="utf-8",
            )
            violations = check_hardcoded_strings.scan_tree(root)
            self.assertEqual(violations, [])


if __name__ == "__main__":
    unittest.main()
