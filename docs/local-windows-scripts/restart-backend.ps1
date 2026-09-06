$ErrorActionPreference = 'Stop'
$backendDir = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\backend'))
. "$PSScriptRoot\backend-process.ps1"
$listener = Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue
if ($listener) {
    $processId = $listener[0].OwningProcess
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$processId"
    if (!(Test-ProjectBackendProcess -Process $process -BackendDirectory $backendDir)) {
        throw 'Port 8080 does not belong to this project. Nothing was stopped.'
    }
    # Only stop the verified development backend; MySQL and its data remain running.
    Stop-Process -Id $processId -ErrorAction Stop
    Wait-Process -Id $processId -Timeout 15 -ErrorAction SilentlyContinue
}
& "$PSScriptRoot\start-backend.ps1"
