param([ValidateRange(1, 120)][int]$TimeoutSeconds = 30)

$ErrorActionPreference = 'Stop'
try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Docker is not installed or is not on PATH.'
    }
    $composeRoot = Join-Path $PSScriptRoot 'backend'
    Write-Host 'Stopping Chessoc and its public HTTPS tunnel...'
    & docker compose --project-directory $composeRoot -f (Join-Path $composeRoot 'docker-compose.yml') --profile public stop --timeout $TimeoutSeconds
    if ($LASTEXITCODE -ne 0) { throw 'Could not stop the stack. Make sure Docker Desktop is running and retry.' }
    $urlFile = Join-Path $PSScriptRoot '.demo/public-url.txt'
    if (Test-Path -LiteralPath $urlFile) { Remove-Item -LiteralPath $urlFile }
    $demoRoot = Join-Path $PSScriptRoot '.demo'
    New-Item -ItemType Directory -Force -Path $demoRoot | Out-Null
    @{ running = $false; lanUrls = @(); publicUrl = $null; updatedAt = (Get-Date).ToString('o') } |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $demoRoot 'addresses.json')
    Write-Host 'Chessoc stopped. Game data is preserved. The public HTTPS link is now offline.' -ForegroundColor Green
    Write-Host "Start again: & `"$(Join-Path $PSScriptRoot 'start-demo.cmd')`""
    exit 0
} catch {
    Write-Host "Stop failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
