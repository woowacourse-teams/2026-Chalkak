#!/usr/bin/env bash
set -euo pipefail
umask 077
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
REAL_RCLONE=$(command -v rclone)
export REAL_RCLONE
TEST_ROOT=$(mktemp -d)
background_pid=''
cleanup() {
  [[ -z "$background_pid" ]] || { touch "${case_dir}/release"; wait "$background_pid" 2>/dev/null || true; }
  rm -rf -- "$TEST_ROOT"
}
trap cleanup EXIT
trap 'printf "FAIL: line=%s scenario=%s\n" "$LINENO" "${scenario:-setup}" >&2; cat "${case_dir:-/dev/null}/output" >&2' ERR
mkdir "$TEST_ROOT/bin"
cat > "$TEST_ROOT/bin/pg_dump" <<'MOCK'
#!/usr/bin/env bash
[[ "$1" != --version ]] || { echo 'pg_dump (PostgreSQL) 18.4'; exit; }
echo db >> "$TEST_CASE/calls"
if [[ "${TEST_HOLD:-}" == db ]]; then
  touch "$TEST_CASE/ready"
  while [[ ! -e "$TEST_CASE/release" ]]; do sleep 0.05; done
fi
[[ "${TEST_FAULT:-}" != db && "${TEST_FAULT:-}" != both ]] || exit 9
for argument in "$@"; do
  [[ "$argument" != --file=* ]] || printf 'synthetic-dump' > "${argument#--file=}"
done
exit 0
MOCK
cat > "$TEST_ROOT/bin/pg_restore" <<'MOCK'
#!/usr/bin/env bash
[[ "$1" != --version ]] || { echo 'pg_restore (PostgreSQL) 18.4'; exit; }
exit 0
MOCK
cat > "$TEST_ROOT/bin/rclone" <<'WRAPPER'
#!/usr/bin/env bash
set -euo pipefail
case "$1" in lsjson|copy|copyto|check|cat) ;; *) exit 8 ;; esac
if [[ "$1" == lsjson ]]; then
  echo images >> "$TEST_CASE/calls"
  if [[ "${TEST_HOLD:-}" == images ]]; then
    touch "$TEST_CASE/ready"
    while [[ ! -e "$TEST_CASE/release" ]]; do sleep 0.05; done
  fi
  [[ "${TEST_FAULT:-}" != image && "${TEST_FAULT:-}" != both ]] || exit 9
fi
if [[ "$1" == copyto && "$3" == backup:bucket/chalkak/runs/* ]]; then
  if [[ "${TEST_FAULT:-}" == record ]]; then echo SECRET_SENTINEL >&2; exit 9; fi
  if [[ "${TEST_FAULT:-}" == completion && "$3" == */COMPLETE ]]; then exit 9; fi
  if [[ "${TEST_FAULT:-}" == record-corrupt && "$3" == */RESULT ]]; then
    "$REAL_RCLONE" "$@"
    printf 'corrupt' >> "$TEST_CASE/target/${3#backup:}"
    exit 0
  fi
