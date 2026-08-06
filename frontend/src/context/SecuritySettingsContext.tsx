import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import axios from 'axios';
import { TOKEN_KEY, useAuth } from '@/context/AuthContext';

export interface SecuritySettings {
  jwtExpiryHours: number;
  inactivityTimeoutMinutes: number;
}

interface SecuritySettingsContextValue {
  settings: SecuritySettings;
  loading: boolean;
  reload: () => void;
}

const DEFAULTS: SecuritySettings = {
  jwtExpiryHours: 2,
  inactivityTimeoutMinutes: 30,
};

const SecuritySettingsContext = createContext<SecuritySettingsContextValue | null>(
  null,
);

export function SecuritySettingsProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated, token } = useAuth();
  const [settings, setSettings] = useState<SecuritySettings>(DEFAULTS);
  const [loading, setLoading] = useState(false);

  const reload = useCallback(() => {
    const stored = token ?? sessionStorage.getItem(TOKEN_KEY);
    if (!isAuthenticated || !stored) {
      setSettings(DEFAULTS);
      return;
    }
    setLoading(true);
    void axios
      .get<SecuritySettings>('/api/general/config/security')
      .then((r) => {
        setSettings({
          jwtExpiryHours: r.data.jwtExpiryHours ?? DEFAULTS.jwtExpiryHours,
          inactivityTimeoutMinutes:
            r.data.inactivityTimeoutMinutes ?? DEFAULTS.inactivityTimeoutMinutes,
        });
      })
      .catch(() => {
        setSettings(DEFAULTS);
      })
      .finally(() => setLoading(false));
  }, [isAuthenticated, token]);

  useEffect(() => {
    reload();
  }, [reload]);

  const value = useMemo(
    () => ({ settings, loading, reload }),
    [settings, loading, reload],
  );

  return (
    <SecuritySettingsContext.Provider value={value}>
      {children}
    </SecuritySettingsContext.Provider>
  );
}

export function useSecuritySettings(): SecuritySettingsContextValue {
  const ctx = useContext(SecuritySettingsContext);
  if (!ctx) {
    throw new Error('useSecuritySettings must be used within SecuritySettingsProvider');
  }
  return ctx;
}
