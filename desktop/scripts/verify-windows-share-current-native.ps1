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
    $output = $expression | & node --max-old-space-size=2048 $cdpHelper $launch.debugPort 
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
            if ((Invoke-Cdp $launch "Boolean(window.__TAURI__?.core?.invoke&&globalThis.__nanfengDesktopWorkState?.current?.summary?.id&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)") -eq $true) { return $launch }
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
 $first=Start-Acceptance
 $fixtureJson=$fixture | ConvertTo-Json -Compress
 $seed=Invoke-Cdp $first @"
(async()=>{const call=(n,a={})=>window.__TAURI__.core.invoke(n,a,{headers:{'x-nanfeng-data-area':'CHAT'}});const staged=await call('stage_preflight_selected_exchange',{selectedPath:$fixtureJson});const imported=await call('import_staged_exchange_as_new_workspace',{args:{stagingId:staged.stagingId,workspaceTitle:'Synthetic share lifecycle'}});return {workspaceId:imported.summary.id,attachmentId:imported.exchange.conversations.flatMap(c=>c.messages).flatMap(m=>m.blocks).find(b=>b.kind==='ASSET_REF').asset.id}})()
"@
 Add-Type 'using System; using System.Runtime.InteropServices; public static class NfShareWindow { [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hwnd); [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow(); [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr hwnd, uint message, IntPtr wparam, IntPtr lparam); }'
 Add-Type -AssemblyName UIAutomationClient
 Add-Type -AssemblyName UIAutomationTypes
 $pidCondition=[System.Windows.Automation.PropertyCondition]::new([System.Windows.Automation.AutomationElement]::ProcessIdProperty,[int]$first.pid)
 $ownWindows=[System.Windows.Automation.AutomationElement]::RootElement.FindAll([System.Windows.Automation.TreeScope]::Children,$pidCondition)
 $ownMain=@($ownWindows | Where-Object {$_.Current.Name -eq '南枫 AI · 隔离测试（非日常版）' -and $_.Current.ClassName -eq 'Tauri Window'})
 if($ownMain.Count -ne 1){throw '分享前未唯一识别主窗口。'}
 $shareHwnd=[IntPtr]$ownMain[0].Current.NativeWindowHandle
 $ownMain[0].SetFocus()
 [NfShareWindow]::SetForegroundWindow($shareHwnd) | Out-Null
 Start-Sleep -Milliseconds 500
 if([NfShareWindow]::GetForegroundWindow() -ne $shareHwnd){throw '验收主窗口没有获得真实前台焦点。'}
 $argsJson=@{args=@{workspaceId=$seed.workspaceId;attachmentId=$seed.attachmentId}} | ConvertTo-Json -Compress
 Invoke-Cdp $first "window.__TAURI__.core.invoke('share_desktop_attachment',$argsJson,{headers:{'x-nanfeng-data-area':'CHAT'}}).then(()=>true)" | Out-Null
 $requestDeadline=[DateTime]::UtcNow.AddSeconds(8)
 do {
   Start-Sleep -Milliseconds 100
   $requestEvents=@(Get-Content (Join-Path $root 'startup.stderr.log') -ErrorAction SilentlyContinue | Where-Object {$_ -match '^windows-share:'})
 } while($requestEvents -notcontains 'windows-share:package-ready' -and -not ($requestEvents -match 'prepare-failed') -and [DateTime]::UtcNow -lt $requestDeadline)
 $outbox=Join-Path $root 'share-outbox'
 $files=@(Get-ChildItem -LiteralPath $outbox -File -Recurse)
 $files.Count | Set-Content (Join-Path $root 'share-lease-count.txt')
 $leaseHash=if($files.Count -eq 1){(Get-FileHash -LiteralPath $files[0].FullName -Algorithm SHA256).Hash}else{''}
 Add-Type -AssemblyName UIAutomationClient
 Add-Type -AssemblyName UIAutomationTypes
 $windows=[System.Windows.Automation.AutomationElement]::RootElement.FindAll([System.Windows.Automation.TreeScope]::Children,[System.Windows.Automation.Condition]::TrueCondition)
 $inventory=@();$sheets=@()
 foreach($w in $windows){if($w.Current.ProcessId -eq $first.pid -or $w.Current.Name -match '^(共享|分享|Share)$'){$inventory+=@{name=$w.Current.Name;class=$w.Current.ClassName;pid=$w.Current.ProcessId;hwnd=$w.Current.NativeWindowHandle}};if($w.Current.Name -match '^(共享|分享|Share)$'){$sheets+=$w}}
 $inventory | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $root 'share-window-inventory.json') -Encoding utf8
 if($files.Count -ne 1){throw '没有唯一的待分享租约。'}
 $cancelAttempted=$sheets.Count -eq 1
 $ownerFocusDismissAttempted=$false
 if($cancelAttempted){
   $pattern=$sheets[0].GetCurrentPattern([System.Windows.Automation.WindowPattern]::Pattern)
   $pattern.Close()
   Start-Sleep -Milliseconds 1800
 }
 if(-not $cancelAttempted){$ownerFocusDismissAttempted=[NfShareWindow]::SetForegroundWindow($shareHwnd)}
 $cancelDeadline=[DateTime]::UtcNow.AddSeconds(5)
 do {
   $beforeCloseEvents=@(Get-Content (Join-Path $root 'startup.stderr.log') -ErrorAction SilentlyContinue | Where-Object {$_ -match '^windows-share:'})
   $remainingBeforeClose=@(Get-ChildItem -LiteralPath $outbox -File -Recurse -ErrorAction SilentlyContinue)
   if($beforeCloseEvents -contains 'windows-share:canceled' -and $remainingBeforeClose.Count -eq 0){break}
   Start-Sleep -Milliseconds 100
 }while([DateTime]::UtcNow -lt $cancelDeadline)
 [pscustomobject]@{phase='before-normal-close';root=$root;cancelAttempted=$cancelAttempted;ownerFocusDismissAttempted=$ownerFocusDismissAttempted;cancelEventObserved=($beforeCloseEvents -contains 'windows-share:canceled');remainingLeaseCount=$remainingBeforeClose.Count;events=$beforeCloseEvents} | ConvertTo-Json -Depth 5 -Compress
 $after=@(Get-ChildItem -LiteralPath $outbox -File -Recurse -ErrorAction SilentlyContinue)
 $alive=[bool](Get-Process -Id $first.pid -ErrorAction SilentlyContinue)
 $cancelCleaned=($after.Count -eq 0)
 # Closing the owning application is a separate cleanup fallback.
 $main=@($inventory | Where-Object { $_.pid -eq $first.pid -and $_.class -eq 'Tauri Window' -and $_.name -eq '南枫 AI · 隔离测试（非日常版）' })
 if($main.Count -ne 1){throw '无法唯一识别精确验收窗口。'}
 $normalClosed=[NfShareWindow]::PostMessage([IntPtr]$main[0].hwnd,0x0010,[IntPtr]::Zero,[IntPtr]::Zero)
 $deadline=[DateTime]::UtcNow.AddSeconds(10)
 while((Get-Process -Id $first.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 100}
 if(Get-Process -Id $first.pid -ErrorAction SilentlyContinue){Stop-Acceptance $first}else{$running.Remove($first)|Out-Null;$stopped.Add([int]$first.pid)}
 $exitCleaned= -not (Test-Path -LiteralPath $outbox)
 $events=@(Get-Content (Join-Path $root 'startup.stderr.log') | Where-Object {$_ -match '^windows-share:'})
 if(-not $normalClosed -or -not $exitCleaned){throw '正常关闭租约清理未通过。'}
 [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;seed=$seed;leaseHash=$leaseHash;leaseBytes=$files[0].Length;dataRequested=($events -contains 'windows-share:data-requested');packageReady=($events -contains 'windows-share:package-ready');cancelAttempted=$cancelAttempted;cancelEventObserved=($events -contains 'windows-share:canceled');cancelCleaned=$cancelCleaned;processAliveBeforeClose=$alive;normalCloseRequested=$normalClosed;exitCleaned=$exitCleaned;events=$events;integrationStatus='PARTIAL_NOT_ACCEPTED';stoppedPids=$stopped.ToArray()} | ConvertTo-Json -Depth 8
} finally {foreach($launch in @($running.ToArray())){Stop-Acceptance $launch}}
