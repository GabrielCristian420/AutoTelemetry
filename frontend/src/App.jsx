import { useAuth } from "./contexts/AuthContext";

export default function App() {
  const { token } = useAuth();
  return (
    <div className="grid">
      <div className="card">
        AutoTelemetry dashboard scaffold {token ? "(authenticated)" : "(guest)"}
      </div>
    </div>
  );
}
