# Cab Service — Core Loop System Design

Covers: request → match → accept (lock) → OTP → live tracking → trip end → payment → rating.
Secondary modules (bidding/re-search, trust score, Aadhaar/PAN blocking, plate OCR, admin) are deliberately out of this doc — each gets its own design pass before it's built.

---

## 1. Data store split — Postgres vs Redis

| Data | Store | Why |
|---|---|---|
| users, driver/rider profiles, vehicles | Postgres | durable, relational |
| trips (status, fare, pickup/drop) | Postgres | source of truth, audited |
| ledger_entries | Postgres | immutable financial audit trail |
| ratings | Postgres | durable |
| outbox_events | Postgres | durable, consumed by worker |
| driver live location (geo index) | Redis (GEOADD) | high-frequency writes, sub-ms nearest-search |
| trip accept lock + fencing token | Redis (SET NX EX) | short-lived, self-expiring |
| OTP + attempt/regen counters | Redis (SET EX / INCR) | short-lived, single-purpose |
| driver connection registry (which WS server) | Redis | shared across WS server instances |
| live location pub/sub during trip | Redis Pub/Sub | ephemeral, latest-wins, no durability needed |

---

## 2. Core Postgres tables (columns, not exhaustive types)

```mermaid
erDiagram
    USERS ||--o| DRIVER_PROFILES : "may have"
    USERS ||--o| RIDER_PROFILES : "may have"
    DRIVER_PROFILES ||--o{ VEHICLES : owns
    DRIVER_PROFILES ||--o{ TRIPS : drives
    RIDER_PROFILES ||--o{ TRIPS : requests
    TRIPS ||--o| RATINGS : "rated once"
    TRIPS ||--o{ LEDGER_ENTRIES : generates
    DRIVER_PROFILES ||--o{ LEDGER_ENTRIES : "balance history"

    USERS {
        uuid id PK
        string name
        string phone
        string email
        string gov_id_number "Aadhaar/PAN, hashed"
        timestamp created_at
    }
    DRIVER_PROFILES {
        uuid id PK
        uuid user_id FK
        string status "online/offline/on_trip/blocked"
        decimal current_balance "cached, derived from ledger"
        decimal average_rating "cached"
        int total_ratings_count "cached"
        timestamp created_at
    }
    RIDER_PROFILES {
        uuid id PK
        uuid user_id FK
        timestamp created_at
    }
    VEHICLES {
        uuid id PK
        uuid driver_id FK
        string type "bike/hatchback/sedan/etc"
        string fuel_type "petrol/cng/diesel/electric"
        string plate_number
        string plate_image_url
        boolean verified
    }
    TRIPS {
        uuid id PK
        uuid rider_id FK
        uuid driver_id FK "nullable until matched"
        string status "requested/assigned/started/completed/cancelled"
        decimal pickup_lat
        decimal pickup_lon
        decimal drop_lat
        decimal drop_lon
        decimal quoted_fare "locked at acceptance, immutable after"
        string cancellation_reason
        timestamp requested_at
        timestamp started_at
        timestamp completed_at
    }
    RATINGS {
        uuid id PK
        uuid trip_id FK
        uuid driver_id FK
        uuid rider_id FK
        int stars
        string comment "required if stars <= 2"
        timestamp created_at
    }
    LEDGER_ENTRIES {
        uuid id PK
        uuid driver_id FK
        uuid trip_id FK
        string type "OWES_APP / APP_OWES_DRIVER / SETTLEMENT"
        decimal amount "signed"
        timestamp created_at
    }
    OUTBOX_EVENTS {
        uuid id PK
        string aggregate_type
        uuid aggregate_id
        string event_type
        jsonb payload
        string status "PENDING/SENT"
        timestamp created_at
    }
```

**Indexes worth calling out explicitly:**
- `outbox_events (status, created_at)` — composite, for the worker's polling query
- `trips (rider_id, status)`, `trips (driver_id, status)` — for history screens
- `ledger_entries (driver_id, created_at)` — for the driver's transaction list

---

## 3. Core loop — sequence diagram

