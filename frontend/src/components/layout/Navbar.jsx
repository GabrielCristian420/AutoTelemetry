import { NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../../contexts/AuthContext";

export default function Navbar() {
  const { logout } = useAuth();
  const navigate = useNavigate();
  return (
    <div className="navbar">
      <span className="brand">AutoTelemetry</span>
      <div className="links">
        <NavLink to="/" end className={({ isActive }) => (isActive ? "active" : "")}>
          Fleet
        </NavLink>
        <a
          onClick={() => {
            logout();
            navigate("/login");
          }}
          style={{ cursor: "pointer" }}
        >
          Logout
        </a>
      </div>
    </div>
  );
}
