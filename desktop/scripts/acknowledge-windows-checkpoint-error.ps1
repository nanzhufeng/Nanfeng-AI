param([Parameter(Mandatory)][int]$ProcessId,[Parameter(Mandatory)][string]$Executable,[Parameter(Mandatory)][string]$Sha256,[ValidateSet('CHAT','WORK')][string]$Area)
$ErrorActionPreference='Stop'
$p=Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId"
if(!$p -or [IO.Path]::GetFullPath($p.ExecutablePath) -ne [IO.Path]::GetFullPath($Executable) -or (Get-FileHash -LiteralPath $Executable -Algorithm SHA256).Hash -ne $Sha256){throw 'candidate identity mismatch'}
Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
$deadline=[datetime]::UtcNow.AddSeconds(10)
$dialog=$null
while([datetime]::UtcNow -lt $deadline){
 $current=Get-Process -Id $ProcessId -ErrorAction Stop
 if($current.MainWindowTitle -eq '南枫 AI · 无法启动' -and $current.MainWindowHandle -ne 0){
  $dialog=[System.Windows.Automation.AutomationElement]::FromHandle($current.MainWindowHandle)
  if($dialog.Current.ProcessId -ne $ProcessId){throw 'dialog PID mismatch'}
 }
 if($dialog){break};Start-Sleep -Milliseconds 200
}
if(!$dialog){throw 'startup recovery dialog absent'}
$children=$dialog.FindAll([System.Windows.Automation.TreeScope]::Descendants,[System.Windows.Automation.Condition]::TrueCondition)
$texts=@();$button=$null
for($i=0;$i -lt $children.Count;$i++){$child=$children.Item($i);$texts+=$child.Current.Name;if($child.Current.Name -eq '确定'){$button=$child}}
$message=$texts -join "`n"
if($message -notlike "*$Area 备份恢复受阻*" -or $message -notlike '*已停止启动*' -or $message -notlike '*保留本机数据*' -or $message -match '[A-Z]:\\' -or !$button){throw 'startup recovery message/button mismatch'}
$title=$dialog.Current.Name
$buttonName=$button.Current.Name
$processHandle=$current.Handle
if($processHandle -eq 0){throw 'candidate process handle unavailable'}
$closeAccepted=$current.CloseMainWindow()
if(!$closeAccepted){throw 'error dialog close rejected'}
if(!$current.WaitForExit(10000)){throw 'candidate did not exit after acknowledgement'}
[pscustomobject]@{pid=$ProcessId;title=$title;area=$Area;message=$message;buttonName=$buttonName;closeAccepted=$closeAccepted;acknowledged=$true;exitCode=$current.ExitCode}|ConvertTo-Json -Compress
