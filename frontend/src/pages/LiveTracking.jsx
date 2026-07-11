import { useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import { api } from "../api/client";

const WINDOW = 50;
const TRAIL_MAX = 200;

export default function LiveTracking() {
  const { id } = useParams();
  const [latest, setLatest] = useState(null);
  const [chartWindow, setChartWindow] = useState([]);
  const [tripTrail, setTripTrail] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [error, setError] = useState(null);
  const trailRef = useRef([]);
  const seenCodes = useRef(new Set());

  useEffect(() => {
    let cancelled = false;

    const poll = async () => {
      try {
        const data = await api.live(id);
        if (cancelled) return;
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
      <h2>Live tracking · Vehicle #{id}</h2>

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

      <div className="card">
        <strong>Map</strong>
        <p style={{ color: "var(--muted)" }}>Live map renders here (VehicleMap).</p>
      </div>

      <div className="card">
        <strong>Charts</strong>
        <p style={{ color: "var(--muted)" }}>Speed / RPM charts render here (TelemetryChart).</p>
      </div>
    </div>
  );
}
