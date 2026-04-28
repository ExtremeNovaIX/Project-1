param(
    [string]$ServerUrl = "http://127.0.0.1:18080"
)

$ErrorActionPreference = "Stop"

function Resolve-ServerPort {
    param([string]$BaseUrl)

    $uri = [Uri]$BaseUrl
    if ($uri.Port -gt 0) {
        return $uri.Port
    }
    if ($uri.Scheme -eq "https") {
        return 443
    }
    return 80
}

function Get-ListenerProcessIds {
    param([int]$Port)

    $pids = New-Object System.Collections.Generic.HashSet[int]
    foreach ($line in @(netstat -ano | Select-String (":{0}\s" -f $Port))) {
        $parts = ($line.ToString() -split "\s+") | Where-Object { $_ -ne "" }
        if ($parts.Length -ge 5 -and $parts[3] -eq "LISTENING") {
            $pidText = $parts[4]
            $resolvedPid = 0
            if ([int]::TryParse($pidText, [ref]$resolvedPid)) {
                [void]$pids.Add($resolvedPid)
            }
        }
    }
    return @($pids)
}

function Wait-PortReleased {
    param(
        [int]$Port,
        [int]$TimeoutSeconds = 30
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ((Get-ListenerProcessIds -Port $Port).Count -eq 0) {
            return $true
        }
        Start-Sleep -Seconds 1
    }

    return $false
}

$port = Resolve-ServerPort -BaseUrl $ServerUrl
$listenerProcessIds = @(Get-ListenerProcessIds -Port $port)
if ($listenerProcessIds.Count -eq 0) {
    Write-Host "No benchmark server is listening on port $port."
    exit 0
}

Write-Host ("Stopping benchmark server on port {0} (PID: {1})..." -f $port, ($listenerProcessIds -join ", "))
foreach ($processId in $listenerProcessIds) {
    try {
        Stop-Process -Id $processId -Force -ErrorAction Stop
    } catch {
        Write-Warning ("Failed to stop PID {0}: {1}" -f $processId, $_.Exception.Message)
    }
}

if (-not (Wait-PortReleased -Port $port)) {
    throw "Benchmark server port was not released after stop request."
}

Write-Host "Benchmark server stopped."
