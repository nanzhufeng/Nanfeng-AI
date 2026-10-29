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
    $output = $expression | & node --max-old-space-size=2048 $cdpHelper $port 2>$null
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

function Read-Workspace($launch, [string]$workspaceId) {
    $headers = @{headers=@{'x-nanfeng-data-area'='CHAT'}} | ConvertTo-Json -Compress
    $id = $workspaceId | ConvertTo-Json -Compress
    return (Invoke-Cdp $launch.debugPort "window.__TAURI__.core.invoke('read_desktop_workspace',{workspaceId:$id},$headers)")
}
function Message-Snapshot($workspace, [string]$text, [string]$attachmentId) {
    $matches = @($workspace.exchange.conversations | ForEach-Object {
        $conversation = $_
        @($conversation.messages | Where-Object {
            $_.role -eq 'user' -and @($_.blocks | Where-Object { $_.text -eq $text }).Count -gt 0
        } | ForEach-Object { [pscustomobject]@{conversationId=$conversation.id;message=$_} })
    })
    $linked = @($matches | Where-Object {
        @($_.message.blocks | Where-Object { $_.kind -eq 'ASSET_REF' -and $_.asset.id -eq $attachmentId }).Count -gt 0
    })
    return [pscustomobject]@{matches=$matches;linked=$linked}
}

