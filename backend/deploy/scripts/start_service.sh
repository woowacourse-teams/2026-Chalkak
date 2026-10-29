#!/usr/bin/env bash
set -euo pipefail

CONFIG_FILE=/etc/chalkak/application.env
COMPOSE_FILE=/opt/chalkak/db/compose.dev.yml
BACKUP_DIR=/opt/chalkak/backups

/opt/chalkak/bin/check_configuration.sh

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
    health_status="$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' chalkak-dev-postgres 2>/dev/null || true)"
    if [[ "${health_status}" == healthy ]]; then
      break
    fi
    if [[ "${attempt}" -eq 30 ]]; then
      docker logs --tail 200 chalkak-dev-postgres >&2 || true
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

# shellcheck source=blue_green.sh
source /opt/chalkak/bin/blue_green.sh

HEALTH_TIMEOUT_SECONDS=240

deployment_id="${DEPLOYMENT_ID:-}"
if [[ ! "${deployment_id}" =~ ^d-[A-Z0-9]+$ ]]; then
  echo "DEPLOYMENT_ID is missing or invalid: '${deployment_id}'" >&2
  exit 1
fi

release_dir="${RELEASES_DIR}/${deployment_id}"
if [[ ! -f "${release_dir}/application.jar" ]]; then
  echo "Release jar is missing: ${release_dir}/application.jar" >&2
  exit 1
fi

# 이전 배포의 롤백 실패로 nginx가 죽은 포트를 가리키면, 살아 있는 쪽으로 먼저 돌린 뒤 target을 정한다.
bg_reconcile
active_port="$(bg_active_port)"
target_port="$(bg_idle_port "${active_port}")"
container="$(bg_container_name "${target_port}")"

# 대상은 항상 유휴 포트이므로 활성 컨테이너는 지우지 않는다.
docker rm -f "${container}" >/dev/null 2>&1 || true
# 대상이 8080이면 nginx는 8080을 쓰지 않으므로, 남은 레거시 서비스는 정리해도 된다.
if [[ "${target_port}" == 8080 ]] && bg_legacy_active; then
  bg_stop_legacy
fi
if [[ -n "$(ss -ltnH "sport = :${target_port}")" ]]; then
  echo "Port ${target_port} is already in use by another process." >&2
  exit 1
fi

chalkak_uid="$(id -u chalkak)"
chalkak_gid="$(id -g chalkak)"

cleanup_on_failure() {
  local rc=$?
  if (( rc != 0 )); then
    docker logs --tail 200 "${container}" >&2 || true
    docker rm -f "${container}" >/dev/null 2>&1 || true
  fi
}
trap cleanup_on_failure EXIT
trap 'exit 143' TERM

docker run -d \
  --name "${container}" \
  --network host \
  --user "${chalkak_uid}:${chalkak_gid}" \
  --env-file "${CONFIG_FILE}" \
  -e "SERVER_PORT=${target_port}" \
  -e SERVER_ADDRESS=127.0.0.1 \
  -e MALLOC_ARENA_MAX=2 \
  --memory 768m \
  --cap-drop ALL --security-opt no-new-privileges \
  --restart unless-stopped \
  --log-driver json-file --log-opt max-size=10m --log-opt max-file=3 \
  --label "chalkak.release=${deployment_id}" \
  -v "${release_dir}:/opt/chalkak/app:ro" \
  -v /opt/chalkak/logs:/opt/chalkak/logs \
  -v /etc/chalkak/firebase-service-account.json:/etc/chalkak/firebase-service-account.json:ro \
  "${IMAGE}" \
  java -Xmx320m -XX:MarkStackSize=524288 -XX:ReservedCodeCacheSize=64m \
  -Xlog:async \
  "-Xlog:gc*=info,safepoint*=info:file=/opt/chalkak/logs/gc-${target_port}.log:utctime,uptime,level,tags:filecount=5,filesize=10M" \
  -jar /opt/chalkak/app/application.jar >/dev/null
bg_log "container ${container} started (release ${deployment_id})"

# 메모리 압박 시 기존 앱 대신 새 컨테이너가 먼저 종료되도록 oom 점수를 올린다(500). 승격 시 0으로 돌린다.
bg_set_oom_score "${container}" 500 || echo "Warning: could not raise oom_score_adj (kill-first priority) for ${container}" >&2

deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
while (( SECONDS < deadline )); do
  if bg_health_ok "${target_port}"; then
    bg_log "container ${container} healthy"
    exit 0
  fi
  if ! bg_container_running "${target_port}"; then
    echo "Container ${container} stopped before becoming healthy." >&2
    exit 1
  fi
  sleep 2
done

echo "Container ${container} did not become healthy within ${HEALTH_TIMEOUT_SECONDS}s." >&2
exit 1
