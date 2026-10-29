$ErrorActionPreference='Stop'
$expected=[Environment]::GetEnvironmentVariable('NANFENG_AI_PRIVATE_DESKTOP','Process')
if ($expected -cnotmatch '^NanfengAIProbe_[a-f0-9]{32}$') {throw 'Verified private desktop required before any GUI action'}
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
using System.Text;
public static class NanfengPrivateDesktopGuard {
 [DllImport("kernel32.dll")] static extern uint GetCurrentThreadId();
 [DllImport("user32.dll")] static extern IntPtr GetThreadDesktop(uint tid);
 [DllImport("user32.dll",SetLastError=true)] static extern IntPtr OpenInputDesktop(uint flags,bool inherit,uint access);
 [DllImport("user32.dll")] static extern bool CloseDesktop(IntPtr desktop);
 [DllImport("user32.dll",CharSet=CharSet.Unicode,SetLastError=true)] static extern bool GetUserObjectInformation(IntPtr h,int index,StringBuilder data,uint bytes,out uint needed);
 static string Name(IntPtr h){var text=new StringBuilder(256);uint needed;if(h==IntPtr.Zero||!GetUserObjectInformation(h,2,text,512,out needed))throw new Exception("Desktop identity unavailable");return text.ToString();}
 public static string Current(){return Name(GetThreadDesktop(GetCurrentThreadId()));}
 public static string Input(){var h=OpenInputDesktop(0,false,1);try{return Name(h);}finally{if(h!=IntPtr.Zero)CloseDesktop(h);}}
}
'@
$current=[NanfengPrivateDesktopGuard]::Current()
$inputName=[NanfengPrivateDesktopGuard]::Input()
if ($current -cne $expected -or $current -ceq $inputName) {throw 'Private desktop must match the verified name and differ from the input desktop'}
@{privateDesktop=$current;inputDesktopDifferent=$true;verified=$true} | ConvertTo-Json -Compress
