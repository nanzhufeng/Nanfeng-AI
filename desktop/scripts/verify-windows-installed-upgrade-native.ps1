param(
    [string]$RootParent = 'D:\CodexTemp',
    [string]$OldSetup = 'D:\CodexTemp\nfai-old-637A6388-setup.exe',
    [string]$NewSetup = 'D:\CodexTemp\nfai-current-11022EF1-setup.exe',
    [switch]$OrdinarySend,
    [switch]$ReminderLifecycle,
    [switch]$PreserveInstallation,
    [switch]$NormalClose
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
if (-not $IsWindows) { throw '仅支持 Windows 原生验收。' }
$rootParentPath = [IO.Path]::GetFullPath($RootParent).TrimEnd('\')
if ($rootParentPath -ne 'D:\CodexTemp') { throw '安装态升级验收只允许 D:\CodexTemp。' }
$oldSetup = [IO.Path]::GetFullPath($OldSetup)
$newSetup = [IO.Path]::GetFullPath($NewSetup)
$oldHash = '637A63881470F1621018923B76D8D264548D7E7F0EB18DE98B62BD90DA3E0DA2'
if ((Get-FileHash -LiteralPath $oldSetup -Algorithm SHA256).Hash -ne $oldHash) { throw '固定旧安装包哈希不符。' }
$newHash = (Get-FileHash -LiteralPath $newSetup -Algorithm SHA256).Hash
if ($newHash -eq $oldHash) { throw '新安装包与固定旧包相同。' }
$desktopRoot = Split-Path -Parent $PSScriptRoot
$launcher = Join-Path $PSScriptRoot 'start-windows-acceptance.ps1'
$cdp = Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs'
$installDir = Join-Path $rootParentPath ('nfai-installed-acceptance.' + [Guid]::NewGuid().ToString())
if ((Split-Path -Parent $installDir) -ne $rootParentPath -or (Test-Path -LiteralPath $installDir)) { throw '隔离安装目录无效。' }
$installedExe = Join-Path $installDir 'nanfeng-ai-desktop-spike.exe'
$root = ''
$running = $null
$oldExeHash = ''
$newExeHash = ''
$stopped = [System.Collections.Generic.List[int]]::new()
$normalCloseReceipts = [System.Collections.Generic.List[object]]::new()
if ($NormalClose) {
    Add-Type 'using System;using System.Runtime.InteropServices;public static class NFInstalledClose{[DllImport("kernel32.dll")]public static extern bool GetExitCodeProcess(IntPtr h,out uint code);}'
}
$uninstallBase = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall'
function Read-InstallRegistration {
    @(Get-ChildItem -LiteralPath $uninstallBase | ForEach-Object { Get-ItemProperty -LiteralPath $_.PSPath } | Where-Object { $_.DisplayName -eq '南枫 AI Windows 独立验收' })
}
if ((Read-InstallRegistration).Count -ne 0) { throw '隔离身份已有安装，拒绝覆盖。' }
function Assert-InstallerProcessSafety {
    $evidence = Join-Path $rootParentPath ('nfai-installer-safety.' + [Guid]::NewGuid().ToString() + '.json')
    $result = & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'check-windows-acceptance-installer-safety.ps1') -EvidencePath $evidence
    if ($LASTEXITCODE -ne 0) { throw "安装/卸载安全门未放行，保留安装与数据；见 $evidence" }
}
function Invoke-Installer([string]$path) {
    Assert-InstallerProcessSafety
    # NSIS requires /D to be the final argument and this path contains no spaces.
    $process = Start-Process -FilePath $path -ArgumentList "/S /D=$installDir" -WindowStyle Hidden -Wait -PassThru
    if ($process.ExitCode -ne 0) { throw "隔离安装器退出码 $($process.ExitCode)。" }
    if (-not (Test-Path -LiteralPath $installedExe -PathType Leaf)) { throw '隔离安装 EXE 缺失。' }
    $rows = Read-InstallRegistration
    $registeredPath = '<missing>'
    if ($rows.Count -eq 1 -and $rows[0].InstallLocation) {
        $rawLocation = [string]$rows[0].InstallLocation
        if ($rawLocation.StartsWith('"') -and $rawLocation.EndsWith('"')) { $rawLocation = $rawLocation.Substring(1, $rawLocation.Length - 2) }
        $registeredPath = [IO.Path]::GetFullPath($rawLocation).TrimEnd('\')
    }
    if ($rows.Count -ne 1 -or $registeredPath -ne $installDir) { throw "隔离安装登记与目标目录不一致：count=$($rows.Count), registered=$registeredPath, expected=$installDir。" }
}
function New-DebugPort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return [int]$listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
function Invoke-Cdp([int]$port, [string]$expression) {
    $value = $expression | & node --max-old-space-size=2048 $cdp $port 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $value) { throw '隔离 WebView2 未响应。' }
    return ($value | ConvertFrom-Json -NoEnumerate)
}
function Start-Installed([string]$existingRoot = '') {
    $port = New-DebugPort
    $arguments = @{ RootParent=$rootParentPath; StartupMode='normal'; Visible=$true; DebugPort=$port; ExecutablePath=$installedExe; RunInPlace=$true }
    if ($existingRoot) { $arguments.Root = $existingRoot }
    $launch = & $launcher @arguments | ConvertFrom-Json
    $script:running = $launch
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    while ([DateTime]::UtcNow -lt $deadline) {
        $process = Get-Process -Id $launch.pid -ErrorAction SilentlyContinue
        if (-not $process) { throw ('隔离安装进程启动后退出：pid={0}, reopen={1}。' -f $launch.pid, [bool]$existingRoot) }
        if ($process.Path) {
            if ([IO.Path]::GetFullPath($process.Path) -ne $installedExe) { throw '隔离安装进程路径不符。' }
            try { if ((Invoke-Cdp $port 'Boolean(window.__TAURI__?.core?.invoke)') -eq $true) { return $launch } }
            catch { }
        }
        Start-Sleep -Milliseconds 250
    }
    throw '隔离安装进程未就绪。'
}
function Stop-Installed {
    if (-not $script:running) { return }
    $launch = $script:running
    $process = Get-Process -Id $launch.pid -ErrorAction SilentlyContinue
    if ($process) {
        if (-not $process.Path -or [IO.Path]::GetFullPath($process.Path) -ne $installedExe -or (Get-FileHash -LiteralPath $installedExe -Algorithm SHA256).Hash -ne $launch.sha256) { throw '拒绝停止身份不符的进程。' }
        if ($NormalClose) {
            $handle = $process.Handle
            $title = $process.MainWindowTitle
            $responding = $process.Responding
            if ($title -ne '南枫 AI · 隔离测试（非日常版）' -or -not $responding -or -not $process.CloseMainWindow()) { throw '安装态隔离窗口未确认正常关闭。' }
            if (-not $process.WaitForExit(15000)) { throw '安装态正常关闭超时；不强停。' }
            [uint32]$exitCode = 259
            if (-not [NFInstalledClose]::GetExitCodeProcess($handle, [ref]$exitCode) -or $exitCode -ne 0) { throw "安装态正常关闭退出码异常：$exitCode。" }
            if ((Get-Content -LiteralPath (Join-Path $launch.root 'startup.stderr.log') -Raw) -match 'panicked|panic in') { throw '安装态正常关闭出现panic。' }
            $normalCloseReceipts.Add(@{pid=$launch.pid;sha256=$launch.sha256;title=$title;responding=$responding;exitCode=$exitCode})
        } else { Stop-Process -Id $launch.pid -Force }
        $deadline = [DateTime]::UtcNow.AddSeconds(15)
        while ((Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 100 }
        if (Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) { throw '隔离安装进程未停止。' }
    }
    $stopped.Add([int]$launch.pid)
    $script:running = $null
}
function Read-Integrity([string]$businessRoot) {
    $python = 'import pathlib,sqlite3,sys,json; root=pathlib.Path(sys.argv[1]); files=sorted(root.rglob("*.sqlite3")); checks=[(str(p.relative_to(root)),sqlite3.connect("file:"+str(p)+"?mode=ro",uri=True).execute("PRAGMA quick_check").fetchone()[0]) for p in files]; print(json.dumps({"count":len(files),"allOk":all(x[1]=="ok" for x in checks)}))'
    $result = & python -c $python $businessRoot | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0 -or -not $result.allOk) { throw '合成 SQLite 完整性失败。' }
    return $result
}
function Read-BusinessHash([string]$path) {
    $expected = [IO.Path]::GetFullPath((Join-Path $root 'workspace.sqlite3'))
    if (-not $root -or [IO.Path]::GetFullPath($path) -ne $expected -or -not (Test-Path -LiteralPath (Join-Path $root '.nanfeng-windows-acceptance.json') -PathType Leaf)) { throw '只允许读取当前标记合成根的业务库哈希。' }
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        try { return (Get-FileHash -LiteralPath $path -Algorithm SHA256 -ErrorAction Stop).Hash }
        catch {
            if ($attempt -eq 19) { throw }
            Start-Sleep -Milliseconds 250
        }
    }
}
try {
    Invoke-Installer $oldSetup
    $oldExeHash = (Get-FileHash -LiteralPath $installedExe -Algorithm SHA256).Hash
    $old = Start-Installed
    $root = [IO.Path]::GetFullPath([string]$old.root)
    if ((Split-Path -Parent $root) -ne $rootParentPath -or -not (Test-Path -LiteralPath (Join-Path $root '.nanfeng-windows-acceptance.json') -PathType Leaf)) { throw '合成业务根未标记。' }
    $nonce = [Guid]::NewGuid().ToString('N')
    $key = 'nfai-synthetic-upgrade-' + $nonce
    $intentJson = ('installed-upgrade-' + $nonce) | ConvertTo-Json -Compress
    $keyJson = $key | ConvertTo-Json -Compress
    $seed = Invoke-Cdp $old.debugPort "(async()=>{const h={headers:{'x-nanfeng-data-area':'CHAT'}};const i=(n,a={})=>window.__TAURI__.core.invoke(n,a,h);const w=await i('ensure_desktop_local_workspace');const p=await i('mutate_desktop_domain',{args:{intentId:$intentJson,workspaceId:w.summary.id,entity:'project',action:'create',objectId:null,expectedRevision:null,fields:{title:'旧安装合成项目',description:'升级保数据'}}});const s=(await i('read_desktop_model_service_settings')).find(x=>x.providerId==='QWEN');const saved=await i('save_desktop_model_service_settings',{args:{providerId:'QWEN',enabled:true,presetId:'QWEN_3_7_PLUS',expectedRevision:s.revision,apiKey:$keyJson}});return {workspaceId:w.summary.id,projectId:p.objectId,revision:saved.revision,credentialStored:saved.credentialStored}})()"
    if (-not $seed.projectId -or -not $seed.credentialStored) { throw '旧安装写入合成业务事实失败。' }
    Stop-Installed
    $businessDb = Join-Path $root 'workspace.sqlite3'
    $beforeInstallHash = Read-BusinessHash $businessDb
    Invoke-Installer $newSetup
    $newExeHash = (Get-FileHash -LiteralPath $installedExe -Algorithm SHA256).Hash
    if ($newExeHash -eq $oldExeHash) { throw '升级后安装 EXE 未更新。' }
    $afterInstallHash = Read-BusinessHash $businessDb
    if ($afterInstallHash -ne $beforeInstallHash) { throw '安装器修改了隔离业务库。' }
    $new = Start-Installed $root
    $workspaceIdJson = $seed.workspaceId | ConvertTo-Json -Compress
    $projectIdJson = $seed.projectId | ConvertTo-Json -Compress
    $readback = Invoke-Cdp $new.debugPort "(async()=>{const h={headers:{'x-nanfeng-data-area':'CHAT'}};const i=(n,a={})=>window.__TAURI__.core.invoke(n,a,h);const w=await i('read_desktop_workspace',{workspaceId:$workspaceIdJson});const project=w.exchange.projects.find(x=>x.id===$projectIdJson);const checks=[];for(const area of ['CHAT','WORK']){const a={headers:{'x-nanfeng-data-area':area}};const s=(await window.__TAURI__.core.invoke('read_desktop_model_service_settings',{},a)).find(x=>x.providerId==='QWEN');const k=await window.__TAURI__.core.invoke('reveal_desktop_model_service_credential',{providerId:'QWEN'},a);checks.push({area,revision:s.revision,presetId:s.presetId,stored:s.credentialStored,keyMatches:k===$keyJson})}return {projectFound:!!project,projectTitle:project?.title||null,checks}})()"
    if (-not $readback.projectFound -or $readback.projectTitle -ne '旧安装合成项目' -or $readback.checks.Count -ne 2) { throw '升级后项目回读失败。' }
    foreach ($check in $readback.checks) { if ($check.revision -ne $seed.revision -or $check.presetId -ne 'QWEN_3_7_PLUS' -or -not $check.stored -or -not $check.keyMatches) { throw '升级后两区配置与密钥配对失败。' } }
    Stop-Installed
    $ordinary = $null
    if ($OrdinarySend) {
        $ordinaryOutput = & node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'verify-windows-ordinary-send-visible.mjs') $rootParentPath "--installed-exe=$installedExe" "--installed-sha256=$newExeHash"
        if ($LASTEXITCODE -ne 0) { throw '安装态普通发送主链失败。' }
        $ordinary = $ordinaryOutput | ConvertFrom-Json
        $ordinaryRoot = [IO.Path]::GetFullPath([string]$ordinary.root)
        if ((Split-Path -Parent $ordinaryRoot) -ne $rootParentPath -or
            -not (Test-Path -LiteralPath (Join-Path $ordinaryRoot '.nanfeng-windows-acceptance.json') -PathType Leaf) -or
            $ordinary.ordinaryRequestCount -ne 1 -or -not $ordinary.cancelledId -or -not $ordinary.failedId) { throw '安装态发送回执或合成根无效。' }
        foreach ($pidToCheck in @($ordinary.pids)) {
            if (Get-Process -Id ([int]$pidToCheck) -ErrorAction SilentlyContinue) { throw '安装态普通发送验收进程仍在运行。' }
        }
        $ordinary | Add-Member -NotePropertyName sqlite -NotePropertyValue (Read-Integrity $ordinaryRoot)
    }
    $reminder = $null
    if ($ReminderLifecycle) {
        $reminderOutput = & node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'verify-windows-reminder-failed-usage-native.mjs') $rootParentPath --lifecycle --notifications "--installed-exe=$installedExe" "--installed-sha256=$newExeHash"
        if ($LASTEXITCODE -ne 0) { throw '安装态提醒生命周期验收失败。' }
        $reminder = $reminderOutput[-1] | ConvertFrom-Json
        $reminder | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $reminder.root 'installed-reminder-verification.json') -Encoding utf8
        if (-not $reminder.coldReadback -or -not $reminder.notificationsMode) { throw '安装态提醒回执不完整。' }
        $toastOutput = & powershell -NoProfile -File (Join-Path $PSScriptRoot 'read-windows-acceptance-toast-history.ps1') -Root $reminder.root
        if ($LASTEXITCODE -ne 0) { throw '安装态通知发送/系统历史未通过。' }
        $reminder | Add-Member -NotePropertyName notification -NotePropertyValue ($toastOutput | ConvertFrom-Json)

    }
    $integrity = Read-Integrity $root
    $beforeUninstallHash = Read-BusinessHash $businessDb
    [pscustomobject]@{oldSetupSha256=$oldHash;newSetupSha256=$newHash;oldExeSha256=$oldExeHash;newExeSha256=$newExeHash;installDir=$installDir;root=$root;workspaceId=$seed.workspaceId;projectId=$seed.projectId;chatWorkPairs='passed';sqlite=$integrity;installedOrdinarySend=$ordinary;installedReminder=$reminder;stoppedPids=@($stopped);normalCloseReceipts=$normalCloseReceipts.ToArray();installationPreserved=[bool]$PreserveInstallation;businessHashBeforeUninstall=$beforeUninstallHash} | ConvertTo-Json -Depth 6
} finally {
    Stop-Installed
    if ($PreserveInstallation -and (Test-Path -LiteralPath $installDir -PathType Container)) {
        [pscustomobject]@{installDir=$installDir;root=$root;oldSetupSha256=$oldHash;newSetupSha256=$newHash;oldExeSha256=$oldExeHash;newExeSha256=$newExeHash;stoppedPids=@($stopped);normalCloseReceipts=$normalCloseReceipts.ToArray();installationPreserved=$true;full13Complete=$false} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $installDir ('upgrade-acceptance.'+[Guid]::NewGuid().ToString()+'.json')) -Encoding utf8
    }
    $uninstaller = Join-Path $installDir 'uninstall.exe'
    if (-not $PreserveInstallation -and (Split-Path -Parent $uninstaller) -eq $installDir -and (Test-Path -LiteralPath $uninstaller -PathType Leaf)) {
        Assert-InstallerProcessSafety
        $process = Start-Process -FilePath $uninstaller -ArgumentList '/S' -WindowStyle Hidden -Wait -PassThru
        if ($process.ExitCode -ne 0) { throw "隔离卸载器退出码 $($process.ExitCode)。" }
    }
    if (-not $PreserveInstallation -and (Read-InstallRegistration).Count -ne 0) { throw '隔离身份卸载项仍在。' }
    if (-not $PreserveInstallation -and (Test-Path -LiteralPath $installDir)) { throw '隔离安装目录仍在。' }
    if ($root -and (Test-Path -LiteralPath (Join-Path $root '.nanfeng-windows-acceptance.json') -PathType Leaf)) {
        if ($beforeUninstallHash -and (Read-BusinessHash (Join-Path $root 'workspace.sqlite3')) -ne $beforeUninstallHash) { throw '卸载器修改了合成业务库。' }
    }
}
