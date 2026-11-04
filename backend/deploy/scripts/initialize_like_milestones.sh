#!/usr/bin/env bash
set -euo pipefail
set +x

# CodeDeploy hook에서는 호출하지 않는다. 단일 EC2의 1차 배포 완료 후 수동 실행한다.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=blue_green.sh
source "${SCRIPT_DIR}/blue_green.sh"

fail() { echo "Like milestone initialization aborted: $*" >&2; exit 1; }

[[ "$(id -u)" == 0 ]] || fail "run as root"
[[ $# == 3 ]] || fail "usage: $0 <dev|prod> <deployment-id> <database-name>"
expected_profile=$1
expected_release=$2
expected_database=$3
[[ "${expected_profile}" == dev || "${expected_profile}" == prod ]] || fail "invalid profile"
[[ "${expected_release}" =~ ^d-[A-Z0-9]+$ ]] || fail "invalid deployment ID"

for command_name in docker curl ss systemctl pgrep; do
  command -v "${command_name}" >/dev/null || fail "missing command: ${command_name}"
done

check_app() {
  local active_port container other_port running release legacy_status legacy_rc=0 listeners app_pids pid container_pids
  active_port="$(bg_active_port)" || fail "cannot determine active port"
  container="$(bg_container_name "${active_port}")"
  other_port="$(bg_idle_port "${active_port}")"
  running="$(docker ps --format '{{.Names}}')" || fail "cannot list containers"
  running="$(awk '/^chalkak-backend-/' <<<"${running}")"
  [[ "${running}" == "${container}" ]] || fail "expected exactly one active backend container"
  release="$(docker inspect -f '{{index .Config.Labels "chalkak.release"}}' "${container}")" || fail "cannot read release"
  [[ "${release}" == "${expected_release}" ]] || fail "active release differs from requested deployment"
  bg_health_ok "${active_port}" || fail "active backend is unhealthy"
  listeners="$(ss -ltnH "sport = :${other_port}")" || fail "cannot check old port"
  [[ -z "${listeners}" ]] || fail "old backend port is still listening"
  legacy_status="$(systemctl is-active "${LEGACY_SERVICE}" 2>/dev/null)" || legacy_rc=$?
  [[ ${legacy_rc} == 3 || ${legacy_rc} == 4 ]] || fail "legacy service is active or its status is unknown"
  [[ "${legacy_status}" == inactive || "${legacy_status}" == unknown ]] || fail "legacy service is not stopped"

  # 포트가 닫혔어도 Worker가 살아 있을 수 있으므로 JAR 프로세스도 대조한다.
  container_pids="$(docker top "${container}" -eo pid)" || fail "cannot inspect backend processes"
  app_pids="$(pgrep -f '(^|/)java .*application[.]jar')" || fail "cannot identify backend JVM"
  for pid in ${app_pids}; do
    awk -v pid="${pid}" '$1 == pid { found=1 } END { exit !found }' <<<"${container_pids}" \
      || fail "another backend JVM is still running"
  done
  active_container="${container}"
}

check_app
umask 077
runtime_env="$(mktemp)"
trap 'rm -f "${runtime_env}"' EXIT
# 실행 중인 컨테이너 설정을 재사용한다. 비밀값은 출력하거나 인자로 전달하지 않는다.
docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "${active_container}" \
  | awk '/^(DB_HOST|DB_PORT|DB_NAME|DB_USERNAME|DB_PASSWORD|SPRING_PROFILES_ACTIVE|NOTIFICATION_LIKE_MILESTONE_ENABLED)=/' \
  >"${runtime_env}"

read_value() { awk -F= -v key="$1" '$1 == key { print substr($0, index($0, "=") + 1) }' "${runtime_env}"; }
for key in DB_HOST DB_NAME DB_USERNAME DB_PASSWORD SPRING_PROFILES_ACTIVE NOTIFICATION_LIKE_MILESTONE_ENABLED; do
  occurrences="$(awk -F= -v key="${key}" '$1 == key { count++ } END { print count+0 }' "${runtime_env}")"
  [[ "${occurrences}" == 1 && -n "$(read_value "${key}")" ]] || fail "missing or duplicate runtime key: ${key}"
done
[[ "$(read_value SPRING_PROFILES_ACTIVE)" == "${expected_profile}" ]] || fail "profile mismatch"
[[ "$(read_value DB_NAME)" == "${expected_database}" ]] || fail "database mismatch"
[[ "$(read_value NOTIFICATION_LIKE_MILESTONE_ENABLED)" == false ]] || fail "phase1 creation switch must be explicitly false"

# 기존 배포의 Java 이미지와 별도로 PostgreSQL 클라이언트만 사용한다.
docker image inspect postgres:18.4 >/dev/null 2>&1 || docker pull postgres:18.4
# 이미지 준비 중 다른 배포가 시작되지 않았는지 SQL 직전에 다시 확인한다.
check_app
echo "Initializing like milestones: profile=${expected_profile}, release=${expected_release}"
docker run --rm -i --network host --env-file "${runtime_env}" postgres:18.4 \
  sh -c 'export PGHOST="$DB_HOST" PGPORT="${DB_PORT:-5432}" PGDATABASE="$DB_NAME" PGUSER="$DB_USERNAME" PGPASSWORD="$DB_PASSWORD" PGCONNECT_TIMEOUT=10; exec psql --no-psqlrc --no-password --set=ON_ERROR_STOP=1' \
  <"${SCRIPT_DIR}/initialize_like_milestones.sql"
echo "Like milestone initialization completed. Record this result before phase2 activation."
