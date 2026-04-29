param(
    [Parameter(Mandatory = $true)]
    [string]$DatasetPath,
    [Parameter(Mandatory = $true)]
    [string]$OutputDir,
    [string]$ServerUrl = "http://127.0.0.1:18080",
    [string]$RunId = ""
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$runnerPath = Join-Path $PSScriptRoot "run_halumem_benchmark.py"

if (-not (Test-Path $runnerPath)) {
    throw "HaluMem runner not found: $runnerPath"
}
if (-not (Test-Path $DatasetPath)) {
    throw "Dataset file not found: $DatasetPath"
}

$pythonCandidates = @("python", "py")
$pythonCommand = $null
foreach ($candidate in $pythonCandidates) {
    try {
        & $candidate --version *> $null
        if ($LASTEXITCODE -eq 0) {
            $pythonCommand = $candidate
            break
        }
    } catch {
    }
}
if (-not $pythonCommand) {
    throw "Python was not found on PATH."
}

Push-Location $repoRoot
try {
    $resolvedOutputDir = $null
    if (Test-Path $OutputDir) {
        $resolvedOutputDir = (Resolve-Path $OutputDir).Path
    } else {
        $resolvedOutputDir = Join-Path $repoRoot $OutputDir
        New-Item -ItemType Directory -Force -Path $resolvedOutputDir | Out-Null
    }

    $args = @(
        $runnerPath,
        "--dataset-path", (Resolve-Path $DatasetPath).Path,
        "--output-dir", $resolvedOutputDir,
        "--server-url", $ServerUrl
    )
    if ($RunId) {
        $args += @("--run-id", $RunId)
    }
    & $pythonCommand @args
    if ($LASTEXITCODE -ne 0) {
        throw "HaluMem runner failed with exit code $LASTEXITCODE."
    }
} finally {
    Pop-Location
}
