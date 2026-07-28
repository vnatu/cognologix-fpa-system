import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Form,
  Modal,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  notification,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { updateDateFormat, fetchDateFormat } from '@/api/general';
import {
  createUser,
  deactivateUser,
  fetchUsers,
  reactivateUser,
  resetUserPassword,
  updateUserRole,
  type AppUser,
} from '@/api/users';
import { useAuth, type UserRole } from '@/context/AuthContext';
import { useDateFormat } from '@/context/DateFormatContext';
import { HEADING_FONT } from '@/theme/antdTheme';
import {
  DATE_FORMAT_OPTIONS,
  formatDate,
  type DateFormatOption,
} from '@/utils/formatDate';
import FxRatesSection from './general/FxRatesSection';
import BackupRestoreSection from './general/BackupRestoreSection';

const { Text } = Typography;

function DateFormatSettings({ canWrite }: { canWrite: boolean }) {
  const { format, setFormat } = useDateFormat();
  const [selected, setSelected] = useState<DateFormatOption>(format);
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    (async () => {
      try {
        const current = await fetchDateFormat();
        setSelected(current);
        setFormat(current);
      } catch {
        notification.error({ message: 'Failed to load date format' });
      } finally {
        setLoading(false);
      }
    })();
  }, [setFormat]);

  useEffect(() => {
    setSelected(format);
  }, [format]);

  const handleSave = async () => {
    setSaving(true);
    try {
      const saved = await updateDateFormat(selected);
      setFormat(saved);
      notification.success({ message: 'Date format saved' });
    } catch {
      notification.error({ message: 'Failed to save date format' });
    } finally {
      setSaving(false);
    }
  };

  const preview = formatDate(dayjs().toDate(), selected);

  return (
    <div style={{ marginBottom: 40 }}>
      <div
        style={{
          fontFamily: HEADING_FONT,
          fontWeight: 700,
          fontSize: 17,
          color: '#232323',
          marginBottom: 4,
        }}
      >
        Date Format
      </div>
      <div style={{ fontSize: 13, color: '#888888', marginBottom: 16 }}>
        Choose how dates are displayed across the application.
      </div>
      <Space direction="vertical" size="middle" style={{ maxWidth: 360 }}>
        <Select
          style={{ width: '100%' }}
          value={selected}
          loading={loading}
          disabled={!canWrite}
          options={DATE_FORMAT_OPTIONS.map((o) => ({ label: o, value: o }))}
          onChange={setSelected}
        />
        <Text type="secondary">
          Preview: <Text strong>{preview}</Text>
        </Text>
        {canWrite && (
          <Button
            type="primary"
            loading={saving}
            onClick={handleSave}
            disabled={loading}
            style={{ fontFamily: HEADING_FONT, fontWeight: 600 }}
          >
            Save
          </Button>
        )}
      </Space>
    </div>
  );
}

