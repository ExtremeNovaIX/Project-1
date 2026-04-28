param(
    [string]$ServerUrl = "http://127.0.0.1:18080",
    [switch]$SkipStart
)

$ErrorActionPreference = "Stop"

$scriptPath = Join-Path $PSScriptRoot "Run-LongMemEval-OneClick.ps1"
if ($SkipStart) {
    & $scriptPath -ServerUrl $ServerUrl -Datasets @("s_cleaned") -SkipStart
} else {
    & $scriptPath -ServerUrl $ServerUrl -Datasets @("s_cleaned")
}

exit $LASTEXITCODE
