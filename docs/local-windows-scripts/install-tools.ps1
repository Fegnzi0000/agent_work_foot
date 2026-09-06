param([ValidateSet('jdk','maven','mysql')][string]$Tool)
$ErrorActionPreference = 'Stop'
$root = 'D:\DevTOOLS'
$proxyOptions = @()
$proxySettings = Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -ErrorAction SilentlyContinue
if ($proxySettings.ProxyEnable -eq 1 -and $proxySettings.ProxyServer -match '^127\.0\.0\.1:\d+$') {
    $proxyOptions = @('--proxy', "http://$($proxySettings.ProxyServer)")
}
New-Item -ItemType Directory -Path "$root\downloads" -Force | Out-Null
function Download([string]$Url, [string]$Output) {
    if ($Url.StartsWith('https://github.com/') -and $proxyOptions.Count) {
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest -Uri $Url -OutFile $Output -Proxy $proxyOptions[1] -TimeoutSec 600
        return
    }
    & curl.exe @proxyOptions --ssl-revoke-best-effort --fail --location --retry 4 --connect-timeout 30 --max-time 1200 --silent --show-error --output $Output $Url
    if ($LASTEXITCODE -ne 0) { throw "Download failed: $Url" }
}
switch ($Tool) {
    'jdk' {
        $archive = "$root\downloads\OpenJDK25U-jdk_x64_windows_hotspot_25.0.4.1_1.zip"
        $url = 'https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_windows_hotspot_25.0.4.1_1.zip'
        Download $url $archive
        Download "$url.sha256.txt" "$archive.sha256.txt"
        $expected = (Get-Content "$archive.sha256.txt" -Raw).Trim().Split(' ')[0]
        if ((Get-FileHash $archive -Algorithm SHA256).Hash -ne $expected) { throw 'JDK checksum mismatch' }
    }
    'maven' {
        $archive = "$root\downloads\apache-maven-3.9.16-bin.zip"
        $url = 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip'
        Download $url $archive
        Download "$url.sha512" "$archive.sha512"
        $expected = (Get-Content "$archive.sha512" -Raw).Trim().Split(' ')[0]
        if ((Get-FileHash $archive -Algorithm SHA512).Hash -ne $expected) { throw 'Maven checksum mismatch' }
    }
    'mysql' {
        $archive = "$root\downloads\mysql-8.4.11-winx64.zip"
        Download 'https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-8.4.11-winx64.zip' $archive
        if ((Get-FileHash $archive -Algorithm MD5).Hash -ne '2e833921898a9a030ea6bfe81bd811bc') { throw 'MySQL checksum mismatch' }
    }
}
Write-Output "$Tool download verified; extracting..."
Expand-Archive -LiteralPath $archive -DestinationPath $root
Write-Output "$Tool installed under $root"
