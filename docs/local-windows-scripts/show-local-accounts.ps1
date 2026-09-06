$ErrorActionPreference = 'Stop'
$accounts = Import-Clixml 'D:\DevTOOLS\ai-meal-private\demo-accounts.clixml'
Write-Output 'Local development only. These are not database passwords.'
[pscustomobject]@{Client='Mini-program email login';Account=$accounts.User.UserName;Password=$accounts.User.GetNetworkCredential().Password}
[pscustomobject]@{Client='Admin website';Account='admin';Password=$accounts.Admin.GetNetworkCredential().Password}
