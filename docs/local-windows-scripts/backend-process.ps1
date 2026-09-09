function Test-ProjectBackendProcess {
    param($Process, [string]$BackendDirectory)
    try {
        if (!$Process.ExecutablePath -or [IO.Path]::GetFileName($Process.ExecutablePath) -ine 'java.exe') { return $false }
        $command = $Process.CommandLine
        if (!$command -or $command -notmatch '(?:^|\s)com\.hyf\.agent_work_foot\.AgentWorkFootApplication(?:\s|$)') { return $false }
        $match = [regex]::Match($command, '(?:^|\s)(?:-cp|-classpath|--class-path)\s+(@?"[^"]+"|\S+)')
        if (!$match.Success) { return $false }
        $argument = $match.Groups[1].Value
        $candidates = @()
        if ($argument.StartsWith('@')) {
            $file = $argument.Substring(1).Trim('"')
            $bytes = [IO.File]::ReadAllBytes($file)
            # Maven argument files may use UTF-8 or the Windows Chinese code page.
            try { $candidates += [Text.UTF8Encoding]::new($false,$true).GetString($bytes) } catch { }
            $candidates += [Text.Encoding]::GetEncoding(936).GetString($bytes)
        } else { $candidates += $argument }
        $expected = [IO.Path]::GetFullPath((Join-Path $BackendDirectory 'target\classes')).TrimEnd('\')
        foreach ($candidate in $candidates) {
            $classpath = $candidate.Trim().Trim('"').Replace('\\','\')
            foreach ($entry in $classpath.Split(';')) {
                if (!$entry) { continue }
                $path = [IO.Path]::GetFullPath($entry.Trim('"').Replace('/','\')).TrimEnd('\')
                if ($path -ieq $expected) { return $true }
            }
        }
    } catch { return $false }
    return $false
}
