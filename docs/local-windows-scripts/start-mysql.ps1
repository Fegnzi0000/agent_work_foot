$ErrorActionPreference = 'Stop'
$private = 'D:\DevTOOLS\ai-meal-private'
$mysqld = 'D:\DevTOOLS\mysql-8.4.11-winx64\bin\mysqld.exe'
if (!(Test-Path "$private\mysql.ini") -or !(Test-Path 'D:\DevTOOLS\mysql-data\auto.cnf')) { throw 'Run setup-database.ps1 once before starting MySQL.' }
$listener = Get-NetTCPConnection -State Listen -LocalPort 3307 -ErrorAction SilentlyContinue
if ($listener) {
    $owner = Get-Process -Id $listener[0].OwningProcess
    if ($owner.Path -ne $mysqld) { throw 'Port 3307 belongs to another program.' }
    Write-Output 'Project MySQL is already running.'
    return
}
$process = Start-Process -FilePath $mysqld -ArgumentList "--defaults-file=$private\mysql.ini" -WindowStyle Hidden -PassThru
for ($attempt = 0; $attempt -lt 45; $attempt++) {
    if ($process.HasExited) { throw 'MySQL exited; inspect D:\DevTOOLS\ai-meal-private\mysql.err.' }
    if (Get-NetTCPConnection -State Listen -LocalPort 3307 -ErrorAction SilentlyContinue) { Write-Output 'MySQL is ready on 127.0.0.1:3307.'; return }
    Start-Sleep -Seconds 1
}
throw 'MySQL startup timed out.'
