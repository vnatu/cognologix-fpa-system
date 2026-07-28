import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Badge,
  Button,
  Input,
  InputNumber,
  Modal,
  Select,
  Skeleton,
  Space,
  Table,
  Tag,
  theme,
  Typography,
  notification,
} from 'antd';
import {
  DownloadOutlined,
  LockOutlined,
  SaveOutlined,
  UnlockOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useIsAdmin } from '@/components/AdminGate';
import SimpleExcelImportModal from '@/components/SimpleExcelImportModal';
import { HEADING_FONT } from '@/theme/antdTheme';
import {
  downloadExpenseSample,
  exportExpenses,
  fetchMonthlyExpenses,
  importExpenses,
  lockMonth,
  saveMonthlyExpenses,
  unlockMonth,
} from './api';
import type { ExpenseEntry } from './types';

const { Text } = Typography;

const MONTH_OPTIONS = Array.from({ length: 12 }, (_, i) => ({
  value: i + 1,
  label: new Date(2000, i, 1).toLocaleString('en-IN', { month: 'long' }),
}));

type TableRow =
  | { kind: 'group'; key: string; categoryGroup: string }
  | { kind: 'entry'; key: string; entry: ExpenseEntry };

function currentMonthYear(): { month: number; year: number } {
  const now = new Date();
  return { month: now.getMonth() + 1, year: now.getFullYear() };
}

