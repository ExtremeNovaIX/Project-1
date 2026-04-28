param(
    [string]$ServerUrl = "http://127.0.0.1:18080",
    [string[]]$Datasets = @("oracle", "s_cleaned"),
    [switch]$SkipStart
)

$ErrorActionPreference = "Stop"

$benchmarkRoot = $PSScriptRoot
$repoRoot = Split-Path -Parent $benchmarkRoot
$outputRoot = Join-Path $benchmarkRoot "out\longmemeval"
$runId = Get-Date -Format "yyyyMMddHHmmss"

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

function Ensure-BenchmarkServer {
    param([string]$BaseUrl)

    if (Test-BenchmarkHealth -BaseUrl $BaseUrl) {
        Write-Host "Benchmark server already running at $BaseUrl"
        return
    }

    if ($SkipStart) {
        throw "Benchmark server is not running and -SkipStart was specified."
    }

    $port = Resolve-ServerPort -BaseUrl $BaseUrl
    $listenerProcessIds = @(Get-ListenerProcessIds -Port $port)
    if ($listenerProcessIds.Count -gt 0) {
        Write-Host ("Detected listener on port {0} (PID: {1}). Waiting for benchmark health before starting a new instance..." -f $port, ($listenerProcessIds -join ", "))
        if (Wait-BenchmarkHealth -BaseUrl $BaseUrl -TimeoutSeconds 60) {
            Write-Host "Benchmark server became healthy."
            return
        }
        throw ("Port {0} is already in use by PID {1}, but {2} is not healthy. Stop the stale benchmark process before retrying." -f $port, ($listenerProcessIds -join ", "), $BaseUrl)
    }

    $startCmd = Join-Path $benchmarkRoot "Start-BenchmarkServer.cmd"
    Write-Host "Starting benchmark server..."
    Start-Process -FilePath "cmd.exe" `
        -ArgumentList "/k", "`"$startCmd`"" `
        -WorkingDirectory $repoRoot `
        -WindowStyle Normal | Out-Null

    if (-not (Wait-BenchmarkHealth -BaseUrl $BaseUrl)) {
        throw "Benchmark server did not become healthy within timeout."
    }
}

function Resolve-DatasetPath {
    param([string[]]$RelativeCandidates)

    foreach ($relativePath in $RelativeCandidates) {
        $candidates = @()
        if ([System.IO.Path]::IsPathRooted($relativePath)) {
            $candidates += $relativePath
        } else {
            $candidates += (Join-Path $repoRoot $relativePath)
            $candidates += (Join-Path $benchmarkRoot $relativePath)
            $candidates += $relativePath
        }

        foreach ($candidatePath in $candidates | Select-Object -Unique) {
            if (Test-Path -LiteralPath $candidatePath) {
                return (Resolve-Path -LiteralPath $candidatePath).Path
            }
        }
    }
    return $null
}

function Get-DatasetCandidates {
    param([string]$DatasetName)

    switch ($DatasetName) {
        "oracle" {
            return @(
                "benchmark\data\longmemeval\longmemeval_oracle.json",
                "benchmark\longmemeval\longmemeval_oracle.json",
                "benchmark\longmemeval_oracle.json"
            )
        }
        "s_cleaned" {
            return @(
                "benchmark\data\longmemeval\longmemeval_s_cleaned.json",
                "benchmark\longmemeval\longmemeval_s_cleaned.json",
                "benchmark\longmemeval_s_cleaned.json"
            )
        }
        "m_cleaned" {
            return @(
                "benchmark\data\longmemeval\longmemeval_m_cleaned.json",
                "benchmark\longmemeval\longmemeval_m_cleaned.json",
                "benchmark\longmemeval_m_cleaned.json"
            )
        }
        default {
            throw "Unknown dataset name [$DatasetName]. Use oracle, s_cleaned, or m_cleaned."
        }
    }
}

function New-DatasetDefinition {
    param([string]$DatasetName)

    $candidatePaths = @(Get-DatasetCandidates -DatasetName $DatasetName)
    $resolvedPath = Resolve-DatasetPath -RelativeCandidates $candidatePaths
    return [pscustomobject]@{
        Name = $DatasetName
        Path = $resolvedPath
        Candidates = $candidatePaths
    }
}

function Invoke-LongMemEvalRun {
    param(
        [string]$DatasetPath,
        [string]$OutputPath,
        [string]$ChildRunId
    )

    $scriptPath = Join-Path $benchmarkRoot "longmemeval\Invoke-LongMemEvalRetrieval.ps1"
    & powershell -NoProfile -ExecutionPolicy Bypass -File $scriptPath `
        -DatasetPath $DatasetPath `
        -OutputPath $OutputPath `
        -ServerUrl $ServerUrl `
        -RunId $ChildRunId

    if ($LASTEXITCODE -ne 0) {
        throw "LongMemEval child runner failed with exit code $LASTEXITCODE for dataset [$DatasetPath]."
    }
}

Ensure-BenchmarkServer -BaseUrl $ServerUrl

if (-not (Test-Path $outputRoot)) {
    New-Item -ItemType Directory -Path $outputRoot | Out-Null
}

$requestedDatasets = @($Datasets | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | ForEach-Object { $_.Trim() })
if ($requestedDatasets.Count -eq 0) {
    throw "No datasets selected. Use -Datasets oracle,s_cleaned,m_cleaned."
}

$availableDatasets = @()
foreach ($datasetName in $requestedDatasets) {
    $dataset = New-DatasetDefinition -DatasetName $datasetName
    if ($null -ne $dataset.Path) {
        $availableDatasets += $dataset
        continue
    }

    Write-Host ("Dataset [{0}] not found. Checked candidates:" -f $datasetName) -ForegroundColor Yellow
    foreach ($candidate in $dataset.Candidates) {
        Write-Host ("  - {0}" -f $candidate) -ForegroundColor Yellow
    }
}

if ($availableDatasets.Count -eq 0) {
    throw "No selected LongMemEval dataset files were found."
}

$summaryRows = @()
foreach ($dataset in $availableDatasets) {
    $childRunId = "$runId-$($dataset.Name)"
    $outputPath = Join-Path $outputRoot ("longmemeval_{0}_{1}.json" -f $dataset.Name, $runId)

    Write-Host ""
    Write-Host ("Running dataset [{0}] from {1}" -f $dataset.Name, $dataset.Path)
    Invoke-LongMemEvalRun -DatasetPath $dataset.Path -OutputPath $outputPath -ChildRunId $childRunId

    if (-not (Test-Path $outputPath)) {
        throw "LongMemEval runner finished without producing output file for dataset [$($dataset.Name)]: $outputPath"
    }

    $result = Get-Content -Path $outputPath -Raw | ConvertFrom-Json
    $summaryRows += [pscustomobject]@{
        dataset = $dataset.Name
        file = $outputPath
        sampleCount = $result.summary.sampleCount
        avgHitAt1 = $result.summary.avgHitAt1
        avgHitAt3 = $result.summary.avgHitAt3
        avgHitAt5 = $result.summary.avgHitAt5
        avgRecallAt1 = $result.summary.avgRecallAt1
        avgRecallAt3 = $result.summary.avgRecallAt3
        avgRecallAt5 = $result.summary.avgRecallAt5
    }
}

$summaryPath = Join-Path $outputRoot ("summary_{0}.json" -f $runId)
$summaryPayload = [pscustomobject]@{
    runId = $runId
    serverUrl = $ServerUrl
    generatedAt = (Get-Date).ToString("s")
    datasets = $summaryRows
}
$summaryPayload | ConvertTo-Json -Depth 10 | Set-Content -Path $summaryPath -Encoding UTF8

Write-Host ""
Write-Host "LongMemEval run completed."
Write-Host ("Summary: {0}" -f $summaryPath)
$summaryRows | Format-Table -AutoSize
