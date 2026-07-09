-- ============================================================================
-- AutoTelemetry — Week 5 migration (V2)
-- Index on telemetry_readings.recorded_at.
--
-- Speeds up time-ordered scans and any future "readings between two timestamps"
-- range filter. The current pagination query orders by recorded_at (scoped by
-- trip_id via idx_readings_trip) and the stats query is scoped by vehicle_id,
-- so neither hits this index yet — it is added now so range queries (e.g. a
-- date-bounded stats view in a later week) don't trigger a seq-scan on the
-- (potentially huge) readings table.
-- ============================================================================

CREATE INDEX idx_readings_recorded_at ON telemetry_readings (recorded_at);
