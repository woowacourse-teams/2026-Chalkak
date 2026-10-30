#!/usr/bin/env bash
set -euo pipefail
umask 077
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
TEST_ROOT=$(mktemp -d)
trap 'rm -rf -- "$TEST_ROOT"' EXIT
trap 'printf "FAIL: line=%s scenario=%s\n" "$LINENO" "${scenario:-setup}" >&2; cat "$TEST_ROOT/output" >&2' ERR
mkdir -p "$TEST_ROOT/bin" "$TEST_ROOT/runner" "$TEST_ROOT/state"
cp "$SCRIPT_DIR/scheduled-backup.sh" "$TEST_ROOT/runner/"
# 예약 래퍼의 종료 코드·시간 제한을 검사한다. 실제 백업 동작은 backup-test.sh에서 검사한다.
cat > "$TEST_ROOT/runner/backup.sh" <<'MOCK'
#!/usr/bin/env bash
echo called > "$TEST_ROOT/called"
if [[ "$TEST_MODE" == timeout* ]]; then
  if [[ "$TEST_MODE" == timeout-stubborn ]]; then trap '' TERM; fi
  sleep 60 &
  echo "$!" > "$TEST_ROOT/child.pid"
  wait
fi
exit "$TEST_EXIT"
MOCK
for tool in pg_dump pg_restore; do
  cat > "$TEST_ROOT/bin/$tool" <<'MOCK'
#!/usr/bin/env bash
echo "tool (PostgreSQL) ${TEST_PG_MAJOR}.4"
MOCK
done
chmod +x "$TEST_ROOT/bin/"*
export PATH="$TEST_ROOT/bin:$PATH" TEST_ROOT TEST_MODE=normal TEST_EXIT=0 TEST_PG_MAJOR=18
touch "$TEST_ROOT/ca.pem" "$TEST_ROOT/pgpass" "$TEST_ROOT/rclone.conf"
cat > "$TEST_ROOT/template" <<EOF
PGHOST=unused
PGPORT=5432
PGDATABASE=fixture
PGUSER=fixture
PGSSLROOTCERT=$TEST_ROOT/ca.pem
PGPASSFILE=$TEST_ROOT/pgpass
RCLONE_CONFIG=$TEST_ROOT/rclone.conf
DB_BACKUP_DESTINATION=backup:bucket/chalkak/db
IMAGE_BACKUP_SOURCE=production:bucket
IMAGE_BACKUP_DESTINATION=backup:bucket/chalkak/images
BACKUP_RUN_DESTINATION=backup:bucket/chalkak/runs
BACKUP_STATE_DIRECTORY=$TEST_ROOT/state
BACKUP_LOCK_FILE=$TEST_ROOT/state/backup.lock
BACKUP_MAX_RUNTIME_SECONDS=5
EOF
prepare() {
  scenario=$1
  cp "$TEST_ROOT/template" "$TEST_ROOT/backup.env"
  chmod 600 "$TEST_ROOT/backup.env" "$TEST_ROOT/pgpass" "$TEST_ROOT/rclone.conf"
  chmod 700 "$TEST_ROOT/state"
  rm -f "$TEST_ROOT/called"
  export TEST_MODE=normal TEST_EXIT=0 TEST_PG_MAJOR=18
}
execute() {
  status=0
  bash "$TEST_ROOT/runner/scheduled-backup.sh" "$TEST_ROOT/backup.env" "$@" > "$TEST_ROOT/output" 2>&1 || status=$?
}
rejected() {
  execute --check
  [[ "$status" == 1 && ! -e "$TEST_ROOT/called" ]]
  if grep -q READY "$TEST_ROOT/output"; then exit 1; fi
  echo "PASS: $scenario"
}
prepare valid-preflight
execute --check
[[ "$status" == 0 && ! -e "$TEST_ROOT/called" ]]
grep -q 'READY:' "$TEST_ROOT/output"
echo "PASS: $scenario"

for value in '' REPLACE_WITH_MEASURED_SECONDS 0 -1 1.5 86400 999999; do
  prepare "invalid-runtime-${value:-empty}"
  printf '\nBACKUP_MAX_RUNTIME_SECONDS=%s\n' "$value" >> "$TEST_ROOT/backup.env"
  rejected
done
prepare missing-required-setting
echo 'PGHOST=' >> "$TEST_ROOT/backup.env"
rejected
prepare placeholder-required-setting
echo 'PGHOST=REPLACE_WITH_RDS_ENDPOINT' >> "$TEST_ROOT/backup.env"
rejected
prepare insecure-config
chmod 644 "$TEST_ROOT/backup.env"
rejected
prepare symlink-config
mv "$TEST_ROOT/backup.env" "$TEST_ROOT/config-target"
ln -s "$TEST_ROOT/config-target" "$TEST_ROOT/backup.env"
rejected
rm "$TEST_ROOT/backup.env"
for credential in pgpass rclone.conf; do
  prepare "insecure-$credential"
  chmod 644 "$TEST_ROOT/$credential"
  rejected
done
prepare insecure-state-directory
chmod 755 "$TEST_ROOT/state"
rejected
prepare different-lock-file
printf '\nBACKUP_LOCK_FILE=%s/another.lock\n' "$TEST_ROOT" >> "$TEST_ROOT/backup.env"
rejected
prepare wrong-postgres-major
export TEST_PG_MAJOR=17
rejected
for code in 0 1 75; do
  prepare "backup-exit-$code"
  export TEST_EXIT=$code
  execute
  [[ "$status" == "$code" && -e "$TEST_ROOT/called" ]]
  echo "PASS: $scenario"
done
for mode in timeout timeout-stubborn; do
  prepare "real-process-group-$mode"
  echo 'BACKUP_MAX_RUNTIME_SECONDS=1' >> "$TEST_ROOT/backup.env"
  export TEST_MODE=$mode
  execute
  expected=124
  if [[ "$mode" == timeout-stubborn ]]; then expected=137; fi
  [[ "$status" == "$expected" ]]
  grep -q 'stage=runtime-limit' "$TEST_ROOT/output"
  child_pid=$(cat "$TEST_ROOT/child.pid")
  # init이 종료된 자식을 회수할 짧은 여유만 둔다. 살아 있는 sleep을 남기지 않아야 한다.
  for _ in {1..50}; do
    if ! kill -0 "$child_pid" 2>/dev/null; then break; fi
    sleep 0.02
  done
  if kill -0 "$child_pid" 2>/dev/null; then exit 1; fi
  echo "PASS: $scenario"
done
