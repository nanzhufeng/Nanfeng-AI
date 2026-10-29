param([string]$RootParent = 'D:\CodexTemp', [switch]$InputDuringSwitchSave)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if (-not $IsWindows -and $PSVersionTable.PSEdition -eq 'Core') { throw '仅支持 Windows 原生验收。' }
$desktopRoot = Split-Path -Parent $PSScriptRoot
$receipt = Get-Content -LiteralPath (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw | ConvertFrom-Json
if ($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance' -or $receipt.sha256 -notmatch '^[A-F0-9]{64}$') { throw '独立候选收据无效。' }
$sourceExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance/{0}/NanfengAI-Windows-Acceptance.exe" -f $receipt.sha256)))
$runExecutable = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance-binaries/{0}/nanfeng-ai-desktop-spike.exe" -f $receipt.sha256)))
if ([IO.Path]::GetFullPath([string]$receipt.executable) -ne $sourceExecutable -or
    (Get-FileHash -LiteralPath $sourceExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) {
    throw '验收候选路径或哈希与收据不符。'
}
$launcher = Join-Path $PSScriptRoot 'start-windows-acceptance.ps1'
$cdpHelper = Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs'
$textValue = "合成未发送草稿-$([Guid]::NewGuid().ToString('N'))"
$root = ''
$running = [System.Collections.Generic.List[object]]::new()
$stopped = [System.Collections.Generic.List[int]]::new()

function New-DebugPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
function Invoke-Cdp([int]$port, [string]$expression) {
    $output = $expression | & node $cdpHelper $port 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $output) { throw '隔离 WebView2 调用失败。' }
    return ($output | ConvertFrom-Json)
}
function Assert-ProcessIdentity($launch) {
    if ($launch.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath([string]$launch.executable) -ne $runExecutable) { throw '运行副本与收据不一致。' }
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if (-not $process -or [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable) { throw '验收 PID 路径不匹配。' }
}
function Stop-Acceptance($launch) {
    $process = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if (-not $process -or [IO.Path]::GetFullPath([string]$process.ExecutablePath) -ne $runExecutable -or
        (Get-FileHash -LiteralPath $runExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) {
        throw '验收 PID 身份不匹配，拒绝停止。'
    }
    Stop-Process -Id $launch.pid -Force
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ((Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
    if (Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) { throw '验收 PID 未停止。' }
    $running.Remove($launch) | Out-Null
    $stopped.Add([int]$launch.pid)
}
function Start-Acceptance {
    $port = New-DebugPort
    $params = @{ RootParent=$RootParent; StartupMode='normal'; Visible=$true; DebugPort=$port }
    if ($root) { $params.Root = $root }
    $launch = & $launcher @params | ConvertFrom-Json
    if (-not $root) { $script:root = [string]$launch.root }
    if ($launch.root -ne $root) { throw '隔离数据根改变。' }
    $running.Add($launch)
    Assert-ProcessIdentity $launch
    $deadline = [DateTime]::UtcNow.AddSeconds(25)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $ready = Invoke-Cdp $port "(async()=>{const h={headers:{'x-nanfeng-data-area':'CHAT'}};const s=await window.__TAURI__.core.invoke('read_desktop_model_service_settings',{},h);return Array.isArray(s)&&Boolean(document.querySelector('#chat-composer'))})()"
            if ($ready -eq $true) { return $launch }
        } catch { Start-Sleep -Milliseconds 250 }
    }
    throw '隔离聊天界面未就绪。'
}
function Read-Draft($launch, [string]$area, [string]$workspaceId, [string]$conversationId = $null) {
    $headers = @{headers=@{'x-nanfeng-data-area'=$area}} | ConvertTo-Json -Compress
    $id = $workspaceId | ConvertTo-Json -Compress
    $routeId = 'null'
    if ($null -ne $conversationId -and $conversationId -ne '') { $routeId = ConvertTo-Json -InputObject $conversationId -Compress }
    return (Invoke-Cdp $launch.debugPort "window.__TAURI__.core.invoke('read_desktop_conversation_draft',{workspaceId:$id,conversationId:$routeId},$headers)")
}
function Wait-For([scriptblock]$check, [string]$errorText) {
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ([DateTime]::UtcNow -lt $deadline) {
        try { if (& $check) { return } } catch {}
        Start-Sleep -Milliseconds 150
    }
    throw $errorText
}

function Click-Action($launch, [string]$action) {
    $name = $action | ConvertTo-Json -Compress
    return (Invoke-Cdp $launch.debugPort "(()=>{const b=[...document.querySelectorAll('[data-action]')].find(x=>x.dataset.action===$name);if(!b)return false;b.click();return true})()")
}
function Wait-Area($launch, [string]$area) {
    Wait-For { (Invoke-Cdp $launch.debugPort 'window.__nanfengDesktopWorkState.dataArea') -eq $area } "未进入 $area 数据区。"
}
function Set-SyntheticDraft($launch, [string]$area, [string]$workspaceId, [string]$value) {
    $headers = @{headers=@{'x-nanfeng-data-area'=$area}} | ConvertTo-Json -Compress
    $args = @{workspaceId=$workspaceId;conversationId=$null;text=$value;attachmentIds=@();modelId=$null;toneOverride=$null;webSearchOverride=$null} | ConvertTo-Json -Compress -Depth 4
    $draft = Invoke-Cdp $launch.debugPort "window.__TAURI__.core.invoke('save_desktop_conversation_draft',{args:$args},$headers)"
    if ($draft.text -ne $value) { throw "$area 合成草稿未保存。" }
}
function Assert-DraftPair($launch, [string]$workspaceId, [string]$chatText, [string]$workText) {
    $chat = Read-Draft $launch 'CHAT' $workspaceId
    $work = Read-Draft $launch 'WORK' $workspaceId
    if ($chat.text -ne $chatText -or $work.text -ne $workText) { throw '同 ID 双区草稿回读不一致。' }
}
$locks = [System.Collections.Generic.List[object]]::new()
function Start-SyntheticLock {
    $tag = [Guid]::NewGuid().ToString('N')
    $ready = Join-Path $root "draft-lock-$tag.ready"
    $release = Join-Path $root "draft-lock-$tag.release"
    $scriptPath = (Resolve-Path (Join-Path $PSScriptRoot 'hold-synthetic-sqlite-lock.py')).Path
    $process = Start-Process -FilePath (Get-Command python).Source -ArgumentList @($scriptPath,$root,$ready,$release) -WindowStyle Hidden -PassThru
    $lock = [pscustomobject]@{process=$process;ready=$ready;release=$release}
    $locks.Add($lock)
    $deadline = [DateTime]::UtcNow.AddSeconds(5)
    while (-not (Test-Path -LiteralPath $ready) -and [DateTime]::UtcNow -lt $deadline) {
        if ($process.HasExited) { throw '合成 SQLite 锁进程提前退出。' }
        Start-Sleep -Milliseconds 50
    }
    if (-not (Test-Path -LiteralPath $ready)) { throw '合成 SQLite 锁未就绪。' }
    return $lock
}
function Release-SyntheticLock($lock) {
    [IO.File]::WriteAllText($lock.release,'release',[Text.UTF8Encoding]::new($false))
    if (-not $lock.process.WaitForExit(5000)) { throw '合成 SQLite 锁进程未退出。' }
    if ($lock.process.ExitCode -ne 0) { throw '合成 SQLite 锁进程失败。' }
    Remove-Item -LiteralPath $lock.ready,$lock.release -ErrorAction SilentlyContinue
    $locks.Remove($lock) | Out-Null
}

try {
    $first = Start-Acceptance
    $workspaceId = Invoke-Cdp $first.debugPort 'window.__nanfengDesktopWorkState.current?.summary?.id||null'
    if (-not $workspaceId) { throw 'CHAT 初始合成工作区缺失。' }
    if ((Click-Action $first 'show-work') -ne $true) { throw 'WORK 可见入口缺失。' }
    Wait-Area $first 'WORK'
    $workId = Invoke-Cdp $first.debugPort 'window.__nanfengDesktopWorkState.current?.summary?.id||null'
    if (-not $workId -or $workId -eq $workspaceId) { throw '初始 WORK 身份不符合受控同 ID 准备条件。' }
    Stop-Acceptance $first
    $parent = [IO.Path]::GetFullPath($RootParent).TrimEnd('\')
    if (-not $root.StartsWith($parent + '\',[StringComparison]::OrdinalIgnoreCase) -or
        -not (Test-Path -LiteralPath (Join-Path $root '.nanfeng-windows-acceptance.json') -PathType Leaf)) { throw '拒绝修改未标记合成根。' }
    $rekeyCode = @'
import sqlite3,sys
path,old,new=sys.argv[1:]
db=sqlite3.connect(path)
assert db.execute('SELECT id FROM workspaces').fetchall()==[(old,)]
assert db.execute('SELECT workspace_id FROM workspace_exchange').fetchall()==[(old,)]
db.execute('PRAGMA foreign_keys=OFF')
db.execute('UPDATE workspace_exchange SET workspace_id=? WHERE workspace_id=?',(new,old))
db.execute('UPDATE workspaces SET id=? WHERE id=?',(new,old))
db.commit()
assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok'
assert db.execute('PRAGMA foreign_key_check').fetchall()==[]
print('same-id-ready')
'@
    $rekeyed = & python -c $rekeyCode (Join-Path $root 'work-area/workspace.sqlite3') $workId $workspaceId
    if ($LASTEXITCODE -ne 0 -or $rekeyed -ne 'same-id-ready') { throw '合成 WORK 身份准备失败。' }

    $chatText = "CHAT-合成同ID草稿-$([Guid]::NewGuid().ToString('N'))"
    $workText = "WORK-合成同ID草稿-$([Guid]::NewGuid().ToString('N'))"
    $setup = Start-Acceptance
    Set-SyntheticDraft $setup 'CHAT' $workspaceId $chatText
    if ((Click-Action $setup 'show-work') -ne $true) { throw '准备阶段 WORK 可见入口缺失。' }
    Wait-Area $setup 'WORK'
    $sameId = Invoke-Cdp $setup.debugPort 'window.__nanfengDesktopWorkState.current?.summary?.id||null'
    if ($sameId -ne $workspaceId) { throw 'WORK UI 未采用合成同 ID。' }
    Set-SyntheticDraft $setup 'WORK' $workspaceId $workText
    Assert-DraftPair $setup $workspaceId $chatText $workText
    Stop-Acceptance $setup

    $active = Start-Acceptance
    Wait-For { (Invoke-Cdp $active.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $chatText } '同 ID CHAT 草稿未显示。'

    if ($InputDuringSwitchSave) {
        $saveLock = Start-SyntheticLock
        $initial = ($chatText + '-before-switch') | ConvertTo-Json -Compress
        Invoke-Cdp $active.debugPort "(()=>{const e=document.querySelector('#chat-composer');e.value=$initial;e.dispatchEvent(new Event('input',{bubbles:true}));return true})()" | Out-Null
        if ((Click-Action $active 'show-work') -ne $true) { throw '保存等待时 WORK 入口缺失。' }
        Start-Sleep -Milliseconds 250
        $blocked = Invoke-Cdp $active.debugPort "({area:window.__nanfengDesktopWorkState.dataArea,editable:Boolean(document.querySelector('#chat-composer')),text:document.querySelector('#chat-composer')?.value})"
        if ($blocked.area -ne 'CHAT' -or -not $blocked.editable -or $blocked.text -ne ($chatText + '-before-switch')) { throw '实际数据库锁未使切区等待在原输入框。' }
        $chatText += '-final-input'
        $final = $chatText | ConvertTo-Json -Compress
        $typed = Invoke-Cdp $active.debugPort "(()=>{const e=document.querySelector('#chat-composer');if(!e||window.__nanfengDesktopWorkState.dataArea!=='CHAT')return false;e.value=$final;e.dispatchEvent(new Event('input',{bubbles:true}));return true})()"
        if ($typed -ne $true) { throw '等待切区时原输入框不可编辑。' }
        Release-SyntheticLock $saveLock
        Wait-Area $active 'WORK'
        Wait-For { (Read-Draft $active 'CHAT' $workspaceId).text -eq $chatText } '最后输入未保存在 CHAT。'
        Wait-For { (Invoke-Cdp $active.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $workText } 'WORK 原草稿改变。'
        Assert-DraftPair $active $workspaceId $chatText $workText
        Stop-Acceptance $active
        $reopened = Start-Acceptance
        Wait-For { (Invoke-Cdp $reopened.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $chatText } '冷启未恢复最后输入。'
        Assert-DraftPair $reopened $workspaceId $chatText $workText
        if ((Click-Action $reopened 'show-work') -ne $true) { throw '冷启 WORK 入口缺失。' }
        Wait-Area $reopened 'WORK'
        Wait-For { (Invoke-Cdp $reopened.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $workText } '冷启 WORK 草稿改变。'
        Stop-Acceptance $reopened
        [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;workspaceId=$workspaceId;inputDuringSwitchSave=$true;sameIdDraftsIsolatedAfterRestart=$true;blocked=$blocked;stoppedPids=@($stopped)} | ConvertTo-Json -Depth 4
        return
    }

    # Calibrate the visible action: with the CHAT database locked, new-chat stays
    # blank until the native read is released, then restores the persisted text.
    $calibrationLock = Start-SyntheticLock
    if ((Click-Action $active 'new-chat') -ne $true) { throw '新对话可见入口缺失。' }
    Start-Sleep -Milliseconds 250
    if ((Invoke-Cdp $active.debugPort "document.querySelector('#chat-composer')?.value||''") -ne '') { throw '受控锁下读取未停在恢复前。' }
    Release-SyntheticLock $calibrationLock
    Wait-For { (Invoke-Cdp $active.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $chatText } '解除锁后 CHAT 原生草稿未恢复。'

    # The same blocked CHAT read now completes after WORK with the same ID is visible.
    $successLock = Start-SyntheticLock
    if ((Click-Action $active 'new-chat') -ne $true) { throw '迟到成功读取入口缺失。' }
    Start-Sleep -Milliseconds 250
    if ((Click-Action $active 'show-work') -ne $true) { throw '读取迟到时 WORK 入口缺失。' }
    Wait-Area $active 'WORK'
    Release-SyntheticLock $successLock
    Wait-For { (Invoke-Cdp $active.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $workText } 'WORK 草稿未显示。'
    Start-Sleep -Milliseconds 450
    $lateSuccess = Invoke-Cdp $active.debugPort "({area:window.__nanfengDesktopWorkState.dataArea,text:document.querySelector('#chat-composer')?.value||'',error:window.__nanfengDesktopWorkState.error||''})"
    if ($lateSuccess.area -ne 'WORK' -or $lateSuccess.text -ne $workText -or $lateSuccess.error) { throw 'CHAT 迟到成功回执覆盖或污染 WORK。' }
    Assert-DraftPair $active $workspaceId $chatText $workText

    if ((Click-Action $active 'return-workspace-origin') -ne $true) { throw '返回 CHAT 可见入口缺失。' }
    Wait-Area $active 'CHAT'
    Wait-For { (Invoke-Cdp $active.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $chatText } '返回 CHAT 后草稿不一致。'
    $failureLock = Start-SyntheticLock
    if ((Click-Action $active 'new-chat') -ne $true) { throw '第二次新对话入口缺失。' }
    Start-Sleep -Milliseconds 250
    if ((Click-Action $active 'show-work') -ne $true) { throw '失败读取时 WORK 入口缺失。' }
    Wait-Area $active 'WORK'
    Start-Sleep -Seconds 4
    Release-SyntheticLock $failureLock
    Start-Sleep -Milliseconds 450
    $lateFailure = Invoke-Cdp $active.debugPort "({area:window.__nanfengDesktopWorkState.dataArea,text:document.querySelector('#chat-composer')?.value||'',error:window.__nanfengDesktopWorkState.error||''})"
    if ($lateFailure.area -ne 'WORK' -or $lateFailure.text -ne $workText -or $lateFailure.error) { throw 'CHAT 迟到失败回执污染 WORK。' }
    Assert-DraftPair $active $workspaceId $chatText $workText

    # Switching must wait for an in-flight CHAT draft write before changing owner.
    if ((Click-Action $active 'return-workspace-origin') -ne $true) { throw '第二次返回 CHAT 入口缺失。' }
    Wait-Area $active 'CHAT'
    Wait-For { (Invoke-Cdp $active.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $chatText } '写入前 CHAT 草稿不一致。'
    $writeLock = Start-SyntheticLock
    $newChatText = "CHAT-合成迟到写入-$([Guid]::NewGuid().ToString('N'))"
    $newJson = $newChatText | ConvertTo-Json -Compress
    $typed = Invoke-Cdp $active.debugPort "(()=>{const e=document.querySelector('#chat-composer');e.value=$newJson;e.dispatchEvent(new Event('input',{bubbles:true}));return e.value})()"
    if ($typed -ne $newChatText) { throw 'CHAT 合成迟到文字未输入。' }
    $releaseJob = Start-Job -ScriptBlock { param($path) Start-Sleep -Seconds 2; [IO.File]::WriteAllText($path,'release') } -ArgumentList $writeLock.release
    Start-Sleep -Milliseconds 220
    if ((Click-Action $active 'show-work') -ne $true) { throw '写入迟到时 WORK 入口缺失。' }
    Wait-Job $releaseJob -Timeout 5 | Out-Null
    if ($releaseJob.State -ne 'Completed') { throw '合成写锁释放任务失败。' }
    Receive-Job $releaseJob | Out-Null
    Remove-Job $releaseJob
    Release-SyntheticLock $writeLock
    Wait-Area $active 'WORK'
    Wait-For { (Invoke-Cdp $active.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $workText } '写入释放后 WORK 草稿不一致。'
    Assert-DraftPair $active $workspaceId $newChatText $workText
    Stop-Acceptance $active

    $reopened = Start-Acceptance
    Assert-DraftPair $reopened $workspaceId $newChatText $workText
    Wait-For { (Invoke-Cdp $reopened.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $newChatText } '冷启 CHAT 草稿不一致。'
    if ((Click-Action $reopened 'show-work') -ne $true) { throw '冷启 WORK 入口缺失。' }
    Wait-Area $reopened 'WORK'
    Wait-For { (Invoke-Cdp $reopened.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $workText } '冷启 WORK 草稿不一致。'
    Stop-Acceptance $reopened

    $integrityCode = @'
import sqlite3,sys
from pathlib import Path
r=Path(sys.argv[1])
for name in ('workspace.sqlite3','work-area/workspace.sqlite3','app-configuration.sqlite3'):
 db=sqlite3.connect((r/name).as_uri()+'?mode=ro',uri=True)
 assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok',name
print('three-databases-ok')
'@
    $integrity = & python -c $integrityCode $root
    if ($LASTEXITCODE -ne 0 -or $integrity -ne 'three-databases-ok') { throw '同 ID 验收库完整性失败。' }
    [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;workspaceId=$workspaceId;lateReadSuccessIsolated=$true;lateReadFailureIsolated=$true;pendingWriteDrainedBeforeAreaSwitch=$true;sameIdDraftsIsolatedAfterRestart=$true;sqliteIntegrity='ok';stoppedPids=@($stopped)} | ConvertTo-Json -Depth 4
} finally {
    foreach ($lock in @($locks)) {
        try { Release-SyntheticLock $lock } catch {
            if (-not $lock.process.HasExited) { Stop-Process -Id $lock.process.Id -Force }
        }
    }
    foreach ($launch in @($running)) {
        try { Stop-Acceptance $launch } catch { Write-Warning '某验收 PID 未能核路径停止。' }
    }
}
