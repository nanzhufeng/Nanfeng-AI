param(
    [string]$Root = '',
    [ValidateSet('blank','c02-c06','c07-c12','c13','c14','c15','c16','p6-v2-picker')]
    [string]$Fixture = 'blank',
    [ValidateSet('diagnostic','normal')][string]$StartupMode = 'diagnostic',
    [string]$C16State = '',
    [ValidateRange(0,65535)][int]$DebugPort = 0,
    [string]$ExecutablePath = '',
    [switch]$RunInPlace,
    [string]$OutputRoot = '',
    [string]$RootParent = '',
    [switch]$StorageLocation,
    [string]$ModelServiceTestEndpoint = '',
    [string]$OrdinaryChatTestEndpoint = '',
    [string]$ReminderActivationUrl = '',
    [string]$OcrTestEndpoint = '',
    [string]$AccountSyncTestEndpoint = '',
    [ValidateRange(0,5000)][int]$AccountCallbackDelayMs = 0,
    [ValidateSet('', 'before-sqlite', 'after-sqlite')][string]$ModelPendingPause = '',
    [switch]$Schema45PairPause,
    [switch]$DraftReadPause,
    [ValidateSet('', 'CHAT', 'WORK')][string]$BackupSwitchPause = '',
    [ValidateSet('', 'CHAT', 'WORK')][string]$BackupRollbackPause = '',
    [ValidateSet('', 'CHAT', 'WORK')][string]$BackupRollbackCopyAbort = '',
    [ValidateSet('', 'CHAT', 'WORK')][string]$BackupRollbackPublishPause = '',
    [ValidateSet('', 'CHAT', 'WORK')][string]$BackupRollbackLedgerPublishPause = '',
    [ValidateSet('', 'CHAT', 'WORK')][string]$BackupRollbackAssetPublishPause = '',
    [ValidateSet('', 'CHAT', 'WORK')][string]$BackupRollbackAssetClearPause = '',
    [ValidateSet('', 'CHAT', 'WORK')][string]$BackupRollbackSecondAssetClearPause = '',
    [ValidateSet('', 'CHAT', 'WORK')][string]$BackupRollbackSecondAssetPublishPause = '',
    [switch]$OrdinaryChatCredentialPause,
    [switch]$FakeCamera,
    [switch]$Visible,
    [switch]$ExternalProfileForFullDelete,
    [ValidateSet('', 'candidate-published', 'after-commit')][string]$FullDeletePause = ''
)
$ErrorActionPreference = 'Stop'
if ($ReminderActivationUrl -and ($StartupMode -ne 'normal' -or !$Visible -or $Fixture -ne 'blank' -or $ReminderActivationUrl -cnotmatch '^nanfeng-ai-windows-acceptance-reminder://open/(CHAT|WORK)/[A-Za-z0-9._-]{1,200}$')) {
    throw '提醒激活验收只允许可见隔离普通启动和严格验收协议地址。'
}

