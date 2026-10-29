# Chessoc demo

On Windows, with Docker Desktop installed, run from this folder:

```powershell
.\start-demo.cmd
```

The script starts Docker Desktop if needed, builds and starts the production Compose stack,
waits for PostgreSQL, checks the website and API through Caddy, and asks the bundled real engine
for a move before reporting **ready**. Open http://localhost/ afterwards. Java, Node, and a host
engine installation are not required. The first build needs internet access for images and engine assets.

For a faster restart with the existing images:

```powershell
.\start-demo.cmd -NoBuild
```

Data and existing games stay in Docker volumes. Ports 80, 443, 5432, 8081, 18080, and 3001 must
be available to this stack. The direct API defaults to port 18080 in the script; set
`CHESSOC_HTTP_HOST_PORT` to override it. A failed startup exits with an error instead of reporting ready.

Stop the demo without deleting its data:

```powershell
docker compose -f backend/docker-compose.yml stop
```

Other devices can use `http://<this-PC-LAN-IP>/`. IP-only HTTPS uses Caddy's local CA; each device
must trust that CA before using HTTPS. See [backend setup](backend/README.md) for certificate details.
