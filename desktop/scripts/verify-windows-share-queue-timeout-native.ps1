param([Parameter(Mandatory)][string]$FixturePath,[string]$RootParent='D:\CodexTemp')
$ErrorActionPreference='Stop'
$session=$null
$result=[ordered]@{layer='NORMAL_NATIVE_INJECTED_WINDOW_QUEUE_DELAY';full13Complete=$false}
try {
    $session=& (Join-Path $PSScriptRoot 'prepare-windows-share-ui-session.ps1') -RootParent $RootParent -FixturePath $FixturePath | ConvertFrom-Json
    $root=[IO.Path]::GetFullPath($session.launch.root)
    $result.root=$root;$result.candidate=$session.candidate;$result.pid=$session.launch.pid
    $p=Get-Process -Id $session.launch.pid
    if([IO.Path]::GetFullPath($p.Path) -ne [IO.Path]::GetFullPath($session.launch.executable) -or (Get-FileHash $p.Path).Hash -ne $session.candidate -or $p.MainWindowTitle -ne '南枫 AI · 隔离测试（非日常版）'){throw '候选窗口身份不符'}
    $sourcePath=Join-Path $root 'assets/3f3fa3e4842fd4d54be489e980058c1aaf1bd89951dd0dd521e29f0b81639969'
    $sourceFiles=@(Get-Item -LiteralPath $sourcePath|Where-Object{$_.Length -eq 43 -and (Get-FileHash $_.FullName).Hash -eq '3F3FA3E4842FD4D54BE489E980058C1AAF1BD89951DD0DD521E29F0B81639969'})
    if($sourceFiles.Count -ne 1){throw '公开原件身份不唯一'}
    $result.sourcePath=$sourceFiles[0].FullName
    $arm=Join-Path $root '.share-window-queue-delay-probe'
    $stream=[IO.File]::Open($arm,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None);$stream.Dispose()
    $arguments=@{args=@{workspaceId=$session.seed.workspaceId;attachmentId=$session.seed.attachmentId}}|ConvertTo-Json -Compress
    $expression="window.__TAURI__.core.invoke('share_desktop_attachment',$arguments,{headers:{'x-nanfeng-data-area':'CHAT'}}).then(()=>({resolved:true}),e=>({resolved:false,error:String(e)}))"
    $raw=$expression|node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs') $session.launch.debugPort
    if($LASTEXITCODE -ne 0){throw '实际IPC观察失败'}
    $result.ipc=$raw|ConvertFrom-Json
    if($result.ipc.resolved -ne $false -or $result.ipc.error -notmatch 'Windows 分享窗口响应超时'){throw '实际五秒超时未观察到'}
    $events=@(Get-Content -LiteralPath (Join-Path $root 'startup.stderr.log')|Where-Object{$_ -like 'windows-share:*'})
    $result.events=$events
    if(($events -join ',') -ne 'windows-share:queue-delay-probe-started,windows-share:queue-delay-probe-ended,windows-share:abandoned-before-window-request'){throw '迟到窗口任务未准确放弃'}
    $result.remainingShareFiles=@(Get-ChildItem -LiteralPath (Join-Path $root 'share-outbox') -Recurse -File -ErrorAction SilentlyContinue).Count
    $result.armConsumed=-not (Test-Path -LiteralPath $arm)
    $result.sourceHash=(Get-FileHash $sourceFiles[0].FullName).Hash
    if($result.remainingShareFiles -ne 0 -or -not $result.armConsumed -or $result.sourceHash -ne '3F3FA3E4842FD4D54BE489E980058C1AAF1BD89951DD0DD521E29F0B81639969'){throw '超时回收或原件保留未通过'}
    $result.passed=$true
} catch {$result.error=$_.Exception.Message;throw} finally {
    if($session){
        $p=Get-Process -Id $session.launch.pid -ErrorAction SilentlyContinue
        if($p -and [IO.Path]::GetFullPath($p.Path) -eq [IO.Path]::GetFullPath($session.launch.executable) -and (Get-FileHash $p.Path).Hash -eq $session.candidate){
            $result.normalCloseRequested=$p.CloseMainWindow()
            $deadline=[DateTime]::UtcNow.AddSeconds(20)
            while((Get-Process -Id $session.launch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline){Start-Sleep -Milliseconds 100}
        }
        $result.processExited=$null -eq (Get-Process -Id $session.launch.pid -ErrorAction SilentlyContinue)
        $result.panicLines=@(Get-Content -LiteralPath (Join-Path $session.launch.root 'startup.stderr.log')|Where-Object{$_ -match 'panic|null pointer'})
        $result.utc=[DateTime]::UtcNow.ToString('o')
        $result|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $session.launch.root ('share-queue-timeout.'+[guid]::NewGuid().ToString()+'.json')) -Encoding utf8
        $result|ConvertTo-Json -Depth 8
    }
}
if(-not $result.processExited -or $result.panicLines.Count -ne 0){throw '超时后正常关闭未通过'}
