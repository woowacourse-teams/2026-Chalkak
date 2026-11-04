#!/usr/bin/env bash
# PR에 붙일 화면 캡처를 캡처 전용 브랜치(client/pr-assets)의 <이슈번호>/ 폴더에 올리고,
# PR 본문에 그대로 붙일 마크다운을 출력한다.
#
# 왜 브랜치에 올리나
#   gh CLI로는 PR 본문에 이미지를 첨부할 수 없다. 코드 없는 빈 브랜치에 올리면
#   CI·브랜치 보호 규칙에 영향이 없고, 레포 안에 근거가 남는다.
#   레포가 공개라 테스트 계정 화면만 올린다(실제 사용자 사진·이메일이 보이면 올리지 않는다).
#
# 하는 일
#   지금 작업 폴더와 브랜치는 건드리지 않는다. 임시 index로 커밋을 만들어 원격 브랜치에 바로 push한다.
#   같은 이름의 파일이 이미 있으면 덮어쓴다.
#
# 사용법
#   upload-media.sh [--create-branch] [--dry-run] <이슈번호> <파일>...
#     --create-branch  원격에 client/pr-assets가 없을 때 새로 만든다. 처음 한 번만, 사람에게 물은 뒤 쓴다.
#     --dry-run        커밋만 만들고 push하지 않는다. 출력되는 주소는 아직 열리지 않는다.
#
# 출력 (표준 출력)
#   파일마다 한 줄. 이미지(png·jpg·gif)는 ![이름](주소), 영상(mp4·mov)은 [이름](주소) 링크.
#   주소는 커밋 SHA 기준이라 브랜치가 나중에 바뀌어도 깨지지 않는다.
#
# 예시
#   upload-media.sh 123 compare-1.png after-2.gif repro.mp4
set -euo pipefail

BRANCH="client/pr-assets"
REMOTE="${MEDIA_REMOTE:-origin}"   # 시험할 때만 다른 원격으로 바꾼다
MAX_BYTES=$((10 * 1024 * 1024))   # GitHub가 PR 본문에서 무리 없이 보여 주는 크기

create_branch=false
dry_run=false
while [[ $# -gt 0 && "$1" == --* ]]; do
  case "$1" in
    --create-branch) create_branch=true ;;
    --dry-run) dry_run=true ;;
    -h|--help) sed -n '2,25p' "$0"; exit 0 ;;
    *) echo "모르는 옵션: $1" >&2; exit 2 ;;
  esac
  shift
done

if [[ $# -lt 2 ]]; then
  sed -n '2,25p' "$0" >&2
  exit 2
fi
number="$1"; shift
[[ "$number" =~ ^[0-9]+$ ]] || { echo "이슈 번호는 숫자여야 해요: $number" >&2; exit 2; }

for file in "$@"; do
  [[ -f "$file" ]] || { echo "파일이 없어요: $file" >&2; exit 2; }
  size=$(wc -c < "$file" | tr -d ' ')
  if (( size > MAX_BYTES )); then
    echo "10MB가 넘어요($file, ${size}B). GIF는 길이·폭을 줄이고, 영상은 잘라서 다시 주세요." >&2
    exit 2
  fi
done

repo="${MEDIA_REPO:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"

# 기존 브랜치 내용 위에 쌓는다. 없으면 --create-branch일 때만 빈 브랜치로 시작한다.
parent=""
if git ls-remote --exit-code --heads "$REMOTE" "$BRANCH" >/dev/null 2>&1; then
  git fetch -q "$REMOTE" "refs/heads/$BRANCH"
  parent=$(git rev-parse FETCH_HEAD)
elif [[ "$create_branch" != true ]]; then
  echo "원격에 $BRANCH 브랜치가 없어요. 사람에게 만들어도 되는지 묻고, 괜찮다면 --create-branch를 붙여 다시 실행하세요." >&2
  exit 3
fi

tmp_index=$(mktemp)
trap 'rm -f "$tmp_index"' EXIT
rm -f "$tmp_index"
export GIT_INDEX_FILE="$tmp_index"
if [[ -n "$parent" ]]; then
  git read-tree "$parent"
else
  git read-tree --empty
fi

for file in "$@"; do
  blob=$(git hash-object -w "$file")
  git update-index --add --cacheinfo "100644,$blob,$number/$(basename "$file")"
done

tree=$(git write-tree)
message="chore: PR 캡처 추가 (#$number)"
if [[ -n "$parent" ]]; then
  commit=$(git commit-tree "$tree" -p "$parent" -m "$message")
else
  commit=$(git commit-tree "$tree" -m "$message")
fi
unset GIT_INDEX_FILE

if [[ "$dry_run" == true ]]; then
  echo "(dry-run) push하지 않았어요. 만든 커밋: $commit" >&2
else
  git push -q "$REMOTE" "$commit:refs/heads/$BRANCH"
fi

for file in "$@"; do
  name=$(basename "$file")
  path="$number/$name"
  case "${name##*.}" in
    png|PNG|jpg|jpeg|JPG|gif|GIF)
      echo "![$name](https://raw.githubusercontent.com/$repo/$commit/$path)" ;;
    *)
      echo "[$name](https://github.com/$repo/blob/$commit/$path)" ;;
  esac
done
