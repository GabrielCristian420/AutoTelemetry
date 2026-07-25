import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { api } from "../api/client";
import VehicleMap from "../components/map/VehicleMap";
import TelemetryChart from "../components/charts/TelemetryChart";

const fmt = (n) => (n == null ? "—" : Number(n).toFixed(2));

function formatTime(isoString) {
  if (!isoString) return "Active now";
  const date = new Date(isoString);
  return date.toLocaleString(undefined, {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function calcDuration(startIso, endIso) {
  const start = new Date(startIso).getTime();
  const end = endIso ? new Date(endIso).getTime() : Date.now();
  const diffSec = Math.max(0, Math.floor((end - start) / 1000));
  const mins = Math.floor(diffSec / 60);
  const secs = diffSec % 60;
  if (mins > 60) {
    const hrs = Math.floor(mins / 60);
    return `${hrs}h ${mins % 60}m`;
  }
  return `${mins}m ${secs}s`;
}

export default function TripHistory() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [vehicle, setVehicle] = useState(null);
  const [stats, setStats] = useState(null);
  const [trips, setTrips] = useState([]);
  const [selectedTrip, setSelectedTrip] = useState(null);
  const [tripReadings, setTripReadings] = useState([]);
  const [viewMode, setViewMode] = useState("chart"); // 'chart' | 'table'
  const [loading, setLoading] = useState(true);
  const [loadingTrip, setLoadingTrip] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;

    const loadData = async () => {
      try {
        setLoading(true);
        const [vData, sData, tData] = await Promise.all([
          api.vehicles().then((list) => list.find((v) => String(v.id) === String(id))),
          api.vehicleStats(id).catch(() => null),
          api.vehicleTrips(id).catch(() => []),
        ]);

        if (cancelled) return;
        setVehicle(vData);
        setStats(sData);

        const sortedTrips = [...tData].sort((a, b) => b.id - a.id);
        setTrips(sortedTrips);

        if (sortedTrips.length > 0) {
          setSelectedTrip(sortedTrips[0]);
        }
        setError(null);
      } catch (e) {
        if (!cancelled) setError(e.message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    loadData();
    return () => {
      cancelled = true;
    };
  }, [id]);

  useEffect(() => {
    if (!selectedTrip) {
      setTripReadings([]);
      return;
    }

    let cancelled = false;
    setLoadingTrip(true);

    api
      .tripReadings(selectedTrip.id, 0, 500)
      .then((res) => {
        if (cancelled) return;
        const readings = (res && res.content) || [];
        setTripReadings(readings);
      })
      .catch(() => {
        if (!cancelled) setTripReadings([]);
      })
      .finally(() => {
        if (!cancelled) setLoadingTrip(false);
      });

    return () => {
      cancelled = true;
    };
  }, [selectedTrip]);

  // Adaptive Downsampling for long trips:
  // Samples ~100-150 representative rows so tables & charts remain instant.
  // ALWAYS preserves DTC fault code occurrences regardless of step.
  const getDownsampledReadings = (readings, trip) => {
    if (!readings || readings.length <= 120) return readings;

    const start = new Date(trip.startedAt).getTime();
    const end = trip.endedAt ? new Date(trip.endedAt).getTime() : Date.now();
    const durationMins = Math.max(1, (end - start) / (1000 * 60));

    let step = 1;
    if (durationMins > 120) {
      step = Math.ceil(readings.length / 150); // ~3-5 min intervals for multi-hour trips
    } else if (durationMins > 30) {
      step = Math.ceil(readings.length / 100); // ~1-2 min intervals
    } else {
      step = Math.ceil(readings.length / 80);
    }

    return readings.filter((r, i) => {
      const hasDtc = r.dtcCodes && r.dtcCodes.length > 0;
      const isBoundary = i === 0 || i === readings.length - 1;
      return i % step === 0 || hasDtc || isBoundary;
    });
  };

  const displayReadings = selectedTrip ? getDownsampledReadings(tripReadings, selectedTrip) : [];
  const isDownsampled = tripReadings.length > displayReadings.length;

  const exportCSV = () => {
    if (!selectedTrip || !displayReadings.length) return;
    const headers = ["Reading ID", "Recorded At", "Speed (km/h)", "RPM", "Engine Temp (C)", "Fuel Level (%)", "Latitude", "Longitude", "DTC Codes"];
    const rows = displayReadings.map((r) => [
      r.id || r.readingId || "",
      r.recordedAt || "",
      r.speedKmh ?? "",
      r.rpm ?? "",
      r.engineTempC ?? "",
      r.fuelLevelPct ?? "",
      r.lat ?? "",
      r.lng ?? "",
      (r.dtcCodes || []).join(";"),
    ]);

    const csvContent = [headers.join(","), ...rows.map((row) => row.join(","))].join("\n");
    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.setAttribute("download", `trip_${selectedTrip.id}_telemetry.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  if (loading) {
    return (
      <div className="grid">
        <div style={{ color: "var(--muted)", padding: "20px 0" }}>
          📡 Loading trip history…
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="grid">
        <div className="alert">{error}</div>
      </div>
    );
  }

  const latestReading = tripReadings.length ? tripReadings[tripReadings.length - 1] : null;

  return (
    <div className="grid">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <div>
          <h2>
            {vehicle ? `${vehicle.make} ${vehicle.model}` : `Vehicle #${id}`}{" "}
            <span style={{ color: "var(--muted)", fontSize: "0.8em" }}>
              {vehicle?.vin}
            </span>
          </h2>
        </div>
        <div style={{ display: "flex", gap: "10px" }}>
          <button className="btn" onClick={() => navigate(`/vehicle/${id}/live`)}>
            📡 Live Tracking
          </button>
        </div>
      </div>

      {stats && (
        <div className="grid stats">
          <div className="card">
            <div className="stat-label">Total Trips</div>
            <div className="stat-value">{trips.length}</div>
          </div>
          <div className="card">
            <div className="stat-label">Avg Speed</div>
            <div className="stat-value">{fmt(stats.avgSpeedKmh)} km/h</div>
          </div>
          <div className="card">
            <div className="stat-label">Max RPM</div>
            <div className="stat-value">{stats.maxRpm ?? "—"}</div>
          </div>
          <div className="card">
            <div className="stat-label">Active DTCs</div>
            <div className="stat-value">{stats.activeDtcCount ?? 0}</div>
          </div>
        </div>
      )}

      <div style={{ display: "grid", gridTemplateColumns: "300px 1fr", gap: "20px", marginTop: "10px" }}>
        {/* Trip List Sidebar */}
        <div className="card" style={{ padding: "16px", maxHeight: "750px", overflowY: "auto" }}>
          <strong style={{ fontSize: "1.1em", display: "block", marginBottom: "12px" }}>
            Recorded Trips ({trips.length})
          </strong>

          {trips.length === 0 ? (
            <div style={{ color: "var(--muted)", fontSize: "0.9em" }}>
              No recorded trips yet for this vehicle.
            </div>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
              {trips.map((t) => {
                const isSelected = selectedTrip && selectedTrip.id === t.id;
                const isActive = t.endedAt == null;

                return (
                  <div
                    key={t.id}
                    onClick={() => setSelectedTrip(t)}
                    style={{
                      padding: "12px",
                      borderRadius: "8px",
                      background: isSelected ? "rgba(16, 185, 129, 0.15)" : "var(--bg-secondary, #1e293b)",
                      border: isSelected ? "1px solid #10b981" : "1px solid transparent",
                      cursor: "pointer",
                      transition: "all 0.2s ease",
                    }}
                  >
                    <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "6px" }}>
                      <strong style={{ color: isSelected ? "#10b981" : "inherit" }}>
                        Trip #{t.id}
                      </strong>
                      <span
                        style={{
                          fontSize: "0.75em",
                          padding: "2px 6px",
                          borderRadius: "4px",
                          background: isActive ? "rgba(16,185,129,0.2)" : "rgba(148,163,184,0.15)",
                          color: isActive ? "#10b981" : "var(--muted)",
                        }}
                      >
                        {isActive ? "Active" : "Completed"}
                      </span>
                    </div>

                    <div style={{ fontSize: "0.85em", color: "var(--muted)" }}>
                      <div>📅 {formatTime(t.startedAt)}</div>
                      <div>⏱ Duration: {calcDuration(t.startedAt, t.endedAt)}</div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        {/* Trip Detail & Map Area */}
        <div style={{ display: "flex", flexDirection: "column", gap: "20px" }}>
          {selectedTrip ? (
            <>
              <div className="card">
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "10px" }}>
                  <strong>
                    Route Map · Trip #{selectedTrip.id}{" "}
                    {loadingTrip && <span style={{ fontSize: "0.85em", color: "var(--muted)" }}>loading coordinates…</span>}
                  </strong>
                  <span style={{ fontSize: "0.85em", color: "var(--muted)" }}>
                    {tripReadings.length} telemetry samples
                  </span>
                </div>
                <VehicleMap trail={tripReadings} latest={latestReading} />
              </div>

              <div className="card">
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "12px" }}>
                  <div>
                    <strong>Telemetry Data · Trip #{selectedTrip.id}</strong>
                    {isDownsampled && (
                      <span style={{ marginLeft: "10px", fontSize: "0.78em", color: "#38bdf8", background: "rgba(56,189,248,0.12)", padding: "2px 8px", borderRadius: "4px" }}>
                        ⚡ Adaptive View ({displayReadings.length} of {tripReadings.length} points)
                      </span>
                    )}
                  </div>
                  <div style={{ display: "flex", gap: "8px" }}>
                    <button
                      className="btn"
                      onClick={() => setViewMode(viewMode === "chart" ? "table" : "chart")}
                      style={{
                        background: "rgba(56, 189, 248, 0.12)",
                        border: "1px solid #38bdf8",
                        color: "#38bdf8",
                        fontWeight: 600,
                        fontSize: "0.85em",
                        padding: "6px 12px",
                      }}
                    >
                      {viewMode === "chart" ? "📋 Table View" : "📊 Chart View"}
                    </button>
                    <button
                      className="btn"
                      onClick={exportCSV}
                      disabled={!displayReadings.length}
                      style={{
                        background: "rgba(16, 185, 129, 0.15)",
                        border: "1px solid #10b981",
                        color: "#10b981",
                        fontWeight: 600,
                        fontSize: "0.85em",
                        padding: "6px 12px",
                      }}
                    >
                      📥 Download CSV
                    </button>
                  </div>
                </div>

                {tripReadings.length > 0 ? (
                  viewMode === "chart" ? (
                    <TelemetryChart data={displayReadings} />
                  ) : (
                    <div style={{ maxHeight: "350px", overflowY: "auto" }}>
                      <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.85em", textAlign: "left" }}>
                        <thead>
                          <tr style={{ borderBottom: "1px solid #334155", color: "var(--muted)" }}>
                            <th style={{ padding: "8px" }}>ID</th>
                            <th style={{ padding: "8px" }}>Time</th>
                            <th style={{ padding: "8px" }}>Speed (km/h)</th>
                            <th style={{ padding: "8px" }}>RPM</th>
                            <th style={{ padding: "8px" }}>Engine (°C)</th>
                            <th style={{ padding: "8px" }}>Fuel (%)</th>
                            <th style={{ padding: "8px" }}>GPS (Lat, Lng)</th>
                            <th style={{ padding: "8px" }}>DTC</th>
                          </tr>
                        </thead>
                        <tbody>
                          {displayReadings.map((r, idx) => (
                            <tr key={r.id || r.readingId || idx} style={{ borderBottom: "1px solid rgba(255,255,255,0.04)" }}>
                              <td style={{ padding: "6px 8px" }}>#{r.id || r.readingId}</td>
                              <td style={{ padding: "6px 8px" }}>{r.recordedAt ? new Date(r.recordedAt).toLocaleTimeString() : "—"}</td>
                              <td style={{ padding: "6px 8px", color: "#10b981", fontWeight: 600 }}>{r.speedKmh ?? "—"}</td>
                              <td style={{ padding: "6px 8px", color: "#38bdf8" }}>{r.rpm ?? "—"}</td>
                              <td style={{ padding: "6px 8px" }}>{r.engineTempC ?? "—"}</td>
                              <td style={{ padding: "6px 8px" }}>{r.fuelLevelPct ?? "—"}</td>
                              <td style={{ padding: "6px 8px", color: "var(--muted)" }}>
                                {r.lat != null && r.lng != null ? `${r.lat.toFixed(4)}, ${r.lng.toFixed(4)}` : "—"}
                              </td>
                              <td style={{ padding: "6px 8px", color: "#ef4444" }}>{(r.dtcCodes || []).join(", ") || "—"}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )
                ) : (
                  <div style={{ color: "var(--muted)", padding: "20px 0", fontSize: "0.9em" }}>
                    No telemetry points recorded for this trip yet.
                  </div>
                )}
              </div>
            </>
          ) : (
            <div className="card" style={{ padding: "40px", textAlign: "center", color: "var(--muted)" }}>
              Select a trip from the left sidebar to view its historical route and telemetry charts.
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
