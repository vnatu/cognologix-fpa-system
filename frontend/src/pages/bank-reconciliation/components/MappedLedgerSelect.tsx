import { useEffect, useRef, useState } from 'react';
import { Select, Spin } from 'antd';
import { fetchLedgers } from '../api';
import type { VoucherType } from '../types';

type LedgerOption = { value: string; label: string };

function optionFor(name: string): LedgerOption {
  return { value: name, label: name };
}

export default function MappedLedgerSelect({
  voucherType,
  mappedLedger,
  disabled,
  onChange,
}: {
  voucherType: VoucherType;
  mappedLedger: string | null;
  disabled: boolean;
  onChange: (ledgerName: string | undefined) => void;
}) {
  const [options, setOptions] = useState<LedgerOption[]>(
    mappedLedger ? [optionFor(mappedLedger)] : [],
  );
  const [fetching, setFetching] = useState(false);
  const timerRef = useRef<number | undefined>(undefined);
  const requestId = useRef(0);

  useEffect(() => {
    setOptions(mappedLedger ? [optionFor(mappedLedger)] : []);
  }, [voucherType]);

  useEffect(() => {
    setOptions((prev) => {
      if (!mappedLedger) return prev;
      if (prev.some((o) => o.value === mappedLedger)) return prev;
      return [optionFor(mappedLedger), ...prev];
    });
  }, [mappedLedger]);

  useEffect(() => {
    return () => {
      if (timerRef.current) window.clearTimeout(timerRef.current);
    };
  }, []);

  const load = (query: string) => {
    const id = ++requestId.current;
    setFetching(true);
    fetchLedgers(query, 0, 20, voucherType)
      .then((page) => {
        if (id !== requestId.current) return;
        const next = page.content.map((l) => optionFor(l.ledgerName));
        if (mappedLedger && !next.some((o) => o.value === mappedLedger)) {
          next.unshift(optionFor(mappedLedger));
        }
        setOptions(next);
      })
      .catch(() => undefined)
      .finally(() => {
        if (id === requestId.current) setFetching(false);
      });
  };

  const onSearch = (query: string) => {
    if (timerRef.current) window.clearTimeout(timerRef.current);
    timerRef.current = window.setTimeout(() => load(query), 300);
  };

  return (
    <Select
      size="small"
      showSearch
      allowClear
      filterOption={false}
      style={{ width: 220 }}
      value={mappedLedger ?? undefined}
      disabled={disabled}
      options={options}
      notFoundContent={fetching ? <Spin size="small" /> : null}
      onSearch={onSearch}
      onChange={(ledgerName: string | undefined) => onChange(ledgerName)}
    />
  );
}
