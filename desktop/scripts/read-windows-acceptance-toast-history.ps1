param([Parameter(Mandatory=$true)][string]$Root)
$ErrorActionPreference='Stop'
$businessRoot=[IO.Path]::GetFullPath($Root)
if (-not (Test-Path -LiteralPath (Join-Path $businessRoot '.nanfeng-windows-acceptance.json') -PathType Leaf)) { throw 'Only marked synthetic root is allowed.' }
$runText=& python (Join-Path $PSScriptRoot 'read-windows-acceptance-reminder-run.py') $businessRoot
if($LASTEXITCODE -ne 0){throw 'Synthetic run read failed.'}
$read=$runText | ConvertFrom-Json
$runs=@($read.runs)
if($runs.Count -ne 1){throw 'Expected exactly one synthetic run.'}
$runId=[string]$runs[0].runId
$record=[ordered]@{runId=$runId;runState=$runs[0].runState;notificationState=$runs[0].notificationState;safeCode=$runs[0].safeCode;systemHistoryMatched=$false}
[void][Windows.UI.Notifications.ToastNotificationManager,Windows.UI.Notifications,ContentType=WindowsRuntime]
$history=[Windows.UI.Notifications.ToastNotificationManager]::History.GetHistory('com.nanzhufeng.ai.desktop.windowsacceptance')
$match=@($history | Where-Object { $_.Tag -eq $runId -and $_.Group -eq 'nanfeng-reminders-v1' })
if($match.Count -eq 1){
 $xml=[xml]$match[0].Content.GetXml()
 $record.systemHistoryMatched=$true
 $record.launch=[string]$xml.toast.launch
 $record.activationType=[string]$xml.toast.activationType
}
$record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $businessRoot 'notification-verification.json') -Encoding UTF8
$record | ConvertTo-Json -Compress
if($record.notificationState -ne 'SENT' -or -not $record.systemHistoryMatched){throw 'Notification send/history has not passed.'}
if($record.activationType -ne 'protocol' -or $record.launch -ne ('nanfeng-ai-windows-acceptance-reminder://open/WORK/'+$runId)){throw 'Notification route mismatch.'}
# Remove only this synthetic run notification after verifying it.
[Windows.UI.Notifications.ToastNotificationManager]::History.Remove($runId,'nanfeng-reminders-v1','com.nanzhufeng.ai.desktop.windowsacceptance')
