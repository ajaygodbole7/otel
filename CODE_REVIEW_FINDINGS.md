# Code Review Findings

Staff engineer reviews conducted 2026-03-02 by:
- **Reviewer A**: Oracle Java Staff Engineer (JDK internals, JVM optimization, Java best practices)
- **Reviewer B**: Google Staff Engineer (distributed systems, correctness, failure modes)

## CRITICAL

| # | Finding | File:Line | Reviewer | Fixed |
|---|---------|-----------|----------|-------|
| 1 | Kafka event published before DB transaction commits (dual-write risk) | `CustomerService.java:100-106` | Both | No — requires transactional outbox; documented as known trade-off |

## HIGH

| # | Finding | File:Line | Reviewer | Fixed |
|---|---------|-----------|----------|-------|
| 2 | Kafka message key uses event ID, not customer ID — breaks per-customer ordering | `CustomerEventPublisher.java:68` | B | Yes |
| 3 | Mutable lists in Customer record — no defensive copies | `Customer.java:20-22` | A | Yes |
| 4 | `findById` catches its own `CustomerNotFoundException` — double ERROR logging on 404s | `CustomerService.java:53-67` | Both | Yes |
| 5 | `@GeneratedValue` not present on `CustomerEntity.id` — docs say it is | `CustomerEntity.java:29-31` | B | No — docs-only fix deferred |

## MEDIUM

| # | Finding | File:Line | Reviewer | Fixed |
|---|---------|-----------|----------|-------|
| 6 | `createdAt` tamperable via PATCH — merged customer's value used instead of existing entity's | `CustomerService.java:207-208` | B | Yes |
| 7 | RFC 7396: `null` in patch should remove field, not set to NullNode | `CustomerService.java:194` | A | No — current behavior correct for required fields; optional fields accept null |
| 8 | No `@Size(max=N)` upper bound on list fields — DoS via unbounded arrays | `Customer.java:20-22` | B | No — deferred |
| 9 | Email uniqueness trigger is case-sensitive | `V1_5_0:35-41` | B | No — deferred |
| 10 | Stack traces in `dev` profile — but `dev` profile doesn't exist (dead code) | `ExceptionTranslator.java:295-307` | A | No — deferred |
| 11 | Exception detail leaks raw messages to clients for internal errors | `ExceptionTranslator.java:283` | B | No — deferred |
| 12 | Synchronous Kafka `.get()` inside transaction holds DB connection for up to 5s | `CustomerEventPublisher.java:68-69` | Both | No — intentional design choice for teaching project |
| 13 | `JsonUtils` has its own `ObjectMapper` — different config from Spring bean | `JsonUtils.java:49` | Both | No — intentional for utility class |
| 14 | `extractValue` generic `<T>` is unchecked cast — false type safety | `JsonUtils.java:271-290` | A | No — deferred |
| 15 | `ValidatorFactory` never closed in tests — resource leak | `CustomerServiceUnitTest.java:54-55` | Both | No — deferred |
| 16 | Unit test mocks `findById` to throw `EmptyResultDataAccessException` — unrealistic scenario | `CustomerServiceUnitTest.java:199-207` | Both | No — deferred |
| 17 | `TSID.Factory.getTsid()` potentially deprecated — use `TSID.fast()` | Multiple files | A | No — deferred to SB4 upgrade |
| 18 | Hardcoded DB credentials in `application.yml` | `application.yml:8-9` | Both | No — acceptable for demo project |
| 19 | `updatedAt` in JSONB vs `updated_at` column drift — two `Instant.now()` calls | V1_3_0 / `CustomerService` | A | No — nanosecond drift acceptable |
| 20 | `@Builder` bypasses Bean Validation | `Customer.java:12-13` | A | No — acceptable trade-off; documented |
| 21 | `flyway-maven-plugin` declared as dependency instead of `flyway-core` | `pom.xml:161-164` | Both | Yes |
| 22 | `hibernate-types-60` redundant with `hypersistence-utils` | `pom.xml:166-169` | Both | Yes |
| 23 | `datafaker` lacks `<scope>test</scope>` | `pom.xml:261-265` | Both | Yes |
| 24 | No HikariCP connection pool settings configured | `application.yml` | B | No — deferred |
| 25 | Kafka `RETRIES_CONFIG` set to `Integer.MAX_VALUE` — OOM risk under Kafka outage at scale | `KafkaConfig.java:30` | B | No — acceptable for demo project |
| 26 | `flyway.clean-disabled: false` in local profile | `application-local.yml:42` | B | No — intentional for local dev |
| 27 | All actuator endpoints exposed in local profile | `application-local.yml:55-57` | B | No — intentional for local dev |
| 28 | Advisory lock `hashtext()` collisions at scale | `V1_5_0:32` | A | No — acceptable at current scale |
| 29 | `TSIDDemo.java` in `src/main` with hardcoded credentials | `TSIDDemo.java` | Both | No — deferred |
| 30 | `spring-boot-starter-hateoas` unused | `pom.xml:104-105` | B | Yes |

