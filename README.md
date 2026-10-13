# Lease-a-car API

Lease-a-car is a Java 21 / Spring Boot 4 two-service leasing API. It separates lease operations
from interest-rate management. Both services validate the caller's identity on every request.

## Current scope

Two independently buildable services exist:

- `lease-api` owns login, vehicle management, lease calculations, and quotes. It will own
  customers.
- `interest-rate-service` will own dated interest rates.

Implemented behavior is stateless JWT login, in-memory customer and vehicle CRUD, historical
interest-rate CRUD, and broker quotes that call the interest-rate service. The calculator has no
standalone HTTP endpoint.

## Authentication API

`lease-api` exposes `POST /auth/login`. Supply one of the two configured demo accounts:

```http
POST /auth/login
Content-Type: application/json

{"username":"broker","password":"<your locally chosen broker password>"}
```

A successful login returns:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

Bad credentials return `401`. Send the token on every later request:

```http
Authorization: Bearer <jwt>
```

Tokens use HS256, issuer `lease-demo`, audience `lease-platform`, and expire after 15 minutes.
Both services independently verify signature, issuer, audience, expiry, and not-before. Missing
or invalid tokens return `401`; a valid identity without the required role returns `403`. No
server-side login session is created.

| Role | Allowed operations |
| --- | --- |
| `BROKER` | Manage customers, request quotes, read vehicles and interest rates |
| `EMPLOYEE` | Manage vehicles and interest rates |

Only login is anonymous. These demo credentials suit local assessment use, not production
identity management. Use HTTPS before exposing either API outside a trusted local environment.

## Customer API

Customer routes require a bearer token. Only `BROKER` may create, read, fully replace, or delete
customers.

| Method | Route | Result |
| --- | --- | --- |
| `POST` | `/customers` | `201` with customer body and `Location` header |
| `GET` | `/customers/{id}` | `200`, or `404` for an unknown UUID |
| `GET` | `/customers?page=0&size=50` | Bounded page with `items`, `page`, `size`, and `total` |
| `PUT` | `/customers/{id}` | Full replacement with `200`, or `404` |
| `DELETE` | `/customers/{id}` | `204`, or `404` |

Create or replace request:

```json
{
  "name": "Ada Lovelace",
  "street": "Analytical Engine Road",
  "houseNumber": "12A",
  "zipcode": "1011 AB",
  "place": "Amsterdam",
  "email": "ada@example.test",
  "phoneNumber": "+31 6 12345678"
}
```

All fields are required strings; `email` must be syntactically valid. House-number letters and
phone-number spaces or plus signs are preserved. Invalid input returns `400` as ProblemDetail;
there is no email-uniqueness or country-specific address/telephone formatting rule. List defaults
are page `0`, size `50`; maximum size is `100`; results sort by UUID. Customers are in memory and
are cleared on restart.

## Vehicle API

Vehicle routes require a bearer token. `BROKER` and `EMPLOYEE` may read; only `EMPLOYEE` may
create, fully replace, or delete vehicles.

| Method | Route | Result |
| --- | --- | --- |
| `POST` | `/vehicles` | `201` with vehicle body and `Location` header |
| `GET` | `/vehicles/{id}` | `200`, or `404` for an unknown UUID |
| `GET` | `/vehicles?page=0&size=50` | Bounded page with `items`, `page`, `size`, and `total` |
| `PUT` | `/vehicles/{id}` | Full replacement with `200`, or `404` |
| `DELETE` | `/vehicles/{id}` | `204`, or `404` |

Create or replace request:

```json
{
  "make": "Volvo",
  "model": "XC40",
  "version": "Recharge",
  "numberOfDoors": 5,
  "co2Emission": null,
  "grossPrice": 52000.00,
  "nettPrice": 43000.00
}
```

`make`, `model`, and `version` must not be blank. Doors and both prices must be positive. CO₂ is
optional; when supplied it is measured in g/km and cannot be negative. Invalid input returns
`400` as ProblemDetail. List defaults are page `0`, size `50`; maximum size is `100`. Results are
sorted by UUID. Identical versions remain separate records with separate UUIDs.

