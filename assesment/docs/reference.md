# Lease-a-car API — Technical Reference

Detailed API contracts, validation rules, authentication,
calculation behavior, and implementation notes.

For installation and basic usage, see [README](../README.md).

## 1. Authentication & Security

### Authentication

POST /auth/login

Request:

```json
{
  "username": "broker",
  "password": "<configured password>"
}
```

Response:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

### JWT Configuration

- Algorithm: HS256
- Issuer: lease-demo
- Audience: lease-platform
- Expiration: 15 minutes
- Stateless authentication
- No server-side sessions

Both services independently validate:

- Signature
- Issuer
- Audience
- Expiration
- Not-before

### Security Responses

| Status | Meaning |
|--------|---------|
| 401 | Missing, expired, or invalid authentication |
| 403 | Authenticated user lacks required permissions |

Only the login endpoint permits anonymous access.

### Security Configuration

Docker Compose requires:

- JWT_SECRET
- BROKER_USERNAME
- BROKER_PASSWORD_HASH
- EMPLOYEE_USERNAME
- EMPLOYEE_PASSWORD_HASH

The JWT secret must contain at least 32 UTF-8 bytes.

Passwords must be stored as valid BCrypt hashes.

The application rejects missing or invalid security configuration.

Optional settings:

- JWT_ISSUER
- JWT_AUDIENCE

Both services must use matching JWT settings.

## 2. Customer API

Base URL: http://localhost:8080

Required role: BROKER

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /customers | Create customer |
| GET | /customers/{id} | Retrieve customer |
| GET | /customers | List customers |
| PUT | /customers/{id} | Replace customer |
| DELETE | /customers/{id} | Delete customer |

### Request Example

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

### Validation

- All fields are required strings.
- Email must be syntactically valid.
- House-number letters are supported.
- Phone-number spaces and plus signs are preserved.
- No email uniqueness constraint.
- No country-specific address or telephone validation.

### Pagination

Defaults:

- page: 0
- size: 50
- maximum size: 100

Results are sorted by UUID.

Customer data is stored in memory and cleared on restart.

## 3. Vehicle API

Base URL: http://localhost:8080

Permissions:

- BROKER: Read
- EMPLOYEE: CRUD

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /vehicles | Create vehicle |
| GET | /vehicles/{id} | Retrieve vehicle |
| GET | /vehicles | List vehicles |
| PUT | /vehicles/{id} | Replace vehicle |
| DELETE | /vehicles/{id} | Delete vehicle |

### Request Example

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

### Validation

- Make, model, and version must not be blank.
- Number of doors must be positive.
- Gross and net prices must be positive.
- CO₂ emission is optional.
- CO₂ emission cannot be negative.

### Vehicle Dataset

10,329 vehicle rows are loaded from CSV at startup.

CSV characteristics:

- Windows-1252 encoding
- Semicolon delimiter
- Quoted values
- Decimal commas

The source `hp` field is not mapped to CO₂.

Duplicate vehicle versions are preserved as separate records.

Manual modifications are cleared on restart.

## 4. Interest Rate API

Base URL: http://localhost:8081

Permissions:

- BROKER: Read
- EMPLOYEE: CRUD

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | /interest-rates | Create rate |
| GET | /interest-rates/{id} | Retrieve rate |
| GET | /interest-rates | List rates |
| GET | /interest-rates/effective?date=YYYY-MM-DD | Effective rate |
| PUT | /interest-rates/{id} | Replace rate |
| DELETE | /interest-rates/{id} | Delete rate |

### Request Example

```json
{
  "interestRate": -0.75,
  "startDate": "2015-02-05"
}
```

### Validation

- Interest rate is required.
- Start date is required.
- Negative and zero interest rates are valid.
- Dates must use ISO format.
- Only one interest rate per start date is allowed.

Duplicate start dates return HTTP 409.

### Historical Rates

20 historical rates are loaded at startup.

CSV characteristics:

- UTF-8 encoding
- Semicolon delimiter
- Decimal commas
- Strict d-M-uuuu dates

Effective-rate selection returns the rate with the
latest start date on or before the requested date.

Dates before 2014-05-01 return HTTP 404.

The supplied historical rate of 4.35% applies from
2019-08-15 until a newer rate is added.

Manual modifications are cleared on restart.

