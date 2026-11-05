param(
    [switch]$NoBuild,
    [switch]$LocalOnly,
    [switch]$NoWatch,
    [ValidateRange(30, 900)][int]$TimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
$composeRoot = Join-Path $PSScriptRoot 'backend'
$composeArgs = @('compose', '--project-directory', $composeRoot, '-f', (Join-Path $composeRoot 'docker-compose.yml'))
$stackStarted = $false
$demoRoot = Join-Path $PSScriptRoot '.demo'
. (Join-Path $PSScriptRoot 'demo-network.ps1')

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
    New-Item -ItemType Directory -Force -Path $demoRoot | Out-Null
    $secretFile = Join-Path $demoRoot 'token-secret'
    if (-not $env:CHESSOC_TOKEN_SECRET) {
        if (-not (Test-Path -LiteralPath $secretFile)) {
            $bytes = New-Object byte[] 32
            $random = [Security.Cryptography.RandomNumberGenerator]::Create()
            try { $random.GetBytes($bytes) } finally { $random.Dispose() }
            [Convert]::ToBase64String($bytes) | Set-Content -LiteralPath $secretFile -NoNewline
        }
        $env:CHESSOC_TOKEN_SECRET = (Get-Content -LiteralPath $secretFile -Raw).Trim()
    }
    if (-not $LocalOnly) { $composeArgs += @('--profile', 'public') }
    else {
        & docker @composeArgs --profile public stop tunnel
        if ($LASTEXITCODE -ne 0) { throw 'Could not stop the public tunnel.' }
    }

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
    $lanAddresses = @(Get-DemoLanUrls)
    foreach ($url in $lanAddresses) { Write-Host "Same Wi-Fi/LAN: $url" -ForegroundColor Cyan }
    if (-not $lanAddresses) { Write-Host 'No LAN address detected. Connect to Wi-Fi or Ethernet to share locally.' }

    if (-not $LocalOnly) {
        Write-Host 'Waiting for the public HTTPS tunnel...'
        $tunnelId = (& docker @composeArgs ps -q tunnel | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or -not $tunnelId) { throw 'Tunnel container is not running.' }
        $tunnelStartedAt = (& docker inspect --format '{{.State.StartedAt}}' $tunnelId | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or -not $tunnelStartedAt) { throw 'Cannot read tunnel start time.' }
        $tunnelUrl = $null
        $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
        do {
            $logs = (& docker @composeArgs logs --no-color --since $tunnelStartedAt tunnel 2>&1 | Out-String)
            $urls = [regex]::Matches($logs, 'https://[a-z0-9-]+\.trycloudflare\.com')
            if ($urls.Count -gt 0) { $tunnelUrl = $urls[$urls.Count - 1].Value; break }
            Start-Sleep -Seconds 2
        } while ((Get-Date) -lt $deadline)
        if (-not $tunnelUrl) { throw 'No public URL was issued. Check: docker compose -f backend/docker-compose.yml --profile public logs tunnel' }
        Wait-Ready 'Public HTTPS website' {
            $response = Invoke-WebRequest -UseBasicParsing -Uri "$tunnelUrl/" -TimeoutSec 10
            $response.StatusCode -eq 200 -and $response.Content -match 'Chessoc'
        }
        Wait-Ready 'Public HTTPS API' {
            $response = Invoke-RestMethod -Uri "$tunnelUrl/api/leaderboard?variant=standard&speed=blitz" -TimeoutSec 10
            $null -ne $response.PSObject.Properties['entries']
        }
        $tunnelUrl | Set-Content -LiteralPath (Join-Path $demoRoot 'public-url.txt')
        Write-Host "SHARE WITH FRIENDS: $tunnelUrl" -ForegroundColor Green
        Write-Host 'Keep Docker running. This temporary URL changes if the tunnel restarts.'
    }
    Write-Host "STOP COMMAND: & `"$(Join-Path $PSScriptRoot 'stop-demo.cmd')`"" -ForegroundColor Yellow
    if (-not $NoWatch) { & (Join-Path $PSScriptRoot 'watch-demo.ps1') -LocalOnly:$LocalOnly }
    exit 0
} catch {
    Write-Host "Startup failed: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "To stop: & `"$(Join-Path $PSScriptRoot 'stop-demo.cmd')`""
    if ($stackStarted) { & docker @composeArgs logs --tail 30 server web caddy }
    if ($stackStarted -and -not $LocalOnly) { & docker @composeArgs logs --tail 30 tunnel }
    exit 1
}
