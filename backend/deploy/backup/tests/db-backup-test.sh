#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
BACKUP_SCRIPT="$SCRIPT_DIR/../db-backup.sh"
REAL_RCLONE=$(command -v rclone) || { echo '테스트에 실제 rclone이 필요합니다.' >&2; exit 1; }
TEST_ROOT=$(mktemp -d)
trap 'rm -rf -- "$TEST_ROOT"' EXIT
mkdir -p "$TEST_ROOT/bin"
REAL_TIMEOUT=$(command -v timeout) || { echo '테스트에 GNU timeout이 필요합니다.' >&2; exit 1; }
export REAL_RCLONE REAL_TIMEOUT

cat > "$TEST_ROOT/bin/pg_dump" <<'MOCK'
#!/usr/bin/env bash
if [[ "$1" == --version ]]; then
  echo "pg_dump (PostgreSQL) ${TEST_PG_VERSION:-18.4}"
  exit
fi
[[ "$PGSSLMODE" == verify-full && "$PGOPTIONS" == *read_only=on* ]] || exit 8
if [[ "${TEST_FAULT:-}" == timeout || "${TEST_FAULT:-}" == timeout-kill ]]; then
  echo "$$" > "$TEST_REMOTE_ROOT/dump.pid"
  [[ "$TEST_FAULT" != timeout-kill ]] || trap '' TERM
  while :; do :; done
fi
if [[ "${TEST_FAULT:-}" == dump ]]; then echo 'SECRET_SENTINEL' >&2; exit 9; fi
for argument in "$@"; do
  if [[ "$argument" == --file=* ]]; then
    [[ "${TEST_FAULT:-}" == empty ]] && touch "${argument#--file=}" && exit
    printf 'synthetic-dump\n' > "${argument#--file=}"
  fi
done
MOCK
cat > "$TEST_ROOT/bin/pg_restore" <<'MOCK'
#!/usr/bin/env bash
if [[ "$1" == --version ]]; then echo 'pg_restore (PostgreSQL) 18.4'; exit; fi
[[ "${TEST_FAULT:-}" != archive ]] || { echo SECRET_SENTINEL >&2; exit 9; }
MOCK
cat > "$TEST_ROOT/bin/rclone" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
case "$1" in copyto|check|cat) ;; *) echo '예상하지 않은 변경 명령' >&2; exit 8 ;; esac
if [[ "$1" == copyto ]]; then
  case "${TEST_FAULT:-}:${2##*/}" in
    upload:database.dump|checksum:database.dump.sha256|completion:COMPLETE)
      echo SECRET_SENTINEL >&2; exit 9 ;;
  esac
fi
if [[ "$1" == check && "${TEST_FAULT:-}" == corrupt ]]; then
  printf 'corruption\n' >> "$TEST_REMOTE_ROOT/${3#backup:}/database.dump"
fi
if [[ "$1" == cat && "${TEST_FAULT:-}" == marker ]]; then echo wrong-marker; exit; fi
exec "$REAL_RCLONE" "$@"
MOCK
# 실제 timeout을 사용하되 시험에서는 1초 제한으로 바꿔 기다리는 시간을 줄인다.
cat > "$TEST_ROOT/bin/timeout" <<'WRAPPER'
#!/usr/bin/env bash
[[ "$1" == --kill-after=10s && "$2" == 5m && "$3" == pg_dump ]] || exit 8
shift 2
exec "$REAL_TIMEOUT" --kill-after=1s 1s "$@"
WRAPPER
chmod +x "$TEST_ROOT/bin/timeout"
chmod +x "$TEST_ROOT/bin/pg_dump" "$TEST_ROOT/bin/pg_restore" "$TEST_ROOT/bin/rclone"
export PATH="$TEST_ROOT/bin:$PATH"
export TEST_FAULT='' TEST_PG_VERSION=18.4
unset PGPASSWORD
trap 'printf "FAIL: line=%s scenario=%s status=%s\n" "$LINENO" "${TEST_FAULT:-none}" "${status:-unset}" >&2; cat "${case_dir:-/dev/null}/output" >&2' ERR

prepare_case() {
  case_dir="$TEST_ROOT/$1"
  mkdir -p "$case_dir/remote" "$case_dir/work"
  touch "$case_dir/ca.pem"
  printf 'unused:5432:fixture:fixture:SECRET_SENTINEL\n' > "$case_dir/pgpass"
  printf '[backup]\ntype = alias\nremote = %s/remote\n' "$case_dir" > "$case_dir/rclone.conf"
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
EOF
  chmod 600 "$case_dir/pgpass" "$case_dir/rclone.conf" "$case_dir/backup.env"
  export TEST_REMOTE_ROOT="$case_dir/remote"
}

