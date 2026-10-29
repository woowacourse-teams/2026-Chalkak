set -euo pipefail

# 배포 서버(root, /opt/chalkak/bin 존재)에서는 실행하지 않는다. 테스트가 실제 레거시 상태를 건드리면 안 된다.
if [[ "${EUID}" -eq 0 && -e /opt/chalkak/bin/blue_green.sh ]]; then
  echo "Refusing to run: this looks like a deploy host (root and /opt/chalkak/bin/blue_green.sh exists)." >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TEST_TEMP_DIR}"' EXIT

REAL_RM="$(command -v rm)"
TEST_COUNT=0
FAILURE_COUNT=0

# 가짜 시계: sleep을 함수로 바꿔 스크립트 자신의 SECONDS를 앞당기고 스텁이 읽을 파일에도 기록한다.
# 실제로 기다리지 않으며 validate_service.sh는 수정하지 않는다.
sleep() {
  SECONDS=$((SECONDS + $1))
  echo "${SECONDS}" >"${CLOCK_FILE}"
}
export -f sleep
# 이전 앱 종료 실패 경로에서 실제 프로세스를 죽이지 않도록 kill도 기록만 하는 함수로 대체한다.
kill() { echo "kill $*" >>"${CALLS_FILE}"; }
export -f kill

