# Assessment testen met Docker en curl

Gecontroleerd op 2026-10-13: onderstaande stappen 1–11 uitgevoerd vanuit deze handleiding;
schone Dockerbuild, 50 tests en alle HTTP-controles geslaagd.

Volg stappen 1 t/m 11 in volgorde, in dezelfde Bash-sessie vanuit de projectroot.
Elk `test`-commando controleert de HTTP-status. Bij een afwijking stopt een uitgevoerd script;
in een interactieve terminal: ga niet verder na een fout. JSON-responses staan in `$RUN_DIR`.
Gebruik `jq` om die bestanden te inspecteren. Deze handleiding verandert alleen tijdelijke testdata.

Benodigd: Git, Docker met Compose v2.24.4+, Bash, curl, OpenSSL en jq. Geen lokale Java of Maven nodig.
Poorten 18080 en 18081 moeten vrij zijn. De handmatige demo gebruikt een eigen Compose-project
en een tijdelijke poortconfiguratie, zodat de normale projectomgeving op 8080/8081 kan blijven draaien.

## 1. Broncode en omgeving

Eenmalig ophalen als je nog geen checkout hebt:

```bash
git clone https://github.com/Drystan-Furor/svb.lease-a-car.git
cd svb.lease-a-car
```

Heb je al een checkout? Open de projectroot. Start daarna een aparte Bash-sessie.
De repository kan privé zijn; gebruik dan je eigen GitHub-toegang.

```bash
bash
```

```bash
set -euo pipefail

git rev-parse HEAD
git status --short
docker version
docker compose version
curl --version
jq --version
openssl version

export COMPOSE_PROJECT_NAME=lease-manual-assessment
export BROKER_USERNAME=broker
export EMPLOYEE_USERNAME=employee
export BROKER_PASSWORD=manual-broker-password
export EMPLOYEE_PASSWORD=manual-employee-password
export JWT_SECRET="$(openssl rand -hex 32)"
export BROKER_PASSWORD_HASH="$(docker run --rm httpd:2.4-alpine \
  htpasswd -bnBC 10 '' "$BROKER_PASSWORD" | tr -d ':\n')"
export EMPLOYEE_PASSWORD_HASH="$(docker run --rm httpd:2.4-alpine \
  htpasswd -bnBC 10 '' "$EMPLOYEE_PASSWORD" | tr -d ':\n')"
export JWT_ISSUER=lease-demo
export JWT_AUDIENCE=lease-platform

API=http://localhost:18080
RATES=http://localhost:18081
RUN_DIR="$(mktemp -d)"
cat > "$RUN_DIR/ports.yaml" <<'YAML'
services:
  lease-api:
    ports: !override
      - "127.0.0.1:18080:8080"
  interest-rate-service:
    ports: !override
      - "127.0.0.1:18081:8080"
YAML
export COMPOSE_FILE="$PWD/compose.yaml:$RUN_DIR/ports.yaml"
printf 'Tijdelijke responses: %s\n' "$RUN_DIR"
```

Wachtwoorden hierboven zijn uitsluitend voor deze lokale demo. Gebruik deze accounts niet voor
publieke hosting. `$RUN_DIR` bevat straks tokens; deel die bestanden niet.

## 2. Schoon bouwen en starten

De build draait alle Java-tests en Google Java Style-controles in Docker. Verwacht `BUILD SUCCESS`,
50 tests zonder fouten en nul Checkstyle-overtredingen. Een falende test moet de build stoppen.

```bash
docker compose down --remove-orphans
docker compose build --no-cache
docker compose up -d
docker compose ps

wait_api() {
  local base="$1"
  local attempt
  for attempt in $(seq 1 60); do
    if [ "$(curl --silent --output /dev/null --max-time 2 \
      --write-out '%{http_code}' "$base/" || true)" = 401 ]; then
      return 0
    fi
    sleep 1
  done
  printf 'API niet gereed: %s\n' "$base" >&2
  return 1
}
wait_api "$API"
wait_api "$RATES"

test "$(docker compose exec -T lease-api id -u)" -ne 0
test "$(docker compose exec -T interest-rate-service id -u)" -ne 0
test "$(docker compose exec -T lease-api printenv INTEREST_RATE_SERVICE_URL)" \
  = http://interest-rate-service:8080
```

