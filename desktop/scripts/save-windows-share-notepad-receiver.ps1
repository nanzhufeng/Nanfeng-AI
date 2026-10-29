param([Parameter(Mandatory)][string]$Root,[Parameter(Mandatory)][string]$PublicFileName)
$ErrorActionPreference='Stop'
$Root=[IO.Path]::GetFullPath($Root)
if([IO.Path]::GetDirectoryName($Root) -ne 'D:\CodexTemp' -or [IO.Path]::GetFileName($Root) -notlike 'nanfeng-ai-windows-acceptance.*'){throw '仅允许隔离验收根'}
$marker=Get-Content (Join-Path $Root '.nanfeng-windows-acceptance.json') -Raw|ConvertFrom-Json
if($marker.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance' -or $PublicFileName -notmatch '^nfa-public-share-[a-f0-9]{32}\.txt$'){throw '验收身份或公开文件名无效'}
$leases=@(Get-ChildItem (Join-Path $Root 'share-outbox') -Recurse -File|Where-Object{$_.Name -eq $PublicFileName})
$digest='3F3FA3E4842FD4D54BE489E980058C1AAF1BD89951DD0DD521E29F0B81639969'
if($leases.Count -ne 1 -or $leases[0].Length -ne 43 -or (Get-FileHash $leases[0].FullName).Hash -ne $digest){throw '公开租约身份无效'}
if($leases[0].Directory.Name -notmatch '^lease-(\d+)-'){throw '来源PID缺失'}
if(Get-Process -Id ([int]$Matches[1]) -ErrorAction SilentlyContinue){throw '仅验收来源退出后的独立另存'}
Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type @'
using System;using System.Text;using System.Collections.Generic;using System.Runtime.InteropServices;
public static class NFReceiverSave {
 public delegate bool E(IntPtr h,IntPtr p);
 [DllImport("user32.dll")]public static extern bool EnumChildWindows(IntPtr h,E e,IntPtr p);
 [DllImport("user32.dll")]public static extern int GetDlgCtrlID(IntPtr h);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)]static extern int GetClassName(IntPtr h,StringBuilder b,int n);
 [DllImport("user32.dll",EntryPoint="SendMessageW")]public static extern IntPtr Msg(IntPtr h,uint m,IntPtr w,IntPtr l);
 [DllImport("user32.dll",EntryPoint="SendMessageW",CharSet=CharSet.Unicode)]public static extern IntPtr Read(IntPtr h,uint m,IntPtr w,StringBuilder l);
 [DllImport("user32.dll")]public static extern bool PostMessage(IntPtr h,uint m,IntPtr w,IntPtr l);
 public static IntPtr[] Find(IntPtr h,int id,string cls){var r=new List<IntPtr>();EnumChildWindows(h,(c,p)=>{var b=new StringBuilder(256);GetClassName(c,b,256);if(GetDlgCtrlID(c)==id&&b.ToString()==cls)r.Add(c);return true;},IntPtr.Zero);return r.ToArray();}
}
'@
function ByName([string]$name){[System.Windows.Automation.PropertyCondition]::new([System.Windows.Automation.AutomationElement]::NameProperty,$name)}
$wins=[System.Windows.Automation.AutomationElement]::RootElement.FindAll([System.Windows.Automation.TreeScope]::Children,[System.Windows.Automation.PropertyCondition]::new([System.Windows.Automation.AutomationElement]::ClassNameProperty,'Notepad'))
$matchesFound=@(foreach($w in $wins){$el=$w.FindFirst([System.Windows.Automation.TreeScope]::Descendants,(ByName $PublicFileName));if($el){[pscustomobject]@{window=$w;element=$el}}})
if($matchesFound.Count -ne 1){throw '公开记事本标签不唯一'}
$window=$matchesFound[0].window
if((Get-Process -Id $window.Current.ProcessId).ProcessName -ne 'Notepad'){throw '接收应用身份不符'}
$tab=[System.Windows.Automation.TreeWalker]::ControlViewWalker.GetParent($matchesFound[0].element)
if($tab.Current.ControlType -ne [System.Windows.Automation.ControlType]::TabItem){throw '公开标签父控件不符'}
$selection=[System.Windows.Automation.SelectionItemPattern]$tab.GetCurrentPattern([System.Windows.Automation.SelectionItemPattern]::Pattern)
$selection.Select()
if(-not $selection.Current.IsSelected){throw '公开标签未选中'}
$file=$window.FindFirst([System.Windows.Automation.TreeScope]::Descendants,[System.Windows.Automation.PropertyCondition]::new([System.Windows.Automation.AutomationElement]::AutomationIdProperty,'File'))
([System.Windows.Automation.ExpandCollapsePattern]$file.GetCurrentPattern([System.Windows.Automation.ExpandCollapsePattern]::Pattern)).Expand()
$saveAs=$window.FindFirst([System.Windows.Automation.TreeScope]::Descendants,(ByName '另存为'))
if(-not $saveAs){throw '另存入口不存在'}
([System.Windows.Automation.InvokePattern]$saveAs.GetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern)).Invoke()
$deadline=[DateTime]::UtcNow.AddSeconds(5)
do{$dialogs=@($window.FindAll([System.Windows.Automation.TreeScope]::Descendants,(ByName '另存为'))|Where-Object{$_.Current.ControlType -eq [System.Windows.Automation.ControlType]::Window});if($dialogs.Count -eq 1){break};Start-Sleep -Milliseconds 100}while([DateTime]::UtcNow -lt $deadline)
if($dialogs.Count -ne 1){throw '另存对话框不唯一'}
$dialog=$dialogs[0]
if($dialog.Current.ProcessId -ne $window.Current.ProcessId){throw '另存对话框进程不符'}
$edits=[NFReceiverSave]::Find([IntPtr]$dialog.Current.NativeWindowHandle,1001,'Edit')
$saves=[NFReceiverSave]::Find([IntPtr]$dialog.Current.NativeWindowHandle,1,'Button')
if($edits.Count -ne 1 -or $saves.Count -ne 1){throw '另存原生控件不唯一'}
$destination=Join-Path $Root ('receiver-saved-'+[guid]::NewGuid().ToString()+'.txt')
if(Test-Path $destination){throw '拒绝覆盖已有接收文件'}
# WM_SETTEXT alone changes the native edit text without committing the Shell dialog's
# filename state. Character input emits the edit notifications used by Save As.
[NFReceiverSave]::Msg($edits[0],177,[IntPtr]::Zero,[IntPtr](-1))|Out-Null
foreach($character in $destination.ToCharArray()){[NFReceiverSave]::Msg($edits[0],258,[IntPtr]([int]$character),[IntPtr]::Zero)|Out-Null}
$text=[Text.StringBuilder]::new(2048)
[NFReceiverSave]::Read($edits[0],13,[IntPtr]2048,$text)|Out-Null
if($text.ToString() -ne $destination){throw '另存路径输入核验失败'}
if(-not [NFReceiverSave]::PostMessage($saves[0],245,[IntPtr]::Zero,[IntPtr]::Zero)){throw '保存输入失败'}
$deadline=[DateTime]::UtcNow.AddSeconds(5)
while(-not (Test-Path $destination) -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 100}
if(-not (Test-Path $destination) -or (Get-Item $destination).Length -ne 43 -or (Get-FileHash $destination).Hash -ne $digest){throw '独立接收字节未通过；不确认任何替换提示'}
$receipt=@{sourceLease=$leases[0].FullName;destination=$destination;receiverPid=$window.Current.ProcessId;bytes=43;sha256=$digest;sourceExited=$true;method='Unique public Notepad tab; Save As character input and native Save button';full13Complete=$false}
$receipt|ConvertTo-Json|Set-Content (Join-Path $Root ('receiver-save.'+[guid]::NewGuid().ToString()+'.json')) -Encoding utf8
$receipt|ConvertTo-Json
