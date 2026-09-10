#!/usr/bin/env python3
"""Read-only tracked-file and local Git-history inventory; never copies file bodies."""
import argparse
import collections
import hashlib
import json
from pathlib import Path
import subprocess


def git(repo, *args):
    return subprocess.check_output(['git', '-C', str(repo), *args])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    repo = args.repo.resolve()
    output = args.output.resolve()
    paths = git(repo, 'ls-files', '-z').decode().split('\0')
    rows = []
    for name in filter(None, paths):
        path = repo / name
        if path.is_symlink():
            rows.append({'path': name, 'kind': 'symlink-not-followed'})
            continue
        if not path.is_file():
            rows.append({'path': name, 'kind': 'missing'})
            continue
        data = path.read_bytes()
        text = None
        try:
            if b'\0' not in data:
                text = data.decode('utf-8')
        except UnicodeDecodeError:
            pass
        rows.append({'path': name, 'kind': 'text' if text is not None else 'binary',
                     'bytes': len(data), 'lines': len(text.splitlines()) if text is not None else None,
                     'sha256': hashlib.sha256(data).hexdigest()})
    raw = git(repo, '-c', 'core.quotepath=false', 'log', '--all', '--reverse', '--numstat',
              '--date=short', '--format=COMMIT%x09%H%x09%ad%x09%s').decode()
    history = []
    current = None
    for line in raw.splitlines():
        if line.startswith('COMMIT\t'):
            _, commit, date, subject = line.split('\t', 3)
            current = {'commit': commit, 'date': date, 'subject': subject, 'files': []}
            history.append(current)
        elif line and current is not None:
            parts = line.split('\t', 2)
            if len(parts) == 3:
                added, deleted, name = parts
                current['files'].append({'path': name, 'added': int(added) if added.isdigit() else None,
                                         'deleted': int(deleted) if deleted.isdigit() else None})
    summary = {'head': git(repo, 'rev-parse', 'HEAD').decode().strip(),
               'scope': 'Tracked working-tree bytes and all locally available Git refs; no file bodies or secrets emitted',
               'tracked_files': len(rows), 'bytes': sum(row.get('bytes', 0) for row in rows),
               'kinds': dict(collections.Counter(row['kind'] for row in rows)),
               'commits': len(history),
               'limitations': ['No ignored or untracked data read', 'No binary visual verification',
                               'Not a line-by-line semantic or external deployment audit']}
    # Never overwrite a tracked file or source directory with generated evidence.
    if any(output == (repo / name).parent or output == repo / name for name in filter(None, paths)):
        raise SystemExit('Choose a separate, new evidence directory or a temporary output directory')
    output.mkdir(parents=True, exist_ok=True)
    for name, value in [('files.json', rows), ('history.json', history), ('summary.json', summary)]:
        target = output / name
        if target.exists():
            raise SystemExit(f'Refusing to overwrite existing evidence: {target.name}')
        target.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(summary, ensure_ascii=False))


if __name__ == '__main__':
    main()
