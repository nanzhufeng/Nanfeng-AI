import test from 'node:test';
import assert from 'node:assert/strict';
import { renderChatFirstShell } from '../src/chat-shell.mjs';
import { renderDesktopSearchPage } from '../src/desktop-search-page.mjs';
import { attachmentPreviewKey } from '../src/attachment-preview-key.mjs';

const image = { id:'shared-image',displayName:'scope.png',mimeType:'image/png',byteCount:100 };
const video = { id:'shared-video',displayName:'scope.mp4',mimeType:'video/mp4',byteCount:100 };
const secondImage = { ...image,id:'other-image' };
const imageThumbnails={},videoThumbnails={};
for (const workspaceId of ['a','b']) {
  imageThumbnails[attachmentPreviewKey(workspaceId,image.id)]={dataUrl:`data:image/png;base64,${workspaceId}-IMAGE`};
  imageThumbnails[attachmentPreviewKey(workspaceId,secondImage.id)]={dataUrl:`data:image/png;base64,${workspaceId}-SECOND`};
  videoThumbnails[attachmentPreviewKey(workspaceId,video.id)]={dataUrl:`data:image/png;base64,${workspaceId}-VIDEO`};
}
test('search image and video cards resolve the workspace owner before reading cached pixels',()=>{
  for(const workspaceId of ['a','b']) {
    const hits=[image,video].map(asset=>({workspaceId,attachmentId:asset.id,entryId:workspaceId+asset.id,...asset,contentKind:asset===image?'IMAGE':'VIDEO'}));
    const html=renderDesktopSearchPage({page:{hits,textCount:0,attachmentCount:2},thumbnails:imageThumbnails,videoThumbnails});
    assert.ok(html.includes(`${workspaceId}-IMAGE`));assert.ok(html.includes(`${workspaceId}-VIDEO`));
    assert.ok(!html.includes(`${workspaceId==='a'?'b':'a'}-IMAGE`));assert.ok(!html.includes(`${workspaceId==='a'?'b':'a'}-VIDEO`));
  }
});
test('chat, composer and generated-image gallery share scoped pixels and explicit card ownership',()=>{
  const conversation={id:'same-conversation',title:'Scope',messages:[
    {id:'user',role:'user',blocks:[image,video].map(asset=>({kind:'ASSET_REF',asset}))},
    {id:'assistant',role:'assistant',blocks:[image,secondImage].map(asset=>({kind:'ASSET_REF',asset}))},
  ]};
  for(const workspaceId of ['a','b']) {
    const html=renderChatFirstShell({data:{summary:{id:workspaceId},exchange:{conversations:[conversation]}},selectedConversationId:conversation.id,native:true,pane:'chat',composerAttachments:[image,video],imageThumbnails,videoThumbnails});
    for(const suffix of ['IMAGE','VIDEO','SECOND']) {
      assert.ok(html.includes(`${workspaceId}-${suffix}`));assert.ok(!html.includes(`${workspaceId==='a'?'b':'a'}-${suffix}`));
    }
    for(const match of html.matchAll(/data-(?:image|video)-thumbnail="[^"]+"([^>]+)/g)) assert.ok(match[1].includes(`data-thumbnail-workspace-id="${workspaceId}"`));
    assert.ok(html.includes('chat-assistant-image-gallery-thumb'));
    assert.ok(html.includes('chat-composer-image-open'));
  }
});
