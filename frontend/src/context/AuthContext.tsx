import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import axios from 'axios';

export type UserRole = 'ADMIN' | 'VIEWER';

export type LogoutReason = 'session_expired' | 'inactivity' | 'logged_out' | null;

interface JwtClaims {
  sub?: string;
  role?: UserRole;
  mustChangePassword?: boolean;
  exp?: number;
}

interface AuthContextValue {
  token: string | null;
  email: string | null;
  role: UserRole | null;
  mustChangePassword: boolean;
  isAuthenticated: boolean;
  isAdmin: () => boolean;
  login: (username: string, password: string) => Promise<{ mustChangePassword: boolean }>;
  logout: (reason?: LogoutReason) => void;
  clearMustChangePassword: () => void;
  /** Re-authenticate with password after change (Account page). */
  refreshSession: (username: string, password: string) => Promise<{ mustChangePassword: boolean }>;
  /** Silent JWT refresh via POST /api/auth/refresh. */
  refreshAccessToken: () => Promise<boolean>;
  /** Optional callback invoked before logout so drafts can be persisted. */
  setBeforeLogout: (fn: (() => void) | null) => void;
  /** Notify inactivity timer of successful API activity (response interceptor). */
  onApiActivity: () => void;
  registerActivityListener: (fn: () => void) => () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export const TOKEN_KEY = 'fpa_token';
const LOGOUT_CHANNEL = 'cognologix_logout';

export function parseJwtClaims(token: string): JwtClaims {
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

export function isJwtExpired(token: string, skewSeconds = 5): boolean {
  const exp = parseJwtClaims(token).exp;
  if (exp == null) return true;
  return Date.now() / 1000 >= exp - skewSeconds;
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

function redirectToLogin(reason: LogoutReason) {
  const path =
    reason && reason !== 'logged_out'
      ? `/login?message=${encodeURIComponent(reason)}`
      : '/login';
  if (window.location.pathname !== '/login') {
    window.location.assign(path);
  } else if (reason && reason !== 'logged_out') {
    const url = new URL(window.location.href);
    url.searchParams.set('message', reason);
    window.history.replaceState({}, '', url.toString());
    window.dispatchEvent(new Event('fpa-login-message'));
  }
}

function readInitialSession() {
  const token = sessionStorage.getItem(TOKEN_KEY);
  if (!token) return sessionFromToken(null);
  if (isJwtExpired(token)) {
    sessionStorage.removeItem(TOKEN_KEY);
    return sessionFromToken(null);
  }
  return sessionFromToken(token);
}

/**
 * Module-level interceptors so child providers can fetch on mount without racing
 * AuthProvider's useEffect (child effects run before parent effects).
 */
type UnauthorizedHandler = (reason: LogoutReason) => void;

const activityListeners = new Set<() => void>();
let unauthorizedHandler: UnauthorizedHandler | null = null;

axios.interceptors.request.use((config) => {
  const stored = sessionStorage.getItem(TOKEN_KEY);
  if (stored) {
    if (isJwtExpired(stored)) {
      sessionStorage.removeItem(TOKEN_KEY);
      unauthorizedHandler?.('session_expired');
      return Promise.reject(new axios.Cancel('Session expired'));
    }
    config.headers.Authorization = `Bearer ${stored}`;
  }
  return config;
});

axios.interceptors.response.use(
  (res) => {
    activityListeners.forEach((fn) => fn());
    return res;
  },
  (err) => {
    if (axios.isCancel(err)) {
      return Promise.reject(err);
    }
    if (err.response?.status === 401) {
      const url = String(err.config?.url ?? '');
      if (!url.includes('/api/auth/login')) {
        sessionStorage.removeItem(TOKEN_KEY);
        unauthorizedHandler?.('session_expired');
      }
    }
    return Promise.reject(err);
  },
);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState(readInitialSession);
  const beforeLogoutRef = useRef<(() => void) | null>(null);
  const logoutChannelRef = useRef<BroadcastChannel | null>(null);

  const applyToken = useCallback((token: string) => {
    sessionStorage.setItem(TOKEN_KEY, token);
    const next = sessionFromToken(token);
    setSession(next);
    return next;
  }, []);

  const clearSessionLocal = useCallback(() => {
    sessionStorage.removeItem(TOKEN_KEY);
    setSession(sessionFromToken(null));
  }, []);

  const logout = useCallback(
    (reason: LogoutReason = 'logged_out') => {
      try {
        beforeLogoutRef.current?.();
      } catch {
        /* draft save must not block logout */
      }
      clearSessionLocal();
      try {
        logoutChannelRef.current?.postMessage('logout');
      } catch {
        /* ignore */
      }
      redirectToLogin(reason);
    },
    [clearSessionLocal],
  );

  const setBeforeLogout = useCallback((fn: (() => void) | null) => {
    beforeLogoutRef.current = fn;
  }, []);

  const onApiActivity = useCallback(() => {
    activityListeners.forEach((fn) => fn());
  }, []);

  const registerActivityListener = useCallback((fn: () => void) => {
    activityListeners.add(fn);
    return () => {
      activityListeners.delete(fn);
    };
  }, []);

  const refreshAccessToken = useCallback(async () => {
    const stored = sessionStorage.getItem(TOKEN_KEY);
    if (!stored || isJwtExpired(stored)) {
      return false;
    }
    try {
      const { data } = await axios.post<{ token: string }>('/api/auth/refresh', null, {
        headers: { Authorization: `Bearer ${stored}` },
      });
      applyToken(data.token);
      return true;
    } catch {
      return false;
    }
  }, [applyToken]);

  // Wire interceptor → React session clear + redirect
  useEffect(() => {
    unauthorizedHandler = (reason) => {
      setSession(sessionFromToken(null));
      redirectToLogin(reason);
    };
    return () => {
      unauthorizedHandler = null;
    };
  }, []);

  useEffect(() => {
    let channel: BroadcastChannel | null = null;
    try {
      channel = new BroadcastChannel(LOGOUT_CHANNEL);
      logoutChannelRef.current = channel;
      channel.onmessage = (event) => {
        if (event.data === 'logout') {
          clearSessionLocal();
          redirectToLogin('logged_out');
        }
      };
    } catch {
      logoutChannelRef.current = null;
    }
    return () => {
      channel?.close();
      logoutChannelRef.current = null;
    };
  }, [clearSessionLocal]);

  const login = useCallback(
    async (username: string, password: string) => {
      const { data } = await axios.post<{ token: string }>('/api/auth/login', {
        username,
        password,
      });
      const next = applyToken(data.token);
      return { mustChangePassword: next.mustChangePassword };
    },
    [applyToken],
  );

  const refreshSession = login;

  const clearMustChangePassword = useCallback(() => {
    setSession((prev) => ({ ...prev, mustChangePassword: false }));
  }, []);

  const isAdmin = useCallback(() => session.role === 'ADMIN', [session.role]);

  const value = useMemo(
    () => ({
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
      refreshAccessToken,
      setBeforeLogout,
      onApiActivity,
      registerActivityListener,
    }),
    [
      session,
      isAdmin,
      login,
      logout,
      clearMustChangePassword,
      refreshSession,
      refreshAccessToken,
      setBeforeLogout,
      onApiActivity,
      registerActivityListener,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
