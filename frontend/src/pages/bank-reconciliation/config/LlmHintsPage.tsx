import { useEffect, useState } from 'react';
import { Button, Form, Input, Modal, Select, Switch, Table, Typography, notification } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { AdminGate } from '@/components/AdminGate';
import { HEADING_FONT } from '@/theme/antdTheme';
import { createHint, deleteHint, fetchHints, updateHint } from '../api';
import type { LlmHint } from '../types';

const { Title } = Typography;

export default function LlmHintsPage() {
  const [rows, setRows] = useState<LlmHint[]>([]);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<LlmHint | null>(null);
  const [form] = Form.useForm();

  const load = () => {
    setLoading(true);
    fetchHints()
      .then(setRows)
      .catch(() => notification.error({ message: 'Failed to load hints' }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const columns: ColumnsType<LlmHint> = [
    { title: 'Hint', dataIndex: 'hintText' },
    { title: 'Voucher', dataIndex: 'voucherType', width: 120 },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 90,
      render: (v, r) => (
        <Switch
          size="small"
          checked={v}
          onChange={(active) => updateHint(r.id, { active }).then(load)}
        />
      ),
    },
    {
      title: '',
      width: 140,
      render: (_, r) => (
        <AdminGate>
          <span>
            <Button
              type="link"
              onClick={() => {
                setEditing(r);
                form.setFieldsValue(r);
                setOpen(true);
              }}
            >
              Edit
            </Button>
            <Button danger type="link" onClick={() => deleteHint(r.id).then(load)}>
              Delete
            </Button>
          </span>
        </AdminGate>
      ),
    },
  ];

  return (
    <div style={{ padding: 28 }}>
      <Title level={3} style={{ fontFamily: HEADING_FONT }}>
        LLM hints
      </Title>
      <AdminGate>
        <Button
          type="primary"
          style={{ margin: '12px 0' }}
          onClick={() => {
            setEditing(null);
            form.resetFields();
            setOpen(true);
          }}
        >
          Add hint
        </Button>
      </AdminGate>
      <Table rowKey="id" loading={loading} dataSource={rows} columns={columns} />
      <Modal
        title={editing ? 'Edit hint' : 'Add hint'}
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() =>
          form.validateFields().then((values) => {
            const req = editing
              ? updateHint(editing.id, values)
              : createHint(values);
            return req.then(() => {
              setOpen(false);
              load();
            });
          })
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item name="hintText" label="Hint" rules={[{ required: true }]}>
            <Input.TextArea rows={3} />
          </Form.Item>
          <Form.Item name="voucherType" label="Voucher type" initialValue="ALL">
            <Select
              options={['ALL', 'PAYMENT', 'RECEIPT', 'CONTRA'].map((v) => ({ value: v, label: v }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
