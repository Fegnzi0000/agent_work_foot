param([ValidateSet('create-admin','preview','purge')][string]$Operation = 'preview', [string]$UserId)
$ErrorActionPreference = 'Stop'
if ($Operation -ne 'create-admin') { $null = [Guid]::Parse($UserId) }
$maintenanceNames = @('AGENT_WORK_FOOT_DB_USERNAME','AGENT_WORK_FOOT_DB_PASSWORD','APP_AUTH_JWT_ACTIVE_SECRET','SPRING_PROFILES_ACTIVE','MAINTENANCE_OPERATION','MAINTENANCE_ACCOUNT','MAINTENANCE_PASSWORD','MAINTENANCE_USER_ID','MAINTENANCE_CONFIRMATION')
$maintenancePrevious = @{}
foreach ($name in $maintenanceNames) { $maintenancePrevious[$name] = [Environment]::GetEnvironmentVariable($name,'Process') }
try {
    $maintenanceSecrets = Import-Clixml -LiteralPath 'D:\DevTOOLS\ai-meal-private\secrets.clixml'
    $env:AGENT_WORK_FOOT_DB_USERNAME = $maintenanceSecrets.Database.UserName
    $env:AGENT_WORK_FOOT_DB_PASSWORD = $maintenanceSecrets.Database.GetNetworkCredential().Password
    $env:APP_AUTH_JWT_ACTIVE_SECRET = $maintenanceSecrets.Jwt.GetNetworkCredential().Password
    $env:SPRING_PROFILES_ACTIVE = 'dev'
    $env:MAINTENANCE_OPERATION = $Operation
    $env:MAINTENANCE_USER_ID = $UserId
    if ($Operation -eq 'create-admin') {
        $env:MAINTENANCE_ACCOUNT = Read-Host 'Administrator account (3-32 letters/numbers/underscore, starts with letter)'
        $maintenanceSecurePassword = Read-Host 'Password (6-20 letters/numbers/underscore; existing account is NOT reset)' -AsSecureString
        $env:MAINTENANCE_PASSWORD = ([pscredential]::new('local',$maintenanceSecurePassword)).GetNetworkCredential().Password
    }
    if ($Operation -eq 'purge') {
        Write-Warning 'Only proceed after verifying account ownership, previewing related data, and creating a protected backup. This permanently deletes online data and releases WeChat identity.'
        $env:MAINTENANCE_CONFIRMATION = Read-Host "Type PURGE $UserId to confirm"
        if ($env:MAINTENANCE_CONFIRMATION -cne "PURGE $UserId") { throw 'Cancelled: confirmation does not match' }
    }
    & "$PSScriptRoot\backend-maven.ps1" -MavenArguments @('--no-transfer-progress','spring-boot:run','-Dspring-boot.run.arguments=--server.address=127.0.0.1 --server.port=0 --app.maintenance.enabled=true')
} finally {
    foreach ($name in $maintenanceNames) { [Environment]::SetEnvironmentVariable($name,$maintenancePrevious[$name],'Process') }
    $maintenanceSecrets=$null; $maintenanceSecurePassword=$null
}
