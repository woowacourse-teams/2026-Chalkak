#!/usr/bin/env bash
set -euo pipefail

CONFIG_FILE=/etc/chalkak/application.env

if [[ ! -f "${CONFIG_FILE}" || -L "${CONFIG_FILE}" ]]; then
  echo "Missing regular configuration file: ${CONFIG_FILE}" >&2
  exit 1
fi

file_security="$(stat -c '%U:%G:%a' "${CONFIG_FILE}")"
if [[ "${file_security}" != root:root:600 ]]; then
  echo "${CONFIG_FILE} must be owned by root:root with mode 600." >&2
  exit 1
fi

required_keys=(
  SPRING_PROFILES_ACTIVE
  SERVER_PORT
  GOOGLE_OIDC_CLIENT_ID
  KAKAO_OIDC_APP_KEY
  SOCIAL_SIGNUP_TOKEN_SECRET
  DB_HOST
  DB_PORT
  DB_NAME
  DB_USERNAME
  DB_PASSWORD
  AWS_REGION
  S3_BUCKET
  S3_PREFIX
  CLOUDFRONT_BASE_URL
  CLOUDFRONT_ORIGIN_PATH
  IMAGE_PROCESSOR_CALLBACK_SECRET
  POST_IMAGE_PROCESSING_TIMEOUT
  SIGNATURE_PROCESSING_TIMEOUT
  CALLBACK_MAX_BODY_BYTES
)

for key in "${required_keys[@]}"; do
  occurrence_count="$(grep -Ec "^${key}=.+$" "${CONFIG_FILE}" || true)"
  if [[ "${occurrence_count}" -ne 1 ]]; then
    echo "Configuration key must appear exactly once with a value: ${key}" >&2
    exit 1
  fi
done

read_value() {
  local key=$1
  awk -F= -v key="${key}" '$1 == key {print substr($0, index($0, "=") + 1); exit}' "${CONFIG_FILE}"
}

profile="$(read_value SPRING_PROFILES_ACTIVE)"
db_host="$(read_value DB_HOST)"
server_port="$(read_value SERVER_PORT)"
aws_region="$(read_value AWS_REGION)"
db_password="$(read_value DB_PASSWORD)"
callback_secret="$(read_value IMAGE_PROCESSOR_CALLBACK_SECRET)"
social_signup_token_secret="$(read_value SOCIAL_SIGNUP_TOKEN_SECRET)"

if [[ "${server_port}" != 8080 ]]; then
  echo "SERVER_PORT must be 8080 for the configured health check." >&2
  exit 1
fi

if [[ "${aws_region}" != ap-northeast-2 ]]; then
  echo "AWS_REGION must be ap-northeast-2." >&2
  exit 1
fi

if [[ ! "${db_password}" =~ ^[A-Za-z0-9._~@%+=,-]{20,}$ ]]; then
  echo "DB_PASSWORD must be at least 20 URL-safe characters without spaces, quotes, #, or $." >&2
  exit 1
fi

if [[ ! "${callback_secret}" =~ ^[A-Za-z0-9._~@%+=,-]{32,}$ ]]; then
  echo "IMAGE_PROCESSOR_CALLBACK_SECRET must be at least 32 URL-safe characters without spaces, quotes, #, or $." >&2
  exit 1
fi

if [[ ! "${social_signup_token_secret}" =~ ^[0-9A-Fa-f]{64}$ ]]; then
  echo "SOCIAL_SIGNUP_TOKEN_SECRET must be exactly 64 hexadecimal characters." >&2
  exit 1
fi

case "${profile}" in
  dev)
    if [[ "${db_host}" != 127.0.0.1 && "${db_host}" != localhost ]]; then
      echo "The dev profile must use the PostgreSQL instance on this EC2 host." >&2
      exit 1
    fi
    ;;
  prod)
    if [[ "${db_host}" == 127.0.0.1 || "${db_host}" == localhost ]]; then
      echo "The prod profile must use an external RDS endpoint." >&2
      exit 1
    fi
    ;;
  *)
    echo "SPRING_PROFILES_ACTIVE must be dev or prod." >&2
    exit 1
    ;;
esac
