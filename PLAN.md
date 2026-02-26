# Spring Boot 3.5.11 Upgrade Plan

## Branch
`spring-boot-3.5.11` off `main` (HEAD `cff0547`)

## Scope
Single pom.xml change: bump `spring-boot-starter-parent` from `3.4.2` → `3.5.11`.
The Spring Boot BOM manages most transitive dependencies (Jackson, Hibernate, Micrometer,
Testcontainers, etc.) — they update automatically.

Explicitly pinned dependencies that are **not** BOM-managed and must be reviewed:

| Dependency | Current | Action |
|---|---|---|
| `flyway-maven-plugin` / `flyway-database-postgresql` | 11.2.0 | Check if SB 3.5.x BOM pins a newer version; keep explicit pin unless build conflict |
| `hypersistence-utils-hibernate-63` | 3.9.0 | Artifact targets Hibernate 6.3 API. Already used with Hibernate 6.6 (SB 3.4). If SB 3.5 bumps Hibernate further, unit tests (mocked) are unaffected; only domain/integration tests would surface issues — and those are skipped |
| `springdoc-openapi-starter-webmvc-ui` | 2.8.4 | Compatible with SB 3.5.x; no change needed |
| `otel-instrumentation-bom` | 2.12.0 | OTel starters are commented out; no impact |
| `lombok` | 1.18.36 | Compatible with Java 21 and SB 3.5; no change needed |

## Risk Areas
- **Hibernate version jump**: If SB 3.5 upgrades Hibernate beyond 6.6, `hypersistence-utils-hibernate-63`
  may emit warnings or fail at runtime. Unit tests (Mockito-mocked repository) will not expose this —
  domain and integration tests would. Those are skipped per requirement.
- **API removals**: Spring Boot 3.5 may remove APIs deprecated in 3.3/3.4. Compilation step catches these.

## Steps
1. ✅ Create branch `spring-boot-3.5.11`
2. ✅ Write this plan
3. Edit `pom.xml` — bump parent to `3.5.11`
4. `mvn clean compile` — catch any compilation errors
5. `mvn test -Dexclude="**/integration/**,**/domain/**"` — unit + util tests only
6. Fix any failures; commit

## Test command
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test \
  -Dexclude="**/integration/**,**/domain/**"
```

Covers: `unit/` (4 classes) and `util/` (6 classes). Skips `integration/` and `domain/` (require Docker).
