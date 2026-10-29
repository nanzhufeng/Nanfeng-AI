param([Parameter(Mandatory)][string]$EvidenceDirectory)
$ErrorActionPreference = 'Stop'
$evidence = [IO.Path]::GetFullPath($EvidenceDirectory)
if (Test-Path -LiteralPath $evidence) { throw 'Preserve existing evidence; use a new directory.' }
New-Item -ItemType Directory -Path $evidence | Out-Null
$root = Join-Path 'D:\CodexTemp' ('nfai-upgrade-source-fixture.'+[guid]::NewGuid())
New-Item -ItemType Directory -Path $root | Out-Null
$files = @('desktop/package.json', 'desktop/src-tauri/Cargo.toml',
    'desktop/src-tauri/Cargo.lock', 'desktop/src-tauri/capabilities/default.json',
    'desktop/src-tauri/permissions/default.toml', 'desktop/src/app.mjs',
    'desktop/src-tauri/src/lib.rs', 'desktop/src-tauri/build.rs',
    'desktop/src-tauri/tauri.windows-acceptance.conf.json')
$bindings = @{}
foreach ($relative in $files) {
    $file = Join-Path $root $relative
    New-Item -ItemType Directory -Path (Split-Path -Parent $file) -Force | Out-Null
    [IO.File]::WriteAllText($file, 'SYNTHETIC_BUILD_INPUT '+$relative)
    $bindings[$relative] = (Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant()
}
$helper = Join-Path $PSScriptRoot 'assert-windows-owned-package-source.ps1'
$results = [Collections.Generic.List[object]]::new()
function Check-Source([string]$name, $hashes, [bool]$expected, $coverage = @{}, [int]$expectedCount = $files.Count) {
    $snapshot = Join-Path $evidence ($name+'.snapshot.json')
    $manifest = @{sourceHashes=$hashes}
    foreach ($key in $coverage.Keys) { $manifest[$key]=$coverage[$key] }
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $snapshot -Encoding utf8
    $stdout = Join-Path $evidence ($name+'.stdout.txt')
    $stderr = Join-Path $evidence ($name+'.stderr.txt')
    & pwsh -NoProfile -File $helper -RepoRoot $root -SourceSnapshot $snapshot 1> $stdout 2> $stderr
    $code = $LASTEXITCODE
    if (($code -eq 0) -ne $expected) { throw "Unexpected source gate result: $name / $code" }
    if ($expected) {
        $result = Get-Content -LiteralPath $stdout -Raw | ConvertFrom-Json
        if (-not $result.sourceMatched -or $result.productBindingsChecked -ne $expectedCount) {
            throw 'Successful gate must verify every synthetic product binding.'
        }
    }
    $results.Add(@{name=$name; exitCode=$code; expectedMatched=$expected})
}
Check-Source 'exact' $bindings $true
foreach ($relative in @('desktop/src-tauri/Cargo.toml','desktop/src-tauri/Cargo.lock',
    'desktop/src-tauri/permissions/default.toml','desktop/src-tauri/capabilities/default.json',
    'desktop/src-tauri/build.rs','desktop/src-tauri/tauri.windows-acceptance.conf.json',
    'desktop/src/app.mjs','desktop/src-tauri/src/lib.rs')) {
    $file=Join-Path $root $relative
    $original=[IO.File]::ReadAllText($file)
    try {
        [IO.File]::WriteAllText($file,$original+' DRIFT')
        Check-Source ('drift-'+$relative.Replace('/','-').Replace('.','-')) $bindings $false
    } finally { [IO.File]::WriteAllText($file,$original) }
}
$missing=$bindings.Clone();$missing.Remove('desktop/src-tauri/Cargo.toml')
Check-Source 'missing-required' $missing $false
$escape=$bindings.Clone();$escape['../outside']='a'*64
Check-Source 'escaped-path' $escape $false
$conflict=$bindings.Clone();$conflict['desktop\src-tauri\Cargo.toml']='a'*64
Check-Source 'conflicting-separators' $conflict $false
$helperOnly=$bindings.Clone();$helperOnly['desktop/scripts/example.mjs']='b'*64
$helperOnly['desktop/tests/example.test.mjs']='c'*64
Check-Source 'unbundled-helper-change' $helperOnly $true
$buildScript = Join-Path $root 'desktop/scripts/build.mjs'
New-Item -ItemType Directory -Path (Split-Path -Parent $buildScript) -Force | Out-Null
[IO.File]::WriteAllText($buildScript,'SYNTHETIC_FRONTEND_BUILD')
$complete = $bindings.Clone()
$complete['desktop/scripts/build.mjs']=(Get-FileHash -LiteralPath $buildScript).Hash.ToLowerInvariant()
$coverage=@{repositoryBuildInputs=@($complete.Keys);repositoryBuildInputTrees=@('desktop/src')}
Check-Source 'explicit-build-script-exact' $complete $true $coverage ($files.Count+1)
[IO.File]::WriteAllText($buildScript,'SYNTHETIC_FRONTEND_BUILD_DRIFT')
Check-Source 'explicit-build-script-drift' $complete $false $coverage
[IO.File]::WriteAllText($buildScript,'SYNTHETIC_FRONTEND_BUILD')
[IO.File]::WriteAllText((Join-Path $root 'desktop/src/added.mjs'),'SYNTHETIC_ADDED_BUILD_INPUT')
Check-Source 'input-tree-added-file' $complete $false $coverage
$invalidTree=@{repositoryBuildInputs=@($complete.Keys);repositoryBuildInputTrees=@('desktop/src-tauri/target')}
Check-Source 'excluded-build-cache-tree' $complete $false $invalidTree
$absent=@{optionalRepositoryInputsAbsent=@('.cargo/config.toml')}
Check-Source 'optional-build-config-absent' $bindings $true $absent
New-Item -ItemType Directory -Path (Join-Path $root '.cargo') -Force | Out-Null
[IO.File]::WriteAllText((Join-Path $root '.cargo/config.toml'),'SYNTHETIC_ADDED_CONFIGURATION')
Check-Source 'optional-build-config-added' $bindings $false $absent
Check-Source 'unverifiable-build-commit' $bindings $false @{gitCommit=('0'*40)}
@{pass=$true; cases=$results; fixtureRoot=$root; installationExecuted=$false} |
    ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $evidence 'result.json') -Encoding utf8
@{pass=$true; cases=$results.Count; installationExecuted=$false} | ConvertTo-Json -Compress
