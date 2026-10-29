param([switch]$ProductionCandidate,[switch]$ValidateOnly)
$ErrorActionPreference = 'Stop'
$desktopRoot = Split-Path -Parent $PSScriptRoot
$candidateDirectory = Join-Path $desktopRoot $(if($ProductionCandidate){'output/windows-production-candidate/windows-user'}else{'output/windows-user'})
$receiptPath = Join-Path $candidateDirectory 'current.json'
if (-not (Test-Path -LiteralPath $receiptPath -PathType Leaf)) {
    throw 'Build the Windows daily-use candidate first: node desktop/scripts/build-windows.mjs --debug --no-bundle --target=x86_64-pc-windows-msvc'
}
$receipt = [IO.File]::ReadAllText($receiptPath) | ConvertFrom-Json
if ($receipt.identifier -ne 'com.nanzhufeng.ai.desktop' -or $receipt.sha256 -notmatch '^[A-F0-9]{64}$') {
    throw 'Invalid daily-use build receipt. Acceptance builds cannot use this launcher.'
}
if ($ProductionCandidate -and ($receipt.build -ne 'windows-x64-release' -or $receipt.buildProfile -ne 'release')) {
    throw 'Production candidate requires a Release receipt.'
}
$executable = Join-Path (Join-Path $candidateDirectory $receipt.sha256) 'NanfengAI.exe'
if ([IO.Path]::GetFullPath($receipt.executable) -ne [IO.Path]::GetFullPath($executable) -or
    (Get-FileHash -LiteralPath $executable -Algorithm SHA256).Hash -ne $receipt.sha256) {
    throw 'Daily-use executable does not match its build receipt.'
}
$mediaTools = Join-Path (Split-Path -Parent $executable) 'media-tools/windows-x86_64'
if (-not $receipt.mediaTools -or [IO.Path]::GetFullPath([string]$receipt.mediaTools) -ne [IO.Path]::GetFullPath($mediaTools)) {
    throw 'Daily-use media tools do not match the build receipt.'
}
& (Join-Path $PSScriptRoot 'verify-windows-media-tools.ps1') -Root $mediaTools | Out-Null
if($ValidateOnly){
    [pscustomobject]@{validated=$true;launched=$false;productionCandidate=[bool]$ProductionCandidate;identifier=$receipt.identifier;executable=$executable;mediaTools=$mediaTools;sha256=$receipt.sha256}|ConvertTo-Json -Compress
    return
}
# Stable per-user profile and the native app-data/config owners survive every
# binary update. Test flags are removed only from this child launch environment.
$profile = Join-Path $env:LOCALAPPDATA 'com.nanzhufeng.ai.desktop/webview2'
New-Item -ItemType Directory -Path $profile -Force | Out-Null
$variables = @{
    WEBVIEW2_USER_DATA_FOLDER = $profile
    WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS = $null
}
Get-ChildItem Env: | Where-Object Name -Like 'NANFENG_AI_*' | ForEach-Object { $variables[$_.Name] = $null }
$previous = @{}
foreach ($key in $variables.Keys) { $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
try {
    foreach ($key in $variables.Keys) { [Environment]::SetEnvironmentVariable($key, $variables[$key], 'Process') }
    $process = Start-Process -FilePath $executable -WindowStyle Hidden -PassThru
    [pscustomobject]@{ pid=$process.Id; identifier=$receipt.identifier; executable=$executable; mediaTools=$mediaTools; profile=$profile; sha256=$receipt.sha256 } | ConvertTo-Json -Compress
} finally {
    foreach ($key in $previous.Keys) { [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process') }
}
