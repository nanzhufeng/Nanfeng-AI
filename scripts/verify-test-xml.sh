#!/usr/bin/env bash
set -euo pipefail

python3 - "$1" <<'PY'
import pathlib
import sys
import xml.etree.ElementTree as ET

target = pathlib.Path(sys.argv[1])
paths = sorted(target.glob("TEST-*.xml")) if target.is_dir() else [target]
if not paths or any(not path.is_file() for path in paths):
    print(f"no JUnit XML found at {target}", file=sys.stderr)
    raise SystemExit(2)

tests = failures = errors = skipped = 0
elapsed = 0.0
for path in paths:
    root = ET.parse(path).getroot()
    tests += int(root.get("tests", 0))
    failures += int(root.get("failures", 0))
    errors += int(root.get("errors", 0))
    skipped += int(root.get("skipped", 0))
    elapsed += float(root.get("time", 0.0))

print(
    f"tests={tests} failures={failures} errors={errors} "
    f"skipped={skipped} time={elapsed:.3f} files={len(paths)}"
)
raise SystemExit(0 if tests > 0 and failures == 0 and errors == 0 and skipped == 0 else 1)
PY
