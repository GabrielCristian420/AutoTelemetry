export const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:8080";

const TOKEN_KEY = "at_token";

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

async function request(method, path, body) {
  const headers = { "Content-Type": "application/json" };
  const token = getToken();
  if (token) headers["Authorization"] = `Bearer ${token}`;
  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (res.status === 401) clearToken();
  if (!res.ok) throw new ApiError(res.status, `Request failed: ${res.status}`);
  if (res.status === 204) return null;
  return res.json();
}

export const api = {
  login: (email, password) =>
    request("POST", "/api/auth/login", { email, password }),
  register: (email, password, fullName) =>
    request("POST", "/api/auth/register", { email, password, fullName }),
  vehicles: () => request("GET", "/api/vehicles"),
  vehicleStats: (id) => request("GET", `/api/vehicles/${id}/stats`),
  live: (id) => request("GET", `/api/vehicles/${id}/live`),
  tripReadings: (id, page = 0, size = 500) =>
    request("GET", `/api/trips/${id}/readings?page=${page}&size=${size}`),
};
