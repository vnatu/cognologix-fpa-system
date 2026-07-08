import { useEffect, useState, useCallback } from 'react';
import {
  Button,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  notification,
  Select,
  Skeleton,
  Table,
  Tag,
} from 'antd';
import { PlusOutlined, EditOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { HEADING_FONT } from '@/theme/antdTheme';
import {
  fetchCustomers,
  fetchCustomer,
  createCustomer,
  updateCustomer,
} from './api';
import type { CustomerSummary, CustomerDetail, LifecycleStatus } from './types';

const STATUS_COLOR: Record<LifecycleStatus, string> = {
  ACTIVE: 'green',
  AT_RISK: 'orange',
  CHURNED: 'red',
  PROSPECT: 'blue',
};

const STATUS_OPTIONS: { label: string; value: LifecycleStatus }[] = [
  { label: 'Active', value: 'ACTIVE' },
  { label: 'At Risk', value: 'AT_RISK' },
  { label: 'Churned', value: 'CHURNED' },
  { label: 'Prospect', value: 'PROSPECT' },
];

interface Props {
  onSelectCustomer: (id: string) => void;
}

interface FormValues {
  customerCode: string;
  customerName: string;
  zohoBooksCustomerRef?: string;
  relationshipOwnerEmployeeId?: string;
  lifecycleStatus: LifecycleStatus;
  dsoDays?: number;
}

export default function CustomersSection({ onSelectCustomer }: Props) {
  const [customers, setCustomers] = useState<CustomerSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form] = Form.useForm<FormValues>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setCustomers(await fetchCustomers());
    } catch {
      notification.error({ message: 'Failed to load customers' });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const openAdd = () => {
    setEditingId(null);
    setModalOpen(true);
  };

  const openEdit = async (id: string) => {
    setEditingId(id);
    setModalOpen(true);
    try {
      const detail: CustomerDetail = await fetchCustomer(id);
      form.setFieldsValue({
        customerCode: detail.customerCode,
        customerName: detail.customerName,
        zohoBooksCustomerRef: detail.zohoBooksCustomerRef,
        relationshipOwnerEmployeeId: detail.relationshipOwnerEmployeeId,
        lifecycleStatus: detail.lifecycleStatus,
        dsoDays: detail.commercialTerms?.dsoDays,
      });
    } catch {
      notification.error({ message: 'Failed to load customer detail' });
    }
  };

  const handleSave = async () => {
    let values: FormValues;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    setSaving(true);
    try {
      if (editingId) {
        await updateCustomer(editingId, {
          customerName: values.customerName,
          lifecycleStatus: values.lifecycleStatus,
          relationshipOwnerEmployeeId: values.relationshipOwnerEmployeeId,
          dsoDays: values.dsoDays,
        });
        notification.success({ message: 'Customer updated' });
      } else {
        await createCustomer({
          customerCode: values.customerCode,
          customerName: values.customerName,
          zohoBooksCustomerRef: values.zohoBooksCustomerRef,
          relationshipOwnerEmployeeId: values.relationshipOwnerEmployeeId,
          lifecycleStatus: values.lifecycleStatus,
          dsoDays: values.dsoDays,
        });
        notification.success({ message: 'Customer created' });
      }
      setModalOpen(false);
      load();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Save failed';
      notification.error({ message: 'Error', description: msg });
    } finally {
      setSaving(false);
    }
  };

  const columns: ColumnsType<CustomerSummary> = [
    {
      title: 'Customer Code',
      dataIndex: 'customerCode',
      key: 'customerCode',
      width: 140,
      render: (v: string) => <code style={{ fontSize: 13 }}>{v}</code>,
    },
    {
      title: 'Customer Name',
      dataIndex: 'customerName',
      key: 'customerName',
    },
    {
      title: 'Lifecycle Status',
      dataIndex: 'lifecycleStatus',
      key: 'lifecycleStatus',
      width: 150,
      render: (v: LifecycleStatus) => (
        <Tag color={STATUS_COLOR[v]}>{v.replace('_', ' ')}</Tag>
      ),
    },
    {
      title: 'Relationship Owner',
      dataIndex: 'relationshipOwnerEmployeeId',
      key: 'relationshipOwnerEmployeeId',
      width: 180,
      render: (v?: string) => v ?? <span style={{ color: '#aaa' }}>—</span>,
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 90,
      render: (_: unknown, row: CustomerSummary) => (
        <Button
          size="small"
          icon={<EditOutlined />}
          onClick={(e) => {
            e.stopPropagation();
            openEdit(row.id);
          }}
        >
          Edit
        </Button>
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
          Customers
        </h3>
        <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>
          Add Customer
        </Button>
      </div>

      {loading ? (
        <Skeleton active paragraph={{ rows: 5 }} />
      ) : (
        <Table
          dataSource={customers}
          columns={columns}
          rowKey="id"
          size="small"
          onRow={(row) => ({
            style: { cursor: 'pointer' },
            onClick: () => {
              onSelectCustomer(row.id);
              openEdit(row.id);
            },
          })}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="No customers yet — add your first client above"
              />
            ),
          }}
          pagination={{ pageSize: 15, hideOnSinglePage: true }}
        />
      )}

      <Modal
        title={
          <span style={{ fontFamily: HEADING_FONT, fontWeight: 700 }}>
            {editingId ? 'Edit Customer' : 'Add Customer'}
          </span>
        }
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSave}
        okText={editingId ? 'Save Changes' : 'Create Customer'}
        confirmLoading={saving}
        width={520}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="customerCode"
            label="Customer Code"
            rules={[{ required: true, message: 'Customer code is required' }]}
          >
            <Input
              disabled={!!editingId}
              placeholder="e.g. ICERTI"
              maxLength={50}
            />
          </Form.Item>

          <Form.Item
            name="customerName"
            label="Customer Name"
            rules={[{ required: true, message: 'Customer name is required' }]}
          >
            <Input placeholder="e.g. Icertis" maxLength={255} />
          </Form.Item>

          <Form.Item
            name="zohoBooksCustomerRef"
            label="Zoho Books Customer Ref"
          >
            <Input placeholder="External reference from Zoho Books" maxLength={100} />
          </Form.Item>

          <Form.Item
            name="lifecycleStatus"
            label="Lifecycle Status"
            rules={[{ required: true, message: 'Please select a status' }]}
          >
            <Select options={STATUS_OPTIONS} placeholder="Select status" />
          </Form.Item>

          <Form.Item
            name="relationshipOwnerEmployeeId"
            label="Relationship Owner Employee ID"
            extra="EmployeeID from the People & Payroll registry"
          >
            <Input placeholder="e.g. EMP0042" maxLength={50} />
          </Form.Item>

          <Form.Item name="dsoDays" label="DSO Days">
            <InputNumber
              min={0}
              max={365}
              placeholder="e.g. 45"
              style={{ width: '100%' }}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
