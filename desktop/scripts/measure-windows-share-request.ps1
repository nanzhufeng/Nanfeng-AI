param([Parameter(Mandatory)][string]$SessionPath, [switch]$KeepShareOpen)
$ErrorActionPreference = 'Stop'
$session = Get-Content -LiteralPath $SessionPath -Raw -Encoding utf8 | ConvertFrom-Json
$launch = $session.launch
$root = $launch.root
$evidencePath = Join-Path $root ('share-request-timing.' + [guid]::NewGuid().ToString() + '.json')
$result = [ordered]@{candidate=$session.candidate;root=$root;pid=$launch.pid;receiverSelected=$false;full13Complete=$false}
Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type @'
using System;using System.Text;using System.Collections.Generic;using System.Runtime.InteropServices;
public static class NFRequestTiming {
 public delegate bool E(IntPtr h,IntPtr p);
 [DllImport("user32.dll")]public static extern IntPtr GetForegroundWindow();
 [DllImport("user32.dll")]public static extern IntPtr GetAncestor(IntPtr h,uint f);
 [DllImport("user32.dll")]public static extern bool SetForegroundWindow(IntPtr h);
 [DllImport("user32.dll")]public static extern bool EnumChildWindows(IntPtr h,E e,IntPtr p);
 [DllImport("user32.dll")]public static extern uint GetWindowThreadProcessId(IntPtr h,out uint p);
 [DllImport("user32.dll")]public static extern bool PostMessage(IntPtr h,uint m,IntPtr w,IntPtr l);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)]static extern int GetWindowText(IntPtr h,StringBuilder b,int n);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)]static extern int GetClassName(IntPtr h,StringBuilder b,int n);
 public static string Title(IntPtr h){var b=new StringBuilder(256);GetWindowText(h,b,256);return b.ToString();}
 public static string Class(IntPtr h){var b=new StringBuilder(256);GetClassName(h,b,256);return b.ToString();}
 public static IntPtr[] Children(IntPtr h){var r=new List<IntPtr>();EnumChildWindows(h,(c,p)=>{if(Class(c)=="Windows.UI.Core.CoreWindow"&&(Title(c)=="共享"||Title(c)=="Share"))r.Add(c);return true;},IntPtr.Zero);return r.ToArray();}
}
'@
function Read-Events { @(Get-Content -LiteralPath (Join-Path $root 'startup.stderr.log') -ErrorAction SilentlyContinue | Where-Object {$_ -like 'windows-share:*'}) }
try {
 $process=Get-Process -Id $launch.pid
 if ([IO.Path]::GetFullPath($process.Path) -ne [IO.Path]::GetFullPath($launch.executable) -or (Get-FileHash $process.Path).Hash -ne $session.candidate) {throw '候选身份不符'}
 if ($process.MainWindowTitle -ne '南枫 AI · 隔离测试（非日常版）') {throw '隔离窗口身份不符'}
 $window=[System.Windows.Automation.AutomationElement]::FromHandle($process.MainWindowHandle)
 $condition=[System.Windows.Automation.AndCondition]::new(
  [System.Windows.Automation.PropertyCondition]::new([System.Windows.Automation.AutomationElement]::IsKeyboardFocusableProperty,$true),
  [System.Windows.Automation.PropertyCondition]::new([System.Windows.Automation.AutomationElement]::ControlTypeProperty,[System.Windows.Automation.ControlType]::Pane))
 $pane=$window.FindFirst([System.Windows.Automation.TreeScope]::Descendants,$condition)
 if (-not $pane -or $pane.Current.ControlType -ne [System.Windows.Automation.ControlType]::Pane) {throw 'WebView Pane不存在'}
 $pane.SetFocus()
 $foreground=[NFRequestTiming]::GetForegroundWindow()
 if ($foreground -ne $process.MainWindowHandle -and [NFRequestTiming]::GetAncestor($foreground,2) -eq $process.MainWindowHandle) {
  $result.foregroundWasOwnedWebView=$foreground.ToInt64()
  $result.promotedOwnedForeground=[NFRequestTiming]::SetForegroundWindow($process.MainWindowHandle)
  Start-Sleep -Milliseconds 250
 }
 if ([NFRequestTiming]::GetForegroundWindow() -ne $process.MainWindowHandle) {
  $windowPattern=[System.Windows.Automation.WindowPattern]$window.GetCurrentPattern([System.Windows.Automation.WindowPattern]::Pattern)
  $windowPattern.SetWindowVisualState([System.Windows.Automation.WindowVisualState]::Minimized)
  $windowPattern.SetWindowVisualState([System.Windows.Automation.WindowVisualState]::Normal)
  $result.restoredIsolatedWindow=[NFRequestTiming]::SetForegroundWindow($process.MainWindowHandle)
  Start-Sleep -Milliseconds 250
 }
 if ([NFRequestTiming]::GetForegroundWindow() -ne $process.MainWindowHandle) {throw '真实前台未确认'}
 $baseline=(Read-Events).Count
 $clock=[Diagnostics.Stopwatch]::StartNew()
 $arguments=@{args=@{workspaceId=$session.seed.workspaceId;attachmentId=$session.seed.attachmentId}}|ConvertTo-Json -Compress
 "window.__TAURI__.core.invoke('share_desktop_attachment',$arguments,{headers:{'x-nanfeng-data-area':'CHAT'}}).then(()=>true)" | node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs') $launch.debugPort | Out-Null
 if ($LASTEXITCODE -ne 0) {throw '分享IPC失败'}
 $result.ipcReturnedMs=$clock.ElapsedMilliseconds
 $samples=[Collections.Generic.List[object]]::new()
 $lastEvents=''
 do {
  $events=@(Read-Events | Select-Object -Skip $baseline)
  $signature=$events -join ','
  if ($signature -ne $lastEvents) {$samples.Add(@{elapsedMs=$clock.ElapsedMilliseconds;events=$events});$lastEvents=$signature}
  if ($events -contains 'windows-share:package-ready') {break}
  Start-Sleep -Milliseconds 100
 } while ($clock.Elapsed.TotalSeconds -lt 45)
 $result.samples=$samples.ToArray()
 $result.packageReadyMs=$clock.ElapsedMilliseconds
 if ($events -notcontains 'windows-share:package-ready') {throw '45秒内未收到package-ready'}
 $frame=[NFRequestTiming]::GetForegroundWindow()
 $children=[NFRequestTiming]::Children($frame)
 [uint32]$shareHostPid=0
 [NFRequestTiming]::GetWindowThreadProcessId($frame,[ref]$shareHostPid)|Out-Null
 if ([NFRequestTiming]::Class($frame) -ne 'ApplicationFrameWindow' -or $children.Count -ne 1 -or [NFRequestTiming]::GetAncestor($children[0],2) -ne $frame -or (Get-Process -Id $shareHostPid).ProcessName -ne 'explorer') {throw '系统分享宿主身份未确认'}
 $result.hostPid=$shareHostPid;$result.frame=$frame.ToInt64();$result.child=$children[0].ToInt64()
 if ($KeepShareOpen) {$result.keptOpenForReceiver=$true; return}
 $leases=@(Get-ChildItem -LiteralPath (Join-Path $root 'share-outbox') -Recurse -File|Where-Object{$_.Name -ne '.share-handoff-v1'})
 if ($leases.Count -ne 1 -or $leases[0].Length -ne 43) {throw '租约不是唯一43B'}
 $result.leaseHash=(Get-FileHash $leases[0].FullName).Hash
 $result.hostCloseRequested=[NFRequestTiming]::PostMessage($frame,16,[IntPtr]::Zero,[IntPtr]::Zero)
 $deadline=[DateTime]::UtcNow.AddSeconds(10)
 do {
  $events=@(Read-Events|Select-Object -Skip $baseline)
  $remaining=@(Get-ChildItem -LiteralPath (Join-Path $root 'share-outbox') -Recurse -File -ErrorAction SilentlyContinue|Where-Object{$_.Name -ne '.share-handoff-v1'}).Count
  if ($events -contains 'windows-share:canceled' -and $remaining -eq 0) {break}
  Start-Sleep -Milliseconds 100
 } while ([DateTime]::UtcNow -lt $deadline)
 $result.cancelCallback=$events -contains 'windows-share:canceled';$result.remainingLeases=$remaining
 if (-not $result.cancelCallback -or $remaining -ne 0) {throw '取消生命周期未通过'}
} catch {$result.error=$_.Exception.Message;throw} finally {
 $result.events=Read-Events
 $process=Get-Process -Id $launch.pid -ErrorAction SilentlyContinue
 if ((-not $KeepShareOpen -or $result.error) -and $process -and [IO.Path]::GetFullPath($process.Path) -eq [IO.Path]::GetFullPath($launch.executable) -and (Get-FileHash $process.Path).Hash -eq $session.candidate) {
  $result.normalCloseRequested=[NFRequestTiming]::PostMessage($process.MainWindowHandle,16,[IntPtr]::Zero,[IntPtr]::Zero)
  $deadline=[DateTime]::UtcNow.AddSeconds(10)
  while ((Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) {Start-Sleep -Milliseconds 100}
  $result.normalExit= -not [bool](Get-Process -Id $launch.pid -ErrorAction SilentlyContinue)
  if (-not $result.normalExit) {Stop-Process -Id $launch.pid -Force;$result.forcedStop=$true}
 }
 $result|ConvertTo-Json -Depth 6|Set-Content -LiteralPath $evidencePath -Encoding utf8
 $result|ConvertTo-Json -Depth 6
}
