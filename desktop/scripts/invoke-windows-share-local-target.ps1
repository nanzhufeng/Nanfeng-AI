param([Parameter(Mandatory)][string]$Root,[ValidateSet('Notepad','Cancel')][string]$Action='Notepad',[string]$SessionPath,[string]$LeasePath,[switch]$DenyReceiverRead)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type @'
using System;using System.Text;using System.Collections.Generic;using System.Runtime.InteropServices;
public static class NFLocalShareTarget {
 public delegate bool E(IntPtr h,IntPtr p);
 [DllImport("user32.dll")]public static extern IntPtr GetForegroundWindow();
 [DllImport("user32.dll")]public static extern IntPtr GetAncestor(IntPtr h,uint f);
 [DllImport("user32.dll")]public static extern bool EnumChildWindows(IntPtr h,E e,IntPtr p);
 [DllImport("user32.dll")]public static extern uint GetWindowThreadProcessId(IntPtr h,out uint p);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)]static extern int GetWindowText(IntPtr h,StringBuilder b,int n);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)]static extern int GetClassName(IntPtr h,StringBuilder b,int n);
 public static string T(IntPtr h){var b=new StringBuilder(256);GetWindowText(h,b,256);return b.ToString();}
 public static string C(IntPtr h){var b=new StringBuilder(256);GetClassName(h,b,256);return b.ToString();}
 public static IntPtr[] Children(IntPtr h){var r=new List<IntPtr>();EnumChildWindows(h,(c,p)=>{if(C(c)=="Windows.UI.Core.CoreWindow"&&(T(c)=="共享"||T(c)=="Share"))r.Add(c);return true;},IntPtr.Zero);return r.ToArray();}
}
'@
if (-not $SessionPath) {$SessionPath=Join-Path $Root 'share-ui-manual-session.json'}
$session=Get-Content -LiteralPath $SessionPath -Raw -Encoding utf8|ConvertFrom-Json
if ([IO.Path]::GetFullPath($session.launch.root) -ne [IO.Path]::GetFullPath($Root)) {throw '会话根身份不符'}
$process=Get-Process -Id $session.launch.pid
if ([IO.Path]::GetFullPath($process.Path) -ne [IO.Path]::GetFullPath($session.launch.executable) -or (Get-FileHash $process.Path).Hash -ne $session.candidate) {throw '分享源身份不符'}
if (-not (Get-Content -LiteralPath (Join-Path $Root 'startup.stderr.log')|Where-Object{$_ -eq 'windows-share:package-ready'})) {throw '实际分享包未就绪'}
$frame=[NFLocalShareTarget]::GetForegroundWindow()
$children=[NFLocalShareTarget]::Children($frame)
[uint32]$shareHostPid=0
[NFLocalShareTarget]::GetWindowThreadProcessId($frame,[ref]$shareHostPid)|Out-Null
if ([NFLocalShareTarget]::C($frame) -ne 'ApplicationFrameWindow' -or $children.Count -ne 1 -or [NFLocalShareTarget]::GetAncestor($children[0],2) -ne $frame -or (Get-Process -Id $shareHostPid).ProcessName -ne 'explorer') {throw '真实系统分享宿主身份未确认'}
$window=[System.Windows.Automation.AutomationElement]::FromHandle($children[0])
if ($Action -eq 'Notepad') {
 $condition=[System.Windows.Automation.AndCondition]::new([System.Windows.Automation.PropertyCondition]::new([System.Windows.Automation.AutomationElement]::NameProperty,'记事本'),[System.Windows.Automation.PropertyCondition]::new([System.Windows.Automation.AutomationElement]::ControlTypeProperty,[System.Windows.Automation.ControlType]::ListItem))
} else {
 $condition=[System.Windows.Automation.PropertyCondition]::new([System.Windows.Automation.AutomationElement]::AutomationIdProperty,'SharePickerCloseButtonTabStop')
}
$target=$window.FindFirst([System.Windows.Automation.TreeScope]::Descendants,$condition)
# Package readiness precedes asynchronous enumeration of local share targets.
# Wait only on this verified system host; do not invoke another target as fallback.
$targetDeadline=[DateTime]::UtcNow.AddSeconds(8)
while (-not $target -and [DateTime]::UtcNow -lt $targetDeadline) {
 if ([NFLocalShareTarget]::GetForegroundWindow() -ne $frame) {throw '等待目标期间系统分享宿主失去前台身份'}
 Start-Sleep -Milliseconds 100
 $target=$window.FindFirst([System.Windows.Automation.TreeScope]::Descendants,$condition)
}
if (-not $target) {throw '指定本地目标或取消控件不存在'}
$allLeases=@(Get-ChildItem -LiteralPath (Join-Path $Root 'share-outbox') -File -Recurse|Where-Object{$_.Name -ne '.share-handoff-v1'})
$leases=@($allLeases|Where-Object{$_.Directory.Name -like "lease-$($process.Id)-*"})
if ($LeasePath) {
 $selectedPath=[IO.Path]::GetFullPath($LeasePath)
 $leases=@($leases|Where-Object{[IO.Path]::GetFullPath($_.FullName) -eq $selectedPath})
 if ($leases.Count -ne 1) {throw '指定租约不属于当前隔离源进程'}
}
if ($leases.Count -ne 1 -or $leases[0].Length -ne 43 -or (Get-FileHash $leases[0].FullName).Hash -ne '3F3FA3E4842FD4D54BE489E980058C1AAF1BD89951DD0DD521E29F0B81639969') {throw '不是唯一公开43B合成附件'}
$receipt=[ordered]@{candidate=$session.candidate;sourcePid=$process.Id;action=$Action;frame=$frame.ToInt64();child=$children[0].ToInt64();hostPid=$shareHostPid;leasePath=$leases[0].FullName;leaseBytes=43;otherPreservedLeaseCount=$allLeases.Count-$leases.Count;utc=[DateTime]::UtcNow.ToString('o')}
$receiptPath=Join-Path $Root ('share-local-target.'+[guid]::NewGuid().ToString()+'.json')
$receipt|ConvertTo-Json|Set-Content -LiteralPath $receiptPath -Encoding utf8
if($DenyReceiverRead -and $Action -ne 'Notepad'){throw '接收拒读探针只允许公开记事本目标'}
$readLock=$null
try {
 if($DenyReceiverRead){
  # Real target fault injection: deny access to this already validated public
  # lease only, without deleting bytes or changing any filesystem permissions.
  $readLock=[IO.File]::Open($leases[0].FullName,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::None)
  $receipt.receiverReadDenied=$true
 }
 ([System.Windows.Automation.InvokePattern]$target.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern)).Invoke()
 if($DenyReceiverRead){Start-Sleep -Seconds 5}
} finally {
 if($readLock){$readLock.Dispose();$receipt.receiverReadLockReleased=$true}
 $receipt|ConvertTo-Json|Set-Content -LiteralPath $receiptPath -Encoding utf8
}
$receipt|ConvertTo-Json
