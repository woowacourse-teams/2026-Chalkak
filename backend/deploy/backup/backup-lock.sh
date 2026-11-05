#!/usr/bin/env bash

# 결과 디렉터리는 백업 사용자가 쓰지만, 잠금 파일의 이름·소유권은 root만 바꿀 수 있어야 한다.
backup_lock_path_valid() {
  local lock_file=${BACKUP_LOCK_FILE:-/var/lib/chalkak-backup-lock/backup.lock}
  local lock_dir=${lock_file%/*} mode
  [[ "$lock_file" == /* && -d "$lock_dir" && ! -L "$lock_dir" &&
     "$(readlink -f -- "$lock_dir")" == "$lock_dir" &&
     "$(stat -c %u -- "$lock_dir")" == 0 ]] || {
    echo '잠금 디렉터리는 심볼릭 링크가 없는 root 소유 절대 경로여야 합니다.' >&2; return 1;
  }
  mode=$(stat -c %a -- "$lock_dir")
  (( (8#$mode & 0022) == 0 )) || {
    echo '잠금 디렉터리는 root 외 사용자가 변경할 수 없어야 합니다.' >&2; return 1;
  }
  [[ -f "$lock_file" && ! -L "$lock_file" && -r "$lock_file" && -w "$lock_file" &&
     "$(stat -c %u -- "$lock_file")" == 0 && "$(stat -c %a -- "$lock_file")" == 660 &&
     "$(stat -c %h -- "$lock_file")" == 1 ]] || {
    echo 'root가 잠금 파일을 일반 파일·mode 660으로 미리 준비해야 합니다.' >&2; return 1;
  }
}

# 통합 실행의 자식은 같은 열린 파일(fd 9)을 상속한다. 환경변수 플래그만으로 잠금을 건너뛰지 않는다.
backup_lock() {
  local lock_file=${BACKUP_LOCK_FILE:-/var/lib/chalkak-backup-lock/backup.lock}
  local result=0
  command -v flock >/dev/null || { echo '필수 도구 없음: flock' >&2; return 1; }
  backup_lock_path_valid || return 1
  # 다른 파일의 fd 9는 재사용하지 않는다. Linux /proc로 실제 파일의 동일성을 확인한다.
  if [[ ! -e /proc/$$/fd/9 || ! "$lock_file" -ef /proc/$$/fd/9 ]]; then
    exec 9<> "$lock_file" || return 1
  fi
  flock --exclusive --nonblock --conflict-exit-code 75 9 || result=$?
  if [[ $result -eq 75 ]]; then
    printf '%s SKIPPED reason=already-running\n' "$(date -u +%FT%TZ)" >&2
  elif [[ $result -ne 0 ]]; then
    echo '백업 잠금을 획득하지 못했습니다.' >&2
  fi
  return "$result"
}
