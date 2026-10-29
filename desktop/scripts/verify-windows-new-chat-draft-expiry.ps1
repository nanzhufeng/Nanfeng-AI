param([string]$RootParent = 'D:\CodexTemp', [ValidateSet(59,61)][int]$AgeMinutes = 61)
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
function Read-Draft($launch, [string]$area, [string]$workspaceId) {
    $headers = @{headers=@{'x-nanfeng-data-area'=$area}} | ConvertTo-Json -Compress
    $id = $workspaceId | ConvertTo-Json -Compress
    return (Invoke-Cdp $launch.debugPort "window.__TAURI__.core.invoke('read_desktop_conversation_draft',{workspaceId:$id,conversationId:null},$headers)")
}
function Wait-For([scriptblock]$check, [string]$errorText) {
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    while ([DateTime]::UtcNow -lt $deadline) {
        try { if (& $check) { return } } catch {}
        Start-Sleep -Milliseconds 150
    }
    throw $errorText
}

try {
    $first = Start-Acceptance
    $inputText = $textValue | ConvertTo-Json -Compress
    $typed = Invoke-Cdp $first.debugPort "(()=>{const e=document.querySelector('#chat-composer');e.value=$inputText;e.dispatchEvent(new Event('input',{bubbles:true}));return e.value})()"
    if ($typed -ne $textValue) { throw '真实输入框未接收合成草稿。' }
    $workspaceId = Invoke-Cdp $first.debugPort "window.__nanfengDesktopWorkState.current?.summary?.id||null"
    if (-not $workspaceId) { throw 'CHAT 工作区未建立。' }
    Wait-For { (Read-Draft $first 'CHAT' $workspaceId).text -eq $textValue } '新对话文字未持久保存。'
    $parent = [IO.Path]::GetFullPath($RootParent).TrimEnd('\')
    if (-not $root.StartsWith($parent + '\',[StringComparison]::OrdinalIgnoreCase) -or
        -not (Test-Path -LiteralPath (Join-Path $root '.nanfeng-windows-acceptance.json') -PathType Leaf)) { throw '拒绝修改未标记的合成数据根。' }
    $attachmentSource = Join-Path $root 'w05b-synthetic-draft.txt'
    [IO.File]::WriteAllText($attachmentSource,'synthetic draft attachment',[Text.UTF8Encoding]::new($false))
    $headers = @{headers=@{'x-nanfeng-data-area'='CHAT'}} | ConvertTo-Json -Compress
    $importArgs = @{workspaceId=$workspaceId;selectedPath=$attachmentSource} | ConvertTo-Json -Compress
    $attachment = Invoke-Cdp $first.debugPort "window.__TAURI__.core.invoke('import_desktop_conversation_attachment',{args:$importArgs},$headers)"
    if (-not $attachment.id -or $attachment.displayName -ne 'w05b-synthetic-draft.txt') { throw '合成附件导入失败。' }
    $draftArgs = @{workspaceId=$workspaceId;conversationId=$null;text=$textValue;attachmentIds=@([string]$attachment.id);modelId='DEEPSEEK_V4_PRO';toneOverride='professional';webSearchOverride=$false} | ConvertTo-Json -Compress -Depth 5
    $saved = Invoke-Cdp $first.debugPort "window.__TAURI__.core.invoke('save_desktop_conversation_draft',{args:$draftArgs},$headers)"
    if ($saved.text -ne $textValue -or $saved.modelId -ne 'DEEPSEEK_V4_PRO' -or $saved.toneOverride -ne 'professional' -or $saved.webSearchOverride -ne $false -or
        @($saved.attachments).Count -ne 1 -or $saved.attachments[0].id -ne $attachment.id) { throw '合成草稿选择或附件未保存。' }
    Stop-Acceptance $first

    $database = Join-Path $root 'workspace.sqlite3'
    $ageCode = @'
import sqlite3,sys,time
path,workspace,text,minutes,attachment=sys.argv[1:]
db=sqlite3.connect(path)
row=db.execute("SELECT text,model_id,tone_override,web_search_override FROM desktop_conversation_drafts_v1 WHERE workspace_id=? AND conversation_key='new-conversation-draft'",(workspace,)).fetchone()
if row != (text,'DEEPSEEK_V4_PRO','professional',0): raise SystemExit('unexpected synthetic draft row')
links=db.execute("SELECT attachment_id FROM desktop_conversation_draft_attachments_v1 WHERE workspace_id=? AND conversation_key='new-conversation-draft'",(workspace,)).fetchall()
if links != [(attachment,)]: raise SystemExit('unexpected synthetic draft attachment')
old=int(time.time()*1000)-int(minutes)*60*1000
db.execute("UPDATE desktop_conversation_drafts_v1 SET updated_at_ms=? WHERE workspace_id=? AND conversation_key='new-conversation-draft'",(old,workspace))
db.commit()
if db.execute("PRAGMA integrity_check").fetchone()[0]!='ok': raise SystemExit('synthetic database integrity failure')
print('ok')
'@
    $aged = & python -c $ageCode $database $workspaceId $textValue $AgeMinutes $attachment.id
    if ($LASTEXITCODE -ne 0 -or $aged -ne 'ok') { throw '合成草稿时间推进失败。' }

    $reopened = Start-Acceptance
    $expectedText = if ($AgeMinutes -ge 60) { '' } else { $textValue }
    Wait-For { (Read-Draft $reopened 'CHAT' $workspaceId).text -eq $expectedText } '新对话文字与一小时边界不符。'
    Start-Sleep -Milliseconds 700
    $after = Read-Draft $reopened 'CHAT' $workspaceId
    $visible = Invoke-Cdp $reopened.debugPort "document.querySelector('#chat-composer')?.value||''"
    if ($after.text -ne $expectedText -or $visible -ne $expectedText -or $after.modelId -ne 'DEEPSEEK_V4_PRO' -or
        $after.toneOverride -ne 'professional' -or $after.webSearchOverride -ne $false -or
        @($after.attachments).Count -ne 1 -or $after.attachments[0].id -ne $attachment.id -or
        (($AgeMinutes -ge 60) -ne ($null -eq $after.expiresAtMs))) {
        throw '一小时边界的正文、界面或模型/风格/联网选择不一致。'
    }
    $attachmentJson = [string]$attachment.id | ConvertTo-Json -Compress
    Wait-For { (Invoke-Cdp $reopened.debugPort "window.__nanfengDesktopWorkState.composerAttachments?.some(x=>x.id===$attachmentJson)||false") -eq $true } '冷启后附件未进入可见 Composer 状态。'
    Wait-For { (Invoke-Cdp $reopened.debugPort "[...document.querySelectorAll('.chat-composer-attachments [data-action=remove-composer-attachment]')].some(x=>x.dataset.id===$attachmentJson)") -eq $true } '冷启后附件未显示为 Composer 卡片。'
    $workClicked = Invoke-Cdp $reopened.debugPort "(()=>{const b=document.querySelector('[data-action=show-work]');if(!b)return false;b.click();return true})()"
    if (-not $workClicked) { throw 'WORK 切换入口缺失。' }
    Wait-For { (Invoke-Cdp $reopened.debugPort "window.__nanfengDesktopWorkState.dataArea") -eq 'WORK' } '未切入 WORK。'
    $workState = Invoke-Cdp $reopened.debugPort "({text:window.__nanfengDesktopWorkState.composerDraft,workspaceId:window.__nanfengDesktopWorkState.current?.summary?.id||null})"
    if (-not $workState.workspaceId -or $workState.text -eq $textValue) { throw '过期草稿进入 WORK。' }
    if ((Invoke-Cdp $reopened.debugPort "window.__nanfengDesktopWorkState.composerAttachments?.some(x=>x.id===$attachmentJson)||false") -eq $true) { throw 'CHAT 附件进入 WORK 可见 Composer。' }
    if ((Invoke-Cdp $reopened.debugPort "[...document.querySelectorAll('.chat-composer-attachments [data-action=remove-composer-attachment]')].some(x=>x.dataset.id===$attachmentJson)") -eq $true) { throw 'CHAT 附件卡片进入 WORK。' }
    $workDraft = Read-Draft $reopened 'WORK' $workState.workspaceId
    if (@($workDraft.attachments).Count -gt 0 -and @($workDraft.attachments | Where-Object {$_.id -eq $attachment.id}).Count -gt 0) { throw 'CHAT 附件进入 WORK。' }
    $back = Invoke-Cdp $reopened.debugPort "(()=>{const b=document.querySelector('[data-action=return-workspace-origin]');if(!b)return false;b.click();return true})()"
    if (-not $back) { throw '返回 CHAT 入口缺失。' }
    Wait-For { (Invoke-Cdp $reopened.debugPort "window.__nanfengDesktopWorkState.dataArea") -eq 'CHAT' } '未返回 CHAT。'
    Wait-For { (Invoke-Cdp $reopened.debugPort "window.__nanfengDesktopWorkState.composerAttachments?.some(x=>x.id===$attachmentJson)||false") -eq $true } '返回 CHAT 后可见 Composer 未恢复附件。'
    Wait-For { (Invoke-Cdp $reopened.debugPort "[...document.querySelectorAll('.chat-composer-attachments [data-action=remove-composer-attachment]')].some(x=>x.dataset.id===$attachmentJson)") -eq $true } '返回 CHAT 后附件卡片未恢复。'
    $returned = Read-Draft $reopened 'CHAT' $workspaceId
    if ($returned.text -ne $expectedText -or @($returned.attachments).Count -ne 1 -or $returned.attachments[0].id -ne $attachment.id) { throw '返回 CHAT 后文字或附件状态改变。' }
    Stop-Acceptance $reopened
    [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;syntheticAgeMinutes=$AgeMinutes;textExpired=($AgeMinutes -ge 60);choicesRetained=$true;attachmentRetained=$true;attachmentChipVisible=$true;uiMatchesNative=$true;workIsolated=$true;returnConsistent=$true;stoppedPids=@($stopped)} | ConvertTo-Json -Depth 4
} finally {
    foreach ($launch in @($running)) {
        try { Stop-Acceptance $launch } catch { Write-Warning '某验收 PID 未能核路径停止。' }
    }
}
