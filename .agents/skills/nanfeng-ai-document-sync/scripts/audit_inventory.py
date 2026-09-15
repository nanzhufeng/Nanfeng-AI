#!/usr/bin/env python3
"""Check an inventory without executing application code or emitting file bodies."""
import argparse
import ast
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, required=True)
    parser.add_argument('--inventory', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    repo = args.repo.resolve()
    source = json.loads(args.inventory.read_text())
    markers = {
        'todo': re.compile(r'\b(?:TODO|FIXME|HACK)\b'),
        'test_declaration': re.compile(r'@Test|#\[test\]|\btest\(|\bfunc Test'),
        'network_boundary': re.compile(r'https?://|\breqwest\b|HttpURLConnection'),
        'persistence_boundary': re.compile(r'@Entity|CREATE TABLE|withTransaction|transaction\('),
    }

    def check(row):
        name = row['path']
        path = repo / name
        result = {'path': name, 'kind': row['kind']}
        if row['kind'] != 'text':
            return result
        raw = path.read_bytes()
        text = raw.decode('utf-8')
        result['matches_inventory'] = hashlib.sha256(raw).hexdigest() == row['sha256']
        result['markers'] = {key: len(regex.findall(text)) for key, regex in markers.items()}
        suffix = path.suffix.lower()
        command = None
        try:
            if suffix == '.json':
                json.loads(text)
                result['syntax'] = 'json:passed'
            elif suffix in ('.xml', '.svg'):
                ET.fromstring(text)
                result['syntax'] = 'xml:passed'
            elif suffix == '.py':
                ast.parse(text)
                result['syntax'] = 'python:passed'
            elif suffix in ('.mjs', '.cjs'):
                command = ['node', '--check', str(path)]
            elif suffix == '.sh':
                first = text.splitlines()[0] if text else ''
                shell = 'zsh' if 'zsh' in first else ('bash' if 'bash' in first else 'sh')
                command = [shell, '-n', str(path)]
            else:
                result['syntax'] = 'not-checked'
            if command:
                if not shutil.which(command[0]):
                    result['syntax'] = command[0] + ':unavailable'
                else:
                    run = subprocess.run(command, capture_output=True, timeout=30)
                    result['syntax'] = command[0] + (':passed' if run.returncode == 0 else ':failed')
        except (ValueError, SyntaxError, ET.ParseError, subprocess.TimeoutExpired) as error:
            result['syntax'] = 'failed:' + type(error).__name__
        return result

    with ThreadPoolExecutor(max_workers=4) as pool:
        rows = list(pool.map(check, source))
    summary = {
        'inventory_files': len(source),
        'syntax': dict(Counter(row.get('syntax', 'non-text') for row in rows)),
        'changed_since_inventory': [row['path'] for row in rows if row.get('matches_inventory') is False],
        'limitations': ['Marker counts are routing hints, not findings or coverage.',
                        'No Kotlin/Rust/SQL type or runtime validation in this script.',
                        'No private, untracked or ignored input; no file bodies emitted.'],
    }
    # Exclusive output creation preserves all prior evidence.
    with args.output.open('x') as output:
        json.dump({'summary': summary, 'files': rows}, output, ensure_ascii=False, indent=2)
        output.write('\n')
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == '__main__':
    main()
