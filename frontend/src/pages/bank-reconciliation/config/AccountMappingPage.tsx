import { useEffect, useState } from 'react';
import {
  Button,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Typography,
  notification,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { AdminGate } from '@/components/AdminGate';
import { HEADING_FONT } from '@/theme/antdTheme';
import {
  createAccountMapping,
  deleteAccountMapping,
  fetchAccountMappings,
  fetchLedgers,
  updateAccountMapping,
} from '../api';
import type { AccountMapping, StatementType } from '../types';

const { Title, Text } = Typography;

const STATEMENT_TYPES: { value: StatementType; label: string }[] = [
  { value: 'HDFC_BANK', label: 'HDFC Bank' },
  { value: 'HSBC_CC', label: 'HSBC credit card' },
];

function statementLabel(type: StatementType): string {
  return STATEMENT_TYPES.find((item) => item.value === type)?.label ?? type;
}

export default function AccountMappingPage() {
  const [rows, setRows] = useState<AccountMapping[]>([]);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<AccountMapping | null>(null);
  const [ledgerOptions, setLedgerOptions] = useState<{ value: string; label: string }[]>([]);
  const [form] = Form.useForm();

  const load = () => {
    setLoading(true);
    fetchAccountMappings()
      .then(setRows)
      .catch(() => notification.error({ message: 'Failed to load account mappings' }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const searchLedgers = (query: string) => {
    fetchLedgers(query, 0, 20)
      .then((page) => setLedgerOptions(page.content.map((ledger) => ({
        value: ledger.ledgerName,
        label: ledger.ledgerName,
      }))))
      .catch(() => undefined);
  };

  const openForm = (row?: AccountMapping) => {
    setEditing(row ?? null);
    form.setFieldsValue(
      row ?? { statementType: 'HDFC_BANK', identifier: '', ledgerName: undefined, active: true },
    );
    setLedgerOptions(row ? [{ value: row.ledgerName, label: row.ledgerName }] : []);
    searchLedgers('');
    setOpen(true);
  };

  const save = () =>
    form.validateFields().then((values) => {
      const payload = {
        statementType: values.statementType as StatementType,
        identifier: values.identifier as string,
        ledgerName: values.ledgerName as string,
        active: Boolean(values.active),
      };
      const request = editing
        ? updateAccountMapping(editing.id, payload)
        : createAccountMapping(payload);
      return request
        .then((saved) => {
          if (saved.warning) {
            notification.warning({ message: 'Saved with a warning', description: saved.warning });
          } else {
            notification.success({ message: 'Saved' });
          }
          setOpen(false);
          form.resetFields();
          load();
        })
        .catch((err) =>
          notification.error({
            message: 'Could not save account mapping',
            description: err.response?.data?.message,
          }),
        );
    });

  const setActive = (row: AccountMapping, active: boolean) => {
    updateAccountMapping(row.id, {
      statementType: row.statementType,
      identifier: row.identifier,
      ledgerName: row.ledgerName,
      active,
    })
      .then((saved) => {
        if (saved.warning) {
          notification.warning({ message: 'Saved with a warning', description: saved.warning });
        }
        load();
      })
      .catch((err) =>
        notification.error({
          message: 'Could not update account mapping',
          description: err.response?.data?.message,
        }),
      );
  };

  const columns: ColumnsType<AccountMapping> = [
    {
      title: 'Statement type',
      dataIndex: 'statementType',
      width: 180,
      render: (type: StatementType) => statementLabel(type),
    },
    { title: 'Identifier', dataIndex: 'identifier' },
    { title: 'Ledger', dataIndex: 'ledgerName' },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 90,
      render: (active: boolean, row) => (
        <AdminGate>
          <Switch checked={active} onChange={(checked) => setActive(row, checked)} />
        </AdminGate>
      ),
    },
    {
      title: '',
      width: 140,
      render: (_, row) => (
        <Space>
          <AdminGate>
            <Button type="link" onClick={() => openForm(row)}>
              Edit
            </Button>
          </AdminGate>
          <AdminGate>
            <Button
              danger
              type="link"
              onClick={() =>
                deleteAccountMapping(row.id)
                  .then(load)
                  .catch((err) =>
                    notification.error({
                      message: 'Could not delete account mapping',
                      description: err.response?.data?.message,
                    }),
                  )
              }
            >
              Delete
            </Button>
          </AdminGate>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 28 }}>
      <Title level={3} style={{ fontFamily: HEADING_FONT }}>
        Account mapping
      </Title>
      <Text type="secondary">
        Maps a statement account to the Tally bank ledger used on every Payment, Receipt, and Contra.
      </Text>
      <AdminGate>
        <Button type="primary" style={{ margin: '12px 0' }} onClick={() => openForm()}>
          Add mapping
        </Button>
      </AdminGate>
      <Table rowKey="id" loading={loading} dataSource={rows} columns={columns} pagination={false} />
      <Modal
        title={editing ? 'Edit account mapping' : 'Add account mapping'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={save}
        okText="Save"
      >
        <Form form={form} layout="vertical">
          <Form.Item name="statementType" label="Statement type" rules={[{ required: true }]}>
            <Select options={STATEMENT_TYPES} />
          </Form.Item>
          <Form.Item
            name="identifier"
            label="Identifier"
            extra="HDFC: account number. HSBC credit card: last 4 digits."
            rules={[{ required: true }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="ledgerName" label="Ledger" rules={[{ required: true }]}>
            <Select
              showSearch
              filterOption={false}
              options={ledgerOptions}
              onSearch={searchLedgers}
              placeholder="Search ledgers"
            />
          </Form.Item>
          <Form.Item name="active" label="Active" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
