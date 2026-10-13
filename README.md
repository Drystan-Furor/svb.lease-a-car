# Lease-a-car API

Lease-a-car is a Java 21 / Spring Boot 4 two-service leasing API. It separates lease operations
from interest-rate management. Both services validate the caller's identity on every request.

## Current scope

Two independently buildable services exist:

- `lease-api` owns login and lease calculations, and will own customers, vehicles, and quotes.
- `interest-rate-service` will own dated interest rates.

Implemented behavior is stateless JWT login plus the monthly-rate calculation in `lease-api`.
The calculator has no HTTP endpoint yet. CRUD resources, historical interest selection, and
service-to-service calls are planned work, not current features.

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

No business REST resource is exposed in this revision. `POST /auth/login` is available; valid
bearer tokens already protect the planned route families in both services.

## Project layout

```text
lease-api/                 Lease API module and calculator
interest-rate-service/     Interest-rate service module
Dockerfile                 Java 21 verification build stage
assesment/docs/            Assessment, implementation plan, decisions
```

See [assessment](assesment/docs/assessment.md) for assignment requirements and
[implementation decisions](assesment/docs/choices.md) for planned REST contracts and security.
