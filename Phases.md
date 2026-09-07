# Phases

Roadmap for the from-scratch build, and where things actually stand. Update the status marker as
stages complete — this file exists specifically so a session that starts cold can tell what's done
without re-reading the whole conversation history.

| # | Stage | Status |
|---|---|---|
| 0 | Skeleton — parent POM, empty `common-lib` | ✅ Done |
| 1 | `location-service` — one CRUD service, zero infra | ✅ Done |
| 2 | Config Server — externalise config, `native` profile | ✅ Done |
| 3 | Eureka — service discovery | ✅ Done |
| 4 | `airline-core-service` + first real Feign call | ✅ Done |
| 5 | API Gateway — single entry point, routing + JWT validation filter | ✅ Done |
| 6 | Auth (`user-service`) — signup, login, JWT | ✅ Done |
| 7 | Speed-run the repeats: `pricing-service`, `ancillary-service`, `seat-service` (data model only), `flight-ops-service` | ✅ Done |
| 8 | Circuit breakers (Resilience4j) on the real Feign calls that now exist | ✅ Done |
| 9 | Kafka + the booking/payment saga (`booking-service`, `payment-service`) | ✅ Done |
| 10 | `notification-service` — pure Kafka consumer | ✅ Done |
| 11 | Harden — seat concurrency, real exception handling, idempotency, N+1 fixes, tests | ✅ Done |
| 12 | Further hardening — transactional outbox, Actuator/Micrometer, Testcontainers, distributed tracing | ✅ Done |
| 13 | Role-based authorization — IDOR fix on bookings, catalog-management role gates | ✅ Done |
| 14 | Multi-passenger bookings — Passenger/Ticket entities, seat-release compensation | ✅ Done |
| 15 | Aircraft and Airport entities — Flight now routes by airport, FlightInstance gets an aircraft | ✅ Done |
| 16 | Airline-ownership authorization — an owner can only manage their own airline's data | ✅ Done |
| 17 | Saga compensation for payment-initiation failure — a booking is cancelled, not stuck, if payment can't start | ✅ Done |
| 18 | Real notification delivery — booking confirmation sends an actual email via MailHog | ✅ Done |
| 19 | FareRules + BaggagePolicy — first of the structural/domain-gap arc closing the course-code comparison | ✅ Done |
| 20 | SeatMap + CabinClass + Seat — second of the arc; SeatInstance now backed by a real seat catalog | ✅ Done |
| — | Frontend | ⬜ Not started at all |

## Currently running (local dev)

Infra: MySQL (single container, `locationdb`, one database per service), `config-server` (8888),
`service-registry`/Eureka (8761), Kafka (standalone container, KRaft mode, port 9092), Zipkin
(standalone container, port 9411, UI at `http://localhost:9411/zipkin/`).

| Service | Port |
|---|---|
| `api-gateway` | 5000 (the only port a real client should ever call) |
| `user-service` | 5001 |
| `airline-core-service` | 5003 |
| `location-service` | 5004 |
| `pricing-service` | 5005 |
| `ancillary-service` | 5006 |
| `seat-service` | 5007 |
| `flight-ops-service` | 5008 |
| `booking-service` | 5009 |
| `payment-service` | 5010 |
| `notification-service` | none — no web server, no REST API, just a Kafka consumer |

All 9 REST business services registered with Eureka using their **IP address**, not hostname (see
"Eureka hostname gotcha" below) — required for `api-gateway`'s reactive load balancer to resolve
them at all. `notification-service` deliberately doesn't register — nothing ever looks it up.

Auth flow fully verified through the gateway: no token → 401, `/auth/login` → works without a
token and returns a JWT, valid token → request proxied through with `X-User-Id`/`X-User-Email`/
`X-User-Roles` headers attached, invalid/malformed token → 401. Nothing downstream reads those
headers yet — that's real future work once something needs a role check.

Circuit breakers verified with a real failure test, not just compiled: killed `location-service`,
watched `airline-core-service`'s calls to it go Closed (slow, ~1.1s, real attempt + fallback) →
Open (fast, ~0.08s, fallback only, no wasted attempts) after 5 failures, then restarted
`location-service` and watched it recover to Closed (fast, real data again) automatically.

## Immediate next steps

**Stage 11 (harden) is fully done** — seat concurrency (pessimistic locking on seat hold), real
exception handling (`ResourceNotFoundException`/`EmailAlreadyRegisteredException`/
`InvalidCredentialsException`, plus a real pre-existing bug fix: `user-service`'s
`.anyRequest().authenticated()` was blocking Spring Boot's own internal `/error` dispatch and
turning every error response into a flat 403), idempotent Kafka consumers, the N+1 Feign bulk-
lookup fix, and 38 new service-layer unit tests across all 9 REST services. Full detail on each is
in `CLAUDE.md`'s "Hard-won lessons" section — kept short here since it's settled history now.

