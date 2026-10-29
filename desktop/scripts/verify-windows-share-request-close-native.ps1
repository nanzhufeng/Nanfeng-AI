param([ValidateRange(1,2)][int]$Requests=1,[switch]$ObserveMessages,[switch]$ReenterFocus)
$ErrorActionPreference='Stop'
$session=& (Join-Path $PSScriptRoot 'prepare-windows-share-ui-session.ps1') | ConvertFrom-Json
$launch=$session.launch
$root=$launch.root
$receiptPath=Join-Path $root ('share-request-close.'+[guid]::NewGuid().ToString()+'.json')
$result=[ordered]@{candidate=$session.candidate;root=$root;pid=$launch.pid;requests=$Requests;layer='NORMAL_NATIVE_REQUEST_CLOSE';full13Complete=$false}
Add-Type 'using System;using System.Runtime.InteropServices;public static class NFRequestClose{[DllImport("user32.dll")]public static extern bool PostMessage(IntPtr h,uint m,IntPtr w,IntPtr l);[DllImport("kernel32.dll")]public static extern bool GetExitCodeProcess(IntPtr h,out uint code);}'
$process=Get-Process -Id $launch.pid
$nativeProcessHandle=$process.Handle
try {
 if ([IO.Path]::GetFullPath($process.Path) -ne [IO.Path]::GetFullPath($launch.executable) -or (Get-FileHash $process.Path).Hash -ne $session.candidate) {throw '隔离源身份不符'}
 $result.title=$process.MainWindowTitle
 $result.responding=$process.Responding
 if ($result.title -ne '南枫 AI · 隔离测试（非日常版）' -or -not $result.responding) {throw '隔离窗口未就绪'}
 if($ObserveMessages -or $ReenterFocus){[IO.File]::WriteAllBytes((Join-Path $root '.share-close-message-probe'),[byte[]]@());$result.messageProbeArmed=$true}
 if($ReenterFocus){[IO.File]::WriteAllBytes((Join-Path $root '.share-close-reentrant-focus-probe'),[byte[]]@());$result.controlledReentry=$true}
 $argsJson=@{args=@{workspaceId=$session.seed.workspaceId;attachmentId=$session.seed.attachmentId}}|ConvertTo-Json -Compress
 for($i=0;$i -lt $Requests;$i++) {
  $output="window.__TAURI__.core.invoke('share_desktop_attachment',$argsJson,{headers:{'x-nanfeng-data-area':'CHAT'}}).then(()=>true)"|node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs') $launch.debugPort
  if($LASTEXITCODE -ne 0 -or ($output|ConvertFrom-Json) -ne $true){throw '分享请求失败'}
 }
 $result.eventsBeforeClose=@(Get-Content (Join-Path $root 'startup.stderr.log')|Where-Object{$_ -like 'windows-share:*'})
 $result.closeRequested=[NFRequestClose]::PostMessage($process.MainWindowHandle,16,[IntPtr]::Zero,[IntPtr]::Zero)
 if(-not $result.closeRequested){throw '正常关闭请求失败'}
 if(-not $process.WaitForExit(15000)){throw '正常关闭超时'}
 [uint32]$nativeExitCode=259
 if(-not [NFRequestClose]::GetExitCodeProcess($nativeProcessHandle,[ref]$nativeExitCode) -or $nativeExitCode -eq 259){throw '退出码无法读取'}
 $result.exitCode=$nativeExitCode
 $result.stderrBytes=(Get-Item (Join-Path $root 'startup.stderr.log')).Length
 $result.panicObserved=[bool](Select-String -LiteralPath (Join-Path $root 'startup.stderr.log') -Pattern 'panicked|null pointer|fatal runtime' -Quiet)
 $result.leaseCountAfterClose=@(Get-ChildItem (Join-Path $root 'share-outbox') -Recurse -File -ErrorAction SilentlyContinue|Where-Object{$_.Name -ne '.share-handoff-v1'}).Count
 $result.passed=($result.exitCode -eq 0 -and -not $result.panicObserved)
 if(-not $result.passed){throw '分享请求后关闭失败，原日志保留'}
} catch {$result.error=$_.Exception.Message;throw} finally {
 $live=Get-Process -Id $launch.pid -ErrorAction SilentlyContinue
 if($live){
  if([IO.Path]::GetFullPath($live.Path) -ne [IO.Path]::GetFullPath($launch.executable) -or (Get-FileHash $live.Path).Hash -ne $session.candidate){throw '拒绝停止身份不符的进程'}
  Stop-Process -Id $launch.pid -Force
  $result.forcedStop=$true
 }
 $result|ConvertTo-Json -Depth 6|Set-Content -LiteralPath $receiptPath -Encoding utf8
 $result|ConvertTo-Json -Depth 6
}
