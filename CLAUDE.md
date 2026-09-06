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
  api-gateway/          single entry point (5000), reactive/WebFlux, JWT validation filter
  location-service/     cities
  airline-core-service/ airlines (Feign -> location-service)
  pricing-service/      fares
  ancillary-service/    meals/baggage/insurance add-ons
  seat-service/         seat instances (no concurrency guard yet — deliberate)
  flight-ops-service/   flights + flight instances (Feign -> airline-core-service, location-service)
  user-service/         auth: signup + login/JWT issuance, both done
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
- **`api-gateway` is reactive (WebFlux/Netty), everything else is blocking (Spring MVC/Tomcat).**
  Never add `spring-boot-starter-web` to `api-gateway` — mixing the two stacks breaks startup.
- **`spring-cloud-starter-gateway` isn't in the Spring Cloud BOM** — use
  `spring-cloud-starter-gateway-server-webflux`. Its routes property is
  `spring.cloud.gateway.server.webflux.routes`, not `spring.cloud.gateway.routes`. Wrong prefix
  fails silently — no routes load, no error, every request 404s via Spring's default static-resource
  handler.
- **Gateway routes live in `api-gateway`'s own local `application.yml`, not `config-repo`** — routes
  are part of the gateway's structural definition, not environment-specific config, and indexed list
  properties (`routes[0]`, `routes[1]`...) don't reliably bind when they arrive via a remote Config
  Server property source, only from a local file.
- **`eureka.instance.prefer-ip-address: true` is mandatory** (set in the shared
  `config-repo/application.yml`) — this machine's Windows hostname isn't real-DNS-resolvable, and
  `api-gateway`'s reactive Netty DNS resolver fails hard on it (`UnknownHostException`) even though
  every blocking (Feign-based) service tolerated it fine.
- **JWT validation happens once, at the gateway** (`JwtAuthenticationFilter`, a `GlobalFilter`)
  which forwards identity as `X-User-Id`/`X-User-Email`/`X-User-Roles` headers. No backend service
  re-validates the token or reads those headers yet — that's the next layer, once something needs a
  role check.
- **Circuit breakers**: `spring.cloud.openfeign.circuitbreaker.enabled: true` (shared config)
  auto-wraps every Feign call, no per-method annotation needed. Fallbacks are duplicated per service
  (same reasoning as duplicating Feign client interfaces) and return a safe placeholder DTO for
  graceful degradation, not a thrown exception — revisit once real exception handling exists
  (Stage 11). **Always set `resilience4j.circuitbreaker.configs.default.minimum-number-of-calls`
  explicitly** — it defaults to 100 if omitted, so a small `sliding-window-size` alone won't make a
  breaker trip quickly; without it, the breaker looks like it silently doesn't work under any
  small-scale test.

## Known gaps (in-progress build, not silently "fix")

- `seat-service.SeatInstance.status` has zero concurrency safety — two requests can "book" the same
  seat right now. Deliberately deferred to its own dedicated, slow stage.
- List endpoints that enrich via Feign (`airline-core-service.getAllAirlines`,
  `flight-ops-service.getAllFlights`/`getAllFlightInstances`) make one Feign call per row — real
  N+1, deferred until it's actually slow enough to matter.
- No tests anywhere yet.
- Not-found cases everywhere throw a generic `RuntimeException` (→ bare 500), not a proper
  exception mapped to 404.
- No backend service reads the `X-User-Id`/`X-User-Roles` headers `api-gateway` forwards — no
  role-based authorization exists, just authentication at the edge.
- No circuit breakers yet on the real Feign calls that now exist.

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

# user-service and api-gateway both need JWT_SECRET — source Backend/.env first
set -a && source .env && set +a && mvn spring-boot:run -pl <service-name>

# New database inside the existing MySQL container
docker exec locationdb mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS <name>;"
```

**Startup order matters:** MySQL container → `config-server` → `service-registry` → business
services → `api-gateway` last (it needs the others already registered in Eureka to route to them).

**If you change `eureka.instance.prefer-ip-address` or anything else in the shared
`config-repo/application.yml`, every already-running service must restart** to re-register with the
new value — Eureka doesn't retroactively update existing registrations, and `api-gateway`'s own
load-balancer cache also needs a restart to see the change (it caches the registry, doesn't poll
instantly).
