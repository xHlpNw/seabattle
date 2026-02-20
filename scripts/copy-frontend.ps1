# Copy Angular production build into Spring Boot static resources.
# Run after: cd frontend && npm run build
# Then start the app from IDEA — open http://localhost:8080

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$src = Join-Path $root "frontend\dist\frontend-app"
$dst = Join-Path $root "src\main\resources\static"

if (-not (Test-Path $src)) {
    Write-Host "Build not found. Run: cd frontend && npm run build" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $dst)) { New-Item -ItemType Directory -Path $dst -Force | Out-Null }
Remove-Item (Join-Path $dst "*") -Recurse -Force -ErrorAction SilentlyContinue
Copy-Item (Join-Path $src "*") -Destination $dst -Recurse -Force
Write-Host "Copied frontend build to src/main/resources/static" -ForegroundColor Green
Write-Host "Start the app and open http://localhost:8080" -ForegroundColor Cyan
