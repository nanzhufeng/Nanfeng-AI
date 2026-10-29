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
    if ($typed -ne $textValue) { throw '未能在真实输入框输入合成文字。' }
    $workspaceId = Invoke-Cdp $first.debugPort "window.__nanfengDesktopWorkState.current?.summary?.id||null"
    if (-not $workspaceId) { throw 'CHAT 工作区未建立。' }
    Wait-For { (Read-Draft $first 'CHAT' $workspaceId).text -eq $textValue } 'CHAT 未持久保存新对话文字。'
    $clicked = Invoke-Cdp $first.debugPort "(()=>{const b=document.querySelector('[data-action=new-chat]');if(!b)return false;b.click();return true})()"
    if (-not $clicked) { throw '真实新对话入口缺失。' }
    Wait-For { (Invoke-Cdp $first.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $textValue } '点击新对话后草稿未恢复。'
    $createArgs = @{intentId=('synthetic-chat-create-' + [Guid]::NewGuid().ToString('N'));workspaceId=$workspaceId;entity='conversation';action='create';fields=@{title='合成已有会话';projectId=$null;firstMessage='合成已发送消息'}} | ConvertTo-Json -Compress -Depth 4
    $chatHeader = @{headers=@{'x-nanfeng-data-area'='CHAT'}} | ConvertTo-Json -Compress
    $created = Invoke-Cdp $first.debugPort "window.__TAURI__.core.invoke('mutate_desktop_domain',{args:$createArgs},$chatHeader)"
    $existingId = [string]$created.objectId
    if (-not $existingId) { throw '未能建立合成已有会话。' }
    Stop-Acceptance $first

    $reopened = Start-Acceptance
    Wait-For { (Invoke-Cdp $reopened.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $textValue } '同根冷启未恢复新对话文字。'
    if ((Read-Draft $reopened 'CHAT' $workspaceId).text -ne $textValue) { throw 'CHAT 原生草稿与界面不一致。' }
    $existingJson = $existingId | ConvertTo-Json -Compress
    $existingClicked = Invoke-Cdp $reopened.debugPort "(()=>{const id=$existingJson;const b=[...document.querySelectorAll('[data-action=select-chat]')].find(x=>x.dataset.id===id);if(!b)return false;b.click();return true})()"
    if (-not $existingClicked) { throw '合成已有会话未出现在真实侧栏。' }
    Wait-For { (Invoke-Cdp $reopened.debugPort "window.__nanfengDesktopWorkState.selectedConversationId") -eq $existingId } '未选中合成已有会话。'
    Wait-For { (Invoke-Cdp $reopened.debugPort "document.querySelector('#chat-composer')?.value||''") -ne $textValue } '已有会话错误显示新对话草稿。'
    $newAgain = Invoke-Cdp $reopened.debugPort "(()=>{const b=document.querySelector('[data-action=new-chat]');if(!b)return false;b.click();return true})()"
    if (-not $newAgain) { throw '已有会话页的新对话入口缺失。' }
    Wait-For { (Invoke-Cdp $reopened.debugPort "({id:window.__nanfengDesktopWorkState.selectedConversationId,text:document.querySelector('#chat-composer')?.value||''})").text -eq $textValue } '从已有会话返回新对话未恢复草稿。'
    if ((Read-Draft $reopened 'CHAT' $workspaceId).text -ne $textValue) { throw '已有会话往返后 CHAT 原生草稿改变。' }
    $workClicked = Invoke-Cdp $reopened.debugPort "(()=>{const b=document.querySelector('[data-action=show-work]');if(!b)return false;b.click();return true})()"
    if (-not $workClicked) { throw '工作区切换入口缺失。' }
    Wait-For { (Invoke-Cdp $reopened.debugPort "window.__nanfengDesktopWorkState.dataArea") -eq 'WORK' } '未切入 WORK。'
    $workState = Invoke-Cdp $reopened.debugPort "({text:window.__nanfengDesktopWorkState.composerDraft,uiText:document.querySelector('#chat-composer')?.value||'',workspaceId:window.__nanfengDesktopWorkState.current?.summary?.id||null})"
    if ($workState.text -eq $textValue -or $workState.uiText -eq $textValue -or -not $workState.workspaceId) { throw 'WORK 草稿与 CHAT 隔离失败。' }
    if ((Read-Draft $reopened 'WORK' $workState.workspaceId).text -eq $textValue) { throw 'CHAT 文字进入 WORK 原生草稿。' }
    $backClicked = Invoke-Cdp $reopened.debugPort "(()=>{const b=document.querySelector('[data-action=return-workspace-origin]');if(!b)return false;b.click();return true})()"
    if (-not $backClicked) { throw '返回 CHAT 入口缺失。' }
    Wait-For { (Invoke-Cdp $reopened.debugPort "window.__nanfengDesktopWorkState.dataArea") -eq 'CHAT' } '未返回 CHAT。'
    Wait-For { (Invoke-Cdp $reopened.debugPort "document.querySelector('#chat-composer')?.value||''") -eq $textValue } '返回 CHAT 后草稿未恢复。'
    if ((Read-Draft $reopened 'CHAT' $workspaceId).text -ne $textValue) { throw '返回 CHAT 后原生草稿改变。' }
    Stop-Acceptance $reopened
    [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;uiInput='passed';newChatClick='passed';sameRootRestart='passed';existingConversationRoundTrip='passed';workIsolation='passed';returnToChat='passed';stoppedPids=@($stopped)} | ConvertTo-Json -Depth 4
} finally {
    foreach ($launch in @($running)) {
        try { Stop-Acceptance $launch } catch { Write-Warning '某个验收 PID 未能确认停止；请按精确运行副本路径人工核对。' }
    }
}
