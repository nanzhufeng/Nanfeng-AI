param([string]$RootParent = 'D:\CodexTemp')
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if (-not $IsWindows -and $PSVersionTable.PSEdition -eq 'Core') { throw '仅支持 Windows 原生验收。' }
$desktopRoot = Split-Path -Parent $PSScriptRoot
$receipt = Get-Content -LiteralPath (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw -Encoding utf8 | ConvertFrom-Json
if ($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance' -or $receipt.sha256 -notmatch '^[A-F0-9]{64}$') { throw '隔离候选收据无效。' }
$sourceExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance/{0}/NanfengAI-Windows-Acceptance.exe" -f $receipt.sha256)))
$runExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance-binaries/{0}/nanfeng-ai-desktop-spike.exe" -f $receipt.sha256)))
if ([IO.Path]::GetFullPath([string]$receipt.executable) -ne $sourceExecutable -or (Get-FileHash -LiteralPath $sourceExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '候选收据或哈希不匹配。' }
$launcher = Join-Path $PSScriptRoot 'start-windows-acceptance.ps1'
$cdpHelper = Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs'
$fixture = [IO.Path]::GetFullPath((Join-Path $desktopRoot '../protocol/artifacts/nfai.exchange.v1.golden.nfai-exchange'))
if (-not (Test-Path -LiteralPath $fixture -PathType Leaf)) { throw '合成夹具缺失。' }
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
    $params = @{RootParent=$RootParent;StartupMode='normal';Visible=$true;DebugPort=(New-LoopbackPort)}
    if ($root) { $params.Root = $root }
    $launch = & $launcher @params | ConvertFrom-Json
    if (-not $root) { $script:root = [string]$launch.root }
    if ($launch.root -ne $root -or $launch.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath([string]$launch.executable) -ne $runExecutable) { throw '隔离根或运行副本不符。' }
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if (-not $process -or [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable) { throw '隔离 PID 身份不符。' }
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
    $firstResult = Invoke-Cdp $first @"
(async()=>{
  const invoke=window.__TAURI__.core.invoke;
  const fixture=$fixtureJson;
  const result={};
  for(const area of ['CHAT','WORK']){
    const h={headers:{'x-nanfeng-data-area':area}};
    const call=(name,args={})=>invoke(name,args,h);
    const staged=await call('stage_preflight_selected_exchange',{selectedPath:fixture});
    const imported=await call('import_staged_exchange_as_new_workspace',{args:{stagingId:staged.stagingId,workspaceTitle:'合成全部清理拒绝 '+area}});
    const id=imported.summary.id;
    const before=await call('read_desktop_workspace',{workspaceId:id});
    let previewError='',deleteError='';
    try{await call('preview_desktop_privacy_deletion',{args:{scope:'ALL_LOCAL_BUSINESS_DATA',selectedTaskIds:[]}})}catch(e){previewError=String(e)}
    try{await call('delete_desktop_privacy_data',{args:{scope:'ALL_LOCAL_BUSINESS_DATA',previewFingerprint:'synthetic-bad-fingerprint',confirmationPhrase:'删除全部本地业务数据',selectedTaskIds:[]}})}catch(e){deleteError=String(e)}
    const after=await call('read_desktop_workspace',{workspaceId:id});
    result[area]={id,previewRejected:previewError.includes('双区'),deleteRejected:deleteError.includes('双区'),unchanged:JSON.stringify(after.exchange)===JSON.stringify(before.exchange)};
  }
  return result;
})()
"@
    foreach ($area in @('CHAT','WORK')) {
        if (-not $firstResult.$area.previewRejected -or -not $firstResult.$area.deleteRejected -or -not $firstResult.$area.unchanged) { throw "$area 拒绝或数据保持失败。" }
    }
    Stop-Acceptance $first
    $second = Start-Acceptance
    $idsJson = @{CHAT=$firstResult.CHAT.id;WORK=$firstResult.WORK.id} | ConvertTo-Json -Compress
    $reopened = Invoke-Cdp $second @"
(async()=>{const ids=$idsJson;const result={};for(const area of ['CHAT','WORK']){const h={headers:{'x-nanfeng-data-area':area}};const w=await window.__TAURI__.core.invoke('read_desktop_workspace',{workspaceId:ids[area]},h);result[area]=w.summary.id===ids[area]&&w.exchange.conversations.length>0;}return result})()
"@
    if (-not $reopened.CHAT -or -not $reopened.WORK) { throw '同根冷启双区合成数据未保留。' }
    Stop-Acceptance $second
    $checks = @{}
    foreach ($area in @('CHAT','WORK')) {
        $db = if ($area -eq 'CHAT') { Join-Path $root 'workspace.sqlite3' } else { Join-Path $root 'work-area/workspace.sqlite3' }
        $check = & python -c "import json,sqlite3,sys; c=sqlite3.connect('file:'+sys.argv[1]+'?mode=ro',uri=True); print(json.dumps({'version':c.execute('PRAGMA user_version').fetchone()[0],'integrity':c.execute('PRAGMA integrity_check').fetchone()[0],'workspaces':c.execute('SELECT COUNT(*) FROM workspaces').fetchone()[0]}))" $db | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0 -or $check.version -ne 46 -or $check.integrity -ne 'ok' -or $check.workspaces -lt 1) { throw "$area SQLite 身份、完整性或数据保持失败。" }
        $checks[$area] = $check
    }
    [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;first=$firstResult;reopen=$reopened;sqlite=$checks;stoppedPids=@($stopped)} | ConvertTo-Json -Depth 6
} finally {
    foreach ($launch in @($running)) {
        try { Stop-Acceptance $launch } catch { Write-Warning $_ }
    }
}
