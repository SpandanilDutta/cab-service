# Cab Service

Backend-focused cab-hailing system (Ola/Uber/Rapido style). Frontend is out of scope for this repo.
Full design reasoning: `docs/cab-service-core-design.md` and `docs/cab-service-design-rationale.md`.

## Layout

```
cab-service/
├── src/                        # API module — one deployable (modular monolith)
│   └── main/java/com/cabservice/
│       ├── user/                # shared identity (a person can be rider AND driver)
│       ├── driver/               # driver profile, vehicle, status
│       ├── rider/                 # rider profile, history
│       ├── matching/           # Redis GEOSEARCH + accept-lock (SETNX + fencing token)
│       ├── trip/                  # trip state machine, fare lock, live-location relay
│       ├── otp/                   # Redis-only OTP (never persisted)
│       ├── payment/            # immutable ledger + cached balance
│       ├── rating/              # rating submission + cached average
│       ├── outbox/              # transactional outbox entities (shared with worker)
│       ├── websocket/        # STOMP endpoints + Redis connection registry + pub/sub
│       ├── config/              # security/JWT, Redis, WebSocket config
│       └── common/            # shared value objects, exceptions — kept intentionally thin
│   └── main/resources/
│       └── db/migration/      # Flyway — V1__init_schema.sql is the baseline
│
├── worker/                       # separate deployable — polls outbox_events, scales
│   └── ...                          independently of API traffic (see design doc §4)
│
├── docker-compose.yml       # postgres + redis + api + worker
└── docs/                          # the two design documents, copied in for reference
```

## Why two deployables, not one

The API scales with request traffic; the worker scales with notification volume. They're
different load profiles, so they're split from day one even though everything else is a
single modular monolith for now — see design doc §1 for the full reasoning.

## Running locally

```bash
docker compose up --build
```

This brings up Postgres (with Flyway running V1 automatically on API startup), Redis,
the API on :8080, and the worker on :8081.

## Schema

The Flyway migration (`src/main/resources/db/migration/V1__init_schema.sql`) matches the
ER diagram in `docs/cab-service-core-design.md` exactly — table-for-table. If you change
one, change the other.

## Next steps (in build order — see design doc §6)

1. Auth (JWT) — `user`, `config`
2. Request → match, straight-line Redis geo search, no lock/OTP yet
3. Accept flow: SETNX + fencing token + outbox write
4. OTP verify
5. WebSocket location streaming
6. Trip completion + ledger + cached balance
7. Rating
8. Deploy, then layer in secondary modules (bidding, trust score, Aadhaar blocking, OCR)
