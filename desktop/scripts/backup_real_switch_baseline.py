"""Private synthetic full-table snapshots and controlled-file fingerprints."""
import hashlib
import json
from pathlib import Path
import sqlite3

CONTROLLED = {'assets': 'attachments/v1', 'packages': 'workspace-packages/v1',
             'p6k-zip-import-assets': 'zip-import-assets/v1', 'exchange-v2/archives': 'workspace-exchange-archives/v2'}

def digest(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def connect(path):
    c = sqlite3.connect(Path(path).resolve().as_uri()+'?mode=ro', uri=True, timeout=3)
    assert c.execute('pragma integrity_check').fetchall() == [('ok',)], str(path)
    assert c.execute('pragma foreign_key_check').fetchall() == [], str(path)
    return c

def tables(path):
    c = connect(path)
    result = {}
    for (name,) in c.execute("select name from sqlite_master where type='table' order by name").fetchall():
        rows = c.execute('select * from "'+name.replace('"','""')+'"').fetchall()
        result[name] = dict(count=len(rows), sha256=hashlib.sha256(repr(sorted(rows,key=repr)).encode()).hexdigest())
    c.close()
    return result

def tree(path, prefix=''):
    path = Path(path)
    if not path.exists():
        return {}
    assert not path.is_symlink() and not path.is_junction()
    if path.is_file():
        return {prefix: digest(path)}
    out = {prefix: None}
    for child in sorted(path.iterdir()):
        out.update(tree(child, prefix+'/'+child.name))
    return out

def snapshot(root, area):
    root = Path(root)
    destination = root / ('pre-switch-full-baseline-'+area)
    destination.mkdir()
    result = {'databases': {}, 'controlledFiles': {}}
    for selected in ('CHAT','WORK'):
        base = root if selected == 'CHAT' else root/'work-area'
        for local in ('workspace.sqlite3','usage-ledger/usage-ledger-v1.sqlite3'):
            path = base/local
            if not path.is_file():
                continue
            key = selected+'/'+local
            output = destination/key
            output.parent.mkdir(parents=True, exist_ok=True)
            c = connect(path)
            target = sqlite3.connect(output)
            c.backup(target)
            target.close(); c.close()
            result['databases'][key] = {'snapshot': str(output), 'tables': tables(output)}
        inventory = {}
        for local, package in CONTROLLED.items():
            inventory.update(tree(base/local, package))
        result['controlledFiles'][selected] = inventory
    path = root/'app-configuration.sqlite3'
    output = destination/'app-configuration.sqlite3'
    c = connect(path); target = sqlite3.connect(output); c.backup(target); target.close(); c.close()
    result['databases']['SHARED/app-configuration.sqlite3'] = {'snapshot':str(output),'tables':tables(output)}
    with (destination/'baseline.json').open('x',encoding='utf-8') as stream:
        json.dump(result,stream,ensure_ascii=False,indent=2)
    return str(destination/'baseline.json')
