param([string]$RootParent = 'D:\CodexTemp')
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
function Start-Acceptance([switch]$DraftReadPause) {
    $port = New-DebugPort
    $params = @{ RootParent=$RootParent; StartupMode='normal'; Visible=$true; DebugPort=$port }
    if ($root) { $params.Root = $root }
    if ($DraftReadPause) { $params.DraftReadPause = $true }
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
function Import-SyntheticAttachment($launch, [string]$area, [string]$workspaceId, [string]$path) {
    $headers = @{headers=@{'x-nanfeng-data-area'=$area}} | ConvertTo-Json -Compress
    $args = @{workspaceId=$workspaceId;selectedPath=$path} | ConvertTo-Json -Compress
    $attachment = Invoke-Cdp $launch.debugPort "window.__TAURI__.core.invoke('import_desktop_conversation_attachment',{args:$args},$headers)"
    if (-not $attachment.id -or -not $attachment.sha256) { throw "$area 合成附件导入失败。" }
    return $attachment
}
function Draft-Spec([string]$area) {
    if ($area -eq 'CHAT') { return [pscustomobject]@{attachment=$chatAttachment;model='DEEPSEEK_V4_PRO';tone='professional';web=$false} }
    return [pscustomobject]@{attachment=$workAttachment;model='GPT_5_6_TERRA';tone='friendly';web=$true}
}
function Set-SyntheticDraft($launch, [string]$area, [string]$workspaceId, [string]$value) {
    $spec = Draft-Spec $area
    $headers = @{headers=@{'x-nanfeng-data-area'=$area}} | ConvertTo-Json -Compress
    $args = @{workspaceId=$workspaceId;conversationId=$null;text=$value;attachmentIds=@([string]$spec.attachment.id);modelId=$spec.model;toneOverride=$spec.tone;webSearchOverride=$spec.web} | ConvertTo-Json -Compress -Depth 4
    $draft = Invoke-Cdp $launch.debugPort "window.__TAURI__.core.invoke('save_desktop_conversation_draft',{args:$args},$headers)"
    if ($draft.text -ne $value -or $draft.modelId -ne $spec.model -or $draft.toneOverride -ne $spec.tone -or
        $draft.webSearchOverride -ne $spec.web -or @($draft.attachments).Count -ne 1 -or
        $draft.attachments[0].id -ne $spec.attachment.id) { throw "$area 合成完整草稿未保存。" }
}
function Assert-DraftPair($launch, [string]$workspaceId, [string]$chatText, [string]$workText) {
    $chat = Read-Draft $launch 'CHAT' $workspaceId
    $work = Read-Draft $launch 'WORK' $workspaceId
    $chatSpec = Draft-Spec 'CHAT'
    $workSpec = Draft-Spec 'WORK'
    if ($chat.text -ne $chatText -or $chat.modelId -ne $chatSpec.model -or $chat.toneOverride -ne $chatSpec.tone -or
        $chat.webSearchOverride -ne $chatSpec.web -or @($chat.attachments).Count -ne 1 -or $chat.attachments[0].id -ne $chatSpec.attachment.id -or
        $work.text -ne $workText -or $work.modelId -ne $workSpec.model -or $work.toneOverride -ne $workSpec.tone -or
        $work.webSearchOverride -ne $workSpec.web -or @($work.attachments).Count -ne 1 -or $work.attachments[0].id -ne $workSpec.attachment.id) {
        throw '同 ID 双区正文、附件或选择回读不一致。'
    }
}
function New-SameIdConversation($launch, [string]$area, [string]$workspaceId, [string]$intentId, [string]$title) {
    $headers = @{headers=@{'x-nanfeng-data-area'=$area}} | ConvertTo-Json -Compress
    $args = @{intentId=$intentId;workspaceId=$workspaceId;entity='conversation';action='create';fields=@{title=$title;projectId=$null;firstMessage="合成 $area 消息"}} | ConvertTo-Json -Compress -Depth 5
    return (Invoke-Cdp $launch.debugPort "window.__TAURI__.core.invoke('mutate_desktop_domain',{args:$args},$headers)")
}
function Set-ExistingDraft($launch, [string]$area, [string]$workspaceId, [string]$conversationId, [string]$value) {
    $spec = Draft-Spec $area
    $headers = @{headers=@{'x-nanfeng-data-area'=$area}} | ConvertTo-Json -Compress
    $args = @{workspaceId=$workspaceId;conversationId=$conversationId;text=$value;attachmentIds=@([string]$spec.attachment.id);modelId=$null;toneOverride=$null;webSearchOverride=$null} | ConvertTo-Json -Compress -Depth 4
    $draft = Invoke-Cdp $launch.debugPort "window.__TAURI__.core.invoke('save_desktop_conversation_draft',{args:$args},$headers)"
    if ($draft.text -ne $value -or @($draft.attachments).Count -ne 1 -or $draft.attachments[0].id -ne $spec.attachment.id) { throw "$area 同会话 ID 草稿未保存。" }
}
function Assert-ExistingDraftPair($launch, [string]$workspaceId, [string]$conversationId, [string]$chatText, [string]$workText) {
    $chat = Read-Draft $launch 'CHAT' $workspaceId $conversationId
    $work = Read-Draft $launch 'WORK' $workspaceId $conversationId
    if ($chat.text -ne $chatText -or $work.text -ne $workText -or
        @($chat.attachments).Count -ne 1 -or $chat.attachments[0].id -ne $chatAttachment.id -or
        @($work.attachments).Count -ne 1 -or $work.attachments[0].id -ne $workAttachment.id) {
        throw '相同 workspace／conversation ID 的双区草稿回读不一致。'
    }
}
function Select-Conversation($launch, [string]$area, [string]$conversationId) {
    $selector = '.chat-sidebar[aria-label="' + $(if ($area -eq 'WORK') { '工作导航' } else { '对话导航' }) + '"] [data-action="select-chat"][data-id="' + $conversationId + '"]'
    $json = $selector | ConvertTo-Json -Compress
    return (Invoke-Cdp $launch.debugPort "(()=>{const e=document.querySelector($json);if(!e)return false;e.click();return true})()")
}
function Assert-VisibleExisting($launch, [string]$area, [string]$conversationId, [string]$value) {
    $spec = Draft-Spec $area
    $visible = Invoke-Cdp $launch.debugPort "({area:window.__nanfengDesktopWorkState.dataArea,conversationId:window.__nanfengDesktopWorkState.selectedConversationId,text:document.querySelector('#chat-composer')?.value||'',attachments:window.__nanfengDesktopWorkState.composerAttachments?.map(x=>x.id)||[]})"
    if ($visible.area -ne $area -or $visible.conversationId -ne $conversationId -or $visible.text -ne $value -or
        @($visible.attachments).Count -ne 1 -or $visible.attachments[0] -ne $spec.attachment.id) {
        throw "$area 相同会话 ID 的可见草稿或附件不一致。"
    }
}
function Assert-VisibleRich($launch, [string]$area, [string]$value) {
    $spec = Draft-Spec $area
    $deadline = [DateTime]::UtcNow.AddSeconds(5)
    do {
        $visible = Invoke-Cdp $launch.debugPort "({area:window.__nanfengDesktopWorkState.dataArea,text:document.querySelector('#chat-composer')?.value||'',model:window.__nanfengDesktopWorkState.pendingNewConversationModelId,tone:window.__nanfengDesktopWorkState.conversationPreferences?.toneOverride,web:window.__nanfengDesktopWorkState.conversationPreferences?.webSearchOverride,attachments:window.__nanfengDesktopWorkState.composerAttachments?.map(x=>x.id)||[],chips:[...document.querySelectorAll('.chat-composer-attachments [data-action=remove-composer-attachment]')].map(x=>x.dataset.id)})"
        if ($visible.area -eq $area -and $visible.text -eq $value -and $visible.model -eq $spec.model -and
            $visible.tone -eq $spec.tone -and $visible.web -eq $spec.web -and
            @($visible.attachments).Count -eq 1 -and $visible.attachments[0] -eq $spec.attachment.id -and
            @($visible.chips).Count -eq 1 -and $visible.chips[0] -eq $spec.attachment.id) { return }
        Start-Sleep -Milliseconds 120
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "$area 可见 Composer 的正文、附件卡片或选择不一致：$($visible | ConvertTo-Json -Compress -Depth 3)"
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
    $chatSource = Join-Path $root 'rich-race-chat.txt'
    $workSource = Join-Path $root 'rich-race-work.txt'
    [IO.File]::WriteAllText($chatSource,"synthetic CHAT attachment $chatText",[Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText($workSource,"synthetic WORK attachment $workText",[Text.UTF8Encoding]::new($false))
    $setup = Start-Acceptance
    $chatAttachment = Import-SyntheticAttachment $setup 'CHAT' $workspaceId $chatSource
    $workAttachment = Import-SyntheticAttachment $setup 'WORK' $workspaceId $workSource
    if ($chatAttachment.id -eq $workAttachment.id) { throw '两区合成附件身份未区分。' }
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
    Assert-VisibleRich $active 'CHAT' $chatText

    # Calibrate the visible action: with the CHAT database locked, new-chat stays
    # blank until the native read is released, then restores the persisted text.
    $calibrationLock = Start-SyntheticLock
    if ((Click-Action $active 'new-chat') -ne $true) { throw '新对话可见入口缺失。' }
    Start-Sleep -Milliseconds 250
    if ((Invoke-Cdp $active.debugPort "document.querySelector('#chat-composer')?.value||''") -ne '') { throw '受控锁下读取未停在恢复前。' }
    Release-SyntheticLock $calibrationLock
    Wait-For { (Invoke-Cdp $active.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $chatText } '解除锁后 CHAT 原生草稿未恢复。'
    Assert-VisibleRich $active 'CHAT' $chatText

    # The same blocked CHAT read now completes after WORK with the same ID is visible.
    $successLock = Start-SyntheticLock
    if ((Click-Action $active 'new-chat') -ne $true) { throw '迟到成功读取入口缺失。' }
    Start-Sleep -Milliseconds 250
    if ((Click-Action $active 'show-work') -ne $true) { throw '读取迟到时 WORK 入口缺失。' }
    Wait-Area $active 'WORK'
    Release-SyntheticLock $successLock
    Wait-For { (Invoke-Cdp $active.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $workText } 'WORK 草稿未显示。'
    Assert-VisibleRich $active 'WORK' $workText
    Start-Sleep -Milliseconds 450
    $lateSuccess = Invoke-Cdp $active.debugPort "({area:window.__nanfengDesktopWorkState.dataArea,text:document.querySelector('#chat-composer')?.value||'',error:window.__nanfengDesktopWorkState.error||''})"
    if ($lateSuccess.area -ne 'WORK' -or $lateSuccess.text -ne $workText -or $lateSuccess.error) { throw 'CHAT 迟到成功回执覆盖或污染 WORK。' }
    Assert-VisibleRich $active 'WORK' $workText
    Assert-DraftPair $active $workspaceId $chatText $workText

    if ((Click-Action $active 'return-workspace-origin') -ne $true) { throw '返回 CHAT 可见入口缺失。' }
    Wait-Area $active 'CHAT'
    Wait-For { (Invoke-Cdp $active.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $chatText } '返回 CHAT 后草稿不一致。'
    Assert-VisibleRich $active 'CHAT' $chatText
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
    Assert-VisibleRich $active 'WORK' $workText
    Assert-DraftPair $active $workspaceId $chatText $workText

    # Switching must wait for an in-flight CHAT draft write before changing owner.
    if ((Click-Action $active 'return-workspace-origin') -ne $true) { throw '第二次返回 CHAT 入口缺失。' }
    Wait-Area $active 'CHAT'
    Wait-For { (Invoke-Cdp $active.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $chatText } '写入前 CHAT 草稿不一致。'
    Assert-VisibleRich $active 'CHAT' $chatText
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
    Assert-VisibleRich $active 'WORK' $workText
    Assert-DraftPair $active $workspaceId $newChatText $workText
    Stop-Acceptance $active

    $reopened = Start-Acceptance
    Assert-DraftPair $reopened $workspaceId $newChatText $workText
    Wait-For { (Invoke-Cdp $reopened.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $newChatText } '冷启 CHAT 草稿不一致。'
    Assert-VisibleRich $reopened 'CHAT' $newChatText
    if ((Click-Action $reopened 'show-work') -ne $true) { throw '冷启 WORK 入口缺失。' }
    Wait-Area $reopened 'WORK'
    Wait-For { (Invoke-Cdp $reopened.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $workText } '冷启 WORK 草稿不一致。'
    Assert-VisibleRich $reopened 'WORK' $workText
    $sameIntent = "same-conversation-$([Guid]::NewGuid().ToString('N').Substring(0,20))"
    $chatConversation = New-SameIdConversation $reopened 'CHAT' $workspaceId $sameIntent 'CHAT 同会话 ID 合成记录'
    $workConversation = New-SameIdConversation $reopened 'WORK' $workspaceId $sameIntent 'WORK 同会话 ID 合成记录'
    $sameConversationId = [string]$chatConversation.objectId
    if (-not $sameConversationId -or $workConversation.objectId -ne $sameConversationId) { throw '双区未生成相同 conversation ID。' }
    $chatExistingText = "CHAT-已有会话草稿-$([Guid]::NewGuid().ToString('N'))"
    $workExistingText = "WORK-已有会话草稿-$([Guid]::NewGuid().ToString('N'))"
    Set-ExistingDraft $reopened 'CHAT' $workspaceId $sameConversationId $chatExistingText
    Set-ExistingDraft $reopened 'WORK' $workspaceId $sameConversationId $workExistingText
    Assert-ExistingDraftPair $reopened $workspaceId $sameConversationId $chatExistingText $workExistingText
    Stop-Acceptance $reopened

    $same = Start-Acceptance
    $chatSelector = '.chat-sidebar[aria-label="对话导航"] [data-action="select-chat"][data-id="' + $sameConversationId + '"]'
    $chatSelectorJson = $chatSelector | ConvertTo-Json -Compress
    Wait-For { Invoke-Cdp $same.debugPort "Boolean(document.querySelector($chatSelectorJson))" } 'CHAT 同 ID 会话未进入侧栏。'
    if ((Select-Conversation $same 'CHAT' $sameConversationId) -ne $true) { throw 'CHAT 同 ID 会话不可点击。' }
    Wait-For { (Invoke-Cdp $same.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $chatExistingText } 'CHAT 已有会话草稿未显示。'
    Assert-VisibleExisting $same 'CHAT' $sameConversationId $chatExistingText
    if ((Click-Action $same 'new-chat') -ne $true) { throw 'CHAT 已有会话离开入口缺失。' }
    Wait-For { (Invoke-Cdp $same.debugPort 'window.__nanfengDesktopWorkState.selectedConversationId===null') -eq $true } 'CHAT 未回到新对话。'
    if ((Click-Action $same 'show-work') -ne $true) { throw '同 ID 会话 WORK 入口缺失。' }
    Wait-Area $same 'WORK'
    $workSelector = '.chat-sidebar[aria-label="工作导航"] [data-action="select-chat"][data-id="' + $sameConversationId + '"]'
    $workSelectorJson = $workSelector | ConvertTo-Json -Compress
    Wait-For { Invoke-Cdp $same.debugPort "Boolean(document.querySelector($workSelectorJson))" } 'WORK 同 ID 会话未进入侧栏。'
    if ((Select-Conversation $same 'WORK' $sameConversationId) -ne $true) { throw 'WORK 同 ID 会话不可点击。' }
    Wait-For { (Invoke-Cdp $same.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $workExistingText } 'WORK 已有会话草稿未显示。'
    Assert-VisibleExisting $same 'WORK' $sameConversationId $workExistingText
    if ((Click-Action $same 'show-chat') -ne $true) { throw '同 ID 会话返回 CHAT 入口缺失。' }
    Wait-Area $same 'CHAT'
    if ((Select-Conversation $same 'CHAT' $sameConversationId) -ne $true) { throw '返回 CHAT 后同 ID 会话不可点击。' }
    Wait-For { (Invoke-Cdp $same.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $chatExistingText } '同 ID 会话返回 CHAT 后草稿不一致。'
    Assert-VisibleExisting $same 'CHAT' $sameConversationId $chatExistingText

    if ((Click-Action $same 'new-chat') -ne $true) { throw '同 ID 迟到读取的新对话入口缺失。' }
    Wait-For { (Invoke-Cdp $same.debugPort 'window.__nanfengDesktopWorkState.selectedConversationId===null') -eq $true } 'CHAT 未离开已有会话。'
    $sameReadLock = Start-SyntheticLock
    if ((Select-Conversation $same 'CHAT' $sameConversationId) -ne $true) { throw '受控锁下 CHAT 同 ID 会话不可点击。' }
    Start-Sleep -Milliseconds 250
    if ((Click-Action $same 'new-chat') -ne $true) { throw '迟到读取时 CHAT 新对话入口缺失。' }
    Wait-For { (Invoke-Cdp $same.debugPort 'window.__nanfengDesktopWorkState.selectedConversationId===null') -eq $true } '迟到读取时 CHAT 未回到新对话。'
    if ((Click-Action $same 'show-work') -ne $true) { throw '同 ID 读取迟到时 WORK 入口缺失。' }
    Wait-Area $same 'WORK'
    if ((Select-Conversation $same 'WORK' $sameConversationId) -ne $true) { throw 'WORK 同 ID 会话不可点击。' }
    Wait-For { (Invoke-Cdp $same.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $workExistingText } '受控锁下 WORK 草稿未显示。'
    Release-SyntheticLock $sameReadLock
    Start-Sleep -Milliseconds 450
    Assert-VisibleExisting $same 'WORK' $sameConversationId $workExistingText
    Assert-ExistingDraftPair $same $workspaceId $sameConversationId $chatExistingText $workExistingText
    Stop-Acceptance $same

    $sameReopened = Start-Acceptance
    Wait-For { Invoke-Cdp $sameReopened.debugPort "Boolean(document.querySelector($chatSelectorJson))" } '冷启 CHAT 同 ID 会话未进入侧栏。'
    if ((Select-Conversation $sameReopened 'CHAT' $sameConversationId) -ne $true) { throw '冷启 CHAT 同 ID 会话不可点击。' }
    Wait-For { (Invoke-Cdp $sameReopened.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $chatExistingText } '冷启 CHAT 同 ID 草稿不一致。'
    Assert-VisibleExisting $sameReopened 'CHAT' $sameConversationId $chatExistingText
    if ((Click-Action $sameReopened 'new-chat') -ne $true) { throw '冷启 CHAT 已有会话离开入口缺失。' }
    Wait-For { (Invoke-Cdp $sameReopened.debugPort 'window.__nanfengDesktopWorkState.selectedConversationId===null') -eq $true } '冷启 CHAT 未回到新对话。'
    if ((Click-Action $sameReopened 'show-work') -ne $true) { throw '冷启 WORK 同 ID 入口缺失。' }
    Wait-Area $sameReopened 'WORK'
    if ((Select-Conversation $sameReopened 'WORK' $sameConversationId) -ne $true) { throw '冷启 WORK 同 ID 会话不可点击。' }
    Wait-For { (Invoke-Cdp $sameReopened.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $workExistingText } '冷启 WORK 同 ID 草稿不一致。'
    Assert-VisibleExisting $sameReopened 'WORK' $sameConversationId $workExistingText
    Assert-ExistingDraftPair $sameReopened $workspaceId $sameConversationId $chatExistingText $workExistingText
    Stop-Acceptance $sameReopened

    # A selection-only edit while the old draft is still loading must keep its
    # saved text and attachment. This is a real visible action, not an IPC save.
    $choice = Start-Acceptance
    Wait-For { (Invoke-Cdp $choice.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $newChatText } '选择竞争前 CHAT 新对话草稿未显示。'
    $choiceLock = Start-SyntheticLock
    if ((Click-Action $choice 'new-chat') -ne $true) { throw '选择竞争的新对话入口缺失。' }
    Start-Sleep -Milliseconds 250
    if ((Invoke-Cdp $choice.debugPort "document.querySelector('#chat-composer')?.value||''") -ne '') { throw '选择竞争读取未受控阻塞。' }
    if ((Click-Action $choice 'toggle-composer-add') -ne $true) { throw '挂起读取时附件菜单入口缺失。' }
    $beforeChoice = Invoke-Cdp $choice.debugPort "({web:window.__nanfengDesktopWorkState.conversationPreferences?.webSearchOverride,checked:document.querySelector('.composer-add-sheet [data-action=toggle-conversation-web-search]')?.getAttribute('aria-checked')})"
    $expectedWeb = $beforeChoice.checked -ne 'true'
    $choiceButton = Invoke-Cdp $choice.debugPort "(()=>{const sheet=document.querySelector('.composer-add-sheet');const button=sheet?.querySelector('[data-action=toggle-conversation-web-search]');if(!button)return {found:false,open:Boolean(sheet)};button.click();return {found:true,open:true}})()"
    if (-not $choiceButton.found) { throw "挂起读取时联网选择入口缺失：$($choiceButton | ConvertTo-Json -Compress)。" }
    $pendingChoice = Invoke-Cdp $choice.debugPort "({area:window.__nanfengDesktopWorkState.dataArea,conversationId:window.__nanfengDesktopWorkState.selectedConversationId,web:window.__nanfengDesktopWorkState.conversationPreferences?.webSearchOverride,checked:document.querySelector('[data-action=toggle-conversation-web-search]')?.getAttribute('aria-checked')})"
    if ($pendingChoice.web -ne $expectedWeb) { throw "挂起读取时联网选择未更新：前 $($beforeChoice | ConvertTo-Json -Compress)，后 $($pendingChoice | ConvertTo-Json -Compress)。" }
    if ((Click-Action $choice 'close-composer-add') -ne $true) { throw '联网选择面板未关闭。' }
    Release-SyntheticLock $choiceLock
    Wait-For { (Invoke-Cdp $choice.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $newChatText } '选择竞争后旧草稿正文未恢复。'
    $choiceVisible = Invoke-Cdp $choice.debugPort "({attachments:window.__nanfengDesktopWorkState.composerAttachments?.map(x=>x.id)||[],model:window.__nanfengDesktopWorkState.pendingNewConversationModelId,tone:window.__nanfengDesktopWorkState.conversationPreferences?.toneOverride,web:window.__nanfengDesktopWorkState.conversationPreferences?.webSearchOverride})"
    if (@($choiceVisible.attachments).Count -ne 1 -or $choiceVisible.attachments[0] -ne $chatAttachment.id -or
        $choiceVisible.model -ne (Draft-Spec 'CHAT').model -or $choiceVisible.tone -ne (Draft-Spec 'CHAT').tone -or $choiceVisible.web -ne $expectedWeb) {
        throw '选择竞争后 CHAT 可见附件或选择不一致。'
    }
    $choiceChat = Read-Draft $choice 'CHAT' $workspaceId
    $choiceWork = Read-Draft $choice 'WORK' $workspaceId
    if ($choiceChat.text -ne $newChatText -or $choiceChat.attachments[0].id -ne $chatAttachment.id -or
        $choiceChat.modelId -ne (Draft-Spec 'CHAT').model -or $choiceChat.toneOverride -ne (Draft-Spec 'CHAT').tone -or $choiceChat.webSearchOverride -ne $expectedWeb -or
        $choiceWork.text -ne $workText -or $choiceWork.attachments[0].id -ne $workAttachment.id -or $choiceWork.webSearchOverride -ne (Draft-Spec 'WORK').web) {
        throw '选择竞争后双区原生草稿未隔离。'
    }
    Stop-Acceptance $choice
    $choiceReopened = Start-Acceptance
    Wait-For { (Invoke-Cdp $choiceReopened.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $newChatText } '选择竞争冷启后 CHAT 正文不一致。'
    $reopenedChoice = Read-Draft $choiceReopened 'CHAT' $workspaceId
    if ($reopenedChoice.webSearchOverride -ne $expectedWeb -or $reopenedChoice.attachments[0].id -ne $chatAttachment.id) { throw '选择竞争冷启回读不一致。' }
    Stop-Acceptance $choiceReopened

    # A marked Debug acceptance root pauses one native draft reply after its
    # store lock is released. Paste still uses WebView2 and the real import IPC.
    $pasted = Start-Acceptance -DraftReadPause
    Wait-For { (Invoke-Cdp $pasted.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $newChatText } '附件竞争前 CHAT 草稿未显示。'
    $armPath = Join-Path $root 'draft-read-pause.arm'
    $reachedPath = Join-Path $root 'draft-read-pause.reached'
    $continuePath = Join-Path $root 'draft-read-pause.continue'
    $staleMarkers = @($armPath,$reachedPath,$continuePath) | Where-Object { Test-Path -LiteralPath $_ }
    if (@($staleMarkers).Count -gt 0) { throw '草稿读取停点标记在布防前已存在。' }
    $armFile = [IO.File]::Open($armPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    $armFile.Dispose()
    if ((Click-Action $pasted 'new-chat') -ne $true) { throw '附件竞争的新对话入口缺失。' }
    Wait-For { Test-Path -LiteralPath $reachedPath -PathType Leaf } '原生草稿回执未停在受控故障点。'
    if ((Invoke-Cdp $pasted.debugPort "document.querySelector('#chat-composer')?.value||''") -ne '') { throw '附件竞争读取期间正文未清空。' }
    $pasteEvent = Invoke-Cdp $pasted.debugPort "(()=>{const data=new DataTransfer();data.items.add(new File([new TextEncoder().encode('synthetic pending paste attachment')],'pending-paste.txt',{type:'text/plain'}));const target=document.querySelector('#chat-composer');const event=new ClipboardEvent('paste',{bubbles:true,cancelable:true,clipboardData:data});target.dispatchEvent(event);return {files:data.files.length,prevented:event.defaultPrevented}})()"
    if ($pasteEvent.files -ne 1 -or $pasteEvent.prevented -ne $true) { throw '合成文件粘贴未被 Composer 接收。' }
    Wait-For { (Invoke-Cdp $pasted.debugPort 'window.__nanfengDesktopWorkState.composerAttachments?.length||0') -eq 1 } '挂起读取时新附件未进入 Composer。'
    $pendingAttachment = Invoke-Cdp $pasted.debugPort 'window.__nanfengDesktopWorkState.composerAttachments[0]'
    if (-not $pendingAttachment.id -or -not $pendingAttachment.sha256 -or $pendingAttachment.id -eq $chatAttachment.id) { throw '挂起读取期间粘贴附件身份无效。' }
    $continueFile = [IO.File]::Open($continuePath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    $continueFile.Dispose()
    Wait-For { (Invoke-Cdp $pasted.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $newChatText } '附件竞争后旧正文未恢复。'
    Wait-For { (Invoke-Cdp $pasted.debugPort 'window.__nanfengDesktopWorkState.composerAttachments?.length||0') -eq 2 } '附件竞争后旧/新附件未合并。'
    $mergedPaste = Read-Draft $pasted 'CHAT' $workspaceId
    $unaffectedWork = Read-Draft $pasted 'WORK' $workspaceId
    if ($mergedPaste.text -ne $newChatText -or @($mergedPaste.attachments).Count -ne 2 -or
        @($mergedPaste.attachments | Where-Object id -eq $chatAttachment.id).Count -ne 1 -or
        @($mergedPaste.attachments | Where-Object id -eq $pendingAttachment.id).Count -ne 1 -or
        $unaffectedWork.text -ne $workText -or $unaffectedWork.attachments[0].id -ne $workAttachment.id) {
        throw '附件竞争后双区草稿回读不一致。'
    }
    Stop-Acceptance $pasted
    Remove-Item -LiteralPath $reachedPath,$continuePath -ErrorAction Stop
    $pasteReopened = Start-Acceptance
    Wait-For { (Invoke-Cdp $pasteReopened.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $newChatText } '附件竞争冷启后旧正文不一致。'
    $reopenedPaste = Read-Draft $pasteReopened 'CHAT' $workspaceId
    if (@($reopenedPaste.attachments).Count -ne 2 -or
        @($reopenedPaste.attachments | Where-Object id -eq $chatAttachment.id).Count -ne 1 -or
        @($reopenedPaste.attachments | Where-Object id -eq $pendingAttachment.id).Count -ne 1) { throw '附件竞争冷启后旧/新附件不一致。' }
    Stop-Acceptance $pasteReopened

    # Remove a newly imported attachment while the old two-attachment read
    # reply is still held. The removal must not erase the older draft assets.
    $removed = Start-Acceptance -DraftReadPause
    Wait-For { (Invoke-Cdp $removed.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $newChatText } '移除竞争前 CHAT 草稿未显示。'
    $staleMarkers = @($armPath,$reachedPath,$continuePath) | Where-Object { Test-Path -LiteralPath $_ }
    if (@($staleMarkers).Count -gt 0) { throw '移除竞争布防前停点标记已存在。' }
    $armFile = [IO.File]::Open($armPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    $armFile.Dispose()
    if ((Click-Action $removed 'new-chat') -ne $true) { throw '移除竞争的新对话入口缺失。' }
    Wait-For { Test-Path -LiteralPath $reachedPath -PathType Leaf } '移除竞争的原生草稿回执未暂停。'
    $removeEvent = Invoke-Cdp $removed.debugPort "(()=>{const data=new DataTransfer();data.items.add(new File([new TextEncoder().encode('synthetic pending removed attachment')],'pending-removed.txt',{type:'text/plain'}));const target=document.querySelector('#chat-composer');const event=new ClipboardEvent('paste',{bubbles:true,cancelable:true,clipboardData:data});target.dispatchEvent(event);return {files:data.files.length,prevented:event.defaultPrevented}})()"
    if ($removeEvent.files -ne 1 -or $removeEvent.prevented -ne $true) { throw '移除竞争的合成文件粘贴未被 Composer 接收。' }
    Wait-For { (Invoke-Cdp $removed.debugPort 'window.__nanfengDesktopWorkState.composerAttachments?.length||0') -eq 1 } '移除竞争的新附件未进入 Composer。'
    $removedAttachment = Invoke-Cdp $removed.debugPort 'window.__nanfengDesktopWorkState.composerAttachments[0]'
    if (-not $removedAttachment.id -or -not $removedAttachment.sha256 -or $removedAttachment.id -eq $chatAttachment.id -or $removedAttachment.id -eq $pendingAttachment.id) { throw '待移除附件身份无效。' }
    $removeId = [string]$removedAttachment.id | ConvertTo-Json -Compress
    $didRemove = Invoke-Cdp $removed.debugPort "(()=>{const b=[...document.querySelectorAll('[data-action=remove-composer-attachment]')].find(x=>x.dataset.id===$removeId);if(!b)return false;b.click();return true})()"
    if ($didRemove -ne $true) { throw '移除竞争的可见附件卡片按钮缺失。' }
    Wait-For { (Invoke-Cdp $removed.debugPort 'window.__nanfengDesktopWorkState.composerAttachments?.length||0') -eq 0 } '挂起读取期间新附件未从 Composer 移除。'
    $continueFile = [IO.File]::Open($continuePath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    $continueFile.Dispose()
    Wait-For { (Invoke-Cdp $removed.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $newChatText } '移除竞争后旧正文未恢复。'
    Wait-For { (Invoke-Cdp $removed.debugPort 'window.__nanfengDesktopWorkState.composerAttachments?.length||0') -eq 2 } '移除竞争后旧附件未恢复。'
    $mergedRemove = Read-Draft $removed 'CHAT' $workspaceId
    $unaffectedRemoveWork = Read-Draft $removed 'WORK' $workspaceId
    if ($mergedRemove.text -ne $newChatText -or @($mergedRemove.attachments).Count -ne 2 -or
        @($mergedRemove.attachments | Where-Object id -eq $chatAttachment.id).Count -ne 1 -or
        @($mergedRemove.attachments | Where-Object id -eq $pendingAttachment.id).Count -ne 1 -or
        @($mergedRemove.attachments | Where-Object id -eq $removedAttachment.id).Count -ne 0 -or
        $unaffectedRemoveWork.text -ne $workText -or $unaffectedRemoveWork.attachments[0].id -ne $workAttachment.id) {
        throw '移除竞争后双区草稿回读不一致。'
    }
    Stop-Acceptance $removed
    Remove-Item -LiteralPath $reachedPath,$continuePath -ErrorAction Stop
    $removeReopened = Start-Acceptance
    Wait-For { (Invoke-Cdp $removeReopened.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $newChatText } '移除竞争冷启后旧正文不一致。'
    $reopenedRemove = Read-Draft $removeReopened 'CHAT' $workspaceId
    if (@($reopenedRemove.attachments).Count -ne 2 -or
        @($reopenedRemove.attachments | Where-Object id -eq $chatAttachment.id).Count -ne 1 -or
        @($reopenedRemove.attachments | Where-Object id -eq $pendingAttachment.id).Count -ne 1 -or
        @($reopenedRemove.attachments | Where-Object id -eq $removedAttachment.id).Count -ne 0) { throw '移除竞争冷启后附件回读不一致。' }
    Stop-Acceptance $removeReopened

    $chatAsset = Join-Path $root ("assets/{0}" -f $chatAttachment.sha256)
    $pendingAsset = Join-Path $root ("assets/{0}" -f $pendingAttachment.sha256)
    $workAsset = Join-Path $root ("work-area/assets/{0}" -f $workAttachment.sha256)
    if (-not (Test-Path -LiteralPath $chatAsset -PathType Leaf) -or -not (Test-Path -LiteralPath $pendingAsset -PathType Leaf) -or -not (Test-Path -LiteralPath $workAsset -PathType Leaf) -or
        (Get-FileHash -LiteralPath $chatAsset -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $chatSource -Algorithm SHA256).Hash -or
        (Get-FileHash -LiteralPath $pendingAsset -Algorithm SHA256).Hash -ne $pendingAttachment.sha256 -or
        (Get-FileHash -LiteralPath $workAsset -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $workSource -Algorithm SHA256).Hash) { throw '双区私有附件资产与合成来源不一致。' }
    $integrityCode = @'
import sqlite3,sys
from pathlib import Path
r=Path(sys.argv[1])
for name in ('workspace.sqlite3','work-area/workspace.sqlite3','app-configuration.sqlite3'):
 db=sqlite3.connect((r/name).as_uri()+'?mode=ro',uri=True)
 assert db.execute('PRAGMA integrity_check').fetchone()[0]=='ok',name
 if name!='app-configuration.sqlite3':
  expected=[(sys.argv[2],sys.argv[3]),(sys.argv[6],sys.argv[7]),(sys.argv[8],sys.argv[9])] if name=='workspace.sqlite3' else [(sys.argv[4],sys.argv[5])]
  assert sorted(db.execute('SELECT attachment_id,sha256 FROM desktop_conversation_attachments').fetchall())==sorted(expected),name
print('three-databases-ok')
'@
    $integrity = & python -c $integrityCode $root $chatAttachment.id $chatAttachment.sha256 $workAttachment.id $workAttachment.sha256 $pendingAttachment.id $pendingAttachment.sha256 $removedAttachment.id $removedAttachment.sha256
    if ($LASTEXITCODE -ne 0 -or $integrity -ne 'three-databases-ok') { throw '同 ID 验收库完整性失败。' }
    [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;workspaceId=$workspaceId;conversationId=$sameConversationId;lateReadSuccessIsolated=$true;lateReadFailureIsolated=$true;richDraftWriteAreaIsolated=$true;sameIdAttachmentsAndChoicesIsolatedAfterRestart=$true;sameConversationIdNativeAndLateReadIsolated=$true;pendingChoiceEditPreservedTextAndAttachment=$true;pendingClipboardAttachmentMergedAfterLateRead=$true;pendingClipboardRemovalExcludedAfterLateRead=$true;privateAssetHashesMatch=$true;sqliteIntegrity='ok';stoppedPids=@($stopped)} | ConvertTo-Json -Depth 4
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
