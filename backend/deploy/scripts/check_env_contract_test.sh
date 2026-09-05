#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TEST_TEMP_DIR}"' EXIT

TEST_COUNT=0
FAILURE_COUNT=0
FIXTURE_VALUE='DO_NOT_LEAK_SENTINEL_$2b$12$synthetic=value'

create_fixture() {
  local name="$1"
  local fixture_root="${TEST_TEMP_DIR}/${name}"
  local key

  mkdir -p "${fixture_root}/deploy/scripts" "${fixture_root}/deploy/examples"
  mkdir -p "${fixture_root}/src/main/resources" "${fixture_root}/src/test/resources"
  mkdir -p "${fixture_root}/src/main/java"
  cp "${SCRIPT_DIR}/check_env_contract.sh" "${fixture_root}/deploy/scripts/"

  # 실제 저장소 설정이나 개인 .env 대신 이 테스트가 만든 합성 계약만 사용한다.
  printf '%s\n' 'fixture: ${SHARED_REQUIRED}' \
    > "${fixture_root}/src/main/resources/application.yml"
  printf '%s\n' 'fixture: ${LOCAL_REQUIRED:fixture-default}' \
    > "${fixture_root}/src/main/resources/application-local.yml"
  printf '%s\n' 'fixture: ${DEPLOY_REQUIRED}' \
    > "${fixture_root}/src/main/resources/application-dev.yml"
  printf '%s\n' 'fixture: ${DEPLOY_REQUIRED}' \
    > "${fixture_root}/src/main/resources/application-prod.yml"
  printf '%s\n' 'fixture: ${TEST_REQUIRED}' \
    > "${fixture_root}/src/test/resources/application-test.yml"
  printf '%s\n' 'class Fixture { String value = "${JAVA_REQUIRED}"; }' \
    > "${fixture_root}/src/main/java/Fixture.java"

  for key in SHARED_REQUIRED LOCAL_REQUIRED TEST_REQUIRED JAVA_REQUIRED; do
    printf '%s=%s\n' "${key}" "${FIXTURE_VALUE}" >> "${fixture_root}/.env.example"
  done

  for key in SHARED_REQUIRED DEPLOY_REQUIRED JAVA_REQUIRED SPRING_PROFILES_ACTIVE SERVER_PORT; do
    printf '%s=%s\n' "${key}" "${FIXTURE_VALUE}" \
      >> "${fixture_root}/deploy/examples/application.dev.env.example"
  done
  cp "${fixture_root}/deploy/examples/application.dev.env.example" \
    "${fixture_root}/deploy/examples/application.prod.env.example"

  {
    printf '%s\n' 'required_keys=('
    for key in SHARED_REQUIRED DEPLOY_REQUIRED JAVA_REQUIRED SPRING_PROFILES_ACTIVE SERVER_PORT; do
      printf '  %s\n' "${key}"
    done
    printf '%s\n' ')'
  } > "${fixture_root}/deploy/scripts/check_configuration.sh"

  printf '%s\n' "${fixture_root}"
}

remove_matching_line() {
  local fixture_file="$1"
  local pattern="$2"

  awk -v pattern="${pattern}" '$0 !~ pattern' "${fixture_file}" > "${fixture_file}.tmp"
  mv "${fixture_file}.tmp" "${fixture_file}"
}

insert_required_key() {
  local fixture_root="$1"
  local key="$2"
  local fixture_file="${fixture_root}/deploy/scripts/check_configuration.sh"

  awk -v key="${key}" '
    /^\)/ { print "  " key }
    { print }
  ' "${fixture_file}" > "${fixture_file}.tmp"
  mv "${fixture_file}.tmp" "${fixture_file}"
}

expect_result() {
  local name="$1"
  local fixture_root="$2"
  local expected_result="$3"
  local expected_message="${4:-}"
  local output
  local status=0

  TEST_COUNT=$((TEST_COUNT + 1))
  output="$(bash "${fixture_root}/deploy/scripts/check_env_contract.sh" 2>&1)" || status=$?

  # 오류가 나더라도 값 전체나 일부를 로그에 출력해서는 안 된다.
  if [[ "${output}" == *DO_NOT_LEAK_SENTINEL* ]]; then
    echo "FAIL: ${name} (환경변수 값이 검사 출력에 노출됐습니다.)" >&2
    FAILURE_COUNT=$((FAILURE_COUNT + 1))
    return
  fi

  if [[ "${expected_result}" == success && "${status}" -ne 0 ]]; then
    echo "FAIL: ${name} (성공해야 하지만 종료 코드 ${status}로 실패했습니다.)" >&2
    FAILURE_COUNT=$((FAILURE_COUNT + 1))
    return
  fi

  if [[ "${expected_result}" == failure && "${status}" -eq 0 ]]; then
    echo "FAIL: ${name} (검사가 성공했지만 실패해야 합니다.)" >&2
    FAILURE_COUNT=$((FAILURE_COUNT + 1))
    return
  fi

  if [[ -n "${expected_message}" && "${output}" != *"${expected_message}"* ]]; then
    echo "FAIL: ${name} (예상한 오류 메시지를 찾지 못했습니다: ${expected_message})" >&2
    FAILURE_COUNT=$((FAILURE_COUNT + 1))
    return
  fi

  echo "PASS: ${name}"
}

