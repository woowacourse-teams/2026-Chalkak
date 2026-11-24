#!/bin/sh
# Claude Stop hook: perform only local, bounded work before returning.
[ "$(git config --global --get chalkak.metrics 2>/dev/null)" = on ] || exit 0
base="${CHALKAK_METRICS_HOME:-$HOME/.config/chalkak/metrics}"
mkdir -p "$base" 2>/dev/null || exit 0
input=$(mktemp "$base/hook.XXXXXXXX") || exit 0
cat > "$input" || { rm -f "$input"; exit 0; }
script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
nohup python3 "$script_dir/collect_metrics.py" --input "$input" </dev/null >/dev/null 2>&1 &
exit 0
