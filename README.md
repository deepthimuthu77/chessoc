# Chessoc demo

On Windows, with Docker Desktop installed, run from this folder:

```powershell
.\start-demo.cmd
```

The script starts Docker Desktop if needed, builds and starts the production Compose stack,
waits for PostgreSQL, checks the website and API through Caddy, and asks the bundled real engine
for a move before reporting **ready**. Open http://localhost/ afterwards. Java, Node, and a host
engine installation are not required. The first build needs internet access for images and engine assets.
The launcher also prints your LAN IP and a **public HTTPS link** to share with friends anywhere.
Cloudflare runs in Docker; no separate installation, domain, or Cloudflare account is needed.

From any PowerShell folder, including `C:\Windows\System32`, run:

```powershell
& "D:\Soumik\Java\chessoc\start-demo.cmd"
```

Keep Docker running while friends play. The temporary `https://...trycloudflare.com` address changes
if its tunnel restarts. The latest link is also saved in `.demo/public-url.txt`. Watch automatically
uses polling on Quick Tunnel links because those links do not support SSE; game moves use secure WebSockets.
See [Cloudflare Quick Tunnel limitations](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/).

To run locally and stop an existing public tunnel:

```powershell
.\start-demo.cmd -LocalOnly
```

The launcher generates a persistent local session-signing secret in `.demo/token-secret` instead of
publishing the demo with the default secret. Keep `.demo/` private and preserve it across restarts.
The first run of this update expires sessions signed with the old default; sign in again or start a new guest session.

For a faster restart with the existing images:

```powershell
.\start-demo.cmd -NoBuild
```

Data and existing games stay in Docker volumes. Ports 80, 443, 5432, 8081, 18080, and 3001 must
be available to this stack. The direct API defaults to port 18080 in the script; set
`CHESSOC_HTTP_HOST_PORT` to override it. A failed startup exits with an error instead of reporting ready.

Stop the demo without deleting its data:

```powershell
& "D:\Soumik\Java\chessoc\stop-demo.cmd"
```

The start script prints this exact stop command. Stopping also disables the public tunnel;
the next start prints the new HTTPS address. Docker Desktop itself stays running.

Devices on the same network can use the printed `http://<LAN-IP>/` address. The public Cloudflare
HTTPS URL has a trusted certificate and needs no manual certificate installation. Direct IP-only
HTTPS uses Caddy's local CA; see [backend setup](backend/README.md) for certificate details.
