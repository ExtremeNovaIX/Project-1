param(
    [string]$ServerUrl = "http://127.0.0.1:18080",
    [string]$DatasetPath = "",
    [string]$RunId = ""
)

$ErrorActionPreference = "Stop"

function Test-BenchmarkHealth {
    param([string]$BaseUrl)

    try {
        $response = Invoke-RestMethod -Method GET -Uri ($BaseUrl.TrimEnd("/") + "/api/benchmark/halumem/health") -TimeoutSec 5
        return $response.status -eq "ok"
    } catch {
        return $false
    }
}

function Wait-BenchmarkHealth {
    param(
        [string]$BaseUrl,
        [int]$TimeoutSeconds = 240
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

function Resolve-DatasetPath {
    param([string]$ExplicitPath)

    if ($ExplicitPath) {
        if (-not (Test-Path $ExplicitPath)) {
            throw "Dataset file not found: $ExplicitPath"
        }
        return (Resolve-Path $ExplicitPath).Path
    }

    $candidates = @(
        (Join-Path $PSScriptRoot "halumem\HaluMem-Medium.jsonl"),
        (Join-Path $PSScriptRoot "halumem\HaluMem-Easy.jsonl"),
        (Join-Path $PSScriptRoot "halumem\HaluMem-Hard.jsonl"),
        (Join-Path $PSScriptRoot "HaluMem-Medium.jsonl"),
        (Join-Path $PSScriptRoot "HaluMem-Easy.jsonl"),
        (Join-Path $PSScriptRoot "HaluMem-Hard.jsonl"),
        (Join-Path $PSScriptRoot "data\halumem\HaluMem-Medium.jsonl"),
        (Join-Path $PSScriptRoot "data\halumem\HaluMem-Easy.jsonl"),
        (Join-Path $PSScriptRoot "data\halumem\HaluMem-Hard.jsonl")
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return (Resolve-Path $candidate).Path
        }
    }

    throw "No HaluMem dataset file was found. Place HaluMem-Medium.jsonl under benchmark\\halumem, benchmark, or benchmark\\data\\halumem."
}

$dataset = Resolve-DatasetPath -ExplicitPath $DatasetPath
$outputDir = Join-Path $PSScriptRoot "out\halumem"
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

if (-not (Test-BenchmarkHealth -BaseUrl $ServerUrl)) {
    $startScript = Join-Path $PSScriptRoot "Start-BenchmarkServer.ps1"
    $serverStdoutPath = Join-Path $outputDir "benchmark-server.stdout.log"
    $serverStderrPath = Join-Path $outputDir "benchmark-server.stderr.log"
    Write-Host "Starting benchmark server..."
    Start-Process powershell -ArgumentList @(
        "-NoProfile",
        "-ExecutionPolicy", "Bypass",
        "-File", $startScript,
        "-ServerUrl", $ServerUrl
    ) -WindowStyle Hidden -RedirectStandardOutput $serverStdoutPath -RedirectStandardError $serverStderrPath | Out-Null

    if (-not (Wait-BenchmarkHealth -BaseUrl $ServerUrl)) {
        throw "Benchmark server did not become healthy at $ServerUrl"
    }
}

$invokeScript = Join-Path $PSScriptRoot "halumem\Invoke-HaluMemBenchmark.ps1"
& $invokeScript -DatasetPath $dataset -OutputDir $outputDir -ServerUrl $ServerUrl -RunId $RunId
