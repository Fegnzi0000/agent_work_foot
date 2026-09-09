#!/usr/bin/env bash
set -euo pipefail
umask 077

: "${MYSQL_HOST:?MYSQL_HOST is required}"
: "${MYSQL_PORT:=3306}"
: "${MYSQL_DATABASE:?MYSQL_DATABASE is required}"
: "${MYSQL_USER:?MYSQL_USER is required}"
: "${MYSQL_PWD:?MYSQL_PWD is required}"
: "${BACKUP_DIR:=/backups}"

mkdir -p "${BACKUP_DIR}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
target="${BACKUP_DIR}/${MYSQL_DATABASE}-${timestamp}.sql.gz"

mysqldump \
  --host="${MYSQL_HOST}" \
  --port="${MYSQL_PORT}" \
  --user="${MYSQL_USER}" \
  --ssl-mode=VERIFY_IDENTITY \
  --single-transaction \
  --quick \
  --routines \
  --triggers \
  --events \
  --set-gtid-purged=OFF \
  --no-tablespaces \
  "${MYSQL_DATABASE}" | gzip -9 > "${target}"

sha256sum "${target}" > "${target}.sha256"
printf 'Backup created: %s\n' "${target}"
