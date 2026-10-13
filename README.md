# Lease-a-car API

A Java 21 / Spring Boot 4 application for managing vehicle leases,
customers, vehicles, and historical interest rates.

The application consists of two independent services secured with JWT.

For a complete manual assessment workflow with curl commands and expected results,
see [test-run.md](test-run.md).

## Quick Start

### Requirements

- Java 21+
- Docker with Docker Compose v2 (for containerized execution)

Maven installation is not required; the Maven Wrapper is included.

### Option 1 — Run Locally (Development)

Start both services in separate terminals.

**Terminal 1 — Lease API**

```bash
./mvnw -pl lease-api spring-boot:run -Dspring-boot.run.profiles=dev
```

**Terminal 2 — Interest Rate Service**

```bash
./mvnw -pl interest-rate-service spring-boot:run \
  -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments="--server.port=8081"
```

Services are available at:

| Service               | URL                   |
|-----------------------|-----------------------|
| Lease API             | http://localhost:8080 |
| Interest Rate Service | http://localhost:8081 |

Development credentials:

| Role     | Username | Password          |
|----------|----------|-------------------|
| BROKER   | broker   | broker-password   |
| EMPLOYEE | employee | employee-password |

The `dev` profile uses development-only credentials and must not
be exposed outside a trusted local environment.

### Option 2 — Run with Docker

Docker Compose requires a shared JWT secret and BCrypt password
hashes for both demo accounts.

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
export BROKER_USERNAME=broker
export EMPLOYEE_USERNAME=employee
```

Generate password hashes:

```bash
docker run --rm -it httpd:2.4-alpine htpasswd -nBC 12 broker
docker run --rm -it httpd:2.4-alpine htpasswd -nBC 12 employee
```
> broker-password
> employee-password


Copy each generated hash (without the username prefix):

```bash
export BROKER_PASSWORD_HASH='<broker hash>'
export EMPLOYEE_PASSWORD_HASH='<employee hash>'
```

Start both services:

```bash
docker compose up --build -d
```

Stop:

```bash
docker compose down
```

### Verify the API

Authenticate:

```bash
curl -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"broker","password":"broker-password"}'
```

The example uses the local `dev` profile credentials.

A successful response contains a JWT access token.

Use this token for subsequent requests:

```http
Authorization: Bearer <accessToken>
```

## How It Works

The application consists of two independently buildable services.

### Lease API

Handles:

- Authentication and JWT generation
- Customer management (CRUD)
- Vehicle management (CRUD)
- Lease calculations
- Lease quotes

### Interest Rate Service

Handles:

- Historical interest-rate management (CRUD)
- Retrieving interest rates effective on a given date

### Lease Quote Flow

1. A broker authenticates and receives a JWT.
2. The broker selects a vehicle and submits lease contract details.
3. Lease API retrieves the vehicle's net price.
4. Lease API requests the applicable historical interest rate.
5. Interest Rate Service returns the effective rate.
6. Lease API calculates and returns the monthly lease amount.

Both services independently validate JWT tokens.

### API Overview

| Method   | Endpoint                  | Description              |
|----------|---------------------------|--------------------------|
| POST     | /auth/login               | Authenticate             |
| GET/POST | /customers                | Customer management      |
| GET/POST | /vehicles                 | Vehicle management       |
| GET/POST | /interest-rates           | Interest-rate management |
| GET      | /interest-rates/effective | Historical interest rate |
| POST     | /lease-quotes             | Calculate lease quote    |

Customer, vehicle, and interest-rate resources also support
GET, PUT, and DELETE operations by ID.

Interest-rate endpoints belong to the Interest Rate Service.
All other endpoints belong to the Lease API.

### Authorization

| Role     | Permissions                                               |
|----------|-----------------------------------------------------------|
| BROKER   | Manage customers, request quotes, read vehicles and rates |
| EMPLOYEE | Manage vehicles and interest rates                        |

### Data Storage

The application uses in-memory storage.

- Customer data is cleared on restart.
- Vehicle data is reloaded from the supplied CSV.
- Historical interest rates are reloaded from the supplied CSV.
- Manual changes are not persisted.

## Build & Test

Run the complete Maven verification:

```bash
./mvnw verify
```

Or verify using Docker without installing Java:

```bash
docker build --target verify .
```

Run the automated assessment smoke test:

```bash
sh tests/e2e/docker-smoke.sh
```

The smoke test requires Docker, Python 3, a POSIX shell,
and available host ports 8080 and 8081.

## Documentation

- [API & Technical Reference](assesment/docs/reference.md)
- [Assessment Requirements](assesment/docs/assessment.md)
- [Implementation Decisions](assesment/docs/choices.md)