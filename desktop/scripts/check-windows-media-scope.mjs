import assert from 'node:assert/strict';

// Only run after the caller verifies the hidden native process and synthetic root.
export async function checkWindowsMediaScope(page, {workspaces,imageId,videoId,reopen=false}) {
  page.setDefaultTimeout(12000);
  await page.locator('#chat-search, #full-search-input').waitFor();
  const readPositions=()=>page.evaluate(async({workspaces,videoId})=>{
    const result=[];for(const workspaceId of workspaces){const p=await __TAURI__.core.invoke('read_desktop_video_preview',{args:{workspaceId,attachmentId:videoId}});result.push(p.positionMillis);}return result;
  },{workspaces,videoId});
  const restoredPositions=reopen?await readPositions():null;
  if(reopen)for(const position of restoredPositions)assert.ok(position>=350&&position<=450);
  if(await page.locator('#chat-search').count())await page.locator('#chat-search').click();
  await page.locator('#full-search-input').fill('scope-no-match-'+Date.now());
  await page.waitForFunction(()=>document.querySelector('.desktop-search-state:not([role="status"])')&&!document.querySelector('[data-search-entry-id]'));
  await page.locator('#full-search-input').fill('Media scope');
  await page.waitForFunction(()=>document.querySelector('[data-search-entry-id]')&&!document.querySelector('.desktop-search-spinner'));
  const facts=[];
  const check=async(kind,id,workspaceId,index)=>{
    const card=page.locator(`[data-${kind}-thumbnail="${id}"][data-thumbnail-workspace-id="${workspaceId}"]`).first();
    await card.evaluate(element=>element.scrollIntoView({block:'center'}));
    const img=card.locator('img').first(); await img.waitFor();
    await img.evaluate(async image=>{if(!image.complete)await new Promise((resolve,reject)=>{image.onload=resolve;image.onerror=reject;});});
    const fact=await img.evaluate(image=>{
      const canvas=document.createElement('canvas');canvas.width=1;canvas.height=1;
      const ctx=canvas.getContext('2d');ctx.drawImage(image,image.naturalWidth/2,image.naturalHeight/2,1,1,0,0,1,1);
      return {width:image.naturalWidth,height:image.naturalHeight,pixel:[...ctx.getImageData(0,0,1,1).data],png:image.src.startsWith('data:image/png;base64,')};
    });
    assert.equal(fact.png,true);
    const [r,,b]=fact.pixel;assert.ok(index===0?r-b>120:b-r>120,`${kind} wrong workspace colour: ${JSON.stringify(fact)}`);
    assert.ok(index===0?fact.width>fact.height:fact.height>fact.width);
    if(kind==='video')assert.ok(Math.max(fact.width,fact.height)<=640);
    facts.push({kind,workspace:index,...fact});return card;
  };
  for(const [index,workspaceId] of workspaces.entries()){
    await check('image',imageId,workspaceId,index);await check('video',videoId,workspaceId,index);
  }
  const playback=[];
  for(const [index,workspaceId] of workspaces.entries()){
    const videoCard=page.locator(`[data-video-thumbnail="${videoId}"][data-thumbnail-workspace-id="${workspaceId}"]`);
    await videoCard.locator('[data-action="open-search-attachment"]').click();
    await page.waitForFunction(()=>{const v=document.querySelector('[data-video-preview]');return v&&v.readyState>=2&&v.videoWidth>0;});
    const video=page.locator('[data-video-preview]');
    const detail=await video.evaluate(async v=>{await v.play();v.pause();v.currentTime=0.4;await new Promise(resolve=>v.addEventListener('seeked',resolve,{once:true}));return {width:v.videoWidth,height:v.videoHeight,duration:v.duration,seek:v.currentTime,error:v.error?.code||null};});
    assert.equal(detail.error,null);assert.ok(Math.abs(detail.seek-0.4)<0.1);assert.ok(detail.duration>=0.9);
    playback.push({workspace:index,...detail});await page.locator('[data-action="close-video-preview"]').click();
    await page.locator(`[data-action="open-search-result"][data-workspace-id="${workspaceId}"]`).first().click();
    await check('image',imageId,workspaceId,index);await check('video',videoId,workspaceId,index);
    await page.locator('[data-action="return-to-search"]').click();
  }
  const positions=await readPositions();for(const position of positions)assert.ok(position>=350&&position<=450);
  return {facts,playback,positions,restoredPositions,reopen,searchToConversationAndBack:true,source:'real WebView2 UI and native owners; owned synthetic assets, not picker/import'};
}
