-- ============================================================================
-- AutoTelemetry — initial schema (V1)
-- Flyway owns the DDL; Hibernate runs with ddl-auto=validate, so this file
-- is the single source of truth for the database structure.
-- ============================================================================

-- ---------- Users ----------
CREATE TABLE users (
    id              BIGSERIAL    PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------- Vehicles ----------
CREATE TABLE vehicles (
    id              BIGSERIAL    PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    vin             VARCHAR(17)  NOT NULL UNIQUE,
    make            VARCHAR(100) NOT NULL,
    model           VARCHAR(100) NOT NULL,
    year            INT,
    plate           VARCHAR(20),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_vehicles_user ON vehicles(user_id);

-- ---------- Trips ----------
CREATE TABLE trips (
    id              BIGSERIAL       PRIMARY KEY,
    vehicle_id      BIGINT          NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    started_at      TIMESTAMPTZ     NOT NULL,
    ended_at        TIMESTAMPTZ,
    distance_km     DOUBLE PRECISION,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX idx_trips_vehicle ON trips(vehicle_id);

-- ---------- Telemetry readings (high-frequency sensor samples) ----------
CREATE TABLE telemetry_readings (
    id              BIGSERIAL       PRIMARY KEY,
    trip_id         BIGINT          NOT NULL REFERENCES trips(id) ON DELETE CASCADE,
    recorded_at     TIMESTAMPTZ     NOT NULL,
    speed_kmh       DOUBLE PRECISION,
    rpm             INT,
    engine_temp_c   DOUBLE PRECISION,
    fuel_level_pct  DOUBLE PRECISION,
    lat             DOUBLE PRECISION,
    lng             DOUBLE PRECISION,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX idx_readings_trip ON telemetry_readings(trip_id);

-- ---------- DTC reference table (OBD-II diagnostic trouble codes) ----------
CREATE TABLE dtc_codes (
    id              BIGSERIAL    PRIMARY KEY,
    code            VARCHAR(10)  NOT NULL UNIQUE,
    description     VARCHAR(500) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ---------- Join table: reading <-> dtc_code (many-to-many) ----------
-- A single telemetry reading can carry several active fault codes.
CREATE TABLE reading_dtc_codes (
    reading_id      BIGINT NOT NULL REFERENCES telemetry_readings(id) ON DELETE CASCADE,
    dtc_code_id     BIGINT NOT NULL REFERENCES dtc_codes(id)         ON DELETE CASCADE,
    PRIMARY KEY (reading_id, dtc_code_id)
);
