import { useEffect, useRef } from 'react';
import { Modal } from 'antd';
import { useAuth } from '@/context/AuthContext';
import { useSecuritySettings } from '@/context/SecuritySettingsContext';
import { useUnsavedChanges } from '@/context/UnsavedChangesContext';

const ACTIVITY_EVENTS: Array<keyof DocumentEventMap> = [
  'mousemove',
  'keydown',
  'click',
  'scroll',
  'touchstart',
];

/**
 * Logs the user out after configurable inactivity. Warns 5 minutes before timeout.
 * Mount only under authenticated layout trees.
 */
export function useInactivityTimer(): void {
  const { isAuthenticated, logout, refreshAccessToken, registerActivityListener } =
    useAuth();
  const { settings } = useSecuritySettings();
  const { persistDrafts } = useUnsavedChanges();

  const timeoutMinutes = settings.inactivityTimeoutMinutes;
  const warnAtMs = Math.max(timeoutMinutes - 5, 1) * 60_000;
  const logoutAtMs = timeoutMinutes * 60_000;

  const warnTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const logoutTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const warningActiveRef = useRef(false);
  const modalShowingRef = useRef(false);

  // Keep latest callbacks in refs so the effect can stay stable on identity churn.
  const refreshRef = useRef(refreshAccessToken);
  const logoutRef = useRef(logout);
  const persistRef = useRef(persistDrafts);
  refreshRef.current = refreshAccessToken;
  logoutRef.current = logout;
  persistRef.current = persistDrafts;

  useEffect(() => {
    if (!isAuthenticated) {
      if (warnTimerRef.current) clearTimeout(warnTimerRef.current);
      if (logoutTimerRef.current) clearTimeout(logoutTimerRef.current);
      warningActiveRef.current = false;
      modalShowingRef.current = false;
      Modal.destroyAll();
      return;
    }

    const clearTimers = () => {
      if (warnTimerRef.current) clearTimeout(warnTimerRef.current);
      if (logoutTimerRef.current) clearTimeout(logoutTimerRef.current);
      warnTimerRef.current = null;
      logoutTimerRef.current = null;
    };

    const forceLogout = () => {
      warningActiveRef.current = false;
      modalShowingRef.current = false;
      Modal.destroyAll();
      persistRef.current();
      logoutRef.current('inactivity');
    };

    const schedule = () => {
      clearTimers();
      warnTimerRef.current = setTimeout(() => {
        if (modalShowingRef.current) return;
        modalShowingRef.current = true;
        warningActiveRef.current = true;
        Modal.confirm({
          title: 'Session expiring soon',
          content:
            'Your session will expire in 5 minutes due to inactivity. Click "Stay Logged In" to continue.',
          okText: 'Stay Logged In',
          cancelText: 'Log Out Now',
          closable: false,
          maskClosable: false,
          keyboard: false,
          centered: true,
          onOk: async () => {
            const ok = await refreshRef.current();
            modalShowingRef.current = false;
            warningActiveRef.current = false;
            if (ok) {
              schedule();
            } else {
              persistRef.current();
              logoutRef.current('session_expired');
            }
          },
          onCancel: () => {
            modalShowingRef.current = false;
            warningActiveRef.current = false;
            persistRef.current();
            logoutRef.current('logged_out');
          },
          afterClose: () => {
            modalShowingRef.current = false;
          },
        });
      }, warnAtMs);

      logoutTimerRef.current = setTimeout(() => {
        forceLogout();
      }, logoutAtMs);
    };

    schedule();

    const onActivity = () => {
      if (warningActiveRef.current) return;
      schedule();
    };

    for (const evt of ACTIVITY_EVENTS) {
      document.addEventListener(evt, onActivity, { passive: true });
    }
    const unregisterApi = registerActivityListener(onActivity);

    return () => {
      for (const evt of ACTIVITY_EVENTS) {
        document.removeEventListener(evt, onActivity);
      }
      unregisterApi();
      clearTimers();
    };
  }, [isAuthenticated, warnAtMs, logoutAtMs, registerActivityListener]);
}
