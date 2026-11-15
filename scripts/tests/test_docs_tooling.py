"""Offline regression suite for deterministic documentation and frontmatter."""
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]

def load_module(filename):
    spec=importlib.util.spec_from_file_location(filename, SCRIPTS/(filename+'.py'))
    mod=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod

sync=load_module('docs_sync')
contract=load_module('docs_contract')
plan_spec=importlib.util.spec_from_file_location('validate_plans', SCRIPTS.parent/'validate_plans.py')
plans=importlib.util.module_from_spec(plan_spec)
plan_spec.loader.exec_module(plans)

class DocumentationToolsTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name)
        files={
          'settings.gradle.kts': 'include(":app")\ninclude(":common")\n',
          'app/build.gradle.kts': 'android { compileSdk = 35\n defaultConfig { applicationId = "com.unblocker.app"\n minSdk = 26\n targetSdk = 35\n versionCode = 35\n versionName = "2.6.1"\n } }\n',
          'gradle/libs.versions.toml': '[versions]\nkotlin = "2.0.21"\n',
          'app/src/main/java/com/unblocker/app/Main.kt': 'package demo\nclass MainActivity {}\ninterface MainView {}\n',
          'app/src/test/java/com/unblocker/app/MainTest.kt': 'class MainTest {}\n',
          'app/src/main/assets/extensions/extensions_manifest.json': json.dumps({'version':6,'extensions':[{'id':'b','name':'B'}, {'id':'a','name':'A'}]})
        }
        for path,body in files.items():
            loc=self.root/path
            loc.parent.mkdir(parents=True,exist_ok=True)
            loc.write_text(body,encoding='utf-8')
    def test_five_inventories_are_deterministic(self):
        first=sync.generated(self.root)
        second=sync.generated(self.root)
        self.assertEqual(first,second)
        self.assertEqual(set(first),set(sync.OUTPUTS))
        self.assertIn('MainActivity',first['SOURCE_SYMBOLS.md'])
        self.assertIn('`2.6.1`',first['BUILD_FACTS.md'])
        self.assertIn('record count: **2**',first['EXTENSION_CATALOG.md'])
    def test_bootstrap_then_write_then_check(self):
        self.assertEqual(sync.run(self.root,'check'),1)
        self.assertFalse((self.root/'docs/generated/BUILD_FACTS.md').exists())
        self.assertEqual(sync.run(self.root,'write'),0)
        self.assertEqual(sync.run(self.root,'check'),0)
    def test_source_change_detects_drift(self):
        self.assertEqual(sync.run(self.root,'write'),0)
        f=self.root/'app/src/main/java/com/unblocker/app/Main.kt'
        f.write_text(f.read_text(encoding='utf-8')+'class NewFeature {}\n',encoding='utf-8')
        self.assertEqual(sync.run(self.root,'check'),1)
        self.assertEqual(sync.run(self.root,'write'),0)
        self.assertEqual(sync.run(self.root,'check'),0)
    def test_manifest_update_detects_drift(self):
        self.assertEqual(sync.run(self.root,'write'),0)
        path=self.root/'app/src/main/assets/extensions/extensions_manifest.json'
        data=json.loads(path.read_text())
        data['extensions'].append({'id':'c','name':'New extension'})
        path.write_text(json.dumps(data))
        self.assertEqual(sync.run(self.root,'check'),1)
    def test_no_source_checkout_errors_explicitly(self):
        with self.assertRaisesRegex(ValueError,'No Gradle app checkout'):
            sync.generated(self.root/'not-a-checkout')
    def test_frontmatter(self):
        p=self.root/'TEST.md'
        p.write_text('---\nname: docs-maintainer\ndescription: Use when docs drift\n---\n# Body\n')
        meta,err=contract.frontmatter(p)
        self.assertEqual(err,'')
        self.assertEqual(meta['name'],'docs-maintainer')
        p.write_text('# Bad')
        self.assertTrue(contract.frontmatter(p)[1])
    def test_truncated_manifest_rejected(self):
        p=self.root/'app/src/main/assets/extensions/extensions_manifest.json'
        p.write_text('{ invalid',encoding='utf-8')
        with self.assertRaisesRegex(ValueError,'Invalid extension manifest'):
            sync.generated(self.root)
    def test_generated_file_unchanged_on_second_write(self):
        sync.run(self.root,'write')
        p=self.root/'docs/generated/MODULE_INDEX.md'
        before=p.read_bytes()
        sync.run(self.root,'write')
        self.assertEqual(before,p.read_bytes())

    def test_missing_inventory_fails_check(self):
        self.assertEqual(sync.run(self.root, 'check'), 1)

    def test_non_object_manifest_entries_have_actionable_error(self):
        p = self.root/'app/src/main/assets/extensions/extensions_manifest.json'
        p.write_text('{"extensions": [null]}', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'Extension item must be object'):
            sync.generated(self.root)

    def test_imported_material_and_build_outputs_are_outside_contract_scope(self):
        for rel in ('unb/original.md', 'app/build/report.md', '.git/notes.md'):
            p = self.root/rel
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text('[original context](missing.md)', encoding='utf-8')
        self.assertFalse(any('missing.md' in e for e in contract.check(self.root)))

    def test_maintained_links_fail_but_code_examples_are_ignored(self):
        p = self.root/'docs/guide.md'
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text('```markdown\n[example](example.md)\n```\n[real](missing.md)', encoding='utf-8')
        issues = contract.check(self.root)
        self.assertTrue(any('missing.md' in e for e in issues))
        self.assertFalse(any('example.md' in e for e in issues))

    def test_missing_readme_is_not_exempt_from_link_validation(self):
        p = self.root/'docs/guide.md'
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text('[overview](../README.md)', encoding='utf-8')
        self.assertTrue(any('missing local link target: ../README.md' in e for e in contract.check(self.root)))

    def test_plans_validate_structure_without_fixed_historical_counts(self):
        folder = self.root/'docs/superpowers/plans'
        folder.mkdir(parents=True)
        body = '# Example Implementation Plan\n\n' + '\n'.join(
            f'**{field}:** example' for field in ('Goal', 'Architecture', 'Tech Stack', 'Spec')
        ) + '\n### Task 1: Check\n- [ ] Run validation\n'
        for name in ('first.md', 'second.md'):
            (folder/name).write_text(body, encoding='utf-8')
        self.assertFalse(any('docs/superpowers/plans' in e for e in plans.validate(self.root)))

    def test_plan_missing_required_metadata_is_rejected(self):
        folder = self.root/'docs/superpowers/plans'
        folder.mkdir(parents=True)
        (folder/'broken.md').write_text('# Broken\n', encoding='utf-8')
        self.assertTrue(any('missing Goal' in e for e in plans.validate(self.root)))

if __name__=='__main__':
    unittest.main()
