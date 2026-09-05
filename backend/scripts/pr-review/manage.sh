#!/usr/bin/env bash
set -euo pipefail

SERVICE_LABEL="com.chalkak.backend-pr-review"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SUPPORT_DIR="${HOME}/Library/Application Support/Chalkak PR Review"
CONFIG_FILE="${SUPPORT_DIR}/config.json"
STATE_FILE="${SUPPORT_DIR}/state.json"
LOG_FILE="${SUPPORT_DIR}/watcher.log"
ERROR_LOG_FILE="${SUPPORT_DIR}/watcher-error.log"
PLIST_FILE="${HOME}/Library/LaunchAgents/${SERVICE_LABEL}.plist"
SERVICE_TARGET="gui/${UID}/${SERVICE_LABEL}"

usage() {
  cat <<'EOF'
사용법: ./scripts/pr-review/manage.sh <명령>

  install      최초 설치 또는 설정 갱신
  configure    리뷰 AI 변경
  status       실행 상태와 설정 확인
  start        자동 감시 시작
  stop         자동 감시 중지
  run-once     지금 한 번 확인
  retry        실패한 PR을 다시 확인
  logs         최근 로그 확인
  uninstall    자동 감시와 로컬 설정 제거
EOF
}

require_macos() {
  if [ "$(uname -s)" != "Darwin" ]; then
    echo "현재 자동 실행 설치는 macOS만 지원합니다." >&2
    exit 1
  fi
}

require_command() {
  local name="$1"
  if ! command -v "${name}" >/dev/null 2>&1; then
    echo "필요한 명령어를 찾을 수 없습니다: ${name}" >&2
    exit 1
  fi
}

choose_provider() {
  local selection=""
  echo "백엔드 PR 리뷰에 사용할 AI를 선택하세요."
  echo "1. Codex"
  echo "2. Claude"
  read -r -p "선택: " selection
  case "${selection}" in
    1|codex|Codex) PROVIDER="codex" ;;
    2|claude|Claude) PROVIDER="claude" ;;
    *) echo "1 또는 2를 입력하세요." >&2; exit 1 ;;
  esac
  require_command "${PROVIDER}"
  PROVIDER_COMMAND="$(command -v "${PROVIDER}")"
  "${PROVIDER_COMMAND}" --version >/dev/null
  if [ "${PROVIDER}" = "codex" ]; then
    if ! "${PROVIDER_COMMAND}" login status >/dev/null; then
      echo "Codex 로그인이 필요합니다. 'codex login' 후 다시 실행하세요." >&2
      exit 1
    fi
  else
    if ! "${PROVIDER_COMMAND}" auth status >/dev/null; then
      echo "Claude 로그인이 필요합니다. 'claude auth login' 후 다시 실행하세요." >&2
      exit 1
    fi
  fi
}

unload_service() {
  launchctl bootout "${SERVICE_TARGET}" >/dev/null 2>&1 || true
}

load_service() {
  launchctl bootstrap "gui/${UID}" "${PLIST_FILE}"
  launchctl enable "${SERVICE_TARGET}"
  launchctl kickstart -k "${SERVICE_TARGET}"
}

write_configuration() {
  local repository="$1"
  local gh_command="$2"
  local python_command="$3"
  mkdir -p "${SUPPORT_DIR}" "$(dirname "${PLIST_FILE}")"
  "${python_command}" "${SCRIPT_DIR}/watcher.py" configure \
    --config "${CONFIG_FILE}" \
    --repo-dir "${BACKEND_DIR}" \
    --repository "${repository}" \
    --gh-command "${gh_command}" \
    --provider "${PROVIDER}" \
    --provider-command "${PROVIDER_COMMAND}" \
    --path "${PATH}" \
    --home "${HOME}"
  touch "${LOG_FILE}" "${ERROR_LOG_FILE}"
  "${python_command}" "${SCRIPT_DIR}/watcher.py" write-plist \
    --config "${CONFIG_FILE}" \
    --python "${python_command}" \
    --output "${PLIST_FILE}" \
    --log "${LOG_FILE}" \
    --error-log "${ERROR_LOG_FILE}"
}

