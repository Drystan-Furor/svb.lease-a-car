#!/bin/sh

set -eu

export BROKER_PASSWORD="smoke-broker-password"
export EMPLOYEE_PASSWORD="smoke-employee-password"
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

# Separate Compose resources from a developer's running project.
export COMPOSE_PROJECT_NAME=lease-assessment-smoke
export LEASE_API_PORT=18080
export INTEREST_RATE_PORT=18081
export DASHBOARD_PORT=18082
cleanup() {
  result=$?
  trap - EXIT
  docker compose down -v --remove-orphans || result=1
  exit "$result"
}
trap cleanup EXIT

printf 'Commit: '
git rev-parse HEAD
printf 'Working tree: '
git status --porcelain
uname -sm
docker version --format 'Client {{.Client.Version}}; server {{.Server.Version}} {{.Server.Os}}/{{.Server.Arch}}'
docker compose version

docker compose build --no-cache
docker compose up -d
python3 tests/e2e/assessment-smoke.py
test "$(docker compose exec -T lease-api id -u)" -ne 0
test "$(docker compose exec -T interest-rate-service id -u)" -ne 0
docker compose exec -T lease-api printenv INTEREST_RATE_SERVICE_URL \
  | grep -qx 'http://interest-rate-service:8080'
printf 'PASS: complete assessment demo, persistence, and container recreation\n'
