#!/usr/bin/env bash
set -euo pipefail
umask 077
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REAL_RCLONE=$(command -v rclone) || { echo '시험에 실제 rclone이 필요합니다.' >&2; exit 1; }
REAL_PATH=$PATH
export REAL_RCLONE
TEST_ROOT=$(mktemp -d)
trap 'rm -rf -- "$TEST_ROOT"' EXIT
trap 'printf "FAIL: line=%s fault=%s\n" "$LINENO" "${TEST_FAULT:-none}" >&2; cat "${case_dir:-/dev/null}/output" >&2' ERR
fixture="$TEST_ROOT/fixture"
mkdir -p "$fixture/source/bucket" "$fixture/target" "$fixture/history" "$fixture/work"
prefixes=(chalkak/posts/prod/original chalkak/posts/prod/thumbnail
  chalkak/signatures/prod/original chalkak/signatures/prod/thumbnail)
for prefix in "${prefixes[@]}"; do
  mkdir -p "$fixture/source/bucket/$prefix"
  extension=webp
  [[ "$prefix" != chalkak/signatures/* ]] || extension=png
  printf 'safe' > "$fixture/source/bucket/$prefix/a.$extension"
done
flexible_key='chalkak/posts/prod/original/2026/10/새 사진 #1.JPG'
mkdir -p "$fixture/source/bucket/${flexible_key%/*}"
printf 'flexible' > "$fixture/source/bucket/$flexible_key"
printf '[production]\ntype = alias\nremote = %s/source\n[backup]\ntype = alias\nremote = %s/target\n' \
  "$fixture" "$fixture" > "$fixture/rclone.conf"
cat > "$fixture/backup.env" <<EOF
RCLONE_CONFIG=$fixture/rclone.conf
IMAGE_BACKUP_SOURCE=production:bucket
IMAGE_BACKUP_DESTINATION=backup:bucket/chalkak/images
IMAGE_BACKUP_TEMP_ROOT=$fixture/work
EOF
bash "$SCRIPT_DIR/../image-backup.sh" "$fixture/backup.env" > "$fixture/backup.log" 2>&1 || {
  cat "$fixture/backup.log"; exit 1;
}
marker=$(find "$fixture/target" -name COMPLETE -print)
run_id=${marker%/*}; run_id=${run_id##*/}
cp -R "$fixture/target/bucket" "$fixture/history/bucket"
printf 'edit' > "$fixture/target/bucket/chalkak/images/files/chalkak/posts/prod/original/a.webp"
mkdir -p "$TEST_ROOT/bin"
cat > "$TEST_ROOT/bin/rclone" <<'WRAPPER'
#!/usr/bin/env bash
set -euo pipefail
case "$1" in copy|copyto|check) ;; *) echo '조회·복사·검사 외 명령 금지' >&2; exit 8 ;; esac
[[ "$2" == backup:* && "$3" == /* ]] || { echo '백업 읽기·로컬 복구만 허용' >&2; exit 8; }
case "${TEST_FAULT:-}:$1" in
  record:copyto|download:copy|verify:check) echo SECRET_SENTINEL >&2; exit 9 ;;
esac
arguments=()
historical=false
while [[ $# -gt 0 ]]; do
  if [[ "$1" == --s3-version-at ]]; then
    [[ "$2" == 2026-10-28T15:00:00Z ]] || exit 8
    historical=true
    shift 2
  else arguments+=("$1"); shift; fi
done
if [[ "$historical" == true ]]; then
  # S3 과거 시각 조회만 합성 저장소로 대체한다. 복사·비교는 실제 rclone이다.
  [[ "${arguments[1]}" == */files ]]
  arguments[1]=history:${arguments[1]#backup:}
  printf '%s\n' "${arguments[0]}" >> "$TEST_CASE/version-calls"
fi
"$REAL_RCLONE" "${arguments[@]}"
if [[ "${arguments[0]}" == copy && "${TEST_FAULT:-}" == corrupt ]]; then
  printf 'evil' > "${arguments[2]}/chalkak/posts/prod/original/a.webp"
fi
WRAPPER
chmod +x "$TEST_ROOT/bin/rclone"
export PATH="$TEST_ROOT/bin:$REAL_PATH" TEST_FAULT=''
key=chalkak/posts/prod/original/a.webp
prepare_case() {
  case_dir="$TEST_ROOT/$1"
  mkdir -p "$case_dir/work"
  cp -R "$fixture/target" "$case_dir/target"
  cp -R "$fixture/history" "$case_dir/history"
  printf '[backup]\ntype = alias\nremote = %s/target\n[history]\ntype = alias\nremote = %s/history\n' \
    "$case_dir" "$case_dir" > "$case_dir/rclone.conf"
  # 복구 환경에는 운영 S3 remote·DB 자격 증명이 필요 없다.
  cat > "$case_dir/restore.env" <<EOF
RCLONE_CONFIG=$case_dir/rclone.conf
IMAGE_BACKUP_DESTINATION=backup:bucket/chalkak/images
IMAGE_BACKUP_TEMP_ROOT=$case_dir/work
EOF
  printf '%s\n' "$key" > "$case_dir/keys.txt"
  record="$case_dir/target/bucket/chalkak/images/runs/$run_id"
  output="$case_dir/restored"
  export TEST_CASE="$case_dir" TEST_FAULT=''
}
execute_restore() {
  status=0
  bash "$SCRIPT_DIR/../image-restore.sh" "$case_dir/restore.env" "$run_id" "$case_dir/keys.txt" "$output" "$@" \
    > "$case_dir/output" 2>&1 || status=$?
  ! grep -q SECRET_SENTINEL "$case_dir/output"
  [[ -z "$(ls -A "$case_dir/work")" ]]
}
assert_failed() {
  [[ $status -ne 0 && ! -f "$output/RESTORED" ]]
  ! grep -q ' RESTORED ' "$case_dir/output"
  if [[ -n "${1:-}" ]]; then grep -q "FAILED stage=$1 " "$case_dir/output"; fi
}

prepare_case latest
execute_restore
[[ $status -eq 0 && $(cat "$output/$key") == edit ]]
[[ $(find "$output" -name '*.webp' | wc -l) -eq 1 ]]
grep -Fxq mode=current "$output/RESTORED"
echo 'PASS: 완료 회차 멤버십 확인 후 선택한 최신 파일만 원래 key로 복구'

prepare_case historical
execute_restore --version-at 2026-10-28T15:00:00Z
[[ $status -eq 0 && $(cat "$output/$key") == safe ]]
[[ $(cat "$case_dir/version-calls") == $'copy\ncheck' ]]
grep -Fxq mode=2026-10-28T15:00:00Z "$output/RESTORED"
echo 'PASS: 과거 조회 옵션이 이미지 복사·내용 비교에 동일 적용 (시점 조회는 합성)'

prepare_case all
cp "$record/verified.txt" "$case_dir/keys.txt"
execute_restore
[[ $status -eq 0 ]]
grep -Fxq files=5 "$output/RESTORED"
for variant in original thumbnail; do
  cmp "$fixture/source/bucket/chalkak/signatures/prod/$variant/a.png" "$output/chalkak/signatures/prod/$variant/a.png"
done
cmp "$fixture/source/bucket/$flexible_key" "$output/$flexible_key"
echo 'PASS: 게시글 WebP·사인 PNG·한글 공백 하위 경로의 혼합 백업→전체 복구'

prepare_case duplicate
printf '%s\n%s' "$key" "$key" > "$case_dir/keys.txt"
execute_restore
[[ $status -eq 0 ]]
grep -Fxq files=1 "$output/RESTORED"
echo 'PASS: 중복 key와 마지막 줄바꿈 없는 목록 처리'

for fault in record download verify corrupt; do
  prepare_case "$fault"
  export TEST_FAULT=$fault
  execute_restore
  expected=$fault
  [[ "$fault" != corrupt ]] || expected=verify
  assert_failed "$expected"
  echo "PASS: $fault 실패는 복구 성공으로 표시하지 않으며 비밀값·임시 파일 정리"
done

prepare_case missing-marker
rm "$record/COMPLETE"
execute_restore
assert_failed record
[[ ! -e "$output" ]]
echo 'PASS: 완료 표식 없는 회차 거부'

prepare_case mismatch
printf 'wrong-list\n' >> "$record/verified.txt"
execute_restore
assert_failed record
echo 'PASS: 대상 목록·검증 목록 불일치 거부'

for scenario in unknown traversal empty; do
  prepare_case "$scenario"
  case "$scenario" in
    unknown) printf 'chalkak/posts/prod/original/unknown.webp\n' > "$case_dir/keys.txt" ;;
    traversal) printf '../escape.webp\n' > "$case_dir/keys.txt" ;;
    empty) : > "$case_dir/keys.txt" ;;
  esac
  execute_restore
  assert_failed selection
  [[ ! -e "$output" ]]
  echo "PASS: $scenario 선택 목록 거부"
