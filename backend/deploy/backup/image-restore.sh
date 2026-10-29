#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077
export LC_ALL=C

if [[ ( $# -ne 4 && $# -ne 6 ) || ! -r "$1" || ! -r "$3" ]]; then
  echo '사용법: bash image-restore.sh /보호된/restore.env 회차ID key목록 /새로운/로컬폴더 [--version-at YYYY-MM-DDTHH:MM:SSZ]' >&2
  exit 1
fi
run_id=$2
selection=$3
output=$4
version_at=''
[[ "$run_id" =~ ^chalkak-images-[0-9]{8}T[0-9]{6}Z-[[:alnum:]]{8}$ ]] || exit 1
[[ "$output" == /* && ! -e "$output" && ! -L "$output" ]] || {
  echo '아직 존재하지 않는 절대 경로의 로컬 폴더를 지정하세요.' >&2; exit 1;
}
if [[ $# -eq 6 ]]; then
  [[ "$5" == --version-at && "$6" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] || exit 1
  version_at=$6
fi
set -a
source "$1"
set +a
stage=configuration
work_dir=''
started=$(date +%s)
finish() {
  local result=$?
  trap - EXIT
  if [[ $result -ne 0 ]]; then
    printf '%s FAILED stage=%s run=%s\n' "$(date -u +%FT%TZ)" "$stage" "$run_id" >&2
  fi
  [[ -z "$work_dir" ]] || rm -rf -- "$work_dir"
  exit "$result"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
[[ -n "${RCLONE_CONFIG:-}" && -r "$RCLONE_CONFIG" && -n "${IMAGE_BACKUP_DESTINATION:-}" ]] || exit 1
[[ "$IMAGE_BACKUP_DESTINATION" =~ ^[[:alnum:]_-]+:[[:alnum:]._-]+/[[:alnum:]_/-]+/images$ ]] || exit 1
[[ "/${IMAGE_BACKUP_DESTINATION#*:}/" != *'/../'* && "/${IMAGE_BACKUP_DESTINATION#*:}/" != *'/./'* ]] || exit 1
for tool in rclone jq sort cmp grep mktemp; do
  command -v "$tool" >/dev/null || { printf '필수 도구 없음: %s\n' "$tool" >&2; exit 1; }
done
work_dir=$(mktemp -d "${IMAGE_BACKUP_TEMP_ROOT:-/tmp}/chalkak-image-restore-XXXXXXXX")
record="$IMAGE_BACKUP_DESTINATION/runs/$run_id"
files="$IMAGE_BACKUP_DESTINATION/files"
rclone_options=(--config "$RCLONE_CONFIG" --ask-password=false --retries 3 --low-level-retries 3
  --contimeout 10s --timeout 60s --stats 0 --log-level ERROR
  --transfers 1 --checkers 1 --buffer-size 8M --s3-upload-concurrency 1 --s3-no-check-bucket)
printf '%s STARTED run=%s mode=%s\n' "$(date -u +%FT%TZ)" "$run_id" "${version_at:-current}"

stage=record
for file in COMPLETE inventory.txt verified.txt; do
  rclone copyto "$record/$file" "$work_dir/$file" "${rclone_options[@]}" --immutable \
    > /dev/null 2> "$work_dir/diagnostic"
done
grep -Fxq 'format=chalkak-images-v1' "$work_dir/COMPLETE"
grep -Fxq "run=$run_id" "$work_dir/COMPLETE"
cmp -s "$work_dir/inventory.txt" "$work_dir/verified.txt"
count=$(wc -l < "$work_dir/verified.txt" | tr -d '[:space:]')
grep -Fxq "files=$count" "$work_dir/COMPLETE"

stage=selection
[[ -s "$selection" ]] || exit 1
jq -Rr '
  if (test("^chalkak/(posts|signatures)/prod/(original|thumbnail)/.+$") | not) or
    test("[\u0000-\u001f\u007f\u2400-\u241f\u2421\uff0f\u201b\\\\]") or
    (split("/") | any(. == "" or . == "." or . == ".." or . == "．" or . == "．．"))
  then error("unsafe key") else . end
' "$selection" > "$work_dir/validated.txt" 2> "$work_dir/diagnostic"
while IFS= read -r key; do
  grep -Fxq "$key" "$work_dir/verified.txt" || exit 1
  printf '%s\n' "$key"
done < "$work_dir/validated.txt" > "$work_dir/selection.txt"
sort -u "$work_dir/selection.txt" -o "$work_dir/selection.txt"
[[ -s "$work_dir/selection.txt" ]] || exit 1

# 회차 기록은 멤버십 확인용이다. 과거 내용은 명시한 백업 S3 시각에서 선택한다.
read_options=("${rclone_options[@]}")
[[ -z "$version_at" ]] || read_options+=(--s3-version-at "$version_at")
stage=download
mkdir -m 700 -- "$output"
rclone copy "$files" "$output" "${read_options[@]}" --immutable \
  --files-from-raw "$work_dir/selection.txt" --no-traverse > /dev/null 2> "$work_dir/diagnostic"
stage=verify
rclone check "$files" "$output" "${read_options[@]}" --download --one-way \
  --files-from-raw "$work_dir/selection.txt" --match "$work_dir/match.txt" \
  > /dev/null 2> "$work_dir/diagnostic"
sort -u "$work_dir/match.txt" -o "$work_dir/match.txt"
cmp -s "$work_dir/selection.txt" "$work_dir/match.txt"
count=$(wc -l < "$work_dir/match.txt" | tr -d '[:space:]')
printf 'run=%s\nmode=%s\nfiles=%s\nverified_at=%s\n' "$run_id" "${version_at:-current}" \
  "$count" "$(date -u +%FT%TZ)" > "$output/RESTORED"
printf '%s RESTORED run=%s files=%s total_seconds=%s mode=%s\n' \
  "$(date -u +%FT%TZ)" "$run_id" "$count" "$(($(date +%s) - started))" "${version_at:-current}"
