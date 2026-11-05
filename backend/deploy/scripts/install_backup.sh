#!/usr/bin/env bash
set -euo pipefail
set +x

# 백업을 이미 구성한 서버만 갱신한다. 설정 생성·도구 설치·예약 활성화는 최초 구성에서 수행한다.
config=/etc/chalkak-backup/backup.env
install_dir=/opt/chalkak-backup
state_dir=/var/lib/chalkak-backup
lock_dir=/var/lib/chalkak-backup-lock
unit_dir=/etc/systemd/system
log_dir=/var/log/chalkak-backup
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
source_dir="$script_dir/../backup"

if [[ ! -e "$config" ]]; then
  echo 'SKIPPED: 백업을 구성하지 않은 서버입니다.'
  exit 0
fi
[[ $EUID -eq 0 ]] || { echo '백업 파일 갱신은 root로 실행해야 합니다.' >&2; exit 1; }
id chalkak-backup >/dev/null 2>&1
[[ -f "$config" && ! -L "$config" && -d "$install_dir" && ! -L "$install_dir" &&
   -d "$state_dir" && ! -L "$state_dir" ]] || {
  echo '최초 백업 구성과 보호 경로를 확인하세요.' >&2; exit 1;
}
# 비밀 설정을 root로 source하지 않는다. 백업 실행 사용자 권한에서 잠금 경로만 검사한다.
runuser -u chalkak-backup -- /bin/bash -c '
  set +x
  source "$1"
  [[ "$BACKUP_LOCK_FILE" == "$3/backup.lock" && "$BACKUP_STATE_DIRECTORY" == "$2" ]]
' bash "$config" "$state_dir" "$lock_dir" || {
  echo '백업 결과 경로와 root 관리 공통 잠금 경로를 확인하세요.' >&2; exit 1;
}

