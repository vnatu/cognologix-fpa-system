import { useEffect, useMemo, useState } from 'react';
import {
  Button,
  Drawer,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
  notification,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { AdminGate, useIsAdmin } from '@/components/AdminGate';
import { HEADING_FONT } from '@/theme/antdTheme';
import { addLedger, fetchLedgerHint, fetchLedgers, importLedgers, saveLedgerHint } from '../api';
import type { Ledger, LedgerHint } from '../types';

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
  const isAdmin = useIsAdmin();
  const [search, setSearch] = useState('');
  const [hintFilter, setHintFilter] = useState<'all' | 'yes' | 'no'>('all');
  const [rows, setRows] = useState<Ledger[]>([]);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();
  const [hintLedger, setHintLedger] = useState<Ledger | null>(null);
  const [hintForm] = Form.useForm();
  const [hintLoading, setHintLoading] = useState(false);
  const [hintSaving, setHintSaving] = useState(false);

  const load = () => {
    setLoading(true);
    const hasHint = hintFilter === 'all' ? undefined : hintFilter === 'yes';
    fetchLedgers(search, 0, 500, undefined, { hasHint })
      .then((p) => setRows(p.content))
      .catch(() => notification.error({ message: 'Failed to load ledgers' }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search, hintFilter]);

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
    {
      title: 'Hint',
      dataIndex: 'hasHint',
      width: 90,
      render: (hasHint: boolean) => (hasHint ? <Tag>Hint</Tag> : null),
    },
  ];

  const openHint = (ledger: Ledger) => {
    setHintLedger(ledger);
    setHintLoading(true);
    fetchLedgerHint(ledger.id)
      .then((hint) => hintForm.setFieldsValue(hintFields(hint)))
      .catch(() => notification.error({ message: 'Failed to load hint' }))
      .finally(() => setHintLoading(false));
  };

  const persistHint = (values: HintForm) => {
    if (!hintLedger) return;
    setHintSaving(true);
    saveLedgerHint(hintLedger.id, values)
      .then(() => {
        notification.success({ message: valuesEmpty(values) ? 'Hint cleared' : 'Hint saved' });
        setHintLedger(null);
        load();
      })
      .catch((err) =>
        notification.error({
          message: 'Could not save hint',
          description: err.response?.data?.message,
        }),
      )
      .finally(() => setHintSaving(false));
  };

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
        <Select
          value={hintFilter}
          style={{ width: 160 }}
          onChange={setHintFilter}
          options={[
            { value: 'all', label: 'All ledgers' },
            { value: 'yes', label: 'Has hint' },
            { value: 'no', label: 'No hint' },
          ]}
        />
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
      <Table
        rowKey="id"
        loading={loading}
        dataSource={rows}
        columns={columns}
        pagination={false}
        onRow={(record) => ({
          onClick: () => openHint(record),
          style: { cursor: 'pointer' },
        })}
      />
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
      <Drawer
        title={hintLedger ? hintLedger.ledgerName : 'Ledger hint'}
        open={hintLedger != null}
        onClose={() => setHintLedger(null)}
        width={420}
        extra={
          isAdmin ? (
            <Space>
              <Button
                onClick={() => persistHint(emptyHint())}
                loading={hintSaving}
              >
                Clear
              </Button>
              <Button type="primary" loading={hintSaving} onClick={() => hintForm.validateFields().then(persistHint)}>
                Save
              </Button>
            </Space>
          ) : null
        }
      >
        <Form form={hintForm} layout="vertical" disabled={!isAdmin || hintLoading}>
          <Form.Item name="purpose" label="Purpose">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
          <Form.Item name="keywords" label="Keywords" extra="Comma-separated narration words.">
            <Input maxLength={500} />
          </Form.Item>
          <Form.Item name="typicalAmount" label="Typical amount">
            <Input maxLength={255} placeholder="small, under ₹5,000" />
          </Form.Item>
          <Form.Item name="disambiguationNote" label="Disambiguation note">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
        </Form>
      </Drawer>
    </div>
  );
}

type HintForm = {
  purpose?: string;
  keywords?: string;
  typicalAmount?: string;
  disambiguationNote?: string;
};

function hintFields(hint: LedgerHint): HintForm {
  return {
    purpose: hint.purpose ?? '',
    keywords: hint.keywords ?? '',
    typicalAmount: hint.typicalAmount ?? '',
    disambiguationNote: hint.disambiguationNote ?? '',
  };
}

function emptyHint(): HintForm {
  return { purpose: '', keywords: '', typicalAmount: '', disambiguationNote: '' };
}

function valuesEmpty(values: HintForm): boolean {
  return !values.purpose?.trim() && !values.keywords?.trim()
    && !values.typicalAmount?.trim() && !values.disambiguationNote?.trim();
}
