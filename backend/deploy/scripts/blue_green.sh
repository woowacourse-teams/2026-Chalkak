#!/usr/bin/env bash
# start_service.sh, validate_service.sh에서 source하는 함수 모음. source만으로는 부작용이 없다.

UPSTREAM_SNIPPET="${UPSTREAM_SNIPPET:-/etc/nginx/snippets/chalkak-upstream.conf}"
RELEASES_DIR="${RELEASES_DIR:-/opt/chalkak/releases}"
IMAGE=eclipse-temurin:25.0.4_7-jre
LEGACY_SERVICE=chalkak-backend.service
LEGACY_APP_DIR="${LEGACY_APP_DIR:-/opt/chalkak/app}"
LEGACY_WANTS_LINK="${LEGACY_WANTS_LINK:-/etc/systemd/system/multi-user.target.wants/${LEGACY_SERVICE}}"
# Spring Boot의 graceful shutdown은 기본 켜져 있고 단계별 대기가 30초(spring.lifecycle.timeout-per-shutdown-phase)다.
# docker stop이 같은 시점에 SIGKILL하지 않도록 그보다 길게 둔다.
STOP_TIMEOUT_SECONDS=40

bg_log() {
  echo "$(date -u +%FT%TZ) $*"
}

# snippet은 primary 한 줄이거나, 전환 중에는 primary + 다른 포트의 backup 한 줄이다. primary 포트를 반환한다.
bg_active_port() {
  local content line primary="" backup="" primaries=0 backups=0
  content="$(cat "${UPSTREAM_SNIPPET}" 2>/dev/null)" || {
    echo "Cannot read upstream snippet: ${UPSTREAM_SNIPPET}" >&2
    return 1
  }
  while IFS= read -r line; do
    if [[ "${line}" =~ ^[[:space:]]*$ || "${line}" =~ ^[[:space:]]*# ]]; then
      continue
    elif [[ "${line}" =~ ^[[:space:]]*server[[:space:]]+127\.0\.0\.1:(8080|8081)\;[[:space:]]*$ ]]; then
      primary="${BASH_REMATCH[1]}"
      primaries=$((primaries + 1))
    elif [[ "${line}" =~ ^[[:space:]]*server[[:space:]]+127\.0\.0\.1:(8080|8081)[[:space:]]+backup\;[[:space:]]*$ ]]; then
      backup="${BASH_REMATCH[1]}"
      backups=$((backups + 1))
    else
      primaries=-1
      break
    fi
  done <<<"${content}"
  if (( primaries == 1 && backups <= 1 )) && [[ "${backup}" != "${primary}" ]]; then
    echo "${primary}"
    return 0
  fi
  echo "Unexpected upstream snippet content: '${content}'" >&2
  return 1
}

bg_idle_port() {
  case "$1" in
    8080) echo 8081 ;;
    8081) echo 8080 ;;
    *)
      echo "Invalid port: '$1'" >&2
      return 1
      ;;
  esac
}

bg_container_name() {
  echo "chalkak-backend-$1"
}

bg_health_ok() {
  curl --fail --silent --max-time 3 "http://127.0.0.1:$1/actuator/health" >/dev/null 2>&1
}

bg_container_running() {
  [[ "$(docker inspect -f '{{.State.Running}}' "$(bg_container_name "$1")" 2>/dev/null || true)" == true ]]
}

# 백업과 임시 파일을 먼저 만들어 검증한 뒤에만 mv로 교체한다. 호출부의 errexit가 꺼져 있어도 안전하도록 단계마다 return 1 한다.
# bg_switch_upstream <port> [backup_port]: backup_port가 있으면 backup 서버로 함께 쓴다.
bg_switch_upstream() {
  local port="$1" backup_port="${2:-}"
  local tmp="${UPSTREAM_SNIPPET}.new"
  local backup="${UPSTREAM_SNIPPET}.prev"
  local expected="server 127.0.0.1:${port};"
  if [[ -n "${backup_port}" ]]; then
    if [[ "${backup_port}" == "${port}" ]]; then
      echo "Backup port must differ from primary: ${port}" >&2
      return 1
    fi
    expected+=$'\n'"server 127.0.0.1:${backup_port} backup;"
  fi

  cp -p "${UPSTREAM_SNIPPET}" "${backup}" || { echo "Cannot back up upstream snippet." >&2; return 1; }
  if ! cp -p "${UPSTREAM_SNIPPET}" "${tmp}" \
    || ! printf '%s\n' "${expected}" >"${tmp}" \
    || [[ "$(cat "${tmp}" 2>/dev/null)" != "${expected}" ]]; then
    echo "Cannot prepare new upstream snippet." >&2
    rm -f "${tmp}" "${backup}"
    return 1
  fi
  if ! mv -f "${tmp}" "${UPSTREAM_SNIPPET}"; then
    echo "Cannot replace upstream snippet." >&2
    rm -f "${tmp}" "${backup}"
    return 1
  fi

  if ! nginx -t >&2; then
    mv -f "${backup}" "${UPSTREAM_SNIPPET}" || true
    echo "nginx -t failed; restored previous upstream snippet." >&2
    return 1
  fi
  if ! nginx -s reload; then
    mv -f "${backup}" "${UPSTREAM_SNIPPET}" || true
    echo "nginx reload failed; restored previous upstream snippet." >&2
    return 1
  fi
  rm -f "${backup}"
}

