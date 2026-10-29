param(
    [Parameter(Mandatory)][int]$ProcessId,
    [Parameter(Mandatory)][string]$Executable,
    [Parameter(Mandatory)][string]$Sha256,
    [Parameter(Mandatory)][datetime]$StartedAt
)
$ErrorActionPreference='Stop'
$expected=[IO.Path]::GetFullPath($Executable)
if ($expected -notmatch '\\windows-acceptance-binaries\\[A-Fa-f0-9]{64}\\nanfeng-ai-desktop-spike\.exe$') {throw 'Not an isolated acceptance executable'}
$identity=Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId"
if (!$identity -or [IO.Path]::GetFullPath([string]$identity.ExecutablePath) -ne $expected -or
    (Get-FileHash -LiteralPath $expected -Algorithm SHA256).Hash -ne $Sha256 -or
    [Math]::Abs(((Get-Process -Id $ProcessId).StartTime.ToUniversalTime()-$StartedAt.ToUniversalTime()).TotalMilliseconds) -gt 20) {throw 'PID identity mismatch'}
Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
public static class NanfengIslandInspect {
 public delegate bool Callback(IntPtr h,IntPtr arg);
 [DllImport("user32.dll")] static extern bool EnumWindows(Callback callback,IntPtr arg);
 [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h,out uint pid);
 [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
 [DllImport("user32.dll")] static extern IntPtr GetForegroundWindow();
 [DllImport("user32.dll",EntryPoint="GetWindowLongPtrW")] static extern IntPtr GetStyle(IntPtr h,int index);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] static extern int GetWindowText(IntPtr h,System.Text.StringBuilder text,int count);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)] static extern int GetClassName(IntPtr h,System.Text.StringBuilder text,int count);
 [StructLayout(LayoutKind.Sequential)] public struct Rect { public int left,top,right,bottom; }
 [DllImport("user32.dll")] static extern bool GetWindowRect(IntPtr h,out Rect rect);
 public class VisibleWindow { public string handle; public string className; public Rect bounds; public bool noActivate,layered,transparent,toolWindow,infrastructure; }
 public static bool IsInfrastructure(string name,long style) {
  const long required=0x080800A0; // NOACTIVATE | LAYERED | TRANSPARENT | TOOLWINDOW
  return (name=="Tao Thread Event Target"||name=="com.nanzhufeng.ai.desktop.windowsacceptance-sic")&&(style&required)==required;
 }
 public class ForegroundSnapshot { public bool owned; public string handle; public string className; public bool? visible; }
 // Only inspect metadata after ownership matches the verified acceptance PID.
 // Never record another application's title, handle, class or visibility.
 public static ForegroundSnapshot ReadForeground(uint expected) {
  var h=GetForegroundWindow();uint pid;GetWindowThreadProcessId(h,out pid);
  if(h==IntPtr.Zero||pid!=expected)return new ForegroundSnapshot{owned=false};
  var name=new System.Text.StringBuilder(256);GetClassName(h,name,256);
  return new ForegroundSnapshot{owned=true,handle=h.ToInt64().ToString("X"),className=name.ToString(),visible=IsWindowVisible(h)};
 }
 public static int VisibleOwnedCount(uint expected) {
  int count=0;EnumWindows((h,arg)=>{uint pid;GetWindowThreadProcessId(h,out pid);if(pid==expected&&IsWindowVisible(h))count++;return true;},IntPtr.Zero);return count;
 }
 public static VisibleWindow[] ReadVisibleOwned(uint expected) {
  var rows=new List<VisibleWindow>();EnumWindows((h,arg)=>{uint pid;GetWindowThreadProcessId(h,out pid);if(pid==expected&&IsWindowVisible(h)){var name=new System.Text.StringBuilder(256);GetClassName(h,name,256);Rect bounds;GetWindowRect(h,out bounds);long style=GetStyle(h,-20).ToInt64();rows.Add(new VisibleWindow{handle=h.ToInt64().ToString("X"),className=name.ToString(),bounds=bounds,noActivate=(style&0x08000000)!=0,layered=(style&0x80000)!=0,transparent=(style&0x20)!=0,toolWindow=(style&0x80)!=0,infrastructure=IsInfrastructure(name.ToString(),style)});}return true;},IntPtr.Zero);return rows.ToArray();
 }
 public class Row { public string title;public bool visible;public bool noActivate;public bool toolWindow;public bool appWindow; }
 public static Row[] Read(uint pid) {
  var rows=new List<Row>();EnumWindows((h,arg)=>{uint owner;GetWindowThreadProcessId(h,out owner);if(owner==pid){var text=new System.Text.StringBuilder(256);GetWindowText(h,text,256);var name=text.ToString();if(name=="南枫岛"||name=="南枫 AI · 隔离测试（非日常版）"){long style=GetStyle(h,-20).ToInt64();rows.Add(new Row{title=name,visible=IsWindowVisible(h),noActivate=(style&0x08000000)!=0,toolWindow=(style&0x80)!=0,appWindow=(style&0x40000)!=0});}}return true;},IntPtr.Zero);return rows.ToArray();
 }
}
'@
$rows=@([NanfengIslandInspect]::Read($ProcessId))
$foreground=[NanfengIslandInspect]::ReadForeground($ProcessId)
$visibleOwned=@([NanfengIslandInspect]::ReadVisibleOwned($ProcessId))
@{pid=$ProcessId;windows=$rows;foregroundOwned=$foreground.owned;foregroundWindow=$foreground;visibleOwnedWindowCount=$visibleOwned.Count;visibleOwnedWindows=$visibleOwned;unexpectedVisibleOwnedWindowCount=@($visibleOwned | Where-Object {!$_.infrastructure}).Count} | ConvertTo-Json -Depth 5 -Compress -EscapeHandling EscapeNonAscii
