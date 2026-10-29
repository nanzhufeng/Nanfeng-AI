import assert from 'node:assert/strict';
import http from 'node:http';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';

const cdpPort = Number(process.argv[2]);
const servicePort = Number(process.argv[3]);
assert.ok(Number.isInteger(cdpPort) && cdpPort >= 1024 && cdpPort <= 65535);
assert.ok(Number.isInteger(servicePort) && servicePort >= 1024 && servicePort <= 65535);
assert.notEqual(cdpPort, servicePort);

let inventoryMode = 'empty';
let heldInventoryResponse = null;
let tokenIdentity = 'A';
let heldRefreshResponse = null;
const paths = [];
const fixtureAccess = 'nfai-synthetic-windows-access-only';
const fixtureRefresh = 'nfai-synthetic-windows-refresh-only';
const fixtureAccessB = 'nfai-synthetic-windows-access-other-account';
const fixtureRefreshB = 'nfai-synthetic-windows-refresh-other-account';
const areaFixtures = JSON.parse(await readFile(new URL('../../protocol/fixtures/data-area-sync-v1.json', import.meta.url), 'utf8'))
  .filter(item => item.platform === 'DESKTOP');
assert.equal(areaFixtures.length, 2);
const sha256 = value => createHash('sha256').update(value).digest('hex');
const canonical = value => Array.isArray(value)
  ? `[${value.map(canonical).join(',')}]`
  : value && typeof value === 'object'
    ? `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${canonical(value[key])}`).join(',')}}`
    : JSON.stringify(value);
function directEnvelope(fixture) {
  const identity = fixture.area === 'WORK' ? `WORK:${fixture.conversationId}` : fixture.conversationId;
  assert.equal(fixture.documentId, `conversation-${sha256(identity).slice(0, 40)}`);
  const payload = {
    format: 'nfai.sync.payload', protocolVersion: 1, schemaVersion: 1,
    appId: 'com.nanzhufeng.ai', documentId: fixture.documentId, revision: 1,
    records: [{ kind: 'conversation', id: fixture.conversationId, revision: 1, classification: 'NORMAL', content: fixture.content }],
  };
  const bytes = Buffer.from(canonical(payload));
  return {
    format: 'nfai.sync.direct', protocolVersion: 1, schemaVersion: 1,
    appId: payload.appId, documentId: payload.documentId, revision: 1,
    payloadHash: sha256(bytes), payloadByteCount: bytes.length, payload,
  };
}
const validDocuments = areaFixtures.map(fixture => ({ envelope: directEnvelope(fixture) }));
const server = http.createServer(async (request, response) => {
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  const body = Buffer.concat(chunks).toString('utf8');
  const path = request.url?.split('?')[0];
  paths.push(path);
  let status = 200;
  let value;
  if (path === '/auth/v1/token' && request.method === 'POST') {
    const payload = JSON.parse(body);
    if (process.argv[4] === 'refreshRace' && request.url?.includes('grant_type=refresh_token')) {
      assert.equal(payload.refresh_token, fixtureRefresh);
      heldRefreshResponse = response;
      return;
    }
    assert.equal(payload.auth_code, 'nfai-synthetic-windows-account-code');
    assert.ok(typeof payload.code_verifier === 'string' && payload.code_verifier.length > 40);
    assert.equal(request.headers.apikey, 'nfai-synthetic-windows-account');
    value = {
      access_token: tokenIdentity === 'A' ? fixtureAccess : fixtureAccessB,
      refresh_token: tokenIdentity === 'A' ? fixtureRefresh : fixtureRefreshB,
      expires_in: process.argv[4] === 'refreshRace' ? 60 : 3600,
      user: tokenIdentity === 'A'
        ? { id: '00000000-0000-4000-8000-000000000041', email: 'windows-inventory@example.invalid', user_metadata: { full_name: '合成验收账号 A' } }
        : { id: '00000000-0000-4000-8000-000000000042', email: 'windows-other-account@example.invalid', user_metadata: { full_name: '合成验收账号 B' } },
    };
  } else if (path === '/rest/v1/rpc/nanfeng_sync_inventory' && request.method === 'POST') {
    assert.ok([`Bearer ${fixtureAccess}`, `Bearer ${fixtureAccessB}`].includes(request.headers.authorization));
    assert.equal(JSON.parse(body).p_app_id, 'com.nanzhufeng.ai');
    if (inventoryMode === 'hold') {
      heldInventoryResponse = response;
      return;
    }
    if (inventoryMode === 'unauthorized') {
      status = 401;
      value = { message: 'synthetic authorization expired' };
    } else if (inventoryMode === 'transient') {
      status = 503;
      value = { message: 'synthetic service interruption' };
      inventoryMode = 'empty';
    } else {
      value = inventoryMode === 'empty'
        ? { documentCount: 0, documents: [] }
        : inventoryMode === 'mixed'
          ? { documentCount: validDocuments.length + 1, documents: [{ envelope: {} }, ...validDocuments] }
          : inventoryMode === 'partialUnknown'
            ? { documentCount: 2, documents: [{ envelope: {} }, validDocuments.find((_, index) => areaFixtures[index].area === 'CHAT')] }
            : inventoryMode === 'chatOnly'
              ? { documentCount: 1, documents: [validDocuments.find((_, index) => areaFixtures[index].area === 'CHAT')] }
          : { documentCount: 1, documents: [] };
    }
  } else {
    status = 404;
    value = { error: 'unexpected synthetic path' };
  }
  const encoded = JSON.stringify(value);
  response.writeHead(status, { 'content-type': 'application/json', 'content-length': Buffer.byteLength(encoded) });
  response.end(encoded);
});

async function evaluate(expression) {
  const tabs = await fetch(`http://127.0.0.1:${cdpPort}/json/list`).then(response => response.json());
  const tab = tabs.find(item => item.type === 'page' && item.url === 'http://tauri.localhost/');
  if (!tab) throw new Error('isolated Tauri page unavailable');
  const socket = new WebSocket(tab.webSocketDebuggerUrl);
  try {
    await new Promise((resolve, reject) => {
      socket.addEventListener('open', resolve, { once: true });
      socket.addEventListener('error', reject, { once: true });
    });
    return await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error('CDP evaluation timed out')), 30000);
      socket.addEventListener('message', event => {
        const message = JSON.parse(event.data);
        if (message.id !== 1) return;
        clearTimeout(timer);
        if (message.error || message.result?.exceptionDetails) reject(new Error(message.result?.exceptionDetails?.exception?.description || message.error?.message || 'CDP evaluation failed'));
        else resolve(message.result?.result?.value);
      });
      socket.send(JSON.stringify({ id: 1, method: 'Runtime.evaluate', params: { expression, awaitPromise: true, returnByValue: true } }));
    });
  } finally {
    socket.close();
  }
}

