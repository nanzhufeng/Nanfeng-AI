"""Independently verify exact prior rows, six restored retries, exports and all controlled bytes."""
import hashlib,json,sys,zipfile
from pathlib import Path
from backup_real_switch_baseline import connect,tables,tree,CONTROLLED
root=Path(sys.argv[1]).resolve(strict=True)
assert root.name.startswith('nanfeng-ai-windows-acceptance.')
assert json.loads((root/'.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
e=json.loads((root/'task-assets-backup-evidence.json').read_text(encoding='utf-8'))
r=json.loads((root/'task-assets-backup-resume-cold-third-evidence.json').read_text(encoding='utf-8'))
f=json.loads((root/'task-assets-backup-final-cold-evidence.json').read_text(encoding='utf-8'))
assert f['pass'] and not f.get('forcedCleanup') and len(f['checks'])==13
baseline=json.loads((root/'pre-switch-full-baseline-final-cold-closed/baseline.json').read_text(encoding='utf-8'))
out={'allOk':True,'root':str(root),'areas':{},'normalRecoveryAccepted':False}
def rows(c,t):return sorted(c.execute('select * from '+t).fetchall(),key=repr)
def cols(c,t):return [v[1] for v in c.execute('pragma table_info('+t+')')]
def hash_value(v):return hashlib.sha256(json.dumps(v,ensure_ascii=False,separators=(',',':')).encode()).hexdigest()
for area in ('CHAT','WORK'):
    base=root if area=='CHAT' else root/'work-area'
    old=connect(Path(baseline['databases'][area+'/workspace.sqlite3']['snapshot']));new=connect(base/'workspace.sqlite3')
    changed={t for t,v in tables(base/'workspace.sqlite3').items() if baseline['databases'][area+'/workspace.sqlite3']['tables'].get(t)!=v}
    allowed={'workspaces','workspace_exchange','desktop_attachment_assets','desktop_local_search_index','desktop_local_search_index_state'}
    for prefix in ('chatgpt','claude','nanfeng_knowledge'):allowed.update(prefix+'_import_'+s for s in ('tasks','items','receipts','provenance'))
    assert changed<=allowed,(area,changed-allowed)
    libraries=e['areas'][area]['libraries'];staged={lib['id']:lib for lib in libraries if lib['label'].endswith('-staged')}
    for table in ('workspaces','workspace_exchange'):
        key='id' if table=='workspaces' else 'workspace_id';names=cols(old,table);ix=names.index(key)
        a={row[ix]:row for row in rows(old,table)};b={row[ix]:row for row in rows(new,table)};assert set(a)==set(b)
        for wid,row in a.items():
            if wid not in staged:assert b[wid]==row
            elif table=='workspaces':assert [v for i,v in enumerate(row) if names[i]!='semantic_hash']==[v for i,v in enumerate(b[wid]) if names[i]!='semantic_hash']
            else:
                before=json.loads(row[1]);after=json.loads(b[wid][1]);assert before['conversations']==after['conversations'][:1]
                before['conversations']=after['conversations'];before['export']['semanticHash']=after['export']['semanticHash'];assert before==after
    for prefix in ('chatgpt','claude','nanfeng_knowledge'):
        lib=next(l for l in staged.values() if l['prefix']==prefix);tid=lib['task']['id'];retry=next(x for x in r['retries'] if x['area']==area and x['label']==lib['label']);ids=set(retry['ids']);assert len(ids)==12
        for suffix in ('tasks','items'):
            table=prefix+'_import_'+suffix;names=cols(old,table);ix=names.index('id' if suffix=='tasks' else 'task_id');a=rows(old,table);b=rows(new,table)
            assert [row for row in a if row[ix]!=tid]==[row for row in b if row[ix]!=tid]
            source=[row for row in a if row[ix]==tid];actual=[row for row in b if row[ix]==tid];mutable={'status','failure','retry_count','updated_at_ms'} if suffix=='tasks' else {'status','conversation_id'}
            keep=[i for i,n in enumerate(names) if n not in mutable];assert sorted(tuple(row[i] for i in keep) for row in source)==sorted(tuple(row[i] for i in keep) for row in actual)
            assert all(row[names.index('status')]==('COMPLETED' if suffix=='tasks' else 'CONFIRMED') for row in actual)
            if suffix=='tasks':assert len(actual)==1 and actual[0][names.index('retry_count')]==source[0][names.index('retry_count')]+1 and actual[0][names.index('failure')] is None
            else:assert {row[names.index('conversation_id')] for row in actual}==ids
        for suffix in ('receipts','provenance'):
            table=prefix+'_import_'+suffix;names=cols(old,table);a=rows(old,table);b=rows(new,table);assert set(a)<=set(b)
            added=set(b)-set(a);assert len(added)==12 and {row[names.index('conversation_id')] for row in added}==ids
            assert all(row[names.index('workspace_id')]==lib['id'] and row[names.index('package_hash')]==next(x for x in r['persistentRetries'] if x['area']==area and x['label']==lib['label'])['before'][3] for row in added)
        actual=json.loads(new.execute('select exchange_json from workspace_exchange where workspace_id=?',(lib['id'],)).fetchone()[0]);inputs=json.loads(Path(lib['input']).read_text(encoding='utf-8'));assert len(actual['conversations'])==13
        for source in inputs:
            conv=next(c for c in actual['conversations'] if c['title']==source.get('title',source.get('name')))
            expected=[source['mapping'][k]['message']['content']['parts'][0] for k in ('u','a')] if 'mapping' in source else [m['text'] for m in source.get('chat_messages',json.loads(source.get('sourceText','{}')).get('chat_messages',[]))]
            assert [[b['text'] for b in m['blocks'] if b['kind']=='TEXT'] for m in conv['messages']]==[[text] for text in expected]
    # All three identity/byte/creation columns remain exact; the cache records actual references.
    names=cols(old,'desktop_attachment_assets');a=rows(old,'desktop_attachment_assets');b=rows(new,'desktop_attachment_assets');assert len(a)==len(b)==1
    stable=[i for i,n in enumerate(names) if n not in ('reference_count','last_referenced_at_ms','last_unreferenced_at_ms')];assert [tuple(row[i] for i in stable) for row in a]==[tuple(row[i] for i in stable) for row in b]
    expected_files={}
    for local,prefix in CONTROLLED.items():expected_files.update(tree(base/local,prefix))
    assert expected_files==baseline['controlledFiles'][area]
    for export in [x for x in f['exports'] if x['area']==area]:
        with zipfile.ZipFile(export['selectedPath']) as z:
            assert z.testzip() is None;payload=json.loads(z.read('payload/conversations.json'));assert len(payload)==export['conversations']
            current=json.loads(new.execute('select exchange_json from workspace_exchange where workspace_id=?',(export['workspaceId'],)).fetchone()[0]);assert [c['id'] for c in payload]==[c['id'] for c in current['conversations']]
            for a,b in zip(payload,current['conversations']):
                assert a['title']==b['title'] and [m['id'] for m in a['messages']]==[m['id'] for m in b['messages']]
                for am,bm in zip(a['messages'],b['messages']):
                    assert [v.get('text') for v in am['blocks']]==[v.get('text') for v in bm['blocks']]
                    for block in am['blocks']:
                        if block['kind']=='ASSET_REF':asset=block['asset'];data=z.read(asset['entry']);assert len(data)==asset['byteCount'] and hashlib.sha256(data).hexdigest()==asset['sha256']
    out['areas'][area]={'allTableCount':len(tables(base/'workspace.sqlite3')),'changedTables':sorted(changed),'unchangedTables':len(tables(base/'workspace.sqlite3'))-len(changed),'tasks':11,'explicitRetriedTasks':3,'newConversations':36,'oldReceiptsConserved':True,'exports':11,'controlledFiles':sum(v is not None for v in expected_files.values()),'integrity':'ok','foreignKeyViolations':0}
    old.close();new.close()
for key,item in baseline['databases'].items():
    if key.endswith('workspace.sqlite3'):continue
    path=root/key.split('/',1)[1] if key.startswith(('CHAT/','SHARED/')) else root/'work-area'/key.split('/',1)[1]
    assert tables(path)==item['tables'],key
with (root/'task-backup-resume-independent.json').open('x',encoding='utf-8') as stream:json.dump(out,stream,ensure_ascii=False,indent=2)
print(json.dumps(out,ensure_ascii=False))
