param(
    [Parameter(Mandatory)][string]$RepoRoot,
    [Parameter(Mandatory)][string]$SourceSnapshot
)
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath($RepoRoot).TrimEnd('\')
$snapshot = Get-Content -LiteralPath $SourceSnapshot -Raw | ConvertFrom-Json
if ($snapshot.gitCommit) {
    $commit = & git -C $repo rev-parse --verify HEAD 2> $null
    if ($LASTEXITCODE -ne 0 -or $commit -ne $snapshot.gitCommit) {
        throw 'Build source commit changed or cannot be verified.'
    }
}
foreach ($missing in @($snapshot.optionalRepositoryInputsAbsent)) {
    if (-not $missing) { continue }
    if ($missing -notin @('.cargo/config.toml','desktop/.cargo/config.toml','desktop/src-tauri/.cargo/config.toml')) {
        throw 'Unrecognized optional repository build input.'
    }
    if (Test-Path -LiteralPath (Join-Path $repo $missing)) {
        throw 'A previously absent repository build configuration was added.'
    }
}
$required = @('desktop/package.json', 'desktop/src-tauri/Cargo.toml',
    'desktop/src-tauri/Cargo.lock', 'desktop/src-tauri/capabilities/default.json',
    'desktop/src-tauri/permissions/default.toml')
$seen = @{}
$checked = 0
foreach ($binding in $snapshot.sourceHashes.PSObject.Properties) {
    $relative = $binding.Name.Replace('\', '/')
    if ($relative.StartsWith('/') -or $relative.Contains(':') -or
        @($relative.Split('/') | Where-Object { $_ -in @('', '.', '..') }).Count) {
        throw 'Invalid source snapshot path.'
    }
    $product = $relative.StartsWith('desktop/src/') -or
        ($relative.StartsWith('desktop/src-tauri/') -and
         -not $relative.StartsWith('desktop/src-tauri/tests/')) -or
        $relative -eq 'desktop/package.json' -or
        $relative -in @($snapshot.repositoryBuildInputs)
    if (-not $product) { continue }
    if ([string]$binding.Value -cnotmatch '^[a-f0-9]{64}$') { throw 'Invalid source hash.' }
    if ($seen.ContainsKey($relative)) {
        if ($seen[$relative] -ne $binding.Value) { throw 'Conflicting source snapshot bindings.' }
        continue
    }
    $seen[$relative] = $binding.Value
    $path = [IO.Path]::GetFullPath((Join-Path $repo $relative))
    if (-not $path.StartsWith($repo+'\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Source path escaped the repository.'
    }
    if ((Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::ReparsePoint) {
        throw 'Reparse product source rejected.'
    }
    if ((Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant() -ne $binding.Value) {
        throw 'Product source or build configuration changed; rebuild and prepare a new package.'
    }
    $checked++
}
foreach ($relative in $required) {
    if (-not $seen.ContainsKey($relative)) { throw 'Required build source binding is missing.' }
}
if (-not @($seen.Keys | Where-Object { $_.StartsWith('desktop/src/') }).Count -or
    -not @($seen.Keys | Where-Object { $_.StartsWith('desktop/src-tauri/src/') }).Count) {
    throw 'Required application source bindings are missing.'
}
foreach ($tree in @($snapshot.repositoryBuildInputTrees)) {
    if (-not $tree) { continue }
    $relative = ([string]$tree).Replace('\','/')
    $allowedTrees = @('desktop/src', 'desktop/dist', 'desktop/src-tauri/src',
        'desktop/src-tauri/vendor', 'desktop/src-tauri/resources', 'desktop/src-tauri/icons',
        'desktop/src-tauri/permissions', 'desktop/src-tauri/capabilities', 'protocol')
    if ($relative -notin $allowedTrees) { throw 'Unrecognized repository build input tree.' }
    $directory = Join-Path $repo $relative
    $entries = @(Get-ChildItem -LiteralPath $directory -Recurse -Force)
    if ((Get-Item -LiteralPath $directory).Attributes -band [IO.FileAttributes]::ReparsePoint -or
        @($entries | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) {
        throw 'Reparse build input tree rejected.'
    }
    $current = @($entries | Where-Object { -not $_.PSIsContainer } | ForEach-Object {
        $_.FullName.Substring($repo.Length+1).Replace('\','/')
    })
    $frozen = @($seen.Keys | Where-Object { $_.StartsWith($relative+'/') })
    if ($current.Count -ne $frozen.Count -or @($current | Where-Object { -not $seen.ContainsKey($_) }).Count) {
        throw 'Repository build input file membership changed; rebuild the package.'
    }
}
@{ productBindingsChecked = $checked; sourceMatched = $true } | ConvertTo-Json -Compress