**Stage 12 (further hardening) is underway.** First up, the transactional outbox pattern, added to
`payment-service` and `booking-service` — this fixes a *different* gap than the one named below:
not "a synchronous Feign call fails mid-saga" (that's still open, see Known deliberate gaps) but
the dual-write hazard where `confirmPayment` used to commit `status=SUCCESS` to its own DB and then
*separately* publish `PaymentCompletedEvent` to Kafka — two unrelated systems, no shared
transaction. A crash or Kafka outage between those two steps meant the event was gone forever with
no error anywhere, even though the DB permanently said SUCCESS. Same shape existed in
`booking-service`'s `PaymentEventConsumer` (save CONFIRMED, then separately publish
`BookingConfirmedEvent`). Fixed by writing the event as an `OutboxEvent` row in the same
`@Transactional` method as the business update, then relaying unpublished rows to Kafka via a
`@Scheduled(fixedDelay = 3000)` `OutboxRelay` per service that only marks a row published after
Kafka actually acknowledges it (blocking on the send future — `KafkaTemplate.send()` doesn't throw
synchronously on broker failure, a real gotcha this surfaced). A relay crash or Kafka outage
mid-send just leaves the row unpublished for the next poll — exactly why the idempotent consumers
built in Stage 11 had to exist.

Verified live, the way this project verifies things that matter: stopped the Kafka container,
confirmed a payment — it still returned 200 and the DB updated immediately (the transaction is
genuinely independent of Kafka), while the downstream booking correctly stayed PENDING (event
undelivered, not silently dropped). Restarted Kafka; the next relay poll delivered the event with
no re-confirm ever called, and the booking flipped to CONFIRMED / seat to BOOKED exactly like the
normal happy path. Under the old code that event would have been lost permanently the instant
Kafka became unreachable. Also unit-tested (`OutboxRelayTest` per service): successful publish
marks the row published, a failed publish leaves it unpublished without crashing the batch, and
one failure doesn't block the rest of the batch from relaying.

Actuator + Micrometer are also done, added to all 12 services with a web server (everything except
`notification-service`, which deliberately has none). Shared exposure config
(`health,info,metrics`, full health detail) lives in `config-repo/application.yml` like every other
cross-service setting. Two real findings along the way, both from checking jar contents directly
rather than trusting older docs: Spring Boot 4 moved the entire Health API to a new module and
package (`org.springframework.boot.health.contributor`, not `org.springframework.boot.actuate.
health`); and Spring Boot 4.0.2 ships no built-in Kafka health contributor at all, so a broken
Kafka connection would leave `/actuator/health` reporting UP while the outbox relay and every
consumer were actually degraded. Fixed with a small custom `KafkaHealthIndicator` in
`payment-service`/`booking-service` (the two producers) — verified live: reports the real cluster
ID when Kafka's up, flips to DOWN when it's stopped. First version used try-with-resources and hung
30+ seconds on a Kafka outage despite a 3-second `describeCluster()` timeout, because `Admin`'s
default `close()` waited on an in-flight connection attempt; explicit `admin.close(Duration.
ofSeconds(2))` in a `finally` block dropped that to ~5s.

Testcontainers is also done: `SeatInstanceConcurrencyTest` now races its 15 threads against a real,
disposable MySQL spun up via `@Testcontainers`/`@Container`/`@ServiceConnection` (Spring Boot's
modern integration — no manual `@DynamicPropertySource`), instead of the shared dev `seat_db`.
Verified as a genuine improvement, not just a swap: a full test run left zero rows in the real dev
database (previously needed a manual cleanup query after every run), and Testcontainers' Ryuk
reaper removed the container automatically once the JVM exited. Re-ran the same negative control as
the original test (revert `findByIdForUpdate` to plain `findById`) against the container and got
the identical failure (10 of 15 threads "won") — the migration preserved the test's actual
regression-catching power. Hit one real gotcha along the way: Testcontainers 2.x renamed its
modules with a `testcontainers-` prefix (`testcontainers-junit-jupiter`, `testcontainers-mysql`),
not the bare names most tutorials still reference — using the old names fails at POM-parsing,
before compilation even starts. Confirmed by reading the actual `testcontainers-bom` pom rather
than guessing.

