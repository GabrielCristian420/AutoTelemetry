import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";

const fmt = (n) => (n == null ? "—" : Number(n).toFixed(2));

export default function Dashboard() {
  const [vehicles, setVehicles] = useState([]);
  const [stats, setStats] = useState({});
  const [error, setError] = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    api
      .vehicles()
      .then(setVehicles)
      .catch((e) => setError(e.message));
  }, []);

  useEffect(() => {
    vehicles.forEach((v) => {
      api
        .vehicleStats(v.id)
        .then((s) => setStats((prev) => ({ ...prev, [v.id]: s })))
        .catch(() => {});
    });
  }, [vehicles]);

  if (error) {
    return (
      <div className="grid">
        <div className="alert">{error}</div>
      </div>
    );
  }

  return (
    <div className="grid">
      <h2>Fleet</h2>
      <ul className="vehicle-list">
        {vehicles.map((v) => {
          const s = stats[v.id] || {};
          return (
            <li key={v.id}>
              <div>
                <strong>
                  {v.make} {v.model}
                </strong>{" "}
                <span style={{ color: "var(--muted)" }}>{v.vin}</span>
                <div style={{ color: "var(--muted)", fontSize: 13, marginTop: 4 }}>
                  Avg {fmt(s.avgSpeedKmh)} km/h · Max RPM {s.maxRpm ?? "—"} · Active DTC{" "}
                  {s.activeDtcCount ?? "—"}
                </div>
              </div>
              <button className="btn" onClick={() => navigate(`/vehicle/${v.id}/live`)}>
                Live track
              </button>
            </li>
          );
        })}
        {vehicles.length === 0 && <li>No vehicles yet.</li>}
      </ul>
    </div>
  );
}
