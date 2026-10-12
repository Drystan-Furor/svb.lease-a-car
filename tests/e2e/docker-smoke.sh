#!/bin/sh

set -eu

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
}

docker compose build --no-cache
docker compose up -d
assert_services_ready
docker compose down
docker compose up -d
assert_services_ready
