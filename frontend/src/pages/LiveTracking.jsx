import { useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { api } from "../api/client";
import VehicleMap from "../components/map/VehicleMap";
import TelemetryChart from "../components/charts/TelemetryChart";

const WINDOW = 50;
const TRAIL_MAX = 200;

export default function LiveTracking() {
  const { id } = useParams();
  const [latest, setLatest] = useState(null);
  const [chartWindow, setChartWindow] = useState([]);
  const [tripTrail, setTripTrail] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const trailRef = useRef([]);
  const seenCodes = useRef(new Set());
  const loadedTripRef = useRef(null);

  useEffect(() => {
    let cancelled = false;

    const poll = async () => {
      try {
        const data = await api.live(id);
        if (cancelled) return;
        setLoading(false);
        const ordered = [...data].sort((a, b) => a.readingId - b.readingId);

        const merged = new Map();
        [...trailRef.current, ...ordered].forEach((r) => merged.set(r.readingId, r));
        const trail = Array.from(merged.values())
          .sort((a, b) => a.readingId - b.readingId)
          .slice(-TRAIL_MAX);
        trailRef.current = trail;
        setTripTrail(trail);
        setChartWindow(ordered.slice(-WINDOW));
        setLatest(ordered.length ? ordered[ordered.length - 1] : null);

        // Fetch historical readings for the active trip to initialize or restore the trail after refreshes
        const activeReading = ordered.find((r) => r.tripId != null);
        const currentTripId = activeReading ? activeReading.tripId : null;

        if (currentTripId && loadedTripRef.current !== currentTripId) {
          loadedTripRef.current = currentTripId;
          api.tripReadings(currentTripId, 0, TRAIL_MAX)
            .then((res) => {
              if (cancelled) return;
              if (res && res.content) {
                const history = res.content.map((r) => ({
                  readingId: r.id,
                  tripId: r.tripId,
                  recordedAt: r.recordedAt,
                  speedKmh: r.speedKmh,
                  rpm: r.rpm,
                  engineTempC: r.engineTempC,
                  fuelLevelPct: r.fuelLevelPct,
                  lat: r.lat,
                  lng: r.lng,
                  dtcCodes: r.dtcCodes,
                }));
                const newMerged = new Map();
                history.forEach((r) => newMerged.set(r.readingId, r));
                trailRef.current.forEach((r) => newMerged.set(r.readingId, r));
                const updatedTrail = Array.from(newMerged.values())
                  .sort((a, b) => a.readingId - b.readingId)
                  .slice(-TRAIL_MAX);
                trailRef.current = updatedTrail;
                setTripTrail(updatedTrail);
              }
            })
            .catch(() => {});
        }

        const active = new Set();
        ordered.forEach((r) => (r.dtcCodes || []).forEach((c) => active.add(c)));
        const fresh = [...active].filter((c) => !seenCodes.current.has(c));
        if (fresh.length) {
          setAlerts((prev) =>
            [...fresh.map((code) => ({ code, at: Date.now() })), ...prev].slice(0, 5)
          );
        }
        seenCodes.current = active;
        setError(null);
      } catch (e) {
        if (!cancelled) setError(e.message);
      }
    };

    poll();
    const t = setInterval(poll, 1000);
    return () => {
      cancelled = true;
      clearInterval(t);
    };
  }, [id]);

  if (error) {
    return (
      <div className="grid">
        <div className="alert">{error}</div>
      </div>
    );
  }

  return (
    <div className="grid">
      <h2>
        Live tracking · Vehicle #{id}
        {loading && <span style={{ marginLeft: "12px", fontSize: "0.8em", opacity: 0.7 }}>📡 Connecting live stream...</span>}
      </h2>

      <div className="grid stats">
        <div className="card">
          <div className="stat-label">Speed</div>
          <div className="stat-value">{latest ? `${latest.speedKmh} km/h` : "—"}</div>
        </div>
        <div className="card">
          <div className="stat-label">RPM</div>
          <div className="stat-value">{latest ? latest.rpm : "—"}</div>
        </div>
        <div className="card">
          <div className="stat-label">Engine</div>
          <div className="stat-value">{latest ? `${latest.engineTempC} °C` : "—"}</div>
        </div>
        <div className="card">
          <div className="stat-label">Fuel</div>
          <div className="stat-value">{latest ? `${latest.fuelLevelPct} %` : "—"}</div>
        </div>
      </div>

      {alerts.length > 0 && (
        <div>
          {alerts.map((a) => (
            <div className="alert" key={a.code + a.at}>
              ⚠ DTC {a.code} active
            </div>
          ))}
        </div>
      )}

      <VehicleMap trail={tripTrail} latest={latest} />

      <div className="card">
        <strong>Telemetry</strong>
        <TelemetryChart data={chartWindow} />
      </div>
    </div>
  );
}