## 3. Inloggen en startdata controleren

Beide logins geven `200`, `tokenType: Bearer` en `expiresIn: 900`. Tokens verlopen na 15 minuten.
Voer bij verlopen tokens deze twee logins opnieuw uit; behoud de eerder verkregen record-ID’s.

```bash
LOGIN_BODY="$(jq -nc --arg u "$BROKER_USERNAME" --arg p "$BROKER_PASSWORD" '{username:$u,password:$p}')"
test "$(curl --silent --show-error --max-time 15 \
  --request POST "$API/auth/login" \
  --header 'Content-Type: application/json' \
  --data "$LOGIN_BODY" \
  --output "$RUN_DIR/broker-login.json" --write-out '%{http_code}')" = 200
jq 'del(.accessToken)' "$RUN_DIR/broker-login.json"
BROKER_TOKEN="$(jq -er 'select(.tokenType == "Bearer" and .expiresIn == 900) | .accessToken' "$RUN_DIR/broker-login.json")"
```

```bash
LOGIN_BODY="$(jq -nc --arg u "$EMPLOYEE_USERNAME" --arg p "$EMPLOYEE_PASSWORD" '{username:$u,password:$p}')"
test "$(curl --silent --show-error --max-time 15 \
  --request POST "$API/auth/login" \
  --header 'Content-Type: application/json' \
  --data "$LOGIN_BODY" \
  --output "$RUN_DIR/employee-login.json" --write-out '%{http_code}')" = 200
jq 'del(.accessToken)' "$RUN_DIR/employee-login.json"
EMPLOYEE_TOKEN="$(jq -er 'select(.tokenType == "Bearer" and .expiresIn == 900) | .accessToken' "$RUN_DIR/employee-login.json")"
```

Bij verse containers: 0 klanten, 10.329 voertuigen en 20 rentetarieven. Lijsten zijn gepagineerd.

```bash
test "$(curl --silent --show-error --max-time 15 \
  --request GET "$API/customers?page=0&size=1" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 200
jq . "$RUN_DIR/response.json"
jq -e '.total == 0' "$RUN_DIR/response.json"
```

```bash
test "$(curl --silent --show-error --max-time 15 \
  --request GET "$API/vehicles?page=0&size=1" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 200
jq . "$RUN_DIR/response.json"
jq -e '.total == 10329' "$RUN_DIR/response.json"
```

```bash
test "$(curl --silent --show-error --max-time 15 \
  --request GET "$RATES/interest-rates?page=0&size=1" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 200
jq . "$RUN_DIR/response.json"
jq -e '.total == 20' "$RUN_DIR/response.json"
```

## 4. Klant aanmaken, lezen en wijzigen

Alle klantacties vereisen BROKER. Verwacht achtereenvolgens `201`, `200`, `200`.
`PUT` vervangt het volledige object.

```bash
CUSTOMER_BODY='{"name":"Ada Lovelace","street":"Analytical Engine Road","houseNumber":"12A","zipcode":"1011 AB","place":"Amsterdam","email":"ada@example.test","phoneNumber":"+31 6 12345678"}'
test "$(curl --silent --show-error --max-time 15 \
  --request POST "$API/customers" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --header 'Content-Type: application/json' \
  --data "$CUSTOMER_BODY" \
  --output "$RUN_DIR/customer.json" --write-out '%{http_code}')" = 201
jq . "$RUN_DIR/customer.json"
CUSTOMER_ID="$(jq -er .id "$RUN_DIR/customer.json")"
test "$(curl --silent --show-error --max-time 15 \
  --request GET "$API/customers/$CUSTOMER_ID" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 200
jq . "$RUN_DIR/response.json"
CUSTOMER_BODY="$(printf '%s' "$CUSTOMER_BODY" | jq -c '.name = "Ada Byron"')"
test "$(curl --silent --show-error --max-time 15 \
  --request PUT "$API/customers/$CUSTOMER_ID" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --header 'Content-Type: application/json' \
  --data "$CUSTOMER_BODY" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 200
jq . "$RUN_DIR/response.json"
jq -e '.name == "Ada Byron" and .houseNumber == "12A"' "$RUN_DIR/response.json"
```