## LOW

| # | Finding | File:Line | Reviewer | Fixed |
|---|---------|-----------|----------|-------|
| 31 | `findByEmail` could return multiple rows if trigger bypassed | `CustomerRepository.java:22` | A | No |
| 32 | `findByEmail` matches any email in array, not just primary | `CustomerRepository.java:19-22` | A | No — by design |
| 33 | Redundant Kafka config with idempotence enabled | `KafkaConfig.java:30` | A | No |
| 34 | Logging user-supplied email at DEBUG level (PII) | `CustomerController.java:102` | A | No |
| 35 | Hardcoded Location URI path (not using `ApiConstants`) | `CustomerController.java:59` | Both | No |
| 36 | `UserAgent` echoed in error response | `ExceptionTranslator.java:310-321` | A | No |
| 37 | Excessive INFO logging in service methods | `CustomerService.java` throughout | Both | No |
| 38 | `CustomerType` not enum-validated — any string passes | `Customer.java:15` | B | No |
| 39 | Phone regex allows digit-free strings | `Customer.java:47` | B | No |
| 40 | No `spring.jpa.open-in-view: false` configured | `application.yml` | B | No |
| 41 | `Faker` shared static instance not thread-safe | `CustomerTestDataProvider.java:15` | A | No |
| 42 | `readJsonFromFile` discards validated `File` object | `JsonUtils.java:312` | Both | No |
| 43 | `toJson` catches `Throwable` instead of `Exception` | `JsonUtils.java:183` | A | No |
| 44 | Integration test name misleading (says "duplicate ID", actual conflict is email) | `CustomerIntegrationTest.java:401` | Both | No |
| 45 | `ExceptionTranslatorUnitTest` uses fragile `getDeclaredMethods()[0]` | `ExceptionTranslatorUnitTest.java:133` | A | No |
| 46 | Exception hierarchy flat — no common sealed base class | All exception classes | A | No |
| 47 | `List<Customer> data` not defensively copied in `CustomerPageResponse` | `CustomerPageResponse.java:6-11` | A | No |
| 48 | `equals()` on unsaved entities with null ID considered equal | `CustomerEntity.java:60-64` | A | No |
| 49 | Redundant index on PK column in V1_0_0 (fixed by V1_1_0) | `V1_0_0:11` | A | No — self-corrected |
| 50 | Email search param not size-bounded | `CustomerAPI.java:115-116` | B | No |
| 51 | Log injection risk via email search param | `CustomerAPI.java:115-116` | B | No |

## NIT

| # | Finding | File:Line | Reviewer | Fixed |
|---|---------|-----------|----------|-------|
| 52 | `@Size(min=1)` redundant with `@NotBlank` | `Customer.java:16-17` | A | No |
| 53 | `toString()` quotes Long id as string | `CustomerEntity.java:72` | A | No |
| 54 | Inconsistent log levels between controller endpoints | `CustomerController.java` | A | No |
| 55 | `Instant.now()` in `ExceptionTranslator` makes timestamp non-deterministic in tests | `ExceptionTranslator.java:287` | A | No |
| 56 | Wildcard import in `CustomerControllerUnitTest` | `CustomerControllerUnitTest.java:35` | A | No |
| 57 | `ValidCurrencyValidator` normalizes input but doesn't normalize stored value | `ValidCurrencyValidator.java:38` | B | No |
| 58 | `ValidatorFactory` leak in `ValidCurrencyTest` | `ValidCurrencyTest.java:25-27` | A | No |
