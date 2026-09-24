#!/bin/bash
# Release template. package_installer.py pins the reviewed commit and file hashes.
set -euo pipefail

source_sha='@SOURCE_COMMIT@'
manage_sha256='@MANAGE_SHA256@'
automatic_sha256='@AUTOMATIC_SHA256@'
if [[ ! "$source_sha" =~ ^[0-9a-f]{40}$ ]] ||
   [[ ! "$manage_sha256" =~ ^[0-9a-f]{64}$ ]] ||
   [[ ! "$automatic_sha256" =~ ^[0-9a-f]{64}$ ]]; then
  echo "배포용 설치기가 아닙니다. 담당자에게 검토 버전으로 생성한 install.sh를 받으세요." >&2
  exit 1
fi

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "자동 설치는 macOS에서 실행하세요." >&2
  exit 1
fi
python3 -c 'import sys; assert sys.version_info >= (3, 10), "Python 3.10 이상이 필요합니다"'
project_root="$(git rev-parse --show-toplevel)"
download_dir="$(mktemp -d)"
trap 'rm -rf -- "$download_dir"' EXIT
echo "설치 도구 원본 커밋: $source_sha"
for script in manage.py automatic.py; do
  curl --fail --silent --show-error --location --proto '=https' --proto-redir '=https' --tlsv1.2 \
    "https://raw.githubusercontent.com/woowacourse-teams/2026-Chalkak/$source_sha/backend/scripts/shared_harness/$script" \
    --output "$download_dir/$script"
done
python3 - "$download_dir" "$manage_sha256" "$automatic_sha256" <<'PY'
import hashlib
from pathlib import Path
import sys
for name, expected in zip(("manage.py", "automatic.py"), sys.argv[2:]):
    if hashlib.sha256((Path(sys.argv[1]) / name).read_bytes()).hexdigest() != expected:
        sys.exit("설치 파일 검증 실패: " + name)
PY
python3 "$download_dir/manage.py" setup --project "$project_root"
