param([string]$RootParent = 'D:\CodexTemp', [switch]$Gui, [switch]$LockFailure)
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
$ownedPids = [System.Collections.Generic.List[int]]::new()
$root = ''
$firstPid = 0
$secondPid = 0
$lockStream = $null

function New-LoopbackPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
function Invoke-Cdp([int]$port, [string]$expression) {
    $output = $expression | & node $cdpHelper $port 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $output) { throw '隔离 WebView2 IPC 调用失败。' }
    return ($output | ConvertFrom-Json)
}
function Assert-OwnedProcess([int]$processId) {
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $processId)
    if (-not $process -or [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable -or
        (Get-FileHash -LiteralPath $runExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '隔离 PID 身份不符。' }
    return $process
}
function Wait-Cdp([int]$port) {
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    while ([DateTime]::UtcNow -lt $deadline) {
        try { if ((Invoke-Cdp $port 'Boolean(window.__TAURI__?.core?.invoke)') -eq $true) { return } }
        catch { Start-Sleep -Milliseconds 250 }
    }
    throw '隔离 WebView2 未就绪。'
}
function Stop-Owned([int]$processId) {
    if (-not (Get-Process -Id $processId -ErrorAction SilentlyContinue)) { return }
    Assert-OwnedProcess $processId | Out-Null
    Stop-Process -Id $processId -Force
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ((Get-Process -Id $processId -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
    if (Get-Process -Id $processId -ErrorAction SilentlyContinue) { throw '隔离 PID 未停止。' }
}

try {
    $port = New-LoopbackPort
    $first = & $launcher -RootParent $RootParent -StartupMode normal -Visible -DebugPort $port -ExternalProfileForFullDelete | ConvertFrom-Json
    $root = [string]$first.root
    $firstPid = [int]$first.pid
    $ownedPids.Add($firstPid)
    if ($first.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath([string]$first.executable) -ne $runExecutable) { throw '启动候选身份不符。' }
    Assert-OwnedProcess $firstPid | Out-Null
    Wait-Cdp $port
    $fixtureJson = $fixture | ConvertTo-Json -Compress
    $syntheticKey = 'nfai-synthetic-full-delete-' + [Guid]::NewGuid().ToString('N')
    $keyJson = $syntheticKey | ConvertTo-Json -Compress
    $before = Invoke-Cdp $port @"
(async()=>{
  const invoke=window.__TAURI__.core.invoke,fixture=$fixtureJson,key=$keyJson;
  const ids={};
  for(const area of ['CHAT','WORK']){
    const h={headers:{'x-nanfeng-data-area':area}};
    const staged=await invoke('stage_preflight_selected_exchange',{selectedPath:fixture},h);
    const imported=await invoke('import_staged_exchange_as_new_workspace',{args:{stagingId:staged.stagingId,workspaceTitle:'合成全部删除 '+area}},h);
    ids[area]=imported.summary.id;
  }
  const h={headers:{'x-nanfeng-data-area':'CHAT'}};
  const setting=(await invoke('read_desktop_model_service_settings',{},h)).find(x=>x.providerId==='QWEN');
  await invoke('save_desktop_model_service_settings',{args:{providerId:'QWEN',enabled:true,presetId:'QWEN_3_7_PLUS',expectedRevision:setting.revision,apiKey:key}},h);
  window.localStorage.setItem('nfai-synthetic-full-delete-probe','old');
  const preview=await invoke('preview_desktop_privacy_deletion',{args:{scope:'ALL_LOCAL_BUSINESS_DATA',selectedTaskIds:[]}},h);
  let staleRejected=false,phraseRejected=false;
  try{await invoke('delete_desktop_privacy_data',{args:{scope:preview.scope,previewFingerprint:'bad',confirmationPhrase:'删除全部本地业务数据',selectedTaskIds:[]}},h)}catch(e){staleRejected=String(e).includes('重新预览')}
  try{await invoke('delete_desktop_privacy_data',{args:{scope:preview.scope,previewFingerprint:preview.fingerprint,confirmationPhrase:'错字',selectedTaskIds:[]}},h)}catch(e){phraseRejected=String(e).includes('完整确认语')}
  return {ids,previewAggregateCount:preview.aggregates.length,staleRejected,phraseRejected,credentialStored:true};
})()
"@
    if (-not $before.staleRejected -or -not $before.phraseRejected -or $before.previewAggregateCount -lt 2) { throw '预览、陈旧指纹或精确确认门失败。' }
    if ($LockFailure) {
        $lockedPath = Join-Path $root 'zz-synthetic-locked-file.txt'
        [IO.File]::WriteAllText($lockedPath, 'synthetic locked file', [Text.UTF8Encoding]::new($false))
        $lockStream = [IO.File]::Open($lockedPath, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    }
    if ($Gui) {
        $submit = Invoke-Cdp $port @'
(async()=>{
  const wait=async selector=>{for(let i=0;i<80;i++){const item=document.querySelector(selector);if(item)return item;await new Promise(r=>setTimeout(r,100))}throw Error('GUI element missing: '+selector)};
  (await wait('[data-action="show-settings"]')).click();
  (await wait('[data-action="open-settings-page"][data-page="privacy"]')).click();
  (await wait('[data-action="open-privacy-cleanup"]')).click();
  (await wait('[data-action="select-privacy-cleanup-scope"][data-scope="ALL_LOCAL_BUSINESS_DATA"]')).click();
  const confirm=await wait('.privacy-cleanup-dialog [data-action="confirm-privacy-cleanup"]');
  const dialog=document.querySelector('.privacy-cleanup-dialog');
  const summaryVisible=dialog.innerText.includes('对话、工作双区')&&dialog.innerText.includes('应用私有文件');
  const input=await wait('#privacy-confirmation');
  input.value='删除全部本地业务数据';
  input.dispatchEvent(new Event('input',{bubbles:true}));
  const enabled=!confirm.disabled;
  if(enabled)confirm.click();
  return {restartRequired:enabled,summaryVisible,clicked:enabled};
})()
'@
        if (-not $submit.summaryVisible -or -not $submit.clicked) { throw '可见删除预览或精确确认点击失败。' }
    } else {
        $submit = Invoke-Cdp $port @"
(async()=>{const h={headers:{'x-nanfeng-data-area':'WORK'}};const invoke=window.__TAURI__.core.invoke;const p=await invoke('preview_desktop_privacy_deletion',{args:{scope:'ALL_LOCAL_BUSINESS_DATA',selectedTaskIds:[]}},h);return await invoke('delete_desktop_privacy_data',{args:{scope:p.scope,previewFingerprint:p.fingerprint,confirmationPhrase:'删除全部本地业务数据',selectedTaskIds:[]}},h)})()
"@
    }
    if (-not $submit.restartRequired) { throw '全部删除确认未进入安全退出队列。' }
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    while ((Get-Process -Id $firstPid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
    if (Get-Process -Id $firstPid -ErrorAction SilentlyContinue) { throw '原生进程未按确认退出。' }
    while (-not $secondPid -and [DateTime]::UtcNow -lt $deadline) {
        $child = Get-CimInstance Win32_Process -Filter ("ParentProcessId={0}" -f $firstPid) |
            Where-Object { $_.ExecutablePath -and [IO.Path]::GetFullPath([string]$_.ExecutablePath) -eq $runExecutable } |
            Select-Object -First 1
        if ($child) { $secondPid = [int]$child.ProcessId; break }
        Start-Sleep -Milliseconds 100
    }
    if (-not $secondPid) { throw '全部删除后未找到同候选自动重启进程。' }
    $ownedPids.Add($secondPid)
    Assert-OwnedProcess $secondPid | Out-Null
    Wait-Cdp $port
    $idsJson = $before.ids | ConvertTo-Json -Compress
    $afterExpression = @"
(async()=>{const invoke=window.__TAURI__.core.invoke,ids=$idsJson,result={};for(const area of ['CHAT','WORK']){const h={headers:{'x-nanfeng-data-area':area}};let oldGone=false;try{await invoke('read_desktop_workspace',{workspaceId:ids[area]},h)}catch{oldGone=true}const s=(await invoke('read_desktop_model_service_settings',{},h)).find(x=>x.providerId==='QWEN');result[area]={oldGone,credentialStored:s.credentialStored,revision:s.revision}}result.probeCleared=window.localStorage.getItem('nfai-synthetic-full-delete-probe')===null;result.failureVisible=document.body.innerText.includes('全部删除未提交');result.outcome=await invoke('read_desktop_full_delete_outcome');return result})()
"@
    $after = $null
    $readbackDeadline = [DateTime]::UtcNow.AddSeconds(30)
    while ([DateTime]::UtcNow -lt $readbackDeadline) {
        try {
            $candidate = Invoke-Cdp $port $afterExpression
            if ($candidate.probeCleared -eq (-not [bool]$LockFailure) -and -not $candidate.outcome) { $after = $candidate; break }
        } catch { Start-Sleep -Milliseconds 250 }
    }
    if (-not $after) { throw '自动重启后的界面回执未完成或 WebView 回读不可用。' }
    foreach ($area in @('CHAT','WORK')) {
        if ($after.$area.oldGone -eq [bool]$LockFailure -or $after.$area.credentialStored -ne [bool]$LockFailure) { throw "$area 双区数据或凭据回读与预期不符。" }
    }
    if ($after.probeCleared -eq [bool]$LockFailure -or $after.outcome -or ($LockFailure -and -not $after.failureVisible)) { throw 'WebView 偏好或删除回滚回执与预期不符。' }
    $sqlite = @{}
    foreach ($name in @('workspace.sqlite3','work-area/workspace.sqlite3','app-configuration.sqlite3')) {
        $path = Join-Path $root $name
        $value = & python -c "import json,sqlite3,sys; c=sqlite3.connect('file:'+sys.argv[1]+'?mode=ro',uri=True); print(json.dumps({'version':c.execute('PRAGMA user_version').fetchone()[0],'integrity':c.execute('PRAGMA integrity_check').fetchone()[0]}))" $path | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0 -or $value.integrity -ne 'ok') { throw "$name SQLite 完整性失败。" }
        $sqlite[$name] = $value
    }
    # The running owner intentionally denies reads of its lock files. Stop only
    # the exact acceptance PID before scanning every byte under the synthetic root.
    Stop-Owned $secondPid
    if ($lockStream) {
        $lockStream.Dispose()
        $lockStream = $null
        if ([IO.File]::ReadAllText($lockedPath) -ne 'synthetic locked file') { throw '独占文件在回滚后发生变化。' }
    }
    $plain = & python -c "import pathlib,sys; root=pathlib.Path(sys.argv[1]); key=sys.argv[2].encode(); print(sum(key in p.read_bytes() for p in root.rglob('*') if p.is_file()))" $root $syntheticKey
    if ($LASTEXITCODE -ne 0 -or [int]$plain -ne 0) { throw "合成密钥在业务根明文检查失败：退出码 $LASTEXITCODE，命中数 $plain。" }
    $markers = @(Get-ChildItem -LiteralPath $root -Force | Where-Object { $_.Name -like '.full-local-delete-v1.*' })
    if ($markers.Count -ne 0) { throw '删除待决或回执控制文件未清理。' }
    [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;originalPid=$firstPid;restartedPid=$secondPid;gui=[bool]$Gui;rollbackExpected=[bool]$LockFailure;before=$before;queued=$submit.restartRequired;after=$after;sqlite=$sqlite;plaintextHits=[int]$plain;controlMarkers=$markers.Count} | ConvertTo-Json -Depth 7
} finally {
    if ($lockStream) { $lockStream.Dispose() }
    foreach ($processId in @($ownedPids)) {
        try { Stop-Owned $processId } catch { Write-Warning $_ }
    }
}