async function until(check, label) {
  const deadline = Date.now() + 30000;
  while (Date.now() < deadline) {
    try {
      const value = await check();
      if (value) return value;
    } catch { /* Native WebView may still be starting. */ }
    await new Promise(resolve => setTimeout(resolve, 250));
  }
  throw new Error(`${label} did not become ready`);
}

const invokeArea = (area, command, args = {}) => evaluate(`window.__TAURI__.core.invoke(${JSON.stringify(command)},${JSON.stringify(args)},{headers:{'x-nanfeng-data-area':${JSON.stringify(area)}}}).catch(error=>({__error:String(error)}))`);
const invoke = (command, args = {}) => invokeArea('CHAT', command, args);
const click = selector => evaluate(`(()=>{const element=document.querySelector(${JSON.stringify(selector)});if(!element||element.disabled)return false;element.click();return true})()`);

if (process.argv[4] === 'privacyActive') {
  try {
    await new Promise((resolve, reject) => server.listen(servicePort, '127.0.0.1', error => error ? reject(error) : resolve()));
    await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'privacy native bridge');
    const account = await invoke('sign_in_desktop_google_account');
    assert.equal(account?.email, 'windows-inventory@example.invalid');
    inventoryMode = 'hold';
    await evaluate(`(()=>{window.__nfaiPrivacyCloudRead=window.__TAURI__.core.invoke('restore_all_desktop_cloud_conversations',{}, {headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>({ok:true,value}),error=>({ok:false,error:String(error)}));return true})()`);
    await until(() => heldInventoryResponse, 'held cloud inventory request');
    for (const area of ['CHAT', 'WORK']) {
      const preview = await invokeArea(area, 'preview_desktop_privacy_deletion', { args: { scope: 'ALL_LOCAL_BUSINESS_DATA' } });
      assert.match(preview?.__error || '', /运行中的任务/, `${area} preview admitted held cloud request`);
    }
    const body = JSON.stringify({ documentCount: 0, documents: [] });
    heldInventoryResponse.writeHead(200, { 'content-type': 'application/json' });
    heldInventoryResponse.end(body);
    heldInventoryResponse = null;
    assert.equal((await evaluate('window.__nfaiPrivacyCloudRead'))?.ok, true);
    assert.ok((await invoke('preview_desktop_privacy_deletion', { args: { scope: 'ALL_LOCAL_BUSINESS_DATA' } })).fingerprint);
    console.log(JSON.stringify({ activeCloudPreviewRejected: true, bothAreas: true, completedPreviewAllowed: true, deletionExecuted: false }));
  } finally {
    if (heldInventoryResponse) heldInventoryResponse.destroy();
    await new Promise(resolve => server.close(resolve));
  }
} else if (process.argv[4] === 'refreshRace') {
  try {
    await new Promise((resolve, reject) => server.listen(servicePort, '127.0.0.1', error => error ? reject(error) : resolve()));
    await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'refresh race native bridge');
    const account = await invoke('sign_in_desktop_google_account');
    assert.equal(account?.email, 'windows-inventory@example.invalid');
    assert.equal(await evaluate(`(()=>{window.__nfaiPendingRefresh=window.__TAURI__.core.invoke('restore_all_desktop_cloud_conversations',{}, {headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>({ok:true,value}),error=>({ok:false,error:String(error)}));return true})()`), true);
    await until(() => heldRefreshResponse, 'delayed session refresh request');
    const signedOut = await invokeArea('WORK', 'sign_out_desktop_google_account');
    assert.ok(!signedOut?.__error, signedOut?.__error);
    const refreshBody = JSON.stringify({ access_token: 'late-refreshed-access', refresh_token: 'late-refreshed-refresh', expires_in: 3600,
      user: { id: '00000000-0000-4000-8000-000000000041', email: 'windows-inventory@example.invalid' } });
    heldRefreshResponse.writeHead(200, { 'content-type': 'application/json', 'content-length': Buffer.byteLength(refreshBody) });
    heldRefreshResponse.end(refreshBody);
    heldRefreshResponse = null;
    const late = await evaluate('window.__nfaiPendingRefresh');
    assert.equal(late?.ok, false, 'late refresh must not complete the cloud action');
    assert.match(String(late.error), /登录状态已变化/);
    assert.equal((await invoke('read_desktop_account_sync'))?.email, null, 'late refresh must not restore the old account');
    assert.equal(paths.filter(path => path === '/auth/v1/token').length, 2);
    assert.equal(paths.filter(path => path === '/rest/v1/rpc/nanfeng_sync_inventory').length, 0);
    console.log(JSON.stringify({ delayedRefreshRejectedAfterWorkSignOut: true, accountStayedSignedOut: true, remoteInventoryRequests: 0 }));
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
} else if (process.argv[4] === 'cancelLogin') {
  try {
    await new Promise((resolve, reject) => server.listen(servicePort, '127.0.0.1', error => error ? reject(error) : resolve()));
    await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'cancel login native bridge');
    assert.equal((await invoke('read_desktop_account_sync'))?.email, null);
    await until(() => evaluate('Boolean(document.querySelector(`[data-action="show-settings"]`))'), 'settings entry');
    assert.equal(await click('[data-action="show-settings"]'), true);
    await until(() => evaluate('Boolean(document.querySelector(`[data-action="open-settings-page"][data-page="account"]`))'), 'account settings entry');
    assert.equal(await click('[data-action="open-settings-page"][data-page="account"]'), true);
    await until(() => evaluate('Boolean(document.querySelector(`.android-account-hero [data-action="sign-in-google-account"]`))'), 'Google sign-in button');
    assert.equal(await click('.android-account-hero [data-action="sign-in-google-account"]'), true);
    await until(() => evaluate('Boolean(document.querySelector(`[data-action="cancel-google-account-sign-in"]`))'), 'cancel authorization button');
    assert.equal(await click('[data-action="cancel-google-account-sign-in"]'), true);
    await until(() => evaluate('!document.querySelector(`[data-action="cancel-google-account-sign-in"]`)'), 'authorization cancellation');
    assert.equal((await invoke('read_desktop_account_sync'))?.email, null);
    await new Promise(resolve => setTimeout(resolve, 2200));
    assert.equal((await invoke('read_desktop_account_sync'))?.email, null, 'late callback must not establish an account');
    assert.equal(paths.filter(path => path === '/auth/v1/token').length, 0, 'cancelled authorization must stop before token exchange');
    assert.equal(await click('.android-account-hero [data-action="sign-in-google-account"]'), true);
    await until(() => evaluate('Boolean(document.querySelector(`[data-action="cancel-google-account-sign-in"]`))'), 'retry authorization pending');
    await until(() => evaluate('document.querySelector(`.android-account-hero.signed-in`)?.textContent?.includes("windows-inventory@example.invalid")'), 'retry sign-in completion');
    assert.equal(paths.filter(path => path === '/auth/v1/token').length, 1);
    assert.equal((await invoke('read_desktop_account_sync'))?.email, 'windows-inventory@example.invalid');
    assert.equal(await click('[data-action="sign-out-google-account"]'), true);
    await until(() => evaluate('!document.querySelector(`.android-account-hero.signed-in`)'), 'sign-out after retry');
    assert.equal((await invoke('read_desktop_account_sync'))?.email, null);
    console.log(JSON.stringify({ visibleCancel: true, lateCallbackIgnored: true, tokenExchangeBeforeRetry: 0, explicitRetryAndSignOut: true }));
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
} else if (process.argv[4] === 'areaSwitchLogin') {
  try {
    await new Promise((resolve, reject) => server.listen(servicePort, '127.0.0.1', error => error ? reject(error) : resolve()));
    await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'area switch login native bridge');
    await until(() => evaluate('Boolean(document.querySelector(`[data-action="show-settings"]`))'), 'settings entry');
    assert.equal(await click('[data-action="show-settings"]'), true);
    await until(() => evaluate('Boolean(document.querySelector(`[data-action="open-settings-page"][data-page="account"]`))'), 'account settings entry');
    assert.equal(await click('[data-action="open-settings-page"][data-page="account"]'), true);
    await until(() => evaluate('Boolean(document.querySelector(`.android-account-hero [data-action="sign-in-google-account"]`))'), 'Google sign-in button');
    assert.equal(await click('.android-account-hero [data-action="sign-in-google-account"]'), true);
    await until(() => evaluate('Boolean(document.querySelector(`[data-action="cancel-google-account-sign-in"]`))'), 'authorization pending');
    assert.equal(await click('.android-settings-primary-header [data-action="show-chat"]'), true);
    await until(() => evaluate('Boolean(document.querySelector(`[data-action="show-work"]`))'), 'work area switch');
    assert.equal(await click('[data-action="show-work"]'), true);
    await until(() => evaluate('window.__nanfengDesktopWorkState?.dataArea === "WORK"'), 'visible WORK area');
    await until(() => evaluate('Boolean(document.querySelector(`[data-action="show-settings"]`))'), 'WORK settings entry');
    assert.equal(await click('[data-action="show-settings"]'), true);
    await until(() => evaluate('Boolean(document.querySelector(`[data-action="open-settings-page"][data-page="account"]`))'), 'WORK account page entry');
    assert.equal(await click('[data-action="open-settings-page"][data-page="account"]'), true);
    await until(() => evaluate('document.querySelector(`.android-account-hero.signed-in`)?.textContent?.includes("windows-inventory@example.invalid")'), 'WORK visible signed-in identity');
    assert.equal((await invokeArea('WORK', 'read_desktop_account_sync'))?.email, 'windows-inventory@example.invalid');
    assert.equal(paths.filter(path => path === '/auth/v1/token').length, 1);
    console.log(JSON.stringify({ visibleAreaSwitchDuringLogin: true, workAccountReadback: true, tokenExchangeCount: 1 }));
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
} else if (process.argv[4] === 'loginRace') {
  try {
    await new Promise((resolve, reject) => server.listen(servicePort, '127.0.0.1', error => error ? reject(error) : resolve()));
    await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'login race native bridge');
    const started = Date.now();
    assert.equal(await evaluate(`(()=>{window.__nfaiPendingLogin=window.__TAURI__.core.invoke('sign_in_desktop_google_account',{}, {headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>({ok:true,value}),error=>({ok:false,error:String(error)}));return true})()`), true);
    await new Promise(resolve => setTimeout(resolve, 150));
    const runtime = await invoke('read_desktop_runtime_info');
    assert.equal(runtime?.platform, 'windows');
    assert.ok(Date.now() - started < 1500, 'local IPC must respond before delayed browser callback');
    const signedOut = await invokeArea('WORK', 'sign_out_desktop_google_account');
    assert.ok(!signedOut?.__error, signedOut?.__error);
    const late = await evaluate('window.__nfaiPendingLogin');
    assert.equal(late?.ok, false, 'late CHAT authorization must not override WORK sign-out');
    assert.match(String(late.error), /登录期间账号状态已改变/);
    assert.equal((await invoke('read_desktop_account_sync'))?.email, null);
    const retry = await invoke('sign_in_desktop_google_account');
    assert.equal(retry?.email, 'windows-inventory@example.invalid');
    const tokenRequestCount = paths.filter(path => path === '/auth/v1/token').length;
    assert.equal(tokenRequestCount, 1, 'revoked login must stop before token exchange');
    console.log(JSON.stringify({ localIpcResponsiveBeforeCallback: true, workSignOutRejectedLateChatLogin: true, explicitRetryLoggedIn: true, callbackDelayMs: 1800, tokenRequestCount }));
  } finally {
    await new Promise(resolve => server.close(resolve));
  }
} else if (process.argv[4] === 'switch') {
  try {
    await new Promise((resolve, reject) => server.listen(servicePort, '127.0.0.1', error => error ? reject(error) : resolve()));
    await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'account switch native bridge');
    const signedOut = await invoke('read_desktop_account_sync');
    assert.equal(signedOut?.email, null);
    const firstLogin = await invoke('sign_in_desktop_google_account');
    assert.equal(firstLogin?.email, 'windows-inventory@example.invalid');
    inventoryMode = 'hold';
    assert.equal(await evaluate(`(()=>{window.__nfaiOldAccountRead=window.__TAURI__.core.invoke('restore_all_desktop_cloud_conversations',{}, {headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>({ok:true,value}),error=>({ok:false,error:String(error)}));return true})()`), true);
    await until(() => heldInventoryResponse, 'old account inventory request');
    const firstSignOut = await invoke('sign_out_desktop_google_account');
    assert.ok(!firstSignOut?.__error, firstSignOut?.__error);
    tokenIdentity = 'B';
    const secondLogin = await invoke('sign_in_desktop_google_account');
    assert.equal(secondLogin?.email, 'windows-other-account@example.invalid');
    assert.equal(secondLogin?.selectedConversationCount, 0);
    const source = areaFixtures.find(fixture => fixture.area === 'CHAT');
    const oldOnly = structuredClone(source);
    oldOnly.conversationId = 'old-account-late-document';
    oldOnly.documentId = `conversation-${sha256(oldOnly.conversationId).slice(0, 40)}`;
    oldOnly.content.title = 'Old account late document';
    const rows = [...validDocuments, { envelope: directEnvelope(oldOnly) }];
    const body = JSON.stringify({ documentCount: rows.length, documents: rows });
    heldInventoryResponse.writeHead(200, { 'content-type': 'application/json', 'content-length': Buffer.byteLength(body) });
    heldInventoryResponse.end(body);
    heldInventoryResponse = null;
    const stale = await evaluate('window.__nfaiOldAccountRead');
    assert.equal(stale?.ok, false, 'old account response must not enter new account');
    assert.match(String(stale.error), /登录状态已变化/, 'old account read must fail at the account boundary');
    const current = await invoke('read_desktop_account_sync');
    assert.equal(current?.email, 'windows-other-account@example.invalid');
    assert.equal(current?.selectedConversationCount, 0);
    for (const fixture of areaFixtures) {
      const workspaces = await invokeArea(fixture.area, 'list_desktop_workspaces');
      let originalFound = false;
      for (const workspace of workspaces) {
        const opened = await invokeArea(fixture.area, 'read_desktop_workspace', { workspaceId: workspace.id });
        assert.ok(!opened?.exchange?.conversations?.some(conversation => conversation.id === 'old-account-late-document'));
        if (opened?.exchange?.conversations?.some(conversation => conversation.id === fixture.conversationId
          && conversation.messages?.[0]?.blocks?.[0]?.text === fixture.content.messages[0].blocks[0].text)) originalFound = true;
      }
      assert.ok(originalFound, `${fixture.area} local conversation changed on account switch`);
    }
    inventoryMode = 'empty';
    const ownList = await invoke('restore_all_desktop_cloud_conversations');
    assert.ok(!ownList?.__error, ownList?.__error);
    assert.deepEqual(ownList?.restored, []);
    assert.equal((await invoke('read_desktop_account_sync'))?.email, 'windows-other-account@example.invalid');
    console.log(JSON.stringify({ oldAccountInventoryRejectedAfterSwitch: true, secondAccountHasNoInheritedSelection: true, oldDocumentNotApplied: true, originalLocalContentPreserved: true, secondAccountOwnEmptyInventoryRead: true }));
  } finally {
    if (heldInventoryResponse) heldInventoryResponse.destroy();
    await new Promise(resolve => server.close(resolve));
  }
} else if (process.argv[4] === 'race') {
  try {
    await new Promise((resolve, reject) => server.listen(servicePort, '127.0.0.1', error => error ? reject(error) : resolve()));
    await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'race native bridge');
    assert.equal((await invoke('read_desktop_account_sync'))?.email, 'windows-inventory@example.invalid');
    inventoryMode = 'hold';
    assert.equal(await evaluate(`(()=>{window.__nfaiHeldCloudRead=window.__TAURI__.core.invoke('restore_all_desktop_cloud_conversations',{}, {headers:{'x-nanfeng-data-area':'CHAT'}}).then(value=>({ok:true,value}),error=>({ok:false,error:String(error)}));return true})()`), true);
    await until(() => heldInventoryResponse, 'held inventory request');
    const signOut = await invoke('sign_out_desktop_google_account');
    assert.ok(!signOut?.__error, signOut?.__error);
    const source = areaFixtures.find(fixture => fixture.area === 'CHAT');
    const raceFixture = structuredClone(source);
    raceFixture.conversationId = 'race-after-signout';
    raceFixture.documentId = `conversation-${sha256(raceFixture.conversationId).slice(0, 40)}`;
    raceFixture.content.title = 'RACE after signout';
    const rows = [...validDocuments, { envelope: directEnvelope(raceFixture) }];
    const body = JSON.stringify({ documentCount: rows.length, documents: rows });
    heldInventoryResponse.writeHead(200, { 'content-type': 'application/json', 'content-length': Buffer.byteLength(body) });
    heldInventoryResponse.end(body);
    heldInventoryResponse = null;
    const read = await evaluate('window.__nfaiHeldCloudRead');
    assert.equal(read?.ok, false, 'pre-sign-out inventory must not apply after sign-out');
    assert.match(String(read.error), /登录状态已变化/, 'signed-out read must fail at the account boundary');
    assert.equal(paths.filter(path => path === '/rest/v1/rpc/nanfeng_sync_inventory').length, 1);
    for (const fixture of areaFixtures) {
      const workspaces = await invokeArea(fixture.area, 'list_desktop_workspaces');
      let originalFound = false;
      for (const workspace of workspaces) {
        const current = await invokeArea(fixture.area, 'read_desktop_workspace', { workspaceId: workspace.id });
        assert.ok(!current?.exchange?.conversations?.some(conversation => conversation.id === 'race-after-signout'));
        if (current?.exchange?.conversations?.some(conversation => conversation.id === fixture.conversationId
          && conversation.messages?.[0]?.blocks?.[0]?.text === fixture.content.messages[0].blocks[0].text)) originalFound = true;
      }
      assert.ok(originalFound, `${fixture.area} local content disappeared after sign-out`);
    }
    const signedOut = await invoke('read_desktop_account_sync');
    assert.equal(signedOut?.email, null);
    console.log(JSON.stringify({ pendingReadRejectedAfterSignOut: true, newRemoteConversationNotApplied: true, localWorkspacesPreserved: true, signedOut: true }));
  } finally {
    if (heldInventoryResponse) heldInventoryResponse.destroy();
    await new Promise(resolve => server.close(resolve));
  }
} else if (['readback', 'readbackOther'].includes(process.argv[4])) {
  await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'reopened native bridge');
  const account = await invoke('read_desktop_account_sync');
  const other = process.argv[4] === 'readbackOther';
  assert.equal(account?.email, other ? 'windows-other-account@example.invalid' : 'windows-inventory@example.invalid');
  assert.equal(account.selectedConversationCount, other ? 0 : 1);
  const workAccount = await invokeArea('WORK', 'read_desktop_account_sync');
  assert.equal(workAccount.selectedConversationCount, 0);
  for (const fixture of areaFixtures) {
    const workspaces = await invokeArea(fixture.area, 'list_desktop_workspaces');
    let found = false;
    for (const workspace of workspaces) {
      const current = await invokeArea(fixture.area, 'read_desktop_workspace', { workspaceId: workspace.id });
      if (current?.exchange?.conversations?.some(conversation => conversation.id === fixture.conversationId
        && conversation.messages?.[0]?.blocks?.[0]?.text === fixture.content.messages[0].blocks[0].text)) found = true;
    }
    assert.ok(found, `${fixture.area} restored conversation did not survive cold restart`);
  }
  console.log(JSON.stringify({ sameRootAccountReadback: true, chatRestoredAfterRestart: true, workRestoredAfterRestart: true, chatReceiptCountForActiveAccount: other ? 0 : 1, workReceiptCountForActiveAccount: 0, configured: account.configured, email: account.email }));
} else {
try {
  await new Promise((resolve, reject) => server.listen(servicePort, '127.0.0.1', error => error ? reject(error) : resolve()));
  await until(() => evaluate('Boolean(window.__TAURI__?.core?.invoke)'), 'native bridge');
  const runtime = await invoke('read_desktop_runtime_info');
  assert.equal(runtime.platform, 'windows');
  await until(() => evaluate('Boolean(document.querySelector(`[data-action="show-settings"]`))'), 'settings entry');
  assert.equal(await click('[data-action="show-settings"]'), true);
  await until(() => evaluate('Boolean(document.querySelector(`[data-action="open-settings-page"][data-page="account"]`))'), 'account settings entry');
  assert.equal(await click('[data-action="open-settings-page"][data-page="account"]'), true);
  await until(() => evaluate('Boolean(document.querySelector(`.android-account-hero [data-action="sign-in-google-account"]`))'), 'Google sign-in button');
  assert.equal(await click('.android-account-hero [data-action="sign-in-google-account"]'), true);
  await until(async () => (await invoke('read_desktop_account_sync'))?.email === 'windows-inventory@example.invalid', 'synthetic account sign-in');
  await until(() => evaluate('Boolean(document.querySelector(`[data-action="load-account-cloud-documents"]`))'), 'cloud list button');
  const account = await invoke('read_desktop_account_sync');
  assert.equal(account?.email, 'windows-inventory@example.invalid');
  assert.equal(await click('[data-action="load-account-cloud-documents"]'), true);
  await until(() => evaluate('document.body.innerText.includes("当前账号没有云端会话。")'), 'UI empty cloud feedback');
  assert.ok(paths.includes('/rest/v1/rpc/nanfeng_sync_inventory'));
  const empty = await invoke('restore_all_desktop_cloud_conversations');
  assert.ok(!empty?.__error, empty?.__error);
  assert.deepEqual(empty?.restored, []);
  assert.ok(paths.includes('/rest/v1/rpc/nanfeng_sync_inventory'));
  assert.ok(!paths.includes('/rest/v1/rpc/nanfeng_sync_list_documents'));
  inventoryMode = 'mismatch';
  const malformed = await invoke('restore_all_desktop_cloud_conversations');
  assert.ok(malformed?.__error, 'mismatched complete inventory must fail');
  const after = await invoke('read_desktop_account_sync');
  assert.equal(after?.email, 'windows-inventory@example.invalid');
  inventoryMode = 'empty';
  const workAccount = await invokeArea('WORK', 'read_desktop_account_sync');
  assert.equal(workAccount?.email, 'windows-inventory@example.invalid');
  const workEmpty = await invokeArea('WORK', 'restore_all_desktop_cloud_conversations');
  assert.ok(!workEmpty?.__error, workEmpty?.__error);
  assert.deepEqual(workEmpty?.restored, []);
  const inventoryRequests = () => paths.filter(path => path === '/rest/v1/rpc/nanfeng_sync_inventory').length;
  inventoryMode = 'unauthorized';
  const beforeUnauthorized = inventoryRequests();
  const unauthorized = await invoke('restore_all_desktop_cloud_conversations');
  assert.ok(unauthorized?.__error, 'authorization failure must be reported');
  assert.equal(inventoryRequests(), beforeUnauthorized + 1, 'authorization failure must not retry');
  inventoryMode = 'transient';
  const beforeTransient = inventoryRequests();
  const recoveredTransient = await invoke('restore_all_desktop_cloud_conversations');
  assert.ok(!recoveredTransient?.__error, recoveredTransient?.__error);
  assert.deepEqual(recoveredTransient?.restored, []);
  assert.equal(inventoryRequests(), beforeTransient + 2, 'fast service failure should retry once');
  inventoryMode = 'mixed';
  const chatMixed = await invoke('restore_all_desktop_cloud_conversations');
  assert.ok(!chatMixed?.__error, chatMixed?.__error);
  assert.equal(chatMixed?.failedCount, 1);
  assert.equal(chatMixed?.restored?.length, 1);
  assert.equal(chatMixed.restored[0].conversationId, areaFixtures.find(fixture => fixture.area === 'CHAT').conversationId);
  assert.equal(chatMixed.restored[0].status, 'RESTORED_AS_NEW_WORKSPACE');
  const chatWorkspace = await invoke('read_desktop_workspace', { workspaceId: chatMixed.restored[0].workspaceId });
  assert.equal(chatWorkspace.exchange.conversations[0].messages[0].blocks[0].text, 'CHAT selected text');
  const workMixed = await invokeArea('WORK', 'restore_all_desktop_cloud_conversations');
  assert.ok(!workMixed?.__error, workMixed?.__error);
  assert.equal(workMixed?.failedCount, 1);
  assert.equal(workMixed?.restored?.length, 1);
  assert.equal(workMixed.restored[0].conversationId, areaFixtures.find(fixture => fixture.area === 'WORK').conversationId);
  assert.equal(workMixed.restored[0].status, 'RESTORED_AS_NEW_WORKSPACE');
  const workWorkspace = await invokeArea('WORK', 'read_desktop_workspace', { workspaceId: workMixed.restored[0].workspaceId });
  assert.equal(workWorkspace.exchange.conversations[0].messages[0].blocks[0].text, 'WORK selected text');
  await until(() => evaluate('Boolean(document.querySelector(`[data-action="show-settings"]`))'), 'settings after direct restore');
  assert.equal(await click('[data-action="show-settings"]'), true);
  await until(() => evaluate('Boolean(document.querySelector(`[data-action="open-settings-page"][data-page="account"]`))'), 'account settings after restore');
  assert.equal(await click('[data-action="open-settings-page"][data-page="account"]'), true);
  await until(() => evaluate('Boolean(document.querySelector(`[data-action="load-account-cloud-documents"]`))'), 'cloud list after restore');
  assert.equal(await click('[data-action="load-account-cloud-documents"]'), true);
  await until(() => evaluate('document.body.innerText.includes("1 个未能恢复")'), 'partial cloud result');
  assert.equal(await evaluate('document.body.innerText.includes("CHAT fixture")'), true);
  assert.equal((await invokeArea('WORK', 'read_desktop_account_sync')).selectedConversationCount, 1);
  inventoryMode = 'partialUnknown';
  const uncertain = await invokeArea('WORK', 'restore_all_desktop_cloud_conversations');
  assert.ok(!uncertain?.__error, uncertain?.__error);
  assert.equal(uncertain.failedCount, 1);
  assert.equal(uncertain.cloudConversationKeys, null, 'unknown item must disable absence pruning');
  assert.equal((await invokeArea('WORK', 'read_desktop_account_sync')).selectedConversationCount, 1);
  inventoryMode = 'chatOnly';
  const completeMissing = await invokeArea('WORK', 'restore_all_desktop_cloud_conversations');
  assert.ok(!completeMissing?.__error, completeMissing?.__error);
  assert.equal(completeMissing.failedCount, 0);
  assert.equal((await invokeArea('WORK', 'read_desktop_account_sync')).selectedConversationCount, 0);
  const continueBefore = paths.length;
  const continuation = await invokeArea('WORK', 'sync_selected_desktop_conversation', {
    workspaceId: workMixed.restored[0].workspaceId,
    conversationId: workMixed.restored[0].conversationId,
    continuation: true,
  });
  assert.ok(continuation?.__error, 'remote deletion must stop automatic continuation');
  assert.equal(paths.length, continueBefore, 'stopped continuation must not call remote service');
  const workAfterDeletion = await invokeArea('WORK', 'read_desktop_workspace', { workspaceId: workMixed.restored[0].workspaceId });
  assert.equal(workAfterDeletion.exchange.conversations[0].messages[0].blocks[0].text, 'WORK selected text');
  assert.ok(paths.every(path => ['/auth/v1/token', '/rest/v1/rpc/nanfeng_sync_inventory'].includes(path)), 'list recovery must not read or write each document separately');
  console.log(JSON.stringify({ candidatePlatform: runtime.platform, syntheticLoginViaVisibleUi: true, completeInventoryViaVisibleUi: true, partialResultViaVisibleUi: true, chatEmptyRestoredCount: empty.restored.length, workEmptyRestoredCount: workEmpty.restored.length, malformedInventoryRejected: true, unauthorizedRejectedWithoutRetry: true, transient503RetriedOnce: true, accountRetainedAfterMalformedRead: true, mixedInventory: { chatRestored: chatMixed.restored.length, workRestored: workMixed.restored.length, failedPerArea: 1, chatTextReadBack: true, workTextReadBack: true }, deletionBoundary: { unknownItemKeptWorkReceipt: true, completeMissingPrunedWorkReceipt: true, localWorkTextPreserved: true, continuationRejectedWithoutNetwork: true }, requestPaths: paths }));
} finally {
  await new Promise(resolve => server.close(resolve));
}
}
