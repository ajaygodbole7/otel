# SSN / Document Removal Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove the `Document` record, the `documents` field on `Customer`, the `findBySSN` repository/service method, and the `?ssn=` search endpoint — eliminating all SSN PII from the data model, APIs, tests, scripts, and documentation.

**Architecture:** The `Document` record (`{country, type, identifier}`) is the sole container for SSN data (`type='SSN'`), stored in the JSONB `customer_json` column. Removal requires a Flyway migration to scrub existing rows, a domain-model change that cascades compile errors through all layers, and cleanup of every test, script, and doc that references SSN or Document.

**Tech Stack:** Java 21, Spring Boot 3.5.11, Spring Data JPA, PostgreSQL JSONB, Flyway 11, Kafka CloudEvents, JUnit 5, Mockito, Testcontainers, Maven

---

## Change Map (15 files)

| # | File | Action |
|---|------|--------|
| T1 | `src/main/resources/db/migration/V1_2_0__remove_documents_from_customer.sql` | **Create** |
| T2 | `src/main/resources/db/migration/V1_1_0__add_jsonb_gin_indexes.sql` | Comment only |
| T3 | `src/main/java/org/observability/otel/domain/Customer.java` | Remove field + record |
| T4 | `src/main/java/org/observability/otel/domain/CustomerRepository.java` | Remove method |
| T5 | `src/main/java/org/observability/otel/service/CustomerService.java` | Remove method + 2 lines |
| T6 | `src/main/java/org/observability/otel/rest/CustomerAPI.java` | Simplify signature |
| T7 | `src/main/java/org/observability/otel/rest/CustomerController.java` | Simplify method |
| T8 | `src/test/java/org/observability/otel/unit/CustomerServiceUnitTest.java` | Remove SSN tests |
| T9 | `src/test/java/org/observability/otel/unit/CustomerControllerUnitTest.java` | Remove SSN tests + helpers |
| T10 | `src/test/java/org/observability/otel/domain/CustomerRepositoryTest.java` | Remove SSN test |
| T11 | `src/test/java/org/observability/otel/integration/CustomerIntegrationTest.java` | Remove SSN test |
| T12 | `src/test/java/org/observability/otel/domain/CustomerTestDataProvider.java` | Remove documents |
| T13 | `infra/e2e-test.sh` | Remove SSN var + JSON field |
| T14 | `infra/load-test.sh` | Update header comment |
| T15 | `CLAUDE.md` | Update 3 locations |
| T16 | `README.md` | Update endpoint table + examples |
| T17 | `architecture.html` | Update 7 locations |

---

## Task 1: Flyway Migration — Scrub Documents from JSONB

**Files:**
- Create: `src/main/resources/db/migration/V1_2_0__remove_documents_from_customer.sql`

**Step 1: Create the migration file**

```sql
-- Remove the documents field from all existing customer JSONB records.
-- Scrubs SSN, DRIVER_LICENSE, and PASSPORT identifiers from the column.
-- One-way: no DOWN migration. Intentional data removal.
UPDATE customers
SET customer_json = customer_json - 'documents',
    updated_at    = now()
WHERE customer_json ? 'documents';
```

**Step 2: Verify filename follows Flyway convention**

```bash
ls src/main/resources/db/migration/
# Expected:
# V1_0_0__create_customer_table.sql
# V1_1_0__add_jsonb_gin_indexes.sql
# V1_2_0__remove_documents_from_customer.sql   ← new
```

---

## Task 2: Update GIN Index Migration Comment

**Files:**
- Modify: `src/main/resources/db/migration/V1_1_0__add_jsonb_gin_indexes.sql`

**Step 1: Fix the comment on line 6**

Before:
```sql
-- Covers both findByEmail and findBySSN native queries
```

After:
```sql
-- Covers findByEmail native query
```

---

## Task 3: Domain Model — Remove Document Record and Field

**Files:**
- Modify: `src/main/java/org/observability/otel/domain/Customer.java`

This is the keystone change. After this, the project will not compile until Tasks 4–7 and 12 are complete. That is expected — follow the compile errors.

**Step 1: Remove the `documents` field from the `Customer` record**

Remove line 23:
```java
// DELETE this line:
@Valid List<Document> documents,
```

