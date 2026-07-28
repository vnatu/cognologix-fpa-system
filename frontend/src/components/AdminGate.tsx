import { Tooltip } from 'antd';
import type { ReactElement } from 'react';
import { cloneElement, isValidElement } from 'react';
import { useAuth } from '@/context/AuthContext';

/** Wraps a write-action control: Viewers see a disabled control with an admin tooltip. */
export function AdminGate({
  children,
  fallback,
}: {
  children: ReactElement;
  /** When true, hide entirely for viewers instead of disabling */
  fallback?: 'hide' | 'disable';
}) {
  const { isAdmin } = useAuth();
  if (isAdmin()) return children;

  if (fallback === 'hide') return null;

  if (!isValidElement(children)) return null;

  const disabled = cloneElement(children, {
    disabled: true,
    onClick: undefined,
  } as Record<string, unknown>);

  return (
    <Tooltip title="Admin access required">
      <span style={{ display: 'inline-block' }}>{disabled}</span>
    </Tooltip>
  );
}

export function useIsAdmin(): boolean {
  return useAuth().isAdmin();
}
