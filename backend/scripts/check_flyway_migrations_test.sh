#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TEMP_DIR}"' EXIT

TEST_COUNT=0

create_repository() {
  local name="$1"
  local repository="${TEMP_DIR}/${name}"

  mkdir -p "${repository}/backend/scripts"
  mkdir -p "${repository}/backend/src/main/resources/db/migration"
  cp "${SCRIPT_DIR}/check_flyway_migrations.sh" "${repository}/backend/scripts/"

  git -C "${repository}" init --quiet
  git -C "${repository}" config user.name "Flyway Contract Test"
  git -C "${repository}" config user.email "flyway-contract-test@example.com"

  printf '%s\n' 'CREATE TABLE base_table (id BIGINT PRIMARY KEY);' \
    > "${repository}/backend/src/main/resources/db/migration/V202608261823__create_base_table.sql"
  git -C "${repository}" add .
  git -C "${repository}" commit --quiet -m "base migration"

  printf '%s\n' "${repository}"
}

commit_all() {
  local repository="$1"
  local message="$2"

  git -C "${repository}" add -A
  git -C "${repository}" commit --quiet -m "${message}"
}

expect_success() {
  local name="$1"
  local repository="$2"
  local base_sha="$3"
  local head_sha="$4"
  local output

  TEST_COUNT=$((TEST_COUNT + 1))
  if ! output="$("${repository}/backend/scripts/check_flyway_migrations.sh" "${base_sha}" "${head_sha}" 2>&1)"; then
    echo "FAIL: ${name}" >&2
    echo "${output}" >&2
    exit 1
  fi
  echo "PASS: ${name}"
}

expect_failure() {
  local name="$1"
  local expected_message="$2"
  local repository="$3"
  local base_sha="$4"
  local head_sha="$5"
  local output

  TEST_COUNT=$((TEST_COUNT + 1))
  if output="$("${repository}/backend/scripts/check_flyway_migrations.sh" "${base_sha}" "${head_sha}" 2>&1)"; then
    echo "FAIL: ${name} (검사가 성공했지만 실패해야 합니다.)" >&2
    echo "${output}" >&2
    exit 1
  fi

  if [[ "${output}" != *"${expected_message}"* ]]; then
    echo "FAIL: ${name} (예상한 오류 메시지를 찾지 못했습니다.)" >&2
    echo "Expected: ${expected_message}" >&2
    echo "Actual: ${output}" >&2
    exit 1
  fi
  echo "PASS: ${name}"
}

repository="$(create_repository no-change)"
base_sha="$(git -C "${repository}" rev-parse HEAD)"
expect_success "migration 변경 없음" "${repository}" "${base_sha}" "${base_sha}"

repository="$(create_repository newer-version)"
base_sha="$(git -C "${repository}" rev-parse HEAD)"
printf '%s\n' 'ALTER TABLE base_table ADD COLUMN name TEXT;' \
  > "${repository}/backend/src/main/resources/db/migration/V202608271030__add_name.sql"
commit_all "${repository}" "newer migration"
head_sha="$(git -C "${repository}" rev-parse HEAD)"
expect_success "최신 버전 추가" "${repository}" "${base_sha}" "${head_sha}"

repository="$(create_repository older-version)"
base_sha="$(git -C "${repository}" rev-parse HEAD)"
printf '%s\n' 'ALTER TABLE base_table ADD COLUMN name TEXT;' \
  > "${repository}/backend/src/main/resources/db/migration/V202608251628__add_name.sql"
commit_all "${repository}" "older migration"
head_sha="$(git -C "${repository}" rev-parse HEAD)"
expect_failure "오래된 버전 추가" "마지막 버전보다 오래되었습니다" \
  "${repository}" "${base_sha}" "${head_sha}"

repository="$(create_repository duplicate-version)"
base_sha="$(git -C "${repository}" rev-parse HEAD)"
printf '%s\n' 'ALTER TABLE base_table ADD COLUMN first_name TEXT;' \
  > "${repository}/backend/src/main/resources/db/migration/V202608271030__add_first_name.sql"
