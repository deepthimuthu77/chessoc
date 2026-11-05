param([switch]$LocalOnly, [switch]$Once, [ValidateRange(2, 60)][int]$PollSeconds = 5)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'demo-network.ps1')
$composeRoot = Join-Path $PSScriptRoot 'backend'
$composeArgs = @('compose', '--project-directory', $composeRoot, '-f', (Join-Path $composeRoot 'docker-compose.yml'), '--profile', 'public')
$demoRoot = Join-Path $PSScriptRoot '.demo'
New-Item -ItemType Directory -Force -Path $demoRoot | Out-Null
$previous = $null
$tunnel = $null
$lastError = $null

Write-Host 'Watching addresses. Network/tunnel changes will print here. Ctrl+C stops this monitor only.'
do {
    try {
        $server = (& docker @composeArgs ps --status running -q server | Out-String).Trim()
        if ($LASTEXITCODE -ne 0) { throw 'Cannot reach Docker. Waiting for it to return.' }
        if (-not $server) {
            @{ running = $false; lanUrls = @(); publicUrl = $null; updatedAt = (Get-Date).ToString('o') } |
                ConvertTo-Json | Set-Content -LiteralPath (Join-Path $demoRoot 'addresses.json')
            Write-Host 'Chessoc is stopped. Address monitoring finished.'
            break
        }
        $lanUrls = @(Get-DemoLanUrls)
        if (-not $LocalOnly) { $tunnel = Get-DemoTunnelState $composeArgs $tunnel }
        $url = if ($LocalOnly) { $null } else { $tunnel.Url }
        $fingerprint = (@($lanUrls) + @($url)) -join '|'
        if ($fingerprint -ne $previous -or $lastError) {
            Write-Host "`n[$(Get-Date -Format 'HH:mm:ss')] Current addresses:" -ForegroundColor Cyan
            foreach ($lan in $lanUrls) { Write-Host "Same Wi-Fi/LAN: $lan" }
            if (-not $lanUrls.Count) { Write-Host 'No LAN connection detected. Waiting for the network.' }
            if ($url) {
                Write-Host "SHARE WITH FRIENDS: $url" -ForegroundColor Green
                $url | Set-Content -LiteralPath (Join-Path $demoRoot 'public-url.txt')
            } elseif (-not $LocalOnly) {
                Write-Host 'Public tunnel is starting or stopped; waiting for a current URL.'
                $urlFile = Join-Path $demoRoot 'public-url.txt'
                if (Test-Path -LiteralPath $urlFile) { Remove-Item -LiteralPath $urlFile }
            }
            @{ running = $true; lanUrls = $lanUrls; publicUrl = $url; updatedAt = (Get-Date).ToString('o') } |
                ConvertTo-Json | Set-Content -LiteralPath (Join-Path $demoRoot 'addresses.json')
            $previous = $fingerprint
        }
        $lastError = $null
    } catch {
        if ($lastError -ne $_.Exception.Message) { Write-Host $_.Exception.Message -ForegroundColor Yellow }
        $lastError = $_.Exception.Message
    }
    if ($Once) { break }
    Start-Sleep -Seconds $PollSeconds
} while ($true)
