$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..\frontend\admin')
try {
    & npm.cmd run dev:api -- --host localhost --port 5173 --strictPort
    if ($LASTEXITCODE -ne 0) { throw 'Admin website startup failed.' }
} finally { Pop-Location }
