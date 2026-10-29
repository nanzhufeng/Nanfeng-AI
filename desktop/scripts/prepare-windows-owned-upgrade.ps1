param(
    [Parameter(Mandatory)][string]$ExistingReceipt,
    [Parameter(Mandatory)][string]$SetupManifest,
    [Parameter(Mandatory)][string]$EvidenceDirectory
)
$ErrorActionPreference = 'Stop'
if (-not $IsWindows) { throw 'Only Windows isolated acceptance is supported.' }
$receipt = Get-Content -LiteralPath $ExistingReceipt -Raw | ConvertFrom-Json
$package = Get-Content -LiteralPath $SetupManifest -Raw | ConvertFrom-Json
$install = [IO.Path]::GetFullPath([string]$receipt.installDir).TrimEnd('\')
$root = [IO.Path]::GetFullPath([string]$receipt.root).TrimEnd('\')
foreach ($entry in @(@{path=$install; prefix='nfai-installed-acceptance.'}, @{path=$root; prefix='nanfeng-ai-windows-acceptance.'})) {
    if ((Split-Path -Parent $entry.path) -ne 'D:\CodexTemp' -or -not (Split-Path -Leaf $entry.path).StartsWith($entry.prefix)) { throw 'Only owned D:\CodexTemp acceptance paths are allowed.' }
    if ((Get-Item -LiteralPath $entry.path).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Reparse acceptance roots are not allowed.' }
}
$marker = Get-Content -LiteralPath (Join-Path $root '.nanfeng-windows-acceptance.json') -Raw | ConvertFrom-Json
if ($marker.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance' -or (Split-Path -Leaf $root) -ne ('nanfeng-ai-windows-acceptance.' + $marker.runId)) { throw 'Acceptance marker identity mismatch.' }
if (-not $receipt.installationPreserved) { throw 'Owned installation receipt mismatch.' }
$baseline = & (Join-Path $PSScriptRoot 'assert-windows-owned-upgrade-baseline.ps1') -InstallDir $install -Root $root -ExeSha256 $receipt.newExeSha256 | ConvertFrom-Json
$sourceSchema = [int]$baseline.sourceSchema
$installedExe = Join-Path $install 'nanfeng-ai-desktop-spike.exe'
if ((Get-FileHash -LiteralPath $installedExe).Hash -ne $receipt.newExeSha256) { throw 'Installed EXE differs from its preserved receipt.' }
$setup = [IO.Path]::GetFullPath([string]$package.path)
if ((Split-Path -Parent $setup) -ne 'D:\CodexTemp' -or $package.sha256 -cnotmatch '^[A-F0-9]{64}$' -or (Split-Path -Leaf $setup) -ne ('nfai-chat-setup.' + $package.sha256 + '.exe') -or (Get-FileHash -LiteralPath $setup).Hash -ne $package.sha256 -or (Get-Item -LiteralPath $setup).Length -ne $package.bytes -or $package.buildKind -ne 'isolated acceptance debug nsis not signed') { throw 'Prepared isolated setup identity mismatch.' }
$repoRoot=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$productSnapshot=[IO.Path]::GetFullPath((Join-Path $repoRoot ([string]$package.sourceSnapshot)))
if (-not $productSnapshot.StartsWith((Join-Path $repoRoot 'docs\review')+'\',[StringComparison]::OrdinalIgnoreCase) -or (Get-FileHash -LiteralPath $productSnapshot).Hash -ne $package.sourceSnapshotSha256) { throw 'Package source snapshot identity mismatch.' }
$null = & (Join-Path $PSScriptRoot 'assert-windows-owned-package-source.ps1') -RepoRoot $repoRoot -SourceSnapshot $productSnapshot
$payloadProofPath = $null
if ($package.payloadHashObservedInsideSetup) {
    if ($package.actualPayloadExeSha256 -cnotmatch '^[A-F0-9]{64}$') { throw 'Observed payload hash is invalid.' }
    $payloadProofPath = [IO.Path]::GetFullPath((Join-Path $repoRoot ([string]$package.payloadVerification)))
    if (-not $payloadProofPath.StartsWith((Join-Path $repoRoot 'docs\review')+'\',[StringComparison]::OrdinalIgnoreCase) -or
        (Get-FileHash -LiteralPath $payloadProofPath).Hash -ne $package.payloadVerificationSha256) {
        throw 'Observed payload proof identity mismatch.'
    }
    $payloadProof = Get-Content -LiteralPath $payloadProofPath -Raw | ConvertFrom-Json
    if (-not $payloadProof.setupPayloadExtracted -or $payloadProof.setupSha256 -ne $package.sha256 -or
        $payloadProof.actualPayloadExeSha256 -ne $package.actualPayloadExeSha256) {
        throw 'Observed payload proof does not describe this setup.'
    }
}
$registrations = @(Get-ChildItem -LiteralPath 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall' | ForEach-Object { Get-ItemProperty -LiteralPath $_.PSPath } | Where-Object DisplayName -eq '南枫 AI Windows 独立验收')
if ($registrations.Count -ne 1 -or [IO.Path]::GetFullPath(([string]$registrations[0].InstallLocation).Trim('"')).TrimEnd('\') -ne $install -or ([string]$registrations[0].UninstallString).Trim('"') -ne (Join-Path $install 'uninstall.exe')) { throw 'Registry installation ownership mismatch.' }
$evidence = [IO.Path]::GetFullPath($EvidenceDirectory)
if (Test-Path -LiteralPath $evidence) { throw 'Evidence directory must be new; existing evidence is preserved.' }
if ($evidence.StartsWith($root + '\', [StringComparison]::OrdinalIgnoreCase) -or $evidence.StartsWith($install + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Evidence cannot be written into the preserved installation or data root.' }
$liveRoot = @(Get-CimInstance Win32_Process | Where-Object { $_.Name -in @('nanfeng-ai-desktop-spike.exe','NanfengAI-Windows-Acceptance.exe') -and ($_.ExecutablePath -eq $installedExe -or ($_.CommandLine -and $_.CommandLine.Contains($root))) })
if ($liveRoot.Count) { throw 'Owned source root is running; no stable upgrade snapshot can be prepared.' }
function Read-Tree([string]$directory) {
    $items = @(Get-ChildItem -LiteralPath $directory -Recurse -Force)
    if (@($items | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) { throw 'Reparse entries in preserved tree are not allowed.' }
    @($items | Where-Object { -not $_.PSIsContainer } | Sort-Object FullName | ForEach-Object {
        @{path=$_.FullName.Substring($directory.Length+1); bytes=$_.Length; sha256=(Get-FileHash -LiteralPath $_.FullName).Hash}
    })
}
$installFiles = Read-Tree $install
$rootFiles = Read-Tree $root
$python = @'
import json,pathlib,sqlite3,sys
root=pathlib.Path(sys.argv[1]); result=[]
for p in root.rglob('*'):
 if not p.is_file(): continue
 with p.open('rb') as f: header=f.read(16)
 if header!=b'SQLite format 3\x00': continue
 with sqlite3.connect(p.as_uri()+'?mode=ro',uri=True) as c:
  assert c.execute('PRAGMA integrity_check').fetchall()==[('ok',)]
  assert not c.execute('PRAGMA foreign_key_check').fetchall()
  result.append({'path':str(p.relative_to(root)), 'schemaVersion':c.execute('PRAGMA user_version').fetchone()[0]})
print(json.dumps(result))
'@
$dbJson = & python -c $python $root
if ($LASTEXITCODE -ne 0) { throw 'Read-only database audit failed.' }
$databases = @($dbJson | ConvertFrom-Json)
$business = @($databases | Where-Object path -eq 'workspace.sqlite3')
if ($business.Count -ne 1 -or $business[0].schemaVersion -ne $sourceSchema) { throw 'Source schema differs from its verified installation baseline.' }
$workBusiness = @($databases | Where-Object { $_.path.Replace('\','/') -eq 'work-area/workspace.sqlite3' })
if ($workBusiness.Count -ne 1 -or $workBusiness[0].schemaVersion -ne $sourceSchema) { throw 'Work-area schema differs from its verified installation baseline.' }
# Hashing credentials/profile files records only identities; no content is emitted.
if ((Read-Tree $install | ConvertTo-Json -Depth 5 -Compress) -ne ($installFiles | ConvertTo-Json -Depth 5 -Compress) -or (Read-Tree $root | ConvertTo-Json -Depth 5 -Compress) -ne ($rootFiles | ConvertTo-Json -Depth 5 -Compress)) { throw 'Preserved files changed during preparation.' }
New-Item -ItemType Directory -Path $evidence | Out-Null
$safetyText = & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'check-windows-acceptance-installer-safety.ps1') -EvidencePath (Join-Path $evidence 'installer-safety.json')
$safetyExit = $LASTEXITCODE
if ($safetyExit -notin @(0,2)) { throw 'Installer safety owner check failed.' }
$safety = $safetyText | ConvertFrom-Json
$scheme = Get-Item -LiteralPath 'HKCU:\Software\Classes\nanfeng-ai-windows-acceptance-reminder\shell\open\command' -ErrorAction SilentlyContinue
$plan = [ordered]@{
    preparedAt=[DateTime]::UtcNow.ToString('o'); action='read-only preparation'; installationStarted=$false
    existingReceipt=[IO.Path]::GetFullPath($ExistingReceipt); installDir=$install; root=$root
    oldExeSha256=$receipt.newExeSha256; setupPath=$setup; setupSha256=$package.sha256
    productSourceSnapshot=$productSnapshot; productSourceSnapshotSha256=$package.sourceSnapshotSha256
    payloadHashObservedInsideSetup=[bool]$package.payloadHashObservedInsideSetup
    expectedInstalledExeSha256=$package.actualPayloadExeSha256
    payloadVerification=$payloadProofPath; payloadVerificationSha256=$package.payloadVerificationSha256
    setupSignature=(Get-AuthenticodeSignature -LiteralPath $setup).Status.ToString()
    sourceSchema=$sourceSchema; targetSchema=56; databases=$databases
    rollbackDirectory=$(if ($sourceSchema -eq 56) { 'D:\CodexTemp\nfai-owned-upgrade-rollback.' + [guid]::NewGuid().ToString() } else { $null })
    installFiles=$installFiles; rootFiles=$rootFiles; stableBeforeAfterHashes=$true
    schemeCommand= $(if ($scheme) { $scheme.GetValue('') } else { $null })
    safeToInstallNow=$safety.safeToInstall; safetyExitCode=$safetyExit
    requirementsBeforeExecution=@('Original W20 run completes natural close and cold reopen', 'Recheck current-user basename safety immediately before installer', 'Revalidate installation registration, package and all preserved file identities', 'Preserve independent rollback installation and data copies before execution')
    requirementsAfterExecution=@('Observe actual installed EXE hash rather than inferred NSIS payload', 'Installer leaves data bytes unchanged before application startup', "Same-root schema $sourceSchema to 56 preserves business and synthetic credential ownership", 'Natural close and cold reopen verify chat UI, memory and reminders', 'System protocol and notification activation need separate evidence')
    actualUpgradeVerified=$false; full13Complete=$false
}
$plan | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $evidence 'upgrade-plan.json') -Encoding utf8
@{prepared=$true; installFiles=$installFiles.Count; rootFiles=$rootFiles.Count; databases=$databases.Count; sourceSchema=$sourceSchema; safeToInstallNow=$safety.safeToInstall; installationStarted=$false; evidence=$evidence} | ConvertTo-Json -Compress
