param([string]$RootParent = 'D:\CodexTemp')
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if (-not $IsWindows -and $PSVersionTable.PSEdition -eq 'Core') { throw '仅支持 Windows 原生验收。' }
$desktopRoot = Split-Path -Parent $PSScriptRoot
$oldHash = '5BCAC80A2611830E6E7187DF9D013D2C76D52110E9BF3D9DE59C1835745B09D6'
$currentAreaSchema = 50
$oldExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot "output/windows-acceptance/$oldHash/NanfengAI-Windows-Acceptance.exe"))
$receipt = Get-Content -LiteralPath (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw -Encoding utf8 | ConvertFrom-Json
if ($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance' -or $receipt.sha256 -notmatch '^[A-F0-9]{64}$' -or $receipt.sha256 -eq $oldHash) { throw '新候选收据无效。' }
if ((Get-FileHash -LiteralPath $oldExecutable -Algorithm SHA256).Hash -ne $oldHash -or (Get-FileHash -LiteralPath $receipt.executable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '新旧候选哈希不符。' }
$launcher = Join-Path $PSScriptRoot 'start-windows-acceptance.ps1'
$cdpHelper = Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs'
$fixture = [IO.Path]::GetFullPath((Join-Path $desktopRoot '../protocol/artifacts/nfai.exchange.v1.golden.nfai-exchange'))
if (-not (Test-Path -LiteralPath $fixture -PathType Leaf)) { throw '合成夹具缺失。' }
$backup = [IO.Path]::GetFullPath((Join-Path $RootParent ('nanfeng-ai-old45-backup.' + [Guid]::NewGuid().ToString('N') + '.nfai-backup')))
$root = ''
$running = [System.Collections.Generic.List[object]]::new()
$stopped = [System.Collections.Generic.List[int]]::new()

function New-LoopbackPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
function Invoke-Cdp($launch, [string]$expression) {
    $output = $expression | & node $cdpHelper $launch.debugPort 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $output) { throw '隔离 WebView2 IPC 失败。' }
    return ($output | ConvertFrom-Json)
}
function Start-Acceptance([string]$candidate) {
    $params = @{RootParent=$RootParent;StartupMode='normal';Visible=$true;DebugPort=(New-LoopbackPort)}
    if ($root) { $params.Root = $root }
    if ($candidate -eq 'old') { $params.ExecutablePath = $oldExecutable }
    $launch = & $launcher @params | ConvertFrom-Json
    if (-not $root) { $script:root = [string]$launch.root }
    $expectedHash = if ($candidate -eq 'old') { $oldHash } else { $receipt.sha256 }
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if ($launch.root -ne $root -or $launch.sha256 -ne $expectedHash -or -not $process -or [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne [IO.Path]::GetFullPath([string]$launch.executable)) { throw '隔离进程或候选身份不符。' }
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

try {
    $old = Start-Acceptance 'old'
    $fixtureJson = $fixture | ConvertTo-Json -Compress
    $backupJson = $backup | ConvertTo-Json -Compress
    $seed = Invoke-Cdp $old @"
(async()=>{const result={};for(const area of ['CHAT','WORK']){const h={headers:{'x-nanfeng-data-area':area}};const call=(n,a={})=>window.__TAURI__.core.invoke(n,a,h);const staged=await call('stage_preflight_selected_exchange',{selectedPath:$fixtureJson});const imported=await call('import_staged_exchange_as_new_workspace',{args:{stagingId:staged.stagingId,workspaceTitle:'旧45合成备份 '+area}});const w=await call('read_desktop_workspace',{workspaceId:imported.summary.id});result[area]={id:imported.summary.id,conversationCount:w.exchange.conversations.length};}const backup=await window.__TAURI__.core.invoke('export_desktop_local_backup',{selectedPath:$backupJson},{headers:{'x-nanfeng-data-area':'CHAT'}});return {...result,backup};})()
"@
    if ($seed.CHAT.conversationCount -lt 1 -or $seed.WORK.conversationCount -lt 1 -or -not (Test-Path -LiteralPath $backup -PathType Leaf)) { throw '旧45合成备份生成失败。' }
    Stop-Acceptance $old
    $new = Start-Acceptance 'new'
    $seedJson = @{chatId=$seed.CHAT.id;workId=$seed.WORK.id} | ConvertTo-Json -Compress
    $newIntentJson = ('synthetic-restore-' + [Guid]::NewGuid().ToString('N')) | ConvertTo-Json -Compress
    $before = Invoke-Cdp $new @"
(async()=>{const ids=$seedJson;const h={headers:{'x-nanfeng-data-area':'CHAT'}};const call=(n,a={})=>window.__TAURI__.core.invoke(n,a,h);const made=await call('mutate_desktop_domain',{args:{intentId:$newIntentJson,workspaceId:ids.chatId,entity:'project',action:'create',objectId:null,expectedRevision:null,fields:{title:'备份后新项目',description:'合成'}}});let wrongAreaRejected=false;try{const wrong=await window.__TAURI__.core.invoke('preflight_desktop_local_backup',{selectedPath:$backupJson},{headers:{'x-nanfeng-data-area':'WORK'}});wrongAreaRejected=(wrong.conflicts||[]).length>0||(wrong.unsupported||[]).length>0;}catch{wrongAreaRejected=true}const preflight=await call('preflight_desktop_local_backup',{selectedPath:$backupJson});return {newProjectId:made.objectId,wrongAreaRejected,preflight};})()
"@
    if (-not $before.wrongAreaRejected -or $before.preflight.schemaVersion -ne 45 -or -not $before.preflight.fingerprint) { throw '当前候选未接受旧45同区备份或未拒绝 WORK 跨区。' }
    $fingerprintJson = [string]$before.preflight.fingerprint | ConvertTo-Json -Compress
    $restore = Invoke-Cdp $new "window.__TAURI__.core.invoke('restore_desktop_local_backup',{fingerprint:$fingerprintJson,replaceLocal:true},{headers:{'x-nanfeng-data-area':'CHAT'}})"
    if (-not $restore.restartRequired) { throw '旧45备份恢复未要求新进程启动。' }
    Stop-Acceptance $new
    $restarted = Start-Acceptance 'new'
    $result = Invoke-Cdp $restarted @"
(async()=>{const ids=$seedJson;const result={};for(const area of ['CHAT','WORK']){const h={headers:{'x-nanfeng-data-area':area}};const id=area==='CHAT'?ids.chatId:ids.workId;const w=await window.__TAURI__.core.invoke('read_desktop_workspace',{workspaceId:id},h);result[area]={id:w.summary.id,conversationCount:w.exchange.conversations.length,lateProjectPresent:(w.exchange.projects||[]).some(x=>x.title==='备份后新项目')}}return result})()
"@
    if ($result.CHAT.conversationCount -lt 1 -or $result.WORK.conversationCount -lt 1 -or $result.CHAT.lateProjectPresent -or $result.WORK.lateProjectPresent) { throw '同根重启旧备份恢复或 WORK 保持失败。' }
    Stop-Acceptance $restarted
    $checks = @{}
    foreach ($area in @('CHAT','WORK')) {
        $db = if ($area -eq 'CHAT') { Join-Path $root 'workspace.sqlite3' } else { Join-Path $root 'work-area/workspace.sqlite3' }
        $check = & python -c "import json,sqlite3,sys; c=sqlite3.connect('file:'+sys.argv[1]+'?mode=ro',uri=True); print(json.dumps({'version':c.execute('PRAGMA user_version').fetchone()[0],'integrity':c.execute('PRAGMA integrity_check').fetchone()[0]}))" $db | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0 -or $check.version -ne $currentAreaSchema -or $check.integrity -ne 'ok') { throw "$area 恢复后数据库版本或完整性失败。" }
        $checks[$area] = $check
    }
    [pscustomobject]@{oldCandidateSha256=$oldHash;newCandidateSha256=$receipt.sha256;root=$root;backup=$backup;backupSha256=(Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash;seed=$seed;preflight=$before.preflight;restore=$restore;reopen=$result;sqlite=$checks;stoppedPids=@($stopped)} | ConvertTo-Json -Depth 7
} finally {
    foreach ($launch in @($running)) { try { Stop-Acceptance $launch } catch { Write-Warning $_ } }
}
