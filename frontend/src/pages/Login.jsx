import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import { ApiError } from "../api/client";

export default function Login() {
  const { login, register } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("demo@autotelemetry.dev");
  const [password, setPassword] = useState("DemoPass123!");
  const [fullName, setFullName] = useState("Demo Driver");
  const [mode, setMode] = useState("login");
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const delay = (ms) => new Promise((r) => setTimeout(r, ms));

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    const MAX_RETRIES = 3;
    for (let attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      try {
        if (mode === "login") await login(email, password);
        else await register(email, password, fullName);
        setLoading(false);
        navigate("/");
        return;
      } catch (err) {
        if (err instanceof ApiError) {
          setLoading(false);
          setError("Invalid credentials or email already exists");
          return;
        }
        // Network error — likely Render cold start
        if (attempt < MAX_RETRIES) {
          setError(`⏳ Server is waking up… retrying (${attempt}/${MAX_RETRIES})`);
          await delay(3000);
        } else {
          setLoading(false);
          setError("Server is still starting up. Please wait 30 seconds and try again.");
        }
      }
    }
  };

  return (
    <div className="grid" style={{ maxWidth: 420, margin: "8vh auto" }}>
      <div className="card">
        <h2>AutoTelemetry</h2>
        <form onSubmit={submit}>
          <input
            className="input"
            placeholder="Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <input
            className="input"
            type="password"
            placeholder="Password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          {mode === "register" && (
            <input
              className="input"
              placeholder="Full name"
              value={fullName}
              onChange={(e) => setFullName(e.target.value)}
            />
          )}
          {error && <div className="alert">{error}</div>}
          <button className="btn" type="submit" disabled={loading}>
            {loading ? "Connecting…" : mode === "login" ? "Sign in" : "Create account"}
          </button>
        </form>
        <p style={{ color: "var(--muted)", marginTop: 12 }}>
          {mode === "login" ? "No account? " : "Have an account? "}
          <a
            onClick={() => setMode(mode === "login" ? "register" : "login")}
            style={{ cursor: "pointer" }}
          >
            {mode === "login" ? "Register" : "Sign in"}
          </a>
        </p>
      </div>
    </div>
  );
}

