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

## Known deliberate gaps (see `CLAUDE.md` for the full list)

- Test coverage is service-layer only — controllers and Spring Data repository interfaces aren't
  tested directly (thin pass-through and framework-generated, respectively). No test hits a real
  database except `SeatInstanceConcurrencyTest`, which genuinely needs one.
- Services are still directly reachable bypassing `api-gateway` — the trusted-header authorization
  model (Stage 13) assumes every request arrives via the gateway, but nothing enforces that at the
  network level yet.
- `booking-service`'s Feign calls to `pricing-service`/`payment-service` have no circuit-breaker
  fallback (deliberately — see `CLAUDE.md`), and no compensation/rollback exists if payment
  initiation fails after the booking row is already saved PENDING — that booking is just stuck,
  never cancelled automatically. Saga compensation is real future work, not covered by Stage 9.

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