Also remove the trailing comma from the line above it (the `phones` field) if `documents` was the last field before `createdAt`. Check the record signature and ensure commas are correct.

The final `Customer` record component list should be:
```java
@Builder
public record Customer(
    Long id,
    @NotBlank @Size(max = 50) String type,
    @NotBlank @Size(min = 1, max = 100) String firstName,
    @NotBlank @Size(min = 1, max = 100) String lastName,
    @Size(max = 100) String middleName,
    @Size(max = 50) String suffix,
    @Valid @NotEmpty List<Address> addresses,
    @Valid @NotEmpty List<Email> emails,
    @Valid @NotEmpty List<Phone> phones,
    Instant createdAt,
    Instant updatedAt
) {}
```

**Step 2: Remove the `Document` record (lines 51–55)**

Delete the entire record definition:
```java
// DELETE this entire block:
record Document(
    @NotBlank @Size(max = 100) String country,
    @NotBlank @Size(max = 50) String type,
    @NotBlank @Size(max = 100) String identifier
) {}
```

**Step 3: Verify the import for `@Valid` is still needed**

`@Valid` is still used on `addresses`, `emails`, and `phones` — keep the import.
`List` import is still used — keep it.

**Step 4: Attempt compile — confirm expected failures**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn compile 2>&1 | grep "ERROR"
```

Expected compile errors (guides the remaining tasks):
- `CustomerService.java` — `.documents(...)` calls
- `CustomerService.java` — `findBySSN` references `Customer` with `documents`
- `CustomerRepository.java` — `findBySSN` references no longer needed
- `CustomerTestDataProvider.java` — `Document` class usage
- Various test files — `Document` usage

---

## Task 4: Repository — Remove findBySSN

**Files:**
- Modify: `src/main/java/org/observability/otel/domain/CustomerRepository.java`

**Step 1: Delete the `findBySSN` method**

Remove lines 26–31 entirely:
```java
// DELETE this entire block:
@Query(value = "SELECT * FROM customers " +
    "WHERE EXISTS (" +
    "  SELECT 1 FROM jsonb_array_elements(customer_json->'documents') AS doc " +
    "  WHERE doc->>'type' = 'SSN' AND doc->>'identifier' = :ssn" +
    ")", nativeQuery = true)
Optional<CustomerEntity> findBySSN(@Param("ssn") String ssn);
```

**Step 2: Verify remaining repository**

The final `CustomerRepository` interface should have exactly two custom methods: `findByEmail` and `findNextPage`.

Remove the `@Param` import if it is now unused (it is still used by `findByEmail` and `findNextPage` — keep it).

---

## Task 5: Service — Remove findBySSN and documents References

**Files:**
- Modify: `src/main/java/org/observability/otel/service/CustomerService.java`

**Step 1: Remove `.documents(customer.documents())` from `create()` (line 98)**

In the `create()` method, the `Customer.builder()` block currently includes:
```java
// DELETE this line:
.documents(customer.documents())
```

**Step 2: Remove `.documents(inboundCustomer.documents())` from `update()` (line 156)**

In the `update()` method's `Customer.builder()` block:
```java
// DELETE this line:
.documents(inboundCustomer.documents())
```

**Step 3: Delete the entire `findBySSN` method (lines 265–283)**

```java
// DELETE this entire method:
/**
 * Find a customer by their SSN.
 * ...
 */
public Customer findBySSN(String ssn) {
    log.info("Finding customer by SSN");
    try {
        Customer customer = customerRepository.findBySSN(ssn)
            .map(this::convertToCustomer)
            .orElseThrow(() -> new CustomerNotFoundException("No customer found with SSN provided"));
        log.info("Successfully found customer by SSN");
        log.debug("Retrieved customer details: {}", customer);
        return customer;
    } catch (Exception e) {
        log.error("Error finding customer by SSN", e);
        return translateAndThrow(e, "Error retrieving customer by SSN");
    }
}
```

**Step 4: Verify compile passes for main sources**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn compile 2>&1 | grep "ERROR"
```

Expected: no errors from main sources. Test compilation errors are expected at this point.

---

## Task 6: API Interface — Simplify searchCustomer Signature

**Files:**
- Modify: `src/main/java/org/observability/otel/rest/CustomerAPI.java`

