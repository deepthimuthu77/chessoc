function Get-DemoLanUrls {
    @(
        foreach ($adapter in [Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces()) {
            if ($adapter.OperationalStatus -ne 'Up') { continue }
            $properties = $adapter.GetIPProperties()
            if ($properties.GatewayAddresses.Count -eq 0) { continue }
            foreach ($address in $properties.UnicastAddresses) {
                $ip = $address.Address
                if ($ip.AddressFamily -eq 'InterNetwork' -and -not [Net.IPAddress]::IsLoopback($ip) -and
                    -not $ip.ToString().StartsWith('169.254.')) { "http://$ip/" }
            }
        }
    ) | Sort-Object -Unique
}

function Get-DemoTunnelState([string[]]$ComposeArgs, $Previous) {
    $container = (& docker @ComposeArgs ps -q tunnel | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot query Docker tunnel status.' }
    if (-not $container) { return @{ Running = $false; Url = $null } }
    $started = (& docker inspect --format '{{.State.StartedAt}}' $container | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read tunnel start time.' }
    if ($Previous -and $Previous.Container -eq $container -and $Previous.Started -eq $started -and $Previous.Url) {
        return $Previous
    }
    $logs = (& docker @ComposeArgs logs --no-color --since $started tunnel 2>&1 | Out-String)
    $urls = [regex]::Matches($logs, 'https://[a-z0-9-]+\.trycloudflare\.com')
    $url = if ($urls.Count) { $urls[$urls.Count - 1].Value } else { $null }
    return @{ Running = $true; Container = $container; Started = $started; Url = $url }
}
