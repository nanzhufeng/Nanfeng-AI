param([string]$RootParent = 'D:\CodexTemp', [switch]$Appearance)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if (-not $IsWindows -or [IO.Path]::GetFullPath($RootParent).TrimEnd('\') -ne 'D:\CodexTemp') { throw '只允许 Windows D:\CodexTemp 标记合成根。' }
$desktopRoot = Split-Path -Parent $PSScriptRoot
$receipt = Get-Content -LiteralPath (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$sourceExe = [IO.Path]::GetFullPath([string]$receipt.executable)
$runExe = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance-binaries/{0}/nanfeng-ai-desktop-spike.exe" -f $receipt.sha256)))
if ($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance' -or
    (Get-FileHash -LiteralPath $sourceExe -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '隔离候选收据不符。' }
$cdpHelper = Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs'
$launch = $null
$root = $null
$pids = @()
function New-Port {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return [int]$listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
function Invoke-Cdp([int]$port, [string]$expression) {
    $result = $expression | & node --max-old-space-size=2048 $cdpHelper $port
    if ($LASTEXITCODE -ne 0 -or -not $result) { throw '隔离 WebView2 IPC 失败。' }
    return $result | ConvertFrom-Json
}
function Wait-Cdp([int]$port, [string]$expression, [string]$label) {
    $deadline = [DateTime]::UtcNow.AddSeconds(25)
    while ([DateTime]::UtcNow -lt $deadline) {
        try { if (Invoke-Cdp $port $expression 2>$null) { return } } catch { }
        Start-Sleep -Milliseconds 200
    }
    throw "等待 $label 超时。"
}
function Start-Candidate([string]$existingRoot) {
    $port = New-Port
    $args = @{ RootParent = $RootParent; StartupMode = 'normal'; Visible = $true; DebugPort = $port }
    if ($existingRoot) { $args.Root = $existingRoot }
    $started = & (Join-Path $PSScriptRoot 'start-windows-acceptance.ps1') @args | ConvertFrom-Json
    if ($started.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath([string]$started.executable) -ne $runExe -or
        -not (Test-Path -LiteralPath (Join-Path $started.root '.nanfeng-windows-acceptance.json') -PathType Leaf)) { throw '运行身份或合成根不符。' }
    Wait-Cdp $port 'Boolean(window.__TAURI__?.core?.invoke && document.querySelector(`[data-action="show-settings"]`))' '原生设置入口'
    return @{ launch = $started; port = $port }
}
function Stop-Candidate($started) {
    if (-not $started) { return }
    $process = Get-Process -Id $started.pid -ErrorAction SilentlyContinue
    if (-not $process) { return }
    if ([IO.Path]::GetFullPath([string]$process.Path) -ne $runExe -or
        (Get-FileHash -LiteralPath $runExe -Algorithm SHA256).Hash -ne $receipt.sha256) { throw 'PID 路径或哈希不符，拒绝停止。' }
    Stop-Process -Id $started.pid -Force -ErrorAction Stop
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ((Get-Process -Id $started.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
    if (Get-Process -Id $started.pid -ErrorAction SilentlyContinue) { throw '受控 PID 未退出。' }
}
try {
    $running = Start-Candidate $null
    $launch = $running.launch
    $root = [IO.Path]::GetFullPath([string]$launch.root)
    if (-not $root.StartsWith('D:\CodexTemp\', [StringComparison]::OrdinalIgnoreCase)) { throw '合成根逃逸。' }
    $pids += [int]$launch.pid
    if (-not (Invoke-Cdp $running.port '(()=>{document.querySelector(`[data-action="show-settings"]`).click();return true})()')) { throw '设置入口未打开。' }
    Wait-Cdp $running.port 'Boolean(document.querySelector(`[data-action="toggle-product-setting"][data-key="memoryEnabled"]`))' '个性化页面'
    $before = Invoke-Cdp $running.port 'window.__TAURI__.core.invoke("read_desktop_app_settings")'
    if (-not $before.product.memoryEnabled -or $before.product.historyLibraryEnabled) { throw '合成空根初始设置不符。' }
    $clicked = Invoke-Cdp $running.port @'
(()=>{
  const first=document.querySelector('[data-action="toggle-product-setting"][data-key="memoryEnabled"]');
  if(!first)return false;
  first.click();
  const second=document.querySelector('[data-action="toggle-product-setting"][data-key="historyLibraryEnabled"]');
  if(!second)return false;
  second.click();
  return true;
})()
'@
    if (-not $clicked) { throw '连续设置点击失败。' }
    $visible = Invoke-Cdp $running.port '(()=>({memory:document.querySelector(`[data-action="toggle-product-setting"][data-key="memoryEnabled"]`)?.getAttribute("aria-checked"),history:document.querySelector(`[data-action="toggle-product-setting"][data-key="historyLibraryEnabled"]`)?.getAttribute("aria-checked")}))()'
    if ($visible.memory -ne 'false' -or $visible.history -ne 'true') { throw '连续点击后的界面选择不符。' }
    Wait-Cdp $running.port '(async()=>{const x=await window.__TAURI__.core.invoke("read_desktop_app_settings");return x.revision>=2 && x.product.memoryEnabled===false && x.product.historyLibraryEnabled===true})()' '双设置落盘'
    if ($Appearance) {
        $appearanceClicked = Invoke-Cdp $running.port @'
(()=>{
  const choose=(kind,value)=>{
    const row=document.querySelector(`[data-action="open-settings-picker"][data-picker="${kind}"]`);
    if(!row)return false;
    row.click();
    const option=document.querySelector(`[data-action="select-settings-picker-option"][data-picker="${kind}"][data-value="${value}"]`);
    if(!option)return false;
    option.click();
    return true;
  };
  return choose('themeColor','blue') && choose('fontSize','large');
})()
'@
        if (-not $appearanceClicked) { throw '主题色与字号可见选择失败。' }
        Wait-Cdp $running.port '(async()=>{const x=await window.__TAURI__.core.invoke("read_desktop_app_settings");return x.appearance.themeColor==="blue" && x.appearance.fontSize==="large"})()' '外观双设置落盘'
        Wait-Cdp $running.port '(()=>{const root=document.documentElement;return root.dataset.themeColor==="blue" && root.dataset.fontSize==="large" && getComputedStyle(root).getPropertyValue("--accent-orange").trim()==="#357ae8" && getComputedStyle(root).getPropertyValue("--app-font-scale").trim()==="1.24"})()' '外观实际样式投影'
    }
    Stop-Candidate $launch
    $launch = $null
    $running = Start-Candidate $root
    $launch = $running.launch
    $pids += [int]$launch.pid
    if ([IO.Path]::GetFullPath([string]$launch.root) -ne $root) { throw '冷启根不符。' }
    $after = Invoke-Cdp $running.port 'window.__TAURI__.core.invoke("read_desktop_app_settings")'
    if ($after.product.memoryEnabled -ne $false -or $after.product.historyLibraryEnabled -ne $true) { throw '同根冷启设置回读不符。' }
    if ($Appearance -and ($after.appearance.themeColor -ne 'blue' -or $after.appearance.fontSize -ne 'large')) { throw '同根冷启外观回读不符。' }
    if ($Appearance) { Wait-Cdp $running.port '(()=>{const root=document.documentElement;return root.dataset.themeColor==="blue" && root.dataset.fontSize==="large" && getComputedStyle(root).getPropertyValue("--accent-orange").trim()==="#357ae8" && getComputedStyle(root).getPropertyValue("--app-font-scale").trim()==="1.24"})()' '冷启外观实际样式' }
    [pscustomobject]@{ candidateSha256 = $receipt.sha256; root = $root; beforeRevision = $before.revision; afterRevision = $after.revision; rapidVisibleSelection = $true; coldMemoryEnabled = $after.product.memoryEnabled; coldHistoryLibraryEnabled = $after.product.historyLibraryEnabled; coldThemeColor = $(if ($Appearance) { $after.appearance.themeColor } else { $null }); coldFontSize = $(if ($Appearance) { $after.appearance.fontSize } else { $null }); pids = $pids } | ConvertTo-Json -Compress
} finally {
    Stop-Candidate $launch
}