fixture_root="$(create_fixture ci-without-local-env)"
expect_result "개인 .env가 없는 CI 환경의 계약 일치" "${fixture_root}" success

fixture_root="$(create_fixture matching-local-env)"
cp "${fixture_root}/.env.example" "${fixture_root}/.env"
expect_result "개인 .env 계약 일치 및 특수문자 값 미출력" "${fixture_root}" success

fixture_root="$(create_fixture missing-local-example-key)"
remove_matching_line "${fixture_root}/.env.example" '^LOCAL_REQUIRED='
expect_result "로컬 예제 키 누락" "${fixture_root}" failure 'LOCAL_REQUIRED'

for deploy_environment in dev prod; do
  fixture_root="$(create_fixture "missing-${deploy_environment}-example-key")"
  remove_matching_line "${fixture_root}/deploy/examples/application.${deploy_environment}.env.example" \
    '^DEPLOY_REQUIRED='
  expect_result "${deploy_environment} 배포 예제 키 누락" "${fixture_root}" failure 'DEPLOY_REQUIRED'
done

fixture_root="$(create_fixture missing-cd-required-key)"
remove_matching_line "${fixture_root}/deploy/scripts/check_configuration.sh" \
  '^[[:space:]]*DEPLOY_REQUIRED$'
expect_result "CD required_keys 키 누락" "${fixture_root}" failure 'DEPLOY_REQUIRED'

fixture_root="$(create_fixture missing-local-env-key)"
cp "${fixture_root}/.env.example" "${fixture_root}/.env"
remove_matching_line "${fixture_root}/.env" '^LOCAL_REQUIRED='
expect_result "개인 .env 키 누락" "${fixture_root}" failure 'LOCAL_REQUIRED'

for fixture_file in .env.example deploy/examples/application.dev.env.example \
  deploy/examples/application.prod.env.example .env; do
  fixture_name="${fixture_file//\//-}"
  fixture_root="$(create_fixture "duplicate-${fixture_name}")"
  if [[ "${fixture_file}" == .env ]]; then
    cp "${fixture_root}/.env.example" "${fixture_root}/.env"
  fi
  printf 'SHARED_REQUIRED=%s\n' "${FIXTURE_VALUE}" >> "${fixture_root}/${fixture_file}"
  expect_result "${fixture_file} 키 중복 및 값 미출력" "${fixture_root}" failure \
    'Duplicate environment keys'

  fixture_root="$(create_fixture "unexpected-${fixture_name}")"
  if [[ "${fixture_file}" == .env ]]; then
    cp "${fixture_root}/.env.example" "${fixture_root}/.env"
  fi
  printf 'UNEXPECTED_KEY=%s\n' "${FIXTURE_VALUE}" >> "${fixture_root}/${fixture_file}"
  expect_result "${fixture_file} 불필요한 키 및 값 미출력" "${fixture_root}" failure 'UNEXPECTED_KEY'
done

fixture_root="$(create_fixture duplicate-cd-required-key)"
insert_required_key "${fixture_root}" DEPLOY_REQUIRED
expect_result "CD required_keys 키 중복" "${fixture_root}" failure 'Duplicate environment keys'

fixture_root="$(create_fixture unexpected-cd-required-key)"
insert_required_key "${fixture_root}" UNEXPECTED_KEY
expect_result "CD required_keys 불필요한 키" "${fixture_root}" failure 'UNEXPECTED_KEY'

for fixture_file in .env.example .env; do
  fixture_root="$(create_fixture "invalid-assignment-${fixture_file}")"
  if [[ "${fixture_file}" == .env ]]; then
    cp "${fixture_root}/.env.example" "${fixture_root}/.env"
  fi
  printf 'invalid assignment %s\n' "${FIXTURE_VALUE}" >> "${fixture_root}/${fixture_file}"
  expect_result "${fixture_file} 잘못된 assignment 및 값 미출력" "${fixture_root}" failure \
    'Invalid environment assignment'
done

# dev/prod가 같은 키를 참조하므로 dev 파일 누락을 무시하면 키 비교만으로는 잡지 못한다.
fixture_root="$(create_fixture missing-required-application-file)"
rm "${fixture_root}/src/main/resources/application-dev.yml"
expect_result "필수 application-dev.yml 파일 누락" "${fixture_root}" failure

if [[ "${FAILURE_COUNT}" -gt 0 ]]; then
  echo "${FAILURE_COUNT} of ${TEST_COUNT} environment contract tests failed." >&2
  exit 1
fi

echo "All ${TEST_COUNT} environment contract tests passed."
