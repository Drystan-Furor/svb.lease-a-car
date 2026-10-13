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

  employee_login_response="$(
    curl --silent --show-error \
      --header 'Content-Type: application/json' \
      --data "{\"username\":\"employee\",\"password\":\"${EMPLOYEE_PASSWORD}\"}" \
      http://localhost:8080/auth/login
  )"
  employee_token="$(
    printf '%s\n' "$employee_login_response" \
      | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p'
  )"
  test -n "$employee_token"

  vehicle_response="$(
    curl --fail --silent --show-error \
      --header "Authorization: Bearer ${employee_token}" \
      --header 'Content-Type: application/json' \
      --data '{"make":"Volvo","model":"XC40","version":"Recharge","numberOfDoors":5,"co2Emission":null,"grossPrice":70000,"nettPrice":63000}' \
      http://localhost:8080/vehicles
  )"
  vehicle_id="$(
    printf '%s\n' "$vehicle_response" \
      | sed -n 's/.*"id":"\([^"]*\)".*/\1/p'
  )"
  test -n "$vehicle_id"
  test "$(
    curl --silent --output /dev/null --write-out '%{http_code}' \
      --header "Authorization: Bearer ${access_token}" \
      "http://localhost:8080/vehicles/${vehicle_id}"
  )" = "200"
  test "$(
    curl --silent --output /dev/null --write-out '%{http_code}' \
      --request DELETE \
      --header "Authorization: Bearer ${access_token}" \
      "http://localhost:8080/vehicles/${vehicle_id}"
  )" = "403"

  quote_response="$(
    curl --fail --silent --show-error \
      --header "Authorization: Bearer ${access_token}" \
      --header 'Content-Type: application/json' \
      --data "{\"vehicleId\":\"${vehicle_id}\",\"mileage\":45000,\"duration\":60,\"startDate\":\"2019-08-15\"}" \
      http://localhost:8080/lease-quotes
  )"
  printf '%s\n' "$quote_response" | grep -q '"monthlyRate":231.95'
  printf '%s\n' "$quote_response" | grep -q '"interestRate":4.35'
  printf '%s\n' "$quote_response" | grep -q '"interestRateStartDate":"2019-08-15"'

  test "$(
    curl --silent --output /dev/null --write-out '%{http_code}' \
      --request DELETE \
      --header "Authorization: Bearer ${employee_token}" \
      "http://localhost:8080/vehicles/${vehicle_id}"
  )" = "204"

  effective_rate="$(
    curl --fail --silent --show-error \
      --header "Authorization: Bearer ${access_token}" \
      'http://localhost:8081/interest-rates/effective?date=2015-02-04'
  )"
  printf '%s\n' "$effective_rate" | grep -q '"interestRate":1.25'

  interest_rate_response="$(
    curl --fail --silent --show-error \
      --header "Authorization: Bearer ${employee_token}" \
      --header 'Content-Type: application/json' \
      --data '{"interestRate":-0.5,"startDate":"2022-01-01"}' \
      http://localhost:8081/interest-rates
  )"
  interest_rate_id="$(
    printf '%s\n' "$interest_rate_response" \
      | sed -n 's/.*"id":"\([^"]*\)".*/\1/p'
  )"
  test -n "$interest_rate_id"
  test "$(
    curl --silent --output /dev/null --write-out '%{http_code}' \
      --header "Authorization: Bearer ${access_token}" \
      "http://localhost:8081/interest-rates/${interest_rate_id}"
  )" = "200"
  test "$(
    curl --silent --output /dev/null --write-out '%{http_code}' \
      --request DELETE \
      --header "Authorization: Bearer ${access_token}" \
      "http://localhost:8081/interest-rates/${interest_rate_id}"
  )" = "403"
  test "$(
    curl --silent --output /dev/null --write-out '%{http_code}' \
      --request DELETE \
      --header "Authorization: Bearer ${employee_token}" \
      "http://localhost:8081/interest-rates/${interest_rate_id}"
  )" = "204"

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
