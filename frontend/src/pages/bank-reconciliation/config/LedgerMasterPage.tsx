import { useEffect, useMemo, useState } from 'react';
import { Button, Form, Input, Modal, Select, Table, Typography, Upload, notification } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { AdminGate } from '@/components/AdminGate';
import { HEADING_FONT } from '@/theme/antdTheme';
import { addLedger, fetchLedgers, importLedgers } from '../api';
import type { Ledger } from '../types';

const { Title, Text } = Typography;

const GROUPS = [
  'Bank Accounts',
  'Cash-in-Hand',
  'Deposits (Asset)',
  'Loans & Advances (Asset)',
  'Stock-in-Hand',
  'Sundry Debtors',
  'Fixed Assets',
  'Investments',
  'Misc. Expenses (ASSET)',
  'Capital Account',
  'Current Liabilities',
  'Loans (Liability)',
  'Provisions',
  'Reserves & Surplus',
  'Sundry Creditors',
  'Bank OD Accounts',
  'Duties & Taxes',
  'Indirect Income',
  'Direct Income',
  'Sales Accounts',
  'Indirect Expenses',
  'Direct Expenses',
  'Purchase Accounts',
  'Suspense A/c',
  'Branch/Divisions',
  'Secured Loans',
  'Unsecured Loans',
  'Current Assets',
];

export default function LedgerMasterPage() {
  const [search, setSearch] = useState('');
  const [rows, setRows] = useState<Ledger[]>([]);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();

  const load = () => {
    setLoading(true);
    fetchLedgers(search, 0, 500)
      .then((p) => setRows(p.content))
      .catch(() => notification.error({ message: 'Failed to load ledgers' }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search]);

  const grouped = useMemo(() => {
    const counts = new Map<string, number>();
    rows.forEach((r) => counts.set(r.accountingNature, (counts.get(r.accountingNature) ?? 0) + 1));
    return counts;
  }, [rows]);

  const columns: ColumnsType<Ledger> = [
    { title: 'Ledger', dataIndex: 'ledgerName' },
    { title: 'Group', dataIndex: 'groupName' },
    { title: 'Nature', dataIndex: 'accountingNature', width: 120 },
    {
      title: 'Bank',
      dataIndex: 'bankAccount',
      width: 80,
      render: (v: boolean) => (v ? 'Yes' : ''),
    },
  ];

  return (
    <div style={{ padding: 28 }}>
      <Title level={3} style={{ fontFamily: HEADING_FONT }}>
        Ledger master
      </Title>
      <Text type="secondary">
        Asset {grouped.get('Asset') ?? 0} · Liability {grouped.get('Liability') ?? 0} · Income{' '}
        {grouped.get('Income') ?? 0} · Expense {grouped.get('Expense') ?? 0}
      </Text>
      <div style={{ display: 'flex', gap: 12, margin: '16px 0' }}>
        <Input.Search placeholder="Search ledgers" allowClear onSearch={setSearch} style={{ maxWidth: 280 }} />
        <AdminGate>
          <Upload
            accept=".xml"
            showUploadList={false}
            beforeUpload={(file) => {
              importLedgers(file)
                .then((res) => {
                  notification.success({ message: `Imported ${res.imported}, updated ${res.updated}` });
                  load();
                })
                .catch((err) =>
                  notification.error({
                    message: 'Import failed',
                    description: err.response?.data?.message,
                  }),
                );
              return false;
            }}
          >
            <Button>Import XML</Button>
          </Upload>
        </AdminGate>
        <AdminGate>
          <Button type="primary" onClick={() => setOpen(true)}>
            Add Ledger
          </Button>
        </AdminGate>
      </div>
      <Table rowKey="id" loading={loading} dataSource={rows} columns={columns} pagination={false} />
      <Modal
        title="Add ledger"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() =>
          form.validateFields().then((values) =>
            addLedger(values.ledgerName, values.groupName).then(() => {
              setOpen(false);
              form.resetFields();
              load();
            }),
          )
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item name="ledgerName" label="Ledger name" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="groupName" label="Tally group" rules={[{ required: true }]}>
            <Select
              showSearch
              options={GROUPS.map((g) => ({ value: g, label: g }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
