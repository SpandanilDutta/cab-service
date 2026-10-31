-- V1__init_schema.sql
-- Core loop schema: users, driver/rider profiles, vehicles, trips, ratings, ledger, outbox.
-- Matches /docs/cab-service-core-design.md section 2.

CREATE EXTENSION IF NOT EXISTS "pgcrypto"; -- for gen_random_uuid()

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    phone           VARCHAR(20) NOT NULL UNIQUE,
    email           VARCHAR(255) UNIQUE,
    gov_id_number   VARCHAR(255), -- store hashed, never plaintext Aadhaar/PAN
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE driver_profiles (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               UUID NOT NULL UNIQUE REFERENCES users(id),
    status                VARCHAR(20) NOT NULL DEFAULT 'offline', -- online/offline/on_trip/blocked
    current_balance       NUMERIC(12,2) NOT NULL DEFAULT 0,       -- cached, derived from ledger_entries
    average_rating        NUMERIC(3,2) NOT NULL DEFAULT 0,        -- cached, derived from ratings
    total_ratings_count    INTEGER NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE rider_profiles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL UNIQUE REFERENCES users(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE vehicles (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id         UUID NOT NULL REFERENCES driver_profiles(id),
    type              VARCHAR(30) NOT NULL,   -- bike/hatchback/sedan/etc
    fuel_type         VARCHAR(20) NOT NULL,   -- petrol/cng/diesel/electric
    plate_number      VARCHAR(20) NOT NULL,
    plate_image_url   TEXT,
    verified          BOOLEAN NOT NULL DEFAULT false,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE trips (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rider_id            UUID NOT NULL REFERENCES rider_profiles(id),
    driver_id           UUID REFERENCES driver_profiles(id), -- nullable until matched
    status              VARCHAR(20) NOT NULL DEFAULT 'requested', -- requested/assigned/started/completed/cancelled
    pickup_lat          NUMERIC(9,6) NOT NULL,
    pickup_lon          NUMERIC(9,6) NOT NULL,
    drop_lat            NUMERIC(9,6) NOT NULL,
    drop_lon            NUMERIC(9,6) NOT NULL,
    quoted_fare         NUMERIC(10,2), -- locked at acceptance, immutable after
    cancellation_reason VARCHAR(100),
    requested_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ
);

CREATE TABLE ratings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id     UUID NOT NULL UNIQUE REFERENCES trips(id), -- one rating per trip
    driver_id   UUID NOT NULL REFERENCES driver_profiles(id),
    rider_id    UUID NOT NULL REFERENCES rider_profiles(id),
    stars       INTEGER NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT comment_required_if_low_rating
        CHECK (stars > 2 OR (comment IS NOT NULL AND length(trim(comment)) > 0))
);

CREATE TABLE ledger_entries (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id   UUID NOT NULL REFERENCES driver_profiles(id),
    trip_id     UUID REFERENCES trips(id), -- null for standalone settlements
    type        VARCHAR(30) NOT NULL,      -- OWES_APP / APP_OWES_DRIVER / SETTLEMENT
    amount      NUMERIC(12,2) NOT NULL,    -- signed
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(50) NOT NULL,  -- e.g. 'TRIP'
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(50) NOT NULL,  -- e.g. 'DRIVER_ASSIGNED'
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING/SENT
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes called out explicitly in the design doc
CREATE INDEX idx_outbox_status_created ON outbox_events (status, created_at);
CREATE INDEX idx_trips_rider_status ON trips (rider_id, status);
CREATE INDEX idx_trips_driver_status ON trips (driver_id, status);
CREATE INDEX idx_ledger_driver_created ON ledger_entries (driver_id, created_at);
