#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077

# CodeDeploy의 각 훅은 별도 프로세스다. root만 지울 수 있는 표시로 훅 사이의 차단도 유지한다.
config=/etc/chalkak-backup/backup.env
lock_dir=/var/lib/chalkak-backup-lock
guard_dir="$lock_dir/deployment"
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
action=${1:-}
[[ $# -eq 1 && "$action" =~ ^(begin|check|finish)$ ]] || {
  echo '사용법: deployment_backup_guard.sh begin|check|finish' >&2; exit 1;
}
[[ $EUID -eq 0 ]] || { echo '배포 차단 관리는 root로 실행하세요.' >&2; exit 1; }
if [[ ! -e "$config" && ! -e "$guard_dir" && ! -L "$guard_dir" ]]; then
  echo 'SKIPPED: 백업을 구성하지 않은 서버입니다.'
  exit 0
fi
deployment_id=${DEPLOYMENT_ID:-}
[[ "$deployment_id" =~ ^d-[A-Z0-9]+$ ]] || { echo '유효한 DEPLOYMENT_ID가 필요합니다.' >&2; exit 1; }

if [[ "$action" == begin ]]; then
  # 옛 실행 파일은 배포 표시를 읽지 못한다. 최초 1회 안전하게 설치한 뒤 훅을 사용한다.
  if ! grep -qx '# chalkak-backup-lock-protocol: 2' /opt/chalkak-backup/current/backup-lock.sh; then
    echo '기존 예약·수동 백업 종료 후 최신 install_backup.sh를 먼저 실행하세요.' >&2; exit 1
  fi
  /bin/bash "$script_dir/install_backup.sh" --prepare-locks
fi
# 백업 사용자에게는 디렉터리 변경 권한이 없다. 표시를 만들거나 지우는 작업은 root끼리도 직렬화한다.
[[ -d "$lock_dir" && ! -L "$lock_dir" && "$(readlink -f -- "$lock_dir")" == "$lock_dir" &&
   "$(stat -c %u -- "$lock_dir")" == 0 && "$(stat -c %a -- "$lock_dir")" == 750 ]] || exit 1
exec 7< "$lock_dir"
flock --exclusive --nonblock --conflict-exit-code 75 7 || {
  echo '다른 배포 차단 작업이 진행 중입니다.' >&2; exit 75;
}

guard_matches() {
  [[ -d "$guard_dir" && ! -L "$guard_dir" && "$(stat -c %u -- "$guard_dir")" == 0 &&
     "$(stat -c %a -- "$guard_dir")" == 700 && -f "$guard_dir/id" && ! -L "$guard_dir/id" &&
     "$(cat "$guard_dir/id")" == "$deployment_id" ]]
}

if [[ "$action" == begin ]]; then
  # 기존 표시가 있으면 자동 만료·덮어쓰기를 하지 않는다. 이전 배포의 진행 여부를 먼저 확인한다.
  if ! mkdir -m 700 "$guard_dir"; then
    echo '이전 배포의 백업 차단이 남아 있습니다. 앱 상태 확인 후 해제하세요.' >&2; exit 1
  fi
  printf '%s\n' "$deployment_id" > "$guard_dir/id"
  timer_active=false
  systemctl is-active --quiet chalkak-backup.timer && timer_active=true
  printf '%s\n' "$timer_active" > "$guard_dir/timer-active"
  wait_pid=''
  cleanup_begin() {
    local status=$?
    trap - EXIT
    trap '' INT TERM
    if [[ -n "$wait_pid" ]]; then
      kill -TERM "$wait_pid" 2>/dev/null || true
      wait "$wait_pid" 2>/dev/null || true
    fi
    if [[ $status -ne 0 ]]; then
      # 아직 새 앱을 시작하지 않았다. 대기 실패 시 기존 백업을 건드리지 않고 예약만 원복한다.
      if [[ "$timer_active" == false ]] || systemctl start chalkak-backup.timer; then
        rm -f "$guard_dir/id" "$guard_dir/timer-active" "$guard_dir/ready"
        rmdir "$guard_dir"
      else
        echo '예약 재개 실패: 배포 차단을 유지합니다. 수동 확인이 필요합니다.' >&2
      fi
    fi
    exit "$status"
  }
  trap cleanup_begin EXIT
  trap 'exit 143' TERM
  trap 'exit 130' INT
  systemctl stop chalkak-backup.timer
  # 비밀 설정은 백업 사용자로만 읽고 숫자 하나만 받는다. CodeDeploy 훅 제한(3600초)보다 짧게 둔다.
  wait_seconds=$(runuser -u chalkak-backup -- /bin/bash -c '
    set +x
    source "$1"
    printf "%s" "${BACKUP_MAX_RUNTIME_SECONDS:-}"
  ' bash "$config")
  [[ "$wait_seconds" =~ ^[1-9][0-9]{0,4}$ ]] && (( wait_seconds < 86400 )) || {
    echo '백업 실행 시간 상한을 확인하세요.' >&2; exit 1;
  }
  (( wait_seconds <= 3300 )) || wait_seconds=3300
  # 이미 대기 중인 예약 전체도 끝나게 한다. 두 잠금의 대기는 하나의 timeout 안에서 합산한다.
  printf 'WAITING: 기존 백업 정상 종료 대기 (최대 %s초)\n' "$wait_seconds"
  timeout --kill-after=10s "$wait_seconds" /bin/bash -c '
    exec 8<> "$1/full-backup.lock"
    flock --exclusive 8
    exec 9<> "$1/backup.lock"
    flock --exclusive 9
  ' bash "$lock_dir" &
  wait_pid=$!
  result=0
  wait "$wait_pid" || result=$?
  wait_pid=''
  [[ $result -eq 0 ]] || exit "$result"
  touch "$guard_dir/ready"
  echo 'BLOCKED: 앱 배포가 끝날 때까지 새 백업을 차단합니다.'
  exit 0
fi

guard_matches || { echo '이 배포의 백업 차단 표시가 없습니다.' >&2; exit 1; }
if [[ "$action" == check ]]; then
  [[ -f "$guard_dir/ready" && ! -L "$guard_dir/ready" ]] || {
    echo '백업 차단 준비가 끝나지 않았습니다. BeforeInstall부터 다시 확인하세요.' >&2; exit 1;
  }
  exit 0
fi

# 성공 훅과 수동 복구 모두 실제 앱 상태를 다시 확인한 뒤 해제한다. 실패하면 표시를 남긴다.
for tool in docker curl ss; do
  command -v "$tool" >/dev/null || { printf '필수 도구 없음: %s\n' "$tool" >&2; exit 1; }
done
# shellcheck source=blue_green.sh
source "$script_dir/blue_green.sh"
active_port=$(bg_active_port)
idle_port=$(bg_idle_port "$active_port")
if ! bg_health_ok "$active_port" || bg_container_running "$idle_port" || bg_port_busy "$idle_port"; then
  echo '백업 차단 유지: 활성 앱의 정상 응답과 이전 앱 종료를 확인하세요.' >&2; exit 1
fi
timer_active=$(cat "$guard_dir/timer-active")
[[ "$timer_active" == true || "$timer_active" == false ]] || exit 1
exec 9<> "$lock_dir/backup.lock"
flock --exclusive --nonblock --conflict-exit-code 75 9
# 타이머가 즉시 실행해도 fd 9 해제 뒤에 시작하므로 표시 정리와 겹치지 않는다.
if [[ "$timer_active" == true ]]; then
  systemctl start chalkak-backup.timer
fi
rm -f "$guard_dir/id" "$guard_dir/timer-active" "$guard_dir/ready"
rmdir "$guard_dir"
echo 'RELEASED: 배포 차단 해제, 예약은 배포 전 활성 상태로 복구했습니다.'
