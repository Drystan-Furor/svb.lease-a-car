# API testen op de draaiende Dockercontainers

Gecontroleerd op 2026-10-13: 47 requests uit stappen 1–8 geslaagd op de README-containers.

Startpunt: beide containers draaien volgens [README](../../README.md). Open een Bash-terminal
in de projectroot. Nodig: `curl` en `jq`. Kopieer onderstaande blokken in volgorde in dezelfde
terminal. Geen build, nieuwe containers, hashes of tijdelijke bestanden nodig.

`curl -i` toont de HTTP-status en response. `curl -fsS` wordt alleen gebruikt om een token of ID
op te slaan; bij een HTTP-fout stopt die opdracht met een melding. Gebruik dan geen lege ID.
De voorbeelden maken eigen testrecords aan en verwijderen alleen die records aan het einde.

## 1. Inloggen

De wachtwoorden komen uit de Dockerstappen in README. Heb je andere ingesteld, vervang ze hier.
Beide logins horen `200` te geven. Tokens zijn 15 minuten geldig; herhaal deze stap bij verlopen tokens.

```bash
BROKER_TOKEN=$(curl -fsS http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"broker","password":"broker-password"}' | jq -er '.accessToken')

EMPLOYEE_TOKEN=$(curl -fsS http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"employee","password":"employee-password"}' | jq -er '.accessToken')
```

BROKER beheert klanten, leest voertuigen/rente en vraagt quotes aan.
EMPLOYEE leest en beheert voertuigen/rente. Beide services controleren hetzelfde token.

## 2. Klanten: lijst, toevoegen, ophalen, wijzigen

Lijst — `200`. `page` begint bij 0; `size` bepaalt het aantal resultaten.

```bash
curl -i 'http://localhost:8080/customers?page=0&size=5' -H "Authorization: Bearer $BROKER_TOKEN"
```

Toevoegen — `201`. De ID wordt automatisch opgeslagen.

```bash
CUSTOMER_ID=$(curl -fsS http://localhost:8080/customers \
  -H "Authorization: Bearer $BROKER_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Testklant","street":"Teststraat","houseNumber":"1","zipcode":"1234 AB","place":"Utrecht","email":"test@example.test","phoneNumber":"0612345678"}' | jq -er '.id')
echo "$CUSTOMER_ID"
```

Ophalen — `200` met de zojuist gemaakte klant.

```bash
curl -i "http://localhost:8080/customers/$CUSTOMER_ID" -H "Authorization: Bearer $BROKER_TOKEN"
```

Wijzigen — `200`, naam wordt `Gewijzigde testklant`. PUT vereist het volledige object.

```bash
curl -i -X PUT "http://localhost:8080/customers/$CUSTOMER_ID" \
  -H "Authorization: Bearer $BROKER_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Gewijzigde testklant","street":"Teststraat","houseNumber":"2","zipcode":"1234 AB","place":"Utrecht","email":"test@example.test","phoneNumber":"0612345678"}'
```

## 3. Voertuigen: lijst, toevoegen, ophalen, wijzigen

Lijst — `200`, met `items`, `page`, `size` en `total`.
Een verse service bevat 10.329 voertuigen; eerdere handmatige wijzigingen veranderen dat aantal.

```bash
curl -i 'http://localhost:8080/vehicles?page=0&size=5' -H "Authorization: Bearer $BROKER_TOKEN"
```

Toevoegen — `201`. `co2Emission: null` is toegestaan.

```bash
VEHICLE_ID=$(curl -fsS http://localhost:8080/vehicles \
  -H "Authorization: Bearer $EMPLOYEE_TOKEN" -H 'Content-Type: application/json' \
  -d '{"make":"Volvo","model":"XC40","version":"Test","numberOfDoors":5,"co2Emission":null,"grossPrice":70000,"nettPrice":63000}' | jq -er '.id')
echo "$VEHICLE_ID"
```

Ophalen — `200`. Beide rollen mogen dit.

```bash
curl -i "http://localhost:8080/vehicles/$VEHICLE_ID" -H "Authorization: Bearer $BROKER_TOKEN"
curl -i "http://localhost:8080/vehicles/$VEHICLE_ID" -H "Authorization: Bearer $EMPLOYEE_TOKEN"
```

Wijzigen — `200`, versie wordt `Gewijzigd`.

```bash
curl -i -X PUT "http://localhost:8080/vehicles/$VEHICLE_ID" \
  -H "Authorization: Bearer $EMPLOYEE_TOKEN" -H 'Content-Type: application/json' \
  -d '{"make":"Volvo","model":"XC40","version":"Gewijzigd","numberOfDoors":5,"co2Emission":0,"grossPrice":70000,"nettPrice":63000}'
```

## 4. Rente: lijst, toevoegen, ophalen, wijzigen, geldige datum

Lijst — `200`. Een verse service bevat 20 tarieven.

```bash
curl -i 'http://localhost:8081/interest-rates?page=0&size=5' -H "Authorization: Bearer $BROKER_TOKEN"
```

