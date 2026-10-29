"""Create a new driver once; preserve the earlier zero-reference helper."""
from pathlib import Path
p=Path(__file__).parent
s=(p/'verify-windows-large-backup-native.mjs').read_text(encoding='utf-8')
a=s.index('async function assets(');b=s.index('\nfunction sqlite(',a)
s=s[:a]+'''function assetBytes(area,count,index){
 const chunks=[Buffer.from(`SYNTHETIC-W15-REFERENCED-${area}-${count}-${index}\\n`)];
 for(let n=0;n<384;n++)chunks.push(Buffer.from(createHash('sha256').update(`${area}/${count}/${index}/${n}`).digest('hex')));
 return Buffer.concat(chunks);
}
async function assets(area){
 const records=[];
 for(const item of result.areas[area].libraries)for(const asset of item.assets){
  const bytes=await readFile(join(areaRoot(area),'assets',asset.sha256));
  assert.equal(bytes.length,asset.byteCount);assert.equal(sha(bytes),asset.sha256);
  records.push({id:asset.id,bytes:bytes.length,sha256:asset.sha256});
 }
 return records;
}
''' +s[b:]
s=s.replace("const mode='large-backup';","const mode='referenced-backup';")
s=s.replace("ex.conversations=Array.from", "const inputAssets=new Map(),identities=[];\n   ex.conversations=Array.from")
old="c.messages[1].id=c.id+'-a';c.messages[1].parentId=c.id+'-u';c.messages[1].blocks=[{kind:'TEXT',ordinal:0,text:i===0?'长回答完整备份验证。'.repeat(9000):'完整合成回答 '+i}];return c;"
new="""c.messages[1].id=c.id+'-a';c.messages[1].parentId=c.id+'-u';
    const data=assetBytes(area,count,i),digest=sha(data),entry='assets/'+digest;
    const asset={id:c.id+'-asset',entry,mimeType:'text/plain',displayName:`合成独立附件-${area}-${count}-${i}.txt`,byteCount:data.length,sha256:digest,classification:'NORMAL'};
    inputAssets.set(entry,data);identities.push(asset);
    c.messages[1].blocks=[{kind:'TEXT',ordinal:0,text:i===0?'长回答完整备份验证。'.repeat(9000):'完整合成回答 '+i},{kind:'ASSET_REF',ordinal:1,asset}];return c;"""
assert old in s;s=s.replace(old,new)
s=s.replace('[100,300,600]','[200,400,600]').replace('packageExchange(ex,new Map())','packageExchange(ex,inputAssets)')
s=s.replace("draftId:imported.exchange.conversations[1].id}","draftId:imported.exchange.conversations[1].id,assets:identities,input:path}")
s=s.replace("attachmentIds:[],modelId:null,toneOverride:null,webSearchOverride:null}});\n   item.draft", "attachmentIds:[identities[1].id],modelId:null,toneOverride:null,webSearchOverride:null}});\n   item.draft")
s=s.replace("result.areas[area].assets=await assets(area,true);check(area+' 1200 nonempty controlled files',result.areas[area].assets.length===1200);", "result.areas[area].assets=await assets(area);check(area+' 1200 independent referenced nonempty assets',result.areas[area].assets.length===1200&&new Set(result.areas[area].assets.map(a=>a.sha256)).size===1200);\n  result.areas[area].physicalBytes=result.areas[area].assets.reduce((n,a)=>n+a.bytes,0);console.log(JSON.stringify({phase:'IMPORTED',area,assets:1200,physicalBytes:result.areas[area].physicalBytes}));")
s=s.replace("const changed=join(areaRoot(area),'assets/w15-large-synthetic/synthetic-0.bin');", "const changed=join(areaRoot(area),'assets',item.assets[0].sha256);")
s=s.replace("'large-backup-evidence.json'","'referenced-backup-evidence.json'")
# Native semantic export must traverse the same existing owner too.
needle="result.areas[area].assets=await assets(area);"
insert="""for(const item of result.areas[area].libraries){
   const selectedPath=join(root,`synthetic-export-${area}-${item.count}.nfai-exchange`);
   item.exportArtifact=await timed(area+' exchange export '+item.count,()=>invoke(port,area,'export_desktop_workspace_to_selected_path',{workspaceId:item.id,selectedPath}));
   const exported=preflightPackage(await readFile(selectedPath));
   const read=await invoke(port,area,'read_desktop_workspace',{workspaceId:item.id});
   assert.deepEqual(exported.exchange.conversations,read.exchange.conversations);
   assert.equal(exported.entryCount,preflightPackage(await readFile(item.input)).entryCount);item.exportPath=selectedPath;
  }
  """
assert needle in s;s=s.replace(needle,insert+needle,1)
with (p/'verify-windows-referenced-backup-native.mjs').open('x',encoding='utf-8',newline='\n') as f:f.write(s)
