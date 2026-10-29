param([string]$RootParent = 'D:\CodexTemp', [switch]$ColdStart)
$ErrorActionPreference = 'Stop'
$desktopRoot = Split-Path -Parent $PSScriptRoot
$session = & (Join-Path $PSScriptRoot 'prepare-windows-share-ui-session.ps1') -RootParent $RootParent | ConvertFrom-Json
$launch = $session.launch
$root = $launch.root
$result = [ordered]@{ candidate=$session.candidate; root=$root; pid=$launch.pid; layer='NATIVE_SHARE_HOST_CLOSE'; receiverSelected=$false; full13Complete=$false }
Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
if (-not ('NfShareHostCancel' -as [type])) {
 Add-Type @'
using System;
using System.Text;
using System.Collections.Generic;
using System.Runtime.InteropServices;
public static class NfShareHostCancel {
 public delegate bool EnumProc(IntPtr hwnd, IntPtr param);
 [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
 [DllImport("user32.dll")] public static extern IntPtr GetAncestor(IntPtr hwnd, uint flags);
 [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr hwnd, uint message, IntPtr wparam, IntPtr lparam);
 [DllImport("user32.dll")] public static extern bool EnumChildWindows(IntPtr hwnd, EnumProc callback, IntPtr param);
 [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetClassName(IntPtr hwnd, StringBuilder buffer, int length);
 [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr hwnd, StringBuilder buffer, int length);
 [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint pid);
 public static string Class(IntPtr hwnd) { var b=new StringBuilder(256); GetClassName(hwnd,b,256); return b.ToString(); }
 public static string Title(IntPtr hwnd) { var b=new StringBuilder(256); GetWindowText(hwnd,b,256); return b.ToString(); }
 public static int Pid(IntPtr hwnd) { uint p; GetWindowThreadProcessId(hwnd,out p); return (int)p; }
 public static IntPtr[] ShareChildren(IntPtr hwnd) {
  var found=new List<IntPtr>();
  EnumChildWindows(hwnd,(child,param)=> {
   if(Class(child)=="Windows.UI.Core.CoreWindow" && (Title(child)=="共享" || Title(child)=="Share")) found.Add(child);
   return true;
  },IntPtr.Zero);
  return found.ToArray();
 }
}
'@
}
function Read-ShareEvents {
 return @(Get-Content -LiteralPath (Join-Path $root 'startup.stderr.log') -ErrorAction SilentlyContinue | Where-Object { $_ -match '^windows-share:' })
}
function Read-Leases {
 return @(Get-ChildItem -LiteralPath (Join-Path $root 'share-outbox') -File -Recurse -ErrorAction SilentlyContinue)
}
try {
 $process = Get-Process -Id $launch.pid
 if ([IO.Path]::GetFullPath($process.Path) -ne [IO.Path]::GetFullPath($launch.executable) -or (Get-FileHash -LiteralPath $process.Path -Algorithm SHA256).Hash -ne $session.candidate) { throw '候选 PID 身份不符。' }
 if ($ColdStart) {
  $result.importPid=$launch.pid
  if (-not [NfShareHostCancel]::PostMessage($process.MainWindowHandle,0x0010,[IntPtr]::Zero,[IntPtr]::Zero)) { throw '导入后正常关闭请求失败。' }
  $deadline=[DateTime]::UtcNow.AddSeconds(10)
  while ((Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
  if (Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) { throw '导入进程未正常退出，不能算冷启。' }
  foreach ($stream in @('stdout','stderr')) {
   $before=Join-Path $root "startup-before-share-cold.$stream.log"
   if (Test-Path -LiteralPath $before) { throw '拒绝覆盖已有冷启前日志。' }
   Copy-Item -LiteralPath (Join-Path $root "startup.$stream.log") -Destination $before
  }
  $reopenArgs=@{RootParent=$RootParent;Root=$root;StartupMode='normal';Visible=$true;DebugPort=$launch.debugPort}
  $reopened=& (Join-Path $PSScriptRoot 'start-windows-acceptance.ps1') @reopenArgs | ConvertFrom-Json
  $launch=$reopened
  $result.pid=$launch.pid
  $result.coldStart=$true
  $deadline=[DateTime]::UtcNow.AddSeconds(15)
  do {
   $process=Get-Process -Id $launch.pid -ErrorAction SilentlyContinue
   if ($process -and $process.MainWindowTitle -eq '南枫 AI · 隔离测试（非日常版）') { break }
   Start-Sleep -Milliseconds 100
  } while ([DateTime]::UtcNow -lt $deadline)
  if (-not $process -or [IO.Path]::GetFullPath($process.Path) -ne [IO.Path]::GetFullPath($launch.executable) -or (Get-FileHash -LiteralPath $process.Path -Algorithm SHA256).Hash -ne $session.candidate) { throw '冷启 PID 身份不符。' }
  if ($process.MainWindowTitle -ne '南枫 AI · 隔离测试（非日常版）') { throw '冷启主窗口未就绪。' }
 }
 $window = [System.Windows.Automation.AutomationElement]::FromHandle($process.MainWindowHandle)
 if ($window.Current.Name -ne '南枫 AI · 隔离测试（非日常版）') { throw '隔离主窗口标题不符。' }
 $condition = [System.Windows.Automation.PropertyCondition]::new([System.Windows.Automation.AutomationElement]::IsKeyboardFocusableProperty,$true)
 $pane = $window.FindFirst([System.Windows.Automation.TreeScope]::Descendants,$condition)
 if (-not $pane -or $pane.Current.ControlType -ne [System.Windows.Automation.ControlType]::Pane) { throw '未找到可接收焦点的 WebView Pane。' }
 $pane.SetFocus()
 Start-Sleep -Milliseconds 750
 if ([NfShareHostCancel]::GetForegroundWindow() -ne $process.MainWindowHandle) { throw '主窗口前台焦点未确认。' }
 $result.focusMethod='UIAutomation WebView Pane SetFocus with foreground HWND verification'
 $argsJson=@{args=@{workspaceId=$session.seed.workspaceId;attachmentId=$session.seed.attachmentId}} | ConvertTo-Json -Compress
 $expression="window.__TAURI__.core.invoke('share_desktop_attachment',$argsJson,{headers:{'x-nanfeng-data-area':'CHAT'}}).then(()=>true)"
 $output=$expression | & node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs') $launch.debugPort
 if ($LASTEXITCODE -ne 0 -or ($output | ConvertFrom-Json) -ne $true) { throw '真实分享 IPC 未成功。' }
 $deadline=[DateTime]::UtcNow.AddSeconds(8)
 do { $events=Read-ShareEvents; if ($events -contains 'windows-share:package-ready') { break }; Start-Sleep -Milliseconds 100 } while ([DateTime]::UtcNow -lt $deadline)
 if ($events -notcontains 'windows-share:package-ready') { throw '没有实际 DataRequested/package-ready。' }
 $leases=Read-Leases
 if ($leases.Count -ne 1 -or $leases[0].Length -ne 43) { throw '公开合成分享租约不是唯一 43B。' }
 $result.leaseBytes=$leases[0].Length
 $result.leaseHash=(Get-FileHash -LiteralPath $leases[0].FullName -Algorithm SHA256).Hash
 $frame=[NfShareHostCancel]::GetForegroundWindow()
 $children=[NfShareHostCancel]::ShareChildren($frame)
 if ([NfShareHostCancel]::Class($frame) -ne 'ApplicationFrameWindow' -or $children.Count -ne 1 -or [NfShareHostCancel]::GetAncestor($children[0],2) -ne $frame) { throw '未唯一识别真实分享宿主及其子窗口。' }
 $hostPid=[NfShareHostCancel]::Pid($frame)
 if ((Get-Process -Id $hostPid).ProcessName -ne 'explorer') { throw '分享宿主不是系统 Explorer。' }
 $result.shareFrameHwnd=$frame.ToInt64()
 $result.shareChildHwnd=$children[0].ToInt64()
 $result.shareHostPid=$hostPid
 $result.shareChildPid=[NfShareHostCancel]::Pid($children[0])
 $result.cancelInput='WM_CLOSE to verified Explorer parent of Share CoreWindow; not an X-button click'
 if (-not [NfShareHostCancel]::PostMessage($frame,0x0010,[IntPtr]::Zero,[IntPtr]::Zero)) { throw '系统分享宿主关闭请求失败。' }
 $deadline=[DateTime]::UtcNow.AddSeconds(8)
 do { $events=Read-ShareEvents; $remaining=Read-Leases; if ($events -contains 'windows-share:canceled' -and $remaining.Count -eq 0) { break }; Start-Sleep -Milliseconds 100 } while ([DateTime]::UtcNow -lt $deadline)
 $result.cancelCallback=($events -contains 'windows-share:canceled')
 $result.leasesAfterCancel=$remaining.Count
 if (-not $result.cancelCallback -or $remaining.Count -ne 0) { throw '真实系统取消回调及租约清理未通过。' }
 $result.cancelLifecyclePassed=$true
} catch {
 $result.error=$_.Exception.Message
 throw
} finally {
 $result.events=Read-ShareEvents
 $process=Get-Process -Id $launch.pid -ErrorAction SilentlyContinue
 if ($process -and [IO.Path]::GetFullPath($process.Path) -eq [IO.Path]::GetFullPath($launch.executable) -and (Get-FileHash -LiteralPath $process.Path -Algorithm SHA256).Hash -eq $session.candidate) {
  $result.normalCloseRequested=[NfShareHostCancel]::PostMessage($process.MainWindowHandle,0x0010,[IntPtr]::Zero,[IntPtr]::Zero)
  $deadline=[DateTime]::UtcNow.AddSeconds(10)
  while ((Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
  $result.normalExit= -not [bool](Get-Process -Id $launch.pid -ErrorAction SilentlyContinue)
  if (-not $result.normalExit) { Stop-Process -Id $launch.pid -Force; $result.forcedStop=$true }
 }
 $result.outboxRemoved= -not (Test-Path -LiteralPath (Join-Path $root 'share-outbox'))
 $json=$result | ConvertTo-Json -Depth 8
 $json | Set-Content -LiteralPath (Join-Path $root 'share-host-cancel-receipt.json') -Encoding utf8
 Write-Output $json
}