if ($StartupMode -eq 'normal' -and $Fixture -ne 'blank') { throw '隔离普通启动不允许业务夹具；只能打开新空根或重开已有合成根。' }
if ($StorageLocation -and ($StartupMode -ne 'normal' -or $Fixture -ne 'blank')) { throw '数据路径验收只允许空夹具的隔离普通启动。' }
if ($ModelPendingPause -and $StartupMode -ne 'normal') { throw '模型待决故障点只允许隔离正常启动。' }
if ($Schema45PairPause -and $StartupMode -ne 'normal') { throw 'Schema45 双区停点只允许隔离正常启动。' }
if ($DraftReadPause -and $StartupMode -ne 'normal') { throw '草稿读取停点只允许隔离正常启动。' }
if ($BackupSwitchPause -and $StartupMode -ne 'normal') { throw '备份切换故障点只允许隔离正常启动。' }
if ($BackupRollbackPause -and $StartupMode -ne 'normal') { throw '备份回滚故障点只允许隔离正常启动。' }
if ($BackupRollbackCopyAbort -and $StartupMode -ne 'normal') { throw '备份回滚复制故障点只允许隔离正常启动。' }
if ($BackupRollbackPublishPause -and $StartupMode -ne 'normal') { throw '备份回滚发布停点只允许隔离正常启动。' }
if ($BackupRollbackLedgerPublishPause -and $StartupMode -ne 'normal') { throw '备份回滚账本发布停点只允许隔离正常启动。' }
if ($BackupRollbackAssetPublishPause -and $StartupMode -ne 'normal') { throw '备份回滚资产发布停点只允许隔离正常启动。' }
if ($BackupRollbackAssetClearPause -and $StartupMode -ne 'normal') { throw '备份回滚资产清理停点只允许隔离正常启动。' }
if ($BackupRollbackSecondAssetClearPause -and $StartupMode -ne 'normal') { throw '备份回滚第二资产移出停点只允许隔离正常启动。' }
if ($BackupRollbackSecondAssetPublishPause -and $StartupMode -ne 'normal') { throw '备份回滚第二资产发布停点只允许隔离正常启动。' }
if ($OrdinaryChatCredentialPause -and $StartupMode -ne 'normal') { throw '普通聊天凭据停点只允许隔离正常启动。' }
if ($FakeCamera -and ($StartupMode -ne 'normal' -or -not $DebugPort)) { throw '合成相机只允许带调试端口的隔离正常启动。' }
$desktopRoot = Split-Path -Parent $PSScriptRoot
$buildOutputRoot = if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    Join-Path $desktopRoot 'output'
} else {
    [IO.Path]::GetFullPath($OutputRoot)
}
$receiptMediaTools = ''
if ($ExecutablePath) {
    $sourceExecutable = [IO.Path]::GetFullPath($ExecutablePath)
} else {
    $receiptPath = Join-Path $buildOutputRoot 'windows-acceptance/current.json'
    if (-not (Test-Path -LiteralPath $receiptPath -PathType Leaf)) { throw '先运行 npm run build:windows:acceptance。' }
    $receipt = [IO.File]::ReadAllText($receiptPath) | ConvertFrom-Json
    if ($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance' -or $receipt.sha256 -notmatch '^[A-F0-9]{64}$') {
        throw 'Windows 独立验收构建收据无效。'
    }
    $receiptDirectory = Join-Path (Join-Path $buildOutputRoot 'windows-acceptance') $receipt.sha256
    $expectedExecutable = Join-Path $receiptDirectory 'NanfengAI-Windows-Acceptance.exe'
    $expectedMediaTools = Join-Path $receiptDirectory 'media-tools/windows-x86_64'
    if ([IO.Path]::GetFullPath([string]$receipt.executable) -ne [IO.Path]::GetFullPath($expectedExecutable) -or
        [IO.Path]::GetFullPath([string]$receipt.mediaTools) -ne [IO.Path]::GetFullPath($expectedMediaTools)) {
        throw 'Windows 独立验收构建收据路径无效。'
    }
    $sourceExecutable = $expectedExecutable
    $receiptMediaTools = $expectedMediaTools
    if ((Get-FileHash -LiteralPath $sourceExecutable -Algorithm SHA256).Hash -ne $receipt.sha256) {
        throw 'Windows 独立验收构建与收据不一致。'
    }
}
if (-not (Test-Path -LiteralPath $sourceExecutable -PathType Leaf)) { throw '先运行 npm run build:windows:acceptance。' }
if ($RunInPlace) {
    $sourceDirectory=Split-Path -Parent $sourceExecutable
    $legacyInstall=$ExecutablePath -and (Split-Path -Leaf $sourceDirectory) -match '^nfai-installed-acceptance\.[a-fA-F0-9-]{8,64}$' -and (Split-Path -Parent $sourceDirectory) -eq 'D:\CodexTemp'
    $registeredAlignment=$false
    $alignmentKey='HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\南枫 AI Windows 对齐验收'
    $alignmentManifest=Join-Path $sourceDirectory 'alignment-launcher\executable.json'
    if ($ExecutablePath -and (Test-Path -LiteralPath $alignmentKey) -and (Test-Path -LiteralPath $alignmentManifest -PathType Leaf)) {
        $registration=Get-ItemProperty -LiteralPath $alignmentKey
        $registeredPath=[IO.Path]::GetFullPath(([string]$registration.InstallLocation).Trim('"')).TrimEnd('\')
        $owned=Get-Content -LiteralPath $alignmentManifest -Raw | ConvertFrom-Json
        $registeredAlignment=$registration.DisplayName -eq '南枫 AI Windows 对齐验收' -and $registration.MainBinaryName -eq 'nanfeng-ai-desktop-spike.exe' -and $registeredPath -eq $sourceDirectory -and $owned.identifier -eq 'com.nanzhufeng.ai.desktop.windowsacceptance' -and $owned.sha256 -cmatch '^[A-F0-9]{64}$' -and (Get-FileHash -LiteralPath $sourceExecutable -Algorithm SHA256).Hash -eq $owned.sha256
        if ((Get-Item -LiteralPath $alignmentManifest -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) {$registeredAlignment=$false}
    }
    if ((-not $legacyInstall -and -not $registeredAlignment) -or (Split-Path -Leaf $sourceExecutable) -ne 'nanfeng-ai-desktop-spike.exe' -or ((Get-Item -LiteralPath $sourceExecutable -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw '原位启动只允许已核验的独立验收安装目录；登记、文件名和程序哈希必须匹配。'
    }
}
if ($DebugPort -gt 0 -and $DebugPort -lt 1024) { throw '调试端口须为 1024–65535。' }
if (-not [string]::IsNullOrWhiteSpace($ModelServiceTestEndpoint)) {
    if ($StartupMode -ne 'normal') { throw '模型连接测试端点只允许隔离正常启动。' }
    $testUri = $null
    if (-not [Uri]::TryCreate($ModelServiceTestEndpoint, [UriKind]::Absolute, [ref]$testUri) -or
        $testUri.Scheme -ne 'http' -or
        $testUri.Host -notin @('127.0.0.1', 'localhost') -or
        $testUri.IsDefaultPort -or
        $testUri.AbsolutePath -ne '/' -or
        -not [string]::IsNullOrEmpty($testUri.Query) -or
        -not [string]::IsNullOrEmpty($testUri.Fragment) -or
        -not [string]::IsNullOrEmpty($testUri.UserInfo)) {
        throw '模型连接测试端点必须是带显式端口且不含路径、认证、查询或片段的本机 HTTP 回环地址。'
    }
}
if (-not [string]::IsNullOrWhiteSpace($OrdinaryChatTestEndpoint)) {
    if ($StartupMode -ne 'normal') { throw '普通聊天测试端点只允许隔离正常启动。' }
    $ordinaryChatUri = $null
    if (-not [Uri]::TryCreate($OrdinaryChatTestEndpoint, [UriKind]::Absolute, [ref]$ordinaryChatUri) -or
        $ordinaryChatUri.Scheme -ne 'http' -or
        $ordinaryChatUri.Host -notin @('127.0.0.1', 'localhost') -or
        $ordinaryChatUri.IsDefaultPort -or
        $ordinaryChatUri.AbsolutePath -ne '/' -or
        -not [string]::IsNullOrEmpty($ordinaryChatUri.Query) -or
        -not [string]::IsNullOrEmpty($ordinaryChatUri.Fragment) -or
        -not [string]::IsNullOrEmpty($ordinaryChatUri.UserInfo)) {
        throw '普通聊天测试端点必须是带显式端口且不含路径、认证、查询或片段的本机 HTTP 回环地址。'
    }
}
if (-not [string]::IsNullOrWhiteSpace($AccountSyncTestEndpoint)) {
    if ($StartupMode -ne 'normal') { throw '账号同步测试端点只允许隔离正常启动。' }
    $accountSyncUri = $null
    if (-not [Uri]::TryCreate($AccountSyncTestEndpoint, [UriKind]::Absolute, [ref]$accountSyncUri) -or
        $accountSyncUri.Scheme -ne 'http' -or
        $accountSyncUri.Host -notin @('127.0.0.1', 'localhost') -or
        $accountSyncUri.IsDefaultPort -or
        $accountSyncUri.AbsolutePath -ne '/' -or
        -not [string]::IsNullOrEmpty($accountSyncUri.Query) -or
        -not [string]::IsNullOrEmpty($accountSyncUri.Fragment) -or
        -not [string]::IsNullOrEmpty($accountSyncUri.UserInfo)) {
        throw '账号同步测试端点必须是带显式端口且不含路径、认证、查询或片段的本机 HTTP 回环地址。'
    }
}
if ($Fixture -eq 'c16') {
    if ($C16State -notmatch '^(system-light|system-dark|light|dark)__(small|standard|large)__(model-root|model-daily|model-deep|add-root|style|search-history|settings-theme)$') { throw 'C16 必须指定 84 项核心矩阵中的一个合法状态。' }
} elseif ($C16State) { throw '只有 C16 夹具可指定主题矩阵状态。' }
$temporaryParent = if ([string]::IsNullOrWhiteSpace($RootParent)) {
    [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')
} else {
    [IO.Path]::GetFullPath($RootParent).TrimEnd('\')
}
New-Item -ItemType Directory -Path $temporaryParent -Force -ErrorAction Stop | Out-Null
$temporaryParentItem = Get-Item -LiteralPath $temporaryParent -Force
if (-not $temporaryParentItem.PSIsContainer -or ($temporaryParentItem.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
    throw '验收根父目录必须是受控普通目录。'
}
$identifier = 'com.nanzhufeng.ai.desktop.windowsacceptance'
if ([string]::IsNullOrWhiteSpace($Root)) {
    $runId = [Guid]::NewGuid().ToString()
    $Root = Join-Path $temporaryParent ('nanfeng-ai-windows-acceptance.' + $runId)
    New-Item -ItemType Directory -Path $Root -ErrorAction Stop | Out-Null
    $marker = @{ schemaVersion=1; identifier=$identifier; runId=$runId } | ConvertTo-Json -Compress
    [IO.File]::WriteAllText((Join-Path $Root '.nanfeng-windows-acceptance.json'), $marker, [Text.UTF8Encoding]::new($false))
} else {
    $Root = [IO.Path]::GetFullPath($Root)
    if ((Split-Path -Parent $Root) -ne $temporaryParent -or (Split-Path -Leaf $Root) -notmatch '^nanfeng-ai-windows-acceptance\.[a-fA-F0-9-]{8,64}$') {
        throw '只允许重开当前用户临时目录中的独立验收根。'
    }
    if (-not (Test-Path -LiteralPath (Join-Path $Root '.nanfeng-windows-acceptance.json') -PathType Leaf)) { throw '独立验收标记缺失。' }
}
$rootItem = Get-Item -LiteralPath $Root -Force
$markerPath = Join-Path $Root '.nanfeng-windows-acceptance.json'
$markerItem = Get-Item -LiteralPath $markerPath -Force
if (-not $rootItem.PSIsContainer -or ($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -or ($markerItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -or $markerItem.Length -gt 4096) { throw '验收根或标记不是受控普通文件/目录。' }
$markerValue = [IO.File]::ReadAllText($markerPath) | ConvertFrom-Json
$runId = (Split-Path -Leaf $Root).Substring('nanfeng-ai-windows-acceptance.'.Length)
if (@($markerValue.PSObject.Properties).Count -ne 3 -or $markerValue.schemaVersion -ne 1 -or $markerValue.identifier -ne $identifier -or $markerValue.runId -ne $runId) { throw '验收标记内容与根身份不一致。' }
$profile = if ($ExternalProfileForFullDelete) {
    Join-Path $temporaryParent ('nanfeng-ai-webview2.' + $runId)
} else { Join-Path $Root 'webview2' }
if (Test-Path -LiteralPath $profile) {
    $profileItem = Get-Item -LiteralPath $profile -Force
    if (-not $profileItem.PSIsContainer -or ($profileItem.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'WebView 验收目录不能是链接或普通文件。' }
}

# Stage immutable native executables so a live acceptance window never locks the build output.
$hash = (Get-FileHash -LiteralPath $sourceExecutable -Algorithm SHA256).Hash
$binaryDirectory = Join-Path $buildOutputRoot ('windows-acceptance-binaries/' + $hash)
$executable = if ($RunInPlace) { $sourceExecutable } else { Join-Path $binaryDirectory 'nanfeng-ai-desktop-spike.exe' }
if (-not $RunInPlace) {
    New-Item -ItemType Directory -Path $binaryDirectory -Force | Out-Null
    if (-not (Test-Path -LiteralPath $executable)) { Copy-Item -LiteralPath $sourceExecutable -Destination $executable -ErrorAction Stop }
}
if ((Get-FileHash -LiteralPath $executable -Algorithm SHA256).Hash -ne $hash) { throw '验收运行副本校验失败。' }
if ($StartupMode -eq 'normal') {
    $existingAcceptance = @(Get-CimInstance Win32_Process -Filter "Name='nanfeng-ai-desktop-spike.exe'" | Where-Object {
        $_.ExecutablePath -eq $executable -and $_.CommandLine -like '*--windows-isolated-normal-startup*'
    })
    if ($existingAcceptance.Count -gt 0) {
        throw ('已有相同候选的隔离正常验收实例，须核对原实例后再启动；PID=' + (($existingAcceptance | ForEach-Object { $_.ProcessId }) -join ','))
    }
}

$sourceMediaTools = if ($receiptMediaTools) { $receiptMediaTools } else { Join-Path (Split-Path -Parent $sourceExecutable) 'media-tools/windows-x86_64' }
& (Join-Path $PSScriptRoot 'verify-windows-media-tools.ps1') -Root $sourceMediaTools | Out-Null
$mediaToolsParent = Join-Path $binaryDirectory 'media-tools'
$mediaTools = if ($RunInPlace) { $sourceMediaTools } else { Join-Path $mediaToolsParent 'windows-x86_64' }
if (-not $RunInPlace -and -not (Test-Path -LiteralPath $mediaTools -PathType Container)) {
    New-Item -ItemType Directory -Path $mediaToolsParent -Force | Out-Null
    Copy-Item -LiteralPath $sourceMediaTools -Destination $mediaToolsParent -Recurse -ErrorAction Stop
}
& (Join-Path $PSScriptRoot 'verify-windows-media-tools.ps1') -Root $mediaTools | Out-Null
$variables = @{
    NANFENG_AI_WINDOWS_ACCEPTANCE_ROOT = $Root
    NANFENG_AI_WINDOWS_STORAGE_LOCATION_ACCEPTANCE = $(if ($StorageLocation) { '1' } else { $null })
    WEBVIEW2_USER_DATA_FOLDER = $profile
    WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS = $null
    NANFENG_AI_WINDOWS_MODEL_PENDING_PAUSE = $null
    NANFENG_AI_WINDOWS_SCHEMA45_PAIR_PAUSE = $null
    NANFENG_AI_WINDOWS_DRAFT_READ_PAUSE = $null
    NANFENG_AI_WINDOWS_BACKUP_SWITCH_PAUSE = $null
    NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_PAUSE = $null
    NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_COPY_ABORT = $null
    NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_PUBLISH_PAUSE = $null
    NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_LEDGER_PUBLISH_PAUSE = $null
    NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_ASSET_PUBLISH_PAUSE = $null
    NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_ASSET_CLEAR_PAUSE = $null
    NANFENG_AI_WINDOWS_ORDINARY_CHAT_CREDENTIAL_PAUSE = $null
    NANFENG_AI_WINDOWS_FULL_DELETE_PAUSE = $null
    NANFENG_AI_WINDOWS_ACCOUNT_SYNC_TEST_ENDPOINT = $null
    NANFENG_AI_WINDOWS_ACCOUNT_CALLBACK_DELAY_MS = $null
    NANFENG_AI_WINDOWS_OCR_TEST_ENDPOINT = $null
    TEMP = $temporaryParent
    TMP = $temporaryParent
}
if ($DebugPort) { $variables.WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS = "--remote-debugging-address=127.0.0.1 --remote-debugging-port=$DebugPort" }
if ($FakeCamera) { $variables.WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS += ' --use-fake-device-for-media-stream --use-fake-ui-for-media-stream' }
if ($ModelServiceTestEndpoint) { $variables.NANFENG_AI_WINDOWS_MODEL_SERVICE_TEST_ENDPOINT = $ModelServiceTestEndpoint.TrimEnd('/') }
if ($OrdinaryChatTestEndpoint) { $variables.NANFENG_AI_WINDOWS_ORDINARY_CHAT_TEST_ENDPOINT = $OrdinaryChatTestEndpoint.TrimEnd('/') }
if ($OcrTestEndpoint) { $variables.NANFENG_AI_WINDOWS_OCR_TEST_ENDPOINT = $OcrTestEndpoint.TrimEnd('/') }
if ($AccountSyncTestEndpoint) {
    $variables.NANFENG_AI_WINDOWS_ACCOUNT_SYNC_TEST_ENDPOINT = $AccountSyncTestEndpoint.TrimEnd('/')
    $variables.NANFENG_SUPABASE_URL = $AccountSyncTestEndpoint.TrimEnd('/')
    $variables.NANFENG_SUPABASE_PUBLISHABLE_KEY = 'nfai-synthetic-windows-account'
    if ($AccountCallbackDelayMs) { $variables.NANFENG_AI_WINDOWS_ACCOUNT_CALLBACK_DELAY_MS = [string]$AccountCallbackDelayMs }
}
if ($ModelPendingPause) { $variables.NANFENG_AI_WINDOWS_MODEL_PENDING_PAUSE = $ModelPendingPause }
if ($Schema45PairPause) { $variables.NANFENG_AI_WINDOWS_SCHEMA45_PAIR_PAUSE = 'after-chat' }
if ($DraftReadPause) { $variables.NANFENG_AI_WINDOWS_DRAFT_READ_PAUSE = 'CHAT_UNBOUND' }
if ($BackupSwitchPause) { $variables.NANFENG_AI_WINDOWS_BACKUP_SWITCH_PAUSE = $BackupSwitchPause }
if ($BackupRollbackPause) { $variables.NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_PAUSE = $BackupRollbackPause }
if ($BackupRollbackCopyAbort) { $variables.NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_COPY_ABORT = $BackupRollbackCopyAbort }
if ($BackupRollbackPublishPause) { $variables.NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_PUBLISH_PAUSE = $BackupRollbackPublishPause }
if ($BackupRollbackLedgerPublishPause) { $variables.NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_LEDGER_PUBLISH_PAUSE = $BackupRollbackLedgerPublishPause }
if ($BackupRollbackAssetPublishPause) { $variables.NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_ASSET_PUBLISH_PAUSE = $BackupRollbackAssetPublishPause }
if ($BackupRollbackAssetClearPause) { $variables.NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_ASSET_CLEAR_PAUSE = $BackupRollbackAssetClearPause }
if ($BackupRollbackSecondAssetClearPause) { $variables.NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_SECOND_ASSET_CLEAR_PAUSE = $BackupRollbackSecondAssetClearPause }
if ($BackupRollbackSecondAssetPublishPause) { $variables.NANFENG_AI_WINDOWS_BACKUP_ROLLBACK_SECOND_ASSET_PUBLISH_PAUSE = $BackupRollbackSecondAssetPublishPause }
if ($OrdinaryChatCredentialPause) { $variables.NANFENG_AI_WINDOWS_ORDINARY_CHAT_CREDENTIAL_PAUSE = 'before-credential' }
if ($FullDeletePause) { $variables.NANFENG_AI_WINDOWS_FULL_DELETE_PAUSE = $FullDeletePause }
$selectors = @{
    'c02-c06' = 'NANFENG_AI_DESKTOP_C02_C06_ACCEPTANCE'
    'c07-c12' = 'NANFENG_AI_DESKTOP_C07_C12_OFFLINE_ACCEPTANCE'
    'c13' = 'NANFENG_AI_DESKTOP_C13_ACCEPTANCE'
    'c14' = 'NANFENG_AI_DESKTOP_C14_ACCEPTANCE'
    'c15' = 'NANFENG_AI_DESKTOP_C15_ACCEPTANCE'
    'c16' = 'NANFENG_AI_DESKTOP_C16_VISUAL_ACCEPTANCE'
}
if ($selectors.ContainsKey($Fixture)) {
    $key = $selectors[$Fixture]
    $variables[$key] = '1'
    $variables[$key + '_ROOT'] = $Root
} elseif ($Fixture -eq 'p6-v2-picker') { $variables.NANFENG_AI_P6_V2_PICKER_ACCEPTANCE_ROOT = $Root }
if ($Fixture -eq 'c16') { $variables.NANFENG_AI_DESKTOP_C16_VISUAL_STATE = $C16State }
$previous = @{}
foreach ($key in $variables.Keys) { $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
try {
    foreach ($key in $variables.Keys) {
        if ($null -eq $variables[$key]) { Remove-Item -LiteralPath ('Env:' + $key) -ErrorAction SilentlyContinue }
        else { [Environment]::SetEnvironmentVariable($key, $variables[$key], 'Process') }
    }
    $startupArgument = if ($StartupMode -eq 'normal') { '--windows-isolated-normal-startup' } else { '--diagnostic-ui-schema-acceptance' }
    if ($Visible) { $startupArgument += ' --windows-acceptance-visible' }
    if ($ReminderActivationUrl) { $startupArgument += ' ' + $ReminderActivationUrl }
    $logRoot = if ($ExternalProfileForFullDelete) { $temporaryParent } else { $Root }
    $logPrefix = if ($ExternalProfileForFullDelete) { 'nanfeng-ai-startup.' + $runId + '.' } else { 'startup.' }
    # Reopening the same synthetic root must retain the prior process's evidence.
    # Keep the current filenames for existing readers, but never truncate old logs.
    $logArchiveId = [guid]::NewGuid().ToString()
    foreach ($stream in @('stdout', 'stderr')) {
        $previousLog = Join-Path $logRoot ($logPrefix + $stream + '.log')
        if (Test-Path -LiteralPath $previousLog -PathType Leaf) {
            $archivedLog = Join-Path $logRoot ($logPrefix + 'archived.' + $logArchiveId + '.' + $stream + '.log')
            Move-Item -LiteralPath $previousLog -Destination $archivedLog -ErrorAction Stop
        }
    }
    $process = Start-Process -FilePath $executable -ArgumentList $startupArgument -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $logRoot ($logPrefix + 'stdout.log')) -RedirectStandardError (Join-Path $logRoot ($logPrefix + 'stderr.log'))
    $launchReceiptPath = Join-Path $temporaryParent ('nanfeng-ai-launch.' + $runId + '.' + $process.Id + '.json')
    $launchReceipt = [pscustomobject]@{ launchReceipt=$launchReceiptPath; pid=$process.Id; root=$Root; identifier=$identifier; executable=$executable; sourceExecutable=$sourceExecutable; mediaTools=$mediaTools; sha256=$hash; diagnostic=($StartupMode -eq 'diagnostic'); startupMode=$StartupMode; fixture=$Fixture; webviewProfile=$profile; debugPort=$DebugPort; visible=[bool]$Visible; processStartedAt=$process.StartTime.ToUniversalTime().ToString('O') }
    # Keep the receipt readable across child-process console code pages, including CJK roots.
    $launchJson = $launchReceipt | ConvertTo-Json -Compress -EscapeHandling EscapeNonAscii
    [IO.File]::WriteAllText($launchReceiptPath, $launchJson, [Text.UTF8Encoding]::new($false))
    Write-Output $launchJson
} finally {
    foreach ($key in $previous.Keys) {
        if ($null -eq $previous[$key]) { Remove-Item -LiteralPath ('Env:' + $key) -ErrorAction SilentlyContinue }
        else { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
    }
}
