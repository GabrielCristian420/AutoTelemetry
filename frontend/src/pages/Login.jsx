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

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    try {
      if (mode === "login") await login(email, password);
      else await register(email, password, fullName);
      navigate("/");
    } catch (err) {
      setError(
        err instanceof ApiError ? "Invalid credentials or email already exists" : "Network error"
      );
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
          <button className="btn" type="submit">
            {mode === "login" ? "Sign in" : "Create account"}
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
