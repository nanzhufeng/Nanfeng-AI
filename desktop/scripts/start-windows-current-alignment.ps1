param([string]$Root='', [ValidateRange(0,65535)][int]$DebugPort=0, [switch]$Hidden, [switch]$PassThru, [ValidateSet('current','release')][string]$PackageProfile='current', [switch]$DevelopmentCandidate)
$ErrorActionPreference='Stop'
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false)
$OutputEncoding=[Console]::OutputEncoding
try {
    $desktop=Split-Path -Parent $PSScriptRoot
    if ($DevelopmentCandidate) {
        $entryOptions=@{DevelopmentDesktop=$desktop;Hidden=[bool]$Hidden;PassThru=$true}
        if ($Root) {$entryOptions.Root=$Root}
        if ($DebugPort) {$entryOptions.DebugPort=$DebugPort}
        & (Join-Path $desktop 'src-tauri/resources/alignment-launcher/Start.ps1') @entryOptions
        if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) {exit $LASTEXITCODE}
        return
    }
    $output=[IO.Path]::GetFullPath((Join-Path $desktop 'output/windows-alignment-acceptance'))
    $pointer=if($PackageProfile -eq 'release'){'current-release.json'}else{'current.json'}
    $receipt=Get-Content -LiteralPath (Join-Path $output $pointer) -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($receipt.installerIdentifier -ne 'com.nanzhufeng.ai.desktop.windowsalignmentinstaller' -or
        $receipt.applicationIdentifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance' -or
        $receipt.sha256 -cnotmatch '^[A-F0-9]{64}$') { throw '当前安装包收据身份无效；旧程序和数据未改动。' }
    $exe=[IO.Path]::GetFullPath([string]$receipt.executable)
    if (!$exe.StartsWith($output+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or
        [IO.Path]::GetFileName($exe) -ine 'nanfeng-ai-desktop-spike.exe' -or
        (Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash -ne $receipt.sha256) {
        throw '当前对齐验收程序与收据不一致，请重新生成对应安装包。'
    }
    if ($receipt.buildProfile -notin @('debug','release') -or ($PackageProfile -eq 'release' -and $receipt.buildProfile -ne 'release')) {
        throw '候选安装包构建类型不匹配；未启动程序。'
    }
    $directory=Split-Path -Parent $exe
    $manifest=Get-Content -LiteralPath (Join-Path $directory 'alignment-launcher/executable.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($manifest.identifier -ne $receipt.applicationIdentifier -or $manifest.sha256 -ne $receipt.sha256 -or
        $manifest.sourceInputSha256 -ne $receipt.applicationSourceInputSha256 -or $manifest.buildProfile -ne $receipt.buildProfile) {
        throw '配套启动入口与程序不一致，已停止启动；原数据保留。'
    }
    # Use the installed entry's existing root-memory owner. The package opts in
    # only after it contains the matching shared entry; old packages remain compatible.
    if ($manifest.entryRootPolicy -eq 'shared-alignment-root-v1') {
        $entryOptions=@{Hidden=[bool]$Hidden;PassThru=$true;UseImmutableRunner=$true;ImmutableOutputRoot=(Join-Path $desktop 'output')}
        if ($Root) {$entryOptions.Root=$Root}
        if ($DebugPort) {$entryOptions.DebugPort=$DebugPort}
        & (Join-Path $directory 'alignment-launcher/Start.ps1') @entryOptions
        if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) {exit $LASTEXITCODE}
        return
    }
    # Repository entry uses the existing immutable runner. Only a registered
    # installation may use RunInPlace; a package directory is not an install.
    $parent=if ($Root) {Split-Path -Parent ([IO.Path]::GetFullPath($Root))} elseif (Test-Path -LiteralPath 'D:\CodexTemp' -PathType Container) {'D:\CodexTemp'} else {[IO.Path]::GetTempPath().TrimEnd('\')}
    $options=@{ExecutablePath=$exe;RootParent=$parent;StartupMode='normal'}
    if ($Root) {$options.Root=$Root}
    if ($DebugPort) {$options.DebugPort=$DebugPort}
    if (!$Hidden) {$options.Visible=$true}
    & (Join-Path $PSScriptRoot 'start-windows-acceptance.ps1') @options
    if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) {exit $LASTEXITCODE}
} catch {Write-Host ('南枫 AI 当前对齐验收版启动失败：'+$_.Exception.Message);exit 1}
