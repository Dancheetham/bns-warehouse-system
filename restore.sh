#!/usr/bin/env bash
set -euo pipefail

# Stands up a BNS Warehouse System from a backup .zip - the "spin up a new
# machine with the same data and settings already in it" path. Works with a
# .zip from either Settings -> Backup & Restore's "Download Full Backup"
# button, or backup.sh - same layout either way (dump.sql + secrets.env +
# manifest.json). See docs/BNS_Warehouse_Setup_Guide.pdf, Section 10.2.
#
# Run this on a FRESH copy of the project (unzipped release or git clone),
# ideally with no .env in it yet - Docker only reads .env at container
# start, which is exactly what this script does right after writing it, so
# the backup's secrets (Postgres/SMTP/Shopify credentials) end up applied
# automatically, not just the data.
#
# For restoring data into an ALREADY-RUNNING system instead (leaving that
# machine's own .env alone), use Settings -> Backup & Restore -> "Restore
# into THIS system" in the app itself - not this script.
#
# Usage:
#   ./restore.sh <backup-file>.zip

if [ $# -ne 1 ]; then
  echo "Usage: $0 <backup-file>.zip" >&2
  exit 1
fi

if [ ! -f "$1" ]; then
  echo "ERROR: $1 not found" >&2
  exit 1
fi
BACKUP_FILE="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"

cd "$(dirname "$0")"

if [ ! -f docker-compose.yml ]; then
  echo "ERROR: docker-compose.yml not found - run this from the project root." >&2
  exit 1
fi

if ! command -v unzip >/dev/null 2>&1; then
  echo "ERROR: 'unzip' isn't installed - install it (e.g. 'apt install unzip' / 'apk add unzip') and re-run." >&2
  exit 1
fi

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

echo "Extracting backup..."
unzip -q "$BACKUP_FILE" -d "$WORKDIR"

if [ ! -f "$WORKDIR/dump.sql" ]; then
  echo "ERROR: $BACKUP_FILE doesn't look like a BNS Warehouse System backup - no dump.sql inside it." >&2
  exit 1
fi

if [ -f "$WORKDIR/secrets.env" ]; then
  if [ -f .env ]; then
    BACKUP_ENV=".env.bak-$(date +%Y%m%d-%H%M%S)"
    echo "An .env already exists here - backing it up to $BACKUP_ENV first."
    mv .env "$BACKUP_ENV"
  fi
  echo "Writing .env from the backup's secrets.env..."
  cp "$WORKDIR/secrets.env" .env
else
  echo "No secrets.env in this backup - leaving any existing .env alone."
  if [ ! -f .env ]; then
    echo "ERROR: no .env exists here and this backup didn't carry one - copy .env.example to .env and fill it in first, then re-run." >&2
    exit 1
  fi
fi

# shellcheck disable=SC1091
set -a
source .env
set +a

echo "Starting the database..."
docker compose up -d postgres

echo "Waiting for it to be ready..."
READY=false
for i in $(seq 1 30); do
  if docker compose exec -T postgres pg_isready -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" >/dev/null 2>&1; then
    READY=true
    break
  fi
  sleep 2
done
if [ "$READY" != true ]; then
  echo "ERROR: the database never became ready - check 'docker compose logs postgres'." >&2
  exit 1
fi

echo "Importing the backup (this recreates the schema and all data - safe even on a brand new, empty database)..."
docker compose exec -T postgres psql -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" < "$WORKDIR/dump.sql"

echo "Building and starting the full system..."
docker compose up --build -d

echo
echo "Done."
echo "  Frontend: http://localhost:8081 (or http://<this-machine's-LAN-IP>:8081 from another PC)"
echo "  Log in with whichever Users existed in the backed-up system - not the seeded"
echo "  admin/ChangeMe123! account, unless the backup was itself taken before that was changed."
