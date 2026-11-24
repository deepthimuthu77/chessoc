# Chessoc VPS Deployment Guide

This directory provides the standalone, all-in-one production Docker image for Chessoc.

The image bundles:
- **Vector-64 Chess Engine** (Linux v1.1.1 engine binary + validator)
- **Java 17 Backend Monolith** (Sun HttpServer + WebSocket transport on 8080/8081)
- **Next.js Web Frontend** (Standalone production build on port 3000)
- **Embedded PostgreSQL Database** (Auto-initialized with schema on first run)
- **Caddy Reverse Proxy** (With automatic Let's Encrypt HTTPS support)
- **Cloudflared Tunnel** (Optional built-in Cloudflare Zero Trust tunnel)

---

## 1. Quick Start (Single Docker Command)

### Option A: Running with HTTP on Port 80
```bash
docker run -d \
  --name chessoc \
  --restart unless-stopped \
  -p 80:80 \
  -v chessoc_pgdata:/var/lib/postgresql/data \
  -v chessoc_config:/var/lib/chessoc \
  chessoc:latest
```

### Option B: Running with a Custom Domain & Automatic HTTPS (Let's Encrypt)
Make sure your domain's DNS A/AAAA record points to your VPS IP address:
```bash
docker run -d \
  --name chessoc \
  --restart unless-stopped \
  -p 80:80 \
  -p 443:443 \
  -e DOMAIN="chess.yourdomain.com" \
  -v chessoc_pgdata:/var/lib/postgresql/data \
  -v chessoc_config:/var/lib/chessoc \
  -v chessoc_caddy:/data \
  chessoc:latest
```

### Option C: Running Behind Cloudflare Zero Trust Tunnel
```bash
docker run -d \
  --name chessoc \
  --restart unless-stopped \
  -e CLOUDFLARE_TUNNEL_TOKEN="<your-tunnel-token>" \
  -v chessoc_pgdata:/var/lib/postgresql/data \
  -v chessoc_config:/var/lib/chessoc \
  chessoc:latest
```

---

## 2. Running with Docker Compose

On your VPS, copy `deploy/docker-compose.yml` and run:

```bash
docker compose up -d
```

To configure your domain or Cloudflare tunnel, create a `.env` file beside `docker-compose.yml`:
```env
DOMAIN=chess.yourdomain.com
CLOUDFLARE_TUNNEL_TOKEN=
```

---

## 3. How to Build the Image

### Building directly on your VPS
Clone the repository and build:
```bash
git clone https://github.com/deepthimuthu77/chessoc.git
cd chessoc
docker build -t chessoc:latest -f Dockerfile.vps .
```

### Building locally and pushing to Docker Hub or GHCR
```bash
# Build
docker build -t your-dockerhub-username/chessoc:latest -f Dockerfile.vps .

# Push
docker push your-dockerhub-username/chessoc:latest

# On your VPS:
docker run -d -p 80:80 -p 443:443 -e DOMAIN=chess.yourdomain.com your-dockerhub-username/chessoc:latest
```

---

## 4. Environment Variables Reference

| Variable | Default | Purpose |
|---|---|---|
| `DOMAIN` | *(none)* | Your public domain name (e.g. `chess.example.com`). Automatically configures Caddy for Let's Encrypt SSL/TLS. |
| `CLOUDFLARE_TUNNEL_TOKEN` | *(none)* | Token for Cloudflare Zero Trust Named Tunnel. Automatically connects via HTTP/2 over port 443. |
| `CHESSOC_TOKEN_SECRET` | *(auto-generated)* | HMAC session signing key. Persisted in `/var/lib/chessoc/token-secret` across restarts. |
| `CHESSOC_DB_URL` | `jdbc:postgresql://127.0.0.1:5432/chessoc` | JDBC connection string. Set to point to an external Postgres database if preferred. |
| `CHESSOC_DB_USER` | `chessoc` | Database username. |
| `CHESSOC_DB_PASSWORD` | `chessoc` | Database password. |
| `CHESSOC_VALIDATOR_WORKERS`| `2` | Number of engine validation worker threads. |
| `CHESSOC_SEARCH_WORKERS` | `2` | Number of engine search worker threads. |

---

## 5. Persistent Volumes

| Container Path | Host Volume | Description |
|---|---|---|
| `/var/lib/postgresql/data` | `chessoc_pgdata` | Stores your database files, accounts, ratings, friends, and game history. |
| `/var/lib/chessoc` | `chessoc_config` | Stores the token secret so sessions survive container restarts. |
| `/data` | `chessoc_caddy` | Stores Caddy SSL certificates and ACME keys (for HTTPS renewals). |