## 5. Voertuig aanmaken, lezen en wijzigen

EMPLOYEE beheert voertuigen; BROKER mag lezen. Nettoprijs 63000 is nodig voor de quote hieronder.
Verwacht `201`, `200`, `200`.

```bash
VEHICLE_BODY='{"make":"Volvo","model":"XC40","version":"Recharge","numberOfDoors":5,"co2Emission":null,"grossPrice":70000,"nettPrice":63000}'
test "$(curl --silent --show-error --max-time 15 \
  --request POST "$API/vehicles" \
  --header "Authorization: Bearer $EMPLOYEE_TOKEN" \
  --header 'Content-Type: application/json' \
  --data "$VEHICLE_BODY" \
  --output "$RUN_DIR/vehicle.json" --write-out '%{http_code}')" = 201
jq . "$RUN_DIR/vehicle.json"
VEHICLE_ID="$(jq -er .id "$RUN_DIR/vehicle.json")"
test "$(curl --silent --show-error --max-time 15 \
  --request GET "$API/vehicles/$VEHICLE_ID" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 200
jq . "$RUN_DIR/response.json"
VEHICLE_BODY="$(printf '%s' "$VEHICLE_BODY" | jq -c '.version = "Demo"')"
test "$(curl --silent --show-error --max-time 15 \
  --request PUT "$API/vehicles/$VEHICLE_ID" \
  --header "Authorization: Bearer $EMPLOYEE_TOKEN" \
  --header 'Content-Type: application/json' \
  --data "$VEHICLE_BODY" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 200
jq . "$RUN_DIR/response.json"
jq -e '.version == "Demo" and .nettPrice == 63000' "$RUN_DIR/response.json"
```

## 6. Rente aanmaken, lezen, wijzigen en historisch selecteren

EMPLOYEE beheert rente op poort 18081; BROKER mag lezen. Verwacht `201` en daarna `200`.
Datum 2030-01-01 komt niet voor in de meegeleverde fixtures.

```bash
RATE_BODY='{"interestRate":4.4,"startDate":"2030-01-01"}'
test "$(curl --silent --show-error --max-time 15 \
  --request POST "$RATES/interest-rates" \
  --header "Authorization: Bearer $EMPLOYEE_TOKEN" \
  --header 'Content-Type: application/json' \
  --data "$RATE_BODY" \
  --output "$RUN_DIR/rate.json" --write-out '%{http_code}')" = 201
jq . "$RUN_DIR/rate.json"
RATE_ID="$(jq -er .id "$RUN_DIR/rate.json")"
test "$(curl --silent --show-error --max-time 15 \
  --request GET "$RATES/interest-rates/$RATE_ID" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 200
jq . "$RUN_DIR/response.json"
RATE_BODY='{"interestRate":4.5,"startDate":"2030-01-01"}'
test "$(curl --silent --show-error --max-time 15 \
  --request PUT "$RATES/interest-rates/$RATE_ID" \
  --header "Authorization: Bearer $EMPLOYEE_TOKEN" \
  --header 'Content-Type: application/json' \
  --data "$RATE_BODY" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 200
jq . "$RUN_DIR/response.json"
jq -e '.interestRate == 4.5' "$RUN_DIR/response.json"
test "$(curl --silent --show-error --max-time 15 \
  --request GET "$RATES/interest-rates/effective?date=2030-01-01" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 200
jq . "$RUN_DIR/response.json"
jq -e --arg id "$RATE_ID" '.id == $id and .interestRate == 4.5' "$RUN_DIR/response.json"
test "$(curl --silent --show-error --max-time 15 \
  --request GET "$RATES/interest-rates/effective?date=2015-02-05" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 200
jq . "$RUN_DIR/response.json"
jq -e '.interestRate == -0.75' "$RUN_DIR/response.json"
```

