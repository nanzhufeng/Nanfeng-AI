param([Parameter(Mandatory=$true)][string]$Root)
$ErrorActionPreference = 'Stop'
$Root = [IO.Path]::GetFullPath($Root)
$rootItem = Get-Item -LiteralPath $Root -Force -ErrorAction Stop
if (-not $rootItem.PSIsContainer -or ($rootItem.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
    throw 'Windows 媒体工具目录无效。'
}
$manifestPath = Join-Path $Root 'runtime-manifest.json'
$manifestItem = Get-Item -LiteralPath $manifestPath -Force -ErrorAction Stop
if (($manifestItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -or $manifestItem.Length -gt 65536) {
    throw 'Windows 媒体工具清单无效。'
}
$manifest = [IO.File]::ReadAllText($manifestPath) | ConvertFrom-Json
if ($manifest.schemaVersion -ne 1 -or
    $manifest.bundleVersion -ne 'n9.0.1-69-g3e11912860-20260917' -or
    $manifest.target -ne 'x86_64-pc-windows-msvc' -or
    $manifest.license -ne 'LGPL-3.0-or-later' -or
    $manifest.archiveSha256 -ne '3fa84213e8c38fae99cdab620cb47f8cebfef6c550d5e950700948133edce5c6') {
    throw 'Windows 媒体工具清单版本、架构或来源不匹配。'
}
$expected = @{
    'avcodec-63.dll' = @{ bytes=90353664L; sha256='76c90b2146a6c26d195273bec51f347ed35340cc3bdf79f48fec7d35270deb2d' }
    'avdevice-63.dll' = @{ bytes=4047872L; sha256='fcfde53726e6e5e45def2443e37609d72b054083475b2bfc8ba360d2a1e6ce7d' }
    'avfilter-12.dll' = @{ bytes=30806016L; sha256='27455514b1ceab60f968532108edefb26716b7f0072beb855cf006c11c23304f' }
    'avformat-63.dll' = @{ bytes=22668800L; sha256='2cd3c7fa0e037bb9d5787c129d4a71d351d4d073fee4b91edb63df6fc4aafee1' }
    'avutil-61.dll' = @{ bytes=3014144L; sha256='198c72089402cd83ddd5c66fa50226ad4f716eacc43d10ef7c6a81e5af3a4f05' }
    'ffmpeg.exe' = @{ bytes=538112L; sha256='2deb3e0609ef34435248c0f46c8e597638924e1390028c124a154302186c1098' }
    'ffprobe.exe' = @{ bytes=228864L; sha256='437a508fcd30fb4d8a02ca91e85af64278e3217c208899a0c02c8a3cc1fa0950' }
    'swresample-7.dll' = @{ bytes=733696L; sha256='85846e143863936ec50ca5a64e27d6ca4b5a4e035efe521b1e9e2fdfe80e327f' }
    'swscale-10.dll' = @{ bytes=2330624L; sha256='f56f60a3b4299d16be4b7682683fac39d5686a42aa88bc93583abc9cd7e803cf' }
}
$actual = @($manifest.files | ForEach-Object { [string]$_.name } | Sort-Object -Unique)
if ($actual.Count -ne $expected.Count -or (Compare-Object ($expected.Keys | Sort-Object) $actual)) {
    throw 'Windows 媒体工具文件清单不完整。'
}
foreach ($file in $manifest.files) {
    $name = [string]$file.name
    if ($name -notmatch '^[A-Za-z0-9._-]+$') { throw 'Windows 媒体工具文件名无效。' }
    $spec = $expected[$name]
    if (-not $spec -or [long]$file.bytes -ne $spec.bytes -or ([string]$file.sha256).ToLowerInvariant() -ne $spec.sha256) {
        throw "Windows 媒体工具固定清单不匹配：$name"
    }
    $path = Join-Path (Join-Path $Root 'bin') $name
    $item = Get-Item -LiteralPath $path -Force -ErrorAction Stop
    if ($item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -or $item.Length -ne $spec.bytes) {
        throw "Windows 媒体工具文件无效：$name"
    }
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $spec.sha256) {
        throw "Windows 媒体工具文件校验失败：$name"
    }
}
foreach ($notice in @('LICENSE-FFMPEG-LGPL-3.0.txt','SOURCE.txt')) {
    $item = Get-Item -LiteralPath (Join-Path $Root $notice) -Force -ErrorAction Stop
    if ($item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Windows 媒体工具许可信息无效。' }
}
if ((Get-Item -LiteralPath (Join-Path $Root 'LICENSE-FFMPEG-LGPL-3.0.txt')).Length -ne 7651L -or
    (Get-FileHash -LiteralPath (Join-Path $Root 'LICENSE-FFMPEG-LGPL-3.0.txt') -Algorithm SHA256).Hash.ToLowerInvariant() -ne 'da7eabb7bafdf7d3ae5e9f223aa5bdc1eece45ac569dc21b3b037520b4464768') {
    throw 'Windows 媒体工具许可证校验失败。'
}
if ((Get-Item -LiteralPath (Join-Path $Root 'SOURCE.txt')).Length -ne 463L -or
    (Get-FileHash -LiteralPath (Join-Path $Root 'SOURCE.txt') -Algorithm SHA256).Hash.ToLowerInvariant() -ne '4d98c6ce49419a9c249a97afeda21d7d217a8d15867443eff9a45113f9ac1541') {
    throw 'Windows 媒体工具来源说明校验失败。'
}
[pscustomobject]@{ root=$Root; version=$manifest.bundleVersion; archiveSha256=$manifest.archiveSha256; files=$expected.Count }
