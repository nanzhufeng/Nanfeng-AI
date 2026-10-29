"""Summarize retained Windows test results without copying assertion source/payloads."""
import argparse
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--logs', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
results = {}
for name in ('node-baseline.xml', 'node-lf.xml'):
    path = args.logs / name
    body = path.read_text(encoding='utf-8-sig')
    root = ET.fromstring(body)
    cases = root.findall('.//testcase')
    failures = []
    for case in cases:
        if case.find('failure') is not None:
            source = case.attrib.get('file', '').replace('\\', '/')
            source = 'desktop/' + source.split('/desktop/', 1)[-1] if '/desktop/' in source else ''
            failures.append({'name': case.attrib['name'], 'source': source,
                             'classification': 'requires contract/behavior review; not assumed stale'})
    results[name] = {'counts': {key: int(value) for key, value in re.findall(r'<!-- (tests|pass|fail|skipped|cancelled) (\d+) -->', body)},
                     'failures': failures}
for name in ('rust-baseline.log', 'rust-with-icon.log', 'rust-archive-path.log', 'rust-backup-flush.log',
             'windows-root-red.log', 'windows-root-green.log', 'windows-root-all.log'):
    body = (args.logs / name).read_text(encoding='utf-8-sig')
    counts = re.findall(r'test result: (\w+)\. (\d+) passed; (\d+) failed; (\d+) ignored;', body)
    failures = re.findall(r'^    ((?:tests|desktop_\w+)::[^\s]+)$', body, re.M)
    results[name] = {'test_results': [{'result': result, 'passed': int(passed), 'failed': int(failed), 'ignored': int(ignored)}
                                    for result, passed, failed, ignored in counts], 'failures': failures}
results['rust-baseline.log']['build_blocker'] = 'Tauri Windows Resource generation required missing icons/icon.ico'
results['scope'] = 'Windows source/behavior tests and first isolated native launch; not Mac regression or full Windows acceptance'
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(results, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
print(json.dumps({name: value.get('counts', value.get('test_results')) for name,value in results.items() if isinstance(value,dict)}, ensure_ascii=False))
