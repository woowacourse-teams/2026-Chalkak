#!/usr/bin/env bash
set -euo pipefail
umask 077

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TEST_ROOT=$(mktemp -d)
cleanup() {
  local pid_file pid
  # 회귀를 재현한 구버전에서도 테스트가 만든 덤프만 종료한다.
  for pid_file in "$TEST_ROOT/"*/dump.pid; do
    [[ -f "$pid_file" ]] || continue
    pid=$(cat "$pid_file")
    kill -KILL "$pid" 2>/dev/null || true
  done
  rm -rf -- "$TEST_ROOT"
}
trap cleanup EXIT
trap 'printf "FAIL: line=%s scenario=%s\n" "$LINENO" "${TEST_MODE:-setup}" >&2; cat "${case_dir:-$TEST_ROOT}/output" >&2' ERR
mkdir -p "$TEST_ROOT/bin"
cat > "$TEST_ROOT/bin/pg_dump" <<'MOCK'
#!/usr/bin/env bash
if [[ "$1" == --version ]]; then echo 'pg_dump (PostgreSQL) 18.4'; exit; fi
echo "$$" > "$TEST_CASE/dump.pid"
if [[ "$TEST_MODE" == stubborn ]]; then trap '' TERM; fi
while :; do :; done
MOCK
cat > "$TEST_ROOT/bin/pg_restore" <<'MOCK'
#!/usr/bin/env bash
echo 'pg_restore (PostgreSQL) 18.4'
MOCK
cat > "$TEST_ROOT/bin/rclone" <<'MOCK'
#!/usr/bin/env bash
touch "$TEST_CASE/unexpected-upload"
exit 9
MOCK
chmod +x "$TEST_ROOT/bin/"*
export PATH="$TEST_ROOT/bin:$PATH"

# 실제 예약 → 통합 → DB 백업의 중첩 timeout을 통과한다. AWS와 DB에는 접속하지 않는다.
for TEST_MODE in normal stubborn; do
  export TEST_MODE
  case_dir="$TEST_ROOT/$TEST_MODE"
  export TEST_CASE="$case_dir"
  mkdir -p "$case_dir/state" "$case_dir/work"
  chmod 700 "$case_dir/state"
  mkdir "$case_dir/locks"
  touch "$case_dir/locks/backup.lock"
  chmod 660 "$case_dir/locks/backup.lock"
  touch "$case_dir/ca.pem" "$case_dir/pgpass" "$case_dir/rclone.conf"
  cat > "$case_dir/backup.env" <<EOF
PGHOST=unused
PGPORT=5432
PGDATABASE=fixture
PGUSER=fixture
PGSSLROOTCERT=$case_dir/ca.pem
PGPASSFILE=$case_dir/pgpass
RCLONE_CONFIG=$case_dir/rclone.conf
DB_BACKUP_DESTINATION=backup:bucket/chalkak/db
DB_BACKUP_TEMP_ROOT=$case_dir/work
IMAGE_BACKUP_SOURCE=production:bucket
IMAGE_BACKUP_DESTINATION=backup:bucket/chalkak/images
BACKUP_RUN_DESTINATION=backup:bucket/chalkak/runs
BACKUP_STATE_DIRECTORY=$case_dir/state
BACKUP_LOCK_FILE=$case_dir/locks/backup.lock
BACKUP_MAX_RUNTIME_SECONDS=1
EOF
  chmod 600 "$case_dir/backup.env" "$case_dir/pgpass" "$case_dir/rclone.conf"
  status=0
  bash "$SCRIPT_DIR/scheduled-backup.sh" "$case_dir/backup.env" > "$case_dir/output" 2>&1 || status=$?
  expected=124
  if [[ "$TEST_MODE" == stubborn ]]; then expected=137; fi
  grep -q 'stage=runtime-limit' "$case_dir/output"
  dump_pid=$(cat "$case_dir/dump.pid")
  # 종료한 자식을 init이 회수할 짧은 여유만 준다.
  for _ in {1..50}; do
    if ! kill -0 "$dump_pid" 2>/dev/null; then break; fi
    sleep 0.02
  done
  if kill -0 "$dump_pid" 2>/dev/null; then
    echo 'FAIL: 전체 제한 시간이 지났는데 덤프가 실행 중입니다.' >&2
    exit 1
  fi
  [[ "$status" == "$expected" ]]
  # 자식에게 상속된 잠금도 해제되어 다음 백업이 실행될 수 있어야 한다.
  flock --exclusive --nonblock "$case_dir/locks/backup.lock" true
  [[ ! -e "$case_dir/unexpected-upload" ]]
  [[ -z "$(find "$case_dir/state" -name COMPLETE -print)" ]]
  echo "PASS: 중첩 timeout $TEST_MODE 덤프 종료·잠금 해제·업로드와 완료 기록 차단"
done
