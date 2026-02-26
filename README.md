# OpenTelemetry Demo with Spring Boot

## Introduction

Demonstration application showcasing OpenTelemetry integration with Spring Boot for observability — distributed tracing, metrics, and structured logging. Features a Customer REST API with PostgreSQL (JSONB storage), Kafka event publishing (CloudEvents), keyset pagination, JSON Merge Patch, and search endpoints.

## Technology Stack

| Category | Technology |
|----------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.11 |
| Persistence | PostgreSQL (JSONB document pattern), Spring Data JPA, Flyway migrations |
| Messaging | Apache Kafka (KRaft mode), CloudEvents |
| Observability | OpenTelemetry Java Agent, OTel Collector, Grafana, Prometheus, Tempo, Loki |
| Validation | Jakarta Bean Validation (JSR-380) |
| Utilities | Lombok, Jackson, Hypersistence TSID |
| Testing | JUnit 5, Mockito, AssertJ, Testcontainers |

## Architecture

```
REST (CustomerController)
        ↓
Service (CustomerService) ──→ Kafka (CustomerEventPublisher) ──→ customer-events topic
        ↓
Repository (CustomerRepository)
        ↓
PostgreSQL (JSONB column)
```

The full `Customer` domain object (with nested `Address`, `Email`, `Phone`, `Document`) is stored as a single JSONB blob in PostgreSQL. GIN indexes support fast JSONB path queries for email and SSN lookups. IDs are TSID (time-sortable 63-bit integers).

Every mutating operation (create, update, patch, delete) publishes a `Customer::created / Customer::updated / Customer::deleted` CloudEvent to Kafka.

OpenTelemetry traces flow:
```
Spring Boot (OTLP) → OTel Collector → Tempo (traces)
                                    → Prometheus (metrics)
                                    → Loki (logs)
```

## Prerequisites

- Java 21
- Docker and Docker Compose
- Maven 3.x (or use the included `./mvnw` wrapper)
- cURL (for manual testing)

> **Java version note**: if your system default is not Java 21, prefix all Maven commands:
> ```bash
> JAVA_HOME=$(/usr/libexec/java_home -v 21) ./mvnw <goal>
> ```

## Quick Start

### 1. Clone the repository
```bash
git clone git@github.com:ajaygodbole7/otel.git
cd otel
```

### 2. Download the OpenTelemetry Java agent
```bash
curl -L https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar \
     -o opentelemetry-javaagent.jar
```

### 3. Start infrastructure
```bash
docker compose -f infra/docker-compose.yml up -d
```

Starts: PostgreSQL, Kafka (KRaft), OTel Collector, Prometheus, Tempo, Loki, Grafana.

### 4. Build
```bash
./mvnw clean package -DskipTests
```

### 5. Run the application

With Maven (recommended for development):
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

With the JAR directly:
```bash
java -javaagent:./opentelemetry-javaagent.jar \
     -jar target/otel-0.0.1-SNAPSHOT.jar \
     --spring.profiles.active=local
```

The `local` profile enables OTel export to `http://localhost:4318` and activates Spring Boot Docker Compose integration.

## API Reference

Base path: `/api/v1/customers`

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/customers` | Create customer |
| `GET` | `/customers/{id}` | Get customer by ID |
| `PUT` | `/customers/{id}` | Full replace |
| `PATCH` | `/customers/{id}` | Partial update (JSON Merge Patch, RFC 7396) |
| `DELETE` | `/customers/{id}` | Delete customer |
| `GET` | `/customers?limit=N&after=<cursor>` | Keyset pagination |
| `GET` | `/customers/search?email=<email>` | Search by email |
| `GET` | `/customers/search?ssn=<ssn>` | Search by SSN |

All error responses use RFC 7807 Problem Details format (`application/problem+json`).

## curl Examples

### Create customer
```bash
curl -X POST http://localhost:8080/api/v1/customers \
  -H "Content-Type: application/json" \
  -d '{
    "type": "INDIVIDUAL",
    "firstName": "John",
    "lastName": "Doe",
    "emails": [{"primary": true, "email": "john.doe@example.com", "type": "PERSONAL"}],
    "phones": [{"type": "MOBILE", "countryCode": "+1", "number": "5555555555"}],
    "addresses": [{"type": "HOME", "line1": "123 Main St", "city": "Springfield", "state": "IL", "postalCode": "62701", "country": "USA"}]
  }'
