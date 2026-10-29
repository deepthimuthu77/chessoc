param(
    [switch]$NoBuild,
    [ValidateRange(30, 900)][int]$TimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
$composeRoot = Join-Path $PSScriptRoot 'backend'
$composeArgs = @('compose', '--project-directory', $composeRoot, '-f', (Join-Path $composeRoot 'docker-compose.yml'))
$stackStarted = $false

function Wait-Ready([string]$Label, [scriptblock]$Check) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastFailure = 'Not ready yet'
    do {
        try {
            if (& $Check) { Write-Host "$Label ready."; return }
        } catch { $lastFailure = $_.Exception.Message }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $Label. $lastFailure"
}

try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Docker Desktop is required. Install it, then run this script again.'
    }
    $dockerReady = $false
    try { & docker info *> $null; $dockerReady = $LASTEXITCODE -eq 0 } catch {}
    if (-not $dockerReady) {
        $desktop = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
        if (-not (Test-Path -LiteralPath $desktop)) {
            throw 'Start Docker Desktop with the Linux containers engine, then retry.'
        }
        Write-Host 'Starting Docker Desktop...'
        Start-Process -FilePath $desktop -WindowStyle Hidden
        Wait-Ready 'Docker' { & docker info *> $null; $LASTEXITCODE -eq 0 }
    }

    & docker compose version
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose is required. Update Docker Desktop, then retry.' }
    if (-not $env:CHESSOC_HTTP_HOST_PORT) { $env:CHESSOC_HTTP_HOST_PORT = '18080' }

    Write-Host 'Starting Chessoc (database, engine, API, website, and Caddy)...'
    $upArgs = @('up', '-d', '--wait', '--wait-timeout', "$TimeoutSeconds")
    if ($NoBuild) { $upArgs += '--no-build' } else { $upArgs += '--build' }
    & docker @composeArgs @upArgs
    if ($LASTEXITCODE -ne 0) { throw 'Compose startup failed. Check the output above for a port conflict or build error.' }
    $stackStarted = $true

    Wait-Ready 'Website through Caddy' {
        $response = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost/' -TimeoutSec 10
        $response.StatusCode -eq 200 -and $response.Content -match 'Chessoc'
    }
    Wait-Ready 'API and database through Caddy' {
        $response = Invoke-RestMethod -Uri 'http://localhost/api/leaderboard?variant=standard&speed=blitz' -TimeoutSec 10
        $null -ne $response.PSObject.Properties['entries']
    }
    $startFen = [Uri]::EscapeDataString('rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1')
    Wait-Ready 'Real chess engine' {
        $response = Invoke-RestMethod -Uri "http://localhost/api/analyze?variant=standard&fen=$startFen" -TimeoutSec 10
        -not [string]::IsNullOrWhiteSpace($response.bestMove)
    }

    Write-Host ''
    Write-Host 'Chessoc is ready: http://localhost/' -ForegroundColor Green
    Write-Host 'Other devices on the same network: http://<this-PC-LAN-IP>/'
    Write-Host 'HTTPS: https://<this-PC-LAN-IP>/ (trust the Caddy local CA on that device first).'
    Write-Host 'Stop later: docker compose -f backend/docker-compose.yml stop'
    exit 0
} catch {
    Write-Host "Startup failed: $($_.Exception.Message)" -ForegroundColor Red
    if ($stackStarted) { & docker @composeArgs logs --tail 30 server web caddy }
    exit 1
}