**Step 1: Update the `@Operation` summary (line 108)**

```java
// Before:
@Operation(summary = "Search customer by email or SSN")
// After:
@Operation(summary = "Search customer by email")
```

**Step 2: Update the `@ApiResponse` for 400 (line 111)**

```java
// Before:
@ApiResponse(responseCode = "400", description = "Exactly one search parameter required"),
// After:
@ApiResponse(responseCode = "400", description = "email parameter is required"),
```

**Step 3: Replace the `searchCustomer` method signature (lines 115–117)**

Before:
```java
ResponseEntity<Customer> searchCustomer(
    @RequestParam(required = false) String email,
    @RequestParam(required = false) String ssn);
```

After:
```java
ResponseEntity<Customer> searchCustomer(
    @RequestParam @NotBlank String email);
```

`@NotBlank` is already imported (used elsewhere in the file from `jakarta.validation.constraints`). Verify the import exists; add `import jakarta.validation.constraints.NotBlank;` if not already present.

Remove unused import `java.util.List` if it was only needed by the old signature (check — it may have been there for other reasons).

---

## Task 7: Controller — Simplify searchCustomer

**Files:**
- Modify: `src/main/java/org/observability/otel/rest/CustomerController.java`

**Step 1: Update the method signature (line 100)**

```java
// Before:
public ResponseEntity<Customer> searchCustomer(String email, String ssn) {

// After:
public ResponseEntity<Customer> searchCustomer(String email) {
```

**Step 2: Update the log statement (line 101)**

```java
// Before:
log.debug("REST request to search Customer by email={} ssn=<redacted>", email != null ? email : "(none)");

// After:
log.debug("REST request to search Customer by email={}", email);
```

**Step 3: Replace the method body (lines 102–108)**

Before:
```java
if ((email == null) == (ssn == null)) {
    throw new IllegalArgumentException("Exactly one of 'email' or 'ssn' must be provided");
}
Customer customer = email != null
    ? customerService.findByEmail(email)
    : customerService.findBySSN(ssn);
return ResponseEntity.ok(customer);
```

After:
```java
Customer customer = customerService.findByEmail(email);
return ResponseEntity.ok(customer);
```

The null/blank guard is now handled by `@NotBlank` in the interface — no manual check needed.

**Step 4: Remove unused imports**

Remove `import jakarta.validation.constraints.Min;` and `import jakarta.validation.constraints.NotNull;` from the controller if they only appeared in the old `@PathVariable`-style params — verify against remaining usages before removing.

**Step 5: Full main-source compile check**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn compile 2>&1 | grep "ERROR"
```

Expected: zero errors. Main sources are complete.

**Step 6: Commit main-source changes**

```bash
git add \
  src/main/resources/db/migration/V1_2_0__remove_documents_from_customer.sql \
  src/main/resources/db/migration/V1_1_0__add_jsonb_gin_indexes.sql \
  src/main/java/org/observability/otel/domain/Customer.java \
  src/main/java/org/observability/otel/domain/CustomerRepository.java \
  src/main/java/org/observability/otel/service/CustomerService.java \
  src/main/java/org/observability/otel/rest/CustomerAPI.java \
  src/main/java/org/observability/otel/rest/CustomerController.java

git commit -m "refactor: remove SSN/Document from domain, repository, service, and API"
```

---

## Task 8: Service Unit Tests — Remove SSN Tests

**Files:**
- Modify: `src/test/java/org/observability/otel/unit/CustomerServiceUnitTest.java`

**Step 1: Delete the `findBySSN` test block**

Find and delete the block that begins with `// findBySSN tests` (around line 434). Delete:
- The comment `// findBySSN tests`
- The test `findBySSN_repositoryReturnsEmpty_throwsCustomerNotFoundException()`
- Any `verify(customerRepository).findBySSN(...)` calls

