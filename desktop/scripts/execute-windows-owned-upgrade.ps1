param(
    [Parameter(Mandatory)][string]$PlanPath,
    [Parameter(Mandatory)][string]$EvidenceDirectory,
    [ValidateSet('StrictQualifiedBaseline','IsolatedDevelopmentRecovery')][string]$Mode='StrictQualifiedBaseline'
)
$ErrorActionPreference = 'Stop'
if (-not $IsWindows) { throw 'Windows only.' }
$plan = Get-Content -LiteralPath $PlanPath -Raw | ConvertFrom-Json
if ($plan.action -ne 'read-only preparation' -or $plan.sourceSchema -notin @(52,56) -or $plan.targetSchema -ne 56) { throw 'Unexpected upgrade plan.' }
$install = [IO.Path]::GetFullPath([string]$plan.installDir).TrimEnd('\')
$root = [IO.Path]::GetFullPath([string]$plan.root).TrimEnd('\')
if ($install -ne 'D:\CodexTemp\nfai-installed-acceptance.7d2cb30e-9606-4d88-bd91-bbc0fec53c92' -or $root -ne 'D:\CodexTemp\nanfeng-ai-windows-acceptance.00afec83-701e-4004-b210-18e974d9163b') { throw 'Only the previously owned synthetic installation is authorized.' }
$evidence = [IO.Path]::GetFullPath($EvidenceDirectory)
if (Test-Path -LiteralPath $evidence) { throw 'Evidence directory must be new.' }
if ($evidence.StartsWith($root,[StringComparison]::OrdinalIgnoreCase) -or $evidence.StartsWith($install,[StringComparison]::OrdinalIgnoreCase)) { throw 'Evidence cannot overwrite preserved roots.' }
New-Item -ItemType Directory -Path $evidence | Out-Null
$outcome = [ordered]@{installationStarted=$false; installed=$false; nativeVerified=$false; full13Complete=$false; stage='safety'; planPath=[IO.Path]::GetFullPath($PlanPath)}
try {
    $safety = & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'check-windows-acceptance-installer-safety.ps1') -EvidencePath (Join-Path $evidence 'installer-safety.json')
    if ($LASTEXITCODE -ne 0) { throw 'Installer process safety gate rejected; installation and data preserved.' }
    $baseline = & (Join-Path $PSScriptRoot 'assert-windows-owned-upgrade-baseline.ps1') -InstallDir $install -Root $root -ExeSha256 $plan.oldExeSha256 | ConvertFrom-Json
    if ($baseline.sourceSchema -ne $plan.sourceSchema) { throw 'Plan schema differs from its verified baseline.' }
    $outcome.executionMode=$Mode
    $outcome.baselineQualified24h=$false
    if ($Mode -eq 'IsolatedDevelopmentRecovery') {
        $newestCandidateUpgrade = $plan.oldExeSha256 -ceq '48D62E9925FAA10B4E3756FB28110785007B12F142F9837AE0D355C63A47154D'
        if ($newestCandidateUpgrade) {
            if ($plan.sourceSchema -ne 56 -or $plan.setupSha256 -cne 'FF0EA411F67066EF58B6D986B92A33534B1FBDA100590E481DD2A2ABF2FF85F0' -or $plan.expectedInstalledExeSha256 -cne '6B4133EB211F3432F1C6EC0D02DA8B3A20B214069A198CED99E7BAF94F76F1A5') { throw 'Current development upgrade permits only owned 48D62 and observed FF0E/6B413.' }
            $repo=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
            $review=Join-Path $repo 'docs/review/20261005-chat-sync-owner'
            $regressionPath=Join-Path $review 'full-frontend-resumed/full-regression.json'
            $nativePath=Join-Path $review 'increment-receipt.json'
            $mergedPath=Join-Path $review 'merged-batch-receipt.json'
            $rustPath=Join-Path $review 'full-rust-execution.json'
            $inputsPath=Join-Path $review 'full-frontend-resumed/inputs.json'
            $reg=Get-Content -LiteralPath $regressionPath -Raw|ConvertFrom-Json
            $nat=Get-Content -LiteralPath $nativePath -Raw|ConvertFrom-Json
            $merged=Get-Content -LiteralPath $mergedPath -Raw|ConvertFrom-Json
            $rust=Get-Content -LiteralPath $rustPath -Raw|ConvertFrom-Json
            $inputs=Get-Content -LiteralPath $inputsPath -Raw|ConvertFrom-Json
            $gui='15B1319D084BF786576130CE52451D90540753396F2D6106AAA34C684EEC3D06'
            if ($reg.filesExecuted -ne 176 -or $reg.totals.pass -ne 992 -or $reg.totals.fail -ne 0 -or $reg.totals.skipped -ne 12 -or @($reg.groups|Where-Object exitCode -ne 0).Count -or -not $reg.productInputsUnchanged -or $merged.guiSha256 -cne $gui -or $merged.setupSha256 -cne $plan.setupSha256 -or $merged.payloadExeSha256 -cne $plan.expectedInstalledExeSha256 -or $merged.rust.pass -ne 840 -or $merged.rust.fail -ne 0 -or $merged.rust.ignored -ne 14 -or $rust.exitCode -ne 0 -or -not $rust.productAndFinalTestsPrePostExact) { throw 'Current complete frontend/core package bindings required.' }
            if ($nat.candidate.sha256 -cne $gui -or -not $nat.buildInputsPrePostExact -or $nat.productInputs -ne 340 -or $nat.testInputs -ne 176 -or $nat.nativeStartupCold.candidateSha256 -cne $gui -or -not $nat.nativeStartupCold.coldExact -or $nat.nativeStartupCold.providerRequests -ne 0 -or @($nat.nativeStartupCold.closes).Count -ne 2 -or @($nat.nativeStartupCold.closes|Where-Object exitCode -ne 0).Count -or -not $nat.sqliteIntegrityAndForeignKeysOk -or $nat.nativeJob.exitCode -ne 0 -or $nat.nativeJob.ownedProcessesRemaining -ne 0) { throw 'Current GUI startup/natural close/cold and independent storage required.' }
            if (@($inputs.tests.PSObject.Properties).Count -ne 176) { throw 'Current final test identity count differs.' }
            foreach ($test in $inputs.tests.PSObject.Properties) {
                $path=[IO.Path]::GetFullPath((Join-Path $repo $test.Name))
                if (-not $path.StartsWith((Join-Path $repo 'desktop/tests')+'\',[StringComparison]::OrdinalIgnoreCase) -or (Get-FileHash -LiteralPath $path).Hash -cne $test.Value) { throw 'Current tests changed after regression.' }
            }
            $developmentEvidenceBindings=@($regressionPath,$nativePath,$mergedPath,$rustPath,$inputsPath) | ForEach-Object { @{path=$_;hash=(Get-FileHash -LiteralPath $_).Hash} }
            $outcome.sourceSupersession=@{oldInstalledSha256=$plan.oldExeSha256;newGuiSha256=$gui;reason='VERIFIED_CHAT_CLEANUP_AND_SYNC_OWNER_FIXES';baseline24hAccepted=$false}
            $outcome.currentAcceptance=@{frontendPath=$regressionPath;frontendSha256=(Get-FileHash $regressionPath).Hash;nativePath=$nativePath;nativeSha256=(Get-FileHash $nativePath).Hash;layer='DEVELOPMENT_ONLY_CURRENT_FULL_UNIT_AND_GUI_STARTUP_COLD'}
            $outcome.stability24hAccepted=$false
            $outcome.requiresCurrentInstalledStability=$true
        } else {
        $currentCandidateUpgrade = $plan.oldExeSha256 -eq 'EE82463C303E91CA2C0AA23698772D004B841EF3E66A32449BC0F596C2DFAF35'
        if ($currentCandidateUpgrade) {
            if ($plan.sourceSchema -ne 56 -or $plan.setupSha256 -ne 'F02B9F0964564E36417324E8A6C4FE3BAE090221BCC1E98D91D82BA67E823BC1' -or $plan.expectedInstalledExeSha256 -ne '48D62E9925FAA10B4E3756FB28110785007B12F142F9837AE0D355C63A47154D') { throw 'Current candidate upgrade permits only the verified EE baseline and observed F02B/48D62 package.' }
        } else {
        if ($plan.sourceSchema -ne 56 -or $plan.oldExeSha256 -ne 'DF51383345FFCF490ED7C5301E79234B6424597A25510E9AF117DAF291C40BFC' -or $plan.setupSha256 -ne '52BCE29688FF24E3F103DA67B9632244BB51913BD4ACB0BC273ECB6F7CF5B123' -or $plan.expectedInstalledExeSha256 -ne 'EE82463C303E91CA2C0AA23698772D004B841EF3E66A32449BC0F596C2DFAF35') { throw 'Development recovery permits only this exact owned baseline and observed current package.' }
        }
        $repo=Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
        $failedRoot='D:\CodexTemp\nfa-installed-chat-normal-detached-24h-20261004\nanfeng-ai-windows-acceptance.3def69c1-b9fc-400f-9568-1ed5f94b19c9'
        if ($currentCandidateUpgrade) { $failedRoot='D:\CodexTemp\nfa-bounded-installed-stability.681b78d4-e1dc-4686-a71d-6974b852404d\nanfeng-ai-windows-acceptance.d119f2ab-df3b-4718-b09e-78a0e58154b9' }
        $failedStatusPath=Join-Path $failedRoot 'local-stability-status.json'
        $failedHostPath=Join-Path $repo 'docs/review/20261004-owned-upgrade-natural-24h-1791121761758/detached-monitor-host-outcome.json'
        if ($currentCandidateUpgrade) { $failedHostPath=Join-Path $repo 'docs/review/20261005-chat-submission-claim/current-installed-b7-24h/host-outcome.json' }
        $failed=Get-Content -LiteralPath $failedStatusPath -Raw|ConvertFrom-Json
        $hostResult=Get-Content -LiteralPath $failedHostPath -Raw|ConvertFrom-Json
        if ($failed.state -ne 'FAILED' -or $failed.accepted24h -or -not $failed.forcedCleanup -or $failed.candidateSha256 -ne $plan.oldExeSha256 -or $hostResult.phase -ne 'FAILED' -or $hostResult.exitCode -ne 1) { throw 'Expected terminal failure changed; no recovery install.' }
        $regressionPath=Join-Path $repo 'docs/review/20261005-chat-submission-claim/bounded-b7-full-regression-serial-path-fixed/full-regression.json'
        $nativePath=Join-Path $repo 'docs/review/20261005-chat-submission-claim/current-chat-concurrent-native-receipt.json'
        if ($currentCandidateUpgrade) {
            $dispositionPath=Join-Path $repo 'docs/review/20261005-chat-submission-claim/current-installed-b7-24h/superseded-source-disposition.json'
            $disposition=Get-Content -LiteralPath $dispositionPath -Raw|ConvertFrom-Json
            if ($disposition.reason -ne 'SUPERSEDED_BY_VERIFIED_NOTIFICATION_SOURCE_FIXES' -or -not $disposition.normalCloseRequested -or -not $disposition.nativeExited -or $disposition.accepted24h) { throw 'Old run supersession not verified.' }
            $regressionPath=Join-Path $repo 'docs/review/20261005-chat-submission-claim/bounded-notification-full-regression-serial/full-regression.json'
            $nativePath=Join-Path $repo 'docs/review/20261005-chat-submission-claim/notification-chat-concurrent-native-receipt.json'
        }
        $reg=Get-Content -LiteralPath $regressionPath -Raw|ConvertFrom-Json
        $nat=Get-Content -LiteralPath $nativePath -Raw|ConvertFrom-Json
        $gui='B7E7A056F3D6335EBA5424B28B9F8A56770E3C90314B4AD97916283C90D45116'
        if ($currentCandidateUpgrade) {
            $gui='6CE7FA429967E1AD5B41D7C8F98D08B10BD1BFD5E144C2219A6AADAE8F1EAD99'
            $build=Get-Content (Join-Path $repo 'docs/review/20261005-chat-submission-claim/notification-candidate-build.json') -Raw|ConvertFrom-Json
            $rust=Get-Content (Join-Path $repo 'docs/review/20261005-chat-submission-claim/notification-full-rust-receipt.json') -Raw|ConvertFrom-Json
            $editors=Get-Content (Join-Path $repo 'docs/review/20261005-chat-submission-claim/notification-candidate-editor-native-receipt.json') -Raw|ConvertFrom-Json
            if (-not $reg.pass -or $reg.fileCount -ne 173 -or $reg.counts.pass -ne 972 -or $reg.counts.fail -ne 0 -or -not $reg.productInputsExact -or $build.candidate.sha256 -ne $gui -or $build.buildExitCode -ne 0 -or -not $build.productAndTestsUnchangedDuringBuild -or -not $rust.pass -or $rust.counts.pass -ne 837 -or $rust.counts.fail -ne 0 -or -not $nat.pass -or $nat.candidateSha256 -ne $gui -or $nat.ordinaryRequests -ne 10 -or -not $nat.sqliteAllOk -or $nat.boundedJob.ownedProcessesRemaining -ne 0 -or -not $editors.pass -or $editors.candidateSha256 -ne $gui -or -not $editors.independentStorage.allSqliteOk) { throw 'Current full frontend/core/build and both Native storage chains required.' }
            foreach ($test in $reg.testManifest.PSObject.Properties) { if ($build.tests.($test.Name) -ne $test.Value) { throw 'Test results not bound to current candidate build.' } }
        } else {
        if (-not $reg.pass -or $reg.candidateSha256 -ne $gui -or $reg.fileCount -ne 171 -or $reg.counts.fail -ne 0 -or $reg.counts.pass -ne 962 -or -not $reg.productInputsExact -or -not $nat.pass -or $nat.candidateSha256 -ne $gui -or $nat.ordinaryRequests -ne 10 -or -not $nat.sqliteAllOk -or $nat.boundedJob.ownedProcessesRemaining -ne 0) { throw 'Current complete frontend and Native/storage evidence required.' }
        }
        foreach ($test in $reg.testManifest.PSObject.Properties) {
            $path=[IO.Path]::GetFullPath((Join-Path $repo $test.Name))
            if (-not $path.StartsWith((Join-Path $repo 'desktop/tests')+'\',[StringComparison]::OrdinalIgnoreCase) -or (Get-FileHash -LiteralPath $path).Hash -ne $test.Value) { throw 'Current test manifest changed.' }
        }
        $outcome.baselineFailurePreserved=@{statusPath=$failedStatusPath;statusSha256=(Get-FileHash $failedStatusPath).Hash;hostPath=$failedHostPath;hostSha256=(Get-FileHash $failedHostPath).Hash;accepted24h=$false}
        $outcome.currentAcceptance=@{frontendPath=$regressionPath;frontendSha256=(Get-FileHash $regressionPath).Hash;nativePath=$nativePath;nativeSha256=(Get-FileHash $nativePath).Hash}
        $outcome.stability24hAccepted=$false
        $outcome.requiresCurrentInstalledStability=$true
        }
    } else {
    if ($plan.sourceSchema -eq 56) {
        $stabilityRoot = 'D:\CodexTemp\nfa-installed-chat-normal-detached-24h-20261004\nanfeng-ai-windows-acceptance.3def69c1-b9fc-400f-9568-1ed5f94b19c9'
        $stabilitySha = $plan.oldExeSha256
        $repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
        $hostOutcome = Get-Content -LiteralPath (Join-Path $repo 'docs\review\20261004-owned-upgrade-natural-24h-1791121761758\detached-monitor-host-outcome.json') -Raw | ConvertFrom-Json
        if ($hostOutcome.phase -ne 'COMPLETED' -or $hostOutcome.exitCode -ne 0) { throw 'Installed 24h monitor host did not complete normally.' }
    } else {
        $stabilityRoot = 'D:\CodexTemp\nfa-stability-pin-24h-20261003\nanfeng-ai-windows-acceptance.6580ea02-2996-45e0-a1e6-1f4056e3af7e'
        $stabilitySha = 'B9660DD275E118CDEF76D0A77918153E44184DFEEA5BC76B667D20D4FDA04D70'
    }
    $status = Get-Content -LiteralPath (Join-Path $stabilityRoot 'local-stability-status.json') -Raw | ConvertFrom-Json
    $stability = Get-Content -LiteralPath (Join-Path $stabilityRoot 'local-stability-evidence.json') -Raw | ConvertFrom-Json
    if ($status.state -ne 'LOCAL_24H_COMPLETED' -or -not $status.accepted24h -or -not $status.normalCloseAndColdVerified -or $status.candidateSha256 -ne $stabilitySha -or $status.wallElapsedMs -lt 86400000 -or $status.monotonicElapsedMs -lt 86400000 -or -not $stability.pass -or $stability.forcedCleanup -or ($plan.sourceSchema -eq 56 -and -not $stability.installedRuntime)) { throw 'Baseline 24h/natural-close/cold evidence is incomplete.' }
        $outcome.baselineQualified24h=$true
    }
    $outcome.stage='identity'
    $desktopRoot=Split-Path -Parent $PSScriptRoot
    $repoRoot=Split-Path -Parent $desktopRoot
    $productSnapshot=[IO.Path]::GetFullPath([string]$plan.productSourceSnapshot)
    if (-not $productSnapshot.StartsWith((Join-Path $repoRoot 'docs\review')+'\',[StringComparison]::OrdinalIgnoreCase) -or (Get-FileHash -LiteralPath $productSnapshot).Hash -ne $plan.productSourceSnapshotSha256) { throw 'Prepared package source snapshot changed.' }
    $null = & (Join-Path $PSScriptRoot 'assert-windows-owned-package-source.ps1') -RepoRoot $repoRoot -SourceSnapshot $productSnapshot
    if (-not $plan.payloadHashObservedInsideSetup -or $plan.expectedInstalledExeSha256 -cnotmatch '^[A-F0-9]{64}$') {
        throw 'Actual setup payload must be observed before installation.'
    }
    $payloadProofPath=[IO.Path]::GetFullPath([string]$plan.payloadVerification)
    if (-not $payloadProofPath.StartsWith((Join-Path $repoRoot 'docs\review')+'\',[StringComparison]::OrdinalIgnoreCase) -or
        (Get-FileHash -LiteralPath $payloadProofPath).Hash -ne $plan.payloadVerificationSha256) { throw 'Payload proof changed.' }
    $payloadProof=Get-Content -LiteralPath $payloadProofPath -Raw | ConvertFrom-Json
    if (-not $payloadProof.setupPayloadExtracted -or $payloadProof.setupSha256 -ne $plan.setupSha256 -or
        $payloadProof.actualPayloadExeSha256 -ne $plan.expectedInstalledExeSha256) { throw 'Payload proof belongs to another setup.' }
    function Assert-Tree([string]$directory, $files) {
        if ((Get-Item -LiteralPath $directory).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Reparse root rejected.' }
        if (@(Get-ChildItem -LiteralPath $directory -Force -Recurse | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) { throw 'Reparse tree entry rejected.' }
        foreach ($file in $files) {
            $path = [IO.Path]::GetFullPath((Join-Path $directory ([string]$file.path)))
            if (-not $path.StartsWith($directory+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Plan path escaped its preserved root.' }
            $item=Get-Item -LiteralPath $path
            if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint -or $item.Length -ne $file.bytes -or (Get-FileHash -LiteralPath $path).Hash -ne $file.sha256) { throw 'Preserved file identity changed.' }
        }
    }
    Assert-Tree $install $plan.installFiles
    Assert-Tree $root $plan.rootFiles
    if ($plan.sourceSchema -eq 56) {
        $rollback = [IO.Path]::GetFullPath([string]$plan.rollbackDirectory)
        if ((Split-Path -Parent $rollback) -ne 'D:\CodexTemp' -or (Split-Path -Leaf $rollback) -cnotmatch '^nfai-owned-upgrade-rollback\.[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' -or (Test-Path -LiteralPath $rollback)) { throw 'Rollback directory must be a new owned D:\CodexTemp GUID directory.' }
        $rollbackInstall = Join-Path $rollback 'installation'
        $rollbackRoot = Join-Path $rollback 'data'
        New-Item -ItemType Directory -Path $rollback | Out-Null
        Copy-Item -LiteralPath $install -Destination $rollbackInstall -Recurse
        Copy-Item -LiteralPath $root -Destination $rollbackRoot -Recurse
        $outcome.rollbackDirectory = $rollback
    } else {
        $rollbackInstall='D:\CodexTemp\nfai-installed-preserved.302923917E23A643C287DEF875CD895267C97C7AFD1D9E5E83C46FDA1E1DF796'
        $rollbackRoot='D:\CodexTemp\nfai-root-preserved.00afec83-701e-4004-b210-18e974d9163b'
    }
    Assert-Tree $rollbackInstall $plan.installFiles
    Assert-Tree $rollbackRoot $plan.rootFiles
    $setup=[IO.Path]::GetFullPath([string]$plan.setupPath)
    if ((Split-Path -Parent $setup) -ne 'D:\CodexTemp' -or $plan.setupSha256 -cnotmatch '^[A-F0-9]{64}$' -or (Split-Path -Leaf $setup) -ne ('nfai-chat-setup.'+$plan.setupSha256+'.exe') -or (Get-FileHash -LiteralPath $setup).Hash -ne $plan.setupSha256) { throw 'Setup identity mismatch.' }
    $rows=@(Get-ChildItem -LiteralPath 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall' | ForEach-Object {Get-ItemProperty -LiteralPath $_.PSPath} | Where-Object DisplayName -eq '南枫 AI Windows 独立验收')
    if ($rows.Count -ne 1 -or ([string]$rows[0].InstallLocation).Trim('"') -ne $install) { throw 'Installation registration changed.' }
    & python (Join-Path $PSScriptRoot 'snapshot-owned-migration.py') $root (Join-Path $evidence 'database-before.json')
    if ($LASTEXITCODE -ne 0) { throw 'Pre-upgrade database audit failed.' }
    # Recheck immediately before NSIS; no other root may be interrupted.
    $safety = & pwsh -NoProfile -File (Join-Path $PSScriptRoot 'check-windows-acceptance-installer-safety.ps1') -EvidencePath (Join-Path $evidence 'installer-safety-immediate.json')
    if ($LASTEXITCODE -ne 0) { throw 'Immediate installer process gate rejected.' }
    if ($Mode -eq 'IsolatedDevelopmentRecovery') {
        if ($newestCandidateUpgrade) {
            foreach ($binding in $developmentEvidenceBindings) { if ((Get-FileHash -LiteralPath $binding.path).Hash -cne $binding.hash) { throw 'Current development evidence changed before installer.' } }
        } else {
        foreach ($binding in @(@{path=$failedStatusPath;hash=$outcome.baselineFailurePreserved.statusSha256},@{path=$failedHostPath;hash=$outcome.baselineFailurePreserved.hostSha256},@{path=$regressionPath;hash=$outcome.currentAcceptance.frontendSha256},@{path=$nativePath;hash=$outcome.currentAcceptance.nativeSha256})) {
            if ((Get-FileHash -LiteralPath $binding.path).Hash -ne $binding.hash) { throw 'Bound recovery evidence changed before installer.' }
        }
        }
    }
    $outcome.stage='install'; $outcome.installationStarted=$true
    $process=Start-Process -FilePath $setup -ArgumentList "/S /D=$install" -WindowStyle Hidden -Wait -PassThru
    $outcome.installerExitCode=$process.ExitCode
    if ($process.ExitCode -ne 0) { throw 'Installer failed; preserve installed state and rollback evidence.' }
    Assert-Tree $root $plan.rootFiles
    $rows=@(Get-ChildItem -LiteralPath 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall' | ForEach-Object {Get-ItemProperty -LiteralPath $_.PSPath} | Where-Object DisplayName -eq '南枫 AI Windows 独立验收')
    if ($rows.Count -ne 1 -or ([string]$rows[0].InstallLocation).Trim('"') -ne $install) { throw 'Post-install registration mismatch.' }
    $exe=Join-Path $install 'nanfeng-ai-desktop-spike.exe'
    $actual=(Get-FileHash -LiteralPath $exe).Hash
    if ($actual -eq $plan.oldExeSha256) { throw 'Installer did not replace the EXE.' }
    if ($actual -ne $plan.expectedInstalledExeSha256) { throw 'Installed EXE differs from the observed setup payload; do not launch it.' }
    $outcome.installed=$true; $outcome.actualInstalledExeSha256=$actual; $outcome.dataUnchangedBeforeStartup=$true
    $outcome.stage='native'
    # RunInPlace observes the installed payload; no reconstructed payload hash is used.
    & python (Join-Path $PSScriptRoot 'run-windows-node-bounded.py') --owned-migration-plan $PlanPath --timeout 180 --output (Join-Path $evidence 'bounded-native') > (Join-Path $evidence 'native.txt') 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Installed native chain failed; installation and source retained.' }
    & python (Join-Path $PSScriptRoot 'snapshot-owned-migration.py') $root (Join-Path $evidence 'database-after.json')
    if ($LASTEXITCODE -ne 0) { throw 'Post-upgrade database audit failed.' }
    @{source=$rollbackRoot;root=$root;sourceFileCount=$plan.rootFiles.Count;installedExecution=$true} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'copy.json') -Encoding utf8
    $checkPlan=$plan.PSObject.Copy();$checkPlan.root=$rollbackRoot
    $checkPlan | ConvertTo-Json -Depth 9 | Set-Content -LiteralPath (Join-Path $evidence 'rollback-check-plan.json') -Encoding utf8
    & python (Join-Path $PSScriptRoot 'check-owned-migration-synthetic.py') $evidence (Join-Path $evidence 'rollback-check-plan.json') > (Join-Path $evidence 'independent.json')
    if ($LASTEXITCODE -ne 0) { throw 'Installed migration preservation audit failed.' }
    Assert-Tree $rollbackInstall $plan.installFiles
    Assert-Tree $rollbackRoot $plan.rootFiles
    $outcome.nativeVerified=$true; $outcome.stage='verified-scoped-upgrade'
} catch {
    $outcome.error=$_.Exception.Message
    throw
} finally {
    # No automatic uninstall or rollback, even after partial installer failure.
    $outcome | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $evidence 'outcome.json') -Encoding utf8
}
