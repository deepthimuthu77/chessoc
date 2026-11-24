#!/usr/bin/env bash
set -e

echo "=== Starting Chessoc All-in-One VPS Service ==="

# 1. PostgreSQL setup (if local DB is used)
if [[ "${CHESSOC_DB_URL:-}" == *"localhost"* || "${CHESSOC_DB_URL:-}" == *"127.0.0.1"* || -z "${CHESSOC_DB_URL:-}" ]]; then
    export CHESSOC_DB_URL="jdbc:postgresql://127.0.0.1:5432/chessoc"
    export CHESSOC_DB_USER="${CHESSOC_DB_USER:-chessoc}"
    export CHESSOC_DB_PASSWORD="${CHESSOC_DB_PASSWORD:-chessoc}"

    PG_DATA="${PGDATA:-/var/lib/postgresql/data}"
    PG_BIN="/usr/lib/postgresql/15/bin"
    if [ ! -d "$PG_BIN" ]; then
        PG_BIN="$(ls -d /usr/lib/postgresql/*/bin 2>/dev/null | head -n 1)"
    fi

    mkdir -p "$PG_DATA" /var/run/postgresql /var/log/postgresql
    chown -R postgres:postgres "$PG_DATA" /var/run/postgresql /var/log/postgresql
    chmod 0700 "$PG_DATA"

    if [ ! -s "$PG_DATA/PG_VERSION" ]; then
        echo "Initializing PostgreSQL database in $PG_DATA..."
        su - postgres -c "$PG_BIN/initdb -D '$PG_DATA' --auth-local=trust --auth-host=scram-sha-256"
        su - postgres -c "$PG_BIN/pg_ctl -D '$PG_DATA' -l /var/log/postgresql/init.log -w start"
        
        echo "Creating database user and database..."
        su - postgres -c "psql -v ON_ERROR_STOP=1 --username postgres" <<EOSQL
CREATE USER ${CHESSOC_DB_USER} WITH PASSWORD '${CHESSOC_DB_PASSWORD}';
CREATE DATABASE chessoc OWNER ${CHESSOC_DB_USER};
EOSQL

        echo "Applying initial schema..."
        su - postgres -c "PGPASSWORD='${CHESSOC_DB_PASSWORD}' psql -v ON_ERROR_STOP=1 -U ${CHESSOC_DB_USER} -d chessoc -f /opt/chessoc/schema.sql"
        su - postgres -c "$PG_BIN/pg_ctl -D '$PG_DATA' -m fast -w stop"
    fi

    echo "Starting PostgreSQL..."
    su - postgres -c "$PG_BIN/pg_ctl -D '$PG_DATA' -l /var/log/postgresql/postgresql.log -w start"
fi

# 2. Token secret persistence
SECRET_DIR="/var/lib/chessoc"
mkdir -p "$SECRET_DIR"
if [ -z "${CHESSOC_TOKEN_SECRET:-}" ]; then
    SECRET_FILE="$SECRET_DIR/token-secret"
    if [ ! -s "$SECRET_FILE" ]; then
        head -c 32 /dev/urandom | base64 | tr -d '\n' > "$SECRET_FILE"
    fi
    export CHESSOC_TOKEN_SECRET="$(cat "$SECRET_FILE")"
fi

# 3. Environment defaults
export CHESSOC_HTTP_PORT="${CHESSOC_HTTP_PORT:-8080}"
export CHESSOC_WS_PORT="${CHESSOC_WS_PORT:-8081}"
export CHESSOC_VALIDATOR_CMD="${CHESSOC_VALIDATOR_CMD:-/opt/vector64/vector64-validator}"
export CHESSOC_SEARCH_CMD="${CHESSOC_SEARCH_CMD:-/opt/vector64/vector64-all}"
export CHESSOC_VALIDATOR_WORKERS="${CHESSOC_VALIDATOR_WORKERS:-2}"
export CHESSOC_SEARCH_WORKERS="${CHESSOC_SEARCH_WORKERS:-2}"

# 4. Start Java backend
echo "Starting Chessoc Server..."
/opt/chessoc-server/bin/chessoc-server &
SERVER_PID=$!

# Wait for server port
for i in {1..30}; do
    if (echo > /dev/tcp/127.0.0.1/$CHESSOC_HTTP_PORT) >/dev/null 2>&1; then
        echo "Chessoc Server ready on port $CHESSOC_HTTP_PORT."
        break
    fi
    sleep 1
done

# 5. Start Next.js web frontend
echo "Starting Next.js Web Frontend..."
PORT=3000 HOSTNAME="0.0.0.0" node /opt/chessoc-web/server.js &
WEB_PID=$!

for i in {1..30}; do
    if (echo > /dev/tcp/127.0.0.1/3000) >/dev/null 2>&1; then
        echo "Next.js Web Frontend ready on port 3000."
        break
    fi
    sleep 1
done

# 6. Optional Cloudflare Tunnel
if [ -n "${CLOUDFLARE_TUNNEL_TOKEN:-}" ]; then
    echo "Starting Cloudflare Tunnel..."
    cloudflared tunnel --protocol http2 --no-autoupdate run --token "$CLOUDFLARE_TUNNEL_TOKEN" &
    TUNNEL_PID=$!
fi

# 7. Configure and start Caddy
if [ -n "${DOMAIN:-}" ] && [ "${DOMAIN:-}" != "localhost" ]; then
    export CADDY_SERVER_NAME="$DOMAIN"
    echo "Configuring Caddy with public domain: $DOMAIN (Auto-TLS on port 80/443)"
else
    export CADDY_SERVER_NAME=":80"
    echo "Configuring Caddy on HTTP port 80"
fi

shutdown() {
    echo "Shutting down Chessoc services..."
    [ -n "$TUNNEL_PID" ] && kill "$TUNNEL_PID" 2>/dev/null || true
    kill "$WEB_PID" 2>/dev/null || true
    kill "$SERVER_PID" 2>/dev/null || true
    if [ -n "$PG_BIN" ] && [ -d "$PG_DATA" ]; then
        su - postgres -c "$PG_BIN/pg_ctl -D '$PG_DATA' -m fast stop" 2>/dev/null || true
    fi
    exit 0
}

trap shutdown SIGINT SIGTERM

echo "Starting Caddy Reverse Proxy..."
caddy run --config /etc/caddy/Caddyfile &
CADDY_PID=$!

wait "$CADDY_PID"