# 이전 배포의 롤백 실패 등으로 nginx가 죽은 포트를 가리키면 살아 있는 쪽으로 먼저 돌린다.
bg_reconcile() {
  local active other
  active="$(bg_active_port)" || return 1
  other="$(bg_idle_port "${active}")"
  if ! bg_health_ok "${active}" && bg_health_ok "${other}"; then
    echo "Active port ${active} is unhealthy but ${other} is healthy; switching upstream to ${other}." >&2
    bg_switch_upstream "${other}" || return 1
  elif bg_health_ok "${active}" && grep -q ' backup;' "${UPSTREAM_SNIPPET}"; then
    # 중단된 배포가 남긴 backup 줄을 정리한다.
    echo "Removing leftover backup line; keeping upstream on ${active}." >&2
    bg_switch_upstream "${active}" || return 1
  fi
}

bg_set_oom_score() {
  local container="$1" score="$2" pid
  pid="$(docker inspect -f '{{.State.Pid}}' "${container}" 2>/dev/null || true)"
  if [[ ! "${pid}" =~ ^[0-9]+$ || "${pid}" -eq 0 || ! -w "/proc/${pid}/oom_score_adj" ]]; then
    echo "Cannot set oom_score_adj for ${container}" >&2
    return 1
  fi
  echo "${score}" >"/proc/${pid}/oom_score_adj"
}

bg_legacy_active() {
  systemctl is-active --quiet "${LEGACY_SERVICE}"
}

# 레거시가 남긴 흔적(wants 링크, enable 상태, 앱 디렉터리)이 있을 때만 정리한다. 없으면 아무것도 하지 않는다.
bg_cleanup_legacy() {
  if [[ -e "${LEGACY_WANTS_LINK}" || -L "${LEGACY_WANTS_LINK}" || -d "${LEGACY_APP_DIR}" ]] \
    || systemctl is-enabled --quiet "${LEGACY_SERVICE}" 2>/dev/null; then
    systemctl disable "${LEGACY_SERVICE}" || true
    rm -f "${LEGACY_WANTS_LINK}"
    systemctl daemon-reload
    rm -rf "${LEGACY_APP_DIR}"
  fi
}

bg_stop_legacy() {
  systemctl stop "${LEGACY_SERVICE}"
  bg_cleanup_legacy
}

# 포트를 점유한 프로세스의 PID 목록 (root 권한이어야 보인다).
bg_listen_pids() {
  ss -ltnpH "sport = :$1" 2>/dev/null | grep -o 'pid=[0-9]*' | cut -d= -f2 | sort -u || true
}

bg_port_busy() {
  [[ -n "$(ss -ltnH "sport = :$1" 2>/dev/null)" ]]
}

# 이전 앱을 종료하고 포트가 비었는지 확인한다.
# 컨테이너 종료가 실패했거나, 첫 배포에서 CodeDeploy가 unit 파일을 지워 systemctl이 레거시를 못 찾는 경우에도
# 포트를 점유한 프로세스를 직접 종료한다.
bg_stop_old() {
  local port="$1" container pids pid waited settle=0
  container="$(bg_container_name "${port}")"
  if [[ "${port}" == 8080 ]] && bg_legacy_active; then
    bg_stop_legacy || true
  elif docker inspect "${container}" >/dev/null 2>&1; then
    if docker stop --time "${STOP_TIMEOUT_SECONDS}" "${container}" >/dev/null; then
      docker rm "${container}" >/dev/null || true
    else
      # --restart unless-stopped 때문에 PID만 죽이면 컨테이너가 되살아난다. 재시작 정책을 끄고 컨테이너부터 제거한다.
      settle=1
      docker update --restart=no "${container}" >/dev/null 2>&1 || true
      docker rm -f "${container}" >/dev/null 2>&1 || true
    fi
  fi

  if bg_port_busy "${port}"; then
    settle=1
    echo "Port ${port} is still in use after stopping the old app; treating it as the legacy process." >&2
    if [[ "${port}" == 8080 ]]; then
      systemctl stop "${LEGACY_SERVICE}" >/dev/null 2>&1 || true
    fi
    pids="$(bg_listen_pids "${port}")"
    for pid in ${pids}; do
      kill -TERM "${pid}" 2>/dev/null || true
    done
    for (( waited = 0; waited < STOP_TIMEOUT_SECONDS; waited++ )); do
      bg_port_busy "${port}" || break
      sleep 1
    done
    if bg_port_busy "${port}"; then
      for pid in $(bg_listen_pids "${port}"); do
        kill -KILL "${pid}" 2>/dev/null || true
      done
      sleep 1
    fi
  fi

  # 되살아나는 프로세스(재시작 정책 등)를 잡기 위해 조치를 했다면 잠시 뒤 다시 확인한다.
  if (( settle )); then
    sleep 3
  fi
  if bg_port_busy "${port}"; then
    echo "Port ${port} is still in use after stop attempts." >&2
    return 1
  fi
  if [[ "${port}" == 8080 ]]; then
    bg_cleanup_legacy || true
  fi
}

bg_prune_releases() {
  local active_container="$1"
  local keep_others=2
  local protected release_dir name label

  protected="$(
    {
      docker inspect -f '{{index .Config.Labels "chalkak.release"}}' "${active_container}" 2>/dev/null || true
      docker ps -a --format '{{.Label "chalkak.release"}}' 2>/dev/null || true
    } | sort -u
  )"

  while IFS= read -r release_dir; do
    [[ -n "${release_dir}" ]] || continue
    name="${release_dir##*/}"
    if grep -qxF -- "${name}" <<<"${protected}"; then
      continue
    fi
    if (( keep_others > 0 )); then
      keep_others=$((keep_others - 1))
      continue
    fi
    rm -rf -- "${release_dir}"
  done < <(cd "${RELEASES_DIR}" 2>/dev/null && ls -1dt d-*/ 2>/dev/null | sed "s#^#${RELEASES_DIR}/#; s#/\$##")
}
