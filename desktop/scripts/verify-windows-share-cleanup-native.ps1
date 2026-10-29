param([string]$RootParent='D:\CodexTemp')
$ErrorActionPreference='Stop'
$desktop=Split-Path -Parent $PSScriptRoot
$current=Get-Content (Join-Path $desktop 'output/windows-acceptance/current.json') -Raw -Encoding utf8 | ConvertFrom-Json
$evidence=[ordered]@{layer='NORMAL_TAURI_IPC_WITH_SYNTHETIC_HANDOFF_MARKERS';candidate=$current.sha256;realShareReceiverConfirmed=$false;full13Complete=$false;stages=@()}
$launch=$null
Add-Type @'
using System;using System.Runtime.InteropServices;
public static class NFShareCleanupClose { [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h,uint m,IntPtr w,IntPtr l); }
'@
function Start-Isolated([string]$root='') {
  $listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
  $listener.Start();$port=$listener.LocalEndpoint.Port;$listener.Stop()
  if($port -eq 6000){throw 'CDP端口受限，请保留证据后选择其他端口'}
  $arguments=@{RootParent=$RootParent;StartupMode='normal';Visible=$true;DebugPort=$port}
  if($root){$arguments.Root=$root}
  $result=& (Join-Path $PSScriptRoot 'start-windows-acceptance.ps1') @arguments | ConvertFrom-Json
  if($result.sha256 -ne $current.sha256){throw '候选已变化'}
  return $result
}
function Eval([string]$expression) {
  $output=$expression | node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs') $launch.debugPort 2>> ($launch.launchReceipt+'.share-cleanup-cdp.log')
  if($LASTEXITCODE -ne 0 -or -not $output){throw '真实IPC执行失败'}
  return ($output | ConvertFrom-Json)
}
function Ready {
  $deadline=[DateTime]::UtcNow.AddSeconds(25)
  do {
    try {if((Eval 'Boolean(window.__TAURI__?.core?.invoke&&globalThis.__nanfengDesktopWorkState?.runtimeInfo?.dataAreasReady)') -eq $true){return}} catch {}
    Start-Sleep -Milliseconds 200
  }while([DateTime]::UtcNow -lt $deadline)
  throw '隔离WebView未就绪'
}
function Close-Normal {
  $process=Get-Process -Id $launch.pid -ErrorAction SilentlyContinue
  if(-not $process){throw '源进程提前退出'}
  if([IO.Path]::GetFullPath($process.Path) -ne [IO.Path]::GetFullPath($launch.executable) -or (Get-FileHash $process.Path).Hash -ne $current.sha256 -or $process.MainWindowTitle -ne '南枫 AI · 隔离测试（非日常版）'){throw '关闭源身份不符'}
  if(-not [NFShareCleanupClose]::PostMessage($process.MainWindowHandle,16,[IntPtr]::Zero,[IntPtr]::Zero)){throw '正常关闭消息未提交'}
  $deadline=[DateTime]::UtcNow.AddSeconds(15)
  while((Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 100}
  if(Get-Process -Id $launch.pid -ErrorAction SilentlyContinue){throw '源进程正常关闭未完成'}
  if(Select-String -LiteralPath (Join-Path $launch.root 'startup.stderr.log') -Pattern 'panicked|null pointer' -Quiet){throw '关闭包含panic；不能称正常退出通过'}
}
try {
  $launch=Start-Isolated
  $evidence.root=$launch.root
  Ready
  $firstPid=$launch.pid
  $source=Join-Path $launch.root 'synthetic-share-cleanup-source.txt'
  $bytes=[Text.Encoding]::UTF8.GetBytes("Public synthetic share cleanup fixture only.`n")
  [IO.File]::WriteAllBytes($source,$bytes)
  $digest=(Get-FileHash $source).Hash.ToLowerInvariant()
  $copies=@()
  foreach($sequence in 1,2){
    $lease=Join-Path $launch.root ("share-outbox/{0}/lease-{1}-{2}" -f $digest.Substring(0,16),$firstPid,$sequence)
    New-Item -ItemType Directory -Path $lease | Out-Null
    $copy=Join-Path $lease ("cleanup-{0}-{1}.txt" -f $firstPid,$sequence)
    [IO.File]::WriteAllBytes($copy,$bytes)
    [IO.File]::WriteAllBytes((Join-Path $lease '.share-handoff-v1'),[byte[]]@(49))
    $copies+=$copy
  }
  $protected=Eval "window.__TAURI__.core.invoke('preview_desktop_privacy_deletion',{args:{scope:'WINDOWS_SHARE_COPIES'}},{headers:{'x-nanfeng-data-area':'CHAT'}})"
  if($protected.aggregates[0].count -ne 0){throw '运行中的来源租约未保护'}
  $evidence.stages+=@{stage='running-source-protected';count=0;pid=$firstPid}
  Close-Normal
  foreach($stream in 'stdout','stderr'){Copy-Item -LiteralPath (Join-Path $launch.root "startup.$stream.log") -Destination (Join-Path $launch.root "startup-first-$firstPid.$stream.log") -ErrorAction Stop}
  $launch=Start-Isolated $launch.root
  Ready
  $evidence.secondPid=$launch.pid
  $preview=Eval "window.__TAURI__.core.invoke('preview_desktop_privacy_deletion',{args:{scope:'WINDOWS_SHARE_COPIES'}},{headers:{'x-nanfeng-data-area':'CHAT'}})"
  $evidence.coldPreview=$preview
  if($preview.aggregates[0].count -ne 2 -or $preview.aggregates[0].byteCount -ne 2*$bytes.Length){throw '冷启清理预览数量/字节不符'}
  $other=Eval "window.__TAURI__.core.invoke('preview_desktop_privacy_deletion',{args:{scope:'WINDOWS_SHARE_COPIES'}},{headers:{'x-nanfeng-data-area':'WORK'}})"
  if($other.aggregates[0].count -ne 0){throw 'WORK误读取CHAT租约'}
  [IO.File]::WriteAllBytes($copies[0],[Text.Encoding]::UTF8.GetBytes('changed synthetic lease'))
  $fingerprint=$preview.fingerprint | ConvertTo-Json -Compress
  $rejected=Eval "window.__TAURI__.core.invoke('delete_desktop_privacy_data',{args:{scope:'WINDOWS_SHARE_COPIES',previewFingerprint:$fingerprint}},{headers:{'x-nanfeng-data-area':'CHAT'}}).then(()=>false,()=>true)"
  if($rejected -ne $true -or -not(Test-Path $copies[0]) -or -not(Test-Path $copies[1])){throw '陈旧预览未拒绝或修改数据'}
  $preview=Eval "window.__TAURI__.core.invoke('preview_desktop_privacy_deletion',{args:{scope:'WINDOWS_SHARE_COPIES'}},{headers:{'x-nanfeng-data-area':'CHAT'}})"
  $fingerprint=$preview.fingerprint | ConvertTo-Json -Compress
  $deleted=Eval "window.__TAURI__.core.invoke('delete_desktop_privacy_data',{args:{scope:'WINDOWS_SHARE_COPIES',previewFingerprint:$fingerprint}},{headers:{'x-nanfeng-data-area':'CHAT'}})"
  if($deleted.deleted[0].count -ne 2 -or (Test-Path $copies[0]) -or (Test-Path $copies[1]) -or (Get-FileHash $source).Hash.ToLowerInvariant() -ne $digest){throw '明确清理或原件保留失败'}
  $after=Eval "window.__TAURI__.core.invoke('read_desktop_privacy_inventory',{}, {headers:{'x-nanfeng-data-area':'CHAT'}})"
  if(@($after.aggregates | Where-Object id -eq 'windows_share_copies' | Where-Object count -gt 0).Count){throw '清理计数未回读'}
  $evidence.stages+=@{stage='cold-reopen-identity-bound-explicit-cleanup';deleted=2;workProtected=$true;stalePreviewRejected=$true;sourcePreserved=$true;byteCount=$deleted.deleted[0].byteCount}
  Close-Normal
  $evidence.normalExit=$true
}catch{$evidence.error=$_.Exception.Message;throw}finally{
  if($launch){
    $process=Get-Process -Id $launch.pid -ErrorAction SilentlyContinue
    if($process -and [IO.Path]::GetFullPath($process.Path) -eq [IO.Path]::GetFullPath($launch.executable) -and (Get-FileHash $process.Path).Hash -eq $current.sha256){
      $evidence.forcedStop=$true;Stop-Process -Id $launch.pid -Force
    }
    $file=Join-Path $launch.root ('share-cleanup-native.'+[guid]::NewGuid().ToString()+'.json')
    $evidence | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $file -Encoding utf8
    $evidence | ConvertTo-Json -Depth 8
  }
}