export default function GeneralTab() {
  const { isAdmin, email: selfEmail } = useAuth();
  const canWrite = isAdmin();
  const [members, setMembers] = useState<AppUser[]>([]);
  const [loading, setLoading] = useState(false);
  const [open, setOpen] = useState(false);
  const [resetOpen, setResetOpen] = useState<AppUser | null>(null);
  const [form] = Form.useForm();
  const [resetForm] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  const loadMembers = useCallback(async () => {
    if (!canWrite) return;
    setLoading(true);
    try {
      setMembers(await fetchUsers());
    } catch {
      notification.error({ message: 'Failed to load users' });
    } finally {
      setLoading(false);
    }
  }, [canWrite]);

  useEffect(() => {
    loadMembers();
  }, [loadMembers]);

  const handleInvite = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      await createUser({
        email: values.email,
        fullName: values.name,
        role: values.role as UserRole,
        initialPassword: values.initialPassword,
      });
      notification.success({ message: 'User created' });
      setOpen(false);
      form.resetFields();
      await loadMembers();
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { error?: string } } })?.response?.data
          ?.error ?? 'Failed to create user';
      notification.error({ message: msg });
    } finally {
      setSubmitting(false);
    }
  };

  const columns: ColumnsType<AppUser> = [
    {
      title: 'Name',
      dataIndex: 'fullName',
      key: 'fullName',
      render: (v: string) => <Text strong style={{ color: '#232323' }}>{v}</Text>,
    },
    {
      title: 'Email',
      dataIndex: 'email',
      key: 'email',
      render: (v: string) => <Text style={{ color: '#555555' }}>{v}</Text>,
    },
    {
      title: 'Role',
      dataIndex: 'role',
      key: 'role',
      render: (role: UserRole, row) =>
        canWrite ? (
          <Select
            size="small"
            value={role}
            style={{ width: 110 }}
            options={[
              { value: 'ADMIN', label: 'Admin' },
              { value: 'VIEWER', label: 'Viewer' },
            ]}
            onChange={async (next) => {
              try {
                await updateUserRole(row.id, next);
                notification.success({ message: 'Role updated' });
                await loadMembers();
              } catch {
                notification.error({ message: 'Failed to update role' });
              }
            }}
          />
        ) : (
          <Tag color={role === 'ADMIN' ? 'error' : 'default'}>
            {role === 'ADMIN' ? 'Admin' : 'Viewer'}
          </Tag>
        ),
    },
    {
      title: 'Status',
      dataIndex: 'active',
      key: 'active',
      render: (active: boolean) => (
        <Tag color={active ? 'success' : 'default'}>{active ? 'Active' : 'Inactive'}</Tag>
      ),
    },
    ...(canWrite
      ? [
          {
            title: 'Actions',
            key: 'actions',
            render: (_: unknown, row: AppUser) => {
              const isSelf = row.email.toLowerCase() === (selfEmail ?? '').toLowerCase();
              return (
                <Space size="small" wrap>
                  {row.active ? (
                    <Tooltip title={isSelf ? 'You cannot deactivate yourself' : undefined}>
                      <Button
                        size="small"
                        disabled={isSelf}
                        onClick={async () => {
                          try {
                            await deactivateUser(row.id);
                            notification.success({ message: 'User deactivated' });
                            await loadMembers();
                          } catch (err: unknown) {
                            const msg =
                              (err as { response?: { data?: { error?: string } } })?.response
                                ?.data?.error ?? 'Failed to deactivate';
                            notification.error({ message: msg });
                          }
                        }}
                      >
                        Deactivate
                      </Button>
                    </Tooltip>
                  ) : (
                    <Button
                      size="small"
                      onClick={async () => {
                        try {
                          await reactivateUser(row.id);
                          notification.success({ message: 'User reactivated' });
                          await loadMembers();
                        } catch {
                          notification.error({ message: 'Failed to reactivate' });
                        }
                      }}
                    >
                      Reactivate
                    </Button>
                  )}
                  <Button size="small" onClick={() => setResetOpen(row)}>
                    Reset password
                  </Button>
                </Space>
              );
            },
          } as ColumnsType<AppUser>[number],
        ]
      : []),
  ];

  return (
    <>
      <DateFormatSettings canWrite={canWrite} />

      <FxRatesSection />

      {canWrite && <BackupRestoreSection />}

      <div
        style={{
          display: 'flex',
          alignItems: 'flex-end',
          justifyContent: 'space-between',
          gap: 16,
          marginBottom: 16,
        }}
      >
        <div>
          <div
            style={{
              fontFamily: HEADING_FONT,
              fontWeight: 700,
              fontSize: 17,
              color: '#232323',
            }}
          >
            Members
          </div>
          <div style={{ fontSize: 13, color: '#888888', marginTop: 3 }}>
            People with access to this workspace. Admins can write; Viewers have read-only access.
          </div>
        </div>
        {canWrite && (
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setOpen(true)}
            style={{ fontFamily: HEADING_FONT, fontWeight: 600 }}
          >
            Add user
          </Button>
        )}
      </div>

      {canWrite ? (
        <Table<AppUser>
          rowKey="id"
          columns={columns}
          dataSource={members}
          loading={loading}
          pagination={false}
          size="middle"
          bordered={false}
          style={{ background: '#ffffff', borderRadius: 8, border: '1px solid #d8d8d8' }}
        />
      ) : (
        <Text type="secondary">Only administrators can manage workspace members.</Text>
      )}

      <Modal
        title={
          <div>
            <div
              style={{
                fontFamily: HEADING_FONT,
                fontWeight: 700,
                fontSize: 18,
                color: '#232323',
              }}
            >
              Add user
            </div>
            <div style={{ fontSize: 13, color: '#888888', fontWeight: 400, marginTop: 4 }}>
              Set an initial password — they must change it on first login.
            </div>
          </div>
        }
        open={open}
        onCancel={() => {
          setOpen(false);
          form.resetFields();
        }}
        onOk={handleInvite}
        okText="Create user"
        cancelText="Cancel"
        confirmLoading={submitting}
        okButtonProps={{ style: { fontFamily: HEADING_FONT, fontWeight: 600 } }}
        cancelButtonProps={{ style: { fontFamily: HEADING_FONT, fontWeight: 600 } }}
        width={440}
      >
        <Form
          form={form}
          layout="vertical"
          requiredMark={false}
          style={{ marginTop: 8 }}
          initialValues={{ role: 'VIEWER' }}
        >
          <Form.Item
            label={<span style={{ fontWeight: 700, color: '#555555', fontSize: 13 }}>Name</span>}
            name="name"
            rules={[{ required: true, message: 'Enter a name.' }]}
          >
            <Input placeholder="Full name" autoComplete="off" />
          </Form.Item>
          <Form.Item
            label={<span style={{ fontWeight: 700, color: '#555555', fontSize: 13 }}>Email</span>}
            name="email"
            rules={[
              { required: true, message: 'Enter an email.' },
              { type: 'email', message: 'Enter a valid email address.' },
            ]}
          >
            <Input placeholder="name@cognologix.com" type="email" autoComplete="off" />
          </Form.Item>
          <Form.Item
            label={<span style={{ fontWeight: 700, color: '#555555', fontSize: 13 }}>Role</span>}
            name="role"
            rules={[{ required: true }]}
          >
            <Select
              options={[
                { value: 'ADMIN', label: 'Admin' },
                { value: 'VIEWER', label: 'Viewer' },
              ]}
            />
          </Form.Item>
          <Form.Item
            label={
              <span style={{ fontWeight: 700, color: '#555555', fontSize: 13 }}>
                Initial Password
              </span>
            }
            name="initialPassword"
            rules={[
              { required: true, message: 'Enter an initial password.' },
              { min: 8, message: 'At least 8 characters.' },
            ]}
          >
            <Input.Password placeholder="Temporary password" autoComplete="new-password" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="Reset password"
        open={!!resetOpen}
        onCancel={() => {
          setResetOpen(null);
          resetForm.resetFields();
        }}
        onOk={async () => {
          if (!resetOpen) return;
          const values = await resetForm.validateFields();
          try {
            await resetUserPassword(resetOpen.id, values.newPassword);
            notification.success({ message: 'Password reset — user must change it on next login' });
            setResetOpen(null);
            resetForm.resetFields();
            await loadMembers();
          } catch {
            notification.error({ message: 'Failed to reset password' });
          }
        }}
        okText="Reset"
      >
        <Form form={resetForm} layout="vertical" requiredMark={false}>
          <Form.Item
            label="New temporary password"
            name="newPassword"
            rules={[
              { required: true, message: 'Enter a password.' },
              { min: 8, message: 'At least 8 characters.' },
            ]}
          >
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
