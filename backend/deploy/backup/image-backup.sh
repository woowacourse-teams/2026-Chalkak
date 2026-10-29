#!/usr/bin/env bash
set -euo pipefail
set +x
umask 077
export LC_ALL=C

if [[ $# -ne 1 || ! -r "$1" ]]; then
  echo '사용법: bash image-backup.sh /보호된/backup.env' >&2
  exit 1
fi
set -a
source "$1"
set +a
stage=configuration
work_dir=''
run_id=unassigned
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
for key in RCLONE_CONFIG IMAGE_BACKUP_SOURCE IMAGE_BACKUP_DESTINATION; do
  [[ -n "${!key:-}" ]] || { printf '필수 설정 누락: %s\n' "$key" >&2; exit 1; }
done
[[ -r "$RCLONE_CONFIG" ]] || exit 1
[[ "$IMAGE_BACKUP_SOURCE" =~ ^[[:alnum:]_-]+:[[:alnum:]._-]+$ ]] || exit 1
[[ "$IMAGE_BACKUP_DESTINATION" =~ ^[[:alnum:]_-]+:[[:alnum:]._-]+/[[:alnum:]_/-]+/images$ ]] || exit 1
[[ "$IMAGE_BACKUP_DESTINATION" != "$IMAGE_BACKUP_SOURCE/"* ]] || exit 1
[[ "/${IMAGE_BACKUP_DESTINATION#*:}/" != *'/../'* && "/${IMAGE_BACKUP_DESTINATION#*:}/" != *'/./'* ]] || exit 1
for tool in rclone sort cmp mktemp; do
  command -v "$tool" >/dev/null || { printf '필수 도구 없음: %s\n' "$tool" >&2; exit 1; }
done
work_dir=$(mktemp -d "${IMAGE_BACKUP_TEMP_ROOT:-/tmp}/chalkak-images-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXXXX")
run_id=${work_dir##*/}
files="$IMAGE_BACKUP_DESTINATION/files"
record="$IMAGE_BACKUP_DESTINATION/runs/$run_id"
prefixes=(chalkak/posts/prod/original chalkak/posts/prod/thumbnail
  chalkak/signatures/prod/original chalkak/signatures/prod/thumbnail)
rclone_options=(--config "$RCLONE_CONFIG" --ask-password=false --retries 3 --low-level-retries 3
  --contimeout 10s --timeout 60s --stats 0 --log-level ERROR
  --transfers 1 --checkers 1 --buffer-size 8M --s3-upload-concurrency 1 --s3-no-check-bucket)
printf '%s STARTED run=%s\n' "$(date -u +%FT%TZ)" "$run_id"
touch "$work_dir/inventory.txt" "$work_dir/verified.txt"

# 버킷 루트나 다른 팀 경로 대신 허용한 네 경로만 조회한다.
stage=inventory
for index in "${!prefixes[@]}"; do
  prefix=${prefixes[$index]}
  rclone lsf "$IMAGE_BACKUP_SOURCE/$prefix" "${rclone_options[@]}" --recursive --files-only --format p \
    > "$work_dir/$index.list" 2> "$work_dir/diagnostic"
  while IFS= read -r path; do
    # 서비스가 만드는 단일 파일 이름만 허용해 줄 단위 목록의 경로 해석을 모호하게 하지 않는다.
    [[ "$path" =~ ^[[:alnum:]_-]+\.webp$ ]] || exit 1
    printf '%s/%s\n' "$prefix" "$path" >> "$work_dir/inventory.txt"
  done < "$work_dir/$index.list"
  sort -u "$work_dir/$index.list" -o "$work_dir/$index.list"
done
sort -u "$work_dir/inventory.txt" -o "$work_dir/inventory.txt"
stage=inventory-upload
rclone copyto "$work_dir/inventory.txt" "$record/inventory.txt" "${rclone_options[@]}" --immutable \
  > /dev/null 2> "$work_dir/diagnostic"

for index in "${!prefixes[@]}"; do
  [[ -s "$work_dir/$index.list" ]] || continue
  prefix=${prefixes[$index]}
  source_path="$IMAGE_BACKUP_SOURCE/$prefix"
  target_path="$files/$prefix"
  stage=copy
  rclone copy "$source_path" "$target_path" "${rclone_options[@]}" --checksum --no-traverse \
    --files-from-raw "$work_dir/$index.list" > /dev/null 2> "$work_dir/diagnostic"

  stage=verify
  check_options=(--download --one-way --files-from-raw "$work_dir/$index.list"
    --match "$work_dir/$index.match" --differ "$work_dir/$index.differ"
    --missing-on-dst "$work_dir/$index.missing" --error "$work_dir/$index.error")
  if ! rclone check "$source_path" "$target_path" "${rclone_options[@]}" "${check_options[@]}" \
    > /dev/null 2> "$work_dir/diagnostic"; then
    # 공통 해시가 없는 같은 크기 변경을 검출하면 다른 파일만 다시 전송한다.
    [[ -s "$work_dir/$index.differ" && ! -s "$work_dir/$index.missing" && ! -s "$work_dir/$index.error" ]] || exit 1
    stage=repair
    rclone copy "$source_path" "$target_path" "${rclone_options[@]}" --ignore-times --no-traverse \
      --files-from-raw "$work_dir/$index.differ" > /dev/null 2> "$work_dir/diagnostic"
    stage=verify
    rclone check "$source_path" "$target_path" "${rclone_options[@]}" "${check_options[@]}" \
      > /dev/null 2> "$work_dir/diagnostic"
  fi
  # 조회 후 사라진 파일이 검사에서 조용히 빠져도 완료로 처리하지 않는다.
  sort -u "$work_dir/$index.match" -o "$work_dir/$index.match"
  cmp -s "$work_dir/$index.list" "$work_dir/$index.match"
  while IFS= read -r path; do
    printf '%s/%s\n' "$prefix" "$path" >> "$work_dir/verified.txt"
  done < "$work_dir/$index.match"
done
sort -u "$work_dir/verified.txt" -o "$work_dir/verified.txt"
cmp -s "$work_dir/inventory.txt" "$work_dir/verified.txt"

stage=record
rclone copyto "$work_dir/verified.txt" "$record/verified.txt" "${rclone_options[@]}" --immutable \
  > /dev/null 2> "$work_dir/diagnostic"
rclone check "$work_dir" "$record" "${rclone_options[@]}" --download --one-way \
  --include /inventory.txt --include /verified.txt > /dev/null 2> "$work_dir/diagnostic"
count=$(wc -l < "$work_dir/verified.txt" | tr -d '[:space:]')
stage=completion
printf 'format=chalkak-images-v1\nrun=%s\nverified_at=%s\nfiles=%s\n' \
  "$run_id" "$(date -u +%FT%TZ)" "$count" > "$work_dir/COMPLETE"
rclone copyto "$work_dir/COMPLETE" "$record/COMPLETE" "${rclone_options[@]}" --immutable \
  > /dev/null 2> "$work_dir/diagnostic"
rclone cat "$record/COMPLETE" "${rclone_options[@]}" 2> "$work_dir/diagnostic" | cmp -s - "$work_dir/COMPLETE"
printf '%s COMPLETE run=%s files=%s\n' "$(date -u +%FT%TZ)" "$run_id" "$count"
