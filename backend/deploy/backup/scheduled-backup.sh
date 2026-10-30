#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077

if [[ $# -lt 1 || $# -gt 2 || ! -r "$1" || ( $# -eq 2 && "$2" != --check ) ]]; then
  echo '사용법: bash scheduled-backup.sh /보호된/backup.env [--check]' >&2
  exit 1
fi
config=$1
stage=schedule-configuration
trap 'result=$?; if [[ $result -ne 0 ]]; then printf "%s FAILED stage=%s exit=%s\n" "$(date -u +%FT%TZ)" "$stage" "$result" >&2; fi' EXIT
# 설정은 실행 사용자가 관리하는 신뢰된 셸 파일이다. 검사 중에도 실제 값을 출력하지 않는다.
[[ -f "$config" && ! -L "$config" && -O "$config" && "$(stat -c %a "$config")" == 600 ]] || {
  echo '설정 파일은 실행 사용자 소유의 일반 파일, mode 600이어야 합니다.' >&2; exit 1;
}
set -a
# shellcheck source=/dev/null
source "$config"
set +a
for key in PGHOST PGPORT PGDATABASE PGUSER PGSSLROOTCERT PGPASSFILE RCLONE_CONFIG DB_BACKUP_DESTINATION \
  IMAGE_BACKUP_SOURCE IMAGE_BACKUP_DESTINATION BACKUP_RUN_DESTINATION BACKUP_LOCK_FILE BACKUP_STATE_DIRECTORY; do
  [[ -n "${!key:-}" && "${!key}" != *REPLACE_WITH_* ]] || {
    printf '예약 실행 설정을 준비하세요: %s\n' "$key" >&2; exit 1;
  }
done
for file in "$PGPASSFILE" "$RCLONE_CONFIG"; do
  [[ -f "$file" && ! -L "$file" && -r "$file" && -O "$file" && "$(stat -c %a "$file")" == 600 ]] || {
    echo '인증 설정은 실행 사용자 소유의 일반 파일, mode 600이어야 합니다.' >&2; exit 1;
  }
done
[[ -r "$PGSSLROOTCERT" && -d "$BACKUP_STATE_DIRECTORY" && ! -L "$BACKUP_STATE_DIRECTORY" &&
   -O "$BACKUP_STATE_DIRECTORY" && "$(stat -c %a "$BACKUP_STATE_DIRECTORY")" == 700 ]] || {
  echo 'CA 인증서와 실행 사용자 소유의 mode 700 결과 디렉터리를 준비하세요.' >&2; exit 1;
}
[[ "$BACKUP_LOCK_FILE" == "$BACKUP_STATE_DIRECTORY/backup.lock" && ! -L "$BACKUP_LOCK_FILE" ]] || {
  echo '잠금 파일은 결과 디렉터리의 backup.lock이어야 합니다.' >&2; exit 1;
}
if [[ ! "${BACKUP_MAX_RUNTIME_SECONDS:-}" =~ ^[1-9][0-9]{0,4}$ ]] ||
   (( BACKUP_MAX_RUNTIME_SECONDS >= 86400 )); then
  echo '실측 후 전체 실행 제한을 1~86399초의 정수로 설정하세요: BACKUP_MAX_RUNTIME_SECONDS' >&2
  exit 1
fi
for tool in timeout flock rclone jq pg_dump pg_restore sha256sum cmp sort mktemp; do
  command -v "$tool" >/dev/null || { printf '필수 도구 없음: %s\n' "$tool" >&2; exit 1; }
done
[[ "$(pg_dump --version)" =~ PostgreSQL\)\ 18\. && "$(pg_restore --version)" =~ PostgreSQL\)\ 18\. ]] || {
  echo 'PostgreSQL 18 백업 도구가 필요합니다.' >&2; exit 1;
}
if [[ "${2:-}" == --check ]]; then echo 'READY: 예약 실행 설정·파일 권한·도구 검사 통과 (연결 검증 아님)'; exit 0; fi
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
stage=scheduled-backup
result=0
# oneshot의 RuntimeMaxSec 대신 GNU timeout으로 전체 프로세스 그룹 실행 시간을 제한한다.
timeout --kill-after=10s "${BACKUP_MAX_RUNTIME_SECONDS}s" \
  bash "$script_dir/backup.sh" "$config" || result=$?
if [[ $result -eq 124 || $result -eq 137 ]]; then stage=runtime-limit; fi
exit "$result"
