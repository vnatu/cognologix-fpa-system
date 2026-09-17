import { useEffect, useState } from 'react';
import { Button, Form, Input, InputNumber, Space, Typography, notification } from 'antd';
import { AdminGate } from '@/components/AdminGate';
import { HEADING_FONT } from '@/theme/antdTheme';
import { fetchOllamaConfig, saveOllamaConfig, testOllama } from '../api';
import type { OllamaConfig, OllamaTestResult } from '../types';

const { Title, Text } = Typography;

export default function OllamaSettingsPage() {
  const [form] = Form.useForm<OllamaConfig>();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [result, setResult] = useState<OllamaTestResult | null>(null);

  useEffect(() => {
    fetchOllamaConfig()
      .then((cfg) => form.setFieldsValue(cfg))
      .catch(() => notification.error({ message: 'Failed to load Ollama settings' }))
      .finally(() => setLoading(false));
  }, [form]);

  return (
    <div style={{ padding: 28, maxWidth: 640 }}>
      <Title level={3} style={{ fontFamily: HEADING_FONT }}>
        Ollama settings
      </Title>
      <Text type="secondary">Stored in general configuration. URL and models apply on the next mapping call.</Text>
      <Form
        form={form}
        layout="vertical"
        style={{ marginTop: 24 }}
        disabled={loading}
        onFinish={(values) => {
          setSaving(true);
          saveOllamaConfig(values)
            .then(() => notification.success({ message: 'Saved' }))
            .catch((err) =>
              notification.error({ message: 'Save failed', description: err.response?.data?.message }),
            )
            .finally(() => setSaving(false));
        }}
      >
        <Form.Item name="baseUrl" label="Ollama URL" rules={[{ required: true }]}>
          <Input />
        </Form.Item>
        <Form.Item name="chatModel" label="Chat model" rules={[{ required: true }]}>
          <Input />
        </Form.Item>
        <Form.Item name="embeddingModel" label="Embedding model" rules={[{ required: true }]}>
          <Input />
        </Form.Item>
        <Form.Item name="batchSize" label="Batch size">
          <InputNumber min={1} max={50} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="timeoutSeconds" label="Timeout (seconds)">
          <InputNumber min={10} max={600} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="companyName" label="Company name (contra detection)">
          <Input />
        </Form.Item>
        <Space>
          <AdminGate>
            <Button type="primary" htmlType="submit" loading={saving}>
              Save
            </Button>
          </AdminGate>
          <AdminGate>
            <Button
              loading={testing}
              onClick={() => {
                setTesting(true);
                testOllama()
                  .then(setResult)
                  .catch((err) =>
                    notification.error({
                      message: 'Test failed',
                      description: err.response?.data?.message,
                    }),
                  )
                  .finally(() => setTesting(false));
              }}
            >
              Test Connection
            </Button>
          </AdminGate>
        </Space>
      </Form>
      {result && (
        <div style={{ marginTop: 24 }}>
          <Text strong>{result.connected ? 'Connected' : 'Failed'}</Text>
          <p>{result.message}</p>
          <p>Chat model pulled: {result.chatModelPulled ? 'yes' : 'no'}</p>
          <p>Embedding model pulled: {result.embeddingModelPulled ? 'yes' : 'no'}</p>
          <p>Available models: {result.availableModels.join(', ') || 'none'}</p>
        </div>
      )}
    </div>
  );
}
