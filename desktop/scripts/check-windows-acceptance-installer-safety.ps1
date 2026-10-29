param([string]$EvidencePath)
$ErrorActionPreference = 'Stop'
if (-not $IsWindows) { throw 'Windows installer safety check requires Windows.' }
# The generated NSIS CheckIfAppIsRunning macro searches by current-user basename,
# so checking only the target installation directory cannot protect other roots.
$currentSid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$conflicts = @()
foreach ($process in @(Get-CimInstance Win32_Process -Filter "Name='nanfeng-ai-desktop-spike.exe'")) {
    $owner = Invoke-CimMethod -InputObject $process -MethodName GetOwnerSid
    if ($owner.ReturnValue -ne 0) { throw 'Cannot verify process owner; installer must not run.' }
    if ($owner.Sid -eq $currentSid) {
        $conflicts += [pscustomobject]@{pid=$process.ProcessId; executable=$process.ExecutablePath}
    }
}
$result = [pscustomobject]@{
    checkedAt=[DateTime]::UtcNow.ToString('o'); safeToInstall=($conflicts.Count -eq 0)
    currentUserSameBasenameCount=$conflicts.Count; conflictingProcesses=$conflicts
    action='read-only'; installationStarted=$false
}
if ($EvidencePath) { $result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $EvidencePath -Encoding utf8 }
$result | ConvertTo-Json -Depth 5
if ($conflicts.Count -ne 0) { exit 2 }
