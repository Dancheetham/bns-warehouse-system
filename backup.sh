#!/usr/bin/env bash
set -euo pipefail

# Takes a full backup (database + connection secrets) of the BNS Warehouse
# System from the command line - the host-side equivalent of clicking
# "Download Full Backup" in Settings -> Backup & Restore, for scripting,
# cron, or whenever the app isn't reachable from a browser right now.
#
# Runs pg_dump INSIDE the postgres container itself (docker compose exec),
# so it's always the exact matching version of pg_dump for this database -
# no client/server version mismatch to worry about, unlike the in-app
# button, which depends on whatever postgres client tools were bundled into
# the API's own Docker image (see backend/Dockerfile).
#
# Produces the exact same .zip layout the in-app button does (dump.sql +
# secrets.env + manifest.json), so either one can be fed to restore.sh or
# uploaded to Settings -> Backup & Restore -> "Restore into THIS system".
#
# Usage:
#   ./backup.sh [output-file.zip]
# Defaults to bns-warehouse-backup-<date>.zip in the current directory.

ORIG_DIR="$(pwd)"
cd "$(dirname "$0")"

if [ ! -f docker-compose.yml ]; then
  echo "ERROR: docker-compose.yml not found - run this from the project root." >&2
  exit 1
fi

if [ ! -f .env ]; then
  echo "ERROR: .env not found in $(pwd) - run this from the project root, with a real .env in place." >&2
  exit 1
fi

# shellcheck disable=SC1091
set -a
source .env
set +a

OUTPUT="${1:-bns-warehouse-backup-$(date +%Y-%m-%d).zip}"
case "$OUTPUT" in
  /*) ;; # already absolute
  *) OUTPUT="$ORIG_DIR/$OUTPUT" ;;
esac

if ! docker compose exec -T postgres true >/dev/null 2>&1; then
  echo "ERROR: the postgres container isn't running - start the stack first (docker compose up)." >&2
  exit 1
fi

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

echo "Dumping database..."
docker compose exec -T postgres pg_dump \
  --no-owner --no-privileges --clean --if-exists \
  -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" > "$WORKDIR/dump.sql"

echo "Writing secrets.env..."
cat > "$WORKDIR/secrets.env" <<EOF
# Written by backup.sh - the real connection secrets this system was running
# with at backup time. Rename this file to .env in a fresh copy of the
# project (or feed this whole zip to restore.sh) to bring a new machine up
# with the exact same settings. See docs/BNS_Warehouse_Setup_Guide.pdf.

POSTGRES_DB=${POSTGRES_DB}
POSTGRES_USER=${POSTGRES_USER}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD}

SMTP_HOST=${SMTP_HOST:-}
SMTP_PORT=${SMTP_PORT:-587}
SMTP_USERNAME=${SMTP_USERNAME:-}
SMTP_PASSWORD=${SMTP_PASSWORD:-}
MAIL_FROM_ADDRESS=${MAIL_FROM_ADDRESS:-}

ALLOW_TEST_DATA_RESET=${ALLOW_TEST_DATA_RESET:-false}

SHOPIFY_SHOP_DOMAIN=${SHOPIFY_SHOP_DOMAIN:-}
SHOPIFY_CLIENT_ID=${SHOPIFY_CLIENT_ID:-}
SHOPIFY_CLIENT_SECRET=${SHOPIFY_CLIENT_SECRET:-}
EOF

echo "Writing manifest.json..."
cat > "$WORKDIR/manifest.json" <<EOF
{
  "backedUpAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "database": "${POSTGRES_DB}",
  "host": "backup.sh"
}
EOF

(cd "$WORKDIR" && zip -q -r "$OUTPUT" dump.sql secrets.env manifest.json)

echo
echo "Done - wrote $OUTPUT"
echo "Keep it somewhere safe - it contains real credentials in plain text."