# 상위 경로를 root만 변경할 수 있으므로 검사와 열기 사이에 백업 사용자가 바꿔치기할 수 없다.
[[ ! -L "$lock_dir" && "$(readlink -f -- "${lock_dir%/*}")" == "${lock_dir%/*}" ]] || exit 1
if [[ -e "$lock_dir" ]]; then
  [[ -d "$lock_dir" && "$(stat -c %u -- "$lock_dir")" == 0 ]] || exit 1
  lock_mode=$(stat -c %a -- "$lock_dir")
  (( (8#$lock_mode & 0022) == 0 )) || {
    echo 'root 외 사용자가 변경 가능한 잠금 디렉터리는 사용할 수 없습니다.' >&2; exit 1;
  }
fi
install -d -o root -g chalkak-backup -m 750 "$lock_dir"
lock_file="$lock_dir/backup.lock"
if [[ ! -e "$lock_file" && ! -L "$lock_file" ]]; then
  # 최초 설치가 겹쳐도 기존 파일을 교체하지 않는다. 생성한 설치기만 권한을 설정한다.
  if (umask 077; set -o noclobber; : > "$lock_file") 2>/dev/null; then
    chown root:chalkak-backup "$lock_file"
    chmod 660 "$lock_file"
  fi
fi
# 이미 사용 중인 inode를 교체·초기화하지 않는다. 잘못된 기존 파일은 자동 보정하지 않는다.
[[ -f "$lock_file" && ! -L "$lock_file" && "$(stat -c %u -- "$lock_file")" == 0 &&
   "$(stat -c %a -- "$lock_file")" == 660 && "$(stat -c %h -- "$lock_file")" == 1 ]] || exit 1
runuser -u chalkak-backup -- test -r "$lock_file"
runuser -u chalkak-backup -- test -w "$lock_file"

# 실행 중인 예약·수동 백업은 중단하지 않는다. 갱신이 실패하면 앱 배포도 중단하고 나중에 재배포한다.
exec 9<> "$lock_file"
flock --exclusive --nonblock --conflict-exit-code 75 9 || {
  echo '백업 또는 다른 파일 갱신이 실행 중입니다. 완료 후 재배포하세요.' >&2; exit 75;
}

# 전체 디렉터리를 복사하지 않는다. 복구 스크립트·테스트·env·인증 파일은 운영 설치 대상이 아니다.
files=(backup.sh backup-lock.sh db-backup.sh image-backup.sh scheduled-backup.sh report-backup-status.sh)
for file in "${files[@]}"; do
  [[ -f "$source_dir/$file" && ! -L "$source_dir/$file" ]] || {
    printf '백업 배포 파일 누락: %s\n' "$file" >&2; exit 1;
  }
  bash -n "$source_dir/$file"
done
service="$script_dir/../systemd/chalkak-backup.service"
[[ -f "$service" && ! -L "$service" ]] || { echo '백업 서비스 파일이 없습니다.' >&2; exit 1; }
[[ ! -L "$install_dir/releases" && ! -L "$unit_dir/chalkak-backup.service" ]] || exit 1
if [[ -e "$install_dir/current" && ! -L "$install_dir/current" ]]; then
  echo 'current는 배포용 심볼릭 링크여야 합니다.' >&2; exit 1
fi

# cwagent가 읽는 파일에는 비밀값 없는 종료 요약만 둔다. 기존 요약은 지우지 않는다.
[[ ! -L "$log_dir" && ! -L "$log_dir/status.log" ]] || exit 1
install -d -o root -g root -m 755 "$log_dir"
if [[ -e "$log_dir/status.log" ]]; then
  [[ -f "$log_dir/status.log" ]] || exit 1
  chown chalkak-backup:chalkak-backup "$log_dir/status.log"
  chmod 644 "$log_dir/status.log"
else
  install -o chalkak-backup -g chalkak-backup -m 644 /dev/null "$log_dir/status.log"
fi

install -d -o root -g root -m 755 "$install_dir/releases"
release=$(mktemp -d "$install_dir/releases/release.XXXXXXXX")
unit_stage=$(mktemp "$unit_dir/.chalkak-backup.service.XXXXXXXX")
old_target=$(readlink "$install_dir/current" || true)
old_unit="$release/previous.service"
had_unit=false
switched=false
committed=false
if [[ -f "$unit_dir/chalkak-backup.service" ]]; then
  cp -p "$unit_dir/chalkak-backup.service" "$old_unit"
  had_unit=true
fi
finish() {
  local status=$?
  trap - EXIT
  if [[ "$committed" == false && "$switched" == true ]]; then
    if [[ -n "$old_target" ]]; then
      ln -s "$old_target" "$release/rollback"
      mv -Tf "$release/rollback" "$install_dir/current"
    else
      rm -f "$install_dir/current"
    fi
    if [[ "$had_unit" == true ]]; then
      mv -f "$old_unit" "$unit_dir/chalkak-backup.service"
    else
      rm -f "$unit_dir/chalkak-backup.service"
    fi
    systemctl daemon-reload || true
  fi
  rm -f "$unit_stage"
  exit "$status"
}
trap finish EXIT
trap 'exit 143' TERM
trap 'exit 130' INT
chmod 755 "$release"
for file in "${files[@]}"; do
  install -o root -g root -m 644 "$source_dir/$file" "$release/$file"
done
install -o root -g root -m 644 "$service" "$unit_stage"
# 실행 코드는 한 회차 안에서 섞이지 않도록 current의 실제 작업 디렉터리에 고정한다.
ln -s "$release" "$release/next"
switched=true
mv -Tf "$release/next" "$install_dir/current"
mv -f "$unit_stage" "$unit_dir/chalkak-backup.service"
systemctl daemon-reload
committed=true
printf 'UPDATED: 백업 실행 파일 갱신 완료 (백업·복구 실행 없음)\n'
# enable/start/restart 및 백업·복구 명령은 호출하지 않는다.
