import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Button,
  Checkbox,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  notification,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { AdminGate, useIsAdmin } from '@/components/AdminGate';
import { HEADING_FONT } from '@/theme/antdTheme';
import { closeRun, exportRun, fetchRun, updateTransaction } from './api';
import MappedLedgerSelect from './components/MappedLedgerSelect';
import { confirmDiscardRun } from './discardRun';
import type { ReconRun, TransactionRow, VoucherType } from './types';

const { Title, Text } = Typography;

const SOURCE_COLOR: Record<string, string> = {
  LEARNED: 'green',
  LLM: 'blue',
  MANUAL: 'orange',
  UNMAPPED: 'red',
};

function sourceLabel(tx: TransactionRow): string {
  if (tx.excluded) return tx.mappingSource ?? 'UNMAPPED';
  return tx.mappedLedger ? (tx.mappingSource ?? 'MANUAL') : 'UNMAPPED';
}

function formatPeriod(run: ReconRun): string {
  if (!run.statementPeriodStart && !run.statementPeriodEnd) return '—';
  return `${run.statementPeriodStart ?? ''} → ${run.statementPeriodEnd ?? ''}`;
}

export default function RunReviewPage() {
  const { runId } = useParams();
  const navigate = useNavigate();
  const isAdmin = useIsAdmin();
  const [run, setRun] = useState<ReconRun | null>(null);
  const [loading, setLoading] = useState(true);
  const [exportOpen, setExportOpen] = useState(false);
  const [selected, setSelected] = useState<string[]>([]);

  const load = useCallback(() => {
    if (!runId) return;
    setLoading(true);
    fetchRun(runId)
      .then(setRun)
      .catch(() => notification.error({ message: 'Failed to load run' }))
      .finally(() => setLoading(false));
  }, [runId]);

  useEffect(() => {
    load();
  }, [load]);

  const patch = (tx: TransactionRow, payload: Parameters<typeof updateTransaction>[2]) => {
    if (!runId) return;
    updateTransaction(runId, tx.id, payload)
      .then(() => load())
      .catch((err) =>
        notification.error({
          message: 'Update failed',
          description: err.response?.data?.message ?? err.message,
        }),
      );
  };

  const unmappedIncluded = useMemo(
    () => (run?.transactions ?? []).filter((t) => !t.excluded && !t.mappedLedger).length,
    [run],
  );

  const columns: ColumnsType<TransactionRow> = [
    {
      title: 'Date',
      dataIndex: 'transactionDate',
      width: 120,
      render: (v: string | null) => (v ? v.slice(0, 10) : '—'),
    },
    { title: 'Narration', dataIndex: 'description', ellipsis: true },
    {
      title: 'Amount',
      dataIndex: 'amount',
      align: 'right',
      width: 110,
      render: (v: number) => Number(v).toLocaleString('en-IN'),
    },
    { title: 'Dr/Cr', dataIndex: 'debitCredit', width: 70 },
    {
      title: 'Voucher Type',
      dataIndex: 'voucherType',
      width: 140,
      render: (_, tx) => (
        <Select
          size="small"
          style={{ width: 120 }}
          value={tx.voucherType}
          disabled={!isAdmin || run?.status === 'CLOSED'}
          options={[
            { value: 'PAYMENT', label: 'Payment' },
            { value: 'RECEIPT', label: 'Receipt' },
            { value: 'CONTRA', label: 'Contra' },
          ]}
          onChange={(voucherType: VoucherType) => patch(tx, { voucherType })}
        />
      ),
    },
    {
      title: 'Mapped Ledger',
      dataIndex: 'mappedLedger',
      width: 240,
      render: (_, tx) => (
        <MappedLedgerSelect
          voucherType={tx.voucherType}
          mappedLedger={tx.mappedLedger}
          disabled={!isAdmin || run?.status === 'CLOSED'}
          onChange={(ledgerName) => patch(tx, { ledgerName: ledgerName ?? '' })}
        />
      ),
    },
    {
      title: 'Source',
      width: 120,
      render: (_, tx) => {
        const label = sourceLabel(tx);
        return <Tag color={SOURCE_COLOR[label] ?? 'default'}>{label}</Tag>;
      },
    },
    {
      title: 'Include',
      width: 80,
      render: (_, tx) => (
        <Checkbox
          checked={!tx.excluded}
          disabled={!isAdmin || run?.status === 'CLOSED'}
          onChange={(e) => patch(tx, { excluded: !e.target.checked })}
        />
      ),
    },
  ];

  if (loading && !run) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin />
      </div>
    );
  }
  if (!run) return null;

  return (
    <div style={{ padding: 28 }}>
      <Title level={3} style={{ fontFamily: HEADING_FONT, marginBottom: 4 }}>
        {run.runNumber}
      </Title>
      <Text type="secondary">
        Account {run.accountNumber ?? '—'}
        {run.customerName ? ` · ${run.customerName}` : ''}
        {' · '}
        {formatPeriod(run)}
        {run.openingBalance != null ? ` · Open ${run.openingBalance}` : ''}
        {run.closingBalance != null ? ` · Close ${run.closingBalance}` : ''}
        {' · '}
        {run.originalFilename}
      </Text>
      <Space size="large" style={{ display: 'flex', marginTop: 16, marginBottom: 16 }}>
        <Text>Total {run.totalTransactions}</Text>
        <Text style={{ color: '#389e0d' }}>Mapped {run.mappedCount}</Text>
        <Text style={{ color: '#cf1322' }}>Unmapped {run.unmappedCount}</Text>
        <Text type="secondary">Excluded {run.excludedCount}</Text>
        <AdminGate>
          <Button onClick={() => {
            setSelected(
              run.transactions.filter((t) => !t.excluded && t.mappedLedger).map((t) => t.id),
            );
            setExportOpen(true);
          }}>
            Export Selected
          </Button>
        </AdminGate>
        <AdminGate>
          <Button
            danger
            disabled={unmappedIncluded > 0 || run.status === 'CLOSED'}
            onClick={() =>
              Modal.confirm({
                title: 'Close this run?',
                content: 'Transactions will be purged. The run summary is kept.',
                onOk: () =>
                  closeRun(run.id).then(() => {
                    notification.success({ message: 'Run closed' });
                    navigate('/bank-reconciliation/run-history');
                  }),
              })
            }
          >
            Close Run
          </Button>
        </AdminGate>
        <AdminGate fallback="hide">
          <Button
            danger
            onClick={() => {
              confirmDiscardRun(run)
                .then(() => {
                  notification.success({ message: `Discarded ${run.runNumber}` });
                  navigate('/bank-reconciliation/run-history');
                })
                .catch((err) =>
                  notification.error({
                    message: 'Discard failed',
                    description: err.response?.data?.message ?? err.message,
                  }),
                );
            }}
          >
            Discard Run
          </Button>
        </AdminGate>
      </Space>
      <Table
        rowKey="id"
        size="small"
        loading={loading}
        dataSource={run.transactions}
        columns={columns}
        pagination={false}
        rowClassName={(tx) => (!tx.excluded && !tx.mappedLedger ? 'bankrecon-unmapped' : '')}
        onRow={(tx) => ({
          style:
            !tx.excluded && !tx.mappedLedger ? { background: '#fff1f0' } : undefined,
        })}
      />
      <Modal
        title="Select transactions to export"
        open={exportOpen}
        onCancel={() => setExportOpen(false)}
        onOk={() => {
          exportRun(run.id, selected, `finsync-${run.runNumber}-export.xlsx`)
            .then(() => {
              setExportOpen(false);
              load();
            })
            .catch((err) =>
              notification.error({
                message: 'Export failed',
                description: err.response?.data?.message ?? err.message,
              }),
            );
        }}
        okText="Generate Export"
      >
        <Checkbox.Group
          style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
          value={selected}
          onChange={(vals) => setSelected(vals as string[])}
          options={run.transactions
            .filter((t) => !t.excluded && t.mappedLedger)
            .map((t) => ({
              value: t.id,
              label: `${t.description.slice(0, 60)} — ${t.mappedLedger}`,
            }))}
        />
      </Modal>
    </div>
  );
}
