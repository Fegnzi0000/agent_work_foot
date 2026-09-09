param([string]$ComputerIp = '127.0.0.1')
$ErrorActionPreference = 'Stop'
$api = "http://${ComputerIp}:8080/api/v1"
$health = Invoke-RestMethod "http://${ComputerIp}:8080/actuator/health" -TimeoutSec 10
if ($health.status -ne 'UP') { throw 'Backend health check failed.' }
try {
    $null = Invoke-RestMethod "$api/auth/login" -Method Post -ContentType application/json -Body '{"email":"test@example.com","password":"Pass_123"}'
    throw 'Legacy email login is unexpectedly available'
} catch {
    if (!$_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne 410) { throw }
}
$accounts = Import-Clixml -LiteralPath 'D:\DevTOOLS\ai-meal-private\demo-accounts.clixml'
$adminBody = @{account='admin';password=$accounts.Admin.GetNetworkCredential().Password} | ConvertTo-Json -Compress
$admin = (Invoke-RestMethod "$api/admin/auth/login" -Method Post -ContentType application/json -Body $adminBody).data
$headers = @{Authorization="Bearer $($admin.accessToken)"}
$null = Invoke-RestMethod "$api/admin/dashboard" -Headers $headers
$users = (Invoke-RestMethod "$api/admin/users?size=100" -Headers $headers).data
if ($null -eq $users.items -or $null -eq $users.totalElements) { throw 'Admin page envelope is invalid' }
$options = Invoke-WebRequest "$api/admin/dashboard" -Method Options -Headers @{Origin='http://localhost:5173';'Access-Control-Request-Method'='GET';'Access-Control-Request-Headers'='authorization'} -UseBasicParsing
if ($options.Headers['Access-Control-Allow-Origin'] -ne 'http://localhost:5173') { throw 'Admin CORS validation failed.' }
$web = Invoke-WebRequest 'http://localhost:5173' -TimeoutSec 10 -UseBasicParsing
if ($web.StatusCode -ne 200) { throw 'Admin website is not serving pages.' }
Write-Output "PASS: health, email endpoint closed, administrator login, user page envelope (total=$($users.totalElements)), Dashboard, CORS, admin website."
Write-Output 'Real WeChat login, privacy interaction and navigation still require phone verification. No email demo consumer login was used.'
