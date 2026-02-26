# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development Loop

Every piece of work follows this loop, in order:

1. **Pull** — `git pull` to sync with `origin/main`
2. **Branch** — `git checkout -b <type>/<short-description>`
3. **Implement** — make the changes
4. **Test** — run the full test suite (see commands below); all tests must pass before committing
5. **Commit** — `git commit` with a clear message; author must be `ajaygodbole7 <ajay.godbole@gmail.com>`, no Co-Authored-By trailers
6. **Push** — `git push -u origin <branch>`
7. **Code review** — create a PR; review and address feedback
8. **Merge to main** — squash-merge into `main`
9. **Rerun tests on main** — `git checkout main && git pull`, then run the full test suite again to confirm green
10. **Push main** — `git push`

Never skip steps 4 or 9. Tests must be green both on the feature branch and again after merging to `main`.

## Build & Run Commands

```bash
# Build the project
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn clean package

# Run ALL tests (unit + integration + repository + utility)
# Requires Podman running; RYUK must be disabled for rootless Podman
DOCKER_HOST="unix:///var/folders/v6/f7hvgvm506jgnmv6y066ccm80000gn/T/podman/podman-machine-default-api.sock" \
  TESTCONTAINERS_RYUK_DISABLED=true \
  JAVA_HOME=$(/usr/libexec/java_home -v 21) \
  mvn test

# Run only unit + utility tests (no Docker/Podman required)
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test \
  -Dtest="*UnitTest,*UtilsTest,JsonUtils*,ValidCurrency*"

# Run a single test class
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -Dtest=CustomerServiceUnitTest

# Run a single test method
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -Dtest=CustomerServiceUnitTest#methodName

# Run the application (default profile, requires local PostgreSQL + Kafka)
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn spring-boot:run

# Run with local profile (uses Docker Compose for infra, enables OpenTelemetry)
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn spring-boot:run -Dspring-boot.run.profiles=local

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

**Layered Spring Boot microservice** (Java 21, Spring Boot 3.5.11) for customer CRUD with observability:

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

System default may be Java 25. Always force Java 21 for Maven. The `./mvnw` wrapper is broken (missing `.mvn/wrapper/maven-wrapper.properties`); use the system `mvn` directly:
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn <goal>
```

## Next Planned Work

**Spring Boot 4 + Java 25 upgrade** — OTel span error marking was deferred to this upgrade.

## Merged Features (all on `main`)

| Commit | Feature |
|--------|---------|
| `bc4de5d` | GIN indexes on JSONB column via Flyway (V1_1_0) |
| `d4b8770` | JSR-380 Bean Validation on Customer domain records |
| `95e935e` | Keyset pagination — `GET /customers?limit=N&after=<cursor>` |
| `654a0fa` | PATCH endpoint — `PATCH /customers/{id}` (RFC 7396 JSON Merge Patch) |
| `7a3653d` | Search endpoints — `GET /customers/search?email=` and `?ssn=` |

## Important Patterns

- **Package-private nested records**: `Email`, `Phone`, `Address`, `Document` are package-private inside `org.observability.otel.domain`. Tests outside that package must use `objectMapper.valueToTree()` + `JsonNode` to read their fields — never call `.email()` / `.primary()` directly from outside the package.
- **Not-found mock pattern**: In unit tests, mock "customer not found" as `.thenThrow(new EmptyResultDataAccessException(...))` — NOT `.thenReturn(Optional.empty())`. The service wraps the `orElseThrow` path through `translateAndThrow`.
- **`translateAndThrow`**: `CustomerNotFoundException` has a pass-through case. All other unknown exceptions fall to the default case → `CustomerServiceException`.
- **Keyset pagination**: `getAllCustomers()` no longer exists — use `getCustomers(Long afterId, int limit)` instead.

## Key Files

| File | Purpose |
|------|---------|
| `src/main/resources/application.yml` | Default config (datasource, Kafka, actuator, logging) |
| `src/main/resources/application-local.yml` | OTel + Docker Compose config for local dev |
| `src/main/resources/db/migration/V1_0_0__create_customer_table.sql` | Flyway schema (JSONB column) |
| `infra/docker-compose.yml` | Full observability stack |
| `infra/otel-collector.yml` | OTel Collector pipeline config |
| `src/main/java/.../util/JsonUtils.java` | Shared JSON utility (validation, serialization, JSONPath, file I/O) |