printf '%s\n' 'ALTER TABLE base_table ADD COLUMN last_name TEXT;' \
  > "${repository}/backend/src/main/resources/db/migration/V202608271030__add_last_name.sql"
commit_all "${repository}" "duplicate migrations"
head_sha="$(git -C "${repository}" rev-parse HEAD)"
expect_failure "PR 내부 버전 중복" "동일한 버전의 Flyway migration" \
  "${repository}" "${base_sha}" "${head_sha}"

repository="$(create_repository modified-migration)"
base_sha="$(git -C "${repository}" rev-parse HEAD)"
printf '%s\n' 'CREATE TABLE changed_table (id BIGINT PRIMARY KEY);' \
  > "${repository}/backend/src/main/resources/db/migration/V202608261823__create_base_table.sql"
commit_all "${repository}" "modify migration"
head_sha="$(git -C "${repository}" rev-parse HEAD)"
expect_failure "기존 migration 수정" "기존 Flyway migration을 수정할 수 없습니다" \
  "${repository}" "${base_sha}" "${head_sha}"

repository="$(create_repository deleted-migration)"
base_sha="$(git -C "${repository}" rev-parse HEAD)"
rm "${repository}/backend/src/main/resources/db/migration/V202608261823__create_base_table.sql"
commit_all "${repository}" "delete migration"
head_sha="$(git -C "${repository}" rev-parse HEAD)"
expect_failure "기존 migration 삭제" "기존 Flyway migration을 삭제할 수 없습니다" \
  "${repository}" "${base_sha}" "${head_sha}"

repository="$(create_repository renamed-migration)"
base_sha="$(git -C "${repository}" rev-parse HEAD)"
mv \
  "${repository}/backend/src/main/resources/db/migration/V202608261823__create_base_table.sql" \
  "${repository}/backend/src/main/resources/db/migration/V202608271030__create_base_table.sql"
commit_all "${repository}" "rename migration"
head_sha="$(git -C "${repository}" rev-parse HEAD)"
expect_failure "기존 migration 이름 변경" "기존 Flyway migration의 이름을 변경할 수 없습니다" \
  "${repository}" "${base_sha}" "${head_sha}"

repository="$(create_repository invalid-name)"
base_sha="$(git -C "${repository}" rev-parse HEAD)"
printf '%s\n' 'ALTER TABLE base_table ADD COLUMN name TEXT;' \
  > "${repository}/backend/src/main/resources/db/migration/V20260827__add_name.sql"
commit_all "${repository}" "invalid migration name"
head_sha="$(git -C "${repository}" rev-parse HEAD)"
expect_failure "잘못된 파일명" "파일명이 규칙과 다릅니다" \
  "${repository}" "${base_sha}" "${head_sha}"

repository="$(create_repository stale-branch)"
target_branch="$(git -C "${repository}" branch --show-current)"
git -C "${repository}" switch --quiet -c feature
printf '%s\n' 'ALTER TABLE base_table ADD COLUMN feature_value TEXT;' \
  > "${repository}/backend/src/main/resources/db/migration/V202608271000__add_feature_value.sql"
commit_all "${repository}" "feature migration"
head_sha="$(git -C "${repository}" rev-parse HEAD)"
git -C "${repository}" switch --quiet "${target_branch}"
printf '%s\n' 'ALTER TABLE base_table ADD COLUMN base_value TEXT;' \
  > "${repository}/backend/src/main/resources/db/migration/V202608271100__add_base_value.sql"
commit_all "${repository}" "new target migration"
base_sha="$(git -C "${repository}" rev-parse HEAD)"
expect_failure "대상 브랜치보다 오래된 PR" "마지막 버전보다 오래되었습니다" \
  "${repository}" "${base_sha}" "${head_sha}"

echo "All ${TEST_COUNT} Flyway migration contract tests passed."
