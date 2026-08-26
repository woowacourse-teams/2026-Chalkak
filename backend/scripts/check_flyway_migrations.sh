#!/usr/bin/env bash
set -euo pipefail

export LC_ALL=C

if [[ "$#" -ne 2 ]]; then
  echo "Usage: $0 <base-sha> <head-sha>" >&2
  exit 2
fi

BASE_SHA="$1"
HEAD_SHA="$2"
BACKEND_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPOSITORY_ROOT="$(git -C "${BACKEND_DIR}" rev-parse --show-toplevel)"
BACKEND_PREFIX="$(git -C "${BACKEND_DIR}" rev-parse --show-prefix)"
MIGRATION_DIR="${BACKEND_PREFIX}src/main/resources/db/migration"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TEMP_DIR}"' EXIT

assert_commit_exists() {
  local revision="$1"
  local label="$2"

  if ! git -C "${REPOSITORY_ROOT}" cat-file -e "${revision}^{commit}" 2>/dev/null; then
    echo "${label} commit을 찾을 수 없습니다: ${revision}" >&2
    echo "CI checkout에서 전체 Git 기록을 가져왔는지 확인해주세요." >&2
    exit 2
  fi
}

extract_version() {
  local path="$1"
  local filename="${path##*/}"

  if [[ "${filename}" =~ ^V([0-9]{12})__.+\.sql$ ]]; then
    printf '%s\n' "${BASH_REMATCH[1]}"
    return 0
  fi

  return 1
}

assert_commit_exists "${BASE_SHA}" "대상 브랜치"
assert_commit_exists "${HEAD_SHA}" "PR"

MERGE_BASE="$(git -C "${REPOSITORY_ROOT}" merge-base "${BASE_SHA}" "${HEAD_SHA}")"
if [[ -z "${MERGE_BASE}" ]]; then
  echo "대상 브랜치와 PR의 공통 커밋을 찾을 수 없습니다." >&2
  exit 2
fi

BASE_MAX_VERSION=""
while IFS= read -r path; do
  [[ -z "${path}" ]] && continue

  if version="$(extract_version "${path}")"; then
    if [[ -z "${BASE_MAX_VERSION}" || "${version}" > "${BASE_MAX_VERSION}" ]]; then
      BASE_MAX_VERSION="${version}"
    fi
  fi
done < <(git -C "${REPOSITORY_ROOT}" ls-tree -r --name-only "${BASE_SHA}" -- "${MIGRATION_DIR}")

CHANGES_FILE="${TEMP_DIR}/changes"
ADDED_MIGRATIONS_FILE="${TEMP_DIR}/added-migrations"
ADDED_VERSIONS_FILE="${TEMP_DIR}/added-versions"

git -C "${REPOSITORY_ROOT}" diff \
  --name-status \
  --find-renames \
  "${MERGE_BASE}" \
  "${HEAD_SHA}" \
  -- "${MIGRATION_DIR}" > "${CHANGES_FILE}"

validation_failed=false

while IFS=$'\t' read -r status first_path second_path; do
  [[ -z "${status}" ]] && continue

  case "${status}" in
    A)
      if ! version="$(extract_version "${first_path}")"; then
        echo "Flyway migration 파일명이 규칙과 다릅니다: ${first_path}" >&2
        echo "파일명은 VyyyyMMddHHmm__description.sql 형식이어야 합니다." >&2
        validation_failed=true
        continue
      fi

      printf '%s\n' "${first_path}" >> "${ADDED_MIGRATIONS_FILE}"
      printf '%s\t%s\n' "${version}" "${first_path}" >> "${ADDED_VERSIONS_FILE}"

      if [[ -n "${BASE_MAX_VERSION}" && ( "${version}" == "${BASE_MAX_VERSION}" || "${version}" < "${BASE_MAX_VERSION}" ) ]]; then
        echo "Flyway migration 버전이 대상 브랜치의 마지막 버전보다 오래되었습니다." >&2
        echo "  대상 브랜치 마지막 버전: V${BASE_MAX_VERSION}" >&2
        echo "  추가된 migration 버전: V${version}" >&2
        echo "  대상 파일: ${first_path}" >&2
        echo "현재 시간 기준의 새 버전으로 파일명을 변경해주세요." >&2
        validation_failed=true
      fi
      ;;
    R*)
      echo "기존 Flyway migration의 이름을 변경할 수 없습니다." >&2
      echo "  변경 전: ${first_path}" >&2
      echo "  변경 후: ${second_path}" >&2
      validation_failed=true
      ;;
    M)
      echo "기존 Flyway migration을 수정할 수 없습니다: ${first_path}" >&2
      validation_failed=true
      ;;
    D)
      echo "기존 Flyway migration을 삭제할 수 없습니다: ${first_path}" >&2
      validation_failed=true
      ;;
    *)
      echo "허용되지 않은 Flyway migration 변경입니다 (${status}): ${first_path}" >&2
      validation_failed=true
      ;;
  esac
done < "${CHANGES_FILE}"

if [[ -s "${ADDED_VERSIONS_FILE}" ]]; then
  DUPLICATE_VERSIONS_FILE="${TEMP_DIR}/duplicate-versions"
  cut -f1 "${ADDED_VERSIONS_FILE}" | sort | uniq -d > "${DUPLICATE_VERSIONS_FILE}"

  while IFS= read -r duplicate_version; do
    [[ -z "${duplicate_version}" ]] && continue
    echo "PR에 동일한 버전의 Flyway migration이 여러 개 추가되었습니다: V${duplicate_version}" >&2
    awk -F '\t' -v version="${duplicate_version}" '$1 == version { print "  - " $2 }' \
      "${ADDED_VERSIONS_FILE}" >&2
    validation_failed=true
  done < "${DUPLICATE_VERSIONS_FILE}"
fi

if [[ "${validation_failed}" == true ]]; then
  exit 1
fi

if [[ -s "${ADDED_MIGRATIONS_FILE}" ]]; then
  migration_count="$(wc -l < "${ADDED_MIGRATIONS_FILE}" | tr -d '[:space:]')"
  echo "Flyway migration contract is valid: ${migration_count} new migration(s)."
else
  echo "Flyway migration contract is valid: no new migrations."
fi