```mermaid
sequenceDiagram
    participant R as Rider App
    participant API as Spring Boot API
    participant GEO as Redis (Geo + Locks + OTP)
    participant PG as Postgres
    participant OB as Outbox Worker
    participant WS as WebSocket Gateway
    participant D as Driver App

    R->>API: POST /trips/request (pickup, drop)
    API->>PG: INSERT trip (status=requested)
    API->>GEO: GEOSEARCH nearest drivers (1km)
    API->>WS: notify candidate driver(s) via registry+pubsub
    WS->>D: push "ride request"

    D->>API: POST /trips/{id}/accept
    API->>GEO: SET trip:{id}:lock token NX EX 30
    alt lock acquired
        API->>PG: BEGIN TX
        PG->>PG: UPDATE trip SET driver_id, status=assigned, quoted_fare=locked
        PG->>PG: INSERT outbox_event (type=DRIVER_ASSIGNED)
        PG->>API: COMMIT
        API->>GEO: SET trip:{id}:otp <code> EX 600
        OB->>PG: SELECT ... FOR UPDATE SKIP LOCKED (pending events)
        OB->>WS: publish DRIVER_ASSIGNED
        WS->>R: push "driver found" + driver info
        WS->>D: (other offered drivers) push "trip taken"
    else lock not acquired
        API-->>D: 409 "already accepted by another driver"
    end

    D->>API: POST /trips/{id}/verify-otp {code}
    API->>GEO: INCR trip:{id}:otp_attempts
    alt otp correct
        API->>GEO: DEL otp + attempts keys
        API->>PG: UPDATE trip SET status=started, started_at=now()
        API-->>D: trip started
    else wrong, attempts <= 3
        API-->>D: "incorrect, X attempts remaining"
    else attempts > 3
        API-->>D: "ask rider to regenerate OTP"
    end

    loop every 2 sec while on trip
        D->>WS: location ping (lat, lon)
        WS->>GEO: PUBLISH trip:{id}:location
        GEO->>WS: fan out to subscribed rider connection
        WS->>R: push live location
    end

    D->>API: POST /trips/{id}/complete
    API->>PG: BEGIN TX
    PG->>PG: UPDATE trip SET status=completed, completed_at=now()
    PG->>PG: INSERT ledger_entry (OWES_APP or APP_OWES_DRIVER, amount)
    PG->>PG: UPDATE driver_profile.current_balance += amount
    PG->>PG: COMMIT
    API-->>D: trip closed, show fare split
    API-->>R: trip closed, prompt rating

    R->>API: POST /trips/{id}/rating {stars, comment?}
    API->>API: validate: stars<=2 requires comment (server-side, not just frontend)
    API->>PG: BEGIN TX (SELECT driver FOR UPDATE)
    PG->>PG: INSERT rating
    PG->>PG: UPDATE driver average_rating, total_ratings_count (incremental mean)
    PG->>PG: COMMIT
```

---

## 4. Redis key patterns, consolidated

```
drivers:geo                         -- GEOADD set, all online drivers
trip:{id}:lock            = token   -- EX 30, fencing token for accept race
trip:{id}:otp             = code    -- EX 600
trip:{id}:otp_attempts    = int     -- INCR, no separate TTL beyond otp window
trip:{id}:otp_regen_count = int     -- caps total regenerations at 3
trip:{id}:last_location   = {lat,lon} -- EX 60, overwrite only
conn:driver:{id}          = serverX -- WS connection registry
channel: trip:{id}:location         -- pub/sub, ephemeral
channel: serverX:outbox             -- pub/sub, cross-server event delivery
```

---

## 5. What's deliberately deferred (not v1)

- Bidding / fare-increase re-search loop
- Driver trust-score system (early-end frequency, OTP-failure pattern, detour detection)
- Aadhaar/PAN-linked blocking + email notifications
- Plate-number OCR verification
- Super-admin tooling
- Full trip-path history (only start/end stored in v1)
- Road-distance-aware matching (S2/H3 + routing-engine two-phase search) — v1 can start with straight-line radius search, upgrade later

---

## 6. Build order from here

1. Postgres schema (above) + Flyway/Liquibase migrations
2. Auth (rider/driver/user, JWT)
3. Request → match (straight-line Redis geo search first, no OTP/lock yet) — get a driver visibly "found"
4. Accept flow with SETNX + fencing token + outbox
5. OTP verify
6. WebSocket location streaming
7. Trip completion + ledger + cached balance
8. Rating
9. Dockerize all of it, deploy
10. Only then: layer in secondary modules one at a time
