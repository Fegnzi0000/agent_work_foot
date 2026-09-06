$ErrorActionPreference = 'Stop'
$secrets = Import-Clixml 'D:\DevTOOLS\ai-meal-private\secrets.clixml'
$info = [Diagnostics.ProcessStartInfo]::new()
$info.FileName = 'D:\DevTOOLS\mysql-8.4.11-winx64\bin\mysqladmin.exe'
$info.Arguments = '--host=127.0.0.1 --port=3307 --user=root shutdown'
$info.UseShellExecute = $false
$info.CreateNoWindow = $true
$info.EnvironmentVariables['MYSQL_PWD'] = $secrets.Root.GetNetworkCredential().Password
$process = [Diagnostics.Process]::Start($info)
$process.WaitForExit()
if ($process.ExitCode -ne 0) { throw 'MySQL shutdown failed.' }
Write-Output 'Project MySQL stopped cleanly. Database files remain saved on D:.'
