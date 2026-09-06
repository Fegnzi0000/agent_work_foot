param([string]$Tests = 'ConsentLifecycleIntegrationTests')
$ErrorActionPreference = 'Stop'
$testSecrets = Import-Clixml -LiteralPath 'D:\DevTOOLS\ai-meal-private\secrets.clixml'
$oldMysql = $env:MYSQL_PWD
$oldUrl = $env:CONSENT_TEST_DB_URL
$oldUser = $env:CONSENT_TEST_DB_USER
$oldPassword = $env:CONSENT_TEST_DB_PASSWORD
try {
    $env:MYSQL_PWD = $testSecrets.Root.GetNetworkCredential().Password
    $testPassword = [Guid]::NewGuid().ToString('N')
    $testSql = "CREATE DATABASE IF NOT EXISTS agent_work_foot_consent_test CHARACTER SET utf8mb4; CREATE USER IF NOT EXISTS 'ai_meal_consent_test'@'localhost' IDENTIFIED BY '$testPassword'; ALTER USER 'ai_meal_consent_test'@'localhost' IDENTIFIED BY '$testPassword'; GRANT ALL PRIVILEGES ON agent_work_foot_consent_test.* TO 'ai_meal_consent_test'@'localhost';"
    $testSql | & 'D:\DevTOOLS\mysql-8.4.11-winx64\bin\mysql.exe' --host=127.0.0.1 --port=3307 --user=root --batch
    if ($LASTEXITCODE -ne 0) { throw 'Isolated test database setup failed' }
    $env:MYSQL_PWD = $oldMysql
    $env:CONSENT_TEST_DB_URL = 'jdbc:mysql://127.0.0.1:3307/agent_work_foot_consent_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&sslMode=DISABLED'
    $env:CONSENT_TEST_DB_USER = 'ai_meal_consent_test'
    $env:CONSENT_TEST_DB_PASSWORD = $testPassword
    & "$PSScriptRoot\backend-maven.ps1" -MavenArguments @('-o',"-Dtest=$Tests",'test')
} finally {
    $env:MYSQL_PWD = $oldMysql
    $env:CONSENT_TEST_DB_URL = $oldUrl
    $env:CONSENT_TEST_DB_USER = $oldUser
    $env:CONSENT_TEST_DB_PASSWORD = $oldPassword
    $testSecrets = $null; $testPassword = $null; $testSql = $null
}
