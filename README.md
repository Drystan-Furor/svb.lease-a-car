# Lease-a-car API

Lease-a-car is a Java 21 / Spring Boot 4 two-service leasing API. It separates lease operations
from interest-rate management. Both services validate the caller's identity on every request.

## Current scope

Two independently buildable services exist:

- `lease-api` owns login, customers, vehicle management, lease calculations, and quotes.
- `interest-rate-service` owns dated interest rates.

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

- Docker Desktop or another Docker engine with Docker Compose v2 or newer.
- Git to obtain the source; OpenSSL for generating the shared secret.
- For the automated assessment demo: Python 3, POSIX shell, and free ports 8080 and 8081.
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
The `dev` profile below has intentionally insecure local-only values; Compose does not use them.
Generate Compose values:

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

Run the complete assessment demo from the repository root with:

```sh
sh tests/e2e/docker-smoke.sh
```

The script builds with `--no-cache`, generates temporary credentials, starts both services,
and runs [assessment-smoke.py](tests/e2e/assessment-smoke.py). It checks supplied fixture counts,
both logins, customer/vehicle/rate create-read-update-delete cycles, historical interest, and a
EUR 239.82 quote at 4.5%. It also checks invalid credentials (`401`), wrong roles (`403`), and a
stopped interest service (`503` ProblemDetail without `monthlyRate`). It recreates the containers
and repeats the demo to check reset behavior. Each successful HTTP assertion prints its status;
any mismatch exits nonzero. Tokens and passwords are not printed.

Compose project `lease-assessment-smoke` isolates the demo's containers and network. Ports still
belong to the host: stop any services using 8080/8081 first. The exit handler removes this demo's
containers and network, including after a failed check. Do not run concurrent copies of the demo
against the same Docker engine. No host JDK, Maven, or prebuilt JAR is used.

## Assessment handover and evidence

Source remote: [Drystan-Furor/svb.lease-a-car](https://github.com/Drystan-Furor/svb.lease-a-car).
An assessor with repository access can obtain a clean copy:

```sh
git clone https://github.com/Drystan-Furor/svb.lease-a-car.git
cd svb.lease-a-car
git checkout 37fb9a00e4b8bfd66edcab3df120cedd18202610
sh tests/e2e/docker-smoke.sh
```

The checkout above selects the delivery SHA verified on both environments. The script prints that
SHA, working-tree changes, host OS/CPU, Docker client/server versions, Compose version, build
results, and functional assertions. A delivery run must start with an empty working-tree status.
The Dockerfile copies only committed source inputs and performs Maven `verify` inside Java 21.
Both module builds run unit/integration tests and Google Java Style checks before producing JARs.
Javadoc describes public request/response contracts, validation, seed import rules, historical
selection, and the calculator's precision and rounding.

### Second environment

S10 passed on 2026-10-13. The same delivery commit passed on the local Docker host and a fresh
GitHub-hosted runner. The user confirmed they are the assessor; their repository ADMIN access was
verified. The repository remains private; the owner will make it public themselves.
The manually dispatched [S10 assessment workflow](.github/workflows/assessment.yml) provides a
fresh Ubuntu 24.04 runner and runs the exact command above. Although the runner may have Java
installed, the build uses only the Docker toolchain and no local JARs or host Java/Maven commands.

After pushing the delivery commit, select **Actions → S10 assessment → Run workflow** on its
branch. Download `assessment-evidence-<commit>` from that run. Keep the run URL and evidence with
the delivery; artifact retention follows repository settings. Selected output is also committed below so the
evidence remains available after the artifact expires.

| Evidence | Status |
| --- | --- |
| Local Docker demo | 2026-10-13: Darwin arm64, Docker Desktop 4.94.0, Engine 29.8.2 linux/arm64, Compose 5.5.1; 50 tests, zero style violations, CRUD/quote/401/403/503/reset passed |
| Verified delivery commit | `37fb9a00e4b8bfd66edcab3df120cedd18202610`; clean worktree on both hosts. Subsequent commits record evidence only. |
| Local evidence | [Recorded output](tests/evidence/s10-local.log) |
| Second-host OS/CPU and Docker/Compose versions | Ubuntu 24.04, Linux x86_64; Docker client/server 28.0.4 linux/amd64; Compose 2.38.2 |
| Second-host build/tests/style and CRUD/quote/401/403/503/reset | Passed: 50 tests, zero failures/errors/skips, zero style violations; full demo passed twice across container recreation. [Successful run](https://github.com/Drystan-Furor/svb.lease-a-car/actions/runs/38394349519) and [recorded output](tests/evidence/s10-github.log) |
| Remote publication and assessor access | Delivery commit pushed to origin and checked out by Actions. User confirmed they are the assessor; their ADMIN access verified. Public visibility will be changed separately by the owner. |

### Known limits

Storage is temporary. Restarting either service discards its manual changes and restores its
packaged fixtures; customers restart empty. Vehicle and rate IDs must be obtained from API
responses, not copied between runs. Quote results are not persisted. Authentication uses two
configured demo accounts and a shared signing secret; there is no account administration,
refresh-token or revocation flow. Swagger and durable storage are separate bonus stories.
The literal formula yields EUR 239.82 for the assignment inputs, as documented above.

## Run services locally

Activate Spring profile `dev` to run without Docker or environment variables. It supplies a shared
local JWT secret and two accounts: `broker` / `broker-password` and `employee` /
`employee-password`. These credentials exist only for local development and must never be used
outside it.

In IntelliJ, add `dev` under **Active profiles** in each Spring Boot run configuration. From a
terminal, start each service in its own terminal:

```sh
./mvnw -pl lease-api spring-boot:run -Dspring-boot.run.profiles=dev
```

```sh
./mvnw -pl interest-rate-service spring-boot:run \
  -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments="--server.port=8081"
```

`lease-api` listens on port `8080`; this local `interest-rate-service` command listens on port
`8081`. Stop either service with `Ctrl+C`. Missing or invalid security configuration stops
startup when profile `dev` is inactive instead of falling back to a default credential.

`POST /auth/login`, customer and vehicle CRUD, interest-rate CRUD, and broker quotes are
available.

## Project layout

```text
lease-api/                 Login, customers, vehicles, quotes, and lease calculator
interest-rate-service/     Historical interest-rate service module
Dockerfile                 Java 21 verification build stage
assesment/docs/            Assessment, implementation plan, decisions
```

See [assessment](assesment/docs/assessment.md) for assignment requirements and
[implementation decisions](assesment/docs/choices.md) for planned REST contracts and security.
