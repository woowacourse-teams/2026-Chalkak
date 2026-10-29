#!/usr/bin/env bash
set -euo pipefail
umask 077
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
BACKUP_SCRIPT="$SCRIPT_DIR/../image-backup.sh"
REAL_RCLONE=$(command -v rclone) || { echo '시험에 실제 rclone이 필요합니다.' >&2; exit 1; }
export REAL_RCLONE
TEST_ROOT=$(mktemp -d)
trap 'rm -rf -- "$TEST_ROOT"' EXIT
trap 'printf "FAIL: line=%s fault=%s\n" "$LINENO" "$TEST_FAULT" >&2; cat "$case_dir/output" >&2' ERR
mkdir -p "$TEST_ROOT/bin"
cat > "$TEST_ROOT/bin/rclone" <<'WRAPPER'
#!/usr/bin/env bash
set -euo pipefail
case "$1" in lsf|copy|copyto|check|cat) ;; *) echo '허용하지 않은 명령' >&2; exit 8 ;; esac
if [[ "$1" == copy || "$1" == copyto ]]; then
  [[ "$3" == backup:* ]] || { echo '원본 쓰기 금지' >&2; exit 8; }
fi
case "${TEST_FAULT:-}:$1" in
  inventory:lsf|copy:copy|inventory-upload:copyto)
    echo SECRET_SENTINEL >&2; exit 9 ;;
  partial:copy)
    [[ "$3" != */posts/prod/thumbnail ]] || { echo SECRET_SENTINEL >&2; exit 9; } ;;
  record:copyto)
    [[ "$3" != */verified.txt ]] || { echo SECRET_SENTINEL >&2; exit 9; } ;;
  completion:copyto)
    [[ "$3" != */COMPLETE ]] || { echo SECRET_SENTINEL >&2; exit 9; } ;;
  marker:cat) echo wrong-marker; exit ;;
esac
if [[ "$1" == copy && "${TEST_FAULT:-}" == late ]]; then
  printf 'late-file' > "$TEST_SOURCE/bucket/chalkak/posts/prod/original/late.webp"
fi
# 공통 해시가 없어서 첫 복사가 같은 크기 변경을 건너뛴 상황을 주입한다.
# 이후 내용 비교와 다른 파일 재전송은 실제 rclone으로 수행한다.
if [[ "$1" == copy && "${TEST_FAULT:-}" == nohash && " $* " == *' --checksum '* ]]; then exit; fi
if [[ "$1" == check && "$2" == production:* ]]; then
  target="$TEST_TARGET/${3#backup:}"
  case "${TEST_FAULT:-}" in
    corrupt) printf 'evil' > "$target/a.webp" ;;
    vanished) rm -f -- "$TEST_SOURCE/bucket/chalkak/posts/prod/original/a.webp" ;;
    verify) echo SECRET_SENTINEL >&2; exit 9 ;;
  esac
fi
exec "$REAL_RCLONE" "$@"
WRAPPER
chmod +x "$TEST_ROOT/bin/rclone"
export PATH="$TEST_ROOT/bin:$PATH" TEST_FAULT=''
prefixes=(chalkak/posts/prod/original chalkak/posts/prod/thumbnail
  chalkak/signatures/prod/original chalkak/signatures/prod/thumbnail)
prepare_case() {
  case_dir="$TEST_ROOT/$1"
  mkdir -p "$case_dir/source/bucket" "$case_dir/target" "$case_dir/work"
  for prefix in "${prefixes[@]}"; do
    mkdir -p "$case_dir/source/bucket/$prefix"
    printf 'safe' > "$case_dir/source/bucket/$prefix/a.webp"
  done
  for prefix in chalkak/posts/dev/original chalkak/staging/prod other-team/posts/prod/original; do
    mkdir -p "$case_dir/source/bucket/$prefix"
    printf 'excluded' > "$case_dir/source/bucket/$prefix/ignored.webp"
  done
  printf '[production]\ntype = alias\nremote = %s/source\n[backup]\ntype = alias\nremote = %s/target\n' \
    "$case_dir" "$case_dir" > "$case_dir/rclone.conf"
  cat > "$case_dir/backup.env" <<EOF
RCLONE_CONFIG=$case_dir/rclone.conf
IMAGE_BACKUP_SOURCE=production:bucket
IMAGE_BACKUP_DESTINATION=backup:bucket/chalkak/images
IMAGE_BACKUP_TEMP_ROOT=$case_dir/work
EOF
  chmod 600 "$case_dir/backup.env" "$case_dir/rclone.conf"
  export TEST_SOURCE="$case_dir/source" TEST_TARGET="$case_dir/target" TEST_FAULT=''
  files="$case_dir/target/bucket/chalkak/images/files"
  runs="$case_dir/target/bucket/chalkak/images/runs"
}
execute_backup() {
  status=0
  bash "$BACKUP_SCRIPT" "$case_dir/backup.env" > "$case_dir/output" 2>&1 || status=$?
  ! grep -q SECRET_SENTINEL "$case_dir/output"
  [[ -z "$(ls -A "$case_dir/work")" ]]
}
assert_failed() {
  [[ $status -ne 0 ]]
  grep -q "FAILED stage=$1 " "$case_dir/output"
  ! grep -q ' COMPLETE ' "$case_dir/output"
  [[ ! -d "$runs" || -z "$(find "$runs" -name COMPLETE -print)" ]]
}

