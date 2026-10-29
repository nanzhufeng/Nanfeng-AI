param([string]$RootParent = 'D:\CodexTemp')
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if (-not $IsWindows -and $PSVersionTable.PSEdition -eq 'Core') { throw '仅支持 Windows 原生验收。' }
$desktopRoot = Split-Path -Parent $PSScriptRoot
$oldHash = '5BCAC80A2611830E6E7187DF9D013D2C76D52110E9BF3D9DE59C1835745B09D6'
$oldExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot "output/windows-acceptance/$oldHash/NanfengAI-Windows-Acceptance.exe"))
$receipt = Get-Content -LiteralPath (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw -Encoding utf8 | ConvertFrom-Json
if ($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance' -or $receipt.sha256 -notmatch '^[A-F0-9]{64}$' -or $receipt.sha256 -eq $oldHash) { throw '新候选收据无效。' }
if ((Get-FileHash -LiteralPath $oldExecutable -Algorithm SHA256).Hash -ne $oldHash -or (Get-FileHash -LiteralPath $receipt.executable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '旧新候选哈希不符。' }
$launcher = Join-Path $PSScriptRoot 'start-windows-acceptance.ps1'
$cdpHelper = Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs'
$root = ''
$running = [System.Collections.Generic.List[object]]::new()
$stopped = [System.Collections.Generic.List[int]]::new()

function New-LoopbackPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
function Invoke-Cdp($launch, [string]$expression) {
    $output = $expression | & node $cdpHelper $launch.debugPort 2>&1
    if ($LASTEXITCODE -ne 0 -or -not $output) { throw "WebView2 IPC 失败：$($output -join ' | ')" }
    return ($output | ConvertFrom-Json)
}
function Start-Acceptance([string]$candidate) {
    $port = New-LoopbackPort
    $params = @{RootParent=$RootParent;StartupMode='normal';Visible=$true;DebugPort=$port}
    if ($root) { $params.Root = $root }
    if ($candidate -eq 'old') { $params.ExecutablePath = $oldExecutable }
    $launch = & $launcher @params | ConvertFrom-Json
    if (-not $root) { $script:root = [string]$launch.root }
    $expectedHash = if ($candidate -eq 'old') { $oldHash } else { $receipt.sha256 }
    if ($launch.root -ne $root -or $launch.sha256 -ne $expectedHash) { throw '隔离根或候选身份不符。' }
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if (-not $process -or [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne [IO.Path]::GetFullPath([string]$launch.executable)) { throw '隔离 PID 身份不符。' }
    $running.Add($launch)
    $deadline = [DateTime]::UtcNow.AddSeconds(25)
    while ([DateTime]::UtcNow -lt $deadline) {
        try { if ((Invoke-Cdp $launch "Boolean(window.__TAURI__?.core?.invoke)") -eq $true) { return $launch } }
        catch { Start-Sleep -Milliseconds 250 }
    }
    throw '隔离 WebView2 未就绪。'
}
function Stop-Acceptance($launch) {
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if (-not $process -or [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne [IO.Path]::GetFullPath([string]$launch.executable) -or (Get-FileHash -LiteralPath $launch.executable -Algorithm SHA256).Hash -ne $launch.sha256) { throw '拒绝停止身份不符的进程。' }
    Stop-Process -Id $launch.pid -Force
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ((Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
    if (Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) { throw '验收 PID 未停止。' }
    $running.Remove($launch) | Out-Null
    $stopped.Add([int]$launch.pid)
}
function Read-Sqlite($path, [string]$query) {
    $value = & python -c 'import sqlite3,sys; c=sqlite3.connect("file:"+sys.argv[1]+"?mode=ro",uri=True); print(c.execute(sys.argv[2]).fetchone()[0])' $path $query
    if ($LASTEXITCODE -ne 0) { throw '隔离 SQLite 回读失败。' }
    return [string]$value
}

try {
    $old = Start-Acceptance 'old'
    $nonce = [Guid]::NewGuid().ToString('N')
    $intentJson = ('old-' + $nonce) | ConvertTo-Json -Compress
    $metadataIntentJson = ('old-meta-' + $nonce) | ConvertTo-Json -Compress
    $seed = Invoke-Cdp $old @"
(async()=>{const h={headers:{'x-nanfeng-data-area':'CHAT'}};const call=(n,a={})=>window.__TAURI__.core.invoke(n,a,h);const w=await call('ensure_desktop_local_workspace');const intentId=$intentJson;const metadataIntentId=$metadataIntentJson;const p=await call('mutate_desktop_domain',{args:{intentId,workspaceId:w.summary.id,entity:'project',action:'create',objectId:null,expectedRevision:null,fields:{title:'旧版合成项目',description:'只用于迁移'}}});await call('upsert_desktop_model_metadata',{args:{intentId:metadataIntentId,workspaceId:w.summary.id,expectedRevision:0,providerId:'openrouter',modelId:'terra-model',metadata:{pricingStatus:'UNKNOWN'}}});return {workspaceId:w.summary.id,intentId,metadataIntentId,projectId:p.objectId};})()
"@
    Stop-Acceptance $old
    $chatDatabase = Join-Path $root 'workspace.sqlite3'
    $workDatabase = Join-Path $root 'work-area/workspace.sqlite3'
    if ((Read-Sqlite $chatDatabase 'PRAGMA user_version') -ne '45' -or (Read-Sqlite $workDatabase 'PRAGMA user_version') -ne '45') { throw '旧候选未生成 Schema45 双区。' }
    $oldRows = Read-Sqlite $chatDatabase 'SELECT COUNT(*) FROM domain_intents'
    $new = Start-Acceptance 'new'
    $seedJson = $seed | ConvertTo-Json -Compress
    $result = Invoke-Cdp $new @"
(async()=>{const h={headers:{'x-nanfeng-data-area':'CHAT'}};const call=(n,a={})=>window.__TAURI__.core.invoke(n,a,h);const s=$seedJson;const errorText=e=>typeof e==='string'?e:JSON.stringify(e);const oldArgs={intentId:s.intentId,workspaceId:s.workspaceId,entity:'project',action:'create',objectId:null,expectedRevision:null,fields:{title:'旧版合成项目',description:'只用于迁移'}};let oldRejected=false,oldMetadataRejected=false,changedRejected=false,metadataChangedRejected=false;try{await call('mutate_desktop_domain',{args:oldArgs});}catch(e){oldRejected=errorText(e).includes('旧 intent 无可验证请求指纹');}try{await call('upsert_desktop_model_metadata',{args:{intentId:s.metadataIntentId,workspaceId:s.workspaceId,expectedRevision:0,providerId:'openrouter',modelId:'terra-model',metadata:{pricingStatus:'UNKNOWN'}}});}catch(e){oldMetadataRejected=errorText(e).includes('旧 intent 无可验证请求指纹');}const newArgs={...oldArgs,intentId:'new-'+s.intentId,fields:{title:'新版合成项目',description:'只用于迁移'}};const created=await call('mutate_desktop_domain',{args:newArgs});try{await call('mutate_desktop_domain',{args:{...newArgs,fields:{title:'新版异字段',description:'只用于迁移'}}});}catch(e){changedRejected=errorText(e).includes('请求内容与首次提交不符');}const replay=await call('mutate_desktop_domain',{args:newArgs});const newMetadata={intentId:'new-'+s.metadataIntentId,workspaceId:s.workspaceId,expectedRevision:1,providerId:'openrouter',modelId:'terra-model',metadata:{pricingStatus:'MANUAL'}};await call('upsert_desktop_model_metadata',{args:newMetadata});try{await call('upsert_desktop_model_metadata',{args:{...newMetadata,metadata:{pricingStatus:'UNKNOWN'}}});}catch(e){metadataChangedRejected=errorText(e).includes('请求内容与首次提交不符');}const read=await call('read_desktop_workspace',{workspaceId:s.workspaceId});return {oldRejected,oldMetadataRejected,changedRejected,metadataChangedRejected,newReplayed:replay.replayed,oldProjectPresent:(read.exchange.projects||[]).some(x=>x.id===s.projectId),newProjectPresent:(read.exchange.projects||[]).some(x=>x.id===created.objectId)};})()
"@
    if (-not $result.oldRejected -or -not $result.oldMetadataRejected -or -not $result.changedRejected -or -not $result.metadataChangedRejected -or -not $result.newReplayed -or -not $result.oldProjectPresent -or -not $result.newProjectPresent) { throw "Schema46 原生迁移/重放失败：$($result | ConvertTo-Json -Compress)" }
    Stop-Acceptance $new
    $restarted = Start-Acceptance 'new'
    $reopen = Invoke-Cdp $restarted @"
(async()=>{const h={headers:{'x-nanfeng-data-area':'CHAT'}};const call=(n,a={})=>window.__TAURI__.core.invoke(n,a,h);const s=$seedJson;const errorText=e=>typeof e==='string'?e:JSON.stringify(e);let oldRejected=false;try{await call('mutate_desktop_domain',{args:{intentId:s.intentId,workspaceId:s.workspaceId,entity:'project',action:'create',objectId:null,expectedRevision:null,fields:{title:'旧版合成项目',description:'只用于迁移'}}});}catch(e){oldRejected=errorText(e).includes('旧 intent 无可验证请求指纹');}const replay=await call('mutate_desktop_domain',{args:{intentId:'new-'+s.intentId,workspaceId:s.workspaceId,entity:'project',action:'create',objectId:null,expectedRevision:null,fields:{title:'新版合成项目',description:'只用于迁移'}}});const metadata=await call('upsert_desktop_model_metadata',{args:{intentId:'new-'+s.metadataIntentId,workspaceId:s.workspaceId,expectedRevision:1,providerId:'openrouter',modelId:'terra-model',metadata:{pricingStatus:'MANUAL'}}});const read=await call('read_desktop_workspace',{workspaceId:s.workspaceId});return {oldRejected,newReplayed:replay.replayed,metadataReplayed:metadata.replayed,projectCount:(read.exchange.projects||[]).length};})()
"@
    if (-not $reopen.oldRejected -or -not $reopen.newReplayed -or -not $reopen.metadataReplayed -or $reopen.projectCount -ne 2) { throw "Schema46 同根再启动失败：$($reopen | ConvertTo-Json -Compress)" }
    Stop-Acceptance $restarted
    $after = [pscustomobject]@{chatVersion=Read-Sqlite $chatDatabase 'PRAGMA user_version';workVersion=Read-Sqlite $workDatabase 'PRAGMA user_version';oldRows=$oldRows;intentRows=Read-Sqlite $chatDatabase 'SELECT COUNT(*) FROM domain_intents';oldNullFingerprints=Read-Sqlite $chatDatabase "SELECT COUNT(*) FROM domain_intents WHERE intent_id IN ('$($seed.intentId)','$($seed.metadataIntentId)') AND request_fingerprint IS NULL";newFingerprints=Read-Sqlite $chatDatabase 'SELECT COUNT(*) FROM domain_intents WHERE request_fingerprint IS NOT NULL';chatIntegrity=Read-Sqlite $chatDatabase 'PRAGMA integrity_check';workIntegrity=Read-Sqlite $workDatabase 'PRAGMA integrity_check'}
    if ($after.chatVersion -ne '46' -or $after.workVersion -ne '46' -or $after.oldNullFingerprints -ne '2' -or [int]$after.newFingerprints -lt 2 -or $after.chatIntegrity -ne 'ok' -or $after.workIntegrity -ne 'ok') { throw "Schema46 数据库回读失败：$($after | ConvertTo-Json -Compress)" }
    [pscustomobject]@{oldCandidateSha256=$oldHash;newCandidateSha256=$receipt.sha256;root=$root;result=$result;reopen=$reopen;sqlite=$after;stoppedPids=@($stopped)} | ConvertTo-Json -Depth 6
} finally {
    foreach ($launch in @($running)) { try { Stop-Acceptance $launch } catch { Write-Warning '某个验收 PID 未能按身份停止，请检查隔离副本。' } }
}
