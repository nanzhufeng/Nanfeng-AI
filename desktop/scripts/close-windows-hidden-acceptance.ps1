param(
    [Parameter(Mandatory)][int]$ProcessId,
    [Parameter(Mandatory)][string]$Executable,
    [Parameter(Mandatory)][string]$Sha256,
    [Parameter(Mandatory)][datetime]$StartedAt,
    [switch]$InspectOnly
)
$ErrorActionPreference = 'Stop'
$expected = [IO.Path]::GetFullPath($Executable)
if ($expected -notmatch '\\windows-acceptance-binaries\\[A-Fa-f0-9]{64}\\nanfeng-ai-desktop-spike\.exe$') { throw 'Not an isolated acceptance executable' }
$process = Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId"
if (!$process -or [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $expected -or
    (Get-FileHash -LiteralPath $expected -Algorithm SHA256).Hash -ne $Sha256 -or
    [Math]::Abs(((Get-Process -Id $ProcessId).StartTime.ToUniversalTime() - $StartedAt.ToUniversalTime()).TotalMilliseconds) -gt 20) {
    throw 'PID identity mismatch'
}
Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
public static class NanfengHiddenClose {
    public delegate bool Callback(IntPtr h, IntPtr arg);
    [DllImport("user32.dll")] static extern bool EnumWindows(Callback callback, IntPtr arg);
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] static extern int GetWindowText(IntPtr h, System.Text.StringBuilder text, int count);
    [DllImport("user32.dll", SetLastError=true)] public static extern bool PostMessage(IntPtr h, uint message, IntPtr w, IntPtr l);
    public static IntPtr[] MainWindows(uint pid, out int visible) {
        var found = new List<IntPtr>(); int count = 0;
        EnumWindows((h,arg) => {
            uint owner; GetWindowThreadProcessId(h,out owner);
            if (owner == pid) {
                var name = new System.Text.StringBuilder(256); GetWindowText(h,name,256);
                if (name.ToString() == "南枫 AI · 隔离测试（非日常版）") {
                    if (IsWindowVisible(h)) count++;
                    found.Add(h);
                }
            }
            return true;
        },IntPtr.Zero);
        visible=count; return found.ToArray();
    }
}
'@
$visibleCount = 0
$windows = [NanfengHiddenClose]::MainWindows($ProcessId,[ref]$visibleCount)
if ($visibleCount -ne 0 -or $windows.Count -ne 1) { throw "Expected one hidden Tauri window; found $($windows.Count), visible $visibleCount" }
if ($InspectOnly) {
    @{ pid=$ProcessId; visibleWindows=$visibleCount; closePosted=$false } | ConvertTo-Json -Compress
    exit 0
}
# WM_CLOSE follows the app's normal close handler without showing or activating it.
if (![NanfengHiddenClose]::PostMessage($windows[0],0x0010,[IntPtr]::Zero,[IntPtr]::Zero)) { throw 'WM_CLOSE failed' }
@{ pid=$ProcessId; visibleWindows=$visibleCount; closePosted=$true } | ConvertTo-Json -Compress