execute_backup() {
  status=0
  bash "$BACKUP_SCRIPT" "$case_dir/backup.env" > "$case_dir/output" 2>&1 || status=$?
  if grep -q SECRET_SENTINEL "$case_dir/output"; then echo 'FAIL: 비밀값 노출'; exit 1; fi
  if [[ -n "$(ls -A "$case_dir/work")" ]]; then echo 'FAIL: 임시 파일 잔존'; exit 1; fi
}

assert_failed() {
  local expected_stage=$1
  [[ $status -ne 0 ]] || exit 1
  grep -q "FAILED stage=$expected_stage " "$case_dir/output" || exit 1
  if grep -q ' COMPLETE ' "$case_dir/output"; then exit 1; fi
  [[ -z "$(find "$case_dir/remote" -name COMPLETE -print)" ]] || exit 1
}

prepare_case success
execute_backup
[[ $status -eq 0 ]] || exit 1
grep -q ' COMPLETE ' "$case_dir/output" || exit 1
first_dump=$(find "$case_dir/remote" -name database.dump -print)
[[ -n "$first_dump" ]] || exit 1
(cd "${first_dump%/*}" && sha256sum --check database.dump.sha256 >/dev/null)
file_mode=$(stat -c '%a' "$first_dump" 2>/dev/null) || file_mode=$(stat -f '%Lp' "$first_dump")
[[ "$file_mode" == 600 ]] || exit 1
echo 'PASS: 실제 rclone 복사·다운로드 검증과 완료 기록'

execute_backup
[[ $status -eq 0 && $(find "$case_dir/remote" -name COMPLETE | wc -l) -eq 2 ]] || exit 1
[[ -f "$first_dump" ]] || exit 1
echo 'PASS: 재실행이 새 회차를 만들고 기존 보관본 유지'

for fault in dump empty archive upload checksum corrupt completion; do
  prepare_case "$fault"
  export TEST_FAULT=$fault
  execute_backup
  expected_stage=$fault
  case "$fault" in empty) expected_stage=dump ;; archive) expected_stage=archive-list ;;
    upload) expected_stage=upload-dump ;; checksum) expected_stage=upload-checksum ;;
    corrupt) expected_stage=verify ;; esac
  assert_failed "$expected_stage"
  echo "PASS: $fault 실패 시 미완료 처리·비밀값 비노출·임시 파일 정리"
done

for fault in timeout timeout-kill; do
  prepare_case "$fault"
  export TEST_FAULT=$fault
  execute_backup
  assert_failed dump
  if [[ "$fault" == timeout ]]; then [[ $status -eq 124 ]] || exit 1
  else [[ $status -eq 137 ]] || exit 1; fi
  dump_pid=$(cat "$case_dir/remote/dump.pid")
  if kill -0 "$dump_pid" 2>/dev/null; then echo 'FAIL: 덤프 프로세스 잔존'; exit 1; fi
  echo "PASS: $fault 종료·업로드 차단·완료 기록 없음·임시 파일 정리"
done

prepare_case missing
export TEST_FAULT=''
printf '\nunset PGHOST\n' >> "$case_dir/backup.env"
execute_backup
assert_failed configuration
echo 'PASS: 필수 설정 누락 시 전송 전 실패'

prepare_case version
export TEST_PG_VERSION=17.9
execute_backup
assert_failed configuration
export TEST_PG_VERSION=18.4
echo 'PASS: PostgreSQL 주 버전 불일치 거부'

prepare_case password
export PGPASSWORD=SECRET_SENTINEL
execute_backup
assert_failed configuration
unset PGPASSWORD
echo 'PASS: 환경변수 비밀번호 거부'

prepare_case marker
export TEST_FAULT=marker
execute_backup
[[ $status -ne 0 ]] || exit 1
grep -q 'FAILED stage=completion ' "$case_dir/output" || exit 1
if grep -q ' COMPLETE ' "$case_dir/output"; then exit 1; fi
echo 'PASS: 완료 표식 재조회 불일치 실패 (이미 전송된 표식은 자동 삭제하지 않음)'

prepare_case retry
export TEST_FAULT=upload
execute_backup
assert_failed upload-dump
export TEST_FAULT=''
execute_backup
[[ $status -eq 0 && $(find "$case_dir/remote" -name COMPLETE | wc -l) -eq 1 ]] || exit 1
echo 'PASS: 실패 후 재실행으로 정상 보관본 생성'

echo '총 16개 시나리오 통과 (DB 명령은 합성 도구, 전송·내용 비교는 실제 rclone)'
