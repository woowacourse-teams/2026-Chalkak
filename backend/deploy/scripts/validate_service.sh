#!/usr/bin/env bash
set -euo pipefail

# shellcheck source=blue_green.sh
source /opt/chalkak/bin/blue_green.sh

STABILIZE_SECONDS="${STABILIZE_SECONDS:-600}"
CHECK_INTERVAL_SECONDS=10
MAX_CONSECUTIVE_FAILURES=6

fail_new_container() {
  docker logs --tail 200 "${new_container}" >&2 || true
  docker rm -f "${new_container}" >/dev/null 2>&1 || true
  exit 1
}

smoke_status() {
  curl --silent --output /dev/null --write-out '%{http_code}' --max-time 5 "http://127.0.0.1:${new_port}/api/v1/banners" || true
}

# health는 UP이어도 실제 요청이 500일 수 있고 nginx backup은 HTTP 500을 넘기지 않으므로 스모크도 함께 본다.
new_app_ok() {
  bg_health_ok "${new_port}" && [[ "$(smoke_status)" == 200 ]]
}

rollback_to_old() {
  if bg_health_ok "${old_port}" && bg_switch_upstream "${old_port}"; then
    bg_log "traffic rolled back ${new_port} -> ${old_port}"
    fail_new_container
  fi
  echo "Rollback not possible; leaving ${new_container} in place." >&2
  exit 1
}

old_port="$(bg_active_port)"
new_port="$(bg_idle_port "${old_port}")"
new_container="$(bg_container_name "${new_port}")"

if ! bg_container_running "${new_port}" || ! bg_health_ok "${new_port}"; then
  echo "New container ${new_container} is not running and healthy." >&2
  fail_new_container
fi

status="$(smoke_status)"
if [[ "${status}" != 200 ]]; then
  echo "Smoke check failed on port ${new_port}: HTTP ${status}" >&2
  fail_new_container
fi
bg_log "smoke check OK on port ${new_port}"

# 안정화 동안 이전 앱을 backup으로 둬서 새 앱이 연결을 받지 못하면 곧바로 이전 앱이 처리한다.
if ! bg_switch_upstream "${new_port}" "${old_port}"; then
  fail_new_container
fi
bg_log "traffic switched ${old_port} -> ${new_port} (backup ${old_port})"

failures=0
stabilize_deadline=$((SECONDS + STABILIZE_SECONDS))
# 마감 시각에 실패가 남아 있으면 회복하거나 연속 실패 한도에 이를 때까지 관찰을 이어간다.
while (( SECONDS < stabilize_deadline || failures > 0 )); do
  sleep "${CHECK_INTERVAL_SECONDS}"
  if new_app_ok; then
    failures=0
    continue
  fi
  failures=$((failures + 1))
  echo "Health/smoke check failed on port ${new_port} (${failures}/${MAX_CONSECUTIVE_FAILURES})" >&2
  if (( failures >= MAX_CONSECUTIVE_FAILURES )); then
    rollback_to_old
  fi
done
bg_log "stabilization finished (${STABILIZE_SECONDS}s)"

# 이전 앱을 종료하기 직전에 한 번 더 확인한다.
if ! new_app_ok; then
  echo "Final health/smoke check failed on port ${new_port}" >&2
  rollback_to_old
fi

# 이전 앱을 종료하기 전에 backup 줄을 제거한다. 실패해도 backup은 무해하므로 배포는 유지한다.
if bg_switch_upstream "${new_port}"; then
  bg_log "backup removed (primary ${new_port})"
else
  bg_log "Warning: failed to remove backup ${old_port}; keeping backup line" >&2
fi

bg_set_oom_score "${new_container}" 0 || echo "Warning: oom_score_adj was not reset for ${new_container}" >&2
# 새 앱이 정상 서비스 중이므로 이전 앱 종료 실패로 배포를 실패시키지 않는다(CodeDeploy 자동 롤백이 정상 릴리스를 되돌리게 된다). 운영자가 ERROR 줄을 보고 직접 종료한다.
if bg_stop_old "${old_port}"; then
  bg_log "old app on port ${old_port} stopped"
else
  echo "ERROR: old app still listening on port ${old_port}; stop it manually (new app on port ${new_port} is serving, deploy kept)" >&2
fi

bg_prune_releases "${new_container}" || echo "Warning: release pruning failed" >&2
