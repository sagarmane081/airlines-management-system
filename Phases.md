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
| 11 | Harden — seat concurrency ✅, real exception handling ✅, idempotency ✅, N+1 fixes ✅, tests | 🟨 In progress |
| — | Frontend | ⬜ Not started at all |

## Currently running (local dev)

Infra: MySQL (single container, `locationdb`, one database per service), `config-server` (8888),
`service-registry`/Eureka (8761), Kafka (standalone container, KRaft mode, port 9092).

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

All 8 business services registered with Eureka using their **IP address**, not hostname (see
"Eureka hostname gotcha" below) — required for `api-gateway`'s reactive load balancer to resolve
them at all.

Auth flow fully verified through the gateway: no token → 401, `/auth/login` → works without a
token and returns a JWT, valid token → request proxied through with `X-User-Id`/`X-User-Email`/
`X-User-Roles` headers attached, invalid/malformed token → 401. Nothing downstream reads those
headers yet — that's real future work once something needs a role check.

Circuit breakers verified with a real failure test, not just compiled: killed `location-service`,
watched `airline-core-service`'s calls to it go Closed (slow, ~1.1s, real attempt + fallback) →
Open (fast, ~0.08s, fallback only, no wasted attempts) after 5 failures, then restarted
`location-service` and watched it recover to Closed (fast, real data again) automatically.

## Immediate next steps

Stage 11 (harden) is underway. Seat concurrency — the headline item — is done: `seat-service`
now holds a real pessimistic lock (`SELECT ... FOR UPDATE`) across a `@Transactional`
`holdSeat(id)` method, exposed as `POST /api/seat-instances/{id}/hold`, atomically flipping
AVAILABLE → HELD or rejecting with 409. `booking-service` now actually calls this (via a
fallback-less `SeatClient`) instead of trusting a client-supplied `seatInstanceId`, and translates
the 409 into a clean failure instead of creating a booking for a seat nobody actually holds.

Verified two ways: `SeatInstanceConcurrencyTest` (seat-service's first test) races 15 threads
against the same seat row on the real MySQL database and asserts exactly 1 wins — confirmed as a
real negative control by temporarily reverting to a plain `findById` and watching the same test
fail reproducibly (10 of 15 "won"). And live, end-to-end, through the real HTTP stack: 5 concurrent
`POST /api/bookings` requests against one seat produced exactly one 201 and four clean 409s.

Real exception handling is also done: every service's `.orElseThrow(() -> new RuntimeException(...))`
for a missing entity now throws a `ResourceNotFoundException` (404) — same `@ResponseStatus`
technique as the two seat-concurrency exceptions, no `@RestControllerAdvice` needed anywhere.
`user-service` also got `EmailAlreadyRegisteredException` (409) and `InvalidCredentialsException`
(401) for signup/login. Along the way this surfaced a real, pre-existing bug: `user-service`'s
`SecurityConfig` had `.anyRequest().authenticated()` with no exception for `/error`, so Spring
Boot's internal error-rendering dispatch was itself getting blocked as "unauthenticated," turning
every error response — the new ones and the old bare 500s alike — into a flat 403. Nobody had
tested a failure path directly against the service since Stage 6. Fixed by permitting `/error`
alongside `/auth/**`. Verified live: 404 across all 8 REST services' not-found cases, 401 for a bad
password, 409 for a duplicate signup email, 201/200 for the happy path.

Idempotency is also done: Kafka is at-least-once, not exactly-once, so `PaymentEventConsumer`
(booking-service) and `BookingConfirmedEventConsumer` (seat-service) both now check the entity's
current status before acting and skip a no-op re-processing instead of unconditionally re-saving
and republishing — the classic idempotent-consumer pattern. `payment-service`'s `confirmPayment`
got the same guard for a duplicate REST call. This works because every event here maps to one
deterministic terminal state; it's explicitly not a generic solution for events with a cumulative
effect. `notification-service` is a known, accepted exception — no database, so a redelivered
event still produces one duplicate log line, fine for a simulated notification.

Verified via a plain Mockito unit test per consumer (`PaymentEventConsumerTest`,
`BookingConfirmedEventConsumerTest`) — a live test forcing real Kafka redelivery turned out to be
unreliable to read (Hibernate silently skips a no-op UPDATE regardless of any guard, muddying the
signal), so the tests call the `@KafkaListener` method directly, twice, and assert the repository
save and the event publish each happened exactly once. Confirmed as a real negative control:
removing the guard made both tests fail reproducibly before restoring it.

The N+1 Feign calls are also fixed: `location-service`'s and `airline-core-service`'s "get all"
endpoints now also accept an optional `ids` query param on the same path (`GET /api/cities?ids=1,2,3`,
`GET /api/airlines?ids=1,2`), backed by a `findAllByIdIn` repository method. `airline-core-service.
getAllAirlines` and `flight-ops-service.getAllFlights`/`getAllFlightInstances` now collect the
distinct IDs they need across the whole list and make exactly one bulk Feign call per dependency,
instead of one call per row (up to 3 per row for flights). `getAllFlightInstances` needed one extra
step — dedupe the underlying `Flight` entities by ID before batch-enriching, since many instances
commonly share one flight. Every existing Feign fallback got a bulk variant too, reusing the
single-lookup fallback (`ids.stream().map(this::getXById).toList()`) rather than duplicating the
placeholder logic. Verified directly via Hibernate's SQL log: `GET /api/flights` against 3 flights
(2 distinct airlines, 3 distinct cities) produced exactly one `where id in (?,?)` and one
`where id in (?,?,?)` query, not the 6+ separate `where id=?` calls it used to make.

Remaining in Stage 11: broader test coverage for the plain CRUD services.

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

## Known deliberate gaps (see `CLAUDE.md` for the full list)

- Still no tests for the plain CRUD services — by explicit decision, not an oversight. Tests exist
  only where they were the only reliable way to prove something (seat concurrency, idempotent
  consumers).
- No backend service reads the `X-User-Id`/`X-User-Roles` headers the gateway now forwards — no
  role-based authorization exists yet, just authentication at the edge.
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
