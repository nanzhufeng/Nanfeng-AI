param([string]$RootParent = 'D:\CodexTemp', [string]$FixturePath = '')
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
if ($FixturePath) { $fixture = [IO.Path]::GetFullPath($FixturePath) }
if (-not (Test-Path -LiteralPath $fixture -PathType Leaf)) { throw '共用合成交换包缺失。' }
$running = [System.Collections.Generic.List[object]]::new()
$stopped = [System.Collections.Generic.List[int]]::new()
$root = ''

function New-LoopbackPort {
    # The machine's ephemeral range can include browser-forbidden ports such as 6000.
    # CDP discovery uses fetch, so reserve an explicit port outside that range.
    for ($attempt = 0; $attempt -lt 50; $attempt++) {
        $candidatePort = Get-Random -Minimum 20000 -Maximum 60000
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $candidatePort)
        try { $listener.Start(); return $candidatePort } catch [Net.Sockets.SocketException] {
            if ($attempt -eq 49) { throw '没有可用的隔离 CDP 端口。' }
        } finally { $listener.Stop() }
    }
}
function Invoke-Cdp($launch, [string]$expression) {
    # Do not create extra files in a new data root before Schema45 initializes it.
    $output = $expression | & node --max-old-space-size=2048 $cdpHelper $launch.debugPort 2>> ($launch.launchReceipt + '.share-cdp.stderr.log')
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
        if (-not (Get-Process -Id $launch.pid -ErrorAction SilentlyContinue)) { throw '隔离进程在 WebView2 就绪前退出；保留 startup 日志。' }
        try {
            if ((Invoke-Cdp $launch "Boolean(window.__TAURI__?.core?.invoke&&globalThis.__nanfengDesktopWorkState?.current?.summary?.id&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)") -eq $true) { return $launch }
        } catch { Start-Sleep -Milliseconds 250 }
    }
    throw '隔离 WebView2 未就绪。'
}
function Stop-Acceptance($launch) {
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if (-not $process) { $running.Remove($launch) | Out-Null; return }
    if ([IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable -or (Get-FileHash -LiteralPath $runExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '拒绝停止身份不符的进程。' }
    Stop-Process -Id $launch.pid -Force
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ((Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
    if (Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) { throw '验收 PID 未停止。' }
    $running.Remove($launch) | Out-Null
    $stopped.Add([int]$launch.pid)
}

try {
 $first=Start-Acceptance
 $fixtureJson=$fixture | ConvertTo-Json -Compress
 $seed=Invoke-Cdp $first @"
(async()=>{const call=(n,a={})=>window.__TAURI__.core.invoke(n,a,{headers:{'x-nanfeng-data-area':'CHAT'}});const staged=await call('stage_preflight_selected_exchange',{selectedPath:$fixtureJson});const imported=await call('import_staged_exchange_as_new_workspace',{args:{stagingId:staged.stagingId,workspaceTitle:'Synthetic share lifecycle'}});return {workspaceId:imported.summary.id,attachmentId:imported.exchange.conversations.flatMap(c=>c.messages).flatMap(m=>m.blocks).find(b=>b.kind==='ASSET_REF').asset.id}})()
"@
 [pscustomobject]@{launch=$first;seed=$seed;candidate=$receipt.sha256} | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $root 'share-ui-manual-session.json') -Encoding utf8
 [pscustomobject]@{launch=$first;seed=$seed;candidate=$receipt.sha256} | ConvertTo-Json -Depth 8
} catch {foreach($launch in @($running.ToArray())){Stop-Acceptance $launch};throw}
