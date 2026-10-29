#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077

if [[ $# -lt 1 || $# -gt 2 || ! -r "$1" ]]; then
  echo '사용법: bash backup.sh /보호된/backup.env [all|db|images]' >&2
  exit 1
fi
config=$(cd -- "$(dirname -- "$1")" && pwd)/$(basename -- "$1")
selection=${2:-all}
case "$selection" in all|db|images) ;; *) echo '대상은 all, db, images 중 하나입니다.' >&2; exit 1 ;; esac
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
set -a
# shellcheck source=/dev/null
source "$config"
set +a
source "$script_dir/backup-lock.sh"
backup_lock

for tool in rclone mktemp cmp; do
  command -v "$tool" >/dev/null || { printf '필수 도구 없음: %s\n' "$tool" >&2; exit 1; }
done
[[ -r "${RCLONE_CONFIG:-}" ]] || { echo 'rclone 설정을 읽을 수 없습니다.' >&2; exit 1; }
[[ "${BACKUP_RUN_DESTINATION:-}" =~ ^[[:alnum:]_-]+:[[:alnum:]._-]+/[[:alnum:]_/-]+/runs$ &&
   "/${BACKUP_RUN_DESTINATION#*:}/" != *'/../'* && "/${BACKUP_RUN_DESTINATION#*:}/" != *'/./'* ]] || {
  echo '통합 결과 경로는 remote:bucket/prefix/runs 형식이어야 합니다.' >&2; exit 1;
}
state_dir=${BACKUP_STATE_DIRECTORY:-/var/lib/chalkak-backup}
[[ "$state_dir" == /* && -d "$state_dir" && ! -L "$state_dir" ]] || {
  echo '실행 사용자가 관리하는 결과 디렉터리를 미리 준비하세요.' >&2; exit 1;
}
run_dir=$(mktemp -d "$state_dir/chalkak-backup-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXXXX")
run_id=${run_dir##*/}
started_at=$(date -u +%FT%TZ)
db_exit=not-selected
images_exit=not-selected
[[ "$selection" == images ]] || db_exit=not-started
[[ "$selection" == db ]] || images_exit=not-started
child_pid=''
active_worker=''
interrupted=false
stage=tasks
record_written=false
write_result() {
  printf 'format=chalkak-backup-v1\nrun=%s\nselection=%s\nstarted_at=%s\nfinished_at=%s\ndb_exit=%s\nimages_exit=%s\ninterrupted=%s\n' \
    "$run_id" "$selection" "$started_at" "$(date -u +%FT%TZ)" "$db_exit" "$images_exit" "$interrupted" > "$run_dir/RESULT"
  record_written=true
}
finish() {
  local result=$?
  trap - EXIT
  if [[ "$record_written" == false || "$interrupted" == true ]]; then write_result; fi
  printf 'exit=%s\nstage=%s\n' "$result" "$stage" > "$run_dir/OUTCOME"
  if [[ $result -eq 0 ]]; then
    printf '%s COMPLETE run=%s db_exit=%s images_exit=%s\n' "$(date -u +%FT%TZ)" "$run_id" "$db_exit" "$images_exit"
  else
    printf '%s FAILED stage=%s run=%s db_exit=%s images_exit=%s\n' "$(date -u +%FT%TZ)" "$stage" "$run_id" "$db_exit" "$images_exit" >&2
  fi
  exit "$result"
}
stop() {
  local code=$1
  trap '' INT TERM
  stage=interrupted
  interrupted=true
  if [[ -n "$child_pid" ]]; then
    kill -TERM "$child_pid" 2>/dev/null || true
    wait "$child_pid" 2>/dev/null || true
    if [[ "$active_worker" == db ]]; then db_exit=$code; else images_exit=$code; fi
  fi
  # 중단된 회차는 로컬에 남긴다. 네트워크 기록을 위해 종료를 지연하지 않는다.
  exit "$code"
}
trap finish EXIT
trap 'stop 130' INT
trap 'stop 143' TERM
printf '%s STARTED run=%s selection=%s\n' "$started_at" "$run_id" "$selection"

run_worker() {
  local worker=$1 result=0
  active_worker=$worker
  bash "$script_dir/$worker-backup.sh" "$config" > "$run_dir/$worker.log" 2>&1 &
  child_pid=$!
  wait "$child_pid" || result=$?
  child_pid=''
  active_worker=''
  if [[ "$worker" == db ]]; then db_exit=$result; else images_exit=$result; fi
}
if [[ "$selection" != images ]]; then run_worker db; fi
if [[ "$selection" != db ]]; then run_worker image; fi
write_result

# 결과 기록에 실패해도 로컬 RESULT와 도구별 로그는 남겨 수동 재시도할 수 있다.
stage=record
destination="$BACKUP_RUN_DESTINATION/$run_id"
rclone_options=(--config "$RCLONE_CONFIG" --ask-password=false --retries 3 --low-level-retries 3
  --contimeout 10s --timeout 60s --stats 0 --log-level ERROR --s3-no-check-bucket)
for file in RESULT db.log image.log; do
  [[ -f "$run_dir/$file" ]] || continue
  rclone copyto "$run_dir/$file" "$destination/$file" "${rclone_options[@]}" --immutable \
    > /dev/null 2> "$run_dir/diagnostic"
done
rclone check "$run_dir" "$destination" "${rclone_options[@]}" --download --one-way \
  --include /RESULT --include /db.log --include /image.log > /dev/null 2> "$run_dir/diagnostic"
stage=tasks
if [[ "$db_exit" != 0 && "$db_exit" != not-selected ]] ||
   [[ "$images_exit" != 0 && "$images_exit" != not-selected ]]; then exit 1; fi
stage=completion
printf 'run=%s\nselection=%s\nverified_at=%s\n' "$run_id" "$selection" "$(date -u +%FT%TZ)" > "$run_dir/COMPLETE"
rclone copyto "$run_dir/COMPLETE" "$destination/COMPLETE" "${rclone_options[@]}" --immutable \
  > /dev/null 2> "$run_dir/diagnostic"
rclone cat "$destination/COMPLETE" "${rclone_options[@]}" 2> "$run_dir/diagnostic" | cmp -s - "$run_dir/COMPLETE"
