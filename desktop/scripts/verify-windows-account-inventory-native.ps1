param([string]$RootParent = 'D:\CodexTemp', [switch]$LoginRace, [switch]$CancelLogin, [switch]$AreaSwitchLogin, [switch]$RefreshRace, [switch]$PrivacyActive)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if (-not $IsWindows -and $PSVersionTable.PSEdition -eq 'Core') { throw '仅支持 Windows 原生验收。' }
$desktopRoot = Split-Path -Parent $PSScriptRoot
$receipt = Get-Content -LiteralPath (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw | ConvertFrom-Json
if ($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance' -or $receipt.sha256 -notmatch '^[A-F0-9]{64}$') { throw '隔离候选收据无效。' }
$sourceExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance/{0}/NanfengAI-Windows-Acceptance.exe" -f $receipt.sha256)))
$runExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance-binaries/{0}/nanfeng-ai-desktop-spike.exe" -f $receipt.sha256)))
if ([IO.Path]::GetFullPath([string]$receipt.executable) -ne $sourceExecutable -or
    (Get-FileHash -LiteralPath $sourceExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '候选收据与 EXE 不匹配。' }
$parent = [IO.Path]::GetFullPath($RootParent).TrimEnd('\')
function New-LoopbackPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return [int]$listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
$debugPort = New-LoopbackPort
$servicePort = New-LoopbackPort
while ($servicePort -eq $debugPort) { $servicePort = New-LoopbackPort }
$launch = $null
$stoppedPids = [System.Collections.Generic.List[int]]::new()
function Stop-VerifiedLaunch($target) {
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $target.pid)
    if (-not $process) { return }
    if ([IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable -or
        (Get-FileHash -LiteralPath $runExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw 'PID 路径或哈希不符，拒绝停止。' }
    Stop-Process -Id $target.pid -Force -ErrorAction Stop
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ((Get-Process -Id $target.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
    if (Get-Process -Id $target.pid -ErrorAction SilentlyContinue) { throw '验收进程未停止。' }
    $stoppedPids.Add([int]$target.pid)
}
try {
    $launch = & (Join-Path $PSScriptRoot 'start-windows-acceptance.ps1') -RootParent $RootParent -StartupMode normal -Visible -DebugPort $debugPort -AccountSyncTestEndpoint ("http://127.0.0.1:{0}/" -f $servicePort) -AccountCallbackDelayMs $(if ($LoginRace -or $CancelLogin -or $AreaSwitchLogin) { 1800 } else { 0 }) | ConvertFrom-Json
    $root = [IO.Path]::GetFullPath([string]$launch.root)
    if (-not $root.StartsWith($parent + '\', [StringComparison]::OrdinalIgnoreCase) -or
        -not (Test-Path -LiteralPath (Join-Path $root '.nanfeng-windows-acceptance.json') -PathType Leaf)) { throw '运行根不是隔离合成根。' }
    if ($launch.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath([string]$launch.executable) -ne $runExecutable) { throw '运行副本身份不符。' }
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if (-not $process -or [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable) { throw '运行进程路径不符。' }
    if ($PrivacyActive) {
        $privacyResult = & node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'verify-windows-account-inventory-native.mjs') $debugPort $servicePort privacyActive
        if ($LASTEXITCODE -ne 0) { throw '云清单运行中全部删除预览门失败。' }
        Stop-VerifiedLaunch $launch
        $launch = $null
        [pscustomobject]@{candidateSha256=$receipt.sha256; root=$root; privacyActive=($privacyResult | ConvertFrom-Json); stoppedPids=@($stoppedPids)} | ConvertTo-Json -Depth 5
        return
    }
    if ($RefreshRace) {
        $raceResult = & node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'verify-windows-account-inventory-native.mjs') $debugPort $servicePort refreshRace
        if ($LASTEXITCODE -ne 0) { throw '退出账号与迟到会话刷新验收失败。' }
        $credentialFile = Join-Path $root 'account-sync-credentials-v2/credentials.json'
        if (-not (Test-Path -LiteralPath $credentialFile -PathType Leaf)) { throw '账号凭据文件未生成。' }
        $credential = Get-Content -LiteralPath $credentialFile -Raw | ConvertFrom-Json
        if (@($credential.records.PSObject.Properties).Count -ne 0) { throw '退出后迟到刷新恢复了账号凭据。' }
        Stop-VerifiedLaunch $launch
        $launch = $null
        [pscustomobject]@{candidateSha256=$receipt.sha256; root=$root; refreshRace=($raceResult | ConvertFrom-Json); stoppedPids=@($stoppedPids)} | ConvertTo-Json -Depth 5
        return
    }
    if ($CancelLogin) {
        $cancelResult = & node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'verify-windows-account-inventory-native.mjs') $debugPort $servicePort cancelLogin
        if ($LASTEXITCODE -ne 0) { throw '可见 Google 授权取消验收失败。' }
        $credentialFile = Join-Path $root 'account-sync-credentials-v2/credentials.json'
        if (Test-Path -LiteralPath $credentialFile -PathType Leaf) {
            $credential = Get-Content -LiteralPath $credentialFile -Raw | ConvertFrom-Json
            if (@($credential.records.PSObject.Properties).Count -ne 0) { throw '取消及重试退出后仍有账号凭据。' }
        }
        Stop-VerifiedLaunch $launch
        $launch = $null
        [pscustomobject]@{candidateSha256=$receipt.sha256; root=$root; cancelLogin=($cancelResult | ConvertFrom-Json); stoppedPids=@($stoppedPids)} | ConvertTo-Json -Depth 5
        return
    }
    if ($LoginRace) {
        $raceResult = & node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'verify-windows-account-inventory-native.mjs') $debugPort $servicePort loginRace
        if ($LASTEXITCODE -ne 0) { throw '登录等待／退出账号交错验收失败。' }
        Stop-VerifiedLaunch $launch
        $launch = $null
        [pscustomobject]@{candidateSha256=$receipt.sha256; root=$root; loginRace=($raceResult | ConvertFrom-Json); stoppedPids=@($stoppedPids)} | ConvertTo-Json -Depth 5
        return
    }
    if ($AreaSwitchLogin) {
        $areaSwitchResult = & node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'verify-windows-account-inventory-native.mjs') $debugPort $servicePort areaSwitchLogin
        if ($LASTEXITCODE -ne 0) { throw '可见授权等待跨区回读验收失败。' }
        Stop-VerifiedLaunch $launch
        $launch = $null
        [pscustomobject]@{candidateSha256=$receipt.sha256; root=$root; areaSwitchLogin=($areaSwitchResult | ConvertFrom-Json); stoppedPids=@($stoppedPids)} | ConvertTo-Json -Depth 5
        return
    }
    $result = & node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'verify-windows-account-inventory-native.mjs') $debugPort $servicePort
    if ($LASTEXITCODE -ne 0) { throw '原生账号完整清单验收失败。' }
    $credentialFile = Join-Path $root 'account-sync-credentials-v2/credentials.json'
    if (-not (Test-Path -LiteralPath $credentialFile -PathType Leaf)) { throw '应用私有账号凭据文件缺失。' }
    $credential = Get-Content -LiteralPath $credentialFile -Raw | ConvertFrom-Json
    if ($credential.version -ne 2 -or @($credential.records.PSObject.Properties).Count -lt 1) { throw '账号凭据文档结构无效。' }
    $sensitiveFiles = @($credentialFile) + @(rg --files $root -g '*.sqlite3' -g '*.db' | Where-Object { $_ -notmatch '[\\/]webview2[\\/]' })
    foreach ($file in $sensitiveFiles) {
        $content = [Text.Encoding]::UTF8.GetString([IO.File]::ReadAllBytes([IO.Path]::GetFullPath($file)))
        if ($content.Contains('nfai-synthetic-windows-access-only') -or $content.Contains('nfai-synthetic-windows-refresh-only')) { throw '合成会话明文泄漏到账号文件或 SQLite。' }
    }
    Stop-VerifiedLaunch $launch
    $launch = $null
    $reopenPort = New-LoopbackPort
    $launch = & (Join-Path $PSScriptRoot 'start-windows-acceptance.ps1') -Root $root -RootParent $RootParent -StartupMode normal -Visible -DebugPort $reopenPort -AccountSyncTestEndpoint ("http://127.0.0.1:{0}/" -f $servicePort) | ConvertFrom-Json
    if ($launch.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath([string]$launch.executable) -ne $runExecutable) { throw '同根重启候选身份不符。' }
    $reopened = & node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'verify-windows-account-inventory-native.mjs') $reopenPort $servicePort readback
    if ($LASTEXITCODE -ne 0) { throw '同根冷启账号回读失败。' }
    Stop-VerifiedLaunch $launch
    $launch = $null
    $racePort = New-LoopbackPort
    $launch = & (Join-Path $PSScriptRoot 'start-windows-acceptance.ps1') -Root $root -RootParent $RootParent -StartupMode normal -Visible -DebugPort $racePort -AccountSyncTestEndpoint ("http://127.0.0.1:{0}/" -f $servicePort) | ConvertFrom-Json
    if ($launch.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath([string]$launch.executable) -ne $runExecutable) { throw '账号竞态进程候选身份不符。' }
    $race = & node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'verify-windows-account-inventory-native.mjs') $racePort $servicePort race
    if ($LASTEXITCODE -ne 0) { throw '账号退出与迟到云清单验收失败。' }
    Stop-VerifiedLaunch $launch
    $launch = $null
    $signedOutCredentials = Get-Content -LiteralPath $credentialFile -Raw | ConvertFrom-Json
    if ($signedOutCredentials.version -ne 2 -or @($signedOutCredentials.records.PSObject.Properties).Count -ne 0) { throw '退出账号后应用私有会话未清理。' }
    $switchPort = New-LoopbackPort
    $launch = & (Join-Path $PSScriptRoot 'start-windows-acceptance.ps1') -Root $root -RootParent $RootParent -StartupMode normal -Visible -DebugPort $switchPort -AccountSyncTestEndpoint ("http://127.0.0.1:{0}/" -f $servicePort) | ConvertFrom-Json
    if ($launch.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath([string]$launch.executable) -ne $runExecutable) { throw '切换账号进程候选身份不符。' }
    $switched = & node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'verify-windows-account-inventory-native.mjs') $switchPort $servicePort switch
    if ($LASTEXITCODE -ne 0) { throw '另一账号与旧清单竞态验收失败。' }
    Stop-VerifiedLaunch $launch
    $launch = $null
    $otherCredentials = Get-Content -LiteralPath $credentialFile -Raw | ConvertFrom-Json
    if ($otherCredentials.version -ne 2 -or @($otherCredentials.records.PSObject.Properties).Count -ne 1) { throw '切换后账号会话数量无效。' }
    foreach ($file in $sensitiveFiles) {
        $content = [Text.Encoding]::UTF8.GetString([IO.File]::ReadAllBytes([IO.Path]::GetFullPath($file)))
        if ($content.Contains('nfai-synthetic-windows-access-other-account') -or $content.Contains('nfai-synthetic-windows-refresh-other-account')) { throw '第二合成账号令牌明文泄漏。' }
    }
    $otherReadbackPort = New-LoopbackPort
    $launch = & (Join-Path $PSScriptRoot 'start-windows-acceptance.ps1') -Root $root -RootParent $RootParent -StartupMode normal -Visible -DebugPort $otherReadbackPort -AccountSyncTestEndpoint ("http://127.0.0.1:{0}/" -f $servicePort) | ConvertFrom-Json
    if ($launch.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath([string]$launch.executable) -ne $runExecutable) { throw '第二账号冷启候选身份不符。' }
    $otherReadback = & node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'verify-windows-account-inventory-native.mjs') $otherReadbackPort $servicePort readbackOther
    if ($LASTEXITCODE -ne 0) { throw '第二账号同根冷启回读失败。' }
    Stop-VerifiedLaunch $launch
    $launch = $null
    [pscustomobject]@{ candidateSha256=$receipt.sha256; root=$root; result=($result | ConvertFrom-Json); coldRestart=($reopened | ConvertFrom-Json); accountRace=($race | ConvertFrom-Json); accountSwitch=($switched | ConvertFrom-Json); otherAccountColdRestart=($otherReadback | ConvertFrom-Json); credentialRecordCountBeforeSignOut=@($credential.records.PSObject.Properties).Count; credentialRecordCountAfterSignOut=0; credentialRecordCountAfterSwitch=1; plaintextSessionTokensFound=$false; stoppedPids=@($stoppedPids) } | ConvertTo-Json -Depth 5
} finally {
    if ($launch) { Stop-VerifiedLaunch $launch }
}