prepare_case initial
execute_backup
[[ $status -eq 0 ]]
grep -q ' COMPLETE .* files=4$' "$case_dir/output"
for prefix in "${prefixes[@]}"; do cmp "$case_dir/source/bucket/$prefix/a.webp" "$files/$prefix/a.webp"; done
[[ $(find "$files" -type f | wc -l) -eq 4 ]]
record=$(find "$runs" -name COMPLETE -print); record=${record%/*}
cmp "$record/inventory.txt" "$record/verified.txt"
[[ $(wc -l < "$record/inventory.txt") -eq 4 ]]
echo 'PASS: 네 운영 경로만 실제 복사·내용 검증·대상 목록·완료 기록'

before=$(stat -c '%Y' "$files/chalkak/posts/prod/original/a.webp" 2>/dev/null) || before=$(stat -f '%m' "$files/chalkak/posts/prod/original/a.webp")
execute_backup
[[ $status -eq 0 && $(find "$runs" -name COMPLETE | wc -l) -eq 2 ]]
after=$(stat -c '%Y' "$files/chalkak/posts/prod/original/a.webp" 2>/dev/null) || after=$(stat -f '%m' "$files/chalkak/posts/prod/original/a.webp")
[[ "$before" == "$after" ]]
echo 'PASS: 변경 없는 파일 유지와 실행별 고유 기록'

printf 'new-photo' > "$case_dir/source/bucket/chalkak/posts/prod/original/new.webp"
printf 'edit' > "$case_dir/source/bucket/chalkak/posts/prod/original/a.webp"
execute_backup
[[ $status -eq 0 ]]
cmp "$case_dir/source/bucket/chalkak/posts/prod/original/a.webp" "$files/chalkak/posts/prod/original/a.webp"
cmp "$case_dir/source/bucket/chalkak/posts/prod/original/new.webp" "$files/chalkak/posts/prod/original/new.webp"
echo 'PASS: 새 파일과 같은 크기의 내용 변경 전송'

rm "$case_dir/source/bucket/chalkak/posts/prod/original/new.webp"
execute_backup
[[ $status -eq 0 && -f "$files/chalkak/posts/prod/original/new.webp" ]]
echo 'PASS: 원본 삭제가 백업 삭제로 전파되지 않음'

prepare_case nohash
execute_backup
[[ $status -eq 0 ]]
printf 'edit' > "$case_dir/source/bucket/chalkak/posts/prod/original/a.webp"
export TEST_FAULT=nohash
execute_backup
[[ $status -eq 0 && $(cat "$files/chalkak/posts/prod/original/a.webp") == edit ]]
echo 'PASS: 해시 없는 같은 크기 변경 주입 → 실제 내용 비교·선택 재전송'

prepare_case late
export TEST_FAULT=late
execute_backup
[[ $status -eq 0 && ! -f "$files/chalkak/posts/prod/original/late.webp" ]]
export TEST_FAULT=''
execute_backup
[[ $status -eq 0 && -f "$files/chalkak/posts/prod/original/late.webp" ]]
echo 'PASS: 목록 작성 후 생성된 파일은 다음 회차에서 백업'

for fault in inventory copy partial corrupt verify vanished inventory-upload record completion; do
  prepare_case "$fault"
  export TEST_FAULT=$fault
  execute_backup
  expected=$fault
  case "$fault" in corrupt|vanished) expected=verify ;; partial) expected=copy ;; esac
  assert_failed "$expected"
  if [[ "$fault" == partial ]]; then [[ -f "$files/chalkak/posts/prod/original/a.webp" ]]; fi
  echo "PASS: $fault 실패 미완료·비밀값 비노출·임시 파일 정리"
done

prepare_case retry
export TEST_FAULT=copy
execute_backup
assert_failed copy
export TEST_FAULT=''
execute_backup
[[ $status -eq 0 && $(find "$runs" -name COMPLETE | wc -l) -eq 1 ]]
echo 'PASS: 실패 후 재실행으로 검증된 회차 생성'

prepare_case invalid
printf '\nunset IMAGE_BACKUP_SOURCE\n' >> "$case_dir/backup.env"
execute_backup
assert_failed configuration
echo 'PASS: 필수 이미지 설정 누락 거부, DB 설정은 요구하지 않음'

prepare_case unsafe
printf '\nIMAGE_BACKUP_DESTINATION=production:bucket/chalkak/images\n' >> "$case_dir/backup.env"
execute_backup
assert_failed configuration
echo 'PASS: 같은 원본 remote·버킷을 대상으로 지정하면 거부'

prepare_case unsupported
printf 'unsupported' > "$case_dir/source/bucket/chalkak/posts/prod/original/unexpected name.webp"
execute_backup
assert_failed inventory
echo 'PASS: 현재 서비스 파일 이름 규칙 밖의 파일은 조용히 누락하지 않고 거부'

prepare_case marker
export TEST_FAULT=marker
execute_backup
[[ $status -ne 0 ]]
grep -q 'FAILED stage=completion ' "$case_dir/output"
! grep -q ' COMPLETE ' "$case_dir/output"
echo 'PASS: 완료 표식 재조회 불일치 실패, 이미 전송된 표식은 자동 삭제하지 않음'

echo '총 20개 시나리오 통과 (실제 rclone 로컬 저장소, 실패·해시 부재 상황 주입; AWS 버저닝은 별도 검증)'