## 7. Quote via beide services

Verwacht `200`, `monthlyRate: 239.82`, `currency: EUR`, `nettPrice: 63000`, rente 4.5 en
rentedatum 2030-01-01. Lease-api haalt de rente via HTTP op bij interest-rate-service.
De letterlijke assessmentformule geeft 239.82, niet het in de opdracht genoemde 239.76.

```bash
QUOTE_BODY="$(jq -nc --arg id "$VEHICLE_ID" \
  '{vehicleId:$id,mileage:45000,duration:60,startDate:"2030-01-01"}')"
test "$(curl --silent --show-error --max-time 15 \
  --request POST "$API/lease-quotes" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --header 'Content-Type: application/json' \
  --data "$QUOTE_BODY" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 200
jq . "$RUN_DIR/response.json"
jq -e '.monthlyRate == 239.82 and .currency == "EUR" and .nettPrice == 63000
  and .interestRate == 4.5 and .interestRateStartDate == "2030-01-01"' "$RUN_DIR/response.json"
```

## 8. Foutscenario’s

Ontbrekend token en onjuiste credentials geven `401`. Een geldige identiteit met de verkeerde
rol geeft `403`. Geen van deze requests mag een record wijzigen.

```bash
test "$(curl --silent --show-error --max-time 15 \
  --request GET "$API/customers" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 401
jq . "$RUN_DIR/response.json"
test "$(curl --silent --show-error --max-time 15 \
  --request GET "$RATES/interest-rates" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 401
jq . "$RUN_DIR/response.json"
BAD_LOGIN='{"username":"broker","password":"wrong"}'
test "$(curl --silent --show-error --max-time 15 \
  --request POST "$API/auth/login" \
  --header 'Content-Type: application/json' \
  --data "$BAD_LOGIN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 401
jq . "$RUN_DIR/response.json"
test "$(curl --silent --show-error --max-time 15 \
  --request GET "$API/customers/$CUSTOMER_ID" \
  --header "Authorization: Bearer $EMPLOYEE_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 403
jq . "$RUN_DIR/response.json"
test "$(curl --silent --show-error --max-time 15 \
  --request POST "$API/vehicles" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --header 'Content-Type: application/json' \
  --data "$VEHICLE_BODY" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 403
jq . "$RUN_DIR/response.json"
test "$(curl --silent --show-error --max-time 15 \
  --request DELETE "$RATES/interest-rates/$RATE_ID" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 403
jq . "$RUN_DIR/response.json"
test "$(curl --silent --show-error --max-time 15 \
  --request POST "$API/lease-quotes" \
  --header "Authorization: Bearer $EMPLOYEE_TOKEN" \
  --header 'Content-Type: application/json' \
  --data "$QUOTE_BODY" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 403
jq . "$RUN_DIR/response.json"
```

Ongeldige invoer geeft `400`; een tweede rente met dezelfde ingangsdatum geeft `409`.

```bash
INVALID_CUSTOMER="$(printf '%s' "$CUSTOMER_BODY" | jq -c '.email = "invalid"')"
test "$(curl --silent --show-error --max-time 15 \
  --request POST "$API/customers" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --header 'Content-Type: application/json' \
  --data "$INVALID_CUSTOMER" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 400
jq . "$RUN_DIR/response.json"
test "$(curl --silent --show-error --max-time 15 \
  --request POST "$RATES/interest-rates" \
  --header "Authorization: Bearer $EMPLOYEE_TOKEN" \
  --header 'Content-Type: application/json' \
  --data "$RATE_BODY" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 409
jq . "$RUN_DIR/response.json"
```

Vóór de eerste rentedatum geeft de renteservice `404`; een quote op die datum geeft `422`.

