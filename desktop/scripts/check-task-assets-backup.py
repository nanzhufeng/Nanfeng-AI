"""Read-only independent full-table and controlled-byte audit of synthetic native backups."""
import hashlib
import json
from pathlib import Path
import sqlite3
import sys
import uuid
import zipfile
from backup_real_switch_baseline import CONTROLLED, connect, snapshot, tables, tree

mode, root_arg, label = sys.argv[1:4]
root = Path(root_arg).resolve(strict=True)
assert root.name.startswith('nanfeng-ai-windows-acceptance.')
assert str(root).lower().startswith('d:\\codextemp\\')
assert json.loads((root/'.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))['identifier'] == 'com.nanzhufeng.ai.desktop.windowsacceptance'
assert label.replace('-', '').isalnum()
if mode == 'snapshot':
    path = snapshot(root, label)
    data = json.loads(Path(path).read_text(encoding='utf-8'))
    print(json.dumps({'path':path, 'databases':len(data['databases']), 'tables':sum(len(d['tables']) for d in data['databases'].values()), 'files':{a:sum(v is not None for v in files.values()) for a,files in data['controlledFiles'].items()}}))
    sys.exit(0)
assert mode == 'compare'
area, package_arg = sys.argv[4:6]
allow_maintenance = sys.argv[6:] == ['--allow-maintenance']
assert not sys.argv[6:] or allow_maintenance
assert area in ('CHAT','WORK')
baseline = json.loads((root/('pre-switch-full-baseline-'+label)/'baseline.json').read_text(encoding='utf-8'))
pre_export = json.loads((root/'pre-switch-full-baseline-before-export'/'baseline.json').read_text(encoding='utf-8'))
base = root if area == 'CHAT' else root/'work-area'
excluded = {'desktop_attachment_staging','desktop_app_settings','desktop_background_runtime_v1','desktop_conversation_read_markers_v1','desktop_audio_preview_positions','desktop_local_search_history','desktop_local_search_index','desktop_local_search_index_state','desktop_pdf_preview_positions','desktop_provider_settings','desktop_video_preview_positions','p6g_catalog','p6g_conversation_override','p6g_global_default','p6g_route_metadata','sync_account_metadata','sync_intents','window_layout'}
preserved = {'desktop_app_settings','desktop_background_runtime_v1','desktop_conversation_read_markers_v1','desktop_provider_settings','p6g_catalog','p6g_global_default','sync_account_metadata','sync_intents','window_layout'}
task_tables = {'chatgpt_import_tasks','claude_import_tasks','nanfeng_knowledge_import_tasks','p6k_zip_import_tasks'}
terminal = {'COMPLETED','PARTIALLY_COMPLETED','FAILED','CANCELLED','SKIPPED'}
def rows(c, name):
    return sorted(c.execute('select * from "'+name.replace('"','""')+'"').fetchall(),key=repr)
def digest(b): return hashlib.sha256(b).hexdigest()
output={'area':area,'allOk':True,'tableComparisons':{},'deviceTables':{},'sanitizedTasks':{},'otherAreaConserved':{},'controlledRootCounts':{},'maintenanceDifferences':[],'allRawTablesExact':True}
def compare_maintenance(old,new,old_rows,new_rows,selected):
    assert allow_maintenance
    cols=[r[1] for r in old.execute('pragma table_info(desktop_attachment_assets)')]
    assert cols==[r[1] for r in new.execute('pragma table_info(desktop_attachment_assets)')]
    old_rows=sorted(old_rows,key=lambda r:r[cols.index('sha256')]);new_rows=sorted(new_rows,key=lambda r:r[cols.index('sha256')])
    assert len(old_rows)==len(new_rows)==1
    mutable={'last_referenced_at_ms','last_unreferenced_at_ms','reference_count'}
    stable=[i for i,col in enumerate(cols) if col not in mutable]
    assert [tuple(r[i] for i in stable) for r in old_rows]==[tuple(r[i] for i in stable) for r in new_rows]
    refs={}
    for (encoded,) in new.execute('select exchange_json from workspace_exchange').fetchall():
        ex=json.loads(encoded)
        for conv in ex['conversations']:
            for msg in conv['messages']:
                for block in msg['blocks']:
                    if block['kind']=='ASSET_REF':
                        h=block['asset']['sha256'];refs[h]=refs.get(h,0)+1
    assert new.execute('select count(*) from desktop_temporary_attachments').fetchone()[0]==0
    assert new.execute('select count(*) from desktop_conversation_draft_attachments_v1').fetchone()[0]==0
    assert new.execute('select count(*) from desktop_transcription_tasks').fetchone()[0]==0
    for before_row,after_row in zip(old_rows,new_rows):
        h=after_row[cols.index('sha256')];assert after_row[cols.index('reference_count')]==refs[h]==36
        time=after_row[cols.index('last_referenced_at_ms')]
        assert time==after_row[cols.index('last_unreferenced_at_ms')] and time>=after_row[cols.index('created_at_ms')]
        prior=before_row[cols.index('last_referenced_at_ms')];assert prior is None or time>=prior
    changed={col:[old_rows[0][i],new_rows[0][i]] for i,col in enumerate(cols) if old_rows[0][i]!=new_rows[0][i]}
    assert set(changed)<=mutable
    output['allRawTablesExact']=False
    output['maintenanceDifferences'].append({'area':selected,'table':'desktop_attachment_assets','rows':1,'changes':changed,'actualReferences':36,'identityBytesCreatedAtExact':True})
with zipfile.ZipFile(package_arg) as z:
    assert z.testzip() is None
    manifest=json.loads(z.read('manifest.json'))
    entries=manifest['entries'];names=[e['path'] for e in entries]
    assert len(names)==len(set(names)) and set(z.namelist())==set(names)|{'manifest.json'}
    for entry in entries:
        b=z.read(entry['path']);assert len(b)==entry['bytes'] and digest(b)==entry['sha256'],entry['path']
    inspection=root/('task-backup-inspection-'+area+'-'+uuid.uuid4().hex);inspection.mkdir()
    path=inspection/'workspace.snapshot';path.write_bytes(z.read('database/nanfeng-ai.snapshot'))
    before=connect(Path(pre_export['databases'][area+'/workspace.sqlite3']['snapshot']))
    packaged=connect(path);current=connect(base/'workspace.sqlite3')
    actual_counts={t:packaged.execute('select count(*) from "'+t+'"').fetchone()[0] for t in manifest['tableCounts']}
    assert actual_counts==manifest['tableCounts']
    for table in tables(path):
        source=rows(before,table);expected=rows(packaged,table);actual=rows(current,table)
        if table in excluded:
            assert expected==[],(table,'device state exported')
            if table in preserved:
                c=connect(Path(baseline['databases'][area+'/workspace.sqlite3']['snapshot']));assert actual==rows(c,table),(table,'device preservation');c.close()
            output['deviceTables'][table]={'packaged':len(expected),'restored':len(actual),'preserved':table in preserved}
            continue
        if table in task_tables:
            cols=[r[1] for r in before.execute('pragma table_info("'+table+'")')];status=cols.index('status');failure=cols.index('failure') if 'failure' in cols else None
            sanitized=[];conversions=0
            for r in source:
                r=list(r)
                if r[status] not in terminal:
                    r[status]='FAILED';conversions+=1
                    if failure is not None:r[failure]='INTERRUPTED'
                sanitized.append(tuple(r))
            assert expected==sorted(sanitized,key=repr),(table,'snapshot sanitizer mismatch')
            assert len(expected)>0,(table,'empty task coverage')
            output['sanitizedTasks'][table]={'rows':len(expected),'converted':conversions,'statuses':packaged.execute('select status,count(*) from "'+table+'" group by status').fetchall()}
        else:
            assert expected==source,(table,'unexpected snapshot alteration')
        if actual!=expected and table=='desktop_attachment_assets' and allow_maintenance:
            compare_maintenance(packaged,current,expected,actual,area)
        else:assert actual==expected,(area,table,'restore row mismatch')
        output['tableComparisons'][table]=len(actual)
    before.close();packaged.close();current.close()
    files={}
    for local,prefix in CONTROLLED.items():
        files.update({k:v for k,v in tree(base/local,prefix).items() if v is not None})
        source_files={k:v for k,v in pre_export['controlledFiles'][area].items() if v is not None and k.startswith(prefix+'/')}
        assert source_files,(area,local,'empty controlled root')
        output['controlledRootCounts'][local]=len(source_files)
    assert files=={k:v for k,v in pre_export['controlledFiles'][area].items() if v is not None},'restored full file inventory'
    packaged_files={entry['path'][7:]:entry['sha256'] for entry in entries if entry['path'].startswith('assets/')}
    assert packaged_files==files,'manifest asset inventory'
    output['manifest']={'entryCount':len(entries),'scope':manifest['scope'],'excluded':manifest['excluded'],'tableCounts':actual_counts,'fileCount':len(files),'fileBytes':sum(e['bytes'] for e in entries if e['path'].startswith('assets/'))}
    if 'database/desktop-usage-ledger-v1.snapshot' in names:
        ledger=inspection/'ledger.snapshot';ledger.write_bytes(z.read('database/desktop-usage-ledger-v1.snapshot'))
        assert tables(ledger)==tables(base/'usage-ledger/usage-ledger-v1.sqlite3'),'ledger restore'
other='WORK' if area=='CHAT' else 'CHAT'
other_base=root if other=='CHAT' else root/'work-area'
for key,item in baseline['databases'].items():
    if key.startswith(other+'/') or key.startswith('SHARED/'):
        actual_path=(other_base/key.split('/',1)[1]) if not key.startswith('SHARED/') else root/'app-configuration.sqlite3'
        actual_tables=tables(actual_path)
        differences=[t for t in set(actual_tables)|set(item['tables']) if actual_tables.get(t)!=item['tables'].get(t)]
        if differences:
            assert key==other+'/workspace.sqlite3' and differences==['desktop_attachment_assets'],(key,differences)
            old=connect(Path(item['snapshot']));new=connect(actual_path)
            compare_maintenance(old,new,rows(old,'desktop_attachment_assets'),rows(new,'desktop_attachment_assets'),other)
            old.close();new.close()
        output['otherAreaConserved'][key]=len(item['tables'])
current_tree={}
for local,prefix in CONTROLLED.items():current_tree.update(tree(other_base/local,prefix))
assert current_tree==baseline['controlledFiles'][other],'other full directory/file inventory'
path=root/('task-backup-compare-'+label+'-'+area+'-'+uuid.uuid4().hex+'.json')
with path.open('x',encoding='utf-8') as f:json.dump(output,f,ensure_ascii=False,indent=2)
print(json.dumps({'path':str(path),'area':area,'allOk':True,'tables':len(output['tableComparisons']),'tasks':output['sanitizedTasks'],'controlledRoots':output['controlledRootCounts'],'manifestEntries':len(entries)}))
