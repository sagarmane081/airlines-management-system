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
| 21 | FlightSchedule — third of the arc; recurring patterns that materialize real FlightInstances | ✅ Done |
| 22 | Richer ancillary domain — fourth and final piece of the arc; Meal/FlightAncillary/FlightMeal give per-flight pricing | ✅ Done |
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

## Stage 21 — FlightSchedule (structural/domain-gap arc, part 3 of 4)

`FlightSchedule` describes a recurring pattern for a `Flight` - "AI101 operates Mon/Wed/Fri,
10:00–12:00 local, Jan 1 through Jan 14." Deliberately not the same thing as a `FlightInstance`
(one real, bookable occurrence): a schedule doesn't auto-generate instances on save. A separate
`POST /api/flight-schedules/{id}/generate-instances` walks every date in the range whose day of
week matches `operatingDays` and materializes one `FlightInstance` per match.

Generation is idempotent by construction - a per-date existence check before each insert means
re-running it (after widening a schedule's range, or just retrying) only creates what's missing,
same discipline as every other idempotent write in this codebase. Unlike the original course
design, `FlightSchedule` doesn't duplicate the flight's airports - a schedule is a pattern for an
*existing* flight that already fixes the route, so it only carries what's genuinely new: time of
day, day-of-week pattern, and a validity date range.

Extracted a second `AirlineOwnershipChecker` (flight-ops-service's own, mirroring seat-service's
Stage 20 one) and refactored `FlightService` to use it instead of its private copy - consistent
application of "sibling classes authored together share the check" now that a second service needs
it. Also made `FlightService.enrichFlight` reusable by `FlightScheduleService` directly (package-
private, not private) rather than duplicating the three-Feign-call enrichment - the same pattern
`AircraftService` already established by injecting `AirlineService` directly in airline-core-service.

**Known, accepted simplification**: `generateInstances` assumes same-day arrival - a schedule that
crosses midnight isn't handled. Documented directly in the method's own Javadoc.

Verified live through the gateway: created a schedule (Mon/Wed/Fri, Jan 1–14 2026), generated
instances and got back exactly the 6 correct dates (Jan 2, 5, 7, 9, 12, 14 - hand-verified against
the calendar, since Jan 1 2026 is a Thursday), confirmed each carries the right `flightScheduleId`
back-reference, and re-ran generation to confirm it returned empty rather than duplicating. Full
reactor `mvn test` confirmed `BUILD SUCCESS`, with 7 new tests in `FlightScheduleServiceTest`
covering day-of-week matching, the idempotency skip, and ownership gating.

## Stage 22 — Richer ancillary domain (structural/domain-gap arc, part 4 of 4 — arc complete)

The last piece of the arc. `Ancillary` was airline-wide flat: one price, applying identically
across an airline's entire fleet. Closed that gap with a two-tier shape - `Ancillary` (and the new
`Meal`) stay reusable per-airline catalog definitions; `FlightAncillary`/`FlightMeal` are join
entities carrying the real, per-flight bookable price and availability. Same pattern `Fare` already
established (a flat catalog entity feeding a specific bookable instance), now applied to ancillaries
and meals too.