Vehicles are held in memory. Every application or container restart reloads all 10,329 supplied
vehicle rows, including duplicate versions; manually entered vehicles are cleared. The packaged
vehicle CSV uses Windows-1252, semicolons, quoted values, and decimal commas. Its source `hp`
column is deliberately not mapped to CO₂.

## Interest-rate API

Interest-rate routes run on `interest-rate-service` and require a bearer token. `BROKER` and
`EMPLOYEE` may read. Only `EMPLOYEE` may create, fully replace, or delete rates.

| Method | Route | Result |
| --- | --- | --- |
| `POST` | `/interest-rates` | `201` with rate body and `Location` header |
| `GET` | `/interest-rates/{id}` | `200`, or `404` for an unknown UUID |
| `GET` | `/interest-rates?page=0&size=50` | Bounded page with `items`, `page`, `size`, and `total` |
| `GET` | `/interest-rates/effective?date=YYYY-MM-DD` | Rate whose `startDate` is latest on or before `date` |
| `PUT` | `/interest-rates/{id}` | Full replacement with `200`, or `404` |
| `DELETE` | `/interest-rates/{id}` | `204`, or `404` |

Create or replace request:

```json
{
  "interestRate": -0.75,
  "startDate": "2015-02-05"
}
```

`interestRate` and ISO `startDate` are required. Zero and negative rates are valid. Exactly one
rate may use each `startDate`; a conflicting `POST` or `PUT` returns `409` and changes nothing.
List defaults are page `0`, size `50`; maximum size is `100`. Results sort by UUID.

Each startup reloads the 20 supplied historical rates into memory. The packaged rate CSV uses
UTF-8, semicolons, decimal commas, and strict `d-M-uuuu` dates. For example, effective dates
`2015-02-04`, `2015-02-05`, and `2015-02-19` return 1.25, -0.75, and 1 percent. Dates before
`2014-05-01` return `404`; `4.35` percent applies from `2019-08-15` until a newer rate is added.
Manual changes disappear at restart.

## Lease-quote API

`BROKER` may request a quote from `lease-api`. `EMPLOYEE` receives `403`. The request carries
only contract input. Lease-api reads the vehicle's net price itself and forwards the same bearer
token to `interest-rate-service` to select historical interest.

```http
POST /lease-quotes
Authorization: Bearer <broker token>
Content-Type: application/json

{
  "vehicleId": "123e4567-e89b-12d3-a456-426614174000",
  "mileage": 45000,
  "duration": 60,
  "startDate": "2019-08-15"
}
```

Successful response contains the supplied contract input, selected rate and rate date, source net
price, calculated `monthlyRate`, and `currency` `EUR`. A vehicle with net price 63000, 45000
annual kilometres, 60 months, and start date 2019-08-15 returns 231.95 EUR at 4.35 percent.

`vehicleId`, positive `mileage`, positive `duration`, and ISO `startDate` are required. An unknown
vehicle returns `404`; invalid input returns `400`. No historical rate maps to `422`. An unreachable
or timed-out interest-rate service returns `503`. A rejected token or malformed downstream response
returns `502`. Lease-api never retries, caches, falls back to another rate, or accepts a client
price or interest-rate override.

## Lease-rate calculation

The calculator follows the literal assessment formula:

```text
(((annualMileage / 12) * durationMonths) / nettPrice)
  + (((interestRate / 100) * nettPrice) / 12)
```

It uses `BigDecimal` with DECIMAL128 precision and rounds only the final EUR amount to two
decimal places using `HALF_UP`.

| Input | Example value |
| --- | ---: |
| Annual mileage | 45,000 km |
| Contract duration | 60 months |
| Net vehicle price | EUR 63,000 |
| Annual interest rate | 4.5% |
| Monthly rate | EUR 239.82 |

