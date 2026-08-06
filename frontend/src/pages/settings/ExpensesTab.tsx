import { useMemo, useState } from 'react';
import {
  AutoComplete,
  Button,
  Form,
  Input,
  Modal,
  Skeleton,
  Table,
  Tag,
  Typography,
  notification,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useIsAdmin } from '@/components/AdminGate';
import { useExpenseCategories } from '@/contexts/ExpenseCategoryContext';
import { HEADING_FONT } from '@/theme/antdTheme';
import { addCategory, deactivateCategory } from '../expenses/api';
import type { ExpenseCategory } from '../expenses/types';

const { Text } = Typography;

export default function ExpensesTab() {
  const isAdmin = useIsAdmin();
  const { categories, reloadCategories } = useExpenseCategories();
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  /** Distinct groups (case-insensitive); keep first-seen spelling for the dropdown. */
  const groupOptions = useMemo(() => {
    const seen = new Map<string, string>();
    for (const c of categories) {
      const trimmed = c.categoryGroup.trim();
      const key = trimmed.toLowerCase();
      if (trimmed && !seen.has(key)) {
        seen.set(key, trimmed);
      }
    }
    return [...seen.values()].sort((a, b) => a.localeCompare(b)).map((g) => ({
      value: g,
    }));
  }, [categories]);

  const handleAdd = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      await addCategory({
        lineCode: values.lineCode,
        categoryGroup: values.categoryGroup,
        displayName: values.displayName,
        description: values.description,
      });
      notification.success({ message: 'Category added' });
      setModalOpen(false);
      form.resetFields();
      reloadCategories();
    } catch (e) {
      if (e && typeof e === 'object' && 'errorFields' in e) return;
      notification.error({ message: 'Failed to add category' });
    } finally {
      setSaving(false);
    }
  };

  const handleDeactivate = (category: ExpenseCategory) => {
    Modal.confirm({
      title: `Deactivate "${category.displayName}"?`,
      content: 'Deactivated categories are hidden from Expense Entry.',
      okText: 'Deactivate',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deactivateCategory(category.id);
          notification.success({ message: 'Category deactivated' });
          reloadCategories();
        } catch {
          notification.error({ message: 'Failed to deactivate category' });
        }
      },
    });
  };

  const columns: ColumnsType<ExpenseCategory> = [
    {
      title: 'Line Code',
      dataIndex: 'lineCode',
      key: 'lineCode',
      render: (v, row) => (
        <Text type={row.active ? undefined : 'secondary'} delete={!row.active}>
          {v}
        </Text>
      ),
    },
    {
      title: 'Group',
      dataIndex: 'categoryGroup',
      key: 'categoryGroup',
      render: (v, row) => (
        <Text type={row.active ? undefined : 'secondary'}>{v}</Text>
      ),
    },
    {
      title: 'Display Name',
      dataIndex: 'displayName',
      key: 'displayName',
      render: (v, row) => (
        <Text type={row.active ? undefined : 'secondary'}>{v}</Text>
      ),
    },
    {
      title: 'Status',
      key: 'active',
      render: (_, row) =>
        row.active ? <Tag color="success">Active</Tag> : <Tag>Inactive</Tag>,
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, row) =>
        isAdmin && row.active ? (
          <Button type="link" danger onClick={() => handleDeactivate(row)}>
            Deactivate
          </Button>
        ) : null,
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
        <div>
          <div style={{ fontFamily: HEADING_FONT, fontWeight: 600, fontSize: 16 }}>
            Expense Categories
          </div>
          <Text type="secondary">
            Overhead line items used in Expense Entry and Budgeting
          </Text>
        </div>
        {isAdmin && (
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setModalOpen(true)}
          >
            Add Category
          </Button>
        )}
      </div>

      {categories.length === 0 ? (
        <Skeleton active paragraph={{ rows: 8 }} />
      ) : (
        <Table<ExpenseCategory>
          rowKey="id"
          columns={columns}
          dataSource={categories}
          pagination={false}
          size="middle"
        />
      )}

      <Modal
        title="Add Expense Category"
        open={modalOpen}
        onCancel={() => {
          setModalOpen(false);
          form.resetFields();
        }}
        onOk={() => void handleAdd()}
        confirmLoading={saving}
        okText="Add"
      >
        <Form form={form} layout="vertical" style={{ marginTop: 12 }}>
          <Form.Item
            name="lineCode"
            label="Line Code"
            rules={[
              { required: true, message: 'Required' },
              { max: 100, message: 'Max 100 characters' },
              {
                pattern: /^[a-z0-9_]+$/,
                message: 'Use lowercase letters, numbers, underscores',
              },
            ]}
          >
            <Input placeholder="e.g. office_supplies" />
          </Form.Item>
          <Form.Item
            name="categoryGroup"
            label="Category Group"
            rules={[{ required: true, message: 'Required' }]}
          >
            <AutoComplete
              options={groupOptions}
              placeholder="Select existing or type a new group"
              filterOption={(input, option) =>
                (option?.value ?? '')
                  .toString()
                  .toLowerCase()
                  .includes(input.toLowerCase())
              }
            />
          </Form.Item>
          <Form.Item
            name="displayName"
            label="Display Name"
            rules={[{ required: true, message: 'Required' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
