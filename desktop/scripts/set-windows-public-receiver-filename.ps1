param([Parameter(Mandatory)][string]$Root,[Parameter(Mandatory)][int]$ReceiverPid,[Parameter(Mandatory)][string]$PublicFilename)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
if($PublicFilename -notmatch '^nfa-share-[a-f0-9]{32}\.txt$'){throw '仅允许唯一公开合成文件名'}
$rootPath=[IO.Path]::GetFullPath($Root)
if($rootPath -notmatch '^D:\\CodexTemp\\nanfeng-ai-windows-acceptance\.[a-f0-9-]{36}$'){throw '不是隔离验收根'}
$files=@(Get-ChildItem -LiteralPath (Join-Path $rootPath 'share-outbox') -Recurse -File|Where-Object{$_.Name -eq $PublicFilename})
if($files.Count -ne 1 -or $files[0].Length -ne 43 -or (Get-FileHash $files[0].FullName).Hash -ne '3F3FA3E4842FD4D54BE489E980058C1AAF1BD89951DD0DD521E29F0B81639969'){throw '公开合成字节身份不符'}
$receiver=Get-Process -Id $ReceiverPid
if($receiver.ProcessName -ne 'Notepad' -or $receiver.MainWindowTitle -ne "$PublicFilename - Notepad"){throw '唯一接收标签不是当前窗口'}
$window=[System.Windows.Automation.AutomationElement]::FromHandle($receiver.MainWindowHandle)
$hosts=$window.FindAll([System.Windows.Automation.TreeScope]::Descendants,[System.Windows.Automation.PropertyCondition]::new([System.Windows.Automation.AutomationElement]::AutomationIdProperty,'FileNameControlHost'))
if($hosts.Count -ne 1){throw '文件名host不唯一'}
$edits=$hosts[0].FindAll([System.Windows.Automation.TreeScope]::Descendants,[System.Windows.Automation.PropertyCondition]::new([System.Windows.Automation.AutomationElement]::AutomationIdProperty,'1001'))
if($edits.Count -ne 1 -or $edits[0].Current.Name -ne $PublicFilename){throw '文件名编辑框身份不符'}
$destination=Join-Path $rootPath ('receiver-saved-'+[guid]::NewGuid().ToString()+'.txt')
if(Test-Path -LiteralPath $destination){throw '目标已存在'}
Add-Type @'
using System;using System.Text;using System.Runtime.InteropServices;
public static class NFPublicFilenameInput {
 [StructLayout(LayoutKind.Explicit,Size=40)] public struct INPUT {[FieldOffset(0)]public uint type;[FieldOffset(8)]public KEYBDINPUT ki;}
 [StructLayout(LayoutKind.Sequential)] public struct KEYBDINPUT {public ushort vk,scan;public uint flags,time;public UIntPtr extra;}
 [StructLayout(LayoutKind.Sequential)] public struct GUI {public uint size,flags;public IntPtr active,focus,capture,menu,move,caret;public int left,top,right,bottom;}
 [DllImport("user32.dll")]static extern uint SendInput(uint n,INPUT[] i,int s);
 [DllImport("user32.dll")]public static extern IntPtr GetAncestor(IntPtr h,uint f);
 [DllImport("user32.dll")]public static extern IntPtr GetForegroundWindow();
 [DllImport("user32.dll")]public static extern bool SetForegroundWindow(IntPtr h);
 [DllImport("user32.dll")]static extern uint GetWindowThreadProcessId(IntPtr h,out uint p);
 [DllImport("kernel32.dll")]static extern uint GetCurrentThreadId();
 [DllImport("user32.dll")]static extern bool AttachThreadInput(uint a,uint b,bool x);
 [DllImport("user32.dll")]static extern IntPtr SetFocus(IntPtr h);
 [DllImport("user32.dll")]static extern bool GetGUIThreadInfo(uint t,ref GUI g);
 [DllImport("user32.dll",CharSet=CharSet.Unicode)]static extern int GetClassName(IntPtr h,StringBuilder b,int n);
 public static string Class(IntPtr h){var b=new StringBuilder(128);GetClassName(h,b,128);return b.ToString();}
 public static bool Focus(IntPtr edit){uint p;uint target=GetWindowThreadProcessId(edit,out p),own=GetCurrentThreadId();bool attached=AttachThreadInput(own,target,true);try{SetFocus(edit);var g=new GUI();g.size=(uint)Marshal.SizeOf(g);return GetGUIThreadInfo(target,ref g)&&g.focus==edit;}finally{if(attached)AttachThreadInput(own,target,false);}}
 static INPUT Key(ushort vk,ushort scan,uint flags){return new INPUT{type=1,ki=new KEYBDINPUT{vk=vk,scan=scan,flags=flags}};}
 public static void Type(IntPtr edit,string text){if(Class(edit)!="Edit"||!Focus(edit)||GetForegroundWindow()!=GetAncestor(edit,2))throw new Exception("实际文件名焦点未确认");var select=new[]{Key(17,0,0),Key(65,0,0),Key(65,0,2),Key(17,0,2)};if(SendInput(4,select,Marshal.SizeOf(typeof(INPUT)))!=4)throw new Exception("选择输入结果未知");foreach(char c in text){var pair=new[]{Key(0,c,4),Key(0,c,6)};if(SendInput(2,pair,Marshal.SizeOf(typeof(INPUT)))!=2)throw new Exception("文字输入结果未知");}}
}
'@
$edit=[IntPtr]$edits[0].Current.NativeWindowHandle
if([NFPublicFilenameInput]::Class($edit) -ne 'Edit'){throw '不是原生文件名Edit'}
$dialog=[NFPublicFilenameInput]::GetAncestor($edit,2)
[NFPublicFilenameInput]::SetForegroundWindow($dialog)|Out-Null
[NFPublicFilenameInput]::Type($edit,$destination)
$receipt=[ordered]@{root=$rootPath;receiverPid=$ReceiverPid;publicFilename=$PublicFilename;source=$files[0].FullName;destination=$destination;filenameEdit=$edit.ToInt64();dialog=$dialog.ToInt64();typedWithConfirmedNativeFocus=$true;saved=$false;utc=[DateTime]::UtcNow.ToString('o')}
$receiptPath=Join-Path $rootPath ('receiver-filename-input.'+[guid]::NewGuid().ToString()+'.json')
$receipt|ConvertTo-Json|Set-Content -LiteralPath $receiptPath -Encoding utf8
$receipt|ConvertTo-Json
