import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Form,
  InputNumber,
  Skeleton,
  Typography,
  notification,
} from 'antd';
import { useIsAdmin } from '@/components/AdminGate';
import { HEADING_FONT } from '@/theme/antdTheme';
import {
  fetchSecurityConfig,
  updateSecurityConfig,
  type SecurityConfig,
} from '@/api/general';
import { useSecuritySettings } from '@/context/SecuritySettingsContext';

const { Text, Paragraph } = Typography;

export default function SecurityTab() {
  const isAdmin = useIsAdmin();
  const { reload } = useSecuritySettings();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<SecurityConfig>();
  const [preview, setPreview] = useState<SecurityConfig>({
    jwtExpiryHours: 2,
    inactivityTimeoutMinutes: 30,
  });

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const cfg = await fetchSecurityConfig();
      form.setFieldsValue(cfg);
      setPreview(cfg);
    } catch {
      notification.error({ message: 'Failed to load security settings' });
    } finally {
      setLoading(false);
    }
  }, [form]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const saved = await updateSecurityConfig(values);
      form.setFieldsValue(saved);
      setPreview(saved);
      reload();
      notification.success({ message: 'Security settings saved' });
    } catch (e) {
      if (e && typeof e === 'object' && 'errorFields' in e) return;
      notification.error({ message: 'Failed to save security settings' });
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <Skeleton active paragraph={{ rows: 6 }} />;
  }

  return (
    <div style={{ maxWidth: 560 }}>
      <div style={{ fontFamily: HEADING_FONT, fontWeight: 600, fontSize: 16, marginBottom: 4 }}>
        Session Security
      </div>
      <Text type="secondary">
        Control JWT lifetime and inactivity logout for Finance users.
      </Text>

      <Paragraph type="secondary" style={{ marginTop: 16 }}>
        Users will be logged out after{' '}
        <Text strong>{preview.jwtExpiryHours} hour{preview.jwtExpiryHours === 1 ? '' : 's'}</Text> or{' '}
        <Text strong>
          {preview.inactivityTimeoutMinutes} minute
          {preview.inactivityTimeoutMinutes === 1 ? '' : 's'}
        </Text>{' '}
        of inactivity, whichever comes first.
      </Paragraph>

      <Form
        form={form}
        layout="vertical"
        style={{ marginTop: 8 }}
        disabled={!isAdmin}
        onValuesChange={(_, all) => {
          if (all.jwtExpiryHours != null && all.inactivityTimeoutMinutes != null) {
            setPreview({
              jwtExpiryHours: all.jwtExpiryHours,
              inactivityTimeoutMinutes: all.inactivityTimeoutMinutes,
            });
          }
        }}
      >
        <Form.Item
          name="jwtExpiryHours"
          label="JWT Token Lifetime"
          extra="How long before users must re-authenticate (hours)"
          rules={[{ required: true, message: 'Required' }]}
        >
          <InputNumber min={1} max={24} precision={0} style={{ width: 160 }} addonAfter="hours" />
        </Form.Item>
        <Form.Item
          name="inactivityTimeoutMinutes"
          label="Inactivity Timeout"
          extra="Log out after this many minutes of inactivity"
          rules={[{ required: true, message: 'Required' }]}
        >
          <InputNumber min={5} max={120} precision={0} style={{ width: 160 }} addonAfter="minutes" />
        </Form.Item>
        {isAdmin && (
          <Button type="primary" loading={saving} onClick={() => void handleSave()}>
            Save
          </Button>
        )}
      </Form>

      <Paragraph type="secondary" style={{ marginTop: 20, fontSize: 13 }}>
        Changes take effect on next login. Active sessions use the token lifetime set at the
        time of login.
      </Paragraph>
    </div>
  );
}
