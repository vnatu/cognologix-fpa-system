import { useEffect, useState, useCallback } from 'react';
import {
  Button,
  Empty,
  Form,
  Input,
  Modal,
  notification,
  Popconfirm,
  Select,
  Skeleton,
  Table,
} from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { HEADING_FONT } from '@/theme/antdTheme';
import { fetchCustomers, fetchProjectCodes, addProjectCode, deleteProjectCode } from './api';
import type { CustomerSummary, ProjectCode } from './types';

interface Props {
  selectedCustomerId: string | null;
  onSelectCustomer: (id: string) => void;
}

interface FormValues {
  projectCode: string;
  description?: string;
}

export default function ProjectCodesSection({
  selectedCustomerId,
  onSelectCustomer,
}: Props) {
  const [customers, setCustomers] = useState<CustomerSummary[]>([]);
  const [customersLoading, setCustomersLoading] = useState(true);
  const [codes, setCodes] = useState<ProjectCode[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<FormValues>();

  useEffect(() => {
    fetchCustomers()
      .then(setCustomers)
      .catch(() => notification.error({ message: 'Failed to load customers' }))
      .finally(() => setCustomersLoading(false));
  }, []);

  const loadCodes = useCallback(async (customerId: string) => {
    setLoading(true);
    try {
      setCodes(await fetchProjectCodes(customerId));
    } catch {
      notification.error({ message: 'Failed to load project codes' });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selectedCustomerId) loadCodes(selectedCustomerId);
    else setCodes([]);
  }, [selectedCustomerId, loadCodes]);

  const handleAdd = async () => {
    let values: FormValues;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    if (!selectedCustomerId) return;
    setSaving(true);
    try {
      await addProjectCode(selectedCustomerId, {
        projectCode: values.projectCode,
        description: values.description,
      });
      notification.success({ message: 'Project code added' });
      setModalOpen(false);
      loadCodes(selectedCustomerId);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Save failed';
      notification.error({ message: 'Error', description: msg });
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (codeId: string) => {
    if (!selectedCustomerId) return;
    try {
      await deleteProjectCode(selectedCustomerId, codeId);
      notification.success({ message: 'Project code removed' });
      loadCodes(selectedCustomerId);
    } catch {
      notification.error({ message: 'Failed to remove project code' });
    }
  };

  const columns: ColumnsType<ProjectCode> = [
    {
      title: 'Project Code',
      dataIndex: 'projectCode',
      key: 'projectCode',
      width: 180,
      render: (v: string) => <code style={{ fontSize: 13 }}>{v}</code>,
    },
    {
      title: 'Description',
      dataIndex: 'description',
      key: 'description',
      render: (v?: string) => v ?? <span style={{ color: '#aaa' }}>—</span>,
    },
    {
      title: '',
      key: 'actions',
      width: 80,
      render: (_: unknown, row: ProjectCode) => (
        <Popconfirm
          title="Remove this project code?"
          description="This cannot be undone."
          okText="Remove"
          okButtonProps={{ danger: true }}
          onConfirm={() => handleDelete(row.id)}
        >
          <Button
            size="small"
            danger
            icon={<DeleteOutlined />}
            type="text"
          />
        </Popconfirm>
      ),
    },
  ];

  return (
    <div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <h3
          style={{
            fontFamily: HEADING_FONT,
            fontWeight: 700,
            fontSize: 16,
            margin: 0,
            color: '#232323',
          }}
        >
          Project Codes
        </h3>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => { form.resetFields(); setModalOpen(true); }}
          disabled={!selectedCustomerId}
        >
          Add Project Code
        </Button>
      </div>

      {customersLoading ? (
        <Skeleton active paragraph={{ rows: 1 }} />
      ) : (
        <Form.Item label="Customer" style={{ marginBottom: 20, maxWidth: 360 }}>
          <Select
            showSearch
            placeholder="Select a customer"
            value={selectedCustomerId ?? undefined}
            onChange={onSelectCustomer}
            options={customers.map((c) => ({
              label: `${c.customerCode} — ${c.customerName}`,
              value: c.id,
            }))}
            filterOption={(input, option) =>
              (option?.label as string)
                .toLowerCase()
                .includes(input.toLowerCase())
            }
          />
        </Form.Item>
      )}

      {!selectedCustomerId ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="Select a customer to manage their project codes"
        />
      ) : loading ? (
        <Skeleton active paragraph={{ rows: 4 }} />
      ) : (
        <Table
          dataSource={codes}
          columns={columns}
          rowKey="id"
          size="small"
          pagination={false}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="No project codes yet — add one above"
              />
            ),
          }}
        />
      )}

      <Modal
        title={
          <span style={{ fontFamily: HEADING_FONT, fontWeight: 700 }}>
            Add Project Code
          </span>
        }
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleAdd}
        okText="Add"
        confirmLoading={saving}
        width={440}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="projectCode"
            label="Project Code"
            rules={[{ required: true, message: 'Project code is required' }]}
          >
            <Input placeholder="e.g. ICERTI-CX-2026" maxLength={50} />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input placeholder="Brief description" maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
