"""Locate synthetic-only credential scanner matches without outputting cell contents."""
import hashlib,json,sqlite3,sys
from pathlib import Path
root=Path(sys.argv[1]).resolve(strict=True)
assert root.name.startswith('nanfeng-ai-windows-acceptance.')
assert json.loads((root/'.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))['identifier']=='com.nanzhufeng.ai.desktop.windowsacceptance'
c=sqlite3.connect((root/'workspace.sqlite3').as_uri()+'?mode=ro',uri=True)
out=[]
for (table,) in c.execute("select name from sqlite_master where type='table' and name not like 'sqlite_%'").fetchall():
    for column in c.execute('pragma table_info("'+table+'")').fetchall():
        name,kind=column[1:3]
        if kind.upper()!='TEXT' and kind:continue
        for (value,) in c.execute('select "'+name+'" from "'+table+'" where "'+name+'" is not null').fetchall():
            if not isinstance(value,str):continue
            lower=value.lower();compact=''.join(lower.split())
            markers=[m for m in ('sk-','xoxb-','ghp_','akia','aiza','authorization: bearer ','api_key=','apikey=','access_token=','refresh_token=','recovery_code=') if m in compact and (m!='sk-' or len(value)>=24)]
            if markers:
                out.append({'table':table,'column':name,'length':len(value),'sha256':hashlib.sha256(value.encode()).hexdigest(),'markers':markers,'skHasOnlyEmbeddedWord':all(i>0 and lower[i-1].isalnum() for i in range(len(lower)) if lower.startswith('sk-',i)) if 'sk-' in markers else None})
c.close()
with (root/'sensitive-synthetic-locations.json').open('x',encoding='utf-8') as f:json.dump(out,f,indent=2)
from collections import Counter
print(json.dumps({'matches':len(out),'groups':dict(Counter(r['table']+'/'+r['column'] for r in out)),'onlyEmbeddedSk':bool(out) and all(r['markers']==['sk-'] and r['skHasOnlyEmbeddedWord'] for r in out)}))