**Step 2: Compile test sources to confirm no remaining Document/SSN references**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test-compile 2>&1 | grep "ERROR" | grep -i "CustomerServiceUnitTest"
```

Expected: no errors for this file.

---

## Task 9: Controller Unit Tests — Remove SSN Tests and Update Email-Only Tests

**Files:**
- Modify: `src/test/java/org/observability/otel/unit/CustomerControllerUnitTest.java`

**Step 1: Delete helper method `buildSearchBySsnRequest(String ssn)`**

```java
// DELETE this method entirely:
private RequestBuilder buildSearchBySsnRequest(String ssn) {
    return MockMvcRequestBuilders.get(SEARCH_URL)
        .param("ssn", ssn);
}
```

**Step 2: Delete helper method `buildSearchBothRequest(String email, String ssn)`**

```java
// DELETE this method entirely:
private RequestBuilder buildSearchBothRequest(String email, String ssn) {
    return MockMvcRequestBuilders.get(SEARCH_URL)
        .param("email", email)
        .param("ssn", ssn);
}
```

**Step 3: Delete test `shouldReturnCustomerWhenSearchingBySSN()`**

```java
// DELETE this test:
@Test
@DisplayName("Should return 200 with customer when searching by SSN")
void shouldReturnCustomerWhenSearchingBySSN() throws Exception { ... }
```

**Step 4: Delete test `shouldReturn404WhenSearchingByMissingSSN()`**

```java
// DELETE this test:
@Test
@DisplayName("Should return 404 Problem Detail when searching by non-existent SSN")
void shouldReturn404WhenSearchingByMissingSSN() throws Exception { ... }
```

**Step 5: Update the mutual-exclusion parameter validation tests**

The old tests verified:
- both `email` + `ssn` → 400
- neither `email` nor `ssn` → 400
- email only → 200
- ssn only → 200

After the change, the only valid call requires `email`. Replace these tests with:

```java
@Test
@DisplayName("Should return 400 when email parameter is missing")
void shouldReturn400WhenEmailIsMissing() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get(SEARCH_URL))
        .andExpect(status().isBadRequest());
}

@Test
@DisplayName("Should return 400 when email parameter is blank")
void shouldReturn400WhenEmailIsBlank() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get(SEARCH_URL).param("email", "  "))
        .andExpect(status().isBadRequest());
}
```

Keep the existing `shouldReturnCustomerWhenSearchingByEmail()` and `shouldReturn404WhenSearchingByMissingEmail()` tests — they are the happy path and error path for the new email-only signature.

**Step 6: Run controller unit tests only**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -Dtest=CustomerControllerUnitTest 2>&1 | tail -20
```

Expected: all tests pass, 0 failures.

---

## Task 10: Repository Tests — Remove SSN Test

**Files:**
- Modify: `src/test/java/org/observability/otel/domain/CustomerRepositoryTest.java`

**Step 1: Delete `shouldFindCustomerBySSN()` test**

Delete the entire test method (around lines 169–195):
```java
// DELETE this test:
@Test
@DisplayName("Should find customer by SSN using JSONB path query")
void shouldFindCustomerBySSN() throws JsonProcessingException { ... }
```

This test uses `Document::type` and `Document::identifier` accessors — both gone with the record removal.

**Step 2: Audit `shouldSaveAndRetrieveFullCustomer()`**

This test uses `CustomerTestDataProvider.createFullCustomer()`. After Task 12 removes `documents` from the provider, verify this test contains no assertions on `documents`. If it does, delete those assertion lines only.

**Step 3: Run repository tests**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -Dtest=CustomerRepositoryTest 2>&1 | tail -20
```

Expected: all remaining tests pass.

---

## Task 11: Integration Tests — Remove SSN Test

**Files:**
- Modify: `src/test/java/org/observability/otel/integration/CustomerIntegrationTest.java`

**Step 1: Delete `shouldFindCustomerBySSN()` integration test**

Delete the entire test method (around lines 391–412):
```java
// DELETE this test:
@Test
@DisplayName("Should find customer by SSN after creation")
void shouldFindCustomerBySSN() throws Exception { ... }
```

This test also does JSONB tree traversal to extract an SSN — all of it goes.

**Step 2: Remove unused imports in the integration test file**

After deletion, check for any now-unused imports:
- `StreamSupport` — used only for SSN extraction? Remove if so.
- Any `Document`-related imports.

**Step 3: Audit remaining tests that call `createFullCustomer()`**

Search for `createFullCustomer` in this file. For each usage, verify no `documents` field is asserted in the response. Remove those assertions if found.

---

## Task 12: Test Data Provider — Remove Documents

**Files:**
- Modify: `src/test/java/org/observability/otel/domain/CustomerTestDataProvider.java`

**Step 1: Remove the `documents` call from `createFullCustomer()`**

Delete lines 77–81:
```java
// DELETE this block:
.documents(List.of(
    new Document("USA", "DRIVER_LICENSE", faker.bothify("??########")),
    new Document("USA", "PASSPORT", faker.numerify("#########")),
    new Document("USA", "SSN", faker.numerify("###-##-####"))
))
```

**Step 2: Verify test-compile passes cleanly**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test-compile 2>&1 | grep "ERROR"
```

