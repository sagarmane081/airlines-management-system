# CLAUDE.md

## Project

Airline booking platform (GDS-style) — a from-scratch learning build, not derived from any
purchased course source. Original course material and all its code were deliberately deleted;
everything under `Backend/` was written from an empty parent POM up, one file at a time.

**Repo layout**

```
Backend/
  pom.xml               root parent POM (packaging=pom), groupId com.sagar
  common-lib/           shared DTOs only — nothing else
  config-repo/          externalised config served by config-server (native profile, no git)
  config-server/        Spring Cloud Config Server
  service-registry/     Eureka
  location-service/     cities
  airline-core-service/ airlines (Feign -> location-service)
  pricing-service/      fares
  ancillary-service/    meals/baggage/insurance add-ons
  seat-service/         seat instances (no concurrency guard yet — deliberate)
  flight-ops-service/   flights + flight instances (Feign -> airline-core-service, location-service)
  user-service/         auth: signup done, login/JWT in progress
Frontend/               not started yet
```

See `Phases.md` for what's built vs. not.

## How I want to work

I am **learning** this stack. Optimise for understanding, not for finishing fast — except where
noted below.

- **New concepts get explained before code.** JPA annotations, DI, Feign, Spring Security, JWT —
  walk through the *why* first, then give the file.
- **Repeated patterns move fast.** Once a CRUD shape (entity → DTO → repository → mapper → service
  → controller) has been explained once, later services reuse it tersely — no need to re-explain
  `@RestController` for the fifth time.
- **I sometimes type files myself, sometimes ask you to create them directly** (e.g. when stepping
  away, or during a "speed-run" batch). Either is fine — just ask if unclear which mode we're in.
- **Always verify files after I say "done."** Real, recurring mistakes so far: files saved without
  a `.java` extension, files landing in the wrong module, edits landing on the wrong service's
  `application.yml`, a new module never added to the root `pom.xml`'s `<modules>`, a service's
  `config-repo/<name>.yml` never created. Check before running, every time.

## Conventions established so far

- **Order, every time:** entity → DTO → repository → mapper → service → controller.
- **DTOs are plain POJOs**, zero JPA annotations. `@NoArgsConstructor` is required on both entities
  (Hibernate needs it for reflection-based instantiation) and DTOs (Jackson needs it for
  deserialization) — same shape, two unrelated reasons.
- **Cross-service references are plain `Long` IDs, never JPA relationships** — enforced by
  database-per-service. *Within* one service's own database, real relationships
  (`@ManyToOne` etc.) are correct and preferred — `FlightInstance.flight` is a real object
  reference, `Flight.airlineId` is a plain Long, in the same class hierarchy.
- **Money is `BigDecimal`, never `double`/`float`.**
- **Enums always use `@Enumerated(EnumType.STRING)`**, never the ordinal default — reordering the
  enum later would silently corrupt existing rows under ORDINAL.
- **Mappers are static utility classes by default.** They only become `@Component` beans with
  injected dependencies the moment they need one (e.g. a mapper that must call another service to
  enrich a DTO). Don't promote a mapper to a bean pre-emptively.
- **`common-lib` holds only DTOs genuinely shared across a service boundary** (Feign contracts) —
  package `com.common.dto`. No entities, no business logic. A DTO gets promoted into it only when
  a second service actually needs the exact same shape (`CityDto`, then `AirlineDto`).
- **`common-lib` must be reinstalled to the local repo (`mvn install -pl common-lib`) every time it
  changes**, before any service run in isolation (`-pl <module>`, no `-am`) can see the change —
  otherwise Maven silently resolves a stale cached jar. Bit us twice, including once with a jar
  left over from the deleted course project under the same coordinates.
- **The root parent POM also needs `mvn install -N`** any time it changes in a way that affects
  resolution (e.g. after the `com.zosh` → `com.sagar` groupId rename).
- **Config lives in two places, deliberately split:** each service's own `application.yml` is
  minimal — just `spring.application.name` and `spring.config.import: optional:configserver:...`.
  Everything else (port, datasource, JPA settings) lives in `Backend/config-repo/<service>.yml`,
  served by `config-server`. Settings shared by *every* service go in `config-repo/application.yml`
  (currently just the Eureka `defaultZone`).
- **`spring-cloud-starter-config` must be an explicit dependency**, not relied on transitively —
  without it, `spring.config.import: optional:configserver:...` worked non-deterministically
  (succeeded once, failed twice, identical code) rather than failing cleanly.
- **One MySQL container (`locationdb`), many databases** — new databases are created inside it via
  `docker exec locationdb mysql -uroot -proot -e "CREATE DATABASE ..."`, not one container per
  service.
- **Secrets (JWT_SECRET, etc.) come from environment variables, fail-fast if missing — never
  hardcoded.** This was literally a defect fixed in a different, now-deleted project first; built
  correctly from day one here.
- **Package convention:** `com.services` for business services, `com.cloud` for infrastructure
  (`config-server`, `service-registry`), `com.common` for `common-lib`. Maven groupId is
  `com.sagar` — unrelated to the Java package names.
- **No TDD**, by explicit decision, for speed — except the seat-concurrency work still to come,
  where a concurrency test is the only reliable way to prove a race condition is actually fixed.

## Known gaps (in-progress build, not silently "fix")

- `seat-service.SeatInstance.status` has zero concurrency safety — two requests can "book" the same
  seat right now. Deliberately deferred to its own dedicated, slow stage.
- List endpoints that enrich via Feign (`airline-core-service.getAllAirlines`,
  `flight-ops-service.getAllFlights`/`getAllFlightInstances`) make one Feign call per row — real
  N+1, deferred until it's actually slow enough to matter.
- No tests anywhere yet.
- No API Gateway yet — every service is directly reachable on its own port, nothing centralised.
- `user-service`: signup works; login/JWT issuance was mid-build when this file was written.
- Not-found cases everywhere throw a generic `RuntimeException` (→ bare 500), not a proper
  exception mapped to 404.

## Commands

```bash
# From Backend/ — check the whole reactor
mvn compile

# Whenever common-lib changes, before running anything else in isolation
mvn install -pl common-lib

# Rarely needed — after a change to the root pom.xml itself
mvn install -N

# Run one service
mvn spring-boot:run -pl <service-name>

# New database inside the existing MySQL container
docker exec locationdb mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS <name>;"
```

**Startup order matters:** MySQL container → `config-server` → `service-registry` → any business
service.
