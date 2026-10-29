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

old_port="$(bg_active_port)"
new_port="$(bg_idle_port "${old_port}")"
new_container="$(bg_container_name "${new_port}")"

if ! bg_container_running "${new_port}" || ! bg_health_ok "${new_port}"; then
  echo "New container ${new_container} is not running and healthy." >&2
  fail_new_container
fi

status="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 5 "http://127.0.0.1:${new_port}/api/v1/banners" || true)"
if [[ "${status}" != 200 ]]; then
  echo "Smoke check failed on port ${new_port}: HTTP ${status}" >&2
  fail_new_container
fi
bg_log "smoke check OK on port ${new_port}"

if ! bg_switch_upstream "${new_port}"; then
  fail_new_container
fi
bg_log "traffic switched ${old_port} -> ${new_port}"

failures=0
stabilize_deadline=$((SECONDS + STABILIZE_SECONDS))
while (( SECONDS < stabilize_deadline )); do
  sleep "${CHECK_INTERVAL_SECONDS}"
  if bg_health_ok "${new_port}"; then
    failures=0
    continue
  fi
  failures=$((failures + 1))
  echo "Health check failed on port ${new_port} (${failures}/${MAX_CONSECUTIVE_FAILURES})" >&2
  if (( failures >= MAX_CONSECUTIVE_FAILURES )); then
    if bg_health_ok "${old_port}" && bg_switch_upstream "${old_port}"; then
      bg_log "traffic rolled back ${new_port} -> ${old_port}"
      fail_new_container
    else
      echo "Rollback not possible; leaving ${new_container} in place." >&2
    fi
    exit 1
  fi
done
bg_log "stabilization finished (${STABILIZE_SECONDS}s)"

bg_set_oom_score "${new_container}" 0 || echo "Warning: oom_score_adj was not reset for ${new_container}" >&2
# 승격된 정상 릴리스를 정리 실패 때문에 롤백시키지 않는다.
if bg_stop_old "${old_port}"; then
  bg_log "old app on port ${old_port} stopped"
else
  echo "Warning: failed to stop old app on port ${old_port}" >&2
fi

bg_prune_releases "${new_container}" || echo "Warning: release pruning failed" >&2