try {
    $first = Start-Acceptance
    $providers = Invoke-Cdp $first.debugPort "window.__TAURI__.core.invoke('read_desktop_model_service_settings',{}, {headers:{'x-nanfeng-data-area':'CHAT'}}).then(s=>({count:s.length,allDisabled:s.every(p=>!p.enabled&&!p.credentialStored)}))"
    if ($providers.count -lt 1 -or -not $providers.allDisabled) { throw '验收根存在已启用或存储凭据的 Provider，拒绝发送。' }
    $workspaceId = Invoke-Cdp $first.debugPort "window.__nanfengDesktopWorkState.current?.summary?.id||null"
    if (-not $workspaceId) { throw '合成 CHAT 工作区缺失。' }
    $before = Read-Workspace $first $workspaceId
    $inputText = $textValue | ConvertTo-Json -Compress
    $typed = Invoke-Cdp $first.debugPort "(()=>{const e=document.querySelector('#chat-composer');e.value=$inputText;e.dispatchEvent(new Event('input',{bubbles:true}));return e.value})()"
    if ($typed -ne $textValue) { throw '真实输入框未接收合成正文。' }
    Wait-For { (Read-Draft $first 'CHAT' $workspaceId).text -eq $textValue } '合成正文未持久保存。'
    $parent = [IO.Path]::GetFullPath($RootParent).TrimEnd('\')
    if (-not $root.StartsWith($parent + '\',[StringComparison]::OrdinalIgnoreCase) -or
        -not (Test-Path -LiteralPath (Join-Path $root '.nanfeng-windows-acceptance.json') -PathType Leaf)) { throw '拒绝修改未标记的合成数据根。' }
    $attachmentSource = Join-Path $root 'w05b-synthetic-send.txt'
    [IO.File]::WriteAllText($attachmentSource,'synthetic send attachment',[Text.UTF8Encoding]::new($false))
    $headers = @{headers=@{'x-nanfeng-data-area'='CHAT'}} | ConvertTo-Json -Compress
    $importArgs = @{workspaceId=$workspaceId;selectedPath=$attachmentSource} | ConvertTo-Json -Compress
    $attachment = Invoke-Cdp $first.debugPort "window.__TAURI__.core.invoke('import_desktop_conversation_attachment',{args:$importArgs},$headers)"
    if (-not $attachment.id -or $attachment.displayName -ne 'w05b-synthetic-send.txt') { throw '合成附件导入失败。' }
    $draftArgs = @{workspaceId=$workspaceId;conversationId=$null;text=$textValue;attachmentIds=@([string]$attachment.id);modelId=$null;toneOverride=$null;webSearchOverride=$null} | ConvertTo-Json -Compress -Depth 5
    $saved = Invoke-Cdp $first.debugPort "window.__TAURI__.core.invoke('save_desktop_conversation_draft',{args:$draftArgs},$headers)"
    if ($saved.text -ne $textValue -or @($saved.attachments).Count -ne 1 -or $saved.attachments[0].id -ne $attachment.id) { throw '带附件草稿未保存。' }
    Stop-Acceptance $first

    $reopened = Start-Acceptance
    $attachmentJson = [string]$attachment.id | ConvertTo-Json -Compress
    Wait-For { (Invoke-Cdp $reopened.debugPort "[...document.querySelectorAll('.chat-composer-attachments [data-action=remove-composer-attachment]')].some(x=>x.dataset.id===$attachmentJson)") -eq $true } '冷启未显示附件卡片。'
    if ((Invoke-Cdp $reopened.debugPort "document.querySelector('#chat-composer')?.value||''") -ne $textValue) { throw '冷启输入框正文不一致。' }
    $rejectArgs = @{workspaceId=$workspaceId;conversationId=$null;projectId=$null;expectedRevision=$null;text=$textValue;attachmentIds=@([string]$attachment.id);modelId='INVALID_SYNTHETIC_MODEL';toneOverride=$null;webSearchOverride=$null;egressAuthorization=@{approvedAtMs=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();disclosureVersion='normal-chat-egress-v1';clientSubmissionId='synthetic-precommit-reject'}} | ConvertTo-Json -Compress -Depth 6
    $rejected = Invoke-Cdp $reopened.debugPort "window.__TAURI__.core.invoke('submit_desktop_ordinary_chat',{args:$rejectArgs},$headers).then(()=>false,()=>true)"
    if ($rejected -ne $true) { throw '非法模型未在提交前被拒绝。' }
    $rejectDraft = Read-Draft $reopened 'CHAT' $workspaceId
    $rejectWorkspace = Read-Workspace $reopened $workspaceId
    if ($rejectDraft.text -ne $textValue -or @($rejectDraft.attachments).Count -ne 1 -or
        @($rejectWorkspace.exchange.conversations).Count -ne @($before.exchange.conversations).Count) { throw '提交前拒绝误改草稿或会话。' }

    $clicked = Invoke-Cdp $reopened.debugPort "(()=>{const b=document.querySelector('[data-action=save-local-message]');if(!b||b.disabled)return false;b.click();return true})()"
    if ($clicked -ne $true) { throw '可见发送按钮缺失或禁用。' }
    Wait-For {
        $current = Read-Workspace $reopened $workspaceId
        (Message-Snapshot $current $textValue $attachment.id).linked.Count -eq 1 -and $null -eq (Read-Draft $reopened 'CHAT' $workspaceId)
    } '发送后未形成带附件消息，或草稿未消费。'
    Wait-For { (Invoke-Cdp $reopened.debugPort "Boolean(document.querySelector('.chat-runtime-state.failed'))&&document.querySelector('#chat-composer')?.value==='' ") -eq $true } '未配置 Provider 的本地失败状态或清空 Composer 未出现。'
    Start-Sleep -Milliseconds 500
    $after = Read-Workspace $reopened $workspaceId
    $snapshot = Message-Snapshot $after $textValue $attachment.id
    if ($snapshot.matches.Count -ne 1 -or $snapshot.linked.Count -ne 1 -or $null -ne (Read-Draft $reopened 'CHAT' $workspaceId)) { throw '发送防抖后重复消息或草稿复活。' }
    if (@($after.exchange.conversations).Count -ne (@($before.exchange.conversations).Count + 1)) { throw '发送没有只建立一条新会话。' }
    $conversationId = $snapshot.linked[0].conversationId
    Stop-Acceptance $reopened

    $final = Start-Acceptance
    $cold = Read-Workspace $final $workspaceId
    $coldSnapshot = Message-Snapshot $cold $textValue $attachment.id
    if ($coldSnapshot.matches.Count -ne 1 -or $coldSnapshot.linked.Count -ne 1 -or
        $coldSnapshot.linked[0].conversationId -ne $conversationId -or
        $null -ne (Read-Draft $final 'CHAT' $workspaceId)) { throw '第二次冷启消息或草稿状态不一致。' }
    if ((Invoke-Cdp $final.debugPort "document.querySelector('#chat-composer')?.value||''") -ne '') { throw '第二次冷启 Composer 复活已发送正文。' }
    $routeJson = $conversationId | ConvertTo-Json -Compress
    $selected = Invoke-Cdp $final.debugPort "(()=>{const b=[...document.querySelectorAll('[data-action=select-chat]')].find(x=>x.dataset.id===$routeJson);if(!b)return false;b.click();return true})()"
    if ($selected -ne $true) { throw '已有会话选择入口缺失。' }
    Wait-For { (Invoke-Cdp $final.debugPort "window.__nanfengDesktopWorkState.selectedConversationId===$routeJson") -eq $true } '未选中已有会话。'
    $existingText = "合成已有会话草稿-$([Guid]::NewGuid().ToString('N'))"
    $existingJson = $existingText | ConvertTo-Json -Compress
    $typedExisting = Invoke-Cdp $final.debugPort "(()=>{const e=document.querySelector('#chat-composer');e.value=$existingJson;e.dispatchEvent(new Event('input',{bubbles:true}));return e.value})()"
    if ($typedExisting -ne $existingText) { throw '已有会话输入框未接收合成正文。' }
    Wait-For { (Read-Draft $final 'CHAT' $workspaceId $conversationId).text -eq $existingText } '已有会话草稿未持久化。'
    $sentExisting = Invoke-Cdp $final.debugPort "(()=>{const b=document.querySelector('[data-action=save-local-message]');if(!b||b.disabled)return false;b.click();return true})()"
    if ($sentExisting -ne $true) { throw '已有会话发送按钮缺失。' }
    Wait-For {
        $now = Read-Workspace $final $workspaceId
        (Message-Snapshot $now $existingText '').matches.Count -eq 1 -and $null -eq (Read-Draft $final 'CHAT' $workspaceId $conversationId)
    } '已有会话发送后消息或草稿消费不符。'
    Wait-For { (Invoke-Cdp $final.debugPort "Boolean(document.querySelector('.chat-runtime-state.failed'))&&document.querySelector('#chat-composer')?.value==='' ") -eq $true } '已有会话未显示本地失败状态。'
    Start-Sleep -Milliseconds 500
    $afterExisting = Read-Workspace $final $workspaceId
    $existingSnapshot = Message-Snapshot $afterExisting $existingText ''
    if ($existingSnapshot.matches.Count -ne 1 -or $existingSnapshot.matches[0].conversationId -ne $conversationId -or
        @($afterExisting.exchange.conversations).Count -ne @($before.exchange.conversations).Count + 1 -or
        $null -ne (Read-Draft $final 'CHAT' $workspaceId $conversationId)) { throw '已有会话防抖后路由、唯一性或草稿不符。' }
    $newChat = Invoke-Cdp $final.debugPort "(()=>{const b=document.querySelector('[data-action=new-chat]');if(!b)return false;b.click();return true})()"
    if ($newChat -ne $true) { throw '已有会话的新对话入口缺失。' }
    Wait-For { (Invoke-Cdp $final.debugPort "Boolean(document.querySelector('[data-action=show-work]'))") -eq $true } '新对话未显示工作模式入口。'
    $workClicked = Invoke-Cdp $final.debugPort "(()=>{const b=document.querySelector('[data-action=show-work]');if(!b)return false;b.click();return true})()"
    if ($workClicked -ne $true) { throw 'WORK 切换入口缺失。' }
    Wait-For { (Invoke-Cdp $final.debugPort "window.__nanfengDesktopWorkState.dataArea") -eq 'WORK' } '未切入 WORK。'
    $workId = Invoke-Cdp $final.debugPort "window.__nanfengDesktopWorkState.current?.summary?.id||null"
    if (-not $workId) { throw 'WORK 工作区缺失。' }
    $workHeaders = @{headers=@{'x-nanfeng-data-area'='WORK'}} | ConvertTo-Json -Compress
    $workIdJson = $workId | ConvertTo-Json -Compress
    $work = Invoke-Cdp $final.debugPort "window.__TAURI__.core.invoke('read_desktop_workspace',{workspaceId:$workIdJson},$workHeaders)"
    $workDraft = Read-Draft $final 'WORK' $workId
    if ((Message-Snapshot $work $textValue $attachment.id).matches.Count -ne 0 -or
        $workDraft.text -eq $textValue -or @($workDraft.attachments | Where-Object { $_.id -eq $attachment.id }).Count -ne 0) { throw 'CHAT 已发送消息或附件进入 WORK。' }
    $back = Invoke-Cdp $final.debugPort "(()=>{const b=document.querySelector('[data-action=return-workspace-origin]');if(!b)return false;b.click();return true})()"
    if ($back -ne $true) { throw '返回 CHAT 入口缺失。' }
    Wait-For { (Invoke-Cdp $final.debugPort "window.__nanfengDesktopWorkState.dataArea") -eq 'CHAT' } '未返回 CHAT。'
    if ($null -ne (Read-Draft $final 'CHAT' $workspaceId)) { throw '返回 CHAT 后已发送草稿复活。' }
    Stop-Acceptance $final

    $last = Start-Acceptance
    $lastWorkspace = Read-Workspace $last $workspaceId
    $lastNew = Message-Snapshot $lastWorkspace $textValue $attachment.id
    $lastExisting = Message-Snapshot $lastWorkspace $existingText ''
    if ($lastNew.linked.Count -ne 1 -or $lastExisting.matches.Count -ne 1 -or
        $lastNew.linked[0].conversationId -ne $conversationId -or
        $lastExisting.matches[0].conversationId -ne $conversationId -or
        $null -ne (Read-Draft $last 'CHAT' $workspaceId) -or
        $null -ne (Read-Draft $last 'CHAT' $workspaceId $conversationId)) { throw '已有会话发送后的同根冷启状态不一致。' }
    Stop-Acceptance $last

    $assetPath = Join-Path $root ("assets/{0}" -f $attachment.sha256)
    if (-not (Test-Path -LiteralPath $assetPath -PathType Leaf) -or
        (Get-FileHash -LiteralPath $assetPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne [string]$attachment.sha256 -or
        (Get-FileHash -LiteralPath $assetPath -Algorithm SHA256).Hash -ne (Get-FileHash -LiteralPath $attachmentSource -Algorithm SHA256).Hash) { throw '发送后私有附件资产不一致。' }
    $integrityCode = 'import sqlite3,sys; db=sqlite3.connect(sys.argv[1]); print(db.execute("PRAGMA integrity_check").fetchone()[0]); print(db.execute("SELECT COUNT(*) FROM desktop_conversation_drafts_v1").fetchone()[0])'
    $integrity = @(& python -c $integrityCode (Join-Path $root 'workspace.sqlite3'))
    if ($LASTEXITCODE -ne 0 -or $integrity[0] -ne 'ok' -or $integrity[1] -ne '0') { throw '发送后 SQLite 完整性或草稿行不符。' }
    [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;precommitRejectedAndDraftRetained=$true;newMessageAndAttachmentCommittedOnce=$true;existingMessageCommittedOnce=$true;providerFailedLocally=$true;draftsConsumedAcrossRestart=$true;workIsolated=$true;assetRetained=$true;sqliteIntegrity='ok';stoppedPids=@($stopped)} | ConvertTo-Json -Depth 4
} finally {
    foreach ($launch in @($running)) {
        try { Stop-Acceptance $launch } catch { Write-Warning '某验收 PID 未能核路径停止。' }
    }
}
