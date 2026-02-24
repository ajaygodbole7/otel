# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build the project
./mvnw clean package

# Run tests (all)
./mvnw test

# Run a single test class
./mvnw test -Dtest=CustomerServiceUnitTest

# Run a single test method
./mvnw test -Dtest=CustomerServiceUnitTest#methodName

# Run the application (default profile, requires local PostgreSQL + Kafka)
./mvnw spring-boot:run

# Run with local profile (uses Docker Compose for infra, enables OpenTelemetry)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Run with OpenTelemetry Java agent
java -javaagent:opentelemetry-javaagent.jar -Dspring.profiles.active=local -jar target/otel-0.0.1-SNAPSHOT.jar
```

## Infrastructure

Start the full observability stack (PostgreSQL, Kafka, OTel Collector, Prometheus, Tempo, Loki, Grafana):

```bash
docker compose -f infra/docker-compose.yml up -d
```

Grafana is available at `http://localhost:3000`. The `local` Spring profile automatically references `./infra/docker-compose.yml` via Spring Boot Docker Compose integration.

## Frontend

```bash
cd frontend
npm install
npm run dev          # Vite dev server
node sse-server.js   # Express SSE server for real-time updates
```

## Architecture

**Layered Spring Boot microservice** (Java 21, Spring Boot 3.4.2) for customer CRUD with observability:

```
REST (CustomerController) → Service (CustomerService) → Repository (CustomerRepository) → PostgreSQL
                                       ↓
                          Kafka (CustomerEventPublisher) → customer-events topic
```

**Key architectural decisions:**

- **JSONB storage**: `CustomerEntity` stores the full customer domain object as a JSONB column in PostgreSQL. The domain record `Customer` (with nested `Address`, `Email`, `Phone`, `Document` records) is serialized/deserialized via Jackson `ObjectMapper` in the service layer.

- **TSID IDs**: `CustomerEntity` uses Hypersistence TSID (`@GeneratedValue(generator = "tsid")`) for time-sortable, distributed-friendly 63-bit IDs.

- **CloudEvents on Kafka**: `CustomerEventPublisher` publishes structured CloudEvents (`Customer::created`, `Customer::updated`, `Customer::deleted`) to the `customer-events` topic on every CRUD operation.

- **RFC 7807 error responses**: `ExceptionTranslator` (`@RestControllerAdvice`) translates all exceptions—including custom ones (`CustomerNotFoundException`, `CustomerConflictException`, etc.) and Spring validation errors—into Problem Details format. It also injects OTel trace/span IDs into error responses.

- **OpenTelemetry**: The app uses the OpenTelemetry Java agent (`opentelemetry-javaagent.jar`) for automatic instrumentation. The `local` profile configures OTLP export to `http://localhost:4318` (the collector). Trace/span IDs are embedded in log patterns and error responses.

- **API contract**: `CustomerAPI` is an interface with full OpenAPI annotations; `CustomerController` implements it. Base path: `/api/v1/customers`.

## Testing Strategy

- **Unit tests** (`src/test/.../unit/`): Mock-based tests for Controller, Service, EventPublisher, and ExceptionTranslator using Mockito.
- **Integration tests** (`src/test/.../integration/`): `@SpringBootTest` with Testcontainers (PostgreSQL + Kafka). `TestOtelApplication` is the test launcher.
- **Domain tests** (`src/test/.../domain/`): Repository-level tests against a real PostgreSQL container.
- **Utility tests** (`src/test/.../util/`): Focused tests for `JsonUtils` split by method group (File, ToJson, FromJson, ExtractValue, IsJson).

Testcontainers config is in `TestcontainersConfiguration.java`; test data generation uses the Faker library via `CustomerTestDataProvider`.

## Java Version Note

System default may be Java 25. Always force Java 21 for Maven:
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test
```

## In-Progress Work (resume point)

Five feature branches are complete, tested, and pushed to `origin`. They have **not been merged to `main`**. Merge them in this recommended order:

| Order | Branch | Commit | Tests | What it does |
|-------|--------|--------|-------|--------------|
| 1 | `feature/jsonb-gin-indexes` | `4461153` | migration | Flyway: GIN index on `customer_json` JSONB column |
| 2 | `feature/jsr380-validation` | `f753189` | 47 pass | `@Valid`/`@NotBlank`/`@Email` on Customer domain records |
| 3 | `feature/keyset-pagination` | `b335847` | 44 pass | Cursor-based pagination (`?limit=N&after=<cursor>`) replaces list-all |
| 4 | `feature/patch-endpoint` | `f5a9b75` | 49 pass | `PATCH /customers/{id}` with RFC 7396 JSON Merge Patch |
| 5 | `feature/search-endpoints` | `6427ab3` | 50 pass | `GET /customers/search?email=` and `?ssn=` |

**Breaking change in keyset-pagination**: `getAllCustomers()` was replaced by `getCustomers(Long afterId, int limit)` — check for any other callers before merging.

**After merging**: Next planned work is **Spring Boot 4 + Java 25 upgrade** (OTel span error marking was deferred to this upgrade).

### Important patterns to know before touching any branch

- **Package-private nested records**: `Email`, `Phone`, `Address`, `Document` are package-private inside `org.observability.otel.domain`. Tests outside that package must use `objectMapper.valueToTree()` + `JsonNode` to read their fields — never call `.email()` / `.primary()` directly from outside the package.
- **Not-found mock pattern**: In unit tests, mock "customer not found" as `.thenThrow(new EmptyResultDataAccessException(...))` — NOT `.thenReturn(Optional.empty())`. The service wraps the `orElseThrow` path through `translateAndThrow`.
- **`translateAndThrow`**: `CustomerNotFoundException` has a pass-through case (added in search-endpoints). All other unknown exceptions fall to the default case → `CustomerServiceException`.

## Key Files

| File | Purpose |
|------|---------|
| `src/main/resources/application.yml` | Default config (datasource, Kafka, actuator, logging) |
| `src/main/resources/application-local.yml` | OTel + Docker Compose config for local dev |
| `src/main/resources/db/migration/V1_0_0__create_customer_table.sql` | Flyway schema (JSONB column) |
| `infra/docker-compose.yml` | Full observability stack |
| `infra/otel-collector.yml` | OTel Collector pipeline config |
| `src/main/java/.../util/JsonUtils.java` | Shared JSON utility (validation, serialization, JSONPath, file I/O) |