install_service() {
  local gh_command python_command repository
  require_macos
  require_command gh
  require_command python3
  gh_command="$(command -v gh)"
  python_command="$(command -v python3)"
  "${gh_command}" auth status
  repository="$(cd "${BACKEND_DIR}" && "${gh_command}" repo view --json nameWithOwner --jq .nameWithOwner)"
  choose_provider
  write_configuration "${repository}" "${gh_command}" "${python_command}"
  unload_service
  load_service
  echo "설치 완료: ${PROVIDER}로 백엔드 PR을 5분마다 확인합니다."
  echo "상태 확인: ./scripts/pr-review/manage.sh status"
  echo "중지: ./scripts/pr-review/manage.sh stop"
}

configure_provider() {
  local gh_command python_command repository
  require_macos
  if [ ! -f "${CONFIG_FILE}" ]; then
    echo "먼저 install을 실행하세요." >&2
    exit 1
  fi
  require_command gh
  require_command python3
  gh_command="$(command -v gh)"
  python_command="$(command -v python3)"
  "${gh_command}" auth status
  repository="$("${python_command}" -c 'import json,sys; print(json.load(open(sys.argv[1]))["repository"])' "${CONFIG_FILE}")"
  choose_provider
  write_configuration "${repository}" "${gh_command}" "${python_command}"
  unload_service
  load_service
  echo "리뷰 AI를 ${PROVIDER}로 변경했습니다."
}

show_status() {
  require_macos
  if [ ! -f "${CONFIG_FILE}" ]; then
    echo "설치되지 않았습니다."
    exit 1
  fi
  if launchctl print "${SERVICE_TARGET}" >/dev/null 2>&1; then
    echo "자동 감시: 실행 중"
  else
    echo "자동 감시: 중지됨"
  fi
  require_command python3
  "$(command -v python3)" "${SCRIPT_DIR}/watcher.py" status --config "${CONFIG_FILE}"
}

start_service() {
  require_macos
  if [ ! -f "${PLIST_FILE}" ]; then
    echo "먼저 install을 실행하세요." >&2
    exit 1
  fi
  if launchctl print "${SERVICE_TARGET}" >/dev/null 2>&1; then
    launchctl kickstart -k "${SERVICE_TARGET}"
  else
    load_service
  fi
  echo "자동 감시를 시작했습니다."
}

stop_service() {
  require_macos
  unload_service
  echo "자동 감시를 중지했습니다. start로 다시 시작할 수 있습니다."
}

run_once() {
  require_command python3
  if [ ! -f "${CONFIG_FILE}" ]; then
    echo "먼저 install을 실행하세요." >&2
    exit 1
  fi
  "$(command -v python3)" "${SCRIPT_DIR}/watcher.py" run --config "${CONFIG_FILE}" --once
}

retry_failed() {
  require_command python3
  if [ ! -f "${CONFIG_FILE}" ]; then
    echo "먼저 install을 실행하세요." >&2
    exit 1
  fi
  "$(command -v python3)" - "${STATE_FILE}" <<'PY'
import json
from pathlib import Path
import sys

path = Path(sys.argv[1])
if path.exists():
    state = json.loads(path.read_text())
    state["runs"] = {
        key: value for key, value in state.get("runs", {}).items()
        if value.get("status") != "failed"
    }
    path.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n")
PY
  run_once
}

show_logs() {
  if [ ! -d "${SUPPORT_DIR}" ]; then
    echo "로그가 없습니다."
    exit 1
  fi
  tail -n 100 "${LOG_FILE}" "${ERROR_LOG_FILE}"
}

uninstall_service() {
  require_macos
  unload_service
  rm -f "${PLIST_FILE}"
  rm -rf "${SUPPORT_DIR}"
  echo "자동 감시와 로컬 설정을 제거했습니다."
}

case "${1:-}" in
  install) install_service ;;
  configure) configure_provider ;;
  status) show_status ;;
  start) start_service ;;
  stop) stop_service ;;
  run-once) run_once ;;
  retry) retry_failed ;;
  logs) show_logs ;;
  uninstall) uninstall_service ;;
  help|-h|--help|"") usage ;;
  *) usage >&2; exit 1 ;;
esac
