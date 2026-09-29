import { useEffect, useState } from 'react';
import {
  Button,
  Form,
  Input,
  Modal,
  Select,
  Skeleton,
  Space,
  Table,
  Tag,
  Typography,
  notification,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { AdminGate, useIsAdmin } from '@/components/AdminGate';
import { HEADING_FONT } from '@/theme/antdTheme';
import {
  apiError,
  createContractType,
  fetchContractConfig,
  fetchContractTypes,
  updateContractConfig,
  updateContractType,
} from '../contracts/api';
import type { ContractType } from '../contracts/types';

const { Title, Text } = Typography;

export default function ContractsTab() {
  const isAdmin = useIsAdmin();
  const [loading, setLoading] = useState(true);
  const [types, setTypes] = useState<ContractType[]>([]);
  const [reminderDays, setReminderDays] = useState('90,60,30,7');
  const [recipients, setRecipients] = useState<string[]>([]);
  const [savingConfig, setSavingConfig] = useState(false);
  const [typeOpen, setTypeOpen] = useState(false);
  const [form] = Form.useForm();

  const load = () => {
    setLoading(true);
    Promise.all([fetchContractTypes(true), fetchContractConfig()])
      .then(([typeRows, config]) => {
        setTypes(typeRows);
        setReminderDays(config.reminderDays);
        setRecipients(config.recipients);
      })
      .catch((error) => notification.error({ message: apiError(error, 'Failed to load contract settings') }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const addType = async () => {
    try {
      const values = await form.validateFields();
      await createContractType(values);
      notification.success({ message: 'Contract type added' });
      setTypeOpen(false);
      form.resetFields();
      load();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
      notification.error({ message: apiError(error, 'Failed to add contract type') });
    }
  };

  const deactivate = (type: ContractType) => {
    Modal.confirm({
      title: `Deactivate ${type.typeCode}?`,
      okText: 'Deactivate',
      okButtonProps: { danger: true },
      onOk: async () => {
        await updateContractType(type.id, {
          displayName: type.displayName,
          description: type.description,
          active: false,
        });
        notification.success({ message: 'Contract type deactivated' });
        load();
      },
    });
  };

  const saveConfig = async () => {
    setSavingConfig(true);
    try {
      const saved = await updateContractConfig({ reminderDays, recipients });
      setReminderDays(saved.reminderDays);
      setRecipients(saved.recipients);
      notification.success({ message: 'Notification settings saved' });
    } catch (error) {
      notification.error({ message: apiError(error, 'Failed to save notification settings') });
    } finally {
      setSavingConfig(false);
    }
  };

  const columns: ColumnsType<ContractType> = [
    { title: 'Code', dataIndex: 'typeCode', width: 120 },
    { title: 'Name', dataIndex: 'displayName' },
    {
      title: 'Active',
      dataIndex: 'active',
      width: 120,
      render: (active: boolean) => (
        <Tag color={active ? 'success' : 'default'}>{active ? 'Active' : 'Inactive'}</Tag>
      ),
    },
    {
      title: '',
      key: 'actions',
      width: 140,
      render: (_, type) =>
        isAdmin && type.active ? (
          <Button size="small" onClick={() => deactivate(type)}>
            Deactivate
          </Button>
        ) : null,
    },
  ];

  if (loading) return <Skeleton active />;

  return (
    <Space direction="vertical" size={24} style={{ width: '100%' }}>
      <div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Title level={4} style={{ fontFamily: HEADING_FONT, marginTop: 0 }}>
            Contract types
          </Title>
          <AdminGate fallback="hide">
            <Button icon={<PlusOutlined />} onClick={() => setTypeOpen(true)}>
              Add type
            </Button>
          </AdminGate>
        </div>
        <Table rowKey="id" columns={columns} dataSource={types} pagination={false} />
      </div>

      <div>
        <Title level={4} style={{ fontFamily: HEADING_FONT }}>
          Notification settings
        </Title>
        <Text type="secondary">
          Reminder days apply to active contracts that do not set their own override.
        </Text>
        <Form layout="vertical" style={{ marginTop: 12, maxWidth: 560 }}>
          <Form.Item label="Reminder days">
            <Input
              value={reminderDays}
              disabled={!isAdmin}
              onChange={(event) => setReminderDays(event.target.value)}
            />
          </Form.Item>
          <Form.Item label="Recipient email addresses">
            <Select
              mode="tags"
              value={recipients}
              disabled={!isAdmin}
              tokenSeparators={[',']}
              onChange={setRecipients}
              placeholder="finance@cognologix.com"
            />
          </Form.Item>
          <AdminGate fallback="hide">
            <Button type="primary" loading={savingConfig} onClick={saveConfig}>
              Save
            </Button>
          </AdminGate>
        </Form>
      </div>

      <Modal open={typeOpen} title="Add contract type" onCancel={() => setTypeOpen(false)} onOk={addType}>
        <Form form={form} layout="vertical">
          <Form.Item name="typeCode" label="Code" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="displayName" label="Display name" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
