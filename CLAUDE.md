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
  a duplicate log line — and now, since real email delivery was added, a duplicate email too. Still
  an accepted trade-off given the no-database design, not silently forgotten — see the real-email
  entry below for the current state of this gap.
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
- **Role-based authorization is a plain trusted-header check, not Spring Security method
  annotations.** `api-gateway` already validated the JWT and forwarded `X-User-Id`/`X-User-Email`/
  `X-User-Roles` as headers (`X-User-Roles` is a single role string, not a list — confirmed from
  `JwtAuthenticationFilter`'s `.header("X-User-Roles", String.valueOf(claims.get("role")))`), but no
  downstream service had ever read them until now. Each gated `create*` service method took on an
  extra `String requesterRole` (or `Long requesterId` for booking ownership) parameter sourced from
  a new `@RequestHeader` on the controller, with a plain `if` throwing a new per-service
  `ForbiddenException` (`@ResponseStatus(FORBIDDEN)`, duplicated per service like every other
  exception class — same reasoning as `ResourceNotFoundException`). Rules chosen: admin-only for
  org-wide reference data (`location-service` cities, `airline-core-service` airlines),
  owner-or-admin for day-to-day catalog writes (fares, ancillaries, seat instances, flights, flight
  instances). `seat-service`'s `holdSeat` was deliberately left ungated — it's an internal Feign call
  from `booking-service` during normal booking flow, not catalog management, and gating it would
  have broken booking for customers.
- **Fixed a real IDOR on `GET /api/bookings/{id}`** — it returned any booking to any authenticated
  caller, zero ownership check. `Booking`/`BookingDto` gained a `userId` field stamped from
  `X-User-Id` at creation (`createBooking(BookingDto, Long requesterId)`); `getBookingById(Long id,
  Long requesterId, String requesterRole)` now throws `ForbiddenException` unless the caller owns
  the booking or holds `ROLE_SYSTEM_ADMIN`. Verified live end-to-end through the gateway with three
  real users (customer/owner/admin roles, real signup+login+JWT): created a booking as one customer,
  confirmed a different customer got 403, `ROLE_SYSTEM_ADMIN` got 200 regardless of ownership, and
  the original owner got 200 — plus confirmed the response DTO's `userId` matched the header-derived
  requester ID, not a client-supplied value.
- **Known limitation, not fixed by this work**: every service is still directly reachable on its own
  port, bypassing `api-gateway`. The whole trusted-header model assumes requests arrive via the
  gateway; a request that skips it could set `X-User-Id`/`X-User-Roles` to anything. Closing this
  needs a network-level boundary (only the gateway's IP allowed to reach service ports), not yet
  built in this learning project.
- **Multi-passenger bookings: `Passenger` and `Ticket` are new entities scoped to `booking-service`,
  not `common-lib`** — a passenger's identity here belongs to this booking record, not a Feign
  contract another service reads. `Booking.seatInstanceId: Long` became
  `Booking.passengers: List<Passenger>` (`@OneToMany(mappedBy="booking", cascade=ALL,
  orphanRemoval=true)` — a passenger has no lifecycle independent of its booking), and each
  `Passenger` now carries its own `seatInstanceId` (one seat per traveler). `Ticket` is a separate
  entity (`@OneToOne` to `Passenger`) rather than fields bolted onto `Passenger`, because a ticket
  doesn't exist yet at booking creation - it's only issued later, asynchronously, when
  `PaymentEventConsumer.onPaymentCompleted` flips the booking to `CONFIRMED`. Ticket numbers are
  deterministic (`"TKT" + zero-padded passenger id`), which made the idempotency guard trivial: the
  existing `if (booking.getStatus() == BookingStatus.CONFIRMED) return;` early-return already
  prevents a redelivered `PaymentCompletedEvent` from issuing a second ticket, no extra guard needed.
  `amount` changed from a flat fare price to `fare.price * passengers.size()`.
- **Multi-seat holds need real compensation, not just clean single-seat failure.** Holding N seats
  in a loop (one Feign call per passenger) means a failure partway through (passenger 3's seat
  already taken) leaves passengers 1 and 2's seats stuck `HELD` - and until this work, `seat-service`
  had no way to undo a hold at all. Added `POST /api/seat-instances/{id}/release`
  (`SeatInstanceService.releaseSeat`, same `findByIdForUpdate` pessimistic-lock pattern as
  `holdSeat`) - idempotent for `AVAILABLE` (safe to retry), refuses to touch `BOOKED` (throws
  `SeatAlreadyBookedException`, 409) since releasing a booked seat would incorrectly free up
  inventory that's part of a confirmed booking. `holdSeat` was left ungated for role checks in
  Stage 13 precisely because it's an internal operational call, not catalog management -
  `releaseSeat` is the same category and was left ungated for the same reason.
  `BookingService.holdAllSeatsOrRollback` holds seats in order, and on any failure releases every
  seat already held for that attempt before rethrowing the original exception - verified live, not
  just in mocked tests: attempted a 2-passenger booking where seat 1 was available and seat 2 was
  already `BOOKED`, got a 409, and confirmed seat 1 (held first) correctly flipped back to
  `AVAILABLE` rather than staying stuck. This closes the *multi-seat partial-hold* case specifically
  - it does **not** close the pre-existing, larger saga-compensation gap (a seat still gets stuck
  `HELD` forever if `paymentClient.initiatePayment` itself fails after every seat hold already
  succeeded) - that remains real future work, deliberately out of scope here.
- **Real bug, only catchable live: `@ElementCollection` defaults to `FetchType.LAZY`, and
  `OutboxRelay` hands `relayOne` an already-detached entity.** Changing `OutboxEvent.seatInstanceId:
  Long` to `seatInstanceIds: List<Long>` (needed so `BookingConfirmedEvent` could carry every
  passenger's seat, not just one) required `@ElementCollection` for the list. But
  `OutboxRelay.relayUnpublishedEvents()` is not itself `@Transactional` - only the per-row
  `relayOne` is - so `findByPublishedFalseOrderByCreatedAtAsc()` returns entities that are already
  detached by the time `relayOne`'s own transaction opens. Accessing the LAZY collection inside that
  new transaction threw `LazyInitializationException: ... no session` on every single relay attempt,
  silently retried forever (by design - that's what the outbox pattern does with any relay failure)
  but never actually succeeding. A pure Mockito unit test with mocked repositories couldn't have
  caught this - `OutboxRelayTest` mocks away Hibernate entirely, so the proxy/session behavior that
  actually broke never runs. Only found via the live end-to-end multi-passenger booking test (seats
  stayed `HELD` instead of flipping to `BOOKED`, then grepping `booking-service`'s real log for the
  warning `OutboxRelay` already logs on every failure). Fixed with
  `@ElementCollection(fetch = FetchType.EAGER)` - correct here specifically because this collection
  is a handful of scalar seat IDs always needed together with the row, not a large or optional
  relationship; EAGER on a real `@OneToMany`/`@ManyToOne` to full entities would be the usual anti-
  pattern. Restarting `booking-service` after the fix picked up and relayed the still-unpublished
  row automatically on the very next poll, with no manual DB fix needed - a clean live demonstration
  of the same retry guarantee the outbox pattern was already documented as providing.
- **`Airport` (location-service) and `Aircraft` (airline-core-service) are real `@ManyToOne`
  relationships, not cross-service Longs** — unlike `Airline.headquartersCityId` (a plain Long,
  since `Airline` and `City` live in different services/databases), `Airport.city` and
  `Aircraft.airline` reference an entity in the *same* service's own database, so a genuine JPA
  relationship is correct per the established convention. This also meant `AircraftService` could
  enrich its `AirlineDto` by calling `AirlineService.getAirlineById` directly (a plain Java method
  call, same service) rather than needing a Feign client - simpler than every other cross-service
  enrichment in the codebase.
- **`Flight.departureCityId`/`arrivalCityId` became `departureAirportId`/`arrivalAirportId` - a
  straight replacement, not an addition.** A flight departs from a specific airport, not an entire
  city; since the field was already a plain cross-service Long (not a JPA relationship), the swap
  was mechanical - rename the field, swap `LocationClient.getCityById`/`getCitiesByIds` for
  `getAirportById`/`getAirportsByIds`, update `FlightMapper`/`FlightDto`. `FlightInstance` separately
  gained an optional `aircraftId` (which tail number is operating this specific instance), enriched
  via a new `AircraftClient` Feign call using the same bulk-lookup-by-distinct-ids pattern as every
  other N+1 fix in this codebase - `getAllFlightInstances` collects every non-null `aircraftId`,
  makes exactly one bulk call, and leaves `aircraft: null` for instances that don't have one
  assigned (which includes flight instances created before this migration - `ddl-auto=update` adds
  the new columns but never backfills them, so old rows correctly show `null` there and for the old
  city-based airport fields too, not a bug).
- **Two `@FeignClient` interfaces cannot share the same `name` without a `contextId` - fails at
  Spring context startup, not compile time.** Adding `AircraftClient` (`name = "airline-core-
  service"`) alongside the pre-existing `AirlineClient` (same `name`) in `flight-ops-service` made
  the service fail to boot with `The bean 'airline-core-service.FeignClientSpecification' could not
  be registered - a bean with that name has already been defined`. Spring Cloud OpenFeign registers
  one internal `FeignClientSpecification` bean per Feign client, keyed by `name` by default; two
  clients pointed at the same downstream service collide on that key even though they're otherwise
  unrelated interfaces. Fixed by adding `contextId = "aircraftClient"` to the second client -
  disambiguates the internal bean while `name` still resolves the same Eureka-registered service for
  both. Only surfaced via a real `mvn spring-boot:run`, not `mvn test-compile` or the (passing)
  Mockito unit tests - neither compiles nor mocked tests ever construct the actual Spring
  `ApplicationContext` that this bean-registration conflict lives in.
- **A local `application.yml` change (gateway routes) needs the gateway restarted to take effect -
  it doesn't come from `config-server`, so there's no live-refresh path.** Added
  `/api/airports/**`/`/api/aircrafts/**` to `api-gateway`'s route predicates but forgot to restart
  the gateway process itself; every request through it 404'd even though the downstream services
  and their new endpoints were already up and correct. Same class of gotcha as the "every already-
  running service must restart to see a shared `config-repo/application.yml` change" note below,
  just for the gateway's *own* local file instead of the shared remote one.
- **Airline-ownership authorization: `Airline` gained `ownerId`, stamped by the request body, not
  the requester header.** Unlike `Booking.userId` (stamped from `X-User-Id` since a customer books
  for themselves), `POST /api/airlines` is admin-only - the admin creating an airline is never its
  owner, so `ownerId` has to be an explicit field in the request, identifying which
  `ROLE_AIRLINE_OWNER` user the airline belongs to. Every owner-gated `create*` method across 5
  services now takes a `requesterId` alongside `requesterRole`, and a small duplicated
  `requireAirlineOwnership(airline, requesterId, requesterRole)` helper (admin bypasses, owner must
  match `airline.getOwnerId()`) gates the actual write - same "duplicate the small check per
  service" reasoning as every other cross-service exception/guard in this codebase.
- **Two shapes of ownership check, depending on whether the resource already has a path to its
  airline.** `Aircraft` (airline-core-service) and `Flight`/`FlightInstance` (flight-ops-service,
  already Feign-calling airline-core-service for enrichment) needed no new dependency - the
  ownership check just reuses a call that already existed. `Fare` (pricing-service) and
  `SeatInstance` (seat-service) had no path to an airline at all (only a `flightId`/
  `flightInstanceId`), and `Ancillary` (ancillary-service) had no path to *anything* - it was a
  flat, unscoped catalog row. Closing all three required real new work: `Ancillary` gained an
  `airlineId` field (a genuine schema change, matching the original course design), and pricing-
  service/seat-service each gained a brand-new Feign dependency on `flight-ops-service` purely to
  resolve `flightId`/`flightInstanceId` → owning airline.
- **These new ownership-check Feign clients deliberately have no fallback**, same reasoning as
  `booking-service`'s `PricingClient`/`PaymentClient`: this is a decision, not a display value - a
  fake "here's some airline" fallback would either silently let an unauthorized write through or
  silently block a legitimate owner. If `flight-ops-service`/`airline-core-service` is unreachable,
  fare/seat/ancillary creation now fails loudly (503 via `NoFallbackAvailableException`) instead of
  degrading.
- **A Feign client can request a narrower response shape than the endpoint's real return type,
  relying on Spring Boot's default lenient Jackson deserialization.** `flight-ops-service`'s
  `GET /api/flights/{id}` returns a full `FlightDto` (flight number, both airports, nested airline),
  but `pricing-service` only needs `{id, airline}` to check ownership - so its `FlightClient`
  declares a local `FlightOwnerView` with just those two fields instead of promoting the whole
  `FlightDto` to `common-lib`. This works because Spring Boot's Jackson auto-config ignores unknown
  JSON properties by default; the extra fields in the real response (`flightNumber`,
  `departureAirport`, `arrivalAirport`) are silently dropped rather than causing a deserialization
  error. First use of this narrower-projection pattern in the codebase - every prior Feign client
  matched its endpoint's response type exactly.
- **Verified live with two separate airline owners, not just unit tests**: admin created "Airline
  A" (owned by user 12) and "Airline B" (owned by user 13); Owner A could create an aircraft/flight/
  flight-instance/fare/seat-instance/ancillary under Airline A but got 403 attempting the same under
  Airline B; Owner B got 403 attempting to touch any of Airline A's resources; admin succeeded on
  Airline A's resources despite not being its owner. All six owner-gated resources confirmed in one
  pass through the real gateway.
- **Saga compensation for payment-initiation failure closes the last named saga-compensation gap.**
  `holdAllSeatsOrRollback` (Stage 14) only ever handled a failure *during* the seat-holding loop,
  before the booking row exists at all. It didn't cover the very next failure point:
  `paymentClient.initiatePayment` throwing *after* every seat was already held and the booking
  already persisted `PENDING` — until now, that left the booking stuck `PENDING` forever with every
  one of its seats stuck `HELD` forever, no automatic recovery. Fixed with
  `initiatePaymentOrCancelBooking`: catches any `RuntimeException` from the payment call, releases
  every passenger's seat via the same `releaseSeatQuietly` helper the multi-seat rollback already
  built, flips the booking to `CANCELLED`, persists that, then rethrows — small change precisely
  because Stage 14 had already built the one piece (`releaseSeatQuietly`) this needed to reuse.
  Deliberately does not introduce a new exception type for this path — the existing "fail loudly on
  a money-critical call" convention (no fallback on `PaymentClient`) already means an uncaught
  exception here correctly surfaces as a 500, and dressing that up isn't what this fix is for.
- **Verified live by stopping `payment-service` for real**, not just in mocked tests: attempted a
  booking through the gateway while it was down, got a 500 (expected — failing loudly, no
  fallback), then confirmed directly in the database that the seat was back to `AVAILABLE` (not
  stuck `HELD`) and the booking was `CANCELLED` with `payment_id NULL` (not stuck `PENDING`).
  Restarted `payment-service`, waited for Eureka's registry cache to catch up, and confirmed a
  fresh booking attempt succeeded normally on the very next try — proving the fix doesn't affect
  the happy path once the dependency recovers.
- **Real email delivery required threading a customer's email through three services, not just
  adding `spring-boot-starter-mail`.** `BookingConfirmedEvent` never carried a recipient address at
  all. The gateway already forwards `X-User-Email` (alongside `X-User-Id`/`X-User-Roles`), so
  `booking-service` reads it for free at booking creation - but the email has to survive from that
  original HTTP request all the way to `PaymentEventConsumer.onPaymentCompleted`, an unrelated Kafka
  consumer invocation that runs later. That meant persisting it: `Booking.userEmail` (stamped like
  `userId`), then threading it through `OutboxEvent.customerEmail` → `BookingConfirmedEvent
  .customerEmail` → `notification-service`'s new `EmailService`. Three services touched
  (`common-lib`, `booking-service`, `notification-service`) for what looked at first like a
  one-service change.
- **MailHog chosen over real Gmail SMTP specifically to avoid needing real credentials** - runs as a
  standalone container (`docker run -d --name mailhog -p 1025:1025 -p 8025:8025 mailhog/mailhog`),
  same pattern as Kafka/MySQL/Zipkin. Accepts any SMTP connection with no auth
  (`spring.mail.properties.mail.smtp.auth: false`), captures every send instead of delivering it,
  and exposes both a web UI and a REST API (`GET http://localhost:8025/api/v2/messages`) to inspect
  what actually got "sent" - genuine SMTP protocol exercise end-to-end without a real inbox or a
  secret in `config-repo`.
- **`EmailService` wraps a single failure per event, and `BookingConfirmedEventConsumer` wraps the
  call to it, so an SMTP outage degrades gracefully at two levels**: a missing `customerEmail`
  (old data, or a future event source that doesn't set it) just skips the send with a warning log,
  and any `RuntimeException` from the send itself (SMTP unreachable, etc.) is caught in the
  consumer and logged rather than crashing the Kafka listener - same "one failure shouldn't take
  down the whole handler" shape already used for seat-service's booking-confirmed processing.
  Redelivery still has no dedup here (a known, accepted limitation since Stage 10 for the log line;
  now the same trade-off extends to the email itself - a redelivered event sends a second copy).
- **`config-repo/notification-service.yml` didn't exist until this stage** - notification-service
  had `spring-cloud-starter-config` as a dependency from day one but nothing to fetch, since it
  previously needed zero service-specific settings. Confirmed the new file was actually being
  served correctly via `GET http://localhost:8888/notification-service/default` before assuming the
  SMTP config was reaching the service - config-server's native-profile filesystem serving picks up
  a brand-new file with no restart needed, unlike a shared `config-repo/application.yml` change,
  which does need every already-running service restarted.
- **Verified live with a full real saga, not a mocked send**: created a booking through the gateway
  (confirmed `userEmail` correctly stamped from the JWT), confirmed the payment, and polled
  MailHog's REST API until the confirmation email appeared - correct `From`, correct `To` (the real
  customer email, not a placeholder), correct subject (`Booking Confirmed - #<id>`), and correct
  body content (booking ID, flight instance, seat numbers). Cleaned up via MailHog's own
  `DELETE /api/v1/messages` alongside the usual database cleanup.
- **`FareRules`/`BaggagePolicy` are "detail" attachments, not independent resources — no separate
  CRUD endpoints, same reasoning as `Passenger`/`Ticket` on `Booking`.** A baggage policy without a
  fare is meaningless, so both are optional nested fields on `FareDto`, created atomically in one
  `POST /api/fares` call. Real `@OneToOne` relationships: `Fare` holds `mappedBy` references with
  `cascade = ALL, orphanRemoval = true`, `FareRules`/`BaggagePolicy` each own the `@JoinColumn` back
  to `Fare` — saving the `Fare` cascades both children automatically, no extra repository calls
  needed, identical shape to `Booking` → `Passenger` → `Ticket`. Both stay nullable on `FareDto`; a
  fare created without them just has `fareRules: null, baggagePolicy: null` — no defaults invented.
  Kept the relationship-linking logic (`fareRules.setFare(fare); fare.setFareRules(fareRules);`) in
  the service layer, not the mapper — same split already used for `Passenger`/`Booking`, keeps
  `FareMapper` a pure field-mapping utility.
- **`FareDto` (and its two new nested DTOs) live in `common-lib`, so `booking-service` — which
  already Feign-calls `pricing-service.getFareById` — automatically gains the new fields with zero
  changes on its side.** Not a projection like `FlightOwnerView`; both sides share the exact same
  class from the exact same jar, so there's no deserialization-compatibility question to verify.
- **Seat catalog modeling: `SeatMap` (per-aircraft layout) → `CabinClass` (a tier's row range within
  it) → `Seat` (one physical row/column) → `SeatInstance` now references a real `Seat` instead of
  freely-typed `seatNumber`/`cabinClass` strings.** All three new entities are same-service
  relationships (`CabinClass → SeatMap`, `Seat → CabinClass`), so real `@ManyToOne`s; `SeatMap`
  itself carries `aircraftId` as the one cross-service Long, matching `Airport`/`Aircraft`'s
  established split. `SeatInstance.seat` is nullable (no `nullable=false`) specifically so the
  existing `SeatInstanceConcurrencyTest` didn't need to seed a full catalog chain just to test
  locking — it only cares about the `status` column.
- **`SeatMap`/`CabinClass`/`Seat` are independent CRUD resources (their own controllers), unlike
  `FareRules`/`BaggagePolicy`.** The distinguishing question: does this thing make sense without
  its parent, and does an airline owner configure it separately from any one flight? A seat catalog
  is set up once per aircraft, independent of any particular flight instance - closer to
  `Aircraft`/`Airport` than to `Passenger`/`Ticket`. Ownership resolved by walking each entity's
  chain to `aircraftId` and calling `airline-core-service` (reusing `AircraftDto`, already in
  `common-lib` from Stage 15 - no new projection needed), via a new no-fallback `AircraftClient` in
  seat-service.
- **Extracted `AirlineOwnershipChecker` as a small shared static utility, not three copies.** The
  established "duplicate the ownership check per service" convention was about avoiding a shared
  *cross-service* dependency in `common-lib`; it was never a mandate to duplicate identical logic
  *within* one service's own sibling classes. `SeatMapService`/`CabinClassService`/`SeatService` all
  need the exact same check, authored together in the same stage - sharing it locally is the more
  consistent choice, the same way `FlightService` already reuses one ownership-check method across
  its two `create*` methods rather than duplicating it a second time in the same class.
- **Real, only-catchable-live bug: `ROW_NUMBER` is a reserved keyword in MySQL 8.0** (added for
  window functions in 8.0). `Seat.rowNumber` mapped to a column literally named `row_number`, and
  Hibernate's `ddl-auto: update` schema generation failed to `CREATE TABLE seats (...)` with a
  syntax error - but logged it as a `WARN`, not a fatal startup error, so the app started
  "successfully" with the `seats` table simply never created. Every other new table
  (`cabin_classes`, `seat_maps`, plus the `seat_id` FK column added to the pre-existing
  `seat_instances`) was created fine in the same pass, which made this look like an entity-scanning
  problem rather than a keyword collision at first. Only surfaced when `SeatInstanceConcurrencyTest`
  (the one test that actually reads a `SeatInstance` back with its `@ManyToOne Seat` joined) hit a
  live "Table 'test.seats' doesn't exist" against the real Testcontainers MySQL - a Mockito unit
  test would never construct real DDL and couldn't have caught it. Fixed by renaming the field to
  `seatRow` (also arguably the better name). Worth remembering for any future column name: MySQL
  8.0's window-function keywords (`ROW_NUMBER`, `RANK`, `DENSE_RANK`, `LEAD`, `LAG`, `NTILE`, `OVER`,
  and a few more) are now reserved and will silently break schema generation the same way.
- **Real, latent Jackson/Lombok bug, also only surfaced live: a primitive `boolean` field on a DTO
  with both `@NoArgsConstructor` and `@AllArgsConstructor` breaks deserialization of a partial JSON
  object.** `SeatDto.exitRow` (and `FareRulesDto.refundable`/`changeable`, latent since Stage 19 but
  never triggered because every prior test happened to supply both booleans) failed with `Cannot
  map 'null' into type 'boolean'` the moment a request omitted the field - e.g. `{"seat":{"id":1}}`
  when creating a `SeatInstance`, supplying only the reference id. Root cause: Jackson can select a
  Lombok all-args constructor as its deserialization creator instead of no-args-plus-setters, and a
  JSON property absent from a partial object becomes a `null` argument at that constructor
  position - which throws immediately for a primitive parameter instead of leaving it at its Java
  default. Fixed by changing every primitive `boolean` DTO field project-wide to `Boolean`, with
  null-safe unboxing (`Boolean.TRUE.equals(dto.getX())`) in the one direction (DTO → entity) that
  needs a real primitive again. Entities themselves keep primitive `boolean` - they're never
  JSON-deserialized directly, only built through mappers, so they were never at risk.
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

- Services are still directly reachable bypassing `api-gateway` — the trusted-header authorization
  model (role gates + booking-ownership check + airline-ownership check) assumes every request
  arrives via the gateway, but nothing enforces that at the network level. A request straight to a
  service's own port could set `X-User-Id`/`X-User-Roles` to anything.
- `Airline.ownerId` is admin-supplied and unvalidated — nothing checks that the assigned owner is
  actually a real user, let alone one with `ROLE_AIRLINE_OWNER`. An admin could assign ownership to
  a nonexistent or wrongly-roled user ID with no error.
- `booking-service`'s Feign calls to `pricing-service`/`payment-service` have no circuit-breaker
  fallback (deliberately — see the money-critical-calls entry above). Payment-initiation failure now
  compensates (releases seats, cancels the booking — see the saga-compensation entry above), but
  that compensation is still best-effort: if the `releaseSeat` call itself fails during
  compensation, the seat is left stuck `HELD` with no retry, the same unsolved edge as the
  multi-seat rollback's own best-effort release.
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
