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
import { useAuth } from '@/context/AuthContext';
import {
  DATE_FORMAT_OPTIONS,
  formatDate as formatDateUtil,
  formatDateTime as formatDateTimeUtil,
  type DateFormatOption,
} from '@/utils/formatDate';

const DEFAULT_FORMAT: DateFormatOption = 'DD MMM YYYY';

interface DateFormatContextValue {
  format: DateFormatOption;
  loading: boolean;
  setFormat: (format: DateFormatOption) => void;
  formatDate: (date: string | Date | null | undefined) => string;
  formatDateTime: (date: string | Date | null | undefined) => string;
}

const DateFormatContext = createContext<DateFormatContextValue | null>(null);

export function DateFormatProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth();
  const [format, setFormatState] = useState<DateFormatOption>(DEFAULT_FORMAT);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!isAuthenticated) {
      setLoading(false);
      return;
    }

    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const { data } = await axios.get<{ format: string }>(
          '/api/general/config/date-format',
        );
        if (!cancelled && DATE_FORMAT_OPTIONS.includes(data.format as DateFormatOption)) {
          setFormatState(data.format as DateFormatOption);
        }
      } catch {
        // API unavailable — keep default
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [isAuthenticated]);

  const setFormat = useCallback((next: DateFormatOption) => {
    setFormatState(next);
  }, []);

  const formatDate = useCallback(
    (date: string | Date | null | undefined) => formatDateUtil(date, format),
    [format],
  );

  const formatDateTime = useCallback(
    (date: string | Date | null | undefined) =>
      formatDateTimeUtil(date, format),
    [format],
  );

  const value = useMemo(
    () => ({ format, loading, setFormat, formatDate, formatDateTime }),
    [format, loading, setFormat, formatDate, formatDateTime],
  );

  return (
    <DateFormatContext.Provider value={value}>
      {children}
    </DateFormatContext.Provider>
  );
}

export function useDateFormat(): DateFormatContextValue {
  const ctx = useContext(DateFormatContext);
  if (!ctx) {
    throw new Error('useDateFormat must be used within DateFormatProvider');
  }
  return ctx;
}
