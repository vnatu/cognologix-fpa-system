import { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Divider,
  Form,
  InputNumber,
  notification,
  Skeleton,
  Typography,
} from 'antd';
import { SaveOutlined } from '@ant-design/icons';
import { HEADING_FONT } from '@/theme/antdTheme';
import { fetchConcentrationRiskConfig, updateConcentrationRiskConfig } from './api';
import type { ConcentrationRiskConfig } from './types';

const { Text, Paragraph } = Typography;

export default function ConcentrationRiskSection() {
  const [config, setConfig] = useState<ConcentrationRiskConfig | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<{ singleClientThresholdPct: number }>();

  useEffect(() => {
    fetchConcentrationRiskConfig()
      .then((cfg) => {
        setConfig(cfg);
        form.setFieldsValue({ singleClientThresholdPct: Number(cfg.singleClientThresholdPct) });
      })
      .catch(() => notification.error({ message: 'Failed to load concentration risk config' }))
      .finally(() => setLoading(false));
  }, [form]);

  const handleSave = async () => {
    let values: { singleClientThresholdPct: number };
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    setSaving(true);
    try {
      const updated = await updateConcentrationRiskConfig(values.singleClientThresholdPct);
      setConfig(updated);
      notification.success({ message: 'Threshold updated' });
    } catch {
      notification.error({ message: 'Failed to update threshold' });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <h3
        style={{
          fontFamily: HEADING_FONT,
          fontWeight: 700,
          fontSize: 16,
          margin: '0 0 4px',
          color: '#232323',
        }}
      >
        Concentration Risk
      </h3>
      <Paragraph type="secondary" style={{ fontSize: 13, marginBottom: 24 }}>
        System-wide concentration thresholds. Actual risk percentages are calculated once
        the Revenue module supplies per-client revenue actuals (spec §8).
      </Paragraph>

      {loading ? (
        <Skeleton active paragraph={{ rows: 3 }} />
      ) : (
        <>
          {/* Single-client threshold */}
          <Card
            size="small"
            title={
              <span style={{ fontFamily: HEADING_FONT, fontWeight: 600, fontSize: 14 }}>
                Single-Client Threshold
              </span>
            }
            style={{ maxWidth: 480, marginBottom: 24 }}
          >
            <Paragraph type="secondary" style={{ fontSize: 13, marginBottom: 16 }}>
              Alert when any single client's revenue exceeds this percentage of total revenue.
              Currently {config ? `${config.singleClientThresholdPct}%` : '—'}.
            </Paragraph>
            <Form form={form} layout="inline" onFinish={handleSave}>
              <Form.Item
                name="singleClientThresholdPct"
                rules={[
                  { required: true, message: 'Required' },
                  { type: 'number', min: 1, max: 100, message: 'Must be 1–100' },
                ]}
              >
                <InputNumber
                  min={1}
                  max={100}
                  precision={2}
                  addonAfter="%"
                  style={{ width: 140 }}
                />
              </Form.Item>
              <Form.Item>
                <Button
                  type="primary"
                  htmlType="submit"
                  icon={<SaveOutlined />}
                  loading={saving}
                >
                  Save
                </Button>
              </Form.Item>
            </Form>
          </Card>

          {/* Watch Groups */}
          <Divider orientation="left" plain style={{ fontSize: 13, color: '#888' }}>
            Combined-Client Watch Groups
          </Divider>

          <Card
            size="small"
            style={{ maxWidth: 600 }}
            title={
              <span style={{ fontFamily: HEADING_FONT, fontWeight: 600, fontSize: 14 }}>
                Watch Groups
              </span>
            }
          >
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message="Backend endpoints pending"
              description={
                <Text style={{ fontSize: 13 }}>
                  Watch group CRUD (named groups of clients whose combined revenue is tracked
                  against a shared threshold — e.g. Icertis + Cadent) requires dedicated API
                  endpoints. These will be available once the watch group endpoints are added
                  to the General module's REST layer.
                </Text>
              }
            />
            <Text type="secondary" style={{ fontSize: 13 }}>
              Spec §8: combined-client watch groups let Finance track clusters of clients
              (e.g. related entities, strategic accounts) whose aggregate revenue concentration
              is monitored in addition to the single-client threshold.
            </Text>
          </Card>
        </>
      )}
    </div>
  );
}
