#!/usr/bin/env bash
set -euo pipefail
umask 077
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
for tool in docker rclone openssl sha256sum; do command -v "$tool" >/dev/null || exit 1; done
docker info >/dev/null
TEST_ROOT=$(mktemp -d)
source_id=''
restore_id=''
cleanup() {
  [[ -z "$restore_id" ]] || docker rm --force "$restore_id" >/dev/null 2>&1 || true
  [[ -z "$source_id" ]] || docker rm --force --volumes "$source_id" >/dev/null 2>&1 || true
  rm -rf -- "$TEST_ROOT"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'echo "FAIL: line=$LINENO" >&2' ERR
mkdir -p "$TEST_ROOT/bin" "$TEST_ROOT/remote" "$TEST_ROOT/work"
openssl req -x509 -newkey rsa:2048 -nodes -keyout "$TEST_ROOT/tls.key" \
  -out "$TEST_ROOT/tls.crt" -days 1 -subj /CN=localhost -addext subjectAltName=DNS:localhost >/dev/null 2>&1
printf 'localhost:5432:fixture:fixture:fixture-only\n' > "$TEST_ROOT/pgpass"
source_id=$(docker create --network none --tmpfs /var/lib/postgresql \
  --env POSTGRES_HOST_AUTH_METHOD=trust --env POSTGRES_USER=fixture --env POSTGRES_DB=fixture \
  postgres:18.4 sh -c 'chown postgres:postgres /tmp/tls.key /tmp/tls.crt /tmp/pgpass; chmod 600 /tmp/tls.key /tmp/pgpass; exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/tmp/tls.crt -c ssl_key_file=/tmp/tls.key')
for file in tls.key tls.crt pgpass; do docker cp "$TEST_ROOT/$file" "$source_id:/tmp/$file" >/dev/null; done
docker start "$source_id" >/dev/null
ready=false
for ((attempt=0; attempt<30; attempt++)); do
  if docker exec "$source_id" pg_isready -h 127.0.0.1 -U fixture -d fixture >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
[[ "$ready" == true ]] || { docker logs "$source_id"; exit 1; }
docker exec -i "$source_id" psql -X -q -U fixture -d fixture -v ON_ERROR_STOP=1 <<'SQL'
CREATE TABLE users (id bigserial PRIMARY KEY, name text NOT NULL);
CREATE TABLE photos (id bigserial PRIMARY KEY, storage_key text UNIQUE NOT NULL);
CREATE TABLE posts (id bigserial PRIMARY KEY, user_id bigint REFERENCES users(id), photo_id bigint REFERENCES photos(id));
CREATE INDEX posts_user_idx ON posts(user_id);
CREATE TABLE "한글 공백" (note text);
INSERT INTO users(name) VALUES ('첫 사용자'), ('second');
INSERT INTO photos(storage_key) VALUES ('chalkak/posts/prod/original/fixture.webp');
INSERT INTO posts(user_id, photo_id) VALUES (1, 1), (2, 1);
INSERT INTO "한글 공백" VALUES ('UTF-8과 공백 식별자');
SQL

# 네트워크 없는 시험 컨테이너의 실제 PG 도구를 사용한다. DB 명령 대역이 아니다.
export SOURCE_CONTAINER="$source_id"
cat > "$TEST_ROOT/bin/pg_dump" <<'WRAPPER'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$1" == --version ]]; then exec docker exec "$SOURCE_CONTAINER" pg_dump --version; fi
arguments=()
for argument in "$@"; do
  case "$argument" in --file=*) target=${argument#--file=} ;; *) arguments+=("$argument") ;; esac
done
docker exec --env PGHOST --env PGPORT --env PGDATABASE --env PGUSER --env PGSSLMODE \
  --env PGCONNECT_TIMEOUT --env PGOPTIONS --env PGSSLROOTCERT=/tmp/tls.crt --env PGPASSFILE=/tmp/pgpass \
  "$SOURCE_CONTAINER" pg_dump "${arguments[@]}" > "$target"
WRAPPER
cat > "$TEST_ROOT/bin/pg_restore" <<'WRAPPER'
#!/usr/bin/env bash
set -euo pipefail
if [[ "$1" == --version ]]; then exec docker exec "$SOURCE_CONTAINER" pg_restore --version; fi
docker exec -i "$SOURCE_CONTAINER" pg_restore --list < "$2"
WRAPPER
chmod +x "$TEST_ROOT/bin/pg_dump" "$TEST_ROOT/bin/pg_restore"
export PATH="$TEST_ROOT/bin:$PATH"
unset PGPASSWORD
printf '[backup]\ntype = alias\nremote = %s/remote\n' "$TEST_ROOT" > "$TEST_ROOT/rclone.conf"
cat > "$TEST_ROOT/backup.env" <<EOF
PGHOST=localhost
PGPORT=5432
PGDATABASE=fixture
PGUSER=fixture
PGSSLROOTCERT=$TEST_ROOT/tls.crt
PGPASSFILE=$TEST_ROOT/pgpass
RCLONE_CONFIG=$TEST_ROOT/rclone.conf
DB_BACKUP_DESTINATION=backup:bucket/chalkak/db
DB_BACKUP_TEMP_ROOT=$TEST_ROOT/work
EOF
bash "$SCRIPT_DIR/../db-backup.sh" "$TEST_ROOT/backup.env" > "$TEST_ROOT/backup.log" 2>&1 || {
  cat "$TEST_ROOT/backup.log"; exit 1;
}
run_dir=$(find "$TEST_ROOT/remote" -name COMPLETE -print)
run_dir=${run_dir%/COMPLETE}
run_id=${run_dir##*/}
echo 'PASS: 실제 PostgreSQL 18 TLS 덤프 → 실제 rclone 보관·내용 검증'

bash "$SCRIPT_DIR/../db-restore.sh" "$TEST_ROOT/backup.env" "$run_id" --keep-container \
  > "$TEST_ROOT/restore.log" 2>&1 || { cat "$TEST_ROOT/restore.log"; exit 1; }
restore_id=$(awk '/^검증용 컨테이너:/ {print $3}' "$TEST_ROOT/restore.log")
[[ -n "$restore_id" ]] || exit 1
grep -q '^public.posts=2$' "$TEST_ROOT/restore.log"
grep -q '^public.users=2$' "$TEST_ROOT/restore.log"
grep -q '^public.한글 공백=1$' "$TEST_ROOT/restore.log"
sql='SELECT json_build_object('
sql+="'users', (SELECT json_agg(t ORDER BY id) FROM users t), "
sql+="'photos', (SELECT json_agg(t ORDER BY id) FROM photos t), "
sql+="'posts', (SELECT json_agg(t ORDER BY id) FROM posts t), "
sql+="'notes', (SELECT json_agg(note ORDER BY note) FROM \"한글 공백\"));"
original=$(docker exec "$source_id" psql -XAt -U fixture -d fixture -c "$sql")
restored=$(docker exec "$restore_id" psql -XAt -U restore -d restored -c "$sql")
[[ "$original" == "$restored" ]] || exit 1
echo 'PASS: 원본·복구 DB의 전체 fixture 사용자·사진·게시글 값 일치'
if docker exec "$restore_id" psql -X -U restore -d restored -v ON_ERROR_STOP=1 \
  -c 'INSERT INTO posts(user_id, photo_id) VALUES (999, 1)' >/dev/null 2>&1; then exit 1; fi
[[ "$(docker exec "$restore_id" psql -XAt -U restore -d restored -c "SELECT count(*) FROM pg_indexes WHERE indexname='posts_user_idx'")" == 1 ]] || exit 1
[[ "$(docker exec "$restore_id" psql -XAt -U restore -d restored -c "INSERT INTO users(name) VALUES ('third') RETURNING id" | head -1)" == 3 ]] || exit 1
echo 'PASS: 외래키·인덱스·시퀀스 복원'
docker rm --force "$restore_id" >/dev/null
restore_id=''
baseline=$(docker ps -aq --filter label=chalkak.backup.restore=true | sort)
bash "$SCRIPT_DIR/../db-restore.sh" "$TEST_ROOT/backup.env" "$run_id" > "$TEST_ROOT/default.log" 2>&1
grep -q ' RESTORED ' "$TEST_ROOT/default.log"
[[ "$(docker ps -aq --filter label=chalkak.backup.restore=true | sort)" == "$baseline" ]] || exit 1
[[ -z "$(ls -A "$TEST_ROOT/work")" ]] || exit 1
echo 'PASS: 기본 복구 성공 후 이번 컨테이너·임시 파일 정리'

assert_restore_failed() {
  local expected=$1
  if bash "$SCRIPT_DIR/../db-restore.sh" "$TEST_ROOT/backup.env" "$run_id" \
    > "$TEST_ROOT/failure.log" 2>&1; then echo 'FAIL: 실패를 성공 처리'; exit 1; fi
  grep -q "FAILED stage=$expected " "$TEST_ROOT/failure.log" || { cat "$TEST_ROOT/failure.log"; exit 1; }
  if grep -q ' RESTORED ' "$TEST_ROOT/failure.log"; then exit 1; fi
  [[ -z "$(ls -A "$TEST_ROOT/work")" ]] || exit 1
  [[ "$(docker ps -aq --filter label=chalkak.backup.restore=true | sort)" == "$baseline" ]] || exit 1
  echo "PASS: $expected 실패 처리와 로컬 임시 파일 정리"
}
cp "$run_dir/database.dump" "$TEST_ROOT/original.dump"
printf X | dd of="$run_dir/database.dump" bs=1 seek=0 conv=notrunc 2>/dev/null
assert_restore_failed verify
cp "$TEST_ROOT/original.dump" "$run_dir/database.dump"
mv "$run_dir/COMPLETE" "$TEST_ROOT/COMPLETE"
assert_restore_failed download
mv "$TEST_ROOT/COMPLETE" "$run_dir/COMPLETE"
if bash "$SCRIPT_DIR/../db-restore.sh" "$TEST_ROOT/backup.env" '../other' >/dev/null 2>&1; then exit 1; fi
echo 'PASS: 잘못된 회차 경로 거부'

# 체크섬이 맞더라도 유효하지 않은 DB 보관본은 실제 복원 단계에서 실패해야 한다.
printf 'not-a-postgresql-archive' > "$run_dir/database.dump"
(cd "$run_dir" && sha256sum database.dump > database.dump.sha256)
bytes=$(wc -c < "$run_dir/database.dump" | tr -d '[:space:]')
printf 'format=pg_dump-custom\nrun=%s\nbytes=%s\n' "$run_id" "$bytes" > "$run_dir/COMPLETE"
assert_restore_failed restore
[[ "$(docker exec "$source_id" psql -XAt -U fixture -d fixture -c "$sql")" == "$original" ]] || exit 1
echo '백업 로그:'
cat "$TEST_ROOT/backup.log"
echo '정상 복구 로그:'
cat "$TEST_ROOT/restore.log"
echo '모든 시험은 합성 데이터·로컬 rclone 저장소·격리된 PostgreSQL 컨테이너 사용 (AWS/RDS 미접속)'