Toevoegen — `201`. Gebruik een vrije ingangsdatum. Krijg je `409`, kies een andere datum in
`RATE_DATE` en herhaal alleen dit blok; verwijder geen bestaand tarief.

```bash
RATE_DATE=2099-01-01
RATE_ID=$(curl -fsS http://localhost:8081/interest-rates \
  -H "Authorization: Bearer $EMPLOYEE_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"interestRate\":4.4,\"startDate\":\"$RATE_DATE\"}" | jq -er '.id')
echo "$RATE_ID"
```

Ophalen — `200`. Beide rollen mogen dit.

```bash
curl -i "http://localhost:8081/interest-rates/$RATE_ID" -H "Authorization: Bearer $BROKER_TOKEN"
curl -i "http://localhost:8081/interest-rates/$RATE_ID" -H "Authorization: Bearer $EMPLOYEE_TOKEN"
```

Wijzigen — `200`, rente wordt `4.5`.

```bash
curl -i -X PUT "http://localhost:8081/interest-rates/$RATE_ID" \
  -H "Authorization: Bearer $EMPLOYEE_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"interestRate\":4.5,\"startDate\":\"$RATE_DATE\"}"
```

Geldige rente op een datum — `200`. Het eerste verzoek geeft jouw tarief van `4.5`.
Het tweede toont de historische fixture van `-0.75`, als die niet eerder is gewijzigd.
De service kiest het laatst ingegane tarief op of vóór de datum.

```bash
curl -i "http://localhost:8081/interest-rates/effective?date=$RATE_DATE" -H "Authorization: Bearer $BROKER_TOKEN"
curl -i 'http://localhost:8081/interest-rates/effective?date=2015-02-05' -H "Authorization: Bearer $BROKER_TOKEN"
```

## 5. Leasequote: beide services samen

`200`, `monthlyRate: 239.82`, `currency: EUR`, `interestRate: 4.5` en `nettPrice: 63000`.
De API haalt prijs en rente zelf op. De letterlijke opdrachtformule geeft €239,82.
Er is geen apart openbaar calculator-endpoint; berekenen loopt via `/lease-quotes`.

```bash
QUOTE="{\"vehicleId\":\"$VEHICLE_ID\",\"mileage\":45000,\"duration\":60,\"startDate\":\"$RATE_DATE\"}"
curl -i http://localhost:8080/lease-quotes \
  -H "Authorization: Bearer $BROKER_TOKEN" -H 'Content-Type: application/json' -d "$QUOTE"
```

## 6. Toegangscontrole

Geen token, ongeldig token of verkeerd wachtwoord — steeds `401`.

```bash
curl -i http://localhost:8080/customers
curl -i http://localhost:8081/interest-rates
curl -i http://localhost:8080/vehicles -H 'Authorization: Bearer ongeldig'
curl -i http://localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"broker","password":"verkeerd"}'
```

Verkeerde rol — steeds `403`: EMPLOYEE mag geen klanten/quotes, BROKER mag geen voertuigen/rente wijzigen.

```bash
curl -i http://localhost:8080/customers -H "Authorization: Bearer $EMPLOYEE_TOKEN"
curl -i -X DELETE "http://localhost:8080/vehicles/$VEHICLE_ID" -H "Authorization: Bearer $BROKER_TOKEN"
curl -i -X DELETE "http://localhost:8081/interest-rates/$RATE_ID" -H "Authorization: Bearer $BROKER_TOKEN"
curl -i http://localhost:8080/lease-quotes \
  -H "Authorization: Bearer $EMPLOYEE_TOKEN" -H 'Content-Type: application/json' -d "$QUOTE"
```

## 7. Validatie en foutafhandeling

Ontbrekende velden bij klant, voertuig, rente en quote — steeds `400`.

```bash
curl -i http://localhost:8080/customers -H "Authorization: Bearer $BROKER_TOKEN" -H 'Content-Type: application/json' -d '{}'
curl -i http://localhost:8080/vehicles -H "Authorization: Bearer $EMPLOYEE_TOKEN" -H 'Content-Type: application/json' -d '{}'
curl -i http://localhost:8081/interest-rates -H "Authorization: Bearer $EMPLOYEE_TOKEN" -H 'Content-Type: application/json' -d '{}'
curl -i http://localhost:8080/lease-quotes -H "Authorization: Bearer $BROKER_TOKEN" -H 'Content-Type: application/json' -d '{}'
```

Ongeldig e-mailadres, nul kilometers, kapotte JSON, ongeldige UUID/datum/paginering — steeds `400`.

