$ErrorActionPreference = 'Stop'
throw 'Email demo initialization is retired. Use manage-local-accounts.ps1 -Operation create-admin for administrators. Consumer defaults are created on first consented WeChat login.'
$api = 'http://127.0.0.1:8080/api/v1'
$accountFile = 'D:\DevTOOLS\ai-meal-private\demo-accounts.clixml'
function New-Password {
    $bytes = New-Object byte[] 8
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return 'Dev_' + [BitConverter]::ToString($bytes).Replace('-', '').Substring(0,12)
}
if (!(Test-Path $accountFile)) {
    [pscustomobject]@{
        User = [pscredential]::new('user@local.test', (ConvertTo-SecureString (New-Password) -AsPlainText -Force))
        Admin = [pscredential]::new('admin@local.test', (ConvertTo-SecureString (New-Password) -AsPlainText -Force))
    } | Export-Clixml $accountFile
}
$accounts = Import-Clixml $accountFile
function Request([string]$Method, [string]$Path, $Body = $null, [string]$Token = '') {
    $options = @{ Uri = "$api$Path"; Method = $Method; ContentType = 'application/json'; TimeoutSec = 20 }
    if ($Token) { $options.Headers = @{ Authorization = "Bearer $Token" } }
    if ($null -ne $Body) { $options.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 10 -Compress)) }
    return (Invoke-RestMethod @options).data
}
function Ensure-Account($Credential) {
    $password = $Credential.GetNetworkCredential().Password
    try { return Request 'POST' '/auth/register' @{email=$Credential.UserName; password=$password; confirmPassword=$password} }
    catch {
        if ([int]$_.Exception.Response.StatusCode -ne 409) { throw }
        return Request 'POST' '/auth/login' @{email=$Credential.UserName; password=$password}
    }
}
$user = Ensure-Account $accounts.User
$admin = Ensure-Account $accounts.Admin
if (!$user.user.onboardingCompleted) {
    $null = Request 'PUT' '/users/me/onboarding' @{nickname='Local Tester'; budgetEnabled=$false; medicalAllergies=@(); dietaryRestrictions=@(); dislikes=@(); tastePreferences=@()} $user.accessToken
}
$foods = Request 'GET' '/food-options?size=100' $null $user.accessToken
if (@($foods.items).Count -ne 10) { throw 'Expected 10 default food options for the new user.' }
$records = Request 'GET' '/diet-records?page=0&size=20' $null $user.accessToken
if (@($records.items).Count -eq 0) {
    $spin = Request 'POST' '/slot/spins' @{} $user.accessToken
    $body = @{actualPrice=$spin.selectedFood.defaultPrice; mealType='LUNCH'; eatenAt=[DateTimeOffset]::Now.ToString('o')}
    $confirmed = Request 'POST' "/slot/spins/$($spin.spinId)/confirm" $body $user.accessToken
    $again = Request 'POST' "/slot/spins/$($spin.spinId)/confirm" $body $user.accessToken
    if ($confirmed.id -ne $again.id) { throw 'Repeated confirmation returned a different record.' }
}
Write-Output 'User registration/login, onboarding, 10 default foods, spin and confirmation verified. Local accounts saved encrypted.'
# Use the project bootstrap service in a temporary loopback web context so SecurityConfig has HttpSecurity.
$secrets = Import-Clixml 'D:\DevTOOLS\ai-meal-private\secrets.clixml'
$values = @{
    AGENT_WORK_FOOT_DB_USERNAME=$secrets.Database.UserName
    AGENT_WORK_FOOT_DB_PASSWORD=$secrets.Database.GetNetworkCredential().Password
    APP_AUTH_JWT_ACTIVE_SECRET=$secrets.Jwt.GetNetworkCredential().Password
    SPRING_PROFILES_ACTIVE='dev'
    SERVER_ADDRESS='127.0.0.1'
    SERVER_PORT='0'
    APP_ADMIN_BOOTSTRAP_ENABLED='true'
    APP_ADMIN_BOOTSTRAP_EMAIL=$accounts.Admin.UserName
    APP_ADMIN_BOOTSTRAP_ACCOUNT='admin'
}
$previous = @{}
try {
    foreach ($name in $values.Keys) { $previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process'); [Environment]::SetEnvironmentVariable($name,$values[$name],'Process') }
    & "$PSScriptRoot\backend-maven.ps1" '--no-transfer-progress' 'spring-boot:run'
} finally {
    foreach ($name in $values.Keys) { [Environment]::SetEnvironmentVariable($name,$previous[$name],'Process') }
}
$login = Request 'POST' '/admin/auth/login' @{account='admin';password=$accounts.Admin.GetNetworkCredential().Password}
if ($login.user.role -ne 'ADMIN') { throw 'Admin role verification failed.' }
$dashboard = Request 'GET' '/admin/dashboard' $null $login.accessToken
$audit = Request 'GET' '/admin/audit-logs' $null $login.accessToken
Write-Output 'Admin login, Dashboard and audit endpoints verified.'
