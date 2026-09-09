$ErrorActionPreference = 'Stop'
$backendDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\backend'))
$java = 'D:\DevTOOLS\jdk-25.0.4.1+1\bin\java.exe'
function Get-NetTCPConnection { param($State,$LocalPort,$ErrorAction); return [pscustomobject]@{OwningProcess=12345} }
function Get-CimInstance { param($ClassName,$Filter); return $global:backendProcessTestFixture }
function Stop-Process { param($Id,$ErrorAction); throw 'TEST_VERIFIED_STOP' }
function Test-Case([string]$Name,[string]$Command,[bool]$Expected,[string]$Executable=$java) {
    $global:backendProcessTestFixture = [pscustomobject]@{ CommandLine=$Command; ExecutablePath=$Executable }
    try { & "$PSScriptRoot\restart-backend.ps1"; throw 'Unexpected completion' }
    catch {
        $stopped = $_.Exception.Message -eq 'TEST_VERIFIED_STOP'
        if ($stopped -ne $Expected) { throw "$Name failed: $($_.Exception.Message)" }
        if (!$Expected -and $_.Exception.Message -notlike 'Port 8080 does not belong*') { throw }
    }
    Write-Output "PASS: $Name"
}
$temp = [IO.Path]::GetTempFileName()
try {
    $classpath = '"' + ($backendDir + '\target\classes;D:\DevTOOLS\maven-repository\example.jar').Replace('\','\\') + '"'
    [IO.File]::WriteAllText($temp,$classpath,[Text.UTF8Encoding]::new($false))
    Test-Case 'Maven UTF-8 argument file' "$java -cp @`"$temp`" com.hyf.agent_work_foot.AgentWorkFootApplication" $true
    [IO.File]::WriteAllText($temp,$classpath,[Text.Encoding]::GetEncoding(936))
    Test-Case 'Maven Windows Chinese argument file' "$java -cp @$temp com.hyf.agent_work_foot.AgentWorkFootApplication" $true
    Test-Case 'Direct classpath' "$java -cp `"$backendDir\target\classes;D:\library.jar`" com.hyf.agent_work_foot.AgentWorkFootApplication" $true
    [IO.File]::WriteAllText($temp,'"D:\another-project\backend\target\classes"')
    Test-Case 'Another checkout is protected' "$java -cp @$temp com.hyf.agent_work_foot.AgentWorkFootApplication" $false
    Test-Case 'Unrelated main class is protected' "$java -cp `"$backendDir\target\classes`" other.Application" $false
    Test-Case 'Non-Java executable is protected' "other.exe -cp `"$backendDir\target\classes`" com.hyf.agent_work_foot.AgentWorkFootApplication" $false 'C:\other.exe'
} finally { [IO.File]::Delete($temp); Remove-Variable backendProcessTestFixture -Scope Global -ErrorAction SilentlyContinue }
Write-Output 'All checks used synthetic processes. No real process was stopped.'
