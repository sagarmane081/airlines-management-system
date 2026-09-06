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
  booking-service/      bookings (Feign -> pricing-service, payment-service; Kafka producer + consumer)
  payment-service/      payments, simulated confirmation (Kafka producer)
  notification-service/ pure Kafka consumer on booking.confirmed — no REST API, no database
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
- **Kafka runs as a standalone container** (`docker run --name kafka`, KRaft mode, no Zookeeper),
  not part of docker-compose — same reasoning as the MySQL container. `KAFKA_ADVERTISED_LISTENERS`
  must be `localhost:9092` (not the container's internal address), same class of fix as the Eureka
  hostname issue, since our services run on the host, not inside Docker.
- **The saga's Kafka consumers are now idempotent against redelivery.** Kafka is at-least-once,
  not exactly-once — a rebalance or slow offset commit can redeliver the same message. Fixed with
  the standard idempotent-consumer pattern: check current state before acting, skip if the
  transition already happened (`PaymentEventConsumer.onPaymentCompleted` and seat-service's
  `BookingConfirmedEventConsumer` both return early if the entity is already in the target
  terminal state, instead of unconditionally re-saving and republishing). `payment-service`'s
  `confirmPayment` got the same guard so a duplicate REST call is also a no-op. This works cleanly
  *because* every event here maps to one deterministic terminal state (PENDING→CONFIRMED,
  AVAILABLE→BOOKED) — it would NOT be sufficient for an event with a cumulative effect (e.g. "add
  $10 to balance"), which needs a real dedup-by-event-ID store instead. `notification-service` is a
  known, deliberate exception: it has no database by design, so a redelivered event still produces
  a duplicate log line — acceptable for a simulated log statement, would need a real dedup
  mechanism if it ever sent an actual notification.
- **Proving idempotency needed a different technique than the concurrency test** — forcing genuine
  Kafka redelivery live (via a relaxed producer-side guard + real HTTP calls) produced logs that
  were impossible to read reliably: Hibernate's own dirty-checking silently skips a no-op UPDATE
  regardless of any app-level guard, and async consumer/producer logs interleave unpredictably.
  Switched to a plain Mockito unit test per consumer (`PaymentEventConsumerTest`,
  `BookingConfirmedEventConsumerTest`) that calls the `@KafkaListener` method directly, twice, with
  the same event, and asserts `repository.save(...)` and `producer.publish(...)` were each invoked
  exactly once — no Spring context, no real Kafka, deterministic. Verified as a real negative
  control the same way as the concurrency test: temporarily removing the guard made the test fail
  reproducibly before restoring it.
- **Not-found and business-rule errors now map to real HTTP statuses, not bare 500s.** Every
  service that had `.orElseThrow(() -> new RuntimeException(...))` for a missing entity now throws
  a per-service `com.services.exception.ResourceNotFoundException` (`@ResponseStatus(NOT_FOUND)`) —
  same minimal technique as `SeatNotAvailableException`/`SeatUnavailableException` from the
  concurrency work, deliberately duplicated per service rather than centralized in `common-lib`
  (same reasoning as duplicating Feign clients/fallbacks: `common-lib` is DTOs only, no business
  logic, and each service stays independently deployable). `user-service` additionally got
  `EmailAlreadyRegisteredException` (409, for duplicate signup) and `InvalidCredentialsException`
  (401, for bad login) — previously both were also a bare `RuntimeException` → 500. No
  `@RestControllerAdvice` was needed anywhere; `@ResponseStatus` on the exception class is
  sufficient and matches the pattern already proven working.
- **`user-service`'s `SecurityConfig` was silently turning every error response into a 403**,
  including the pre-existing bare-500 case, and had been since Stage 6 — nobody had tested a
  failure path (bad password, duplicate email) directly against the service until this pass.
  `.anyRequest().authenticated()` also gates Spring Boot's own internal `/error` forward (the
  dispatch it uses to render whatever status code an exception produced) — since `/error` was never
  in the `permitAll` list, that internal dispatch got treated as an unauthenticated request and
  denied with `Http403ForbiddenEntryPoint`, masking the real status (401, 409, or the old 500) with
  a plain 403. Fixed by adding `/error` alongside `/auth/**` in `permitAll()`. Confirmed via
  `logging.level.org.springframework.security=DEBUG`, which showed the exact sequence: `Securing
  POST /auth/login` → passes → `Securing GET /error` → `Http403ForbiddenEntryPoint: ... Rejecting
  access`. Any Spring Security config with a catch-all `.anyRequest().authenticated()` needs `/error`
  explicitly permitted, or every error response through that filter chain silently becomes 403.
- **Seat booking now uses pessimistic locking (`SELECT ... FOR UPDATE`), not optimistic.**
  `SeatInstanceRepository.findByIdForUpdate` (`@Lock(LockModeType.PESSIMISTIC_WRITE)`) plus a
  `@Transactional` `SeatInstanceService.holdSeat` — the lock is only meaningful for the life of one
  transaction, so the read-check-write has to happen inside a single `@Transactional` method, not
  split across separate repository calls (which would each get their own transaction/connection by
  default and release the lock before the check-then-act completed). `POST /api/seat-instances/{id}/hold`
  atomically flips AVAILABLE → HELD or throws `SeatNotAvailableException` (409). Chosen over
  optimistic locking (`@Version`) because seat booking is exactly the high-contention, low-cardinality
  case pessimistic locking is for — one specific row, multiple people racing for it at once — and
  blocking-then-correct beats fail-after-the-fact-then-retry here.
- **`booking-service` now actually calls `seat-service` to hold the seat** (`SeatClient`, no Feign
  fallback — same reasoning as `PricingClient`/`PaymentClient`: a fake "seat held" response would be
  actively dangerous, not gracefully degraded) — closing a real gap where it previously trusted a
  client-supplied `seatInstanceId` with zero interaction with seat-service at all.
- **`spring.cloud.openfeign.circuitbreaker.enabled=true` wraps *every* exception from a Feign call**
  — including a legitimate 4xx client response, not just infra failures — in
  `NoFallbackAvailableException` when no fallback bean exists for that client. Catching the concrete
  exception type directly (e.g. `FeignException.Conflict`) silently never fires; you have to catch
  `NoFallbackAvailableException` and check `getCause()`. Bit `booking-service`'s seat-conflict
  handling: the 409 from `seat-service` was arriving fine, but the `catch (FeignException.Conflict)`
  never matched, so it fell through to a bare 500 instead of translating to a clean 409.
- **The seat-concurrency fix is proved by `SeatInstanceConcurrencyTest`** (seat-service's first and
  currently only test) — 15 threads race `holdSeat` on the same seat row against the real MySQL
  database (no mocking, no Testcontainers — same dev DB every service already uses), asserting
  exactly 1 success and 14 `SeatNotAvailableException`s. Verified as a real negative control, not
  just a passing assertion: swapping `findByIdForUpdate` back to plain `findById` made the same test
  fail reproducibly (10 of 15 threads "won"), confirming the test actually catches the regression it
  claims to.
- **Not every service needs `spring-boot-starter-web` or Eureka.** `notification-service` is a
  pure Kafka consumer — nothing ever calls it via REST or Feign, so it has no controller, no
  database, and deliberately skips both `spring-boot-starter-web` and the Eureka client dependency.
  It still starts and stays up because `spring-kafka`'s listener container runs on non-daemon
  threads, which is enough to keep the JVM alive with no embedded server at all. Don't reflexively
  copy the web+Eureka dependency block onto a service just because every other service has it —
  check whether anything actually needs to call in or be discovered first.
- **Spring Boot 4.0 split its autoconfiguration into per-technology modules** — the old trick of
  just adding `spring-kafka` as a bare library dependency and getting `KafkaTemplate`/`@KafkaListener`
  autoconfigured for free no longer works: `spring-boot-autoconfigure` no longer contains a
  `kafka` package at all. The dependency is now `org.springframework.boot:spring-boot-starter-kafka`
  (there's a matching `spring-boot-kafka` autoconfigure module it pulls in). Silent failure mode
  without it: a `@KafkaListener` method compiles fine but never actually registers a listener (no
  `@EnableKafka` gets activated), and a `KafkaTemplate<String, Object>` injection point throws
  `UnsatisfiedDependencyException` at startup — both look like ordinary misconfiguration, not "wrong
  artifact." Same modularization likely applies to other add-on techs going forward — if a starter
  used to "just work" by adding the raw client library, check for a dedicated `spring-boot-starter-*`
  first before assuming an autoconfiguration bug.
- **The Kafka saga's producer→consumer wiring is eventually consistent with Eureka's registry
  cache** — a service that just started may not yet see another service that registered moments
  earlier (Eureka's client-side registry fetch interval, ~30s), causing a real, transient
  `NoFallbackAvailableException`/503 on the very first Feign call after a fresh boot. Not a bug;
  retrying after the registry catches up resolves it. This is also why `payment-service` and
  `booking-service`'s Feign clients (`PricingClient`, `PaymentClient`) deliberately have **no**
  fallback, unlike every other Feign client in the codebase: those calls determine money (a real
  price, a real payment attempt), so a graceful placeholder-DTO fallback would silently let a
  booking through with a fake price or a payment that was never actually initiated. Failing loudly
  (503 → no fallback → 500) is the correct behavior here — enrichment calls (city/airline names)
  get a placeholder fallback, financial calls do not.
- **The N+1 Feign call pattern on list endpoints is fixed via bulk lookup endpoints, not caching.**
  `airline-core-service.getAllAirlines` and `flight-ops-service.getAllFlights`/
  `getAllFlightInstances` used to make one Feign call per row (up to 3 per row for flights: airline
  + departure city + arrival city). Fixed by extending each "get all" endpoint to also accept an
  optional `ids` query param on the SAME path (`GET /api/cities?ids=1,2,3`,
  `GET /api/airlines?ids=1,2`) backed by a `findAllByIdIn` repository method, and having the calling
  service collect all the *distinct* IDs it needs across the whole list, make exactly one bulk
  Feign call, and map results back with a `Map<Long, Dto>` — regardless of how many rows are being
  enriched. `getAllFlightInstances` needed one extra step: dedupe the underlying `Flight` entities
  by ID first (multiple instances commonly share one flight) before batch-enriching, otherwise the
  final `Collectors.toMap` step throws on the duplicate key. Confirmed via Hibernate's SQL log
  showing a single `where id in (?,?,?)` per list call instead of N separate `where id=?` calls.
  Bulk fallbacks were added to every existing Feign fallback class too (`ids.stream().map(this::
  getXById).toList()`), reusing the single-lookup fallback rather than duplicating the placeholder
  logic.
- **Broader unit test coverage added across all 9 REST services**, deliberately scoped to the
  service layer only (not controllers or repositories), mocking every collaborator — repository,
  Feign client, `PasswordEncoder`/`JwtUtil` for `user-service` — so every test is hermetic: no DB,
  no Spring context, no real Kafka, runs in milliseconds. Each plain CRUD service got the same
  shape: create (happy path), getById (happy + `ResourceNotFoundException`), getAll. Services with
  Feign dependencies got extra coverage for what's actually novel there —
  `AirlineServiceTest`/`FlightServiceTest` assert the bulk-lookup batching happens exactly once per
  dependency regardless of row count (the N+1 fix, now regression-tested, not just manually
  verified via SQL logs), `BookingServiceTest` directly exercises the `NoFallbackAvailableException`
  unwrapping gotcha (raw `FeignException.Conflict`, wrapped-by-breaker, and an unrelated-cause case
  that must rethrow rather than swallow), and `PaymentServiceTest` gets a dedicated test for the
  idempotency guard that was previously only verified live. `AuthServiceTest` mocks `JwtUtil`
  entirely rather than exercising the real one, specifically to avoid `JwtConstant`'s static
  `JWT_SECRET` env-var read at class-load time — keeps the test runnable regardless of how or where
  it's invoked, not dependent on `.env` being sourced first.
- **Git Bash on Windows mangles Unix-style absolute-path arguments** (like `/tmp/...` or
  `/opt/kafka/...`) passed to `docker run`/`docker exec`, silently rewriting them as Windows paths
  before Docker ever sees them — MSYS's automatic path conversion, not a Docker or Kafka bug.
  Prefix the command with `MSYS_NO_PATHCONV=1` whenever a docker command's arguments contain a
  Unix-style path meant to stay literal.
- **Transactional outbox added to `payment-service` and `booking-service`**, fixing a real
  dual-write hazard that's different from the saga-compensation gap `Phases.md` names (that one —
  a synchronous Feign call failing mid-workflow — is a separate, harder problem the outbox pattern
  doesn't touch). The hazard: `confirmPayment` used to (1) commit `status=SUCCESS` to its own DB,
  then (2) separately publish `PaymentCompletedEvent` to Kafka — two unrelated systems, no shared
  transaction. A crash or Kafka outage between those two steps meant the DB said SUCCESS forever
  but the event was gone forever too, with no error anywhere. Same shape existed in
  `booking-service`'s `PaymentEventConsumer` (save `CONFIRMED`, then separately publish
  `BookingConfirmedEvent`). Fixed by writing the event as an `OutboxEvent` row in the *same*
  `@Transactional` method as the business update — one database, real ACID guarantee, so both
  commit or both roll back. A separate `@Scheduled(fixedDelay = 3000)` `OutboxRelay` per service
  polls for unpublished rows and relays them to Kafka, blocking on the send future
  (`.get(5, TimeUnit.SECONDS)`, since `KafkaTemplate.send()` doesn't throw synchronously on broker
  failure — only marking a row published after Kafka actually acknowledges it. A relay crash or
  Kafka outage mid-send just leaves the row unpublished for the next poll to retry — the exact
  reason the idempotent consumers built earlier had to exist. Each service's `OutboxEvent` is typed
  to the one event shape it emits (not a generic JSON-payload-plus-type-discriminator envelope) —
  proportionate, since neither service emits more than one kind of event.
- **`KafkaTemplate.send()` returns a `CompletableFuture` that only fails asynchronously** — a
  `try/catch` around the call itself does not see broker-level failures (only immediate
  serialization errors would throw synchronously). `PaymentEventProducer`/`BookingEventProducer`
  had to start returning the future so `OutboxRelay` could block on it (`.get(timeout)`) and know
  for certain whether the send actually succeeded before marking the outbox row published.
  Fire-and-forget publishing (the old code) always "succeeds" immediately regardless of whether
  Kafka is even reachable — fine for a producer with no durability requirement, actively wrong for
  a relay whose entire job is to know when delivery truly happened.
- **Verified live, not just unit-tested**: stopped the Kafka container, confirmed a payment — it
  still returned 200 SUCCESS and the DB updated immediately, proving the business transaction is
  genuinely independent of Kafka's availability. The downstream booking stayed PENDING (event
  correctly undelivered, not silently dropped) for as long as Kafka was down. Restarted Kafka; the
  next `OutboxRelay` poll (within 3s) delivered the event with no re-confirm call ever made, and the
  booking flipped to CONFIRMED / seat to BOOKED exactly as the original happy path does. This is
  the actual point of the pattern — under the old code, that event would have been lost permanently
  the moment Kafka became unreachable, with no error raised anywhere to reveal it.
- **Actuator + Micrometer added to every service with a web server** (all 12 except
  `notification-service`, which deliberately has none — Actuator's HTTP endpoints need a web stack,
  and adding one just for this would contradict its Stage 10 design). Exposure config
  (`management.endpoints.web.exposure.include: health,info,metrics`,
  `management.endpoint.health.show-details: always`) and `info.app.name` live in the shared
  `config-repo/application.yml`, same as every other setting common to all services. Micrometer
  itself needs no separate dependency — it's bundled with `spring-boot-starter-actuator` and starts
  collecting real data (JVM, HikariCP pool, HTTP request timings) the moment the starter is on the
  classpath; `/actuator/metrics/{name}` surfaces it directly, no Prometheus/Grafana needed to get
  the learning value. Deliberately skipped `micrometer-registry-prometheus` for now — that's a
  separate, later addition if a real dashboard is ever wanted.
- **Spring Boot 4 moved the entire Health API to a new module and package**: not
  `org.springframework.boot.actuate.health` (the answer most tutorials and AI training data would
  give) but `org.springframework.boot.health.contributor`, in a dedicated `spring-boot-health`
  artifact — confirmed by inspecting jars directly, since `spring-boot-actuator-4.0.2.jar` itself
  contains zero classes with "Health" in the name. `HealthIndicator`/`AbstractHealthIndicator`
  otherwise work exactly as the pre-4.0 versions did (`doHealthCheck(Health.Builder)`); only the
  import path changed. Same modularization pattern as Kafka and Security before it — check the
  actual jar contents before assuming a class still lives where older docs say it does.
- **Spring Boot 4.0.2 ships no built-in Kafka health contributor** — confirmed by inspecting both
  `spring-boot-actuator-autoconfigure` and `spring-boot-kafka` jars directly, neither contains a
  Kafka-related health class. Unlike the DataSource health indicator (which auto-registers and
  correctly reported `db: UP`), a broken Kafka connection would leave `/actuator/health` reporting
  UP even though the outbox relay and every consumer are actually degraded. Fixed with a small
  custom `KafkaHealthIndicator` (`payment-service`, `booking-service` — the two Kafka producers)
  using `KafkaAdmin.getConfigurationProperties()` to build a real `Admin` client and call
  `describeCluster()`. Verified live: reports `UP` with the real cluster ID when Kafka is up, and
  correctly flips to `DOWN` when the container is stopped.
- **`Admin.close()` (no-arg) can block far longer than any timeout on the operation it's closing
  after** — the first version of `KafkaHealthIndicator` used try-with-resources (which always calls
  the no-arg `close()`), and stopping Kafka made the health check hang 30+ seconds despite a 3-second
  timeout on `describeCluster()`, because closing the admin client waited on an in-flight connection
  attempt to the unreachable broker. Fixed by closing explicitly with `admin.close(Duration.
  ofSeconds(2))` in a `finally` block instead of try-with-resources — dropped the failure-path
  response time to ~5s. A health check that takes 30s to report unhealthy defeats the purpose of
  having one.
- **`SeatInstanceConcurrencyTest` now runs against a real, disposable MySQL via Testcontainers**
  instead of the shared dev database (`seat_db` on the `locationdb` container). `@Testcontainers` +
  `@Container static MySQLContainer<?> mysql` + `@ServiceConnection` — the modern Spring Boot 3.1+
  integration — wires the container's JDBC URL/credentials into `DataSourceAutoConfiguration`
  automatically, no manual `@DynamicPropertySource` needed, and it takes priority over whatever
  config-server would otherwise supply. Confirmed as a genuine improvement, not just a swap: the
  real dev `seat_db` had zero rows touched after a full test run (previously needed a manual
  `DELETE FROM seat_instances WHERE flight_instance_id = -1` cleanup step after every run), and the
  container is fully removed automatically by Testcontainers' Ryuk reaper once the JVM exits — no
  dangling containers left behind either. Re-ran the exact same negative control as before
  (temporarily reverting `findByIdForUpdate` to plain `findById`) against the container and got the
  identical failure (10 of 15 threads "won") — proof the migration preserved the test's actual
  regression-catching power, not just its "builds and passes" status.
- **Testcontainers 2.x renamed its JUnit/database modules with a `testcontainers-` prefix** —
  `testcontainers-junit-jupiter` and `testcontainers-mysql`, not the `junit-jupiter`/`mysql`
  artifact IDs that most tutorials, StackOverflow answers, and AI training data still reference.
  Using the old names fails at the Maven POM-parsing stage with "version is missing" (since the
  version, managed by the imported `testcontainers-bom`, is only registered under the new
  artifactIds) — not a compile error, a build-can't-even-start error. Confirmed by grepping the
  actual `testcontainers-bom-2.0.3.pom` for the real artifact names rather than guessing from
  memory. Versions for all of `spring-boot-testcontainers`, `testcontainers-junit-jupiter`, and
  `testcontainers-mysql` come from the BOM chain already imported (`spring-boot-dependencies` →
  `testcontainers-bom`) — no explicit `<version>` needed, consistent with every other dependency in
  this project.
- **Distributed tracing (Micrometer Tracing + Zipkin) added to `api-gateway` and every service that
  participates in either the synchronous Feign chain or the async Kafka saga** — the 9 REST business
  services plus `notification-service` (worth doing even with no web server: Kafka trace context
  propagation only needs Micrometer Tracing on the classpath, not a web stack, so it can complete
  the saga trace at its final hop without contradicting its Stage 10 "no web server" design).
  Skipped `config-server`/`service-registry` deliberately — config fetch happens once at startup,
  before any request-scoped trace context exists, so instrumenting them traces nothing useful.
  `management.tracing.sampling.probability: 1.0` (sample everything) lives in the shared
  `config-repo/application.yml` — a deliberate learning-project choice, never appropriate in
  production, where it would mean tracing overhead and storage cost scale with every single request.
  Zipkin runs as a standalone container (`docker run -p 9411:9411 openzipkin/zipkin`), same pattern
  as Kafka/MySQL.
- **`spring-boot-starter-opentelemetry` and `spring-boot-starter-zipkin` must not both be added
  together** — confirmed by reading the resolved dependency tree, not assuming. The OTel starter
  pulls in `micrometer-tracing-bridge-otel` plus an OTLP exporter (for shipping to an OTel
  Collector); the Zipkin starter pulls in a *different*, incompatible bridge
  (`micrometer-tracing-bridge-brave`, Zipkin's native tracer) plus the classic Zipkin reporter. Two
  competing Micrometer Tracing bridge implementations on the classpath at once is not a supported
  combination. For "export traces to Zipkin," `spring-boot-starter-zipkin` alone is correct and
  sufficient — it already brings Brave + the Zipkin reporter, exactly matching the classic
  `management.zipkin.tracing.endpoint` property this project uses.
- **Kafka trace propagation across service boundaries genuinely works** — confirmed by pulling the
  full span tree for a real trace from Zipkin's API (`GET /api/v2/trace/{id}`), not just checking
  that spans exist somewhere. `payment-service`'s `payment.completed send` (PRODUCER span) correctly
  parents `booking-service`'s `payment.completed process` (CONSUMER span) in a *different JVM*, and
  `booking-service`'s `booking.confirmed send` correctly fans out to **two sibling CONSUMER spans**
  — `seat-service` and `notification-service` — both children of the same producer span, proving
  Kafka's one-topic-many-consumer-groups fan-out preserves trace context identically to every
  subscriber. This needed `spring.kafka.template.observation-enabled: true` and
  `spring.kafka.listener.observation-enabled: true` explicitly — confirmed via `javap` on
  `KafkaProperties$Template`/`$Listener` that these are real, correctly-named fields, not a guess.
- **The transactional outbox pattern inherently breaks trace continuity across the async hop it
  protects** — a real, non-obvious architectural interaction between two patterns built in the same
  stage, not a bug. `OutboxRelay.relayUnpublishedEvents` is `@Scheduled`, so it has no incoming
  request to inherit a trace context from; every poll starts a **new**, unrelated root trace. This
  means "user confirms payment" and "seat gets marked booked" can never appear in the same trace —
  the actual Kafka publish happens later, from a completely disconnected scheduled task, by design
  (that's the whole point of decoupling the publish from the original request for reliability). What
  *is* fully traceable end-to-end: the synchronous portion of one HTTP request (proven above,
  gateway → booking-service → pricing/seat/payment via Feign), and each async hop from its own
  outbox-relay-poll trace onward (proven above, one publish correctly fanning out to all consumers).
  Trying to force one continuous trace across the whole saga would require manually propagating and
  restoring trace context into the outbox row itself — real, legitimate future work, not something
  Micrometer Tracing does automatically once an outbox sits between the request and the publish.
- **OpenFeign does not automatically get Micrometer Tracing instrumentation just because an
  `ObservationRegistry` bean exists** — confirmed by discovering that `pricing-service`,
  `seat-service`, and `payment-service` were each starting a brand-new, disconnected trace when
  called via Feign from `booking-service`, instead of continuing the caller's trace. Root cause:
  `spring-cloud-starter-openfeign` pulls in `feign-core`/`feign-slf4j` but not `feign-micrometer` —
  the actual artifact that instruments Feign's HTTP client to read/write trace propagation headers.
  Confirmed via `mvn dependency:tree` before assuming. Adding `io.github.openfeign:feign-micrometer`
  explicitly (to every service making Feign calls: `airline-core-service`, `flight-ops-service`,
  `booking-service`; version resolved automatically via the existing BOM chain) fixed it completely
  — re-verified with a fresh request and got a single, correctly-nested five-service trace
  (`api-gateway` → `booking-service` → `pricing-service`/`seat-service`/`payment-service`, each
  Feign call wrapped in its own `circuit-breaker` span from Resilience4j's own instrumentation).
- **Fixed a real, unrelated bug found only because tracing needed a request to actually go through
  the gateway**: `api-gateway`'s route table never had entries for `booking-service` or
  `payment-service` — they were added in Stage 9, after the gateway's routes were written in Stage
  5, and nobody had called either through the gateway since. Both APIs were completely unreachable
  through the only intended public entry point until this was caught. Added the missing routes to
  `api-gateway`'s local `application.yml`, matching the existing pattern exactly.
- **JaCoCo added to the root parent POM's `<build><plugins>` (not `<pluginManagement>`)** — the one
  place in this project where that distinction actually matters. `maven-compiler-plugin` and
  `spring-boot-maven-plugin` live in `<pluginManagement>` because each child module opts in
  individually (deliberately — e.g. `common-lib` never adds `spring-boot-maven-plugin`, since it's a
  plain library, not a runnable app). JaCoCo needs the opposite: every module should get instrumented
  coverage uniformly, whether or not it has tests yet, without 15 child poms each needing to
  redeclare it. A plugin declared directly under the parent's `<build><plugins>` is inherited by
  every child automatically; one under `<pluginManagement>` is not, until a child lists it too. Two
  executions: `prepare-agent` (wires the coverage agent into Surefire) and `report`, bound to the
  `test` phase (generates `target/site/jacoco/index.html` right after tests run, no separate `mvn
  jacoco:report` step needed). Verified per-module, not just "plugin resolved": `mvn test` at the
  reactor root produced a real HTML report in all 9 services that have tests, with numbers that
  track the actual mocking choices already made — `seat-service`'s service package hit 85%
  instruction / 100% branch (concurrency + plain CRUD both covered), `flight-ops-service` sits at
  47% (its controller layer and the enrichment fallback paths are the intentionally-untested
  surface, consistent with "service layer only" from the Stage 11 test-coverage decision).
  Caught and fixed one self-inflicted mistake immediately by re-reading the file after editing: the
  first attempt at this edit left the root POM with two sibling `<properties>` blocks (should be
  one) — a genuine reminder that editing near an existing block without reading its surrounding
  structure first can silently duplicate rather than merge.

## Known gaps (in-progress build, not silently "fix")

- No backend service reads the `X-User-Id`/`X-User-Roles` headers `api-gateway` forwards — no
  role-based authorization exists, just authentication at the edge.
- `booking-service`'s Feign calls to `pricing-service`/`payment-service` have no circuit-breaker
  fallback (deliberately — see the money-critical-calls entry above), and no compensation/rollback
  exists if payment initiation fails after the booking row is already saved PENDING — that booking
  is just stuck, never cancelled automatically. This is a saga-compensation gap, not a dual-write
  hazard — the transactional outbox doesn't address it. Saga compensation is real future work.
- Test coverage is at the service layer only — controllers and Spring Data repository interfaces
  are untested (thin pass-through and framework-generated respectively, low value to cover
  directly). No test hits a real database except `SeatInstanceConcurrencyTest`, which genuinely
  needs one.

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
