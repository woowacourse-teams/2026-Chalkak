#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077

if [[ $# -lt 2 || $# -gt 3 || ! -r "$1" || ( $# -eq 3 && "$3" != --keep-container ) ]]; then
  echo '사용법: bash db-restore.sh /보호된/backup.env 회차ID [--keep-container]' >&2
  exit 1
fi
run_id=$2
keep_container=${3:-}
[[ "$run_id" =~ ^chalkak-db-[0-9]{8}T[0-9]{6}Z-[[:alnum:]]{8}$ ]] || {
  echo '백업 로그의 정확한 회차ID를 지정하세요.' >&2; exit 1;
}
set -a
source "$1"
set +a
stage=configuration
work_dir=''
container_id=''
started=$(date +%s)
finish() {
  local result=$?
  trap - EXIT
  if [[ $result -ne 0 ]]; then
    printf '%s FAILED stage=%s run=%s\n' "$(date -u +%FT%TZ)" "$stage" "$run_id" >&2
  fi
  if [[ -n "$container_id" && ( $result -ne 0 || -z "$keep_container" ) ]]; then
    docker rm --force "$container_id" >/dev/null 2>&1 || true
  fi
  [[ -z "$work_dir" ]] || rm -rf -- "$work_dir"
  exit "$result"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
[[ -n "${RCLONE_CONFIG:-}" && -r "$RCLONE_CONFIG" && -n "${DB_BACKUP_DESTINATION:-}" ]] || exit 1
[[ "$DB_BACKUP_DESTINATION" =~ ^[[:alnum:]_-]+:[^/[:space:]][^[:space:]]*/db$ ]] || exit 1
for tool in rclone docker sha256sum mktemp; do
  command -v "$tool" >/dev/null || { printf '필수 도구 없음: %s\n' "$tool" >&2; exit 1; }
done
work_dir=$(mktemp -d "${DB_BACKUP_TEMP_ROOT:-/tmp}/chalkak-restore-XXXXXXXX")
remote="$DB_BACKUP_DESTINATION/$run_id"
rclone_options=(--config "$RCLONE_CONFIG" --ask-password=false --retries 3 --low-level-retries 3
  --contimeout 10s --timeout 60s --stats 0 --log-level ERROR)
printf '%s STARTED run=%s\n' "$(date -u +%FT%TZ)" "$run_id"
stage=download
for file in COMPLETE database.dump.sha256 database.dump; do
  rclone copyto "$remote/$file" "$work_dir/$file" "${rclone_options[@]}" --immutable \
    > /dev/null 2> "$work_dir/diagnostic"
done
stage=verify
grep -Fxq "run=$run_id" "$work_dir/COMPLETE"
grep -Fxq 'format=pg_dump-custom' "$work_dir/COMPLETE"
bytes=$(wc -c < "$work_dir/database.dump" | tr -d '[:space:]')
[[ "$bytes" -gt 0 ]] || exit 1
grep -Fxq "bytes=$bytes" "$work_dir/COMPLETE"
[[ "$(cat "$work_dir/database.dump.sha256")" == "$(cd "$work_dir" && sha256sum database.dump)" ]] || exit 1

# 신뢰하는 팀 보관본만 사용한다. 덤프 복원은 보관본의 SQL을 실행한다.
# 기존 DB 주소를 입력받지 않고, 외부 통신·포트·호스트 볼륨이 없는 새 컨테이너만 만든다.
stage=container
container_id=$(docker run --detach --rm --network none --tmpfs /var/lib/postgresql \
  --label chalkak.backup.restore=true --env POSTGRES_HOST_AUTH_METHOD=trust \
  --env POSTGRES_USER=restore --env POSTGRES_DB=restored postgres:18.4 \
  2> "$work_dir/diagnostic")
ready=false
for ((attempt=0; attempt<30; attempt++)); do
  if docker exec "$container_id" pg_isready -h 127.0.0.1 -U restore -d restored >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
[[ "$ready" == true ]] || exit 1
[[ "$(docker exec "$container_id" pg_restore --version)" =~ PostgreSQL\)\ 18\. ]] || exit 1
stage=restore
restore_started=$(date +%s)
docker exec -i "$container_id" pg_restore --username=restore --dbname=restored \
  --no-owner --no-privileges --exit-on-error --single-transaction \
  < "$work_dir/database.dump" > /dev/null 2> "$work_dir/diagnostic"
restore_seconds=$(($(date +%s) - restore_started))
stage=query
docker exec -i "$container_id" psql -X -qAt -U restore -d restored -v ON_ERROR_STOP=1 \
  2> "$work_dir/diagnostic" <<'SQL'
BEGIN READ ONLY;
SET LOCAL statement_timeout = '30s';
SELECT format('SELECT %L || count(*) FROM %I.%I;', table_schema || '.' || table_name || '=', table_schema, table_name)
FROM information_schema.tables
WHERE table_type = 'BASE TABLE' AND table_schema NOT IN ('pg_catalog', 'information_schema')
  AND table_schema !~ '^pg_'
ORDER BY table_schema, table_name
\gexec
ROLLBACK;
SQL
printf '%s RESTORED run=%s total_seconds=%s restore_seconds=%s\n' \
  "$(date -u +%FT%TZ)" "$run_id" "$(($(date +%s) - started))" "$restore_seconds"
if [[ -n "$keep_container" ]]; then
  printf '검증용 컨테이너: %s (확인 후 docker rm --force로 정리)\n' "$container_id"
fi
