-- ============================================================================
-- AutoTelemetry — Week 5 migration (V2)
-- Index on telemetry_readings.recorded_at to make time-range scans fast.
-- The stats/aggregation layer queries readings "between two timestamps";
-- without this index Postgres would seq-scan the (potentially huge) readings
-- table on every stats call.
-- ============================================================================

CREATE INDEX idx_readings_recorded_at ON telemetry_readings (recorded_at);
