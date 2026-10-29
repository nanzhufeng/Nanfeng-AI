param([string]$RootParent='D:\CodexTemp', [ValidateSet('CHAT','WORK')][string]$DataArea='WORK', [switch]$RejectFailureWrite)
$ErrorActionPreference='Stop'
$desktopRoot=Split-Path -Parent $PSScriptRoot
$receipt=Get-Content -LiteralPath (Join-Path $desktopRoot 'output/windows-acceptance/current.json') -Raw | ConvertFrom-Json
if($receipt.identifier -ne 'com.nanzhufeng.ai.desktop.windowsacceptance'){throw '独立候选身份无效'}
$expected=[IO.Path]::GetFullPath((Join-Path $desktopRoot ("output/windows-acceptance-binaries/{0}/nanfeng-ai-desktop-spike.exe" -f $receipt.sha256)))
$launch=$null
$root=''
$port=0
$pids=[Collections.Generic.List[int]]::new()
function Start-Candidate([bool]$reopen){
    $listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
    $listener.Start()
    try{$script:port=$listener.LocalEndpoint.Port}finally{$listener.Stop()}
    $arguments=@{RootParent=$RootParent;StartupMode='normal';DebugPort=$port}
    if($reopen){$arguments.Root=$root}
    $script:launch=& (Join-Path $PSScriptRoot 'start-windows-acceptance.ps1') @arguments | ConvertFrom-Json
    if($launch.sha256 -ne $receipt.sha256 -or [IO.Path]::GetFullPath($launch.executable) -ne $expected){throw '启动身份不符'}
    $script:root=$launch.root
    $pids.Add([int]$launch.pid)
}
function Stop-Candidate{
    if(-not $launch){return}
    $actual=Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $launch.pid)
    if($actual){
        if([IO.Path]::GetFullPath($actual.ExecutablePath) -ne $expected -or (Get-FileHash -LiteralPath $expected -Algorithm SHA256).Hash -ne $receipt.sha256){throw '清理PID身份不符'}
        Stop-Process -Id $launch.pid -Force -ErrorAction Stop
    }
    $script:launch=$null
}
function Invoke-Native([string]$command){
    $expression='window.__TAURI__.core.invoke('+ (ConvertTo-Json $command -Compress)+',{}, {headers:{"x-nanfeng-data-area":"'+$DataArea+'"}}).catch(error=>({__error:String(error)}))'
    $raw=$expression | & node --max-old-space-size=2048 (Join-Path $PSScriptRoot 'windows-acceptance-cdp-eval.mjs') $port 2>$null
    if($LASTEXITCODE -ne 0){throw '原生回读失败'}
    $value=$raw | ConvertFrom-Json
    if($value.__error){throw $value.__error}
    return $value
}
try{
    Start-Candidate $false
    Start-Sleep -Seconds 3
    $workspace=Invoke-Native 'ensure_desktop_local_workspace'
    if(-not $workspace.summary.id){throw '合成workspace未创建'}
    $directOutput=& (Join-Path $PSScriptRoot 'verify-windows-background-cycle-direct.ps1') -Root $root -ForegroundPid $launch.pid -DataArea $DataArea -RejectFailureWrite:$RejectFailureWrite
    $script:launch=$null
    $direct=($directOutput | Select-Object -Last 1) | ConvertFrom-Json
    if($direct.candidateSha256 -ne $receipt.sha256 -or $direct.runCount -ne 1){throw '后台生产周期证据不符'}
    Start-Candidate $true
    Start-Sleep -Seconds 3
    $projection=Invoke-Native 'read_desktop_reminders'
    $plan=@($projection.plans | Where-Object planId -EQ 'synthetic-background-plan')
    $expectedCode=if($RejectFailureWrite){'PROCESS_INTERRUPTED'}else{'SERVICE_DISABLED'}
    if($plan.Count -ne 1 -or $plan[0].lastSafeErrorCode -ne $expectedCode -or $plan[0].latestResult){throw '冷启失败结果投影不符'}
    if($RejectFailureWrite -and $plan[0].status -ne 'UNKNOWN'){throw '冷启未恢复UNKNOWN'}
    Stop-Candidate
    $facts=& python (Join-Path $PSScriptRoot 'read-windows-acceptance-background-facts.py') $root | ConvertFrom-Json
    $peer=if($DataArea -eq 'WORK'){'CHAT'}else{'WORK'}
    if($LASTEXITCODE -ne 0 -or $facts.$DataArea.runCount -ne 1 -or $facts.$peer.runCount -ne 0){throw '双区后台运行隔离不符'}
    [pscustomobject]@{candidateSha256=$receipt.sha256;root=$root;dataArea=$DataArea;direct=$direct;coldFailureReadback=$true;facts=$facts;foregroundPids=@($pids)} | ConvertTo-Json -Depth 8 -Compress
}finally{Stop-Candidate}
