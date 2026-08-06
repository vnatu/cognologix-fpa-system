import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  type ReactNode,
} from 'react';
import { useAuth } from '@/context/AuthContext';

export interface UnsavedRegistration {
  pageName: string;
  period: string;
  isDirty: () => boolean;
  getDraft: () => unknown;
}

interface UnsavedChangesContextValue {
  register: (id: string, registration: UnsavedRegistration) => () => void;
  persistDrafts: () => void;
  peekDraft: <T = unknown>(pageName: string, period: string) => T | null;
  discardDraft: (pageName: string, period: string) => void;
  /** True if any registered page currently has unsaved edits. */
  hasUnsavedChanges: () => boolean;
  /**
   * If a registered page is dirty, prompt before running `onProceed`.
   * On Leave, drafts are persisted to sessionStorage for this session.
   */
  confirmIfDirty: (onProceed: () => void) => void;
}

const UnsavedChangesContext = createContext<UnsavedChangesContextValue | null>(null);

function draftKey(pageName: string, period: string): string {
  return `draft_${pageName}_${period}`;
}

export function UnsavedChangesProvider({ children }: { children: ReactNode }) {
  const { setBeforeLogout } = useAuth();
  const registrations = useRef(new Map<string, UnsavedRegistration>());

  const persistDrafts = useCallback(() => {
    const merged = new Map<string, Record<string, unknown>>();
    for (const reg of registrations.current.values()) {
      if (!reg.isDirty()) continue;
      const key = draftKey(reg.pageName, reg.period);
      const draft = reg.getDraft();
      const existing = merged.get(key) ?? {};
      if (draft && typeof draft === 'object' && !Array.isArray(draft)) {
        Object.assign(existing, draft as Record<string, unknown>);
      } else {
        existing.data = draft as unknown;
      }
      merged.set(key, existing);
    }
    for (const [key, value] of merged) {
      try {
        sessionStorage.setItem(key, JSON.stringify(value));
      } catch {
        /* quota / private mode */
      }
    }
  }, []);

  const register = useCallback((id: string, registration: UnsavedRegistration) => {
    registrations.current.set(id, registration);
    return () => {
      registrations.current.delete(id);
    };
  }, []);

  const peekDraft = useCallback(<T,>(pageName: string, period: string): T | null => {
    try {
      const raw = sessionStorage.getItem(draftKey(pageName, period));
      if (!raw) return null;
      return JSON.parse(raw) as T;
    } catch {
      return null;
    }
  }, []);

  const discardDraft = useCallback((pageName: string, period: string) => {
    sessionStorage.removeItem(draftKey(pageName, period));
  }, []);

  const hasUnsavedChanges = useCallback(() => {
    for (const reg of registrations.current.values()) {
      if (reg.isDirty()) return true;
    }
    return false;
  }, []);

  const confirmIfDirty = useCallback(
    (onProceed: () => void) => {
      if (!hasUnsavedChanges()) {
        onProceed();
        return;
      }
      void import('antd').then(({ Modal }) => {
        Modal.confirm({
          title: 'Unsaved changes',
          content:
            'You have unsaved changes on this page. Leave without saving? A draft will be kept for this browser session.',
          okText: 'Leave',
          cancelText: 'Stay',
          onOk: () => {
            persistDrafts();
            onProceed();
          },
        });
      });
    },
    [hasUnsavedChanges, persistDrafts],
  );

  useEffect(() => {
    setBeforeLogout(() => persistDrafts());
    return () => setBeforeLogout(null);
  }, [setBeforeLogout, persistDrafts]);

  return (
    <UnsavedChangesContext.Provider
      value={{
        register,
        persistDrafts,
        peekDraft,
        discardDraft,
        hasUnsavedChanges,
        confirmIfDirty,
      }}
    >
      {children}
    </UnsavedChangesContext.Provider>
  );
}

export function useUnsavedChanges(): UnsavedChangesContextValue {
  const ctx = useContext(UnsavedChangesContext);
  if (!ctx) {
    throw new Error('useUnsavedChanges must be used within UnsavedChangesProvider');
  }
  return ctx;
}
