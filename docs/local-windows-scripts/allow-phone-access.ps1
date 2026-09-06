# Run in an Administrator PowerShell. Only Java TCP 8080 from the local subnet is allowed.
$ErrorActionPreference = 'Stop'
$jdk = Get-ChildItem -LiteralPath 'D:\DevTOOLS' -Directory -Filter 'jdk-25*' | Sort-Object Name -Descending | Select-Object -First 1
if (!$jdk) { throw 'JDK 25 not found.' }
$name = 'AI-Meal-Local-Backend-8080'
$existing = Get-NetFirewallRule -Name $name -ErrorAction SilentlyContinue
if ($existing) { Write-Output 'Project firewall rule already exists.'; return }
New-NetFirewallRule -Name $name -DisplayName 'AI Meal local backend (local subnet only)' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8080 -RemoteAddress LocalSubnet -Program "$($jdk.FullName)\bin\java.exe" -Profile Any -ErrorAction Stop | Select-Object Name,Enabled,Action
