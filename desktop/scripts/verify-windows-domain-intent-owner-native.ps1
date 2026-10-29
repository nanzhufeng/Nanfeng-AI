param([string]$RootParent = 'D:\CodexTemp')
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if (-not $IsWindows -and $PSVersionTable.PSEdition -eq 'Core') { throw '仅支持 Windows 原生验收。' }
$desktopRoot = Split-Path -Parent $PSScriptRoot
$receipt = Get-Content -LiteralPath (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw -Encoding utf8 | ConvertFrom-Json
if ($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance' -or $receipt.sha256 -notmatch '^[A-F0-9]{64}$') { throw '隔离候选收据无效。' }
$sourceExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance/{0}/NanfengAI-Windows-Acceptance.exe" -f $receipt.sha256)))
$runExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance-binaries/{0}/nanfeng-ai-desktop-spike.exe" -f $receipt.sha256)))
if ([IO.Path]::GetFullPath([string]$receipt.executable) -ne $sourceExecutable -or (Get-FileHash -LiteralPath $sourceExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '候选 EXE 与收据不匹配。' }
$launcher = Join-Path $PSScriptRoot 'start-windows-acceptance.ps1'
$cdpHelper = Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs'
$fixture = [IO.Path]::GetFullPath((Join-Path $desktopRoot '../protocol/artifacts/nfai.exchange.v1.golden.nfai-exchange'))
if (-not (Test-Path -LiteralPath $fixture -PathType Leaf)) { throw '共用合成交换包缺失。' }
$running = [System.Collections.Generic.List[object]]::new()
$stopped = [System.Collections.Generic.List[int]]::new()
$root = ''

function New-LoopbackPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
function Invoke-Cdp($launch, [string]$expression) {
    $output = $expression | & node $cdpHelper $launch.debugPort 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $output) { throw '隔离 WebView2 IPC 调用失败。' }
    return ($output | ConvertFrom-Json)
}
function Start-Acceptance {
    $port = New-LoopbackPort
    $params = @{RootParent=$RootParent;StartupMode='normal';Visible=$true;DebugPort=$port}
    if ($root) { $params.Root = $root }
    $launch = & $launcher @params | ConvertFrom-Json
    if (-not $root) { $script:root = [string]$launch.root }
    if ($launch.root -ne $root -or $launch.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath([string]$launch.executable) -ne $runExecutable) { throw '隔离根或运行副本不符。' }
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if (-not $process -or [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable) { throw '隔离 PID 身份不符。' }
    $running.Add($launch)
    $deadline = [DateTime]::UtcNow.AddSeconds(25)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            if ((Invoke-Cdp $launch "Boolean(window.__TAURI__?.core?.invoke)") -eq $true) { return $launch }
        } catch { Start-Sleep -Milliseconds 250 }
    }
    throw '隔离 WebView2 未就绪。'
}
function Stop-Acceptance($launch) {
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if (-not $process -or [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable -or (Get-FileHash -LiteralPath $runExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '拒绝停止身份不符的进程。' }
    Stop-Process -Id $launch.pid -Force
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ((Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
    if (Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) { throw '验收 PID 未停止。' }
    $running.Remove($launch) | Out-Null
    $stopped.Add([int]$launch.pid)
}

try {
    $first = Start-Acceptance
    $fixtureJson = $fixture | ConvertTo-Json -Compress
    $intentJson = ('synthetic-shared-owner-' + [Guid]::NewGuid().ToString('N')) | ConvertTo-Json -Compress
    $result = Invoke-Cdp $first @"
(async()=>{
  const h={headers:{'x-nanfeng-data-area':'CHAT'}};
  const call=(name,args={})=>window.__TAURI__.core.invoke(name,args,h);
  const errorText=e=>typeof e==='string'?e:JSON.stringify(e);
  const first=await call('ensure_desktop_local_workspace');
  const staged=await call('stage_preflight_selected_exchange',{selectedPath:$fixtureJson});
  const second=await call('import_staged_exchange_as_new_workspace',{args:{stagingId:staged.stagingId,workspaceTitle:'合成第二工作区'}});
  if(first.summary.id===second.summary.id)throw Error('合成工作区未隔离');
  const intentId=$intentJson;
  const original=await call('mutate_desktop_domain',{args:{intentId,workspaceId:first.summary.id,entity:'project',action:'create',objectId:null,expectedRevision:null,fields:{title:'第一区合成项目',description:'仅验收'}}});
  let crossRejected=false,historyCrossRejected=false,metadataCrossRejected=false,wrongOperationRejected=false,changedFieldsRejected=false,metadataFieldsRejected=false;
  try{await call('mutate_desktop_domain',{args:{intentId,workspaceId:second.summary.id,entity:'project',action:'create',objectId:null,expectedRevision:null,fields:{title:'第二区不应生成',description:'仅验收'}}});}catch(e){crossRejected=errorText(e).includes('领域 intent 属于其他工作区');}
  try{await call('undo_desktop_domain',{args:{intentId,workspaceId:second.summary.id}});}catch(e){historyCrossRejected=errorText(e).includes('历史 intent 属于其他工作区');}
  try{await call('upsert_desktop_model_metadata',{args:{intentId,workspaceId:second.summary.id,expectedRevision:0,providerId:'openrouter',modelId:'terra-model',metadata:{}}});}catch(e){metadataCrossRejected=errorText(e).includes('模型 metadata intent 属于其他工作区');}
  try{await call('undo_desktop_domain',{args:{intentId,workspaceId:first.summary.id}});}catch(e){wrongOperationRejected=errorText(e).includes('历史 intent 操作不符');}
  try{await call('mutate_desktop_domain',{args:{intentId,workspaceId:first.summary.id,entity:'project',action:'create',objectId:null,expectedRevision:null,fields:{title:'同 ID 异字段不应成功',description:'仅验收'}}});}catch(e){changedFieldsRejected=errorText(e).includes('请求内容与首次提交不符');}
  const replay=await call('mutate_desktop_domain',{args:{intentId,workspaceId:first.summary.id,entity:'project',action:'create',objectId:null,expectedRevision:null,fields:{title:'第一区合成项目',description:'仅验收'}}});
  const nonce=intentId.slice(-32);
  const metadataArgs={intentId:'meta-'+nonce,workspaceId:first.summary.id,expectedRevision:0,providerId:'openrouter',modelId:'terra-model',metadata:{pricingStatus:'UNKNOWN'}};
  const metadataOriginal=await call('upsert_desktop_model_metadata',{args:metadataArgs});
  try{await call('upsert_desktop_model_metadata',{args:{...metadataArgs,metadata:{pricingStatus:'MANUAL'}}});}catch(e){metadataFieldsRejected=errorText(e).includes('请求内容与首次提交不符');}
  const other=await call('mutate_desktop_domain',{args:{intentId:'other-'+nonce,workspaceId:first.summary.id,entity:'project',action:'create',objectId:null,expectedRevision:null,fields:{title:'另一合成项目',description:'仅验收'}}});
  const updateArgs={intentId:'update-'+nonce,workspaceId:first.summary.id,entity:'project',action:'update',objectId:original.objectId,expectedRevision:1,fields:{title:'第一区已修改',description:'仅验收'}};
  await call('mutate_desktop_domain',{args:updateArgs});
  let changedTargetRejected=false,changedRevisionRejected=false,metadataRevisionRejected=false;
  try{await call('mutate_desktop_domain',{args:{...updateArgs,objectId:other.objectId}});}catch(e){changedTargetRejected=errorText(e).includes('对象或预期版本不符');}
  try{await call('mutate_desktop_domain',{args:{...updateArgs,expectedRevision:2}});}catch(e){changedRevisionRejected=errorText(e).includes('对象或预期版本不符');}
  try{await call('upsert_desktop_model_metadata',{args:{...metadataArgs,expectedRevision:1}});}catch(e){metadataRevisionRejected=errorText(e).includes('预期版本不符');}
  const updateReplay=await call('mutate_desktop_domain',{args:updateArgs});
  const metadataReplay=await call('upsert_desktop_model_metadata',{args:metadataArgs});
  const secondRead=await call('read_desktop_workspace',{workspaceId:second.summary.id});
  return {firstId:first.summary.id,secondId:second.summary.id,crossRejected,historyCrossRejected,metadataCrossRejected,wrongOperationRejected,changedFieldsRejected,metadataFieldsRejected,changedTargetRejected,changedRevisionRejected,metadataRevisionRejected,updateReplayed:updateReplay.replayed,metadataReplayed:metadataReplay.replayed,metadataHashStable:metadataReplay.semanticHash===metadataOriginal.semanticHash,replayed:replay.replayed,objectMatched:original.objectId===replay.objectId,secondUntouched:!(secondRead.exchange.projects||[]).some(x=>x.title==='第二区不应生成')};
})()
"@
    if (-not $result.crossRejected -or -not $result.historyCrossRejected -or -not $result.metadataCrossRejected -or -not $result.wrongOperationRejected -or -not $result.changedFieldsRejected -or -not $result.metadataFieldsRejected -or -not $result.changedTargetRejected -or -not $result.changedRevisionRejected -or -not $result.metadataRevisionRejected -or -not $result.updateReplayed -or -not $result.metadataReplayed -or -not $result.metadataHashStable -or -not $result.replayed -or -not $result.objectMatched -or -not $result.secondUntouched) { throw "原生 intent owner、字段/目标/版本或回执重放边界失败：$($result | ConvertTo-Json -Compress)" }
    Stop-Acceptance $first
    $secondLaunch = Start-Acceptance
    $ids = @{firstId=$result.firstId;secondId=$result.secondId} | ConvertTo-Json -Compress
    $reopened = Invoke-Cdp $secondLaunch @"
(async()=>{const h={headers:{'x-nanfeng-data-area':'CHAT'}};const ids=$ids;const call=(name,args={})=>window.__TAURI__.core.invoke(name,args,h);const a=await call('read_desktop_workspace',{workspaceId:ids.firstId});const b=await call('read_desktop_workspace',{workspaceId:ids.secondId});return {firstPresent:(a.exchange.projects||[]).some(x=>x.title==='第一区已修改'),secondUntouched:!(b.exchange.projects||[]).some(x=>x.title==='第二区不应生成')};})()
"@
    if (-not $reopened.firstPresent -or -not $reopened.secondUntouched) { throw '同根冷启 owner 回读失败。' }
    Stop-Acceptance $secondLaunch
    $database = Join-Path $root 'workspace.sqlite3'
    $integrity = & python -c "import sqlite3,sys; c=sqlite3.connect('file:'+sys.argv[1]+'?mode=ro',uri=True); print(c.execute('PRAGMA integrity_check').fetchone()[0])" $database
    if ($LASTEXITCODE -ne 0 -or $integrity -ne 'ok') { throw 'CHAT SQLite 完整性失败。' }
    [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;firstRun=$result;reopen=$reopened;sqliteIntegrity=$integrity;stoppedPids=@($stopped)} | ConvertTo-Json -Depth 6
} finally {
    foreach ($launch in @($running)) { try { Stop-Acceptance $launch } catch { Write-Warning '某个验收 PID 未能按身份停止，请检查隔离副本。' } }
}
