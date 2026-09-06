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
| 9 | Kafka + the booking/payment saga (`booking-service`, `payment-service`) | ⬜ Not started |
| 10 | `notification-service` — pure Kafka consumer | ⬜ Not started |
| 11 | Harden — seat concurrency (the headline work), idempotency, real exception handling, N+1 fixes, tests | ⬜ Not started |
| — | Frontend | ⬜ Not started at all |

## Currently running (local dev)

Infra: MySQL (single container, `locationdb`, one database per service), `config-server` (8888),
`service-registry`/Eureka (8761).

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

Kafka/saga (Stage 9) is next — the largest remaining chunk, with an open scope question (real
payment gateway vs. simulated) still to decide when we get there.

## Known deliberate gaps (see `CLAUDE.md` for the full list)

- Seat booking has no concurrency safety yet — its own dedicated stage, not a bug to patch now.
- List endpoints doing Feign enrichment have an N+1 call pattern — deferred until it's slow enough
  to justify a bulk endpoint.
- No tests anywhere yet, by explicit decision, except the concurrency test still to come.
- No backend service reads the `X-User-Id`/`X-User-Roles` headers the gateway now forwards — no
  role-based authorization exists yet, just authentication at the edge.

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
