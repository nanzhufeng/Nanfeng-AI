"""Reconstruct a historical schema in a marked synthetic backup, never a live DB."""
import hashlib
import json
from pathlib import Path
import sqlite3
import sys
import uuid
import zipfile

root, source, target = map(lambda x: Path(x).resolve(), sys.argv[1:4])
version = int(sys.argv[4])
assert version in (50, 51)
parent = Path('D:/CodexTemp').resolve()
assert root.parent == parent and root.name.startswith('nanfeng-ai-windows-acceptance.')
marker = json.loads((root/'.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))
assert marker['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
assert source.parent == target.parent == parent
assert source.name.startswith('nfai-normal-backup.') and target.name.startswith('nfai-normal-backup.')
assert source.is_file() and not target.exists()
with zipfile.ZipFile(source) as archive:
    entries = {item.filename: archive.read(item) for item in archive.infolist()}
manifest = json.loads(entries['manifest.json'])
assert manifest['schemaVersion'] == 52
database_entry = 'database/nanfeng-ai.snapshot'
original = next(x for x in manifest['entries'] if x['path'] == database_entry)
assert hashlib.sha256(entries[database_entry]).hexdigest() == original['sha256']
scratch = parent/('nfai-synthetic-backup-schema.'+uuid.uuid4().hex+'.sqlite3')
with scratch.open('xb') as output:
    output.write(entries[database_entry])
with sqlite3.connect(scratch) as db:
    assert db.execute('PRAGMA user_version').fetchone()[0] == 52
    assert db.execute('PRAGMA integrity_check').fetchall() == [('ok',)]
    db.execute('DROP TABLE desktop_ocr_attempts_v1')
    if version == 50:
        db.execute('ALTER TABLE desktop_app_settings DROP COLUMN memory_retrieval_enabled')
    db.execute(f'PRAGMA user_version={version}')
    names = db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name").fetchall()
    manifest['tableCounts'] = {name: db.execute('SELECT COUNT(*) FROM "'+name.replace('"','""')+'"').fetchone()[0] for (name,) in names}
    assert db.execute('PRAGMA integrity_check').fetchall() == [('ok',)]
entries[database_entry] = scratch.read_bytes()
original['bytes'] = len(entries[database_entry])
original['sha256'] = hashlib.sha256(entries[database_entry]).hexdigest()
manifest['schemaVersion'] = version
manifest.pop('manifestSha256')
manifest['manifestSha256'] = hashlib.sha256(json.dumps(manifest,ensure_ascii=False,sort_keys=True,separators=(',',':')).encode()).hexdigest()
entries['manifest.json'] = json.dumps(manifest,ensure_ascii=False,separators=(',',':')).encode()
with zipfile.ZipFile(target,'x',compression=zipfile.ZIP_DEFLATED) as archive:
    for name, content in entries.items():
        archive.writestr(name,content)
print(json.dumps({'fixtureKind':'SYNTHETIC_SCHEMA_RECONSTRUCTION','archivedSchema':version,'sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'backupSha256':hashlib.sha256(target.read_bytes()).hexdigest(),'scratch':str(scratch)}))
