#!/usr/bin/env bash

# 통합 실행의 자식은 같은 열린 파일(fd 9)을 상속한다. 환경변수 플래그만으로 잠금을 건너뛰지 않는다.
backup_lock() {
  local lock_file=${BACKUP_LOCK_FILE:-/var/lib/chalkak-backup/backup.lock}
  local result=0
  command -v flock >/dev/null || { echo '필수 도구 없음: flock' >&2; return 1; }
  [[ "$lock_file" == /* && ! -L "$lock_file" && -d "${lock_file%/*}" ]] || {
    echo '보호된 디렉터리 안의 절대 잠금 파일 경로가 필요합니다.' >&2; return 1;
  }
  # 다른 파일의 fd 9는 재사용하지 않는다. Linux /proc로 실제 파일의 동일성을 확인한다.
  if [[ ! -e /proc/$$/fd/9 || ! "$lock_file" -ef /proc/$$/fd/9 ]]; then
    exec 9>> "$lock_file" || return 1
  fi
  flock --exclusive --nonblock --conflict-exit-code 75 9 || result=$?
  if [[ $result -eq 75 ]]; then
    printf '%s SKIPPED reason=already-running\n' "$(date -u +%FT%TZ)" >&2
  elif [[ $result -ne 0 ]]; then
    echo '백업 잠금을 획득하지 못했습니다.' >&2
  fi
  return "$result"
}
