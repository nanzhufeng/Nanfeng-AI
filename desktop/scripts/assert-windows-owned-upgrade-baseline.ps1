param(
    [Parameter(Mandatory)][string]$InstallDir,
    [Parameter(Mandatory)][string]$Root,
    [Parameter(Mandatory)][string]$ExeSha256
)
$ErrorActionPreference = 'Stop'
$install = [IO.Path]::GetFullPath($InstallDir).TrimEnd('\')
$data = [IO.Path]::GetFullPath($Root).TrimEnd('\')
if ($install -ne 'D:\CodexTemp\nfai-installed-acceptance.7d2cb30e-9606-4d88-bd91-bbc0fec53c92' -or
    $data -ne 'D:\CodexTemp\nanfeng-ai-windows-acceptance.00afec83-701e-4004-b210-18e974d9163b') {
    throw 'Only the previously owned synthetic installation is authorized.'
}
foreach ($directory in @($install, $data)) {
    if ((Get-Item -LiteralPath $directory).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Reparse baseline rejected.' }
}
if ($ExeSha256 -ceq '302923917E23A643C287DEF875CD895267C97C7AFD1D9E5E83C46FDA1E1DF796') {
    $schema = 52
} elseif ($ExeSha256 -ceq 'DF51383345FFCF490ED7C5301E79234B6424597A25510E9AF117DAF291C40BFC') {
    $repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    $proof = Join-Path $repo 'docs\review\20261004-owned-upgrade-natural-24h-1791121761758\outcome.json'
    if ((Get-FileHash -LiteralPath $proof).Hash -ne '351DCDEF5AB69095103A7E088ECC672E51B95688B96D0A3D718F44DBF4AC0AD3') { throw 'Prior installed upgrade proof changed.' }
    $outcome = Get-Content -LiteralPath $proof -Raw | ConvertFrom-Json
    if (-not $outcome.installed -or -not $outcome.nativeVerified -or -not $outcome.dataUnchangedBeforeStartup -or
        $outcome.installerExitCode -ne 0 -or $outcome.actualInstalledExeSha256 -cne $ExeSha256) { throw 'Prior installed upgrade was not verified.' }
    $schema = 56
} elseif ($ExeSha256 -ceq 'EE82463C303E91CA2C0AA23698772D004B841EF3E66A32449BC0F596C2DFAF35') {
    $repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    $proof = Join-Path $repo 'docs/review/20261005-chat-submission-claim/b7-owned-upgrade-execution/outcome.json'
    if ((Get-FileHash -LiteralPath $proof).Hash -ne '85D1E4358A28B2C8A99497D05D93AB879B1905A279851EFFCDBE699A1C3543A6') { throw 'Current isolated installed upgrade proof changed.' }
    $outcome = Get-Content -LiteralPath $proof -Raw | ConvertFrom-Json
    if (-not $outcome.installed -or -not $outcome.nativeVerified -or -not $outcome.dataUnchangedBeforeStartup -or
        $outcome.installerExitCode -ne 0 -or $outcome.actualInstalledExeSha256 -cne $ExeSha256 -or
        $outcome.stage -ne 'verified-scoped-upgrade' -or $outcome.stability24hAccepted) { throw 'Current isolated upgrade proof or qualification differs.' }
    $schema = 56
} elseif ($ExeSha256 -ceq '48D62E9925FAA10B4E3756FB28110785007B12F142F9837AE0D355C63A47154D') {
    $repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    $proof = Join-Path $repo 'docs/review/20261005-chat-submission-claim/notification-owned-upgrade-execution/outcome.json'
    if ((Get-FileHash -LiteralPath $proof).Hash -ne '9BB115AB046927379C8751E1CEDEFE71823B51A082D9600AECDE30A5B2F54C02') { throw 'Current notification candidate installed proof changed.' }
    $outcome = Get-Content -LiteralPath $proof -Raw | ConvertFrom-Json
    if (-not $outcome.installed -or -not $outcome.nativeVerified -or -not $outcome.dataUnchangedBeforeStartup -or
        $outcome.installerExitCode -ne 0 -or $outcome.actualInstalledExeSha256 -cne $ExeSha256 -or
        $outcome.stage -ne 'verified-scoped-upgrade' -or $outcome.stability24hAccepted) { throw 'Current development installation proof or qualification differs.' }
    $schema = 56
} elseif ($ExeSha256 -ceq '6B4133EB211F3432F1C6EC0D02DA8B3A20B214069A198CED99E7BAF94F76F1A5') {
    $repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
    $proof = Join-Path $repo 'docs/review/20261005-chat-sync-owner/owned-upgrade-execution/outcome.json'
    if ((Get-FileHash -LiteralPath $proof).Hash -cne '5DBFA52E693F524B7FCC2493F3B35247915BB3A81B52C3382BF39ABB75D3C1EA') { throw 'Current installed upgrade proof changed.' }
    $outcome = Get-Content -LiteralPath $proof -Raw | ConvertFrom-Json
    if (-not $outcome.installed -or -not $outcome.nativeVerified -or -not $outcome.dataUnchangedBeforeStartup -or $outcome.installerExitCode -ne 0 -or $outcome.actualInstalledExeSha256 -cne $ExeSha256) { throw 'Current owned installation was not verified.' }
    $schema = 56
} else { throw 'Unrecognized owned installation baseline.' }
if ((Get-FileHash -LiteralPath (Join-Path $install 'nanfeng-ai-desktop-spike.exe')).Hash -cne $ExeSha256) { throw 'Installed EXE differs from its baseline.' }
@{ sourceSchema = $schema; exeSha256 = $ExeSha256 } | ConvertTo-Json -Compress
