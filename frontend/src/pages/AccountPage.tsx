import { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Form,
  Input,
  Space,
  Typography,
  notification,
} from 'antd';
import { useAuth } from '@/context/AuthContext';
import { changeOwnPassword, fetchMe, type AppUser } from '@/api/users';
import { HEADING_FONT } from '@/theme/antdTheme';

const { Text } = Typography;

export default function AccountPage() {
  const {
    email,
    role,
    mustChangePassword,
    refreshSession,
    clearMustChangePassword,
  } = useAuth();
  const [profile, setProfile] = useState<AppUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    (async () => {
      try {
        setProfile(await fetchMe());
      } catch {
        notification.error({ message: 'Failed to load profile' });
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const handleChangePassword = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const updated = await changeOwnPassword(
        values.currentPassword,
        values.newPassword,
      );
      setProfile(updated);
      form.resetFields();
      notification.success({ message: 'Password updated' });
      if (email) {
        await refreshSession(email, values.newPassword);
      } else {
        clearMustChangePassword();
      }
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { error?: string } } })?.response?.data
          ?.error ?? 'Failed to change password';
      notification.error({ message: msg });
    } finally {
      setSaving(false);
    }
  };

  const displayName = profile?.fullName ?? '—';
  const displayEmail = profile?.email ?? email ?? '—';
  const displayRole = profile?.role ?? role ?? '—';

  return (
    <div style={{ padding: 32, maxWidth: 560 }}>
      <div
        style={{
          fontFamily: HEADING_FONT,
          fontWeight: 700,
          fontSize: 22,
          color: '#232323',
          marginBottom: 8,
        }}
      >
        Account
      </div>
      <Text type="secondary" style={{ display: 'block', marginBottom: 24 }}>
        Your profile and password settings.
      </Text>

      {mustChangePassword && (
        <Alert
          type="warning"
          showIcon
          message="You must change your password before continuing."
          style={{ marginBottom: 24 }}
        />
      )}

      <div
        style={{
          background: '#ffffff',
          border: '1px solid #d8d8d8',
          borderRadius: 8,
          padding: 24,
          marginBottom: 24,
        }}
      >
        <div
          style={{
            fontFamily: HEADING_FONT,
            fontWeight: 700,
            fontSize: 16,
            marginBottom: 16,
          }}
        >
          Profile
        </div>
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <div>
            <Text type="secondary" style={{ fontSize: 12 }}>Name</Text>
            <div style={{ fontWeight: 600 }}>{loading ? '…' : displayName}</div>
          </div>
          <div>
            <Text type="secondary" style={{ fontSize: 12 }}>Email</Text>
            <div style={{ fontWeight: 600 }}>{displayEmail}</div>
          </div>
          <div>
            <Text type="secondary" style={{ fontSize: 12 }}>Role</Text>
            <div style={{ fontWeight: 600 }}>{displayRole}</div>
          </div>
        </Space>
      </div>

      <div
        style={{
          background: '#ffffff',
          border: '1px solid #d8d8d8',
          borderRadius: 8,
          padding: 24,
        }}
      >
        <div
          style={{
            fontFamily: HEADING_FONT,
            fontWeight: 700,
            fontSize: 16,
            marginBottom: 16,
          }}
        >
          Change Password
        </div>
        <Form form={form} layout="vertical" requiredMark={false} onFinish={handleChangePassword}>
          <Form.Item
            label="Current Password"
            name="currentPassword"
            rules={[{ required: true, message: 'Enter your current password.' }]}
          >
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item
            label="New Password"
            name="newPassword"
            rules={[
              { required: true, message: 'Enter a new password.' },
              { min: 8, message: 'At least 8 characters.' },
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            label="Confirm New Password"
            name="confirmPassword"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: 'Confirm your new password.' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('Passwords do not match.'));
                },
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Button
            type="primary"
            htmlType="submit"
            loading={saving}
            style={{ fontFamily: HEADING_FONT, fontWeight: 600 }}
          >
            Update password
          </Button>
        </Form>
      </div>
    </div>
  );
}
