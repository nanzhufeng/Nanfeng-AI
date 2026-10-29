"""Audit only marked synthetic NORMAL checkpoint copies against complete baselines."""
import json,pathlib,sqlite3,sys
from backup_real_switch_baseline import tables,tree,CONTROLLED
root=pathlib.Path(sys.argv[1]).resolve(strict=True);assert root.is_relative_to(pathlib.Path('D:/CodexTemp').resolve())
assert json.loads((root/'.nanfeng-windows-acceptance.json').read_text(encoding='utf-8'))['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
config=json.loads((root/'normal-checkpoint-config.json').read_text(encoding='utf-8'));e=json.loads((root/'normal-checkpoint-evidence.json').read_text(encoding='utf-8'));assert e['pass'] and not e.get('forcedCleanup')
baseline=json.loads(pathlib.Path(config['baseline']).read_text(encoding='utf-8'));valid=config['fault']=='valid';expected=baseline['databases'] if valid else {k:{'tables':v} for k,v in config['beforeDatabases'].items()}
out={'root':str(root),'fault':config['fault'],'area':config['area'],'databases':{},'controlledFiles':{},'allOk':True}
for key,before in expected.items():
 area,rel=key.split('/',1);base=root if area in ('CHAT','SHARED') else root/'work-area';actual=tables(base/rel);assert set(actual)==set(before['tables']);changed=[t for t in actual if actual[t]!=before['tables'][t]];details={}
 if not valid:assert not changed,(key,changed)
 else:
  prior=sqlite3.connect(pathlib.Path(baseline['databases'][key]['snapshot']).as_uri()+'?mode=ro',uri=True);current=sqlite3.connect((base/rel).as_uri()+'?mode=ro',uri=True)
  for table in changed:
   assert table in ('desktop_attachment_assets','desktop_background_runtime_v1','desktop_conversation_read_markers_v1'),(key,table)
   excluded={'desktop_attachment_assets':{'last_referenced_at_ms','last_unreferenced_at_ms','reference_count'},'desktop_background_runtime_v1':{'safe_code','label','updated_at_ms'}}.get(table)
   if excluded:
    columns=[r[1] for r in prior.execute('pragma table_info("'+table+'")') if r[1] not in excluded];q='select '+','.join('"'+c+'"' for c in columns)+' from "'+table+'"';assert sorted(prior.execute(q).fetchall(),key=repr)==sorted(current.execute(q).fetchall(),key=repr)
   details[table]={'beforeRows':before['tables'][table]['count'],'afterRows':actual[table]['count'],'explicitDerivedColumns':sorted(excluded) if excluded else 'UI read markers created during actual opening; entire marker table recorded separately'}
  if rel=='workspace.sqlite3':
   for sha,count in current.execute('select sha256,reference_count from desktop_attachment_assets'):
    refs=current.execute('select count(*) from desktop_conversation_attachments where sha256=?',(sha,)).fetchone()[0]
    drafts=current.execute('select count(*) from desktop_conversation_draft_attachments_v1 d join desktop_conversation_attachments a on a.workspace_id=d.workspace_id and a.attachment_id=d.attachment_id where a.sha256=?',(sha,)).fetchone()[0]
    assert count==refs+drafts,(area,sha,count,refs,drafts)
  prior.close();current.close()
 out['databases'][key]={'tables':len(actual),'changes':details,'allOtherTablesExact':True}
expected_files=baseline['controlledFiles'] if valid else config['beforeControlledFiles']
for area in ('CHAT','WORK'):
 base=root if area=='CHAT' else root/'work-area';actual={}
 for local,prefix in CONTROLLED.items():actual.update(tree(base/local,prefix))
 assert actual==expected_files[area],area;out['controlledFiles'][area]={'exact':True,'entries':len(actual)}
(root/'normal-checkpoint-independent-final.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n',encoding='utf-8');print(json.dumps({'root':str(root),'fault':config['fault'],'area':config['area'],'allOk':True,'tables':sum(v['tables'] for v in out['databases'].values()),'fileEntries':{a:v['entries'] for a,v in out['controlledFiles'].items()}}))