**A same-airline consistency check runs unconditionally, even for `ROLE_SYSTEM_ADMIN`** -
deliberately separate from the ownership check next to it. `FlightAncillaryService`/
`FlightMealService` verify two different things: does the requester own the airline behind this
*flight* (ownership, admins bypass), and does the *ancillary/meal* being attached actually belong to
that same airline (a data-consistency rule an admin shouldn't be able to skip either, since
cross-wiring two airlines' catalogs is a modeling error, not a permissions question). Verified live:
admin was correctly blocked (403) attaching Airline B's meal to Airline A's flight, then the
identical request succeeded (201) once the meal actually belonged to Airline A - proving the check
fires on the real mismatch, not the role.

Deliberately skipped `InsuranceCoverage` (present in the original course design) to keep this stage
the same size as the seat-catalog stage (3 new entities, not 4) - scope discipline, not an oversight.

Third and final application of the `AirlineOwnershipChecker` extraction pattern (after seat-service
and flight-ops-service) - `AncillaryService` now shares the utility instead of its own private copy.

Verified live end-to-end through the gateway: created a `Meal` and an `Ancillary` for one airline,
attached both to a real flight with per-flight prices that correctly differed from the catalog's
base price, confirmed a different airline owner was blocked from touching the flight (403), and
confirmed the same-airline mismatch check independently (403 even for admin, 201 once corrected).
Full reactor `mvn test` confirmed `BUILD SUCCESS` with 22 new tests across `MealServiceTest`,
`FlightAncillaryServiceTest`, and `FlightMealServiceTest`.

**The structural/domain-gap arc from the course-code comparison is now complete**: FareRules +
BaggagePolicy (Stage 19) → SeatMap + CabinClass + Seat (Stage 20) → FlightSchedule (Stage 21) →
richer ancillary domain (Stage 22). Remaining smaller gaps from that original comparison (flight
search, Redis caching, JWT logout, SMS notifications, CORS) are still open - see below.

## Stage 23 — Flight search (first of the "smaller gaps")

Every earlier stage let a client fetch flights/instances by ID or list everything - there was no
way to ask "what flies from A to B on date X", the actual first thing a real traveler does. Closed
that with `GET /api/flights/search?departureAirportId=&arrivalAirportId=&date=&cabinClass=&minPrice=
&maxPrice=&sortBy=`, entirely inside `flight-ops-service` (plus one small supporting endpoint in
`pricing-service`), no new entities needed - this is a read/aggregation feature over data that
already exists.

**Design decisions:**

- **`pricing-service` gained a bulk lookup**: `GET /api/fares?flightIds=1,2,3` (reusing the existing
  `GET /api/fares` path with an optional query param, same shape as every other bulk-lookup endpoint
  in this codebase - `getAllAirlines`/`getAllFlights`'s `ids` param from the N+1 fix). Backed by a
  new `FareRepository.findAllByFlightIdIn`.
- **`flight-ops-service`'s new `FareClient` deliberately HAS a circuit-breaker fallback** (returns
  `List.of()`), unlike `PricingClient`/`PaymentClient` in `booking-service`. Those two are money-
  critical writes where a fake fallback would be actively dangerous; this is a browse/search read -
  degrading to "prices temporarily unavailable, flight still shown" is the correct behavior, not a
  cover-up.
- **One Feign call each, not per-row**: `FlightInstanceRepository` gained a single derived query
  (`findByFlight_DepartureAirportIdAndFlight_ArrivalAirportIdAndDepartureTimeBetweenAndStatus`) to
  fetch the day's candidate instances, then exactly one bulk call to `flightService.enrichFlights`
  (already existing, reused - same package-private-reuse pattern as `FlightScheduleService`) and one
  bulk call to the new `FareClient`, regardless of how many flights matched. Same N+1-avoidance
  discipline as everywhere else in this build.
- **Result shape and filtering semantics**, deliberately reasoned through up front: `FlightSearchResultDto`
  pairs one `FlightInstanceDto` with one `FareDto` - a flight instance with two matching fares
  (e.g. ECONOMY and BUSINESS both within a price range) produces two result rows, not a nested list,
  so cabin classes are directly comparable/sortable as peers. A flight instance with **no** fare
  matching an active `cabinClass`/`minPrice`/`maxPrice` filter is excluded entirely. With **no**
  such filter active, every matching instance is still returned, with `fare: null` if it has no
  fares set up yet at all - so browsing an unpriced route isn't silently hidden by incomplete
  catalog data.
- **No gateway route change needed** - `/api/flights/**` already covers `/api/flights/search`, and
  Spring's `PathPattern` matcher prioritizes literal path segments over `{id}` path-variable
  segments regardless of declaration order. This was flagged during design as unverified and
  specifically tested live below, not just assumed.

Full reactor `mvn test` confirmed `BUILD SUCCESS`, with 8 new tests in `FlightSearchServiceTest`
covering: multi-fare row expansion, cabin-class/min-price/max-price filtering, the unpriced-flight
null-fare inclusion/exclusion split, the empty-short-circuit (`verifyNoInteractions` on both
Feign/enrichment collaborators when no instances match at all), and both sort modes.

Verified live end-to-end through the gateway: created a real city/airport pair, an airline, a
flight, one flight instance on 2026-10-15, and two fares (ECONOMY $250, BUSINESS $800). Confirmed,
in order: (1) with no fares yet, the flight still appeared with `fare: null`; (2) after adding
fares, no-filter search returned exactly 2 rows, one per cabin class; (3) `cabinClass=ECONOMY`,
`minPrice=500`, and `maxPrice=300` each correctly isolated the one matching fare; (4)
`sortBy=price` returned ascending (250 before 800); (5) a wrong date and a reversed route each
correctly returned `[]`; (6) `GET /api/flights/{id}` still resolves correctly and was not swallowed
by the new `/search` route, confirming the routing-precedence assumption above was correct in
practice, not just in theory. All test data cleaned from the shared dev database afterward.

**Known limitation**: search only matches flights with `status = SCHEDULED` on the given calendar
date by departure time - no multi-city, no return-leg, no nearby-date suggestions, no seat-
availability check (a search result can point to a flight instance with zero seats left; that's
only discovered at booking time). Real future work, not attempted here - this stage closes the
"can't search at all" gap, not the "search is as smart as a real GDS" gap.

## Stage 24 — Redis caching (second of the "smaller gaps")

The highest-traffic reads in this codebase are single-entity lookups of reference data that almost
never changes: `airline-core-service.getAirlineById` (called on *every* `create*` request across 6
services purely to run the ownership check) and `location-service.getCityById`/`getAirportById`
(called on nearly every enrichment path). Every one of those repeat requests hit the real database,
even though the underlying row is effectively static. Added Redis as a shared cache in front of
these four single-id lookups: `CityService.getCityById`, `AirportService.getAirportById`,
`AirlineService.getAirlineById`, `AircraftService.getAircraftById` - all via a plain declarative
`@Cacheable(value = "<name>", key = "#id")`, no code touches Redis directly.

**Design decisions:**

- **Redis runs as one standalone container** (`docker run -d --name redis -p 6379:6379 redis`),
  same pattern as MySQL/Kafka/Zipkin/MailHog - not one per service.
- **Confirmed the Spring Boot 4 module split applies to caching too**, before assuming anything:
  `CacheAutoConfiguration` moved from `org.springframework.boot.autoconfigure.cache` to a dedicated
  `spring-boot-cache` module (`org.springframework.boot.cache.autoconfigure`), same pattern already
  hit with Kafka, Security, and the Health API. `spring-boot-starter-cache` +
  `spring-boot-starter-data-redis` are the correct starters, and both resolve cleanly off the
  existing BOM chain with no explicit version.
- **`spring.cache.type: redis` and the Redis connection settings live in the shared
  `config-repo/application.yml`**, not per-service - verified this is safe by reading
  `CacheAutoConfiguration`'s real source rather than assuming: the whole class is gated behind
  `@ConditionalOnBean(CacheAspectSupport.class)`, which only exists once `@EnableCaching` creates it.
  So the shared setting is completely inert for every service that doesn't have `@EnableCaching` -
  same reasoning already established for Kafka's `bootstrap-servers` sitting in the same shared file
  even though most services never produce or consume anything.
- **Cache values are JSON, not JDK serialization** - a per-service `CacheConfig` bean supplies a
  `RedisCacheConfiguration` using `GenericJackson2JsonRedisSerializer`, which Spring Boot's own
  Redis cache auto-configuration picks up automatically in place of its default. Confirmed live: a
  cached `AirlineDto` reads back as plain, human-readable JSON via `redis-cli GET`, not opaque
  binary, and needed zero changes to the existing Lombok DTOs (no `Serializable` required).
- **Only single-id lookups are cached, deliberately not the bulk `getXByIds` endpoints** - those
  already collapse to one `WHERE id IN (...)` query per call, and mixing partial-cache-hits with a
  batched DB fetch would be real added complexity for what these bulk endpoints already do
  efficiently. A proportionate-scope call, not an oversight.
- **No `@CacheEvict` anywhere, and this is deliberate, not a gap** - none of the four cached
  services has an update or delete endpoint yet, so a cached entry can never actually go stale from
  a write today. Adding eviction annotations to `create*` methods would be dead code (a brand-new
  row's id was never in the cache to evict). Each `@Cacheable` method carries a comment flagging
  this explicitly: the moment an update endpoint is added for City/Airport/Airline/Aircraft, it
  *must* evict the corresponding key or reads will silently serve the pre-update value until the
  10-minute TTL backstop expires. That TTL exists precisely as a backstop for this scenario, not as
  the primary staleness guard.

Full reactor `mvn test` confirmed `BUILD SUCCESS` with zero test changes needed - `@Cacheable` is a
Spring AOP proxy concern, invisible to the existing Mockito unit tests that construct each service
directly via `new XService(...)`, bypassing the proxy entirely.

Verified live through the gateway, not just via the annotation being present: created a real city,
airline, and aircraft, then watched three things line up. (1) `redis-cli KEYS *` showed `cities::11`
and `airlines::11` populated after a single `GET /api/airlines/{id}` call - confirming the cascading
benefit reasoned about in design actually happens, since `AirlineService.getAirlineById` internally
calls the now-cached `LocationClient.getCityById`. (2) `redis-cli GET airlines::11` returned
readable JSON with the expected `@class` type marker, and `TTL` showed ~595 of the configured 600
seconds remaining. (3) Grepped `airline-core-service`'s and `location-service`'s own Hibernate SQL
logs across the whole session - `select ... from airlines where id=?` and
`select ... from cities where id=?` each appear **exactly once**, despite the city and airline being
fetched three separate times (once during airline creation, twice via two separate `getAirlineById`
GET requests) - direct proof the cache absorbed every repeat, not just that the endpoint returned
the right JSON. Repeated the same GET-twice-check for `AircraftService.getAircraftById`
(`aircraft::4` appeared in Redis after one call). All test data cleaned from MySQL and Redis
afterward.

**Known limitation**: caching is scoped to these four single-id lookups only - `pricing-service`
(fares change more often, different invalidation shape) and flight search results (would need a
multi-field cache key) were deliberately left out of this stage's scope, consistent with "start
narrow" already used for FlightSchedule. Real follow-up work if ever wanted, not silently forgotten.

## Stage 25 — JWT logout (third of the "smaller gaps")

JWTs are stateless by design - the gateway validates purely via signature + `exp`, with no
server-side lookup, so there was previously no way to invalidate a token before its natural 24h
expiry. Closed that with a Redis-backed revocation blocklist, built directly on Stage 24's Redis
work: `POST /auth/logout` (`user-service`) hashes the raw token (SHA-256) and writes
`revoked-tokens::<hash>` into Redis with a TTL equal to the token's own remaining lifetime - the
entry self-expires exactly when the token would have anyway, no cleanup job needed.
`JwtAuthenticationFilter` (`api-gateway`) - already the sole real JWT-validation point in this
system - gets one more check after signature/expiry succeed: does this hash exist in the blocklist?
If so, 401, instead of forwarding.

**Design decisions:**

- **`/auth/logout` reads the raw `Authorization` header directly, not gateway-forwarded
  `X-User-*` headers** - `/auth/**` bypasses `JwtAuthenticationFilter` entirely (same as
  signup/login always have), so the endpoint parses and validates the token itself via the
  existing `JwtUtil`.
- **Reactive Redis in `api-gateway`, not blocking** - a blocking `RedisTemplate` call inside a
  WebFlux `GlobalFilter` would stall the Netty event loop. Confirmed, by reading the actual Boot 4
  `DataRedisReactiveAutoConfiguration` source rather than assuming, that `spring-boot-starter-data-
  redis` alone auto-configures a `ReactiveStringRedisTemplate` bean for free once `reactor-core` is
  on the classpath (already true for the gateway) - no separate reactive-specific starter artifact
  needed.
- **Token hashing (`TokenHasher`, SHA-256 → hex) is duplicated in both `user-service` and
  `api-gateway`**, not centralized in `common-lib` - `common-lib` stays DTOs-only, same reasoning
  already applied to Feign clients, exceptions, and ownership checks. Both copies must stay byte-
  for-byte identical or a revoked token's hash won't match what the gateway checks.
- **The blocklist stores a hash, not the raw token** - a Redis dump never contains a working,
  replayable bearer token in plaintext.
- **A new `InvalidTokenException` (401)** for a logout call with a malformed/tampered/expired
  token, matching the existing one-exception-per-real-scenario granularity
  (`EmailAlreadyRegisteredException`, `InvalidCredentialsException`).
- **A missing `Authorization` header on `/auth/logout` is rejected by `@RequestHeader` itself
  (400), before the controller method even runs** - the method's own check only needs to catch a
  header that's *present* but not a Bearer token (401). Caught during live testing that an initial
  defensive `authHeader == null` check was actually unreachable dead code once `@RequestHeader`'s
  default `required = true` was accounted for, and removed it rather than leaving it in.

Full reactor `mvn test` confirmed `BUILD SUCCESS`, with 2 new tests in `AuthServiceTest`: one
asserting the Redis TTL written matches the token's remaining lifetime (mocked `Claims.
getExpiration()`), one asserting a malformed/tampered token throws `InvalidTokenException` and
never touches Redis at all (`verifyNoInteractions(redisTemplate)`).

Verified live end-to-end through the real gateway: (1) a fresh token worked normally against a
protected endpoint; (2) `POST /auth/logout` returned 204 and `redis-cli KEYS 'revoked-tokens::*'`
showed the new entry, with `TTL` reporting ~86351 of the ~86400-second (24h) token lifetime
remaining; (3) the exact same token immediately got 401 on the same protected endpoint it had just
succeeded on; (4) logging in again issued a **new** token that worked normally - confirming
revocation is per-token, not per-user/session; (5) logging out the same already-revoked token a
second time still returned 204 (harmless, idempotent re-write of the same key); (6) a malformed
`Authorization` header returned 401, a missing one returned 400. All test data cleaned from MySQL
and Redis afterward.

**Known limitation**: revocation is per-token, not per-user - there's no "log out all my
sessions/devices" operation, since nothing currently tracks which tokens belong to which active
sessions beyond the claims embedded in each token itself. Real future work if ever wanted, not
attempted here.

## Stage 26 — CORS (fourth of the "smaller gaps")

Triggered by analyzing the original course's frontend (`Frontend Original/`, copied in read-only
for structural reference, same as the earlier `Backend Original/` comparison - never a dependency
of the build itself). Its dev server runs on `http://localhost:5173` and calls the backend through
a single `axios` instance with a Bearer token from `localStorage` - the same trusted-header/JWT
model this backend already has, but from a **browser**, which is the one client type that actually
enforces CORS (every prior verification in this whole build used `curl`, which ignores CORS
entirely - the gap was real but had been invisible until now).

Added global CORS to `api-gateway`'s local `application.yml` (not `config-repo` - same reasoning
already established for routes: CORS is part of the gateway's structural definition of what it
exposes, and its config shape has the same remote-config-server list/map-binding fragility that
justified keeping routes local). Confirmed the exact property path by reading
`GlobalCorsProperties`'s real Boot/Cloud-Gateway source rather than assuming - it binds to
`spring.cloud.gateway.server.webflux.globalcors.cors-configurations`, the same prefix family
already confirmed for routes in Stage 5, not the more commonly-documented
`spring.cloud.gateway.globalcors`.

**Design decisions:**

- **Origin allowlist is explicit** (`http://localhost:5173` only), not a wildcard - deliberately
  narrow for now since only the known local dev frontend origin exists; a deployed frontend origin
  is real future work to add here, not forgotten.
- **`allowCredentials` was deliberately left unset (false)** - it only governs cookies/HTTP
  auth/TLS certs automatically attached by the browser. A manually-set `Authorization: Bearer
  <token>` header (this project's entire auth model) isn't a CORS "credential" and works fine
  without it - and leaving it false sidesteps the CORS spec rule that `allowCredentials: true`
  cannot be combined with a wildcard origin, which doesn't apply here anyway since the origin list
  is already explicit.
- **`allowedHeaders` is scoped to what the frontend actually sends** (`Content-Type`,
  `Authorization`), not a wildcard - matches every other "explicit over implicit" choice already
  established in this build (e.g. the Resilience4j `minimum-number-of-calls` setting).

**Verification needed a real browser, not `curl`** - `curl` doesn't send `Origin` or enforce
preflight, so it can't prove or disprove CORS behavior at all. Built a tiny static test page
(`fetch('http://localhost:5000/api/cities', {headers: {Authorization: 'Bearer ...'}})`), served it
over real HTTP on two ports via the Browser pane: `5173` (the allowed origin) and `5174` (a
disallowed origin), and confirmed three independent signals. (1) From `5173`, a real browser fetch
succeeded - `status=401` (the fake token was correctly rejected, a separate concern from CORS) was
readable in JS, which is only possible if the browser let the response through at all. (2)
`access-control-allow-origin` read via `res.headers.get()` from JS came back `null` even on the
successful `5173` request - initially looked like a failure, but is actually expected: that header
isn't on the Fetch API's default response-header read safelist, so JS can never see it via
`headers.get()` regardless of whether the server sent it. Confirmed the real headers instead via
raw `curl -i` simulating the identical preflight (`Origin`, `Access-Control-Request-Method/
Headers`) - the response correctly carried `Access-Control-Allow-Origin: http://localhost:5173`,
`Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE,OPTIONS`, `Access-Control-Allow-Headers:
authorization`, and `Access-Control-Max-Age: 3600`, present even on the downstream 401 response
(proving `JwtAuthenticationFilter`'s early-completion path doesn't bypass the earlier `CorsWebFilter`
header injection). (3) From `5174` (disallowed), the real browser fetch threw `"Failed to fetch"` -
genuinely blocked by the browser itself, not just a missing header - and the raw `curl` preflight
simulation for `5174` confirmed why: `403 Forbidden`, no CORS headers at all. Both test HTTP servers
stopped afterward.

**Known limitation**: only the local dev origin is allowlisted. Adding a deployed frontend origin
(Vercel, etc.) is a one-line addition to the same `allowedOrigins` list whenever that exists - not
attempted here since no such deployment exists yet.

## Stage 27 — SMS notifications via Twilio (fifth and last of the "smaller gaps")

Extends Stage 18's real-notification-delivery work (email via MailHog) with a second channel.
`notification-service`'s `BookingConfirmedEventConsumer` now attempts both an email and an SMS on
every `booking.confirmed` event, each independently.

**Threading a phone number end-to-end needed four services touched**, not just notification-service
- `User` gained an optional `phoneNumber` (`user-service`), embedded as a JWT claim at login
(`JwtUtil.generateToken`), forwarded by `api-gateway`'s `JwtAuthenticationFilter` as `X-User-Phone`
(only when the claim is actually present - a user without a phone gets no header at all, not an
empty one), read by `booking-service`'s `BookingController`/`BookingService` and stamped onto
`Booking.userPhone` exactly like `userEmail` already was, then threaded through
`OutboxEvent.customerPhone` → `BookingConfirmedEvent.customerPhone` → `SmsService`. Same pipeline
shape as email in Stage 18, just extended one field further - the email precedent made this a
mechanical, well-understood change rather than a new design.

**A real testability problem surfaced and was fixed before any live testing**: the first version of
`SmsService` called `Twilio.init(...)` directly in its own constructor and referenced
`TwilioConstant`'s fail-fast fields (`ACCOUNT_SID`/`AUTH_TOKEN`/`FROM_PHONE_NUMBER`, mirroring
`JwtConstant`'s env-var pattern) from inside `sendBookingConfirmationSms`. That would have made
`SmsServiceTest` require real Twilio env vars just to construct the service - exactly the trap
`AuthServiceTest` was already built to avoid around `JwtConstant` (by mocking `JwtUtil` entirely).
Fixed by splitting responsibilities: `TwilioInitializer` (a separate `@Component` with
`@PostConstruct`) owns the one-time global `Twilio.init(ACCOUNT_SID, AUTH_TOKEN)` call, `SmsService`
now takes `fromPhoneNumber` as a plain constructor-injected `@Value` (sourced from
`config-repo/notification-service.yml`'s `twilio.from-phone-number: ${TWILIO_PHONE_NUMBER}` - a
normal Spring placeholder, not `TwilioConstant`, since the from-number isn't actually a secret the
way the account SID/auth token are). `SmsServiceTest` now constructs a real `SmsService` with a
literal string, no environment dependency at all - confirmed by running `mvn test` for
notification-service with no Twilio env vars sourced, and it passed. Only the account
SID/auth token - genuine secrets - keep the fail-fast `TwilioConstant`/`System.getenv()` treatment.

**`Message.creator(...)` is a static Twilio SDK call, not an injected collaborator**, so
`SmsServiceTest` uses Mockito's static mocking (`mockStatic(Message.class)`) instead of the usual
constructor-injection `@Mock` pattern every other service test in this codebase uses - the first
time this project needed it. Confirmed it works with zero extra dependency: Mockito 5's inline mock
maker (bundled in `mockito-core`, already on the classpath via `spring-boot-starter-test`) supports
static mocking by default since Mockito 5.0, no separate `mockito-inline` artifact needed.

**Verified the actual Twilio Java SDK coordinates and API shape against the real jar before
writing code**, not from memory - `com.twilio.sdk:twilio:13.0.0` confirmed directly against Maven
Central's `maven-metadata.xml`, and `Twilio.init(String,String)` / `Message.creator(PhoneNumber,
PhoneNumber, String)` / the no-arg `Creator.create()` convenience method confirmed via `javap`
against the downloaded jar - same discipline already applied to every Spring Boot 4 module-split
surprise this project has hit (Kafka, Security, Health, Cache).

**Each notification channel gets its own independent try/catch in the consumer** - an SMTP outage
must not prevent the SMS attempt, and a Twilio failure must not prevent the email attempt. Two new
tests (`aFailedEmailSendDoesNotPreventTheSmsAttempt`, `aFailedSmsSendDoesNotPreventTheEmailAttempt`)
directly assert this in `BookingConfirmedEventConsumerTest`, not just the existing "one failure
doesn't propagate" tests carried over from Stage 18.

**A real, unrelated gap was found and fixed while building the live test data**: `POST
/api/seat-instances` actually requires a real `Seat` reference (`seatRepository.findById(seatId)`,
throwing if absent) - contradicting a prior assumption in this file that a full seat catalog wasn't
needed to create a seat instance (that note was about the entity column being nullable for the
concurrency *test*, which manipulates the repository directly, not about the REST API accepting a
null seat). Discovered as a real `500` (not the intended `404`) because `seatRepository.findById(null)`
throws `InvalidDataAccessApiUsageException` rather than returning empty - a second, smaller,
pre-existing gap this stage did not attempt to fix (a `400` would be more correct than a `500` for
a missing required reference, but that's a pattern that would need auditing across every service
that does this, not a one-line fix specific to SMS).

Full reactor `mvn test` confirmed `BUILD SUCCESS`, including two other test files this stage's
`BookingConfirmedEvent.customerPhone` field addition rippled into and required updating -
`seat-service`'s own `BookingConfirmedEventConsumerTest` (it also consumes this event, to mark
seats `BOOKED`) plus `booking-service`'s `OutboxRelayTest`/`PaymentEventConsumerTest` - a reminder
that a shared `common-lib` event type change needs a repo-wide grep for every construction site,
not just the modules a stage's design initially focuses on.

Verified live end-to-end through the real gateway, with real Twilio credentials the user added to
`.env` (never pasted into or handled directly by the assistant beyond writing them into that
gitignored file once volunteered in chat - flagged to the user that pasting live secrets into a
chat transcript isn't private storage, and recommended rotating the Twilio auth token afterward as
a precaution). Built a full booking chain (city → airport → airline → aircraft → seat map → cabin
class → seat → flight → flight instance → fare → seat instance → booking → payment confirmation)
and confirmed, in order: (1) the signup response and the booking's `userPhone` field both correctly
carried the real phone number end-to-end; (2) the JWT's decoded payload actually contained a
`phoneNumber` claim; (3) `notification-service`'s logs showed a real Twilio API call was made and
correctly rejected with Twilio's own error 21608 ("trial account, unverified destination number")
before the number was verified; (4) MailHog confirmed the booking-confirmation **email** still sent
successfully in the exact same failed attempt - direct proof the two channels' failure isolation
works, not just that the code compiles that way; (5) after the user verified the destination number
in the Twilio console, `TWILIO`'s own `OutgoingCallerIds` API was queried directly
(`GET /2010-04-01/Accounts/{sid}/OutgoingCallerIds.json`) to check real state rather than trusting
the console alone - it kept returning an empty list even after the user's verification, so a third
retry attempt was made and still correctly rejected with the identical, correct error. Per the
user's explicit choice, real delivery was not chased further - the repeated real API calls, the
consistent and correct rejection reason, and the independently-successful email are accepted as
sufficient proof the integration is wired correctly end-to-end; getting an actual SMS to land is a
Twilio-account-configuration matter outside this codebase, not a code gap.

All test data cleaned from MySQL, Redis (including stale cache entries for the now-deleted test
airline/aircraft/airports from Stage 24's caching), and MailHog afterward.

**Known limitations**: E.164 phone number format is not validated or normalized anywhere in this
codebase - a badly-formatted number just produces a Twilio API error at send time, caught and
logged like any other SMS failure, not a validation error at signup. `POST /api/seat-instances`
returning `500` instead of `404` for a missing/null seat reference (found during this stage's live
test setup) remains unfixed - a real, pre-existing, narrow gap, not specific to SMS.

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
