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
| 5 | API Gateway — single entry point, routing | ⬜ Not started |
| 6 | Auth (`user-service`) — signup, login, JWT | 🔶 In progress — signup done, login/JWT issuance next |
| 7 | Speed-run the repeats: `pricing-service`, `ancillary-service`, `seat-service` (data model only), `flight-ops-service` | ✅ Done |
| 8 | Circuit breakers (Resilience4j) on the real Feign calls that now exist | ⬜ Not started |
| 9 | Kafka + the booking/payment saga (`booking-service`, `payment-service`) | ⬜ Not started |
| 10 | `notification-service` — pure Kafka consumer | ⬜ Not started |
| 11 | Harden — seat concurrency (the headline work), idempotency, real exception handling, N+1 fixes, tests | ⬜ Not started |
| — | Frontend | ⬜ Not started at all |

## Currently running (local dev)

Infra: MySQL (single container, `locationdb`, one database per service), `config-server` (8888),
`service-registry`/Eureka (8761).

| Service | Port |
|---|---|
| `user-service` | 5001 |
| `airline-core-service` | 5003 |
| `location-service` | 5004 |
| `pricing-service` | 5005 |
| `ancillary-service` | 5006 |
| `seat-service` | 5007 |
| `flight-ops-service` | 5008 |

## Immediate next steps

1. Finish `user-service`: `JwtConstant`/`JwtUtil`, `LoginRequest`/`LoginResponse`, `/auth/login`.
2. Circle back to the API Gateway (Stage 5) — build it now with JWT validation wired in from the
   start, rather than bare routing first and retrofitting auth later.
3. Then continue down the stage list above.

## Known deliberate gaps (see `CLAUDE.md` for the full list)

- Seat booking has no concurrency safety yet — its own dedicated stage, not a bug to patch now.
- List endpoints doing Feign enrichment have an N+1 call pattern — deferred until it's slow enough
  to justify a bulk endpoint.
- No tests anywhere yet, by explicit decision, except the concurrency test still to come.
