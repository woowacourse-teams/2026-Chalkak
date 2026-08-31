#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TEMP_DIR}"' EXIT

extract_env_keys() {
  local file=$1

  awk -F= -v file="${file}" '
    /^[[:space:]]*($|#)/ { next }
    /^[A-Za-z_][A-Za-z0-9_]*=/ {
      print $1
      next
    }
    {
      printf "Invalid environment assignment in %s at line %d.\n", file, NR > "/dev/stderr"
      invalid = 1
    }
    END { exit invalid }
  ' "${file}" | LC_ALL=C sort
}

extract_placeholders() {
  local file

  for file in "$@"; do
    if [[ ! -f "${file}" ]]; then
      echo "Missing environment configuration file: ${file}" >&2
      return 1
    fi
    grep -Eho '\$\{[A-Z][A-Z0-9_]*(:[^}]*)?\}' "${file}" || true
  done \
    | sed -E 's/^\$\{//; s/:.*\}$//; s/\}$//' \
    | LC_ALL=C sort -u
}

extract_java_placeholders() {
  grep -REho '\$\{[A-Z][A-Z0-9_]*(:[^}]*)?\}' "${PROJECT_ROOT}/src/main/java" --include='*.java' 2>/dev/null || true
}

extract_required_keys() {
  awk '
    /^[[:space:]]*required_keys=\(/ {
      capture = 1
      next
    }
    capture && /^[[:space:]]*\)/ { exit }
    capture {
      gsub(/^[[:space:]]+|[[:space:]]+$/, "")
      if ($0 ~ /^[A-Z][A-Z0-9_]*$/) {
        print $0
      }
    }
  ' "${PROJECT_ROOT}/deploy/scripts/check_configuration.sh" | LC_ALL=C sort
}

assert_no_duplicates() {
  local keys_file=$1
  local label=$2
  local duplicates

  duplicates="$(uniq -d "${keys_file}")"
  if [[ -n "${duplicates}" ]]; then
    echo "Duplicate environment keys in ${label}:" >&2
    echo "${duplicates}" >&2
    return 1
  fi
}

assert_same_keys() {
  local expected_file=$1
  local actual_file=$2
  local label=$3
  local missing_file="${TEMP_DIR}/missing"
  local unexpected_file="${TEMP_DIR}/unexpected"

  comm -23 "${expected_file}" "${actual_file}" > "${missing_file}"
  comm -13 "${expected_file}" "${actual_file}" > "${unexpected_file}"

  if [[ -s "${missing_file}" || -s "${unexpected_file}" ]]; then
    echo "Environment key contract mismatch: ${label}" >&2
    if [[ -s "${missing_file}" ]]; then
      echo "Missing keys:" >&2
      sed 's/^/  - /' "${missing_file}" >&2
    fi
    if [[ -s "${unexpected_file}" ]]; then
      echo "Unexpected keys:" >&2
      sed 's/^/  - /' "${unexpected_file}" >&2
    fi
    return 1
  fi
}

LOCAL_EXPECTED="${TEMP_DIR}/local-expected"
LOCAL_EXAMPLE="${TEMP_DIR}/local-example"
DEPLOY_EXPECTED="${TEMP_DIR}/deploy-expected"
DEPLOY_DEV_EXAMPLE="${TEMP_DIR}/deploy-dev-example"
DEPLOY_PROD_EXAMPLE="${TEMP_DIR}/deploy-prod-example"
DEPLOY_REQUIRED="${TEMP_DIR}/deploy-required"
JAVA_PLACEHOLDERS="${TEMP_DIR}/java-placeholders"

extract_java_placeholders \
  | sed -E 's/^\$\{//; s/:.*\}$//; s/\}$//' \
  | LC_ALL=C sort -u > "${JAVA_PLACEHOLDERS}"

{
  extract_placeholders \
    "${PROJECT_ROOT}/src/main/resources/application.yml" \
    "${PROJECT_ROOT}/src/main/resources/application-local.yml" \
    "${PROJECT_ROOT}/src/test/resources/application-test.yml"
  cat "${JAVA_PLACEHOLDERS}"
} | LC_ALL=C sort -u > "${LOCAL_EXPECTED}"

{
  extract_placeholders \
    "${PROJECT_ROOT}/src/main/resources/application.yml" \
    "${PROJECT_ROOT}/src/main/resources/application-dev.yml" \
    "${PROJECT_ROOT}/src/main/resources/application-prod.yml"
  cat "${JAVA_PLACEHOLDERS}"
  printf '%s\n' SPRING_PROFILES_ACTIVE SERVER_PORT
} | LC_ALL=C sort -u > "${DEPLOY_EXPECTED}"

extract_env_keys "${PROJECT_ROOT}/.env.example" > "${LOCAL_EXAMPLE}"
extract_env_keys "${PROJECT_ROOT}/deploy/examples/application.dev.env.example" > "${DEPLOY_DEV_EXAMPLE}"
extract_env_keys "${PROJECT_ROOT}/deploy/examples/application.prod.env.example" > "${DEPLOY_PROD_EXAMPLE}"
extract_required_keys > "${DEPLOY_REQUIRED}"

assert_no_duplicates "${LOCAL_EXAMPLE}" ".env.example"
assert_no_duplicates "${DEPLOY_DEV_EXAMPLE}" "deploy/examples/application.dev.env.example"
assert_no_duplicates "${DEPLOY_PROD_EXAMPLE}" "deploy/examples/application.prod.env.example"
assert_no_duplicates "${DEPLOY_REQUIRED}" "deploy/scripts/check_configuration.sh required_keys"

assert_same_keys "${LOCAL_EXPECTED}" "${LOCAL_EXAMPLE}" ".env.example"
assert_same_keys "${DEPLOY_EXPECTED}" "${DEPLOY_DEV_EXAMPLE}" "deploy/examples/application.dev.env.example"
assert_same_keys "${DEPLOY_EXPECTED}" "${DEPLOY_PROD_EXAMPLE}" "deploy/examples/application.prod.env.example"
assert_same_keys "${DEPLOY_EXPECTED}" "${DEPLOY_REQUIRED}" "deploy/scripts/check_configuration.sh required_keys"

if [[ -f "${PROJECT_ROOT}/.env" ]]; then
  LOCAL_ENV="${TEMP_DIR}/local-env"
  extract_env_keys "${PROJECT_ROOT}/.env" > "${LOCAL_ENV}"
  assert_no_duplicates "${LOCAL_ENV}" ".env"
  assert_same_keys "${LOCAL_EXAMPLE}" "${LOCAL_ENV}" ".env"
fi

echo "Environment key contracts are synchronized."
