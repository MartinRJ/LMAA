[CmdletBinding()]
param(
    [switch] $Lan,
    [ValidateRange(1, 65535)]
    [int] $Port = 8000
)

$ErrorActionPreference = "Stop"
$backendDirectory = Split-Path -Parent $PSScriptRoot
$repositoryDirectory = Split-Path -Parent $backendDirectory
$pythonPath = Join-Path $backendDirectory ".venv\Scripts\python.exe"
$keyPath = Join-Path $repositoryDirectory "OpenAI API KEY.txt"

if (-not (Test-Path -LiteralPath $pythonPath -PathType Leaf)) {
    throw "Backend-Umgebung fehlt. Zuerst die Installation aus backend/README.md ausführen."
}
if (-not (Test-Path -LiteralPath $keyPath -PathType Leaf)) {
    throw "OpenAI API KEY.txt fehlt im Repository-Wurzelverzeichnis."
}

$apiKey = (Get-Content -Raw -LiteralPath $keyPath).Trim()
if ([string]::IsNullOrWhiteSpace($apiKey)) {
    throw "OpenAI API KEY.txt ist leer."
}

$previousApiKey = $env:LMAA_OPENAI_API_KEY
$previousHost = $env:LMAA_BIND_HOST
$previousPort = $env:PORT

try {
    $env:LMAA_OPENAI_API_KEY = $apiKey
    $env:LMAA_BIND_HOST = if ($Lan) { "0.0.0.0" } else { "127.0.0.1" }
    $env:PORT = [string] $Port
    & $pythonPath -m lmaa_backend.server
    if ($LASTEXITCODE -ne 0) {
        throw "Backend-Prozess wurde mit Exitcode $LASTEXITCODE beendet."
    }
}
finally {
    $apiKey = $null
    $env:LMAA_OPENAI_API_KEY = $previousApiKey
    $env:LMAA_BIND_HOST = $previousHost
    $env:PORT = $previousPort
}
