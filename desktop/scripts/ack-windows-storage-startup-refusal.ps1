param([Parameter(Mandatory)][int]$ProcessId,[Parameter(Mandatory)][string]$Executable,[Parameter(Mandatory)][string]$Sha256,[Parameter(Mandatory)][string]$Root)
$ErrorActionPreference='Stop'
$resolvedRoot=[IO.Path]::GetFullPath($Root)
if(!$resolvedRoot.StartsWith('D:\CodexTemp\nfa-storage-startup-refusal\',[StringComparison]::OrdinalIgnoreCase)){throw 'Only owned synthetic storage refusal roots allowed'}
$marker=Get-Content -LiteralPath (Join-Path $resolvedRoot '.nanfeng-windows-acceptance.json') -Raw -Encoding utf8 | ConvertFrom-Json
if($marker.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance'){throw 'Root marker mismatch'}
$p=Get-Process -Id $ProcessId -ErrorAction Stop
if([IO.Path]::GetFullPath($p.Path) -ne [IO.Path]::GetFullPath($Executable) -or (Get-FileHash -LiteralPath $p.Path).Hash -ne $Sha256){throw 'Owned Native identity mismatch'}
. (Join-Path $PSScriptRoot 'windows-file-dialog-handles.ps1')
Add-Type @'
using System;using System.Text;using System.Runtime.InteropServices;
public static class NFAStorageFailureDialog{
[DllImport("user32.dll",CharSet=CharSet.Unicode)]static extern int GetWindowTextW(IntPtr h,StringBuilder b,int n);
[DllImport("user32.dll",EntryPoint="SendMessageW")]public static extern IntPtr Send(IntPtr h,uint m,IntPtr w,IntPtr l);
[DllImport("kernel32.dll")]public static extern IntPtr OpenProcess(uint a,bool inherit,uint pid);
[DllImport("kernel32.dll")]public static extern uint WaitForSingleObject(IntPtr h,uint milliseconds);
[DllImport("kernel32.dll")]public static extern bool GetExitCodeProcess(IntPtr h,out uint code);
[DllImport("kernel32.dll")]public static extern bool CloseHandle(IntPtr h);
public static string Text(IntPtr h){var b=new StringBuilder(8192);GetWindowTextW(h,b,b.Capacity);return b.ToString();}}
'@
$deadline=[DateTime]::UtcNow.AddSeconds(30)
do{$dialogs=@([NanfengNativeDialog]::Dialogs([uint32]$ProcessId));if($dialogs.Count -eq 1){break};Start-Sleep -Milliseconds 100}while([DateTime]::UtcNow -lt $deadline)
if($dialogs.Count -ne 1){throw 'Unique owned startup refusal dialog absent'}
$title=[NFAStorageFailureDialog]::Text($dialogs[0]);$children=@([NanfengNativeDialog]::Children($dialogs[0]));$body=(@($children | Where-Object {[NanfengNativeDialog]::ClassName($_) -eq 'Static'} | ForEach-Object {[NFAStorageFailureDialog]::Text($_)}) -join "`n")
if($title -ne '南枫 AI · 无法启动' -or !$body.Contains('本机数据路径校验或迁移未完成') -or !$body.Contains('请保留原数据目录、目标目录和路径配置') -or $body.Contains($resolvedRoot)){throw 'Honest safe storage failure text mismatch'}
$controls=@($children | ForEach-Object {@{handle=$_.ToInt64();class=[NanfengNativeDialog]::ClassName($_);id=[NanfengNativeDialog]::GetDlgCtrlID($_);enabled=[NanfengNativeDialog]::IsWindowEnabled($_);text=[NFAStorageFailureDialog]::Text($_)}})
$controls|ConvertTo-Json -Depth 4|Set-Content -LiteralPath (Join-Path $resolvedRoot 'storage-startup-dialog-controls.json') -Encoding utf8
$buttons=@($children | Where-Object {[NanfengNativeDialog]::ClassName($_) -eq 'Button' -and [NFAStorageFailureDialog]::Text($_) -in @('确定','确定(&O)','OK','&OK') -and [NanfengNativeDialog]::IsWindowEnabled($_)})
if($buttons.Count -ne 1){throw 'Owned acknowledgement button mismatch'}
$buttonId=[NanfengNativeDialog]::GetDlgCtrlID($buttons[0])
$handle=[NFAStorageFailureDialog]::OpenProcess(0x00101000,$false,[uint32]$ProcessId)
if($handle -eq [IntPtr]::Zero){throw 'Cannot retain exact source process wait/query handle'}
try{
 [void][NFAStorageFailureDialog]::Send($buttons[0],0x00F5,[IntPtr]::Zero,[IntPtr]::Zero)
 if([NFAStorageFailureDialog]::WaitForSingleObject($handle,30000) -ne 0){throw 'Acknowledged source failed to exit'}
 [uint32]$exitCode=0
 if(![NFAStorageFailureDialog]::GetExitCodeProcess($handle,[ref]$exitCode) -or $exitCode -ne 2){throw "Expected honest exit code 2, got $exitCode"}
 @{pid=$ProcessId;title=$title;message=$body;exitCode=$exitCode;acknowledged=$true;buttonId=$buttonId}|ConvertTo-Json -Compress
}finally{[void][NFAStorageFailureDialog]::CloseHandle($handle)}
