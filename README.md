# Lease-a-car API

Lease-a-car is a Java 21 / Spring Boot 4 foundation for a two-service leasing API. It separates
lease operations from interest-rate management so later stories can add authenticated REST
resources and service-to-service quote retrieval without sharing data storage.

## Current scope

Two independently buildable services exist:

- `lease-api` owns lease calculations and will own customers, vehicles, login, and quotes.
- `interest-rate-service` will own dated interest rates.

Both application contexts start successfully. The implemented business behavior is the
monthly-rate calculation in `lease-api`; it has no HTTP endpoint yet. Authentication, CRUD
resources, historical interest selection, service-to-service calls, runtime Docker images, and
Compose startup are planned work, not current features.

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

## Run services locally

Runtime Docker images and Compose are intentionally deferred to S11. Until then, start each
service in its own terminal with a local JDK:

```sh
./mvnw -pl lease-api spring-boot:run
```

```sh
./mvnw -pl interest-rate-service spring-boot:run \
  -Dspring-boot.run.arguments="--server.port=8081"
```

`lease-api` listens on port `8080`; `interest-rate-service` listens on port `8081` in this setup.
Stop either service with `Ctrl+C`.

Neither service exposes business REST endpoints in this revision. Starting them proves their
independent application configuration only.

## Project layout

```text
lease-api/                 Lease API module and calculator
interest-rate-service/     Interest-rate service module
Dockerfile                 Java 21 verification build stage
assesment/docs/            Assessment, implementation plan, decisions
```

See [assessment](assesment/docs/assessment.md) for assignment requirements and
[implementation decisions](assesment/docs/choices.md) for planned REST contracts and security.
