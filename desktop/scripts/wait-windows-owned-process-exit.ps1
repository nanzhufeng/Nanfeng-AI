param(
    [Parameter(Mandatory)][int]$ProcessId,
    [Parameter(Mandatory)][string]$Executable,
    [Parameter(Mandatory)][string]$Sha256,
    [Parameter(Mandatory)][datetime]$StartedAt
)
$ErrorActionPreference = 'Stop'
$expected=[IO.Path]::GetFullPath($Executable)
if ($expected -notmatch '\\windows-acceptance-binaries\\[A-Fa-f0-9]{64}\\nanfeng-ai-desktop-spike\.exe$') { throw 'Not an isolated acceptance executable' }
$identity=Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId"
$process=Get-Process -Id $ProcessId -ErrorAction Stop
try {
    if (!$identity -or [IO.Path]::GetFullPath([string]$identity.ExecutablePath) -ne $expected -or
        (Get-FileHash -LiteralPath $expected -Algorithm SHA256).Hash -ne $Sha256 -or
        [Math]::Abs(($process.StartTime.ToUniversalTime()-$StartedAt.ToUniversalTime()).TotalMilliseconds) -gt 20) {
        throw 'PID identity mismatch'
    }
    $null=$process.Handle
    Write-Output '{"ready":true}'
    [Console]::Out.Flush()
    if (!$process.WaitForExit(60000)) { throw 'Owned process did not exit in the observation window' }
    @{exitCode=$process.ExitCode} | ConvertTo-Json -Compress
} finally { $process.Dispose() }
