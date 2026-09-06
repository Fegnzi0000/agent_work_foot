$ErrorActionPreference = 'Stop'
# Stop at the input boundary: never request a real secret or write credentials.
function Read-Host {
    param([string]$Prompt, [switch]$AsSecureString)
    throw 'TEST_SECRET_PROMPT_REACHED'
}
try {
    & "$PSScriptRoot\configure-wechat.ps1"
    throw 'Expected the script to request a secret.'
} catch {
    if ($_.Exception.Message -ne 'TEST_SECRET_PROMPT_REACHED') { throw }
}
Write-Output 'PASS: UTF-8 project JSON parsed and secret prompt reached; no credentials read or written.'
