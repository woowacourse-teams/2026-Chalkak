#!/usr/bin/env bash
set -euo pipefail

if ! id chalkak >/dev/null 2>&1; then
  useradd --system --home-dir /opt/chalkak --shell /usr/sbin/nologin chalkak
fi

# CodeDeploy Install 단계가 파일을 복사하기 전에 목적지가 존재해야 한다.
install -d -m 0750 -o root -g root /opt/chalkak/incoming
install -d -m 0750 -o chalkak -g chalkak /opt/chalkak/logs
install -d -m 0750 -o root -g root /opt/chalkak/bin /opt/chalkak/db /opt/chalkak/backups
