import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Modal, Space, Table, Tag, Typography, notification } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { HEADING_FONT } from '@/theme/antdTheme';
import { AdminGate } from '@/components/AdminGate';
import { fetchRun, fetchRuns } from './api';
import { confirmDiscardRun } from './discardRun';
import type { ReconRun } from './types';

const { Title } = Typography;

export default function RunHistoryPage() {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [data, setData] = useState<{ content: ReconRun[]; total: number }>({ content: [], total: 0 });
  const [closedRun, setClosedRun] = useState<ReconRun | null>(null);

  const load = (p = page) => {
    setLoading(true);
    fetchRuns(p - 1, 20)
      .then((res) => setData({ content: res.content, total: res.totalElements }))
      .catch(() => notification.error({ message: 'Failed to load run history' }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page]);

  const columns: ColumnsType<ReconRun> = [
    { title: 'Run #', dataIndex: 'runNumber', width: 90 },
    { title: 'Account', dataIndex: 'accountNumber' },
    {
      title: 'Statement Period',
      render: (_, r) =>
        r.statementPeriodStart || r.statementPeriodEnd
          ? `${r.statementPeriodStart ?? ''} → ${r.statementPeriodEnd ?? ''}`
          : '—',
    },
    { title: 'Total', dataIndex: 'totalTransactions', width: 70 },
    { title: 'Mapped', dataIndex: 'mappedCount', width: 80 },
    { title: 'Unmapped', dataIndex: 'unmappedCount', width: 90 },
    { title: 'Excluded', dataIndex: 'excludedCount', width: 90 },
    { title: 'Exports', dataIndex: 'exportCount', width: 80 },
    {
      title: 'Status',
      dataIndex: 'status',
      width: 100,
      render: (s: string) => <Tag color={s === 'OPEN' ? 'blue' : 'default'}>{s}</Tag>,
    },
    {
      title: 'Date',
      dataIndex: 'createdAt',
      width: 120,
      render: (v: string) => v?.slice(0, 10),
    },
    {
      title: 'View',
      width: 170,
      render: (_, r) => (
        <Space size={0}>
          <Button
            type="link"
            onClick={() => {
              if (r.status === 'OPEN') {
                navigate(`/bank-reconciliation/runs/${r.id}`);
              } else {
                fetchRun(r.id).then(setClosedRun);
              }
            }}
          >
            View
          </Button>
          <AdminGate fallback="hide">
            <Button
              type="link"
              danger
              onClick={() => {
                confirmDiscardRun(r)
                  .then(() => {
                    notification.success({ message: `Discarded ${r.runNumber}` });
                    setClosedRun((current) => (current?.id === r.id ? null : current));
                    setData((prev) => ({
                      content: prev.content.filter((row) => row.id !== r.id),
                      total: Math.max(0, prev.total - 1),
                    }));
                  })
                  .catch((err) =>
                    notification.error({
                      message: 'Discard failed',
                      description: err.response?.data?.message ?? err.message,
                    }),
                  );
              }}
            >
              Discard
            </Button>
          </AdminGate>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 28 }}>
      <Title level={3} style={{ fontFamily: HEADING_FONT }}>
        Run history
      </Title>
      <Table
        rowKey="id"
        loading={loading}
        dataSource={data.content}
        columns={columns}
        pagination={{
          current: page,
          pageSize: 20,
          total: data.total,
          onChange: setPage,
        }}
      />
      <Modal
        title={closedRun ? `${closedRun.runNumber} (closed)` : 'Run'}
        open={!!closedRun}
        onCancel={() => setClosedRun(null)}
        footer={null}
      >
        {closedRun && (
          <div>
            <p>Account: {closedRun.accountNumber ?? '—'}</p>
            <p>
              Period: {closedRun.statementPeriodStart ?? '—'} → {closedRun.statementPeriodEnd ?? '—'}
            </p>
            <p>Total {closedRun.totalTransactions}</p>
            <p>Mapped {closedRun.mappedCount}</p>
            <p>Unmapped {closedRun.unmappedCount}</p>
            <p>Excluded {closedRun.excludedCount}</p>
            <p>Exports {closedRun.exportCount}</p>
          </div>
        )}
      </Modal>
    </div>
  );
}
