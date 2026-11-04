#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TEST_TEMP_DIR}"' EXIT
mkdir -p "${TEST_TEMP_DIR}/stubs"
printf 'server 127.0.0.1:8081;\n' >"${TEST_TEMP_DIR}/upstream"

# 실제 Docker·DB·systemd를 호출하지 않고 실행 차단과 실패 전파만 확인한다.
cat >"${TEST_TEMP_DIR}/stubs/docker" <<'STUB'
#!/usr/bin/env bash
set -euo pipefail
case "$1" in
  ps)
    echo chalkak-backend-8081
    [[ "${CASE}" != two_containers ]] || echo chalkak-backend-8080
    ;;
  top) printf 'PID\n1234\n' ;;
  image) exit 0 ;;
  inspect)
    if [[ "$3" == *Config.Env* ]]; then
      printf 'DB_HOST=localhost\nDB_NAME=chalkak_test\nDB_USERNAME=test\nDB_PASSWORD=do-not-log\nSPRING_PROFILES_ACTIVE=dev\n'
      case "${CASE}" in
        switch_missing) ;;
        switch_on) echo NOTIFICATION_LIKE_MILESTONE_ENABLED=true ;;
        *) echo NOTIFICATION_LIKE_MILESTONE_ENABLED=false ;;
      esac
    elif [[ "${CASE}" == wrong_release ]]; then
      echo d-OLD
    else
      echo d-NEW
    fi
    ;;
  run)
    echo invoked >"${TEST_TEMP_DIR}/sql-called"
    while [[ $# -gt 0 ]]; do
      if [[ "$1" == --env-file ]]; then echo "$2" >"${TEST_TEMP_DIR}/env-path"; fi
      shift
    done
    cat >"${TEST_TEMP_DIR}/submitted.sql"
    [[ "${CASE}" != sql_failure ]] || exit 1
    printf 'initialized_rows\n2\n'
    ;;
  *) exit 1 ;;
esac
STUB
cat >"${TEST_TEMP_DIR}/stubs/id" <<'STUB'
#!/usr/bin/env bash
echo 0
STUB
cat >"${TEST_TEMP_DIR}/stubs/curl" <<'STUB'
#!/usr/bin/env bash
[[ "${CASE}" != unhealthy ]]
STUB
cat >"${TEST_TEMP_DIR}/stubs/ss" <<'STUB'
#!/usr/bin/env bash
if [[ "${CASE}" == port_unknown ]]; then exit 1; fi
if [[ "${CASE}" == old_port ]]; then echo LISTEN; fi
STUB
cat >"${TEST_TEMP_DIR}/stubs/systemctl" <<'STUB'
#!/usr/bin/env bash
case "${CASE}" in
  legacy_active) echo active; exit 0 ;;
  legacy_unknown) echo failed; exit 1 ;;
  *) echo inactive; exit 3 ;;
esac
STUB
cat >"${TEST_TEMP_DIR}/stubs/pgrep" <<'STUB'
#!/usr/bin/env bash
echo 1234
if [[ "${CASE}" == other_jvm ]]; then echo 9876; fi
STUB
chmod +x "${TEST_TEMP_DIR}"/stubs/*
export TEST_TEMP_DIR
export PATH="${TEST_TEMP_DIR}/stubs:${PATH}"
export UPSTREAM_SNIPPET="${TEST_TEMP_DIR}/upstream"
count=0

run_case() {
  local name=$1 expected=$2 database=${3:-chalkak_test} rc=0
  rm -f "${TEST_TEMP_DIR}/sql-called" "${TEST_TEMP_DIR}/env-path"
  CASE="${name}" bash "${SCRIPT_DIR}/initialize_like_milestones.sh" dev d-NEW "${database}" \
    >"${TEST_TEMP_DIR}/output" 2>&1 || rc=$?
  if [[ "${expected}" == success ]]; then
    [[ ${rc} == 0 ]] && grep -q 'initialization completed' "${TEST_TEMP_DIR}/output"
    cmp "${SCRIPT_DIR}/initialize_like_milestones.sql" "${TEST_TEMP_DIR}/submitted.sql"
  else
    [[ ${rc} != 0 ]] || { echo "FAIL ${name}: expected failure"; exit 1; }
    ! grep -q 'initialization completed' "${TEST_TEMP_DIR}/output"
    if [[ "${name}" != sql_failure ]]; then [[ ! -e "${TEST_TEMP_DIR}/sql-called" ]]; fi
  fi
  ! grep -q 'do-not-log' "${TEST_TEMP_DIR}/output"
  if [[ -f "${TEST_TEMP_DIR}/env-path" ]]; then
    [[ ! -e "$(cat "${TEST_TEMP_DIR}/env-path")" ]]
  fi
  count=$((count + 1))
  echo "PASS ${name}"
}

run_case normal success
for name in two_containers wrong_release unhealthy old_port port_unknown legacy_active legacy_unknown other_jvm switch_missing switch_on; do
  run_case "${name}" failure
done
run_case wrong_database failure other_database
run_case sql_failure failure
echo "${count} initialization script checks passed"
