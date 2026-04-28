param(
    [Parameter(Mandatory = $true)]
    [string]$DatasetPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath,

    [string]$ServerUrl = "http://127.0.0.1:18080",

    [string]$RunId = ("run-" + (Get-Date -Format "yyyyMMddHHmmss")),

    [int]$BatchMessageCount = 0
)

$ErrorActionPreference = "Stop"

$scriptRoot = $PSScriptRoot
$pythonScript = Join-Path $scriptRoot "run_longmemeval_retrieval.py"
if (-not (Test-Path $pythonScript)) {
    throw "Python runner not found: $pythonScript"
}

$python = Get-Command python -ErrorAction SilentlyContinue
if ($null -eq $python) {
    throw "Python is required for the LongMemEval runner but was not found on PATH."
}

$arguments = @(
    $pythonScript,
    "--dataset-path", $DatasetPath,
    "--output-path", $OutputPath,
    "--server-url", $ServerUrl,
    "--run-id", $RunId
)

if ($BatchMessageCount -gt 0) {
    $arguments += @("--batch-message-count", $BatchMessageCount)
}

& $python.Source @arguments
exit $LASTEXITCODE
