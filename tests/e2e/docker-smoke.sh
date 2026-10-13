#!/bin/sh

set -eu

BROKER_PASSWORD="smoke-broker-password"
EMPLOYEE_PASSWORD="smoke-employee-password"
export BROKER_USERNAME="broker"
export EMPLOYEE_USERNAME="employee"
export JWT_SECRET
JWT_SECRET="$(openssl rand -hex 32)"
export BROKER_PASSWORD_HASH
BROKER_PASSWORD_HASH="$(
  docker run --rm httpd:2.4-alpine \
    htpasswd -bnBC 10 "" "$BROKER_PASSWORD" | tr -d ':\n'
)"
export EMPLOYEE_PASSWORD_HASH
EMPLOYEE_PASSWORD_HASH="$(
  docker run --rm httpd:2.4-alpine \
    htpasswd -bnBC 10 "" "$EMPLOYEE_PASSWORD" | tr -d ':\n'
)"

cleanup() {
  docker compose down --volumes --remove-orphans
}

trap cleanup EXIT

assert_services_ready() {
  running_services="$(docker compose ps --status running --services)"
  printf '%s\n' "$running_services" | grep -qx 'lease-api'
  printf '%s\n' "$running_services" | grep -qx 'interest-rate-service'

  for port in 8080 8081; do
    attempts=0
    until curl --silent --show-error --output /dev/null "http://localhost:${port}"; do
      attempts=$((attempts + 1))
      if [ "$attempts" -eq 20 ]; then
        exit 1
      fi
      sleep 1
    done
  done

  docker compose exec -T lease-api printenv INTEREST_RATE_SERVICE_URL \
    | grep -qx 'http://interest-rate-service:8080'
  test "$(docker compose exec -T lease-api id -u)" -ne 0
  test "$(docker compose exec -T interest-rate-service id -u)" -ne 0

  login_response="$(
    curl --silent --show-error \
      --header 'Content-Type: application/json' \
      --data "{\"username\":\"broker\",\"password\":\"${BROKER_PASSWORD}\"}" \
      http://localhost:8080/auth/login
  )"
  printf '%s\n' "$login_response" | grep -q '"tokenType":"Bearer"'
  access_token="$(
    printf '%s\n' "$login_response" \
      | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'
  )"
  test -n "$access_token"

  test "$(
    curl --silent --output /dev/null --write-out '%{http_code}' \
      --header "Authorization: Bearer ${access_token}" \
      http://localhost:8081/
  )" = "404"
  test "$(
    curl --silent --output /dev/null --write-out '%{http_code}' \
      http://localhost:8081/
  )" = "401"
}

docker compose build --no-cache
docker compose up -d
assert_services_ready
docker compose down
docker compose up -d
assert_services_ready
