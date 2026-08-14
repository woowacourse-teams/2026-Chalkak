#!/usr/bin/env bash
set -euo pipefail

systemctl stop chalcak-backend.service 2>/dev/null || true

if ! id chalcak >/dev/null 2>&1; then
  useradd --system --home-dir /opt/chalcak --shell /usr/sbin/nologin chalcak
fi

# CodeDeploy Install 단계가 파일을 복사하기 전에 목적지가 존재해야 한다.
install -d -m 0750 -o chalcak -g chalcak /opt/chalcak/app /opt/chalcak/logs
install -d -m 0750 -o root -g root /opt/chalcak/bin /opt/chalcak/db /opt/chalcak/backups