Expected: zero errors.

**Step 3: Run unit + utility tests (no containers required)**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test \
  -Dtest="*UnitTest,*UtilsTest,JsonUtils*,ValidCurrency*" 2>&1 | tail -20
```

Expected: all tests pass, 0 failures.

**Step 4: Commit test changes**

```bash
git add \
  src/test/java/org/observability/otel/unit/CustomerServiceUnitTest.java \
  src/test/java/org/observability/otel/unit/CustomerControllerUnitTest.java \
  src/test/java/org/observability/otel/domain/CustomerRepositoryTest.java \
  src/test/java/org/observability/otel/integration/CustomerIntegrationTest.java \
  src/test/java/org/observability/otel/domain/CustomerTestDataProvider.java

git commit -m "test: remove SSN/Document tests and update test data provider"
```

---

## Task 13: Infrastructure Scripts — e2e-test.sh

**Files:**
- Modify: `infra/e2e-test.sh`

**Step 1: Remove the `E2E_SSN` variable**

Delete the line (around line 99):
```bash
# DELETE:
E2E_SSN="123-45-$(( RANDOM % 9000 + 1000 ))"
```

**Step 2: Remove the `"documents"` field from the `CREATE_BODY` printf block**

The `CREATE_BODY` currently contains:
```bash
printf '{
  ...
  "documents": [{"country": "USA", "type": "SSN", "identifier": "%s"}]
}' "$E2E_EMAIL" "$E2E_SSN"
```

After removal, it becomes:
```bash
printf '{
  "type": "INDIVIDUAL",
  "firstName": "E2E",
  "lastName": "Tester",
  "middleName": "Integration",
  "emails": [{"primary": true, "email": "%s", "type": "PERSONAL"}],
  "phones": [{"type": "MOBILE", "countryCode": "+1", "number": "5550001234"}],
  "addresses": [{"type": "HOME", "line1": "1 Test Ave", "city": "Chicago", "state": "IL", "postalCode": "60601", "country": "USA"}]
}' "$E2E_EMAIL"
```

Note: The `printf` drops from two `%s` tokens to one, and `"$E2E_SSN"` is removed from the argument list.

---

## Task 14: Infrastructure Scripts — load-test.sh

**Files:**
- Modify: `infra/load-test.sh`

**Step 1: Remove the SSN search line from the header comment block**

Find and delete (around line 11 in the header):
```bash
#   GET    /api/v1/customers/search?ssn=
```

The `make_customer` function body already has no `documents` — no functional change needed.

**Step 2: Commit infra script changes**

```bash
git add infra/e2e-test.sh infra/load-test.sh
git commit -m "chore: remove SSN from e2e and load test scripts"
```

---

## Task 15: Documentation — CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

**Step 1: Update Architecture section — remove Document from domain record description**

Find:
```markdown
domain record `Customer` (with nested `Address`, `Email`, `Phone`, `Document` records)
```

Replace with:
```markdown
domain record `Customer` (with nested `Address`, `Email`, `Phone` records)
```

**Step 2: Update Important Patterns section**

Find:
```markdown
**Package-private nested records**: `Email`, `Phone`, `Address`, `Document` are package-private inside `org.observability.otel.domain`.
```

Replace with:
```markdown
**Package-private nested records**: `Email`, `Phone`, `Address` are package-private inside `org.observability.otel.domain`.
```

**Step 3: Update Merged Features table — search endpoints row**

Find:
```markdown
| `95e935e` | Search endpoints — `GET /customers/search?email=` and `?ssn=` |
```

Replace with:
```markdown
| `95e935e` | Search endpoint — `GET /customers/search?email=` |
```

---

## Task 16: Documentation — README.md

**Files:**
- Modify: `README.md`

**Step 1: Remove `?ssn=` row from the API endpoint table**

Find and delete the row:
```markdown
| GET /customers/search?ssn=<ssn>   | Search by SSN |
```
(exact text may vary — find the SSN search row)

**Step 2: Update Domain Model section**

Remove any reference to the `Document` record or SSN storage from the domain model description.

**Step 3: Remove SSN from example curl commands**

Remove the curl example for `?ssn=` search if present.

---

## Task 17: Documentation — architecture.html

**Files:**
- Modify: `architecture.html`

Seven targeted find-and-replace operations:

| Location | Find | Replace |
|----------|------|---------|
| ~line 546 | `Email and SSN search endpoints backed by GIN indexes` | `Email search endpoint backed by GIN index` |
| ~line 552 | `GIN indexes on JSONB for fast email and SSN lookups` | `GIN index on JSONB for fast email lookup` |
| ~line 689 | `nested value records: \`Address\`, \`Email\`, \`Phone\`, \`Document\`` | `nested value records: \`Address\`, \`Email\`, \`Phone\`` |
| ~line 845 | `` `GET /customers/search?email=` and `?ssn=` `` | `` `GET /customers/search?email=` `` |
| ~line 1100 | `search by email/SSN` | `search by email` |
| ~line 1123 | `findByEmail\` and \`findBySSN\` — JSONB path queries` | `` `findByEmail` — JSONB path query`` |
| ~line 1238 | `fast email and SSN lookups` | `fast email lookup` |

