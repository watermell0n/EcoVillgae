[CmdletBinding()]
param(
    [switch]$SkipDependencies
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

if (-not $SkipDependencies) {
    Push-Location $projectRoot
    try {
        # Wait for both health checks so Spring does not spend its startup time
        # retrying a PostgreSQL or Redis connection that is not ready yet.
        docker compose up -d --wait --wait-timeout 45 postgres redis
    }
    finally {
        Pop-Location
    }
}

Push-Location (Join-Path $projectRoot 'backend')
try {
    & .\gradlew.bat bootRun
}
finally {
    Pop-Location
}
