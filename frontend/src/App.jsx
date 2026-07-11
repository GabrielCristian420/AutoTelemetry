import { Routes, Route, Navigate } from "react-router-dom";
import { useAuth } from "./contexts/AuthContext";
import Navbar from "./components/layout/Navbar";
import Login from "./pages/Login";
import Dashboard from "./pages/Dashboard";
import LiveTracking from "./pages/LiveTracking";

function RequireAuth({ children }) {
  const { token } = useAuth();
  return token ? children : <Navigate to="/login" replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        path="/*"
        element={
          <RequireAuth>
            <Navbar />
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/vehicle/:id/live" element={<LiveTracking />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </RequireAuth>
        }
      />
    </Routes>
  );
}