**Step 2: Commit all documentation changes**

```bash
git add CLAUDE.md README.md architecture.html
git commit -m "docs: remove all SSN/Document references from documentation"
```

---

## Task 18: Full Regression Test Suite

**Step 1: Detect container runtime and export env vars**

```bash
if docker info &>/dev/null 2>&1; then
  unset DOCKER_HOST TESTCONTAINERS_RYUK_DISABLED
elif PODMAN_SOCK=$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}' 2>/dev/null) \
     && [ -S "$PODMAN_SOCK" ]; then
  export DOCKER_HOST="unix://$PODMAN_SOCK"
  export TESTCONTAINERS_RYUK_DISABLED=true
else
  echo "ERROR: no container runtime" >&2
fi
```

**Step 2: Run the full test suite**

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test 2>&1 | tail -30
```

Expected output:
```
[INFO] Tests run: NNN, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

The count will be lower than the pre-change 221 — this is correct because SSN tests were deleted. Verify the exact delta matches the number of tests removed.

**Step 3: Audit — confirm zero SSN/Document references remain in source**

```bash
grep -rn --include="*.java" --include="*.sql" --include="*.sh" \
  -i "ssn\|findBySSN\|\.documents\b\|Document\b\|identifier" \
  src/ infra/ CLAUDE.md README.md architecture.html
```

Expected: zero matches (or only false-positive matches like "patch document" in javadoc — verify each).

**Step 4: Final commit if any files modified after tests**

```bash
git status
# If clean:
git log --oneline -5
```

**Step 5: Push the branch**

```bash
git push -u origin refactor/remove-ssn-document
```

---

## Verification Checklist

- [ ] `V1_2_0` migration file created and correctly named
- [ ] `Document` record deleted from `Customer.java`
- [ ] `documents` field removed from `Customer` record
- [ ] `findBySSN` deleted from repository, service
- [ ] `ssn` param removed from `CustomerAPI` and `CustomerController`
- [ ] `searchCustomer` accepts only `email`, enforced by `@NotBlank`
- [ ] All SSN service/controller unit tests deleted
- [ ] Email-only search validation tests updated
- [ ] `shouldFindCustomerBySSN` deleted from repository and integration tests
- [ ] `documents(...)` removed from `createFullCustomer()` in test data provider
- [ ] `E2E_SSN` and `"documents"` removed from `e2e-test.sh`
- [ ] SSN comment removed from `load-test.sh` header
- [ ] CLAUDE.md, README.md, architecture.html updated
- [ ] `mvn test` green with 0 failures
- [ ] Zero SSN/Document references in grep audit
- [ ] Branch pushed to origin
