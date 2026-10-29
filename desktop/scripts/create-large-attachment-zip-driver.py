"""Generate a dedicated driver from preserved existing native helper functions."""
from pathlib import Path
p=Path(__file__).parent
search=(p/'verify-windows-large-attachment-search-native.mjs').read_text(encoding='utf-8')
history=(p/'verify-windows-large-history-zip-native.mjs').read_text(encoding='utf-8')
prefix=search[:search.index('try{\n let port=await start();result.root=root;')]
zipstage=history[history.index('async function zipStage('):history.index('\ntry {\n let port=await start();')]
body=r'''
async function validateImported(port,area,input,initial=false){
 const lib=result.areas[area],data=await invoke(port,area,'read_desktop_workspace',{workspaceId:lib.workspaceId});
 const rows=data.exchange.conversations.filter(c=>c.id!==lib.seedId);assert.equal(rows.length,576);
 const references=[];
 for(const source of input){const row=rows.find(c=>c.title===source.title);assert.ok(row,source.title);assert.equal(row.messages.length,2);
  for(const [index,message] of row.messages.entries()){
   const sourceMessage=Object.values(source.mapping)[index].message;
   assert.equal(message.blocks.filter(b=>b.kind==='TEXT').map(b=>b.text).join('\n'),sourceMessage.content.parts[0]);
   const blocks=message.blocks.filter(b=>b.kind==='ASSET_REF');assert.equal(blocks.length,1);
   const asset=blocks[0].asset,f=result.formats.find(f=>asset.displayName.endsWith('.'+f.ext));assert.ok(f);
   assert.equal(asset.sha256,f.sha256);assert.equal(asset.byteCount,f.byteCount);assert.equal(asset.mimeType,f.mime);
   assert.equal(asset.displayName,sourceMessage.metadata.attachments[0].name);
   references.push({workspaceId:lib.workspaceId,conversationId:row.id,messageId:message.id,attachmentId:asset.id,category:f.category,fileType:f.fileType});
  }
 }
 assert.equal(references.length,1152);assert.equal(new Set(references.map(tuple)).size,1152);
 if(initial){lib.references=references;lib.exchangeHash=hash(data.exchange);lib.conversationIds=rows.map(c=>c.id);await writeFile(join(root,'imported-'+area+'.json'),JSON.stringify(data.exchange),{flag:'wx'});}
 else{assert.equal(hash(data.exchange),lib.exchangeHash);assert.deepEqual(references,lib.references);assert.deepEqual(rows.map(c=>c.id),lib.conversationIds);}
 check(area+' full input text official message ownership and private asset identity',true);
 return data;
}
async function zipInput(path,rows){
 await writeFile(path+'.json',JSON.stringify(rows),{flag:'wx'});
 execFileSync('python',['-c',"import json,zipfile,sys,pathlib; rows=json.load(open(sys.argv[1],encoding='utf-8')); base=pathlib.Path(sys.argv[3]); z=zipfile.ZipFile(sys.argv[2],'x',compression=zipfile.ZIP_STORED); [z.writestr('conversations-'+str(i//100+1)+'.json',json.dumps(rows[i:i+100],ensure_ascii=False)) for i in range(0,len(rows),100)]; [z.write(base/('fixture.'+ext),'synthetic-'+ext+'.dat') for ext in ['txt','pdf','zip','docx','xlsx','pptx','png','mp4','wav']]; z.close()",path+'.json',path,join(root,'synthetic-attachments')],{timeout:30000});
}
const progress=async stage=>{result.stage=stage;await writeFile(join(root,'zip-attachment-progress.json'),JSON.stringify(result,null,2));console.log(JSON.stringify({stage,root,checks:result.checks.length}));};
try{
 let port=await start();result.root=root;
 await until(()=>evaluate(port,'Boolean(document.querySelector("#chat-composer")&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)'),'ready');
 const fixtureDir=join(root,'synthetic-attachments');await mkdir(fixtureDir);
 execFileSync('python',[join(desktopRoot,'scripts/create-large-search-attachments-synthetic.py'),fixtureDir],{encoding:'utf8',timeout:30000});
 const ffmpeg=join(receipt.mediaTools,'bin/ffmpeg.exe');
 execFileSync(ffmpeg,['-hide_banner','-loglevel','error','-f','lavfi','-i','color=c=green:s=128x96:d=1','-frames:v','1',join(fixtureDir,'fixture.png')],{timeout:30000});
 execFileSync(ffmpeg,['-hide_banner','-loglevel','error','-f','lavfi','-i','color=c=blue:s=128x96:d=1','-c:v','libopenh264','-pix_fmt','yuv420p',join(fixtureDir,'fixture.mp4')],{timeout:30000});
 execFileSync(ffmpeg,['-hide_banner','-loglevel','error','-f','lavfi','-i','sine=frequency=440:sample_rate=48000','-t','1','-c:a','pcm_s16le',join(fixtureDir,'fixture.wav')],{timeout:30000});
 result.formats=[];
 for(const [ext,mime,category] of formats){const bytes=await readFile(join(fixtureDir,'fixture.'+ext));result.formats.push({ext,mime,category,byteCount:bytes.length,sha256:sha(bytes),fileType:['xlsx','pptx','png','mp4','wav'].includes(ext)?'other':ext});}
 const {packageExchange,semanticHash}=await import('../../protocol/scripts/exchange-lib.mjs');
 const original=JSON.parse(await readFile(join(desktopRoot,'../protocol/fixtures/nfai.exchange.v1.golden.json'),'utf8'));
 for(const area of areas){
  await invoke(port,area,'ensure_desktop_local_workspace');
  const ex=structuredClone(original);ex.export.id='w14-zip-attachment-seed-'+area.toLowerCase();
  for(const c of ex.conversations)for(const m of c.messages)m.blocks=[{kind:'TEXT',ordinal:0,text:'W14 ZIP seed'}];ex.export.semanticHash=semanticHash(ex);
  const seed=join(root,area+'-seed.nfai-exchange');await writeFile(seed,packageExchange(ex,new Map()),{flag:'wx'});
  const pre=await invoke(port,area,'stage_preflight_selected_exchange',{selectedPath:seed});
  const imported=await invoke(port,area,'import_staged_exchange_as_new_workspace',{args:{stagingId:pre.stagingId,workspaceTitle:'W14 非空附件ZIP '+area}});
  const input=[];
  for(const f of result.formats)for(let i=0;i<64;i++){
   const id=`w14-${area}-${f.ext}-${i}`,u=id+'-u',a=id+'-a',attachment={id:'synthetic-'+f.ext,name:`${term}-${area}.${f.ext}`};
   const message=(role,mid)=>({id:mid,author:{role},content:{parts:[`${id} ${role} 正文\nUnicode Ω 😀\n代码 \`const n = ${i};\`\n末行`,{asset_pointer:'file-service://synthetic-'+f.ext}]},metadata:{attachments:[attachment]}});
   input.push({id,title:`历史 ${area} ${f.ext} ${i} 中文 😀`,create_time:1,update_time:2,mapping:{[u]:{parent:null,children:[a],message:message('user',u)},[a]:{parent:u,children:[],message:message('assistant',a)}}});
  }
  const lib={area,provider:'CHATGPT',expectedCount:576,workspaceId:imported.summary.id,seedId:imported.exchange.conversations[0].id,input:join(root,area+'-576-attachments.zip'),queries:[],ui:[],previews:[],taskIds:[]};result.areas[area]=lib;
  await zipInput(lib.input,input);
  const task=await timed(area+' official ZIP576',()=>zipStage(port,lib,lib.input));lib.taskIds.push(task.id);lib.ids=task.items.map(i=>i.conversationId);
  assert.equal(new Set(lib.ids).size,576);
  assert.deepEqual(task.officialAttachmentSummary,{referencedOccurrences:1152,recoverableUniqueAssets:9,missingUniqueAssets:0,fallbackNameUniqueAssets:0,restoredUniqueAssets:9,restoredOccurrences:1152});
  assert.equal(task.manualAssets.length,9);assert.ok(task.manualAssets.every(a=>a.status==='AUTO_LINKED'));
  await validateImported(port,area,input,true);await progress(area+' official ZIP imported');
  const replay=await zipStage(port,lib,lib.input);lib.taskIds.push(replay.id);assert.deepEqual(replay.items.map(i=>i.conversationId),lib.ids);
  await validateImported(port,area,input);check(area+' official ZIP replay conserved IDs text and occurrences',true);
  lib.latestTask=await invoke(port,area,'read_latest_p6k_zip_import_task',{workspaceId:lib.workspaceId});
  await invoke(port,area,'export_desktop_workspace_to_selected_path',{workspaceId:lib.workspaceId,selectedPath:join(root,area+'-complete.nfai-exchange')});
  for(const [category,count] of [['all',1152],['file',768],['image',128],['video',128],['audio',128]])await complete(port,area,category,count);
  check(area+' full native classified ownership tuples',true);await progress(area+' replay and export complete');
 }
 await installProjectionAccess(port);
 for(const area of areas){await selectLibraryProjection(port,area,result.areas[area].workspaceId);await openSearch(port);await fullUi(port,area,'all',1152);
  for(const category of ['image','video','audio','file'])await fullUi(port,area,category,category==='file'?768:128);
  await fullUi(port,area,'all',1152);for(const [format] of formats)await preview(port,area,format);await click(port,'[data-action="close-search"]');}
 await conserve(port);result.beforeCloseSqlite=sqlite();await normalClose(port);
 port=await start(true);await until(()=>evaluate(port,'Boolean(window.__TAURI__?.core?.invoke)'),'cold bridge');await conserve(port);
 for(const area of areas){const lib=result.areas[area],input=JSON.parse(await readFile(lib.input+'.json','utf8'));await validateImported(port,area,input);assert.deepEqual(await invoke(port,area,'read_latest_p6k_zip_import_task',{workspaceId:lib.workspaceId}),lib.latestTask);delete lib.latestTask;check(area+' cold exact task receipt',true);}
 await installProjectionAccess(port);
 for(const area of areas){for(const [category,count] of [['all',1152],['file',768],['image',128],['video',128],['audio',128]])await complete(port,area,category,count);
  await selectLibraryProjection(port,area,result.areas[area].workspaceId);await openSearch(port);await fullUi(port,area,'all',1152);for(const [format] of formats)await preview(port,area,format,true);await click(port,'[data-action="close-search"]');}
 await conserve(port);await normalClose(port);result.finalSqlite=sqlite(true);result.pass=true;
}catch(error){result.error=String(error.stack||error);process.exitCode=1;}
finally{if(launch){result.forcedCleanup=true;try{stop();}catch(error){result.cleanupError=String(error);}}result.pids=pids;if(root)await writeFile(join(root,'large-attachment-zip-evidence.json'),JSON.stringify(result,null,2));}
console.log(JSON.stringify({root,pass:result.pass,error:result.error?.slice(0,1600)}));
'''
target=p/'verify-windows-large-attachment-zip-native.mjs'
with target.open('x',encoding='utf-8') as f:f.write(prefix+zipstage+body)
