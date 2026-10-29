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
$running = [System.Collections.Generic.List[object]]::new()
$stopped = [System.Collections.Generic.List[int]]::new()
$root = ''

function New-LoopbackPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
function Invoke-Cdp($launch, [string]$expression) {
    $output = $expression | & node $cdpHelper $launch.debugPort 2>&1
    if ($LASTEXITCODE -ne 0 -or -not $output) { throw ("隔离 WebView2 IPC 调用失败：" + ($output -join ' | ')) }
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
    $tag = 'meta-undo-' + [Guid]::NewGuid().ToString('N')
    $tagJson = $tag | ConvertTo-Json -Compress
    $result = Invoke-Cdp $first @"
(async()=>{
  const h={headers:{'x-nanfeng-data-area':'CHAT'}};
  const call=(name,args={})=>window.__TAURI__.core.invoke(name,args,h);
  const errorText=e=>typeof e==='string'?e:JSON.stringify(e);
  const tag=$tagJson;
  const local=await call('ensure_desktop_local_workspace');
  const workspaceId=local.summary.id;
  await call('upsert_desktop_model_metadata',{args:{intentId:tag+'-metadata',workspaceId,expectedRevision:0,providerId:'openrouter',modelId:'terra-model',metadata:{pricingStatus:'UNKNOWN'}}});
  const metadataOnly=await call('read_desktop_workbench_history',{workspaceId});
  let metadataUndoRejected=false;
  try{await call('undo_desktop_domain',{args:{intentId:tag+'-bad-undo',workspaceId}});}catch(e){metadataUndoRejected=errorText(e).includes('没有可 undo 的本地动作');}
  const project=await call('mutate_desktop_domain',{args:{intentId:tag+'-project',workspaceId,entity:'project',action:'create',objectId:null,expectedRevision:null,fields:{title:'可撤销合成项目',description:'仅验收'}}});
  const projectHistory=await call('read_desktop_workbench_history',{workspaceId});
  await call('undo_desktop_domain',{args:{intentId:tag+'-undo-project',workspaceId}});
  const undoneHistory=await call('read_desktop_workbench_history',{workspaceId});
  const undoneProjection=await call('read_desktop_workspace',{workspaceId});
  return {workspaceId,projectId:project.objectId,metadataOnlyNoUndo:!metadataOnly.canUndo&&!metadataOnly.canRedo,metadataUndoRejected,projectCanUndo:projectHistory.canUndo,afterUndoNoUndo:!undoneHistory.canUndo,afterUndoCanRedo:undoneHistory.canRedo,metadataRetained:undoneHistory.modelMetadata.length===1,projectAbsent:!(undoneProjection.exchange.projects||[]).some(x=>x.id===project.objectId)};
})().catch(e=>({__error:String(e)}))
"@
    if ($result.__error) { throw "首轮 metadata 原生 IPC 失败：$($result.__error)" }
    if (-not $result.metadataOnlyNoUndo -or -not $result.metadataUndoRejected -or -not $result.projectCanUndo -or -not $result.afterUndoNoUndo -or -not $result.afterUndoCanRedo -or -not $result.metadataRetained -or -not $result.projectAbsent) { throw '原生 metadata 历史栈边界失败。' }
    Stop-Acceptance $first
    $secondLaunch = Start-Acceptance
    $ids = @{workspaceId=$result.workspaceId;projectId=$result.projectId;tag=$tag} | ConvertTo-Json -Compress
    $reopened = Invoke-Cdp $secondLaunch @"
(async()=>{const h={headers:{'x-nanfeng-data-area':'CHAT'}};const ids=$ids;const call=(name,args={})=>window.__TAURI__.core.invoke(name,args,h);const before=await call('read_desktop_workbench_history',{workspaceId:ids.workspaceId});await call('redo_desktop_domain',{args:{intentId:ids.tag+'-redo-project',workspaceId:ids.workspaceId}});const after=await call('read_desktop_workbench_history',{workspaceId:ids.workspaceId});const workspace=await call('read_desktop_workspace',{workspaceId:ids.workspaceId});return {coldUndoAbsent:!before.canUndo,coldRedoAvailable:before.canRedo,projectRestored:(workspace.exchange.projects||[]).some(x=>x.id===ids.projectId),metadataRetained:after.modelMetadata.length===1};})().catch(e=>({__error:String(e)}))
"@
    if ($reopened.__error) { throw "冷启 metadata 原生 IPC 失败：$($reopened.__error)" }
    if (-not $reopened.coldUndoAbsent -or -not $reopened.coldRedoAvailable -or -not $reopened.projectRestored -or -not $reopened.metadataRetained) { throw '同根冷启 metadata undo/redo 回读失败。' }
    Stop-Acceptance $secondLaunch
    $database = Join-Path $root 'workspace.sqlite3'
    $integrity = & python -c "import sqlite3,sys; c=sqlite3.connect('file:'+sys.argv[1]+'?mode=ro',uri=True); print(c.execute('PRAGMA integrity_check').fetchone()[0])" $database
    if ($LASTEXITCODE -ne 0 -or $integrity -ne 'ok') { throw 'CHAT SQLite 完整性失败。' }
    [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;firstRun=$result;reopen=$reopened;sqliteIntegrity=$integrity;stoppedPids=@($stopped)} | ConvertTo-Json -Depth 6
} finally {
    foreach ($launch in @($running)) { try { Stop-Acceptance $launch } catch { Write-Warning '某个验收 PID 未能按身份停止，请检查隔离副本。' } }
}
