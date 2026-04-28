param(
    [string]$Profile = "benchmark",
    [string]$ServerUrl = "http://127.0.0.1:18080"
)

$ErrorActionPreference = "Stop"

$script:ServerPort = $null

function Test-BenchmarkHealth {
    param([string]$BaseUrl)

    try {
        $response = Invoke-RestMethod -Method GET -Uri ($BaseUrl.TrimEnd("/") + "/api/benchmark/memory/health") -TimeoutSec 5
        return $response.status -eq "ok"
    } catch {
        return $false
    }
}

function Wait-BenchmarkHealth {
    param(
        [string]$BaseUrl,
        [int]$TimeoutSeconds = 180
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-BenchmarkHealth -BaseUrl $BaseUrl) {
            return $true
        }
        Start-Sleep -Seconds 2
    }

    return $false
}

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

$script:ServerPort = Resolve-ServerPort -BaseUrl $ServerUrl

if (Test-BenchmarkHealth -BaseUrl $ServerUrl) {
    Write-Host "Benchmark server already running at $ServerUrl"
    exit 0
}

$listenerProcessIds = @(Get-ListenerProcessIds -Port $script:ServerPort)
if ($listenerProcessIds.Count -gt 0) {
    Write-Host ("Detected existing listener on port {0} (PID: {1}). Waiting for benchmark health..." -f $script:ServerPort, ($listenerProcessIds -join ", "))
    if (Wait-BenchmarkHealth -BaseUrl $ServerUrl -TimeoutSeconds 60) {
        Write-Host "Benchmark server became healthy."
        exit 0
    }
    throw ("Port {0} is already in use by PID {1}, but {2} did not become healthy. Stop the stale benchmark process before retrying." -f $script:ServerPort, ($listenerProcessIds -join ", "), $ServerUrl)
}

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    & .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=$Profile"
} finally {
    Pop-Location
}
