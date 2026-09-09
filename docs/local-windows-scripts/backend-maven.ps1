param([Parameter(ValueFromRemainingArguments = $true)][string[]]$MavenArguments = @('--version'))
$ErrorActionPreference = 'Stop'
$jdk = Get-ChildItem -LiteralPath 'D:\DevTOOLS' -Directory -Filter 'jdk-25*' | Sort-Object Name -Descending | Select-Object -First 1
if (!$jdk -or !(Test-Path "$($jdk.FullName)\bin\javac.exe")) { throw 'JDK 25 is not installed in D:\DevTOOLS yet.' }
$maven = 'D:\DevTOOLS\apache-maven-3.9.16\bin\mvn.cmd'
if (!(Test-Path $maven)) { throw 'Maven is not installed yet.' }
$previousJava = $env:JAVA_HOME
$previousPath = $env:PATH
Push-Location (Join-Path $PSScriptRoot '..\backend')
try {
    $env:JAVA_HOME = $jdk.FullName
    $env:PATH = "$($jdk.FullName)\bin;$env:PATH"
    & $maven '-Dmaven.repo.local=D:\DevTOOLS\maven-repository' @MavenArguments
    if ($LASTEXITCODE -ne 0) { throw "Maven exited with code $LASTEXITCODE" }
} finally {
    $env:JAVA_HOME = $previousJava
    $env:PATH = $previousPath
    Pop-Location
}
