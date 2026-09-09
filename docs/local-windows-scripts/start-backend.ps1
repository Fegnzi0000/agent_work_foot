$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\backend-process.ps1"
$backendDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\backend'))
$listener = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue
if ($listener) {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener[0].OwningProcess)"
    if (Test-ProjectBackendProcess -Process $process -BackendDirectory $backendDir) {
        Write-Output 'Project backend is already running at http://127.0.0.1:8080. Stop its original terminal before restarting.'
        return
    }
    throw 'Port 8080 belongs to another program.'
}
& "$PSScriptRoot\start-mysql.ps1"
$secrets = Import-Clixml 'D:\DevTOOLS\ai-meal-private\secrets.clixml'
$names = @('AGENT_WORK_FOOT_DB_USERNAME','AGENT_WORK_FOOT_DB_PASSWORD','APP_AUTH_JWT_ACTIVE_SECRET','SPRING_PROFILES_ACTIVE','WECHAT_MINI_PROGRAM_APP_ID','WECHAT_MINI_PROGRAM_APP_SECRET')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    $env:AGENT_WORK_FOOT_DB_USERNAME = $secrets.Database.UserName
    $env:AGENT_WORK_FOOT_DB_PASSWORD = $secrets.Database.GetNetworkCredential().Password
    $env:APP_AUTH_JWT_ACTIVE_SECRET = $secrets.Jwt.GetNetworkCredential().Password
    $env:SPRING_PROFILES_ACTIVE = 'dev'
    $wechatFile = 'D:\DevTOOLS\ai-meal-private\wechat.clixml'
    if (Test-Path $wechatFile) {
        $wechat = Import-Clixml $wechatFile
        $env:WECHAT_MINI_PROGRAM_APP_ID = $wechat.UserName
        $env:WECHAT_MINI_PROGRAM_APP_SECRET = $wechat.GetNetworkCredential().Password
        Write-Output "WeChat configuration loaded for AppID $($wechat.UserName)."
    }
    if (!$env:WECHAT_MINI_PROGRAM_APP_SECRET) { Write-Output 'WeChat secret is not configured. Consumer login is unavailable; run configure-wechat.ps1. Administrator login is separate.' }
    & "$PSScriptRoot\backend-maven.ps1" '--no-transfer-progress' 'spring-boot:run'
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
