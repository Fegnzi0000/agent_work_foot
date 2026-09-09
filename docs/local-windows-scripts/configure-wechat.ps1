$ErrorActionPreference = 'Stop'
$config = Get-Content -LiteralPath (Join-Path $PSScriptRoot '..\frontend\user\project.config.json') -Raw -Encoding UTF8 | ConvertFrom-Json
Write-Output "Mini-program AppID: $($config.appid)"
$secret = Read-Host 'Enter the matching WeChat AppSecret (input is hidden)' -AsSecureString
if ($secret.Length -eq 0) { throw 'No secret entered; nothing saved.' }
if (!(Test-Path 'D:\DevTOOLS\ai-meal-private\secrets.clixml')) { throw 'Initialize the local environment first.' }
[pscredential]::new($config.appid, $secret) | Export-Clixml 'D:\DevTOOLS\ai-meal-private\wechat.clixml'
Write-Output 'WeChat configuration saved, encrypted for your Windows account. Restart the backend to apply it.'
