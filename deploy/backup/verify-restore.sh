#!/usr/bin/env bash
set -euo pipefail

: "${MYSQL_HOST:?MYSQL_HOST is required}"
: "${MYSQL_PORT:=3306}"
: "${MYSQL_USER:?MYSQL_USER is required}"
: "${MYSQL_PWD:?MYSQL_PWD is required}"
: "${BACKUP_FILE:?BACKUP_FILE is required}"
: "${RESTORE_DATABASE:?RESTORE_DATABASE is required}"

if [[ ! "${RESTORE_DATABASE}" =~ ^[A-Za-z0-9_]+_restore_verify$ ]]; then
  printf 'RESTORE_DATABASE must contain only letters, numbers and underscores, and end with _restore_verify\n' >&2
  exit 2
fi

test -f "${BACKUP_FILE}"
test -f "${BACKUP_FILE}.sha256"
sha256sum --check "${BACKUP_FILE}.sha256"

mysql --host="${MYSQL_HOST}" --port="${MYSQL_PORT}" --user="${MYSQL_USER}" \
  --ssl-mode=VERIFY_IDENTITY \
  --execute="CREATE DATABASE IF NOT EXISTS \`${RESTORE_DATABASE}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"

gzip -dc "${BACKUP_FILE}" | mysql --host="${MYSQL_HOST}" --port="${MYSQL_PORT}" \
  --user="${MYSQL_USER}" --ssl-mode=VERIFY_IDENTITY "${RESTORE_DATABASE}"

mysql --host="${MYSQL_HOST}" --port="${MYSQL_PORT}" --user="${MYSQL_USER}" \
  --ssl-mode=VERIFY_IDENTITY --database="${RESTORE_DATABASE}" \
  --execute="SELECT version,description,success FROM flyway_schema_history ORDER BY installed_rank; SELECT COUNT(*) AS users_count FROM users;"

printf 'Restore verification completed in database: %s\n' "${RESTORE_DATABASE}"
printf 'The verification database is intentionally retained for review; remove it through the approved database process.\n'