```

### Get customer by ID
```bash
curl http://localhost:8080/api/v1/customers/{id}
```

### Full update (PUT)
```bash
curl -X PUT http://localhost:8080/api/v1/customers/{id} \
  -H "Content-Type: application/json" \
  -d '{ "type": "INDIVIDUAL", "firstName": "John", "lastName": "Smith", ... }'
```

### Partial update (PATCH — JSON Merge Patch)
```bash
curl -X PATCH http://localhost:8080/api/v1/customers/{id} \
  -H "Content-Type: application/merge-patch+json" \
  -d '{"firstName": "Jane"}'
```

### Delete customer
```bash
curl -X DELETE http://localhost:8080/api/v1/customers/{id}
```

### Keyset pagination
```bash
# First page
curl "http://localhost:8080/api/v1/customers?limit=20"

# Next page — pass the last ID from the previous response as the cursor
curl "http://localhost:8080/api/v1/customers?limit=20&after={lastId}"
```

### Search by email or SSN
```bash
curl "http://localhost:8080/api/v1/customers/search?email=john.doe%40example.com"
curl "http://localhost:8080/api/v1/customers/search?ssn=123-45-6789"
```

Add `-v` to any command to see full request/response headers.

## Testing

The project has **265 tests across 4 layers** — unit/utility tests run without Docker; integration and repository tests spin up real containers via Testcontainers.

| Layer | Tests | What runs |
|-------|-------|-----------|
| Unit | 94 | Mock-based, strict Mockito (`@ExtendWith(MockitoExtension.class)`), no I/O |
| Integration | 13 | Full Spring Boot + real PostgreSQL + real Kafka; verifies HTTP → DB → Kafka in each test |
| Repository | 13 | `@DataJpaTest` + real PostgreSQL; JSONB round-trip, GIN index queries, keyset pagination |
| Utility | 144 | `JsonUtils`, `ValidCurrency`; `@ParameterizedTest`, no Spring context |

```bash
# All tests
./mvnw test

# Unit + utility only (no Docker required)
./mvnw test -Dtest="*UnitTest,*UtilsTest,JsonUtils*,ValidCurrency*"

# Single class
./mvnw test -Dtest=CustomerServiceUnitTest

# Single method
./mvnw test -Dtest=CustomerServiceUnitTest#shouldCreateCustomer
```

## Observability

| Tool | URL | Notes |
|------|-----|-------|
| Grafana | http://localhost:3000 | Anonymous auth, Admin role. Pre-configured datasources for Prometheus, Tempo, Loki |
| Prometheus | http://localhost:9090 | Metrics scraping |
| Tempo | http://localhost:3200 | Distributed traces |

Trace and span IDs are embedded in log output and in RFC 7807 error response bodies.

## Key Configuration Files

| File | Purpose |
|------|---------|
| `src/main/resources/application.yml` | Default config — datasource, Kafka, actuator, logging |
| `src/main/resources/application-local.yml` | Local dev — OTel OTLP export, Docker Compose integration |
| `src/main/resources/db/migration/V1_0_0__create_customer_table.sql` | Flyway schema — JSONB column |
| `src/main/resources/db/migration/V1_1_0__add_gin_indexes.sql` | Flyway — GIN indexes on JSONB paths |
| `infra/docker-compose.yml` | Full observability stack |
| `infra/otel-collector.yml` | OTel Collector pipeline (receivers, processors, exporters) |
