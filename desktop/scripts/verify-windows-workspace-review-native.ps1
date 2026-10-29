param([string]$RootParent = 'D:\CodexTemp')
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if (-not $IsWindows -and $PSVersionTable.PSEdition -eq 'Core') { throw '仅支持 Windows 原生验收。' }
$desktopRoot = Split-Path -Parent $PSScriptRoot
$receipt = Get-Content -LiteralPath (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw | ConvertFrom-Json
if ($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance' -or $receipt.sha256 -notmatch '^[A-F0-9]{64}$') { throw '验收收据身份无效。' }
$sourceExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance/{0}/NanfengAI-Windows-Acceptance.exe" -f $receipt.sha256)))
$runExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance-binaries/{0}/nanfeng-ai-desktop-spike.exe" -f $receipt.sha256)))
if ([IO.Path]::GetFullPath([string]$receipt.executable) -ne $sourceExecutable -or (Get-FileHash -LiteralPath $sourceExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '验收 EXE 与收据不符。' }
$parent = [IO.Path]::GetFullPath($RootParent).TrimEnd('\')
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$listener.Start()
try { $port = $listener.LocalEndpoint.Port } finally { $listener.Stop() }
$launch = $null
$cdpHelper = Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs'
function Invoke-Cdp([string]$expression) {
    $value = $expression | & node $cdpHelper $port 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $value) { throw '隔离 WebView2 未响应。' }
    return ($value | ConvertFrom-Json -NoEnumerate)
}
function Read-Page([string]$page) {
    $expression = "(()=>{const b=document.querySelector('[data-action=open-settings-page][data-page=$page]');if(!b)return null;b.click();const p=document.querySelector('.android-settings-secondary');return p?{heading:p.querySelector('.android-settings-header h1')?.textContent||'',text:p.innerText}:null})()"
    return Invoke-Cdp $expression
}
try {
    $launch = & (Join-Path $PSScriptRoot 'start-windows-acceptance.ps1') -RootParent $RootParent -StartupMode normal -Visible -DebugPort $port | ConvertFrom-Json
    $root = [IO.Path]::GetFullPath([string]$launch.root)
    if (-not $root.StartsWith($parent + '\', [StringComparison]::OrdinalIgnoreCase) -or -not (Test-Path -LiteralPath (Join-Path $root '.nanfeng-windows-acceptance.json') -PathType Leaf)) { throw '运行根不是隔离合成根。' }
    if ($launch.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath([string]$launch.executable) -ne $runExecutable) { throw '运行副本身份不符。' }
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if (-not $process -or [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable) { throw '运行进程路径不符。' }
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    $opened = $false
    while (-not $opened -and [DateTime]::UtcNow -lt $deadline) {
        try { $opened = Invoke-Cdp "(()=>{const b=document.querySelector('[data-action=show-settings]');if(!b)return false;b.click();return true})()" } catch { Start-Sleep -Milliseconds 250 }
    }
    if (-not $opened) { throw '原生设置入口未就绪。' }
    $workspace = Read-Page 'workspace'
    $review = Read-Page 'feature-review'
    $about = Read-Page 'about'
    if ($workspace.heading -ne '工作区' -or $workspace.text -notmatch '工作区独立对话：保留' -or $workspace.text -notmatch '引用文字到另一区域') { throw '原生工作区页缺少 Mac 基线功能审阅。' }
    if ($review.heading -ne '功能审阅' -or $review.text -notmatch '备份区域选择' -or $review.text -notmatch '工作区独立对话与文字引用' -or $review.text -notmatch '不自动发送或夹带附件') { throw '原生功能审阅未同时保留 Windows 和 Mac 事项。' }
    $sourceCommit = (& git -C $desktopRoot rev-parse --verify HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $sourceCommit -notmatch '^[a-fA-F0-9]{40,64}$') { throw '当前源码 Git Commit 无法核验。' }
    $commitLabel = $sourceCommit.Substring(0, 8)
    $expectedCommitText = [regex]::Escape("Git Commit：$commitLabel")
    if ($about.heading -ne '关于' -or $about.text -notmatch '开发者信息' -or $about.text -notmatch $expectedCommitText) { throw '原生关于页未显示当前构建源码 Git Commit。' }
    [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;workspaceHeading=$workspace.heading;workspaceReviewVisible=$true;featureReviewHeading=$review.heading;backupAndReferenceVisible=$true;aboutCommit=$commitLabel;pid=[int]$launch.pid} | ConvertTo-Json -Depth 3
} finally {
    if ($launch) {
        $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
        if ($process) {
            if ([IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable -or (Get-FileHash -LiteralPath $runExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) { throw 'PID 路径或哈希不符，拒绝停止。' }
            Stop-Process -Id $launch.pid -Force -ErrorAction Stop
            $deadline = [DateTime]::UtcNow.AddSeconds(15)
            while ((Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
            if (Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) { throw '验收进程未停止。' }
        }
    }
}
