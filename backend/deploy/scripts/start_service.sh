#!/usr/bin/env bash
set -euo pipefail

CONFIG_FILE=/etc/chalcak/application.env
COMPOSE_FILE=/opt/chalcak/db/compose.dev.yml
BACKUP_DIR=/opt/chalcak/backups

/opt/chalcak/bin/check_configuration.sh

profile="$(awk -F= '$1 == "SPRING_PROFILES_ACTIVE" {print substr($0, index($0, "=") + 1); exit}' "${CONFIG_FILE}")"

if [[ "${profile}" == dev ]]; then
  for command_name in docker gzip; do
    if ! command -v "${command_name}" >/dev/null 2>&1; then
      echo "Required development command is missing: ${command_name}" >&2
      exit 1
    fi
  done

  docker compose --env-file "${CONFIG_FILE}" -f "${COMPOSE_FILE}" up -d postgres

  for attempt in $(seq 1 30); do
    health_status="$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' chalcak-dev-postgres 2>/dev/null || true)"
    if [[ "${health_status}" == healthy ]]; then
      break
    fi
    if [[ "${attempt}" -eq 30 ]]; then
      docker logs --tail 200 chalcak-dev-postgres >&2 || true
      exit 1
    fi
    sleep 2
  done

  install -d -m 0750 -o root -g root "${BACKUP_DIR}"
  backup_path="${BACKUP_DIR}/pre-deploy-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
  docker compose --env-file "${CONFIG_FILE}" -f "${COMPOSE_FILE}" \
    exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' \
    | gzip -9 >"${backup_path}"
  chmod 0600 "${backup_path}"
  find "${BACKUP_DIR}" -type f -name 'pre-deploy-*.sql.gz' -mtime +7 -delete
fi

systemctl restart chalcak-backend.service
