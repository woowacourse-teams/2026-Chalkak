#!/usr/bin/env bash
set -euo pipefail

HEALTH_URL=http://127.0.0.1:8080/actuator/health

for _ in {1..60}; do
  if curl --fail --silent --show-error --max-time 3 "${HEALTH_URL}" >/dev/null; then
    exit 0
  fi

  if ! systemctl is-active --quiet chalcak-backend.service; then
    break
  fi

  sleep 2
done

systemctl status chalcak-backend.service --no-pager >&2 || true
journalctl -u chalcak-backend.service -n 200 --no-pager >&2 || true
exit 1
