param(
    [Parameter(Mandatory)][ValidatePattern('^[A-F0-9]{64}$')][string]$ExpectedSha256,
    [Parameter(Mandatory)][string]$EvidenceDirectory,
    [Parameter(Mandatory)][string]$RootParent
)
$ErrorActionPreference='Stop'
$desktop=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$evidence=[IO.Path]::GetFullPath($EvidenceDirectory)
$parent=[IO.Path]::GetFullPath($RootParent)
if (!$parent.StartsWith('D:\CodexTemp\',[StringComparison]::OrdinalIgnoreCase)) {throw 'Synthetic D: root required'}
if (!(Test-Path -LiteralPath $evidence -PathType Container)) {throw 'Existing evidence directory required'}
if (Test-Path -LiteralPath (Join-Path $evidence 'hidden-focus-native.json')) {throw 'Evidence already exists'}
$old=@('6861334AFEE1ECD4B6A0179203DAEE42007E001CACDC833BCA619B1C3F526A60','D1ED325302D81EDE500A0C8FD6E873F3CE7C950E9E6615604FC0D3C6615BDA32','2E812965E6B2D46FA98D0B206A9A05980BB8C7DB6AEE1C97C40AEFFB1B486574')
if ($ExpectedSha256 -in $old) {throw 'Candidate predates focus protection'}
$candidate=Get-Content -Raw -LiteralPath (Join-Path $desktop 'output/windows-acceptance-release/current.json') | ConvertFrom-Json
if ($candidate.sha256 -cne $ExpectedSha256 -or $candidate.buildProfile -cne 'release' -or $candidate.identifier -cne 'com.nanzhufeng.ai.desktop.windowsacceptance') {throw 'Candidate receipt mismatch'}
if ((Get-FileHash -LiteralPath $candidate.executable -Algorithm SHA256).Hash -cne $ExpectedSha256) {throw 'Candidate hash mismatch'}
$result=[ordered]@{sha256=$ExpectedSha256;buildProfile='release';startedUtc=[datetime]::UtcNow.ToString('o');cycles=@();pass=$false;failure=$null}
$launch=$null
$root=''
function Inspect-Owned {
    & (Join-Path $PSScriptRoot 'inspect-windows-island-acceptance.ps1') -ProcessId $launch.pid -Executable $launch.executable -Sha256 $ExpectedSha256 -StartedAt ([datetime]$launch.processStartedAt) | ConvertFrom-Json
}
function Close-Owned {
    $close=& (Join-Path $PSScriptRoot 'close-windows-hidden-acceptance.ps1') -ProcessId $launch.pid -Executable $launch.executable -Sha256 $ExpectedSha256 -StartedAt ([datetime]$launch.processStartedAt) | ConvertFrom-Json
    $until=[datetime]::UtcNow.AddSeconds(20)
    while ((Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) -and [datetime]::UtcNow -lt $until) {Start-Sleep -Milliseconds 100}
    if (Get-Process -Id $launch.pid -ErrorAction SilentlyContinue) {throw 'Owned process did not close normally'}
    return $close
}
try {
    foreach ($cycle in @('initial','cold')) {
        $launchArgs=@{RootParent=$parent;ExecutablePath=$candidate.executable;StartupMode='normal'}
        if ($root) {$launchArgs.Root=$root}
        $launch=& (Join-Path $PSScriptRoot 'start-windows-acceptance.ps1') @launchArgs | ConvertFrom-Json
        if ($launch.visible -or $launch.sha256 -cne $ExpectedSha256) {throw 'Hidden launch identity mismatch'}
        $root=$launch.root
        $record=[ordered]@{cycle=$cycle;launch=$launch;samples=@();close=$null}
        $result.cycles+=,$record
        $deadline=[datetime]::UtcNow.AddSeconds(30)
        do {
            $sample=Inspect-Owned
            $record.samples+=,$sample
            if ($sample.foregroundOwned -or $sample.unexpectedVisibleOwnedWindowCount -ne 0 -or @($sample.windows | Where-Object visible).Count) {throw 'Background focus or visible window violation'}
            if (@($sample.windows).Count -ge 2) {break}
            Start-Sleep -Milliseconds 200
        } while ([datetime]::UtcNow -lt $deadline)
        if (@($sample.windows).Count -ne 2) {throw 'Main and island windows not ready'}
        foreach ($i in 1..5) {
            Start-Sleep -Milliseconds 300
            $sample=Inspect-Owned
            $record.samples+=,$sample
            if ($sample.foregroundOwned -or $sample.unexpectedVisibleOwnedWindowCount -ne 0 -or @($sample.windows | Where-Object visible).Count) {throw 'Background focus or visible window violation'}
        }
        $record.close=Close-Owned
        $launch=$null
    }
    $result.pass=$true
} catch {
    $result.failure=$_.Exception.Message
} finally {
    if ($launch -and (Get-Process -Id $launch.pid -ErrorAction SilentlyContinue)) {
        try {$result.failureClose=Close-Owned;$launch=$null} catch {
            $result.failureCloseError=$_.Exception.Message
            # The root is synthetic and the exact PID/hash/start-time must still match.
            # Cleanup never upgrades a failed acceptance result to pass.
            try {
            $owned=Get-CimInstance Win32_Process -Filter "ProcessId=$($launch.pid)"
            if ($owned) {
                if ([IO.Path]::GetFullPath($owned.ExecutablePath) -ne [IO.Path]::GetFullPath($launch.executable) -or
                    (Get-FileHash -LiteralPath $launch.executable -Algorithm SHA256).Hash -cne $ExpectedSha256 -or
                    [Math]::Abs(((Get-Process -Id $launch.pid).StartTime.ToUniversalTime()-([datetime]$launch.processStartedAt).ToUniversalTime()).TotalMilliseconds) -gt 20) {
                    $result.cleanupIdentityMismatch=$true
                } else {
                    Stop-Process -Id $launch.pid -Force
                    $result.forcedCleanup=$true
                    $result.pass=$false
                }
            }
            } catch {$result.forcedCleanupError=$_.Exception.Message}
        }
    }
    $result.endedUtc=[datetime]::UtcNow.ToString('o')
    $result | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $evidence 'hidden-focus-native.json') -Encoding utf8
}
if (!$result.pass) {throw $result.failure}
Write-Output 'Hidden initial/cold focus samples passed; both processes closed normally.'
