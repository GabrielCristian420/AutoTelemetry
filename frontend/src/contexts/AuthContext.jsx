import { createContext, useContext, useState, useCallback } from "react";
import { api, setToken, clearToken, getToken } from "../api/client";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setTokenState] = useState(() => getToken());

  const login = useCallback(async (email, password) => {
    const res = await api.login(email, password);
    setToken(res.token);
    setTokenState(res.token);
    return res;
  }, []);

  const register = useCallback(async (email, password, fullName) => {
    await api.register(email, password, fullName);
    return login(email, password);
  }, [login]);

  const logout = useCallback(() => {
    clearToken();
    setTokenState(null);
  }, []);

  return (
    <AuthContext.Provider value={{ token, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
