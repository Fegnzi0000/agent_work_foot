param([string]$ComputerIp)
$ErrorActionPreference = 'Stop'
if (!$ComputerIp) {
    $interfaces = Get-NetAdapter -Physical | Where-Object Status -eq 'Up'
    $addresses = @($interfaces | ForEach-Object {
        Get-NetIPAddress -InterfaceIndex $_.ifIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue |
            Where-Object { $_.AddressState -eq 'Preferred' -and $_.IPAddress -notmatch '^(127\.|169\.254\.)' }
    })
    $wireless = @($addresses | Where-Object InterfaceAlias -match '^(WLAN|Wi-Fi|无线)')
    if ($wireless.Count -eq 1) { $addresses = $wireless }
    if ($addresses.Count -ne 1) { throw 'Multiple or no physical IPv4 addresses found. Pass -ComputerIp with the address reachable from your phone.' }
    $ComputerIp = $addresses[0].IPAddress
}
$parsed = $null
if (![Net.IPAddress]::TryParse($ComputerIp, [ref]$parsed) -or $parsed.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork) { throw 'A valid IPv4 address is required.' }
if (!(Get-NetIPAddress -AddressFamily IPv4 | Where-Object IPAddress -eq $ComputerIp)) { throw 'The selected IP does not belong to this computer.' }
$userDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\frontend\user'))
$envFile = Join-Path $userDir '.env.local'
$line = "TARO_APP_API_BASE_URL=http://${ComputerIp}:8080/api/v1"
$content = if (Test-Path $envFile) { [IO.File]::ReadAllText($envFile) } else { '' }
if ($content -match '(?m)^TARO_APP_API_BASE_URL=.*$') { $content = [regex]::Replace($content, '(?m)^TARO_APP_API_BASE_URL=.*$', $line) }
else { $content = $content.TrimEnd() + "`n$line`n" }
[IO.File]::WriteAllText($envFile, $content, [Text.UTF8Encoding]::new($false))
Write-Output "Mini-program API: http://${ComputerIp}:8080/api/v1"
Push-Location $userDir
try {
    & npm.cmd run build:weapp
    if ($LASTEXITCODE -ne 0) { throw 'Mini-program build failed.' }
} finally { Pop-Location }
Write-Output "Phone connectivity check: http://${ComputerIp}:8080/actuator/health"