```bash
test "$(curl --silent --show-error --max-time 15 \
  --request GET "$RATES/interest-rates/effective?date=2014-04-30" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 404
jq . "$RUN_DIR/response.json"
NO_RATE_QUOTE="$(printf '%s' "$QUOTE_BODY" | jq -c '.startDate = "2014-04-30"')"
test "$(curl --silent --show-error --max-time 15 \
  --request POST "$API/lease-quotes" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --header 'Content-Type: application/json' \
  --data "$NO_RATE_QUOTE" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 422
jq . "$RUN_DIR/response.json"
jq -e '.status == 422 and (has("monthlyRate") | not)' "$RUN_DIR/response.json"
```

## 9. Verwijderen en ontbrekende records

Iedere DELETE geeft `204` zonder responsebody; daarna geeft GET `404`.

```bash
test "$(curl --silent --show-error --max-time 15 \
  --request DELETE "$API/customers/$CUSTOMER_ID" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 204
test "$(curl --silent --show-error --max-time 15 \
  --request GET "$API/customers/$CUSTOMER_ID" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 404
jq . "$RUN_DIR/response.json"
```

```bash
test "$(curl --silent --show-error --max-time 15 \
  --request DELETE "$API/vehicles/$VEHICLE_ID" \
  --header "Authorization: Bearer $EMPLOYEE_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 204
test "$(curl --silent --show-error --max-time 15 \
  --request GET "$API/vehicles/$VEHICLE_ID" \
  --header "Authorization: Bearer $EMPLOYEE_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 404
jq . "$RUN_DIR/response.json"
```

```bash
test "$(curl --silent --show-error --max-time 15 \
  --request DELETE "$RATES/interest-rates/$RATE_ID" \
  --header "Authorization: Bearer $EMPLOYEE_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 204
test "$(curl --silent --show-error --max-time 15 \
  --request GET "$RATES/interest-rates/$RATE_ID" \
  --header "Authorization: Bearer $EMPLOYEE_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 404
jq . "$RUN_DIR/response.json"
```

## 10. Uitval renteservice en resetgedrag

Maak opnieuw een klant en voertuig aan. De oude ID’s zijn verwijderd. Gebruik de nieuwe voertuig-ID
voor de uitvaltest. Verwacht `201` voor beide nieuwe records.

```bash
test "$(curl --silent --show-error --max-time 15 \
  --request POST "$API/customers" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --header 'Content-Type: application/json' \
  --data "$CUSTOMER_BODY" \
  --output "$RUN_DIR/customer.json" --write-out '%{http_code}')" = 201
jq . "$RUN_DIR/customer.json"
CUSTOMER_ID="$(jq -er .id "$RUN_DIR/customer.json")"
test "$(curl --silent --show-error --max-time 15 \
  --request POST "$API/vehicles" \
  --header "Authorization: Bearer $EMPLOYEE_TOKEN" \
  --header 'Content-Type: application/json' \
  --data "$VEHICLE_BODY" \
  --output "$RUN_DIR/vehicle.json" --write-out '%{http_code}')" = 201
jq . "$RUN_DIR/vehicle.json"
VEHICLE_ID="$(jq -er .id "$RUN_DIR/vehicle.json")"
QUOTE_BODY="$(printf '%s' "$QUOTE_BODY" | jq -c --arg id "$VEHICLE_ID" '.vehicleId = $id')"
```

Stop alleen de renteservice. Quote geeft `503` met ProblemDetail en zonder `monthlyRate`.
Start de renteservice ook bij een mislukte controle weer met `docker compose start interest-rate-service`.

```bash
docker compose stop interest-rate-service
test "$(curl --silent --show-error --max-time 15 \
  --request POST "$API/lease-quotes" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --header 'Content-Type: application/json' \
  --data "$QUOTE_BODY" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 503
jq . "$RUN_DIR/response.json"
jq -e '.status == 503 and (has("monthlyRate") | not)' "$RUN_DIR/response.json"
docker compose start interest-rate-service
wait_api "$RATES"
```

