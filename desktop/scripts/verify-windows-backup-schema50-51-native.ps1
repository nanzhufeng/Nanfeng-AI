param([ValidateSet(50,51)][int]$ArchivedSchema=51,[ValidateSet('CHAT','WORK')][string]$Area='WORK')
$ErrorActionPreference='Stop'
$desktopRoot=Split-Path -Parent $PSScriptRoot
$receipt=Get-Content (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw -Encoding UTF8 | ConvertFrom-Json
if($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance' -or (Get-FileHash -LiteralPath $receipt.executable -Algorithm SHA256).Hash -ne $receipt.sha256){throw '候选身份不符'}
$parent='D:\CodexTemp'
$listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
$listener.Start();$port=$listener.LocalEndpoint.Port;$listener.Stop()
$launch=$null
function Invoke-Cdp([string]$expression){
 $text=$expression | & node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs') $port
 if($LASTEXITCODE -ne 0 -or -not $text){throw '原生IPC失败'}
 return ($text | ConvertFrom-Json)
}
function Stop-Seed {
 if(-not $script:launch){return}
 $p=Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $script:launch.pid)
 if($p){
  if($p.ExecutablePath -ne $script:launch.executable -or (Get-FileHash -LiteralPath $p.ExecutablePath).Hash -ne $receipt.sha256){throw '拒绝停止身份不符的PID'}
  Stop-Process -Id $script:launch.pid -Force
  $deadline=[DateTime]::UtcNow.AddSeconds(15)
  while((Get-Process -Id $script:launch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 100}
  if(Get-Process -Id $script:launch.pid -ErrorAction SilentlyContinue){throw '种子进程未退出'}
 }
 $script:launch=$null
}
try {
 $launch=& (Join-Path $PSScriptRoot 'start-windows-acceptance.ps1') -RootParent $parent -StartupMode normal -Visible -DebugPort $port | ConvertFrom-Json
 if($launch.sha256 -ne $receipt.sha256){throw '启动候选不符'}
 $root=$launch.root;$seedPid=$launch.pid
 $deadline=[DateTime]::UtcNow.AddSeconds(30);$ready=$false
 while([DateTime]::UtcNow -lt $deadline){try{$ready=(Invoke-Cdp 'Boolean(window.__TAURI__?.core?.invoke)');if($ready){break}}catch{};Start-Sleep -Milliseconds 250}
 if(-not $ready){throw '原生界面未就绪'}
 $areaJson=$Area | ConvertTo-Json -Compress
 $intentJson=('synthetic-schema-backup-'+[guid]::NewGuid().ToString('N')) | ConvertTo-Json -Compress
 $seed=Invoke-Cdp "(async()=>{const h={headers:{'x-nanfeng-data-area':$areaJson}};const call=(n,a={})=>window.__TAURI__.core.invoke(n,a,h);const w=await call('ensure_desktop_local_workspace');const p=await call('mutate_desktop_domain',{args:{intentId:$intentJson,workspaceId:w.summary.id,entity:'project',action:'create',fields:{title:'旧备份合成保留项目',description:'仅用于隔离验收'}}});return {workspaceId:w.summary.id,projectId:p.objectId}})()"
 if(-not $seed.workspaceId -or -not $seed.projectId){throw '合成项目失败'}
 $assetName='synthetic-backup-normal-'+[guid]::NewGuid().ToString('N')+'.txt'
 $areaRoot=if($Area -eq 'WORK'){Join-Path $root 'work-area'}else{$root}
 [IO.File]::WriteAllText((Join-Path $areaRoot ('assets\'+$assetName)),'SYNTHETIC-NORMAL-BACKUP-FIXTURE',[Text.Encoding]::UTF8)
 $id=[guid]::NewGuid().ToString('N')
 $source=Join-Path $parent ('nfai-normal-backup.source52.'+$id+'.nfai-backup')
 $backup=Join-Path $parent ('nfai-normal-backup.schema'+$ArchivedSchema+'.'+$id+'.nfai-backup')
 $sourceJson=$source | ConvertTo-Json -Compress
 $export=Invoke-Cdp "window.__TAURI__.core.invoke('export_desktop_local_backup',{selectedPath:$sourceJson},{headers:{'x-nanfeng-data-area':$areaJson}})"
 if($export.schemaVersion -ne 52){throw '当前原生导出不是52'}
 Stop-Seed
 $fixtureText=& python (Join-Path $PSScriptRoot 'make-windows-synthetic-historical-backup.py') $root $source $backup $ArchivedSchema
 if($LASTEXITCODE -ne 0){throw '历史结构合成包构造失败'}
 $fixture=$fixtureText | ConvertFrom-Json
 $restoreText=& (Join-Path $PSScriptRoot 'verify-windows-backup-current-restore-native.ps1') -Root $root -Backup $backup -WorkspaceId $seed.workspaceId -ProjectId $seed.projectId -AssetName $assetName -Area $Area
 $restore=$restoreText | ConvertFrom-Json
 [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;area=$Area;archivedSchema=$ArchivedSchema;fixture=$fixture;backup=$backup;seedPid=$seedPid;restore=$restore} | ConvertTo-Json -Depth 6
} finally {Stop-Seed}
