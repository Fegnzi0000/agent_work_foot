$ErrorActionPreference = 'Stop'
$private = 'D:\DevTOOLS\ai-meal-private'
$data = 'D:\DevTOOLS\mysql-data'
$mysqld = 'D:\DevTOOLS\mysql-8.4.11-winx64\bin\mysqld.exe'
if (Test-Path "$private\secrets.clixml") { throw 'Local credentials already exist. Use start-mysql.ps1; initialization will not be repeated.' }
if ((Test-Path $data) -and (Get-ChildItem -LiteralPath $data -Force | Select-Object -First 1)) { throw 'Existing data directory is not empty. Refusing to initialize it.' }
if (Get-NetTCPConnection -State Listen -LocalPort 3307 -ErrorAction SilentlyContinue) { throw 'Port 3307 is in use.' }
New-Item -ItemType Directory -Path $private -Force | Out-Null
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls.exe $private /inheritance:r /grant:r "${identity}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not protect local credentials directory.' }
function New-Secret {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
}
$rootPassword = New-Secret
$appPassword = New-Secret
$jwtSecret = New-Secret
[pscustomobject]@{
    Root = [pscredential]::new('root', (ConvertTo-SecureString $rootPassword -AsPlainText -Force))
    Database = [pscredential]::new('ai_meal', (ConvertTo-SecureString $appPassword -AsPlainText -Force))
    Jwt = [pscredential]::new('jwt', (ConvertTo-SecureString $jwtSecret -AsPlainText -Force))
} | Export-Clixml "$private\secrets.clixml"
Copy-Item -LiteralPath "$PSScriptRoot\mysql.ini" -Destination "$private\mysql.ini"
# The generated bootstrap file is protected by the directory ACL and removed after startup.
$sql = "ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';`nCREATE DATABASE agent_work_foot CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;`nCREATE USER 'ai_meal'@'localhost' IDENTIFIED BY '$appPassword';`nGRANT ALL PRIVILEGES ON agent_work_foot.* TO 'ai_meal'@'localhost';`n"
[IO.File]::WriteAllText("$private\bootstrap.sql", $sql, [Text.UTF8Encoding]::new($false))
& $mysqld "--defaults-file=$private\mysql.ini" --initialize-insecure
if ($LASTEXITCODE -ne 0) { throw 'MySQL initialization failed; inspect mysql.err. Existing files are preserved.' }
$process = Start-Process -FilePath $mysqld -ArgumentList @("--defaults-file=$private\mysql.ini", "--init-file=$private\bootstrap.sql") -WindowStyle Hidden -PassThru
for ($attempt = 0; $attempt -lt 45; $attempt++) {
    if ($process.HasExited) { throw 'MySQL exited; inspect mysql.err.' }
    if (Get-NetTCPConnection -State Listen -LocalPort 3307 -ErrorAction SilentlyContinue) {
        Remove-Item -LiteralPath 'D:\DevTOOLS\ai-meal-private\bootstrap.sql'
        Write-Output 'MySQL initialized and listening on 127.0.0.1:3307. Local credentials are encrypted for your Windows account.'
        exit 0
    }
    Start-Sleep -Seconds 1
}
throw 'MySQL startup timed out; existing data and configuration are preserved.'
