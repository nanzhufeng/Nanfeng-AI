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
    $intentPrefixJson = ('synthetic-privacy-' + [Guid]::NewGuid().ToString('N')) | ConvertTo-Json -Compress
    $result = Invoke-Cdp $first @"
(async()=>{
  const h={headers:{'x-nanfeng-data-area':'CHAT'}};
  const call=(name,args={})=>window.__TAURI__.core.invoke(name,args,h);
  const errorText=e=>typeof e==='string'?e:JSON.stringify(e);
  const staged=await call('stage_preflight_selected_exchange',{selectedPath:$fixtureJson});
  const imported=await call('import_staged_exchange_as_new_workspace',{args:{stagingId:staged.stagingId,workspaceTitle:'合成隐私清理工作区'}});
  const workspaceId=imported.summary.id;
  const before=await call('read_desktop_workspace',{workspaceId});
  const knowledge=before.exchange.knowledge[0],memory=before.exchange.memory[0];
  if(!knowledge||!memory)throw Error('共用夹具缺少知识或记忆');
  const prefix=$intentPrefixJson;
  const intents=[prefix+'-knowledge',prefix+'-memory'];
  for(const [index,item] of [knowledge,memory].entries()){
    await call('mutate_desktop_domain',{args:{intentId:intents[index],workspaceId,entity:index?'memory':'knowledge',action:'softDelete',objectId:item.id,expectedRevision:item.revision,fields:{}}});
  }
  const preview=await call('preview_desktop_privacy_deletion',{args:{scope:'KNOWLEDGE_MEMORY_TRASH',selectedTaskIds:[]}});
  if(preview.aggregates.reduce((sum,item)=>sum+item.count,0)!==2)throw Error('清理预览数量不符');
  await call('delete_desktop_privacy_data',{args:{scope:preview.scope,previewFingerprint:preview.fingerprint,confirmationPhrase:'',selectedTaskIds:[]}});
  const after=await call('read_desktop_workspace',{workspaceId});
  let oldIdRejected=false,oldActionRejected=false;
  try{await call('mutate_desktop_domain',{args:{intentId:intents[0],workspaceId,entity:'project',action:'create',objectId:null,expectedRevision:null,fields:{title:'旧 ID 不应创建项目',description:'合成'}}});}catch(e){oldIdRejected=errorText(e).includes('领域 intent 操作不符');}
  try{await call('mutate_desktop_domain',{args:{intentId:intents[1],workspaceId,entity:'memory',action:'softDelete',objectId:memory.id,expectedRevision:memory.revision,fields:{}}});}catch(e){oldActionRejected=errorText(e).includes('领域 intent 操作不符');}
  return {workspaceId,intents,removed:after.exchange.knowledge.length===0&&after.exchange.memory.length===0,oldIdRejected,oldActionRejected,projectAbsent:!(after.exchange.projects||[]).some(x=>x.title==='旧 ID 不应创建项目')};
})()
"@
    if (-not $result.removed -or -not $result.oldIdRejected -or -not $result.oldActionRejected -or -not $result.projectAbsent) { throw '原生知识与记忆清理或旧 ID 拒绝失败。' }
    Stop-Acceptance $first
    $secondLaunch = Start-Acceptance
    $ids = @{workspaceId=$result.workspaceId;intents=$result.intents} | ConvertTo-Json -Compress
    $reopened = Invoke-Cdp $secondLaunch @"
(async()=>{const h={headers:{'x-nanfeng-data-area':'CHAT'}};const ids=$ids;const call=(name,args={})=>window.__TAURI__.core.invoke(name,args,h);const a=await call('read_desktop_workspace',{workspaceId:ids.workspaceId});let rejected=false;try{await call('mutate_desktop_domain',{args:{intentId:ids.intents[0],workspaceId:ids.workspaceId,entity:'project',action:'create',objectId:null,expectedRevision:null,fields:{title:'冷启旧 ID 不应创建项目',description:'合成'}}});}catch(e){rejected=String(e).includes('领域 intent 操作不符');}return {removed:a.exchange.knowledge.length===0&&a.exchange.memory.length===0,rejected,projectAbsent:!(a.exchange.projects||[]).some(x=>x.title==='冷启旧 ID 不应创建项目')};})()
"@
    if (-not $reopened.removed -or -not $reopened.rejected -or -not $reopened.projectAbsent) { throw '同根冷启隐私清理回读失败。' }
    Stop-Acceptance $secondLaunch
    $database = Join-Path $root 'workspace.sqlite3'
    $databaseCheck = & python -c "import json,sqlite3,sys; c=sqlite3.connect('file:'+sys.argv[1]+'?mode=ro',uri=True); w=sys.argv[2]; rows=c.execute('SELECT entity,action,object_id,before_exchange_json,after_exchange_json,undone FROM domain_intents WHERE workspace_id=?',(w,)).fetchall(); print(json.dumps({'integrity':c.execute('PRAGMA integrity_check').fetchone()[0],'retiredCount':len(rows),'allSanitized':all(r==('purged','purged','purged','{}','{}',1) for r in rows)}))" $database $result.workspaceId | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0 -or $databaseCheck.integrity -ne 'ok' -or $databaseCheck.retiredCount -ne 2 -or -not $databaseCheck.allSanitized) { throw 'CHAT SQLite 快照隔离或完整性失败。' }
    [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;firstRun=$result;reopen=$reopened;sqlite=$databaseCheck;stoppedPids=@($stopped)} | ConvertTo-Json -Depth 6
} finally {
    foreach ($launch in @($running)) { try { Stop-Acceptance $launch } catch { Write-Warning '某个验收 PID 未能按身份停止，请检查隔离副本。' } }
}
