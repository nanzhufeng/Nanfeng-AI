param(
    [Parameter(Mandatory)][int]$ProcessId,
    [Parameter(Mandatory)][ValidateSet('cancel','select','save')][string]$Action,
    [string]$SelectedPath = '',
    [string]$AcceptanceRoot = ''
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'windows-file-dialog-handles.ps1')
Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class NanfengFileDialogMessage {
    [DllImport("user32.dll", EntryPoint="SendMessageW", CharSet=CharSet.Unicode)]
    public static extern IntPtr SendText(IntPtr hwnd, uint message, IntPtr wparam, string text);
    [DllImport("user32.dll", EntryPoint="SendMessageW")]
    public static extern IntPtr SendNoText(IntPtr hwnd, uint message, IntPtr wparam, IntPtr lparam);
}
'@
if ($Action -in @('select','save')) {
    if (-not $AcceptanceRoot) { throw '文件选择/保存必须提供标记合成根。' }
    $root = [IO.Path]::GetFullPath($AcceptanceRoot).TrimEnd('\')
    $path = [IO.Path]::GetFullPath($SelectedPath)
    if (-not $path.StartsWith($root + '\', [StringComparison]::OrdinalIgnoreCase)) { throw '目标必须在标记合成根内。' }
    $marker = Get-Content -LiteralPath (Join-Path $root '.nanfeng-windows-acceptance.json') -Raw | ConvertFrom-Json
    if ($marker.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance') { throw '合成根身份无效。' }
    if ($Action -eq 'select' -and -not (Test-Path -LiteralPath $path -PathType Leaf)) { throw '选择仅允许现存合成文件。' }
    if ($Action -eq 'save') {
        $exportRoot = Join-Path $root 'synthetic-transcription-export'
        if (-not $path.StartsWith($exportRoot + '\', [StringComparison]::OrdinalIgnoreCase) -or
            [IO.Path]::GetExtension($path) -notin @('.txt','.md','.docx') -or (Test-Path -LiteralPath $path) -or
            -not (Test-Path -LiteralPath (Split-Path -Parent $path) -PathType Container)) {
            throw '保存仅允许独立标记根 synthetic-transcription-export 下不存在的 TXT/MD/DOCX 文件。'
        }
    }
}
$dialogs = @([NanfengNativeDialog]::Dialogs([uint32]$ProcessId))
if ($dialogs.Count -ne 1) { throw "目标进程的系统文件对话框数量无效：$($dialogs.Count)" }
$dialog = $dialogs[0]
$children = @([NanfengNativeDialog]::Children($dialog))
$editId = if ($Action -eq 'save') { 1001 } else { 1148 }
$buttonId = if ($Action -eq 'cancel') { 2 } else { 1 }
$edit = @($children | Where-Object { [NanfengNativeDialog]::ClassName($_) -eq 'Edit' -and [NanfengNativeDialog]::GetDlgCtrlID($_) -eq $editId })
$button = @($children | Where-Object { [NanfengNativeDialog]::ClassName($_) -eq 'Button' -and [NanfengNativeDialog]::GetDlgCtrlID($_) -eq $buttonId -and [NanfengNativeDialog]::IsWindowEnabled($_) })
if ($button.Count -ne 1) { throw '无法精确定位文件对话框按钮。' }
if ($Action -in @('select','save')) {
    if ($edit.Count -ne 1) { throw '无法精确定位文件名输入框。' }
    $written = [NanfengFileDialogMessage]::SendText($edit[0], 0x000C,
        [IntPtr]::Zero, $path)
    if ($written -eq [IntPtr]::Zero) { throw '文件名未写入系统对话框。' }
}
[void][NanfengFileDialogMessage]::SendNoText($button[0], 0x00F5,
    [IntPtr]::Zero, [IntPtr]::Zero)
[pscustomobject]@{ action=$Action; processId=$ProcessId; dialogHandle=$dialog.ToInt64() } | ConvertTo-Json -Compress
