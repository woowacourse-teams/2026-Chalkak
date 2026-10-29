#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077

# 설정 파일은 실행 사용자가 관리하는 신뢰된 셸 설정만 사용한다.
if [[ $# -ne 1 || ! -r "$1" ]]; then
  echo '사용법: bash db-backup.sh /보호된/backup.env' >&2
  exit 1
fi
set -a
source "$1"
set +a
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/backup-lock.sh"
backup_lock

stage=configuration
work_dir=''
run_id=not-started
finish() {
  local result=$?
  trap - EXIT
  if [[ $result -ne 0 ]]; then
    printf '%s FAILED stage=%s run=%s\n' "$(date -u +%FT%TZ)" "$stage" "$run_id" >&2
  fi
  if [[ -n "$work_dir" && -d "$work_dir" ]]; then
    rm -rf -- "$work_dir"
  fi
  exit "$result"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

for key in PGHOST PGPORT PGDATABASE PGUSER PGSSLROOTCERT PGPASSFILE RCLONE_CONFIG DB_BACKUP_DESTINATION; do
  if [[ -z "${!key:-}" ]]; then
    printf '필수 설정 누락: %s\n' "$key" >&2
    exit 1
  fi
done
for file in "$PGSSLROOTCERT" "$PGPASSFILE" "$RCLONE_CONFIG"; do
  [[ -r "$file" ]] || { echo '인증·연결 설정 파일을 읽을 수 없습니다.' >&2; exit 1; }
done
[[ -z "${PGPASSWORD:-}" ]] || { echo 'PGPASSWORD 대신 PGPASSFILE을 사용하세요.' >&2; exit 1; }
[[ "$DB_BACKUP_DESTINATION" =~ ^[[:alnum:]_-]+:[^/[:space:]][^[:space:]]*/db$ ]] || {
  echo '백업 경로는 remote:bucket/prefix/db 형식이어야 합니다.' >&2
  exit 1
}
for tool in pg_dump pg_restore rclone sha256sum cmp mktemp timeout; do
  command -v "$tool" >/dev/null || { printf '필수 도구 없음: %s\n' "$tool" >&2; exit 1; }
done
[[ "$(pg_dump --version)" =~ PostgreSQL\)\ 18\. ]] || {
  echo 'PostgreSQL 18 pg_dump가 필요합니다.' >&2
  exit 1
}
[[ "$(pg_restore --version)" =~ PostgreSQL\)\ 18\. ]] || {
  echo 'PostgreSQL 18 pg_restore가 필요합니다.' >&2
  exit 1
}

export PGSSLMODE=verify-full PGCONNECT_TIMEOUT=10
export PGAPPNAME=chalkak-db-backup
export PGOPTIONS='-c default_transaction_read_only=on'
work_dir=$(mktemp -d "${DB_BACKUP_TEMP_ROOT:-/tmp}/chalkak-db-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXXXX")
run_id=${work_dir##*/}
destination="$DB_BACKUP_DESTINATION/$run_id"
rclone_options=(--config "$RCLONE_CONFIG" --ask-password=false --retries 3 --low-level-retries 3
  --contimeout 10s --timeout 60s --stats 0 --log-level ERROR)
printf '%s STARTED run=%s\n' "$(date -u +%FT%TZ)" "$run_id"

# 외부 도구의 원문 오류에는 접속 정보가 포함될 수 있어 공개 로그에 보내지 않는다.
stage=dump
timeout --kill-after=10s 5m pg_dump --format=custom --no-owner --no-privileges --no-password --lock-wait-timeout=10s \
  --file="$work_dir/database.dump" > /dev/null 2> "$work_dir/diagnostic"
[[ -s "$work_dir/database.dump" ]] || exit 1
stage=archive-list
pg_restore --list "$work_dir/database.dump" > /dev/null 2> "$work_dir/diagnostic"
stage=checksum
(cd "$work_dir" && sha256sum database.dump > database.dump.sha256)
bytes=$(wc -c < "$work_dir/database.dump" | tr -d '[:space:]')

stage=upload-dump
rclone copyto "$work_dir/database.dump" "$destination/database.dump" \
  "${rclone_options[@]}" --immutable > /dev/null 2> "$work_dir/diagnostic"
stage=upload-checksum
rclone copyto "$work_dir/database.dump.sha256" "$destination/database.dump.sha256" \
  "${rclone_options[@]}" --immutable > /dev/null 2> "$work_dir/diagnostic"

# 크기나 S3 ETag만 믿지 않고 원격 내용을 다시 읽어 비교한다.
stage=verify
rclone check "$work_dir" "$destination" "${rclone_options[@]}" --download --one-way \
  --include /database.dump --include /database.dump.sha256 \
  > /dev/null 2> "$work_dir/diagnostic"

stage=completion
printf 'format=pg_dump-custom\nrun=%s\nverified_at=%s\nbytes=%s\n' \
  "$run_id" "$(date -u +%FT%TZ)" "$bytes" > "$work_dir/COMPLETE"
rclone copyto "$work_dir/COMPLETE" "$destination/COMPLETE" "${rclone_options[@]}" --immutable \
  > /dev/null 2> "$work_dir/diagnostic"
rclone cat "$destination/COMPLETE" "${rclone_options[@]}" 2> "$work_dir/diagnostic" \
  | cmp -s - "$work_dir/COMPLETE"
printf '%s COMPLETE run=%s bytes=%s\n' "$(date -u +%FT%TZ)" "$run_id" "$bytes"
