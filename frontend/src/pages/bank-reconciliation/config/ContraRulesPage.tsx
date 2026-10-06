import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Button, Form, Input, Modal, Select, Table, Typography, notification } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { AdminGate } from '@/components/AdminGate';
import { HEADING_FONT } from '@/theme/antdTheme';
import { createContraRule, deleteContraRule, fetchContraRules, fetchOllamaConfig } from '../api';
import type { ContraRule } from '../types';

const { Title, Text } = Typography;

export default function ContraRulesPage() {
  const [rows, setRows] = useState<ContraRule[]>([]);
  const [companyName, setCompanyName] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();

  const load = () => {
    setLoading(true);
    Promise.all([fetchContraRules(), fetchOllamaConfig()])
      .then(([rules, config]) => {
        setRows(rules);
        setCompanyName(config.companyName ?? '');
      })
      .catch(() => notification.error({ message: 'Failed to load contra rules' }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const columns: ColumnsType<ContraRule> = [
    { title: 'Pattern type', dataIndex: 'patternType', width: 140 },
    { title: 'Value', dataIndex: 'patternValue' },
    {
      title: '',
      width: 90,
      render: (_, r) => (
        <AdminGate>
          <Button danger type="link" onClick={() => deleteContraRule(r.id).then(load)}>
            Delete
          </Button>
        </AdminGate>
      ),
    },
  ];

  return (
    <div style={{ padding: 28 }}>
      <Title level={3} style={{ fontFamily: HEADING_FONT }}>
        Contra rules
      </Title>
      <Text type="secondary">
        A narration is contra when it matches a rule below and also contains the company name.
      </Text>
      {companyName !== null && (
        <Alert
          style={{ marginTop: 12 }}
          type={companyName.trim() ? 'info' : 'warning'}
          showIcon
          message={
            companyName.trim()
              ? `Company name these rules use: ${companyName.trim()}`
              : 'Company name these rules use: (empty)'
          }
          description={
            <>
              The check ignores letter case and extra spaces. A transfer is contra only when the narration
              matches a rule and contains this name. An empty or wrong name classifies internal transfers as
              Payment or Receipt.{' '}
              <Link to="/bank-reconciliation/config/ollama">Change it in LLM settings</Link>.
            </>
          }
        />
      )}
      <AdminGate>
        <Button type="primary" style={{ margin: '12px 0' }} onClick={() => setOpen(true)}>
          Add rule
        </Button>
      </AdminGate>
      <Table rowKey="id" loading={loading} dataSource={rows} columns={columns} pagination={false} />
      <Modal
        title="Add contra rule"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() =>
          form.validateFields().then((values) =>
            createContraRule(values.patternType, values.patternValue).then(() => {
              setOpen(false);
              form.resetFields();
              load();
            }),
          )
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item name="patternType" label="Type" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'PREFIX', label: 'PREFIX' },
                { value: 'CONTAINS', label: 'CONTAINS' },
              ]}
            />
          </Form.Item>
          <Form.Item name="patternValue" label="Value" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