create_fixture() {
  local name="$1"
  local root="${TEST_TEMP_DIR}/${name}"

  mkdir -p "${root}/bin" "${root}/releases" "${root}/stubs"
  sed "s#/opt/chalkak/bin/blue_green.sh#${root}/bin/blue_green.sh#" \
    "${SCRIPT_DIR}/validate_service.sh" >"${root}/bin/validate_service.sh"
  cp "${SCRIPT_DIR}/blue_green.sh" "${root}/bin/blue_green.sh"
  printf 'server 127.0.0.1:8080;\n' >"${root}/upstream.conf"
  # 레거시 정리 경로는 임시 디렉터리를 가리키게 한다(실제 /opt, /etc는 건드리지 않는다).
  mkdir -p "${root}/legacy-app"
  touch "${root}/legacy.wants"
  echo 0 >"${root}/clock"
  echo 0 >"${root}/new_calls"
  echo 0 >"${root}/smoke_calls"

  # rm 가드: 픽스처 밖의 절대 경로를 지우려 하면 기록하고 거부한다.
  cat >"${root}/stubs/rm" <<STUB
#!/usr/bin/env bash
for a; do
  if [[ "\$a" == /* && "\$a" != "${TEST_TEMP_DIR}"/* ]]; then echo "rm \$a" >>"${root}/escape"; exit 99; fi
done
exec "${REAL_RM}" "\$@"
STUB

  # curl: 포트·시계·호출 횟수로 새 앱(8081) 상태를 결정한다. 이전 앱(8080)은 OLD_DOWN이 없으면 정상이다.
  cat >"${root}/stubs/curl" <<'STUB'
#!/usr/bin/env bash
url="${*: -1}"
now="$(cat "${CLOCK_FILE}")"
if [[ "${url}" == */api/v1/banners ]]; then
  smoke_calls=$(( $(cat "${SMOKE_CALLS_FILE}") + 1 ))
  echo "${smoke_calls}" >"${SMOKE_CALLS_FILE}"
  if [[ -n "${SMOKE_500_FROM_CALL:-}" ]] && (( smoke_calls >= SMOKE_500_FROM_CALL )); then
    printf '500'
  else
    printf '200'
  fi
  exit 0
fi
if [[ "${url}" == *:8080/* ]]; then
  [[ -z "${OLD_DOWN:-}" ]] && exit 0
  exit 22
fi
calls=$(( $(cat "${NEW_CALLS_FILE}") + 1 ))
echo "${calls}" >"${NEW_CALLS_FILE}"
if [[ -n "${FAIL_FROM_CALL:-}" ]] && (( calls >= FAIL_FROM_CALL )); then
  exit 22
fi
if [[ -n "${FAIL_FROM:-}" ]] && (( now >= FAIL_FROM )) && { [[ -z "${RECOVER_AT:-}" ]] || (( now < RECOVER_AT )); }; then
  exit 22
fi
exit 0
STUB
  cat >"${root}/stubs/docker" <<'STUB'
#!/usr/bin/env bash
echo "docker $*" >>"${CALLS_FILE}"
if [[ "$1" == inspect ]]; then
  case "$*" in
    *State.Running*) echo true ;;
    *State.Pid*) echo 0 ;;
  esac
fi
exit 0
STUB
  printf '#!/usr/bin/env bash\necho "nginx $*" >>"${CALLS_FILE}"\nexit 0\n' >"${root}/stubs/nginx"
  # 레거시 systemd 서비스는 없는 것으로 취급한다.
  printf '#!/usr/bin/env bash\necho "systemctl $*" >>"${CALLS_FILE}"\nexit 3\n' >"${root}/stubs/systemctl"
  # ss: LISTEN_OLD가 있으면 이전 앱(8080)이 계속 포트를 점유하는 것으로 응답한다.
  cat >"${root}/stubs/ss" <<'STUB'
#!/usr/bin/env bash
if [[ -n "${LISTEN_OLD:-}" && "${*: -1}" == *:8080 ]]; then
  printf 'LISTEN 0 511 127.0.0.1:8080 0.0.0.0:* users:(("java",pid=4242,fd=9))\n'
fi
exit 0
STUB
  chmod +x "${root}/stubs/"*
  : >"${root}/calls"
  printf '%s\n' "${root}"
}

run_validate() {
  local root="$1"
  shift
  env PATH="${root}/stubs:${PATH}" CLOCK_FILE="${root}/clock" NEW_CALLS_FILE="${root}/new_calls" SMOKE_CALLS_FILE="${root}/smoke_calls" \
    CALLS_FILE="${root}/calls" UPSTREAM_SNIPPET="${root}/upstream.conf" RELEASES_DIR="${root}/releases" \
    LEGACY_APP_DIR="${root}/legacy-app" LEGACY_WANTS_LINK="${root}/legacy.wants" \
    STABILIZE_SECONDS=100 "$@" bash "${root}/bin/validate_service.sh" >"${root}/output" 2>&1
}

check() {
  local name="$1" condition="$2"
  if ! eval "${condition}"; then
    echo "FAIL: ${name} (조건 불일치: ${condition})" >&2
    FAILURE_COUNT=$((FAILURE_COUNT + 1))
    return 1
  fi
}

# expect_case 이름 기대종료코드 기대snippet 이전앱종료(yes|no) 새컨테이너삭제(yes|no) [환경변수...]
expect_case() {
  local name="$1" expected_status="$2" expected_snippet="$3" old_stopped="$4" new_removed="$5"
  shift 5
  local root status=0 ok=1
  root="$(create_fixture "${name//[^A-Za-z0-9]/_}")"
  LAST_ROOT="${root}"

  TEST_COUNT=$((TEST_COUNT + 1))
  run_validate "${root}" "$@" || status=$?

  check "${name}: 종료 코드" '[[ "${status}" -eq "${expected_status}" ]]' || ok=0
  check "${name}: snippet" '[[ "$(cat "${root}/upstream.conf")" == "${expected_snippet}" ]]' || ok=0
  check "${name}: 픽스처 밖 rm 없음" '[[ ! -e "${root}/escape" ]]' || ok=0
  if [[ "${old_stopped}" == yes && " $* " != *" LISTEN_OLD=1 "* ]]; then
    # 이전 앱이 정상 종료되어 8080 승격이면 레거시 정리가 임시 대상에 적용되어야 한다.
    check "${name}: 레거시 임시 대상 제거" '[[ ! -e "${root}/legacy-app" && ! -e "${root}/legacy.wants" ]]' || ok=0
  fi
  if [[ "${old_stopped}" == yes ]]; then
    check "${name}: 이전 앱 종료" 'grep -q "docker stop .*chalkak-backend-8080" "${root}/calls"' || ok=0
  else
    check "${name}: 이전 앱 유지" '! grep -q "docker stop" "${root}/calls"' || ok=0
  fi
  if [[ "${new_removed}" == yes ]]; then
    check "${name}: 새 컨테이너 삭제" 'grep -q "docker rm -f chalkak-backend-8081" "${root}/calls"' || ok=0
  else
    check "${name}: 새 컨테이너 유지" '! grep -q "docker rm -f" "${root}/calls"' || ok=0
  fi

  if (( ok == 1 )); then
    echo "PASS: ${name}"
  else
    sed 's/^/  | /' "${root}/output" >&2
  fi
}

PROMOTED=$'server 127.0.0.1:8081;'
OLD_ONLY=$'server 127.0.0.1:8080;'
SWITCHED_WITH_BACKUP=$'server 127.0.0.1:8081;\nserver 127.0.0.1:8080 backup;'

expect_case "a. 안정화 내내 정상이면 승격" 0 "${PROMOTED}" yes no
expect_case "b. 초반 연속 6회 실패면 롤백" 1 "${OLD_ONLY}" no yes FAIL_FROM=5
expect_case "c. 마감 직전 실패가 6회 미만이어도 승격하지 않고 6회까지 관찰 후 롤백" 1 "${OLD_ONLY}" no yes FAIL_FROM=85
expect_case "d. 마감 직전 실패 후 회복하면 회복 뒤 승격" 0 "${PROMOTED}" yes no FAIL_FROM=85 RECOVER_AT=125
# 호출 1회(초기 확인) + 안정화 10회 이후 12번째 호출이 최종 확인이다.
expect_case "e. 안정화는 정상이지만 최종 확인이 실패하면 롤백" 1 "${OLD_ONLY}" no yes FAIL_FROM_CALL=12
# health는 정상이지만 스모크가 500이면(첫 호출은 전환 전 스모크) 6회 실패로 롤백한다.
expect_case "g. health는 정상이고 스모크만 500이 6회 연속이면 롤백" 1 "${OLD_ONLY}" no yes SMOKE_500_FROM_CALL=2
expect_case "f. 최종 확인 실패 시 이전 앱도 비정상이면 롤백 불가" 1 "${SWITCHED_WITH_BACKUP}" no no FAIL_FROM_CALL=12 OLD_DOWN=1

# 이전 앱 종료 후에도 포트가 점유되면 배포는 성공으로 두고 조치 가능한 ERROR 줄을 남긴다.
expect_case "h. 이전 앱 종료 실패는 ERROR 로그만 남기고 배포는 성공" 0 "${PROMOTED}" yes no LISTEN_OLD=1
check "h: ERROR 줄" 'grep -q "ERROR: old app still listening on port 8080; stop it manually" "${LAST_ROOT}/output"' \
  || { FAILURE_COUNT=$((FAILURE_COUNT + 1)); sed 's/^/  | /' "${LAST_ROOT}/output" >&2; }
check "h: 점유 PID에 SIGTERM/SIGKILL 시도" 'grep -q "kill -TERM 4242" "${LAST_ROOT}/calls" && grep -q "kill -KILL 4242" "${LAST_ROOT}/calls"' \
  || FAILURE_COUNT=$((FAILURE_COUNT + 1))

if [[ "${FAILURE_COUNT}" -gt 0 ]]; then
  echo "${FAILURE_COUNT} check(s) failed in ${TEST_COUNT} validate_service tests." >&2
  exit 1
fi

echo "All ${TEST_COUNT} validate_service tests passed."
