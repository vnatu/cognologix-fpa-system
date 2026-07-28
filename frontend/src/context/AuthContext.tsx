import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import axios from 'axios';

export type UserRole = 'ADMIN' | 'VIEWER';

interface JwtClaims {
  sub?: string;
  role?: UserRole;
  mustChangePassword?: boolean;
}

interface AuthContextValue {
  token: string | null;
  email: string | null;
  role: UserRole | null;
  mustChangePassword: boolean;
  isAuthenticated: boolean;
  isAdmin: () => boolean;
  login: (username: string, password: string) => Promise<{ mustChangePassword: boolean }>;
  logout: () => void;
  clearMustChangePassword: () => void;
  refreshSession: (username: string, password: string) => Promise<{ mustChangePassword: boolean }>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const TOKEN_KEY = 'fpa_token';

function parseJwtClaims(token: string): JwtClaims {
  try {
    const payload = token.split('.')[1];
    let normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const pad = normalized.length % 4;
    if (pad) {
      normalized += '='.repeat(4 - pad);
    }
    const json = atob(normalized);
    return JSON.parse(json) as JwtClaims;
  } catch {
    return {};
  }
}

function sessionFromToken(token: string | null) {
  if (!token) {
    return {
      token: null as string | null,
      email: null as string | null,
      role: null as UserRole | null,
      mustChangePassword: false,
    };
  }
  const claims = parseJwtClaims(token);
  const role = claims.role === 'ADMIN' || claims.role === 'VIEWER' ? claims.role : null;
  return {
    token,
    email: claims.sub ?? null,
    role,
    mustChangePassword: Boolean(claims.mustChangePassword),
  };
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState(() =>
    sessionFromToken(localStorage.getItem(TOKEN_KEY)),
  );

  useEffect(() => {
    const reqId = axios.interceptors.request.use((config) => {
      const stored = localStorage.getItem(TOKEN_KEY);
      if (stored) {
        config.headers.Authorization = `Bearer ${stored}`;
      }
      return config;
    });

    const resId = axios.interceptors.response.use(
      (res) => res,
      (err) => {
        if (err.response?.status === 401) {
          localStorage.removeItem(TOKEN_KEY);
          setSession(sessionFromToken(null));
        }
        return Promise.reject(err);
      },
    );

    return () => {
      axios.interceptors.request.eject(reqId);
      axios.interceptors.response.eject(resId);
    };
  }, []);

  const applyToken = useCallback((token: string) => {
    localStorage.setItem(TOKEN_KEY, token);
    const next = sessionFromToken(token);
    setSession(next);
    return next;
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const { data } = await axios.post<{ token: string }>('/api/auth/login', {
      username,
      password,
    });
    const next = applyToken(data.token);
    return { mustChangePassword: next.mustChangePassword };
  }, [applyToken]);

  const refreshSession = login;

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    setSession(sessionFromToken(null));
  }, []);

  const clearMustChangePassword = useCallback(() => {
    setSession((prev) => ({ ...prev, mustChangePassword: false }));
  }, []);

  const isAdmin = useCallback(() => session.role === 'ADMIN', [session.role]);

  return (
    <AuthContext.Provider
      value={{
        token: session.token,
        email: session.email,
        role: session.role,
        mustChangePassword: session.mustChangePassword,
        isAuthenticated: !!session.token,
        isAdmin,
        login,
        logout,
        clearMustChangePassword,
        refreshSession,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
