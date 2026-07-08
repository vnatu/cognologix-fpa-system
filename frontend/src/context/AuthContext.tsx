import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import axios from 'axios';

interface AuthContextValue {
  token: string | null;
  isAuthenticated: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const TOKEN_KEY = 'fpa_token';

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setToken] = useState<string | null>(() =>
    localStorage.getItem(TOKEN_KEY),
  );

  // Attach Bearer token to every outgoing request by reading from localStorage.
  // Using an interceptor (not axios.defaults) guarantees the header is present
  // even when React state is initialised from localStorage on page reload.
  useEffect(() => {
    const reqId = axios.interceptors.request.use((config) => {
      const stored = localStorage.getItem(TOKEN_KEY);
      if (stored) {
        config.headers.Authorization = `Bearer ${stored}`;
      }
      return config;
    });

    // Clear token state on 401 so the router redirects to login
    const resId = axios.interceptors.response.use(
      (res) => res,
      (err) => {
        if (err.response?.status === 401) {
          localStorage.removeItem(TOKEN_KEY);
          setToken(null);
        }
        return Promise.reject(err);
      },
    );

    return () => {
      axios.interceptors.request.eject(reqId);
      axios.interceptors.response.eject(resId);
    };
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const { data } = await axios.post<{ token: string }>('/api/auth/login', {
      username,
      password,
    });
    localStorage.setItem(TOKEN_KEY, data.token);
    setToken(data.token);
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    setToken(null);
  }, []);

  return (
    <AuthContext.Provider value={{ token, isAuthenticated: !!token, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
