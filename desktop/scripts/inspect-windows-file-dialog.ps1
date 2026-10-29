param([Parameter(Mandatory)][int]$ProcessId)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'windows-file-dialog-handles.ps1')
$result = @()
foreach ($dialog in @([NanfengNativeDialog]::Dialogs([uint32]$ProcessId))) {
    $items = @([pscustomobject]@{ name=''; id=''; class='#32770'; type='ControlType.Window'; enabled=[NanfengNativeDialog]::IsWindowEnabled($dialog); hwnd=$dialog.ToInt64() })
    foreach ($handle in @([NanfengNativeDialog]::Children($dialog))) {
        $class = [NanfengNativeDialog]::ClassName($handle)
        $id = [NanfengNativeDialog]::GetDlgCtrlID($handle)
        if ($class -notin @('Edit','Button','ComboBox') -or $id -notin @(1001,1148,1,2)) { continue }
        $items += [pscustomobject]@{ name=''; id=[string]$id; class=$class; type="ControlType.$class"; enabled=[NanfengNativeDialog]::IsWindowEnabled($handle); hwnd=$handle.ToInt64() }
    }
    $result += [pscustomobject]@{ name=''; class='#32770'; type='ControlType.Window'; children=$items }
}
ConvertTo-Json -InputObject @($result) -Depth 6 -Compress
