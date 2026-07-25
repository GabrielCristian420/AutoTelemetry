import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";

const fmt = (n) => (n == null ? "—" : Number(n).toFixed(2));

export default function Dashboard() {
  const [vehicles, setVehicles] = useState([]);
  const [stats, setStats] = useState({});
  const [error, setError] = useState(null);
  const [adding, setAdding] = useState(false);
  const navigate = useNavigate();

  const fetchVehicles = () => {
    api
      .vehicles()
      .then(setVehicles)
      .catch((e) => setError(e.message));
  };

  useEffect(() => {
    fetchVehicles();
  }, []);

  useEffect(() => {
    vehicles.forEach((v) => {
      api
        .vehicleStats(v.id)
        .then((s) => setStats((prev) => ({ ...prev, [v.id]: s })))
        .catch(() => {});
    });
  }, [vehicles]);

  const handleAddDemoVehicle = async () => {
    try {
      setAdding(true);
      setError(null);
      const randomVin = "1HGCR2F8" + String(Math.floor(100000003 + Math.random() * 899999990));
      await api.createVehicle({
        vin: randomVin,
        make: "Tesla",
        model: "Model 3",
        year: 2024,
        plate: "B 100 DEMO",
      });
      fetchVehicles();
    } catch (e) {
      setError(e.message || "Failed to add vehicle");
    } finally {
      setAdding(false);
    }
  };

  const handleDeleteVehicle = async (vehicleId) => {
    if (!window.confirm("Are you sure you want to delete this vehicle from your fleet?")) return;
    try {
      setError(null);
      await api.deleteVehicle(vehicleId);
      fetchVehicles();
    } catch (e) {
      setError(e.message || "Failed to delete vehicle");
    }
  };

  if (error) {
    return (
      <div className="grid">
        <div className="alert">{error}</div>
      </div>
    );
  }

  return (
    <div className="grid">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <h2>Fleet</h2>
        <button className="btn" onClick={handleAddDemoVehicle} disabled={adding}>
          {adding ? "Adding..." : "➕ Add Demo Vehicle"}
        </button>
      </div>

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
              <div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
                <button
                  className="btn"
                  onClick={() => navigate(`/vehicle/${v.id}/trips`)}
                  style={{
                    background: "rgba(56, 189, 248, 0.12)",
                    border: "1px solid #38bdf8",
                    color: "#38bdf8",
                    fontWeight: 600,
                  }}
                >
                  📜 Trip history
                </button>
                <button className="btn" onClick={() => navigate(`/vehicle/${v.id}/live`)}>
                  📡 Live track
                </button>
                <button
                  className="btn"
                  onClick={() => handleDeleteVehicle(v.id)}
                  style={{
                    background: "rgba(239, 68, 68, 0.12)",
                    border: "1px solid #ef4444",
                    color: "#ef4444",
                    padding: "6px 10px",
                  }}
                  title="Delete vehicle"
                >
                  🗑️
                </button>
              </div>
            </li>
          );
        })}
        {vehicles.length === 0 && (
          <li style={{ flexDirection: "column", alignItems: "flex-start", gap: 12 }}>
            <div>No vehicles in your fleet yet.</div>
            <button className="btn" onClick={handleAddDemoVehicle} disabled={adding}>
              {adding ? "Adding..." : "➕ Add First Demo Vehicle"}
            </button>
          </li>
        )}
      </ul>
    </div>
  );
}