done

unsafe_index=0
for unsafe_key in 'chalkak/posts/prod/original/nested/../a.webp' 'chalkak/posts/prod/original/./a.webp' 'chalkak/posts/prod/original//a.webp' '/chalkak/posts/prod/original/a.webp' 'chalkak/posts/prod/original/a.webp/' $'chalkak/posts/prod/original/a\t.webp' 'chalkak/posts/prod/original/a\b.webp' 'other-team/original/a.webp' 'chalkak/posts/prod/original/．．/a.webp' 'chalkak/posts/prod/original/a␊b.webp'; do
  unsafe_index=$((unsafe_index + 1))
  prepare_case "unsafe-selection-$unsafe_index"
  printf '%s\n' "$unsafe_key" > "$case_dir/keys.txt"
  # 기록에 있어도 위험한 경로·대상 밖 prefix는 거부한다.
  printf '%s\n' "$unsafe_key" >> "$record/inventory.txt"
  cp "$record/inventory.txt" "$record/verified.txt"
  printf 'format=chalkak-images-v1\nrun=%s\nfiles=6\n' "$run_id" > "$record/COMPLETE"
  execute_restore
  assert_failed selection
  [[ ! -e "$output" ]]
  echo 'PASS: 기록 속 위험 경로·대상 밖 prefix를 복구 전에 거부'
done

prepare_case missing-file
rm "$case_dir/target/bucket/chalkak/images/files/$key"
execute_restore
assert_failed
echo 'PASS: 목록에만 있고 실제로 없는 파일은 실패'

prepare_case existing
mkdir "$output"
printf 'keep' > "$output/keep"
execute_restore
assert_failed
[[ $(cat "$output/keep") == keep ]]
echo 'PASS: 기존 복구 폴더 덮어쓰기 거부'

prepare_case symbolic
mkdir "$case_dir/existing"
ln -s "$case_dir/existing" "$output"
execute_restore
assert_failed
[[ -L "$output" && -z "$(ls -A "$case_dir/existing")" ]]
echo 'PASS: 심볼릭 링크 대상 거부'

prepare_case remote
output=production:bucket
execute_restore
assert_failed
echo 'PASS: 운영 S3 등 원격 경로를 복구 대상으로 지정하면 거부'

prepare_case invalid-time
execute_restore --version-at yesterday
assert_failed
echo 'PASS: 시간대 없는 모호한 과거 시각 거부'

echo '총 28개 시나리오 통과 (실제 rclone 로컬 복구·비교; S3 버전 선택은 합성 경로, 실제 AWS 검증은 #844)'
