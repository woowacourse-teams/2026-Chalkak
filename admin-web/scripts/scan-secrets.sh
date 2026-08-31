#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(git -C "$script_dir" rev-parse --show-toplevel)"
scanner="${1:-gitleaks}"
bundle_dir="$repo_root/admin-web/.next/static"

if ! command -v "$scanner" >/dev/null 2>&1; then
  printf 'Gitleaks is required. Install the pinned version documented in README.md.\n' >&2
  exit 1
fi

if [[ ! -d "$bundle_dir" ]]; then
  printf 'Browser build output is missing. Run the real-mode build before scanning.\n' >&2
  exit 1
fi

# Inspect tracked history only: never open an untracked local .env file.
# Full redaction also applies to failure output; do not add --verbose or raw reports.
"$scanner" git --redact=100 --no-banner --log-level warn \
  --log-opts='HEAD -- admin-web .github/workflows/admin-web-ci.yml' "$repo_root"
"$scanner" dir --redact=100 --no-banner --log-level warn "$bundle_dir"
printf 'Admin web Git history and browser bundle secret scans passed.\n'