```bash
curl -i http://localhost:8080/customers \
  -H "Authorization: Bearer $BROKER_TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Test","street":"Teststraat","houseNumber":"1","zipcode":"1234 AB","place":"Utrecht","email":"ongeldig","phoneNumber":"0612345678"}'
curl -i http://localhost:8080/lease-quotes \
  -H "Authorization: Bearer $BROKER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"vehicleId\":\"$VEHICLE_ID\",\"mileage\":0,\"duration\":60,\"startDate\":\"$RATE_DATE\"}"
curl -i http://localhost:8080/customers -H "Authorization: Bearer $BROKER_TOKEN" -H 'Content-Type: application/json' -d '{'
curl -i http://localhost:8080/vehicles/geen-uuid -H "Authorization: Bearer $BROKER_TOKEN"
curl -i 'http://localhost:8081/interest-rates/effective?date=ongeldig' -H "Authorization: Bearer $BROKER_TOKEN"
curl -i 'http://localhost:8080/customers?page=-1&size=5' -H "Authorization: Bearer $BROKER_TOKEN"
```

Dubbele rentedatum — `409`.

```bash
curl -i http://localhost:8081/interest-rates \
  -H "Authorization: Bearer $EMPLOYEE_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"interestRate\":4.5,\"startDate\":\"$RATE_DATE\"}"
```

Geen rente vóór de eerste fixture — rente geeft `404`, quote geeft `422` zonder maandbedrag.
Dit veronderstelt dat je geen eigen tarief op of vóór 2014-04-30 hebt toegevoegd.

```bash
curl -i 'http://localhost:8081/interest-rates/effective?date=2014-04-30' -H "Authorization: Bearer $BROKER_TOKEN"
curl -i http://localhost:8080/lease-quotes \
  -H "Authorization: Bearer $BROKER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"vehicleId\":\"$VEHICLE_ID\",\"mileage\":45000,\"duration\":60,\"startDate\":\"2014-04-30\"}"
```

## 8. Testrecords verwijderen

DELETE geeft `204` zonder body. De daaropvolgende GET geeft `404`.
Voer dit pas uit nadat je bovenstaande tests hebt gedaan.

```bash
curl -i -X DELETE "http://localhost:8080/customers/$CUSTOMER_ID" -H "Authorization: Bearer $BROKER_TOKEN"
curl -i "http://localhost:8080/customers/$CUSTOMER_ID" -H "Authorization: Bearer $BROKER_TOKEN"

curl -i -X DELETE "http://localhost:8080/vehicles/$VEHICLE_ID" -H "Authorization: Bearer $EMPLOYEE_TOKEN"
curl -i "http://localhost:8080/vehicles/$VEHICLE_ID" -H "Authorization: Bearer $BROKER_TOKEN"

curl -i -X DELETE "http://localhost:8081/interest-rates/$RATE_ID" -H "Authorization: Bearer $EMPLOYEE_TOKEN"
curl -i "http://localhost:8081/interest-rates/$RATE_ID" -H "Authorization: Bearer $BROKER_TOKEN"
```

Quote voor het verwijderde voertuig — `404`.

```bash
curl -i http://localhost:8080/lease-quotes \
  -H "Authorization: Bearer $BROKER_TOKEN" -H 'Content-Type: application/json' -d "$QUOTE"
```

De containers blijven draaien. Klaar voor de gewone API-test.

## Optioneel: uitval renteservice — 503

Deze test onderbreekt de renteservice en wist bij starten alle handmatig ingevoerde rente.
Voer de Dockercommando’s uit in de README-terminal met de geëxporteerde Compose-variabelen.
Gebruik een bestaand voertuig uit de lijst; jouw testvoertuig is hierboven verwijderd.

```bash
LIVE_VEHICLE_ID=$(curl -fsS 'http://localhost:8080/vehicles?page=0&size=1' \
  -H "Authorization: Bearer $BROKER_TOKEN" | jq -er '.items[0].id')

docker compose stop interest-rate-service

curl -i http://localhost:8080/lease-quotes \
  -H "Authorization: Bearer $BROKER_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"vehicleId\":\"$LIVE_VEHICLE_ID\",\"mileage\":45000,\"duration\":60,\"startDate\":\"2020-01-01\"}"

docker compose start interest-rate-service
```

Verwacht `503` zonder maandbedrag. Start de service ook weer als de curl-opdracht mislukt.
Wacht na starten tot de servicelog `Started` meldt; daarna moeten normale requests weer werken.

## Optioneel: herstart en geheugenopslag

Dit wist alle handmatige wijzigingen in beide services. Daarna: 0 klanten, 10.329 voertuigen,
20 rentetarieven uit CSV. Maak eventueel vooraf via stap 2 een klant en noteer diens ID;
na herstart geeft GET op die ID `404`. Oude tokens blijven geldig tot hun vervaltijd.

```bash
docker compose restart
docker compose logs --tail=20 lease-api interest-rate-service
```

Wacht op `Started`, herhaal zo nodig de login en voer de lijstrequests uit stappen 2–4 opnieuw uit.

Alle 18 publieke methode/pad-combinaties komen hierboven aan bod. Het API-contract staat in
[reference.md](reference.md); aanvullende geautomatiseerde tests staan in [README](../../README.md#build--test).
