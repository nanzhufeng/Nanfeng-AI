param([string]$RootParent = 'D:\CodexTemp', [switch]$Normal, [ValidateSet('CHAT', 'WORK')][string]$Area = 'CHAT')
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if (-not $IsWindows) { throw '仅支持 Windows 原生隔离验收。' }
$parent = [IO.Path]::GetFullPath($RootParent).TrimEnd('\')
if ($parent -ne 'D:\CodexTemp') { throw '仅允许 D:\CodexTemp 下的标记合成根。' }
$desktopRoot = Split-Path -Parent $PSScriptRoot
$receipt = Get-Content -LiteralPath (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$sourceExe = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance/{0}/NanfengAI-Windows-Acceptance.exe" -f $receipt.sha256)))
$expectedExe = [IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance-binaries/{0}/nanfeng-ai-desktop-spike.exe" -f $receipt.sha256)))
if ($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance' -or $receipt.sha256 -notmatch '^[A-F0-9]{64}$' -or [IO.Path]::GetFullPath([string]$receipt.executable) -ne $sourceExe -or (Get-FileHash -LiteralPath $sourceExe -Algorithm SHA256).Hash -ne $receipt.sha256) { throw '候选收据和源 EXE 不匹配。' }
$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$listener.Start()
try { $port = [int]$listener.LocalEndpoint.Port } finally { $listener.Stop() }
$launch = $null
try {
    $launch = & (Join-Path $PSScriptRoot 'start-windows-acceptance.ps1') -RootParent $parent -StartupMode normal -Visible:$true -DebugPort $port | ConvertFrom-Json
    $root = [IO.Path]::GetFullPath([string]$launch.root)
    if (-not $root.StartsWith($parent + '\', [StringComparison]::OrdinalIgnoreCase) -or -not (Test-Path -LiteralPath (Join-Path $root '.nanfeng-windows-acceptance.json') -PathType Leaf) -or $launch.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath([string]$launch.executable) -ne $expectedExe) { throw '运行身份或合成根不符。' }
    $cdpHelper = Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs'
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    $ready = $false
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $text = 'Boolean(window.__TAURI__?.core?.invoke)' | & node --max-old-space-size=2048 $cdpHelper $port 2>$null
            if ($LASTEXITCODE -eq 0 -and $text -eq 'true') { $ready = $true; break }
        } catch { }
        Start-Sleep -Milliseconds 250
    }
    if (-not $ready) { throw '隔离 WebView2 IPC 未就绪。' }
    $areaRoot = if ($Area -eq 'WORK') { Join-Path $root 'work-area' } else { $root }
    if (-not (Test-Path -LiteralPath $areaRoot -PathType Container)) { throw '所选数据区域根不存在。' }
    if ($Normal) {
        $assetRoot = Join-Path $areaRoot 'assets'
        if (-not (Test-Path -LiteralPath $assetRoot -PathType Container)) { throw '正常资产根不存在。' }
        $assetName = 'synthetic-backup-normal-' + [Guid]::NewGuid().ToString('N') + '.txt'
        [IO.File]::WriteAllText((Join-Path $assetRoot $assetName), 'SYNTHETIC-NORMAL-BACKUP-FIXTURE', [Text.Encoding]::UTF8)
    } else {
        $junction = Join-Path $areaRoot 'p6k-zip-import-assets'
        $outside = Join-Path $areaRoot 'outside-backup-package-fixture'
        if (Test-Path -LiteralPath $junction) { throw '受控 ZIP 资产目录已存在，拒绝修改。' }
        New-Item -ItemType Directory -Path $outside -ErrorAction Stop | Out-Null
        $fixture = Join-Path $outside 'outside.txt'
        [IO.File]::WriteAllText($fixture, 'SYNTHETIC-OUTSIDE-BACKUP-FIXTURE', [Text.Encoding]::UTF8)
        New-Item -ItemType Junction -Path $junction -Target $outside -ErrorAction Stop | Out-Null
    }
    $backupPrefix = if ($Normal) { 'nfai-normal-backup.' } else { 'nfai-junction-rejected.' }
    $backup = Join-Path $parent ($backupPrefix + [Guid]::NewGuid().ToString('N') + '.nfai-backup')
    $backupJson = $backup | ConvertTo-Json -Compress
    $areaJson = $Area | ConvertTo-Json -Compress
    if ($Normal) {
        $intentJson = ('synthetic-backup-project-' + [Guid]::NewGuid().ToString('N')) | ConvertTo-Json -Compress
        $expression = "(async()=>{const h={headers:{'x-nanfeng-data-area':$areaJson}};const call=(name,args={})=>window.__TAURI__.core.invoke(name,args,h);const w=await call('ensure_desktop_local_workspace');const made=await call('mutate_desktop_domain',{args:{intentId:$intentJson,workspaceId:w.summary.id,entity:'project',action:'create',fields:{title:'合成正常备份项目',description:'新候选正常导出'}}});const artifact=await call('export_desktop_local_backup',{selectedPath:$backupJson});const preflight=await call('preflight_desktop_local_backup',{selectedPath:$backupJson});return {workspaceId:w.summary.id,projectId:made.objectId,artifact,preflight}})()"
    } else {
        $expression = "(async()=>{try{await window.__TAURI__.core.invoke('export_desktop_local_backup',{selectedPath:$backupJson},{headers:{'x-nanfeng-data-area':$areaJson}});return {rejected:false}}catch(error){return {rejected:true,error:String(error)}}})()"
    }
    $text = $expression | & node --max-old-space-size=2048 $cdpHelper $port
    if ($LASTEXITCODE -ne 0 -or -not $text) { throw '隔离 WebView2 备份 IPC 失败。' }
    $result = $text | ConvertFrom-Json
    if ($Normal) {
        if (-not (Test-Path -LiteralPath $backup -PathType Leaf) -or -not $result.artifact.sha256 -or -not $result.preflight.fingerprint -or -not $result.projectId) { throw '正常有内容备份导出或预检失败。' }
        $entry = & python -c 'import sys,zipfile; z=zipfile.ZipFile(sys.argv[1]); expected="assets/attachments/v1/"+sys.argv[2]; print(expected if expected in z.namelist() else "MISSING")' $backup $assetName
        if ($LASTEXITCODE -ne 0 -or $entry -eq 'MISSING') { throw '正常资产未进入备份包。' }
        [pscustomobject]@{ candidateSha256 = $receipt.sha256; root = $root; area = $Area; workspaceId = $result.workspaceId; projectId = $result.projectId; backup = $backup; assetEntry = $entry; preflight = $result.preflight.fingerprint; pid = [int]$launch.pid } | ConvertTo-Json -Compress
    } else {
        if (-not $result.rejected -or $result.error -notmatch '受控资产根包含链接' -or (Test-Path -LiteralPath $backup) -or [IO.File]::ReadAllText($fixture) -ne 'SYNTHETIC-OUTSIDE-BACKUP-FIXTURE') { throw '备份根链接没有安全拒绝，或合成外部文件发生变化。' }
        [pscustomobject]@{ candidateSha256 = $receipt.sha256; root = $root; area = $Area; rejected = $result.rejected; error = $result.error; backupAbsent = $true; outsideFixtureRetained = $true; pid = [int]$launch.pid } | ConvertTo-Json -Compress
    }
} finally {
    if ($launch) {
        $process = Get-Process -Id $launch.pid -ErrorAction SilentlyContinue
        if ($process) {
            if ([IO.Path]::GetFullPath([string]$process.Path) -ne $expectedExe -or (Get-FileHash -LiteralPath $expectedExe -Algorithm SHA256).Hash -ne $receipt.sha256) { throw 'PID 路径或哈希不符，拒绝停止。' }
            Stop-Process -Id $launch.pid -Force -ErrorAction Stop
            $deadline = [DateTime]::UtcNow.AddSeconds(15)
            while ((Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
            if (Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) { throw '受控 PID 未退出。' }
        }
    }
}
