#!/bin/bash
# Download one immutable source revision, then install local automation once.
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "자동 설치는 macOS에서 실행하세요." >&2
  exit 1
fi
python3 -c 'import sys; assert sys.version_info >= (3, 10), "Python 3.10 이상이 필요합니다"'
project_root="$(git rev-parse --show-toplevel)"
installer_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

if [[ -f "$installer_dir/manage.py" && -f "$installer_dir/automatic.py" ]]; then
  python3 "$installer_dir/manage.py" setup --project "$project_root"
else
  source_sha="$(git ls-remote https://github.com/woowacourse-teams/2026-Chalkak.git refs/heads/be/develop | awk 'NR == 1 {print $1}')"
  if [[ ! "$source_sha" =~ ^[0-9a-f]{40}$ ]]; then
    echo "백엔드 원본 커밋을 확인할 수 없습니다." >&2
    exit 1
  fi
  download_dir="$(mktemp -d)"
  trap 'rm -rf -- "$download_dir"' EXIT
  echo "설치 도구 원본 커밋: $source_sha"
  for script in manage.py automatic.py; do
    curl --fail --silent --show-error --location --proto '=https' --tlsv1.2 \
      "https://raw.githubusercontent.com/woowacourse-teams/2026-Chalkak/$source_sha/backend/scripts/shared_harness/$script" \
      --output "$download_dir/$script"
  done
  python3 "$download_dir/manage.py" setup --project "$project_root"
fi
