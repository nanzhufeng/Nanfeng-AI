$ErrorActionPreference = 'Stop'
Add-Type @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;
public static class NanfengNativeDialog {
    private delegate bool EnumProc(IntPtr hwnd, IntPtr context);
    [DllImport("user32.dll")] private static extern bool EnumWindows(EnumProc callback, IntPtr context);
    [DllImport("user32.dll")] private static extern bool EnumChildWindows(IntPtr parent, EnumProc callback, IntPtr context);
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(IntPtr hwnd, out uint pid);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hwnd);
    [DllImport("user32.dll")] public static extern bool IsWindowEnabled(IntPtr hwnd);
    [DllImport("user32.dll")] public static extern int GetDlgCtrlID(IntPtr hwnd);
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] private static extern int GetClassNameW(IntPtr hwnd, StringBuilder text, int capacity);
    public static string ClassName(IntPtr hwnd) { var text=new StringBuilder(256); GetClassNameW(hwnd,text,text.Capacity); return text.ToString(); }
    public static IntPtr[] Dialogs(uint pid) {
        var found=new List<IntPtr>();
        EnumWindows((hwnd, context)=>{ uint actual; GetWindowThreadProcessId(hwnd,out actual);
            if(actual==pid && IsWindowVisible(hwnd) && ClassName(hwnd)=="#32770") found.Add(hwnd);
            return true;
        },IntPtr.Zero);
        return found.ToArray();
    }
    public static IntPtr[] Children(IntPtr parent) {
        var found=new List<IntPtr>();
        EnumChildWindows(parent,(hwnd,context)=>{found.Add(hwnd);return true;},IntPtr.Zero);
        return found.ToArray();
    }
}
'@
