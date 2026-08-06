#!/usr/bin/env bash
# 저장소를 처음 클론한 팀원이 로컬 백엔드 개발환경을 준비할 때 한 번 실행한다.
set -euo pipefail

BACKEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$BACKEND_DIR"

echo "==> 1. .env 준비"
if [ -f .env ]; then
  echo "    .env 가 이미 있어 건너뛴다."
else
  cp .env.example .env
  echo "    .env.example 을 .env 로 복사했다."
fi

echo "==> 2. Java 확인"
java -version

echo "==> 3. Docker Compose 확인"
docker compose version

echo "==> 4. PostgreSQL 기동"
docker compose up -d

echo "==> 5. 컨테이너가 healthy 가 될 때까지 대기"
status="starting"
for _ in $(seq 1 30); do
  status="$(docker inspect -f '{{.State.Health.Status}}' chalkak-postgres 2>/dev/null || echo "starting")"
  if [ "$status" = "healthy" ]; then
    echo "    chalkak-postgres: healthy"
    break
  fi
  sleep 2
done

if [ "$status" != "healthy" ]; then
  echo "    chalkak-postgres 가 healthy 상태가 되지 않았다. 'docker compose logs postgres' 로 확인한다." >&2
  exit 1
fi

echo "==> 6. 빌드 확인"
./gradlew build

echo
echo "완료. 애플리케이션은 './gradlew bootRun' 으로 실행한다."