Distributed tracing closes out Stage 12. Micrometer Tracing + `spring-boot-starter-zipkin` added to
`api-gateway` and every service touched by either the synchronous Feign chain or the async Kafka
saga (the 9 REST business services plus `notification-service`, which needs no web server to
participate in a Kafka consumer's trace span). `management.tracing.sampling.probability: 1.0`
(trace everything) lives in the shared config — fine for a learning project, never for production.
Zipkin runs as a standalone container, same pattern as Kafka/MySQL.

Verified by pulling full span trees from Zipkin's API, not just confirming spans exist somewhere.
Two complete, correctly-nested traces proven:
- **The synchronous half**: one `POST /api/bookings` through the gateway produced a single trace
  spanning `api-gateway` → `booking-service` → `pricing-service`/`seat-service`/`payment-service`
  (each Feign call wrapped in its own `circuit-breaker` span from Resilience4j).
- **The async half**: `payment-service`'s Kafka `PRODUCER` span for `payment.completed` correctly
  parents `booking-service`'s `CONSUMER` span in a different JVM; `booking-service`'s
  `booking.confirmed` publish correctly fans out to **two sibling consumer spans** —
  `seat-service` and `notification-service` — both children of the same producer span, proving
  Kafka's one-topic-many-consumer-groups fan-out preserves trace context identically for every
  subscriber.

Three real bugs found and fixed getting there, all by checking actual behavior/jars rather than
assuming a framework "just handles it":
1. `spring-boot-starter-opentelemetry` and `spring-boot-starter-zipkin` must never be combined —
   they pull in two different, competing Micrometer Tracing bridges (OTel vs. Brave). For "export
   to Zipkin," the Zipkin starter alone is correct and sufficient.
2. OpenFeign doesn't get tracing instrumentation just because an `ObservationRegistry` bean exists
   — `spring-cloud-starter-openfeign` doesn't pull in `feign-micrometer`, the artifact that actually
   reads/writes trace headers on Feign's HTTP client. Without it, `pricing-service`, `seat-service`,
   and `payment-service` each started a disconnected new trace instead of continuing the caller's.
   Fixed by adding `io.github.openfeign:feign-micrometer` explicitly to every Feign-calling service.
3. `api-gateway`'s route table never had entries for `booking-service`/`payment-service` — added in
   Stage 9, after the gateway's routes were written in Stage 5, and nobody had called either through
   the gateway since. Both APIs were unreachable through the only intended public entry point until
   tracing needed a real gateway request to verify against. Fixed by adding the missing routes.

One genuine, non-obvious architectural finding, not a bug: the transactional outbox inherently
breaks trace continuity across the async hop it protects. `OutboxRelay` is `@Scheduled`, so it has
no incoming request to inherit a trace from — every poll starts a **new** root trace. "User confirms
payment" and "seat gets booked" can never share one trace ID; the whole point of the outbox is to
decouple the publish from the original request, and that decoupling is exactly what breaks
continuity. What's fully traceable: the synchronous portion of one request, and each async hop from
its own relay-poll trace onward. A truly continuous saga-wide trace would need trace context stored
in the outbox row itself and manually restored on relay — real future work, not something
Micrometer Tracing does automatically once an outbox sits in the path.

**Stage 12 is now fully done.**

Also added since: JaCoCo, caught as a real gap (none of the test work above had any coverage
reporting at all). Declared once in the root parent POM's `<build><plugins>` — not
`<pluginManagement>`, where the other two build plugins live — specifically so every child module
inherits it automatically without editing 15 individual poms. `mvn test` now produces a real HTML
report per service (`target/site/jacoco/index.html`) right after tests run. Spot-checked the
numbers rather than just confirming the plugin ran: `seat-service`'s service package is at 85%
instruction / 100% branch coverage, `flight-ops-service` sits lower (47%) with the gap concentrated
in its controller layer and enrichment fallback paths — consistent with the Stage 11 decision to
test the service layer only, not a sign the plugin is misconfigured.

The Stage 9 saga verified end-to-end: `POST /api/bookings` (booking-service, Feign → pricing-service
for the real price, Feign → payment-service to initiate a PENDING payment) →
`POST /api/payments/{id}/confirm` (payment-service, simulated confirmation — no real payment
gateway) → publishes `PaymentCompletedEvent` → booking-service consumes it, flips the booking to
CONFIRMED, publishes `BookingConfirmedEvent` → seat-service consumes it, flips the seat to BOOKED.
Payment confirmation is simulated by a direct API call for now (no real gateway), by explicit scope
decision — revisit only if a real payment integration becomes part of the learning goals.

Stage 10 (`notification-service`) verified end-to-end too: the same `BookingConfirmedEvent` that
flips the seat to BOOKED is also consumed by `notification-service`, which just logs a simulated
"sending confirmation" line — no database, no REST API, no Eureka registration, since nothing ever
needs to call it or discover it. It stays alive purely because the Kafka listener container's
threads are non-daemon.

## Stage 13 — Role-based authorization

Two gaps closed, both flagged as "known deliberate gaps" in every prior stage until now.

**IDOR fix**: `GET /api/bookings/{id}` previously returned any booking to any authenticated caller
— zero ownership check. `Booking`/`BookingDto` gained a `userId` field, stamped from the gateway's
`X-User-Id` header at creation time (`createBooking(BookingDto, Long requesterId)`). `getBookingById`
now takes the requester's ID and role and throws `ForbiddenException` (403) unless the caller is the
booking's owner or holds `ROLE_SYSTEM_ADMIN`.

**Catalog-management role gates**: seven `create*` endpoints across six services now check
`X-User-Roles` before writing, using the existing `Role` enum from `user-service`
(`ROLE_CUSTOMER`/`ROLE_AIRLINE_OWNER`/`ROLE_SYSTEM_ADMIN`):
- Admin-only (org-wide reference data): `POST /api/cities` (`location-service`),
  `POST /api/airlines` (`airline-core-service`).
- Owner-or-admin (day-to-day catalog work): `POST /api/fares` (`pricing-service`),
  `POST /api/ancillaries` (`ancillary-service`), `POST /api/seat-instances` (`seat-service`),
  `POST /api/flights` and `POST /api/flight-instances` (`flight-ops-service`).

`seat-service`'s `holdSeat` was deliberately left ungated — it's an internal Feign call from
`booking-service`, not catalog management, and gating it would have broken the booking flow for
every role including customers who are supposed to be able to book seats.

Implementation follows the pattern already established for `ResourceNotFoundException`: a new
per-service `ForbiddenException` (`@ResponseStatus(FORBIDDEN)`), duplicated across all seven
services rather than centralized in `common-lib` (same reasoning as every other duplicated
exception — `common-lib` is DTOs only). Each gated service method took on an extra
`String requesterRole`/`Long requesterId` parameter sourced from a new `@RequestHeader` on the
controller; no `@RestControllerAdvice`, no Spring Security method annotations — plain `if` checks,
consistent with how `ResourceNotFoundException` was done in Stage 11.

13 new/renamed unit tests added across `CityServiceTest`, `AirlineServiceTest`, `FareServiceTest`,
`AncillaryServiceTest`, `SeatInstanceServiceTest`, `FlightServiceTest` (entirely new create-endpoint
coverage — none existed before), and `BookingServiceTest` (owner-succeeds, admin-succeeds-without-
ownership, non-owner-non-admin-forbidden). Full reactor `mvn test` confirmed BUILD SUCCESS with zero
regressions.

Verified live through the gateway with three real signed-up-and-logged-in users (one per role), not
just unit tests: all seven create endpoints returned the exact expected status per role
(403/201 as designed), and the booking IDOR check was confirmed end-to-end — created a booking as
one customer, fetched it as a different customer (403), as `ROLE_SYSTEM_ADMIN` (200, ownership
bypassed correctly), and as the original owner (200). Test users, bookings, seats, and catalog rows
created during verification were cleaned up from the shared dev database afterward.

**Known limitation, carried forward, not fixed by this stage**: every backend service is still
directly reachable on its own port, bypassing `api-gateway` entirely. The whole authorization model
here trusts `X-User-Id`/`X-User-Roles` headers *as forwarded by the gateway* — a request that skips
the gateway and hits a service directly could set those headers to anything. Closing this requires
a network-level boundary (e.g. only the gateway's IP allowed to reach service ports) that this
learning project hasn't built yet.

## Stage 14 — Multi-passenger bookings

Closed the biggest structural simplification flagged when comparing against the original course
codebase (`Backend Original/`): a booking was one row = one seat = an anonymous traveler, with no
captured identity and no travel document. Two new entities, both scoped to `booking-service` (not
`common-lib` — a passenger's identity here belongs to this booking record, not a Feign contract):

- **`Passenger`** — `firstName`, `lastName`, `dateOfBirth`, `gender`, `passportNumber`,
  `nationality`, `seatInstanceId` (replaces `Booking.seatInstanceId` — the seat now belongs to the
  passenger, not the booking). Real `@ManyToOne` back to `Booking`, `cascade = ALL,
  orphanRemoval = true`.
- **`Ticket`** — `ticketNumber` (deterministic: `"TKT" + zero-padded passenger id`), `status`,
  `issuedAt`. `@OneToOne` to `Passenger`, kept separate rather than adding fields to `Passenger`
  directly, since a ticket only exists once the booking is confirmed — issued inside
  `PaymentEventConsumer.onPaymentCompleted`, reusing the existing idempotency guard (redelivery is
  already a no-op there, so ticket issuance got that protection for free).

`Booking.amount` changed from a flat fare price to `fare.price × passengers.size()`.

**Seat-release compensation added alongside it, not deferred.** Multi-passenger bookings hold one
seat per passenger in a loop, which makes a partial failure (passenger 3's seat already taken)
much more likely to actually happen — and until now `seat-service` had no way to undo a hold at
all, so passengers 1 and 2's seats would've been stuck `HELD` forever. Added
`POST /api/seat-instances/{id}/release` (idempotent for `AVAILABLE`, refuses to touch `BOOKED` via
a new `SeatAlreadyBookedException`), and `BookingService.holdAllSeatsOrRollback` now releases every
seat already held for an attempt before rethrowing on failure. This closes the *multi-seat
partial-hold* case specifically — it does **not** close the larger, pre-existing saga-compensation
gap (a seat still gets stuck `HELD` if payment initiation itself fails after every hold already
succeeded), which remains real future work.

One real bug found only by live end-to-end testing, not by the (passing) mocked unit tests:
changing `OutboxEvent.seatInstanceId: Long` to `seatInstanceIds: List<Long>` (needed so
`BookingConfirmedEvent` could carry every passenger's seat) required `@ElementCollection`, which
defaults to `FetchType.LAZY`. `OutboxRelay.relayUnpublishedEvents()` isn't itself `@Transactional`,
so by the time `relayOne`'s own transaction opened, the entity was already detached — every relay
attempt threw `LazyInitializationException` and silently retried forever, never actually
succeeding. A pure Mockito test couldn't have caught this since it mocks away Hibernate entirely.
Fixed with `@ElementCollection(fetch = FetchType.EAGER)` (correct here specifically because it's a
handful of scalar IDs always needed with the row, not a large relationship). Restarting
`booking-service` picked up and relayed the stuck row automatically on the next poll — a clean live
demonstration of the outbox pattern's own retry guarantee.

Verified live end-to-end through the gateway: a 2-passenger booking correctly doubled the fare
amount, held both seats, and — after confirming payment — issued one ticket per passenger and
flipped both seats to `BOOKED` (fixing the bug above along the way). Separately verified the
rollback path: a booking with one available seat and one already-`BOOKED` seat returned 409, and
the available seat was confirmed back to `AVAILABLE` rather than left stuck. All test data cleaned
up afterward. Full reactor `mvn test` confirmed `BUILD SUCCESS` with new coverage for
`releaseSeat` (happy path, idempotent-on-`AVAILABLE`, throws-on-`BOOKED`), the multi-seat hold/
rollback paths in `BookingServiceTest`, and multi-seat marking in seat-service's
`BookingConfirmedEventConsumerTest`.

## Stage 15 — Aircraft and Airport entities

Closed the second-biggest structural gap from the course-code comparison: flights routed by whole
City, and there was no fleet concept at all behind an airline.

- **`Airport`** (`location-service`) — `iataCode`, `name`, real `@ManyToOne` to `City` (same
  service, same database, unlike `Airline.headquartersCityId` which is a plain cross-service Long).
  Admin-only to create, same rule as `City`. `AirportDto` lives in `common-lib` (nesting `CityDto`,
  mirroring `AirlineDto`) since `flight-ops-service` needs it over Feign.
- **`Aircraft`** (`airline-core-service`) — `registrationNumber`, `model`, `manufacturer`,
  `totalSeats`, `status` (new `AircraftStatus` enum), real `@ManyToOne` to `Airline`. Owner-or-admin
  to create, same rule as `Flight`/`Fare`/`Ancillary` — an airline manages its own fleet.
- **`Flight.departureCityId`/`arrivalCityId` became `departureAirportId`/`arrivalAirportId`** — a
  straight replacement (a flight departs from a specific airport, not an entire city), mechanical
  since the field was already a plain cross-service Long, not a JPA relationship.
- **`FlightInstance` gained an optional `aircraftId`** (which tail number operates this instance),
  enriched via a new `AircraftClient` using the same bulk-lookup-by-distinct-ids pattern as every
  other N+1 fix already in this codebase.

Two real bugs found only by actually starting the services, neither visible in `mvn test-compile`
or the (passing) mocked unit tests:
1. Adding a second `@FeignClient` pointed at `airline-core-service` (`AircraftClient`, alongside
   the pre-existing `AirlineClient`) made `flight-ops-service` fail to boot — Spring Cloud OpenFeign
   registers one internal bean per Feign client keyed by `name`, and two clients sharing a `name`
   collide. Fixed with `contextId = "aircraftClient"` on the second client.
2. The new `/api/airports`/`/api/aircrafts` gateway routes 404'd even after both downstream services
   were confirmed up — because the gateway's own `application.yml` had been edited but the gateway
   process itself was never restarted to pick it up (unlike `config-repo` changes, a local
   `application.yml` edit has no live-refresh path at all).

Verified live end-to-end through the gateway: created an airport (admin-gated) and an aircraft
(owner-gated), then a real `Flight` referencing the new airport and a `FlightInstance` referencing
the new aircraft — both came back correctly enriched, and bulk `GET /api/flight-instances` batched
the aircraft lookup into one call while correctly leaving `aircraft: null` for pre-migration
instances that never had one assigned. All test data cleaned up afterward. Full reactor `mvn test`
confirmed `BUILD SUCCESS` with new coverage for `AirportServiceTest`, `AircraftServiceTest`, and
updated `FlightServiceTest` cases for airport-based enrichment and aircraft assignment.

## Stage 16 — Airline-ownership authorization

Closed a gap named in every prior stage's "known deliberate gaps" list: `ROLE_AIRLINE_OWNER` could
manage *any* airline's catalog data, not just their own, because `Airline` had no owner at all.

**`Airline` gained `ownerId`**, supplied explicitly in the create request body (not stamped from
the requester header, since only admins can create airlines — the admin creating one is never its
owner). Every owner-gated `create*` method across 5 services now takes a `requesterId` alongside
`requesterRole`, gated by a small duplicated `requireAirlineOwnership` helper (admin bypasses, owner
must match `airline.getOwnerId()`).

Two different shapes of fix depending on whether a resource already had a path to its airline:
- **Cheap** — `Aircraft` (airline-core-service, same-service relationship) and `Flight`/
  `FlightInstance` (flight-ops-service, already calling airline-core-service for enrichment) needed
  no new dependency at all.
- **Real new work** — `Fare` (pricing-service) and `SeatInstance` (seat-service) had no path to an
  airline (only a `flightId`/`flightInstanceId`), so each gained a brand-new Feign dependency on
  `flight-ops-service` purely to resolve ownership. `Ancillary` (ancillary-service) had no path to
  *anything* — a flat, unscoped catalog row — so it gained a real `airlineId` field (matching the
  original course design) plus a new Feign dependency on `airline-core-service`.

These new ownership-check Feign clients deliberately have **no fallback**, same reasoning as
`booking-service`'s `PricingClient`/`PaymentClient`: a fake "here's some airline" fallback would
either silently let an unauthorized write through or silently block a legitimate owner. If the
dependency is unreachable, the write now fails loudly instead of degrading.

One new technique, not used anywhere else in the codebase: `pricing-service`'s `FlightClient`
declares a narrower `FlightOwnerView` (`{id, airline}`) instead of consuming flight-ops-service's
full `FlightDto` — relying on Spring Boot's default lenient Jackson deserialization to silently
drop the extra JSON fields (`flightNumber`, both airports) it doesn't need. Every prior Feign client
matched its endpoint's real response type exactly; this is the first deliberately-partial
projection.

Verified live with two separate airline owners through the real gateway, not just mocked unit
tests: admin created "Airline A" (owned by user 12) and "Airline B" (owned by user 13); Owner A
succeeded creating an aircraft/flight/flight-instance/fare/seat-instance/ancillary under Airline A
but got 403 attempting the identical action under Airline B; Owner B got 403 touching any of Airline
A's resources; admin succeeded on Airline A's resources despite not being its owner. All six
owner-gated resource types confirmed in one pass. Full reactor `mvn test` confirmed `BUILD SUCCESS`
with new forbidden/admin-bypass test coverage across all five affected services' test suites.

**Known limitation, not fixed by this stage**: `Airline.ownerId` is admin-supplied and completely
unvalidated — nothing checks the assigned user actually exists or holds `ROLE_AIRLINE_OWNER`.

## Stage 17 — Saga compensation for payment-initiation failure

Closed the last named saga-compensation gap: Stage 14's `holdAllSeatsOrRollback` only handled a
failure *during* the seat-holding loop, before the booking row exists. It never covered the next
failure point — `paymentClient.initiatePayment` throwing *after* every seat was already held and
the booking already persisted `PENDING` — which used to leave both the booking and every one of its
seats stuck forever with no automatic recovery.

Small fix precisely because Stage 14 had already built the piece it needed: `initiatePayment
OrCancelBooking` catches any failure from the payment call, releases every passenger's seat via the
same `releaseSeatQuietly` helper the multi-seat rollback already uses, flips the booking to
`CANCELLED`, persists that, then rethrows. No new exception type — the existing "fail loudly on a
money-critical call" convention (no fallback on `PaymentClient`) already means this surfaces as a
plain 500, which is correct here.

Verified live by stopping `payment-service` for real: attempted a booking through the gateway while
it was down (500, as expected), then confirmed directly in the database that the seat returned to
`AVAILABLE` and the booking was `CANCELLED` with `payment_id NULL` rather than stuck `PENDING`.
Restarted `payment-service`, waited for Eureka's registry cache to catch up, and confirmed a fresh
booking succeeded normally on the next attempt. Full reactor `mvn test` confirmed `BUILD SUCCESS`
with 2 new `BookingServiceTest` cases covering single- and multi-passenger compensation.

**Known limitation, not fixed by this stage**: the compensation itself is best-effort — if the
`releaseSeat` call fails during compensation (seat-service also down, say), that seat is left stuck
`HELD` with no retry, the same unsolved edge the multi-seat rollback already had.

## Stage 18 — Real notification delivery

Closed the last item from the original course-code comparison: `notification-service` previously
just logged a line on booking confirmation. It now sends a real email via SMTP, verified against
MailHog (a local, no-credentials-needed SMTP catcher) rather than a real inbox.

**Bigger than "add a mail dependency" because `BookingConfirmedEvent` never carried a recipient at
all.** The gateway already forwards `X-User-Email`, so `booking-service` reads it for free at
booking creation — but it has to survive from that original HTTP request all the way to
`PaymentEventConsumer.onPaymentCompleted`, an unrelated async Kafka consumer invocation. That meant
persisting it: `Booking.userEmail` (stamped like `userId`), threaded through
`OutboxEvent.customerEmail` → `BookingConfirmedEvent.customerEmail` → notification-service's new
`EmailService`. Three services touched for what looked at first like a one-service change.

**MailHog runs as a standalone container**, same pattern as Kafka/MySQL/Zipkin — accepts any SMTP
connection with no auth, captures every send instead of delivering it, and exposes a REST API to
inspect what was "sent." Chosen specifically to avoid needing real Gmail credentials.

`EmailService` skips sending (with a warning log) if `customerEmail` is missing, and
`BookingConfirmedEventConsumer` catches any send failure so an SMTP outage can't crash the Kafka
listener — same "one failure shouldn't take down the whole handler" shape already used elsewhere.
Redelivery still has no dedup in `notification-service` (unchanged since Stage 10, now extended
from "duplicate log line" to "duplicate email") — an accepted trade-off given its no-database
design, not a new gap.

Verified live with a full real saga: created a booking through the gateway (confirmed `userEmail`
correctly stamped from the JWT), confirmed the payment, and polled MailHog's REST API until the
confirmation email appeared with the correct `From`, the real customer's `To`, the right subject
(`Booking Confirmed - #<id>`), and correct body content. Full reactor `mvn test` confirmed
`BUILD SUCCESS`, with `notification-service` getting real test coverage (`EmailServiceTest`,
`BookingConfirmedEventConsumerTest`) for the first time — it had zero tests before this stage.

## Stage 19 — FareRules + BaggagePolicy (structural/domain-gap arc, part 1 of 4)

First stage of a larger arc closing the remaining structural/domain gaps from the course-code
comparison, targeted at making this project's backend a genuine flagship piece — not just resilience
patterns, but real domain richness too. Planned order: FareRules/BaggagePolicy (this stage) → seat/
cabin modeling → flight schedules → richer ancillary domain.

`Fare` was flat (just price/currency/cabin class) — no refund/change policy, no baggage allowance.
Added `FareRules` (`refundable`, `changeable`, `cancellationFee`, `changeFee`,
`refundDeadlineHours`, `changeDeadlineHours`) and `BaggagePolicy` (`cabinBaggageAllowanceKg`,
`checkedBaggageAllowanceKg`, `checkedBaggagePieces`, `extraBaggageFeePerKg`), matching the original
course design's intent without copying its padding.

Modeled as optional nested fields on `FareDto`, not independent CRUD resources — same reasoning as
`Passenger`/`Ticket` on `Booking`: a baggage policy without a fare is meaningless. Real `@OneToOne`
relationships with cascade, so creating a `Fare` with both nested objects persists all three rows in
one `save()` call. Both stay nullable — a fare created without them just has `null` fields, no
invented defaults.

Since `FareDto` lives in `common-lib`, `booking-service` (which already Feign-calls
`pricing-service.getFareById`) picked up the new fields automatically with zero code changes on its
side — same jar, same class, no deserialization compatibility question the way the earlier
`FlightOwnerView` projection needed.

Verified live through the gateway: created a fare with both nested objects populated (confirmed
correct round-trip on `GET`), and a second fare with neither (confirmed both stay `null`, not
defaulted). Full reactor `mvn test` confirmed `BUILD SUCCESS`, with `FareServiceTest` gaining 2 new
cases (linked-when-provided, null-when-not-provided) on top of the existing ownership-check
coverage.

## Stage 20 — SeatMap + CabinClass + Seat (structural/domain-gap arc, part 2 of 4)

The biggest stage of the arc: `SeatInstance` previously carried `seatNumber`/`cabinClass` as
freely-typed strings with no catalog backing at all - no consistency guarantee, no validation, no
real seat map behind any of it. Closed that with three new entities in `seat-service`:

- **`SeatMap`** — one per aircraft (`aircraftId`, cross-service to `airline-core-service`), total
  row count.
- **`CabinClass`** — a tier (`ECONOMY`/`PREMIUM_ECONOMY`/`BUSINESS`/`FIRST`) claiming a row range
  within a `SeatMap`, with its own seat pitch and seats-per-row. Real `@ManyToOne` to `SeatMap`
  (same service).
- **`Seat`** — one physical row/column within a `CabinClass` (window/middle/aisle, exit row).
  `getSeatNumber()` is derived (`seatRow + columnLetter`), not stored, so it can't drift out of
  sync with its parts.

`SeatInstance.seat` now references a real `Seat` (`@ManyToOne`, same service) instead of two raw
strings — the actual point of this stage. Unlike `FareRules`/`BaggagePolicy` (nested, created
atomically with their parent), these three are independent CRUD resources with their own
controllers, the same shape as `Aircraft`/`Airport` — a seat catalog is configured once per
aircraft, not tied to any one flight. Ownership resolved the same way as everywhere else: walk the
chain to `aircraftId`, call `airline-core-service` via a new no-fallback `AircraftClient`, reusing
`AircraftDto` from `common-lib`.

Extracted a shared `AirlineOwnershipChecker` utility for the three new services rather than
duplicating the identical check three times — the established "duplicate per service" convention
was about avoiding cross-service coupling in `common-lib`, not about repeating logic within one
service's own sibling classes authored in the same stage.

Two real bugs, both only catchable live, neither visible in `mvn test-compile` or the (passing)
mocked unit tests:
1. **`ROW_NUMBER` is a reserved keyword in MySQL 8.0** (added for window functions). Hibernate's
   schema update failed to create the `seats` table with a syntax error but only logged a `WARN`,
   not a fatal error - the app "started successfully" with the table simply missing. Only surfaced
   when `SeatInstanceConcurrencyTest` (the one test reading a `SeatInstance` back with its seat
   joined) hit a live "Table doesn't exist" against the real Testcontainers MySQL. Fixed by renaming
   the field to `seatRow`.
2. **A primitive `boolean` DTO field breaks Jackson deserialization of a partial JSON object.**
   `SeatDto.exitRow` (and latently, `FareRulesDto.refundable`/`changeable` since Stage 19) failed
   with `Cannot map 'null' into type 'boolean'` the moment a request omitted the field - Jackson can
   select the Lombok all-args constructor as its creator, and a missing property becomes a `null`
   constructor argument, which throws for a primitive instead of defaulting. Fixed project-wide by
   changing every primitive `boolean` DTO field to `Boolean`, with null-safe unboxing where a real
   primitive is needed again (entity setters).

Verified live through the gateway end-to-end: created a `SeatMap` for a real aircraft, a
`CabinClass` within it, a `Seat` within that, then a `SeatInstance` referencing the seat (full
nested enrichment round-tripped correctly), exercised hold/release, and booked it through
`booking-service`'s real saga to confirm zero regression in the existing flow. Full reactor
`mvn test` confirmed `BUILD SUCCESS`, including the concurrency test, with 20 new tests across
`SeatMapServiceTest`/`CabinClassServiceTest`/`SeatServiceTest`.

## Known deliberate gaps (see `CLAUDE.md` for the full list)

- Test coverage is service-layer only — controllers and Spring Data repository interfaces aren't
  tested directly (thin pass-through and framework-generated, respectively). No test hits a real
  database except `SeatInstanceConcurrencyTest`, which genuinely needs one.
- Services are still directly reachable bypassing `api-gateway` — the trusted-header authorization
  model (Stage 13) assumes every request arrives via the gateway, but nothing enforces that at the
  network level yet.
- `booking-service`'s Feign calls to `pricing-service`/`payment-service` have no circuit-breaker
  fallback (deliberately — see `CLAUDE.md`). Payment-initiation failure now compensates (Stage 17 —
  releases seats, cancels the booking), but that compensation is itself best-effort: if the
  `releaseSeat` call fails during compensation, the seat is left stuck `HELD` with no retry.

## Hard-won lessons from this build (see `CLAUDE.md` for the full list)

- **`spring-cloud-starter-gateway` isn't in the Spring Cloud BOM anymore** — use
  `spring-cloud-starter-gateway-server-webflux`, and its routes property is
  `spring.cloud.gateway.server.webflux.routes`, not the old `spring.cloud.gateway.routes`. Silent
  failure mode: routes don't load, every request falls through to Spring's static-resource handler
  and 404s, with no error logged.
- **Eureka hostname gotcha**: on this machine, the Windows/Hyper-V hostname
  (`OptimusPrime.mshome.net`) isn't real-DNS-resolvable. Spring MVC services tolerated it; Gateway's
  reactive Netty DNS resolver did not (`UnknownHostException`). Fixed globally via
  `eureka.instance.prefer-ip-address: true` in `config-repo/application.yml`.
- **Gateway routes belong in the local `application.yml`, not `config-repo`** — routes are part of
  the gateway's structural definition (what it exposes), not environment-specific config like a
  datasource URL. Also sidesteps a real Spring Binder limitation: indexed list properties
  (`routes[0]`, `routes[1]`...) don't reliably bind when they arrive via a remote Config Server
  property source, only from a local file.
- **Resilience4j's `minimum-number-of-calls` defaults to 100** if not set explicitly — `sliding-
  window-size` alone doesn't make a breaker trip quickly. Without setting `minimum-number-of-calls`
  too (we use 5, matching a small sliding window of 10), the breaker won't evaluate failure rate
  until 100 calls have happened, making it look like it silently doesn't work during any small-scale
  test.