The assessment example says EUR 239.76, but literal formula result is EUR 239.821428..., which
rounds to EUR 239.82. Annual mileage, duration, and net price must be positive. Interest may be
negative.

## Requirements

- Docker Desktop or another Docker engine for reproducible verification.
- Optional for local development: JDK 21 or newer.

Maven Wrapper is committed. Installing Maven is not required.

## Build and test

Run the full, reproducible Java 21 build without a local JDK or Maven installation:

```sh
docker build --target verify .
```

The `verify` stage builds both JARs, runs all tests, and checks Google Java Style. A failing test
or style violation fails the Docker build.

For local development with a JDK:

```sh
./mvnw verify
```

## Run with Docker Compose

Compose requires one shared JWT secret and both demo accounts through environment variables.
No working secret, password, or password hash is stored in Git. Generate local values:

```sh
export JWT_SECRET="$(openssl rand -base64 48)"
export BROKER_USERNAME=broker
export EMPLOYEE_USERNAME=employee
```

Generate each BCrypt hash without placing its password on the command line. The command prompts
twice and prints `<username>:<hash>`; copy only the hash after the colon:

```sh
docker run --rm -it httpd:2.4-alpine htpasswd -nBC 12 broker
docker run --rm -it httpd:2.4-alpine htpasswd -nBC 12 employee
```

Export the two generated hashes. Single quotes preserve the `$` characters:

```sh
export BROKER_PASSWORD_HASH='<paste broker BCrypt hash>'
export EMPLOYEE_PASSWORD_HASH='<paste employee BCrypt hash>'
```

Compose refuses to build or start when one of these five variables is absent. The application
also stops on a secret shorter than 32 UTF-8 bytes, malformed BCrypt hashes, blank usernames, or
duplicate usernames. Optional `JWT_ISSUER` and `JWT_AUDIENCE` variables override the documented
defaults, but both containers must receive matching values.

Build both images from source without a local JDK or Maven installation:

```sh
docker compose build --no-cache
```

Start both containers:

```sh
docker compose up --build -d
docker compose ps
```

`lease-api` is available on host port `8080`; `interest-rate-service` is available on host port
`8081`. Inside Compose, `lease-api` receives
`INTEREST_RATE_SERVICE_URL=http://interest-rate-service:8080`. This uses the Compose service
name and container port—not `localhost` or host port `8081`.

Log in after startup:

```sh
curl --request POST http://localhost:8080/auth/login \
  --header 'Content-Type: application/json' \
  --data '{"username":"broker","password":"<your broker password>"}'
```

Stop services and remove their temporary containers:

```sh
docker compose down
```

Runtime images use a pinned Java 21 JRE base image, contain only their verified application JAR,
and run as a non-root user. The Docker build was tested on Docker Desktop using its default
supported architecture; no platform is forced.

Run the automated start/stop smoke check with:

```sh
sh tests/e2e/docker-smoke.sh
```

## Run services locally

To run without Docker, export the same required variables described above. Start each service in
its own terminal with a local JDK; both terminals must inherit the same `JWT_SECRET`:

```sh
./mvnw -pl lease-api spring-boot:run
```

```sh
./mvnw -pl interest-rate-service spring-boot:run \
  -Dspring-boot.run.arguments="--server.port=8081"
```

`lease-api` listens on port `8080`; this local `interest-rate-service` command listens on port
`8081`. Stop either service with `Ctrl+C`. Missing or invalid security configuration stops
startup instead of falling back to a default credential.

`POST /auth/login`, vehicle CRUD, interest-rate CRUD, and broker quotes are available. Customer
management remains planned.

## Project layout

```text
lease-api/                 Login, vehicles, quotes, and lease calculator
interest-rate-service/     Historical interest-rate service module
Dockerfile                 Java 21 verification build stage
assesment/docs/            Assessment, implementation plan, decisions
```

See [assessment](assesment/docs/assessment.md) for assignment requirements and
[implementation decisions](assesment/docs/choices.md) for planned REST contracts and security.