export default function ExpenseEntryPage() {
  const isAdmin = useIsAdmin();
  const { token } = theme.useToken();
  const [searchParams] = useSearchParams();
  const defaults = currentMonthYear();

  const [month, setMonth] = useState(
    Number(searchParams.get('month')) || defaults.month,
  );
  const [year, setYear] = useState(
    Number(searchParams.get('year')) || defaults.year,
  );

  useEffect(() => {
    const m = Number(searchParams.get('month'));
    const y = Number(searchParams.get('year'));
    if (m >= 1 && m <= 12) setMonth(m);
    if (y >= 2000 && y <= 2100) setYear(y);
  }, [searchParams]);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [locked, setLocked] = useState(false);
  const [lockedBy, setLockedBy] = useState<string | null>(null);
  const [entries, setEntries] = useState<ExpenseEntry[]>([]);
  const [draftAmounts, setDraftAmounts] = useState<Record<string, number>>({});
  const [draftNotes, setDraftNotes] = useState<Record<string, string>>({});
  const [dirty, setDirty] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [unlockOpen, setUnlockOpen] = useState(false);
  const [unlockReason, setUnlockReason] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchMonthlyExpenses(month, year);
      setLocked(data.locked);
      setLockedBy(data.lockedBy);
      setEntries(data.entries);
      const amounts: Record<string, number> = {};
      const notes: Record<string, string> = {};
      for (const e of data.entries) {
        amounts[e.categoryId] = Number(e.amount ?? 0);
        notes[e.categoryId] = e.notes ?? '';
      }
      setDraftAmounts(amounts);
      setDraftNotes(notes);
      setDirty(false);
    } catch {
      notification.error({ message: 'Failed to load expenses' });
    } finally {
      setLoading(false);
    }
  }, [month, year]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      if (dirty) {
        e.preventDefault();
        e.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [dirty]);

  const yearOptions = useMemo(() => {
    const y = defaults.year;
    return [y - 2, y - 1, y, y + 1].map((value) => ({ value, label: String(value) }));
  }, [defaults.year]);

  const tableRows: TableRow[] = useMemo(() => {
    const rows: TableRow[] = [];
    let lastGroup = '';
    for (const entry of entries) {
      if (entry.categoryGroup !== lastGroup) {
        lastGroup = entry.categoryGroup;
        rows.push({
          kind: 'group',
          key: `group-${entry.categoryGroup}`,
          categoryGroup: entry.categoryGroup,
        });
      }
      rows.push({ kind: 'entry', key: entry.categoryId, entry });
    }
    return rows;
  }, [entries]);

  const editable = isAdmin && !locked;

  const handleSave = async () => {
    setSaving(true);
    try {
      await saveMonthlyExpenses(
        month,
        year,
        entries.map((e) => ({
          categoryId: e.categoryId,
          amount: draftAmounts[e.categoryId] ?? 0,
          notes: draftNotes[e.categoryId] || null,
        })),
      );
      notification.success({ message: 'Expenses saved' });
      setDirty(false);
      await load();
    } catch {
      notification.error({ message: 'Failed to save expenses' });
    } finally {
      setSaving(false);
    }
  };

  const handleLock = () => {
    Modal.confirm({
      title: 'Lock this month?',
      content: 'Locked months cannot be edited until unlocked with a reason.',
      okText: 'Lock Month',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await lockMonth(month, year);
          notification.success({ message: 'Month locked' });
          await load();
        } catch {
          notification.error({ message: 'Failed to lock month' });
        }
      },
    });
  };

  const handleUnlock = async () => {
    if (!unlockReason.trim()) {
      notification.warning({ message: 'Unlock reason is required' });
      return;
    }
    try {
      await unlockMonth(month, year, unlockReason.trim());
      notification.success({ message: 'Month unlocked' });
      setUnlockOpen(false);
      setUnlockReason('');
      await load();
    } catch {
      notification.error({ message: 'Failed to unlock month' });
    }
  };

  const columns: ColumnsType<TableRow> = [
    {
      title: 'Category Name',
      key: 'name',
      render: (_, row) => {
        if (row.kind === 'group') {
          return (
            <Text strong style={{ fontFamily: HEADING_FONT }}>
              {row.categoryGroup}
            </Text>
          );
        }
        return row.entry.displayName;
      },
    },
    {
      title: 'Notes',
      key: 'notes',
      width: 280,
      render: (_, row) => {
        if (row.kind === 'group') return null;
        return (
          <Input
            value={draftNotes[row.entry.categoryId] ?? ''}
            disabled={!editable}
            placeholder="Optional"
            maxLength={500}
            onChange={(e) => {
              setDraftNotes((prev) => ({
                ...prev,
                [row.entry.categoryId]: e.target.value,
              }));
              setDirty(true);
            }}
          />
        );
      },
    },
    {
      title: 'Amount (Rs Lakhs)',
      key: 'amount',
      width: 180,
      align: 'right',
      render: (_, row) => {
        if (row.kind === 'group') return null;
        return (
          <InputNumber
            style={{ width: '100%' }}
            min={0}
            precision={2}
            disabled={!editable}
            value={draftAmounts[row.entry.categoryId] ?? 0}
            onChange={(value) => {
              setDraftAmounts((prev) => ({
                ...prev,
                [row.entry.categoryId]: value ?? 0,
              }));
              setDirty(true);
            }}
          />
        );
      },
    },
  ];

  return (
    <div style={{ padding: 28, maxWidth: 1100 }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          gap: 16,
          marginBottom: 20,
          flexWrap: 'wrap',
        }}
      >
        <div>
          <div
            style={{
              fontFamily: HEADING_FONT,
              fontWeight: 700,
              fontSize: 22,
              color: token.colorTextHeading,
            }}
          >
            Expense Entry
          </div>
          <Text type="secondary">Capture monthly overhead actuals (Rs Lakhs)</Text>
        </div>
        <Space wrap>
          <Select
            style={{ width: 140 }}
            options={MONTH_OPTIONS}
            value={month}
            onChange={(v) => {
              if (dirty) {
                Modal.confirm({
                  title: 'Unsaved changes',
                  content: 'Discard unsaved changes and switch month?',
                  onOk: () => setMonth(v),
                });
              } else {
                setMonth(v);
              }
            }}
          />
          <Select
            style={{ width: 100 }}
            options={yearOptions}
            value={year}
            onChange={(v) => {
              if (dirty) {
                Modal.confirm({
                  title: 'Unsaved changes',
                  content: 'Discard unsaved changes and switch year?',
                  onOk: () => setYear(v),
                });
              } else {
                setYear(v);
              }
            }}
          />
          {locked ? (
            <Tag color="error">
              <LockOutlined /> Locked
              {lockedBy ? ` by ${lockedBy}` : ''}
            </Tag>
          ) : (
            <Badge status="success" text="Open" />
          )}
          {isAdmin && !locked && (
            <Button danger icon={<LockOutlined />} onClick={handleLock}>
              Lock Month
            </Button>
          )}
          {isAdmin && locked && (
            <Button icon={<UnlockOutlined />} onClick={() => setUnlockOpen(true)}>
              Unlock Month
            </Button>
          )}
        </Space>
      </div>

      <Space wrap style={{ marginBottom: 16 }}>
        <Button
          icon={<DownloadOutlined />}
          onClick={() =>
            exportExpenses(month, year).catch(() =>
              notification.error({ message: 'Export failed' }),
            )
          }
        >
          Export
        </Button>
        {isAdmin && (
          <Button
            icon={<UploadOutlined />}
            disabled={locked}
            onClick={() => setImportOpen(true)}
          >
            Import
          </Button>
        )}
        <Button
          onClick={() =>
            downloadExpenseSample().catch(() =>
              notification.error({ message: 'Sample download failed' }),
            )
          }
        >
          Download Sample
        </Button>
      </Space>

      {loading ? (
        <Skeleton active paragraph={{ rows: 10 }} />
      ) : (
        <>
          <Table<TableRow>
            rowKey="key"
            columns={columns}
            dataSource={tableRows}
            pagination={false}
            size="middle"
            onRow={(row) =>
              row.kind === 'group'
                ? {
                    style: {
                      background: token.colorFillSecondary,
                      fontWeight: 600,
                    },
                  }
                : {}
            }
          />
          {isAdmin && (
            <div style={{ marginTop: 20 }}>
              <Button
                type="primary"
                icon={<SaveOutlined />}
                loading={saving}
                disabled={locked || !dirty}
                onClick={() => void handleSave()}
              >
                Save
              </Button>
            </div>
          )}
        </>
      )}

      <SimpleExcelImportModal
        open={importOpen}
        title="Import Expenses"
        onClose={() => setImportOpen(false)}
        onImported={() => void load()}
        importFile={async (file) => {
          const r = await importExpenses(month, year, file);
          return {
            totalRows: r.totalRows,
            created: r.created + r.updated,
            skipped: r.skipped,
            errors: r.errors,
          };
        }}
      />

      <Modal
        title="Unlock Month"
        open={unlockOpen}
        onCancel={() => {
          setUnlockOpen(false);
          setUnlockReason('');
        }}
        onOk={() => void handleUnlock()}
        okText="Unlock"
      >
        <Text type="secondary">Provide a reason for unlocking (required).</Text>
        <Input.TextArea
          style={{ marginTop: 12 }}
          rows={3}
          value={unlockReason}
          onChange={(e) => setUnlockReason(e.target.value)}
          placeholder="Reason for unlock"
        />
      </Modal>
    </div>
  );
}