Herstart beide services. Handmatig ingevoerde klant en voertuig verdwijnen. Fixtures worden
opnieuw geladen. Tokens blijven geldig tot hun vervaltijd, omdat het signing secret gelijk blijft.

```bash
docker compose restart
wait_api "$API"
wait_api "$RATES"
test "$(curl --silent --show-error --max-time 15 \
  --request GET "$API/customers/$CUSTOMER_ID" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 404
jq . "$RUN_DIR/response.json"
test "$(curl --silent --show-error --max-time 15 \
  --request GET "$API/vehicles/$VEHICLE_ID" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 404
jq . "$RUN_DIR/response.json"
```

```bash
test "$(curl --silent --show-error --max-time 15 \
  --request GET "$API/customers?page=0&size=1" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 200
jq . "$RUN_DIR/response.json"
jq -e '.total == 0' "$RUN_DIR/response.json"
```

```bash
test "$(curl --silent --show-error --max-time 15 \
  --request GET "$API/vehicles?page=0&size=1" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 200
jq . "$RUN_DIR/response.json"
jq -e '.total == 10329' "$RUN_DIR/response.json"
```

```bash
test "$(curl --silent --show-error --max-time 15 \
  --request GET "$RATES/interest-rates?page=0&size=1" \
  --header "Authorization: Bearer $BROKER_TOKEN" \
  --output "$RUN_DIR/response.json" --write-out '%{http_code}')" = 200
jq . "$RUN_DIR/response.json"
jq -e '.total == 20' "$RUN_DIR/response.json"
```

## 11. Opruimen

Voer dit ook uit na een afgebroken demo, vanuit dezelfde Bash-sessie. Bij een fout kun je eerst
`docker compose logs --tail=100` en de responsebestanden bekijken. Verwijder alleen de tijdelijke
map die deze handleiding heeft aangemaakt.

```bash
docker compose down --remove-orphans
rm -rf "$RUN_DIR"
unset BROKER_TOKEN EMPLOYEE_TOKEN JWT_SECRET BROKER_PASSWORD_HASH EMPLOYEE_PASSWORD_HASH
unset BROKER_PASSWORD EMPLOYEE_PASSWORD COMPOSE_FILE COMPOSE_PROJECT_NAME
printf 'PASS: handmatige assessmentworkflow afgerond\n'
exit
```

## Alternatief: automatische rooktest

Dit is een afzonderlijke route op poorten 8080/8081; die moeten hiervoor vrij zijn.
Voer dit uit in een nieuwe shell zonder de tijdelijke COMPOSE_FILE van de handmatige demo.
Benodigd naast Docker: Python 3, OpenSSL en POSIX shell. Het script bouwt zonder cache, maakt zelf
accounts aan, controleert de keten en ruimt containers automatisch op.

```bash
sh tests/e2e/docker-smoke.sh
```

Verwacht `PASS: complete assessment demo, including container recreation` en exitcode 0.

## Tweede omgeving: GitHub Actions

Gebruik de meegeleverde workflow **S10 assessment**. Met ingelogde GitHub CLI:

```bash
gh workflow run assessment.yml --ref master
gh run list --workflow assessment.yml --limit 5
# Vul het run-ID uit de vorige uitvoer in:
read -r -p 'Run-ID: ' ASSESSMENT_RUN_ID
gh run watch "$ASSESSMENT_RUN_ID" --exit-status
gh run download "$ASSESSMENT_RUN_ID" --dir /tmp/lease-assessment-evidence
```

Verwacht een geslaagde run met artifact `assessment-evidence-<commit>`.
Controleer de commit in het log, Docker/Compose-versies, host/CPU, 50 geslaagde tests,
nul stijlmeldingen en de volledige functionele demo. De workflow gebruikt geen host-Java/Maven.
Zie [README](README.md#assessment-handover-and-evidence) voor het vastgelegde S10-bewijs.

De oorspronkelijke eisen staan in [assessment.md](assesment/docs/assessment.md).
Swagger en blijvende opslag zijn bonusonderdelen en maken geen deel uit van deze testworkflow.
