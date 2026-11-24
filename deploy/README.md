# Chessoc Production Deployment & Engine Hosting Guide

This directory provides the standalone, production-ready all-in-one Docker image and orchestration scripts for **Chessoc** — a self-hosted chess platform and engine hosting infrastructure.

---

## 📦 What the Image Bundles

* **Vector-64 Chess Engine v1.1.1**: Embedded Linux x86_64 binaries (`vector64-all` with NNUE weights + `vector64-validator`).
* **PostgreSQL 16**: Embedded relational database, auto-initialized with schema on first launch.
* **Java 17 Game Server**: High-throughput Sun `HttpServer` (API) + `Java-WebSocket` (real-time game synchronization) on ports 8080/8081.
* **Next.js Web Frontend**: Standalone optimized production build on port 3000.
* **Caddy Reverse Proxy**: Edge router with automatic Let's Encrypt SSL/TLS certificates on port 80/443.
* **Cloudflare Zero Trust Tunnel**: Embedded `cloudflared` client for zero-inbound-port deployments.

---

## 🚀 Quick Start on Any Linux VPS

### Recommended: Production Run (All State Under `/opt/chess`)

To keep your VPS organized and ensure all database records, user profiles, and keys are stored in one place:

```bash
mkdir -p /opt/chess/data /opt/chess/secrets
cd /opt/chess

# If your VPS already has other Docker services running, create an isolated bridge:
docker network create chess_net 2>/dev/null || true

docker run -d \
  --name chessoc \
  --network chess_net \
  --restart unless-stopped \
  -v /opt/chess/data:/var/lib/postgresql/data \
  -v /opt/chess/secrets:/var/lib/chessoc \
  chessoc:latest
```

---

## 🌐 Domain & SSL Options

### Option 1: Direct Domain with Auto-HTTPS (Ports 80 & 443)
If your domain DNS points directly to your VPS IP address:

```bash
docker run -d \
  --name chessoc \
  --network chess_net \
  --restart unless-stopped \
  -p 80:80 \
  -p 443:443 \
  -e DOMAIN="chess.yourdomain.com" \
  -v /opt/chess/data:/var/lib/postgresql/data \
  -v /opt/chess/secrets:/var/lib/chessoc \
  -v chessoc_caddy:/data \
  chessoc:latest
```

### Option 2: Cloudflare Zero Trust Tunnel (Zero Open Inbound Ports)
If routing through a Cloudflare Tunnel:

```bash
docker run -d \
  --name chessoc \
  --network chess_net \
  --restart unless-stopped \
  -e CLOUDFLARE_TUNNEL_TOKEN="your-token" \
  -v /opt/chess/data:/var/lib/postgresql/data \
  -v /opt/chess/secrets:/var/lib/chessoc \
  chessoc:latest
```

---

## ♟️ Custom Engine & Tournament Hosting

Chessoc has zero chess rules hardcoded in the server. You can swap the bundled Vector-64 engine for any UCI engine (Stockfish, Komodo, LC0, Torch, or custom research engines).

### Mounting a Custom Engine Binary
Mount your engine executable into the container and point the worker pool to it:

```bash
docker run -d \
  --name chessoc \
  --network chess_net \
  -v /path/to/my-stockfish:/opt/engine/stockfish:ro \
  -e CHESSOC_VALIDATOR_CMD="/opt/engine/stockfish" \
  -e CHESSOC_SEARCH_CMD="/opt/engine/stockfish" \
  -e CHESSOC_VALIDATOR_WORKERS=4 \
  -e CHESSOC_SEARCH_WORKERS=2 \
  -v /opt/chess/data:/var/lib/postgresql/data \
  -v /opt/chess/secrets:/var/lib/chessoc \
  chessoc:latest
```

---

## 🧹 Complete Uninstallation / Cleanup

To completely remove Chessoc, its database, its container, and its image from your VPS without touching any other services:

```bash
docker rm -f chessoc && docker rmi chessoc:latest && docker network rm chess_net && rm -rf /opt/chess
```