## 5. Lease Quote API

Base URL: http://localhost:8080

Required role: BROKER

POST /lease-quotes

### Request Example

```json
{
  "vehicleId": "123e4567-e89b-12d3-a456-426614174000",
  "mileage": 45000,
  "duration": 60,
  "startDate": "2019-08-15"
}
```

### Processing

1. Validate the contract input.
2. Retrieve the vehicle by UUID.
3. Read the vehicle's net price.
4. Forward the bearer token to Interest Rate Service.
5. Retrieve the effective historical interest rate.
6. Calculate the monthly lease amount.
7. Return the calculated quote.

### Response Content

Successful responses include:

- Contract input
- Vehicle net price
- Selected interest rate
- Interest-rate effective date
- Monthly lease amount
- Currency: EUR

### Error Handling

| Status | Description |
|--------|-------------|
| 400 | Invalid contract input |
| 401 | Invalid or missing authentication |
| 403 | Insufficient permissions |
| 404 | Vehicle not found |
| 422 | No applicable historical interest rate |
| 502 | Downstream authentication or response failure |
| 503 | Interest Rate Service unavailable or timed out |

The implementation does not:

- Retry failed downstream requests
- Cache historical interest rates
- Fall back to alternative interest rates
- Accept client-provided price overrides
- Accept client-provided interest-rate overrides

## 6. Lease Calculation

The implementation follows the assessment formula:

```text
(((annualMileage / 12) * durationMonths) / nettPrice)
  + (((interestRate / 100) * nettPrice) / 12)
```

### Calculation Example

| Parameter | Value |
|-----------|-------|
| Annual mileage | 45,000 km |
| Duration | 60 months |
| Net vehicle price | EUR 63,000 |
| Interest rate | 4.5% |
| Monthly rate | EUR 239.82 |

### Precision

- BigDecimal
- DECIMAL128 precision
- Final rounding only
- HALF_UP
- Two decimal places

The assessment mentions EUR 239.76, but applying the
specified formula produces EUR 239.821428..., rounded
to EUR 239.82.

The calculator has no standalone HTTP endpoint.

## 7. Build & Verification

### Maven

```bash
./mvnw verify
```

### Docker

```bash
docker build --target verify .
```

The verification stage:

- Builds both application JARs
- Executes automated tests
- Checks Google Java Style

Builds fail on test failures or style violations.

### Automated Assessment

```bash
sh tests/e2e/docker-smoke.sh
```

The assessment script:

- Builds Docker images without cache
- Generates temporary credentials
- Starts both services
- Verifies authentication
- Tests CRUD operations
- Verifies historical interest rates
- Validates lease calculations
- Tests authorization failures
- Tests downstream service failures
- Restarts services and verifies data reset behavior
- Cleans up temporary containers and networks

The assessment uses an isolated Compose project named
`lease-assessment-smoke`.

Host ports 8080 and 8081 must be available.

## 8. Runtime & Deployment

### Docker

Runtime containers:

- Use a pinned Java 21 JRE base image
- Contain verified application JARs
- Run as non-root users

### Service Communication

Inside Docker Compose:

```text
http://interest-rate-service:8080
```

The Lease API communicates with the Interest Rate Service
using the internal Compose service name.

The external host port is 8081.

### Local Development

The `dev` Spring profile provides:

| Property | Value |
|----------|-------|
| Broker username | broker |
| Broker password | broker-password |
| Employee username | employee |
| Employee password | employee-password |

These credentials are exclusively for local development.

### Persistence

All application data is held in memory.

| Dataset | Restart Behavior |
|---------|------------------|
| Customers | Cleared |
| Vehicles | Original CSV reloaded |
| Interest rates | Original CSV reloaded |
| Manual modifications | Lost |

## 9. Project Structure

```text
lease-api/
    Authentication
    Customer management
    Vehicle management
    Lease calculations
    Lease quotes

interest-rate-service/
    Historical interest-rate management

tests/e2e/
    Automated assessment verification

assesment/docs/
    Assessment requirements
    Implementation decisions

Dockerfile
    Java 21 verification and runtime builds
```

## 10. Additional References

- [README](../README.md)
- [Assessment](../assesment/docs/assessment.md)
- [Implementation Decisions](../assesment/docs/choices.md)