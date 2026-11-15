# Generated source inventory

This folder is machine-owned by `scripts/docs_sync.py` and checked by `scripts/docs_sync.py --check`. After extracting this pack **into a full checkout** run:

```bash
python3 scripts/docs_sync.py --write
python3 scripts/docs_sync.py --check
```

Outputs: `MODULE_INDEX.md` (Gradle module summary), `BUILD_FACTS.md` (build settings/catalog values), `EXTENSION_CATALOG.md` (manifest metadata), `SOURCE_SYMBOLS.md` (Kotlin declarations), `TEST_CATALOG.md` (test files/names). The script intentionally does not infer acceptance status or feature meaning. Do **not** edit generated pages manually.