fi
if [[ "${TEST_FAULT:-}" == marker && "$1" == cat && "$2" == backup:bucket/chalkak/runs/*/COMPLETE ]]; then
  echo wrong-marker; exit 0
fi
exec "$REAL_RCLONE" "$@"
WRAPPER
chmod +x "$TEST_ROOT/bin/"*
export PATH="$TEST_ROOT/bin:$PATH"
unset PGPASSWORD
prepare_case() {
  scenario=$1
  case_dir="$TEST_ROOT/$scenario"
  mkdir -p "$case_dir/state" "$case_dir/work" "$case_dir/target"
  for prefix in posts signatures; do
    for type in original thumbnail; do
      mkdir -p "$case_dir/source/bucket/chalkak/$prefix/prod/$type"
      printf 'image' > "$case_dir/source/bucket/chalkak/$prefix/prod/$type/photo.png"
    done
  done
  touch "$case_dir/ca.pem" "$case_dir/pgpass" "$case_dir/calls"
  printf '[production]\ntype = alias\nremote = %s/source\n[backup]\ntype = alias\nremote = %s/target\n' \
    "$case_dir" "$case_dir" > "$case_dir/rclone.conf"
  cat > "$case_dir/backup.env" <<EOF
PGHOST=unused
PGPORT=5432
PGDATABASE=fixture
PGUSER=fixture
PGSSLROOTCERT=$case_dir/ca.pem
PGPASSFILE=$case_dir/pgpass
RCLONE_CONFIG=$case_dir/rclone.conf
DB_BACKUP_DESTINATION=backup:bucket/chalkak/db
IMAGE_BACKUP_SOURCE=production:bucket
IMAGE_BACKUP_DESTINATION=backup:bucket/chalkak/images
DB_BACKUP_TEMP_ROOT=$case_dir/work
IMAGE_BACKUP_TEMP_ROOT=$case_dir/work
BACKUP_LOCK_FILE=$case_dir/backup.lock
BACKUP_STATE_DIRECTORY=$case_dir/state
BACKUP_RUN_DESTINATION=backup:bucket/chalkak/runs
EOF
  export TEST_CASE="$case_dir" TEST_FAULT='' TEST_HOLD=''
}
execute() {
  status=0
  bash "$SCRIPT_DIR/backup.sh" "$case_dir/backup.env" "$@" > "$case_dir/output" 2>&1 || status=$?
  if grep -q SECRET_SENTINEL "$case_dir/output"; then
    echo 'FAIL: 비밀값 노출' >&2; exit 1
  fi
  [[ -z "$(ls -A "$case_dir/work")" ]]
  result=''
  if [[ -d "$case_dir/state" ]]; then result=$(find "$case_dir/state" -name RESULT -print | head -1); fi
}
assert_recorded() {
  [[ -s "$result" ]]
  local remote_result
  remote_result="$case_dir/target/bucket/chalkak/runs/$(basename "${result%/RESULT}")/RESULT"
  cmp "$result" "$remote_result"
  [[ $(stat -c '%a' "$result") == 600 && $(stat -c '%a' "${result%/RESULT}") == 700 ]]
}

prepare_case success
execute
[[ $status -eq 0 && $(head -1 "$case_dir/calls") == db ]]
grep -q '^db_exit=0$' "$result"
grep -q '^images_exit=0$' "$result"
assert_recorded
[[ $(find "$case_dir/target/bucket/chalkak/runs" -name COMPLETE | wc -l) -eq 1 ]]
echo 'PASS: DB → 이미지 실제 전송·검증, 잠금 상속, 로컬·원격 결과와 보호 권한'

for fault in db image both; do
  prepare_case "$fault"
  export TEST_FAULT=$fault
  execute
  [[ $status -ne 0 ]]
  grep -q '^db$' "$case_dir/calls"
  grep -q '^images$' "$case_dir/calls"
  if grep -q ' COMPLETE run=chalkak-backup-' "$case_dir/output"; then
    echo 'FAIL: 실패한 백업을 성공으로 기록' >&2; exit 1
  fi
  case "$fault" in
    db) grep -q '^db_exit=9$' "$result"; grep -q '^images_exit=0$' "$result" ;;
    image) grep -q '^db_exit=0$' "$result"; grep -q '^images_exit=9$' "$result" ;;
    both) grep -q '^db_exit=9$' "$result"; grep -q '^images_exit=9$' "$result" ;;
  esac
  assert_recorded
  [[ -z "$(find "$case_dir/target/bucket/chalkak/runs" -name COMPLETE -print)" ]]
  echo "PASS: $fault 실패여도 두 작업 시도·개별 종료 코드 보존·전체 실패"
done

prepare_case db-configuration
printf '\nunset PGHOST\n' >> "$case_dir/backup.env"
execute
[[ $status -ne 0 ]]
grep -q '^db_exit=1$' "$result"
grep -q '^images_exit=0$' "$result"
assert_recorded
echo 'PASS: DB 설정 누락도 이미지 작업을 막지 않음'

for selection in db images; do
  prepare_case "only-$selection"
  execute "$selection"
  [[ $status -eq 0 ]]
  assert_recorded
  if [[ "$selection" == db ]]; then
    if grep -q images "$case_dir/calls"; then
      echo 'FAIL: 선택하지 않은 이미지 백업 실행' >&2; exit 1
    fi
    grep -q '^images_exit=not-selected$' "$result"
  else
    if grep -q '^db$' "$case_dir/calls"; then
      echo 'FAIL: 선택하지 않은 DB 백업 실행' >&2; exit 1
    fi
    grep -q '^db_exit=not-selected$' "$result"
  fi
  echo "PASS: $selection 개별 재시도와 제외 대상 기록"
done

for fault in record record-corrupt; do
  prepare_case "$fault"
  export TEST_FAULT=$fault
  execute
  [[ $status -ne 0 && -s "$result" ]]
  grep -q 'FAILED stage=record ' "$case_dir/output"
  grep -q '^db_exit=0$' "$result"
  grep -q '^stage=record$' "${result%/RESULT}/OUTCOME"
  echo "PASS: $fault 결과 전송·내용 비교 실패 시 전체 실패, 로컬 결과 유지"
done

for fault in completion marker; do
  prepare_case "$fault"
  export TEST_FAULT=$fault
  execute
  [[ $status -ne 0 ]]
  grep -q 'FAILED stage=completion ' "$case_dir/output"
  if grep -q ' COMPLETE run=chalkak-backup-' "$case_dir/output"; then
    echo 'FAIL: 완료 검증 실패 후 성공 기록' >&2; exit 1
  fi
  echo "PASS: $fault 전체 완료 표식 전송·재조회 실패 미완료 처리"
done

prepare_case duplicate
export TEST_HOLD=db
bash "$SCRIPT_DIR/backup.sh" "$case_dir/backup.env" > "$case_dir/output" 2>&1 &
background_pid=$!
for ((attempt=0; attempt<100; attempt++)); do
  [[ ! -f "$case_dir/ready" ]] || break
  sleep 0.05
done
[[ -f "$case_dir/ready" ]]
for entry in backup db-backup image-backup; do
  status=0
  bash "$SCRIPT_DIR/$entry.sh" "$case_dir/backup.env" > "$case_dir/duplicate" 2>&1 || status=$?
  [[ $status -eq 75 ]]
  grep -q 'SKIPPED reason=already-running' "$case_dir/duplicate"
  [[ $(wc -l < "$case_dir/calls") -eq 1 ]]
  echo "PASS: 실행 중 $entry 직접 호출 차단, 종료 코드 75, 기존 작업 유지"
done
# 종료 요청 중 자식이 아직 처리 중이면 잠금을 유지하고 다음 이미지는 시작하지 않는다.
kill -TERM "$background_pid"
sleep 0.1
status=0
bash "$SCRIPT_DIR/backup.sh" "$case_dir/backup.env" > "$case_dir/duplicate" 2>&1 || status=$?
[[ $status -eq 75 ]]
touch "$case_dir/release"
status=0
wait "$background_pid" || status=$?
background_pid=''
[[ $status -eq 143 ]]
if grep -q images "$case_dir/calls"; then
  echo 'FAIL: 중단 요청 후 이미지 백업 실행' >&2; exit 1
fi
grep -q 'FAILED stage=interrupted ' "$case_dir/output"
[[ $(find "$case_dir/state" -name RESULT | wc -l) -eq 1 ]]
grep -q '^interrupted=true$' "$(find "$case_dir/state" -name RESULT -print)"
echo 'PASS: TERM 전달·자식 종료까지 잠금 유지·다음 작업 차단·로컬 중단 결과'
export TEST_HOLD=''
execute
[[ $status -eq 0 ]]
echo 'PASS: 종료 후 잠금 해제·재실행 (잠금 파일은 삭제하지 않음)'

for worker in db image; do
  prepare_case "direct-$worker"
  export TEST_HOLD=db
  [[ "$worker" == db ]] || export TEST_HOLD=images
  bash "$SCRIPT_DIR/$worker-backup.sh" "$case_dir/backup.env" > "$case_dir/output" 2>&1 &
  background_pid=$!
  for ((attempt=0; attempt<100; attempt++)); do
    [[ ! -f "$case_dir/ready" ]] || break
    sleep 0.05
  done
  [[ -f "$case_dir/ready" ]]
  status=0
  bash "$SCRIPT_DIR/backup.sh" "$case_dir/backup.env" > "$case_dir/duplicate" 2>&1 || status=$?
  [[ $status -eq 75 ]]
  touch "$case_dir/release"
  wait "$background_pid"
  background_pid=''
  echo "PASS: $worker 개별 백업이 실행 중일 때 통합 실행도 차단"
done

prepare_case unrelated-fd
bash "$SCRIPT_DIR/backup.sh" "$case_dir/backup.env" 9>> "$case_dir/other.lock" > "$case_dir/output" 2>&1
echo 'PASS: 상속된 다른 파일의 fd 9를 잠금 우회로 사용하지 않음'

prepare_case unlocked-fd
bash "$SCRIPT_DIR/backup.sh" "$case_dir/backup.env" 9>> "$case_dir/backup.lock" > "$case_dir/output" 2>&1
grep -q ' COMPLETE ' "$case_dir/output"
echo 'PASS: 같은 파일 fd를 상속해도 실제 flock 획득 수행'

for invalid in selection missing-state symlink-lock; do
  prepare_case "$invalid"
  case "$invalid" in
    selection) execute invalid ;;
    missing-state) rmdir "$case_dir/state"; execute ;;
    symlink-lock) touch "$case_dir/other.lock"; ln -s "$case_dir/other.lock" "$case_dir/backup.lock"; execute ;;
  esac
  [[ $status -ne 0 && ! -s "$case_dir/calls" ]]
  echo "PASS: $invalid 설정 거부, 백업 시작 안 함"
done
echo '총 23개 시나리오 통과 (DB 합성 명령, 실제 Linux flock·rclone·내용 비교)'
