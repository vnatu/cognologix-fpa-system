import { useEffect, useRef, useState } from 'react';
import { Button, Form, Input, InputNumber, Radio, Space, Typography, notification, theme } from 'antd';
import { AdminGate } from '@/components/AdminGate';
import { HEADING_FONT } from '@/theme/antdTheme';
import { fetchOllamaConfig, saveOllamaConfig, testOllama } from '../api';
import type { LlmProvider, LlmProviderSettings, OllamaConfig, OllamaTestResult } from '../types';

const { Title, Text } = Typography;

const OLLAMA_DEFAULTS: LlmProviderSettings = {
  baseUrl: 'http://localhost:11434',
  chatModel: 'qwen2.5:32b',
  embeddingUrl: 'http://localhost:11434',
  embeddingModel: 'nomic-embed-text',
  apiKey: '',
};

const OMLX_DEFAULTS: LlmProviderSettings = {
  baseUrl: 'http://localhost:9000',
  chatModel: 'mlx-community/Qwen3.6-35B-A3B-4bit',
  embeddingUrl: 'http://localhost:9000',
  embeddingModel: 'mlx-community/nomicai-modernbert-embed-base-4bit',
  apiKey: '',
};

function providerLabel(provider: LlmProvider): string {
  return provider === 'OLLAMA' ? 'Ollama' : 'oMLX';
}

function resultLines(message: string): string[] {
  return message
    .split('\n')
    .flatMap((part) => part.split(/(?<=\.)\s+(?=[A-Z])/))
    .map((line) => line.trim())
    .filter(Boolean);
}

function fingerprint(provider: LlmProvider, values: Partial<OllamaConfig>): string {
  const profile = readProfile(values, provider);
  return JSON.stringify({
    provider,
    baseUrl: profile.baseUrl.trim(),
    chatModel: profile.chatModel.trim(),
    embeddingUrl: profile.embeddingUrl.trim(),
    embeddingModel: profile.embeddingModel.trim(),
    apiKey: provider === 'OMLX' ? profile.apiKey : '',
    batchSize: values.batchSize ?? null,
    timeoutSeconds: values.timeoutSeconds ?? null,
    companyName: (values.companyName ?? '').trim(),
  });
}

function readProfile(values: Partial<OllamaConfig>, provider: LlmProvider): LlmProviderSettings {
  const defaults = provider === 'OLLAMA' ? OLLAMA_DEFAULTS : OMLX_DEFAULTS;
  return {
    baseUrl: values.baseUrl ?? defaults.baseUrl,
    chatModel: values.chatModel ?? defaults.chatModel,
    embeddingUrl: provider === 'OMLX' ? (values.baseUrl ?? defaults.baseUrl) : (values.embeddingUrl ?? defaults.embeddingUrl),
    embeddingModel: values.embeddingModel ?? defaults.embeddingModel,
    apiKey: provider === 'OMLX' ? (values.apiKey ?? '') : '',
  };
}

export default function OllamaSettingsPage() {
  const { token } = theme.useToken();
  const [form] = Form.useForm<OllamaConfig>();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [result, setResult] = useState<OllamaTestResult | null>(null);
  const [selected, setSelected] = useState<LlmProvider>('OMLX');
  const [dirty, setDirty] = useState(false);
  const savedKey = useRef('');
  const [profiles, setProfiles] = useState<Record<LlmProvider, LlmProviderSettings>>({
    OLLAMA: OLLAMA_DEFAULTS,
    OMLX: OMLX_DEFAULTS,
  });

  const showProfile = (provider: LlmProvider, profile: LlmProviderSettings) => {
    form.setFieldsValue({
      provider,
      baseUrl: profile.baseUrl,
      chatModel: profile.chatModel,
      embeddingUrl: profile.embeddingUrl,
      embeddingModel: profile.embeddingModel,
      apiKey: profile.apiKey,
    });
  };

  useEffect(() => {
    fetchOllamaConfig()
      .then((cfg) => {
        const nextProfiles = {
          OLLAMA: cfg.ollamaSettings ?? OLLAMA_DEFAULTS,
          OMLX: cfg.omlxSettings ?? OMLX_DEFAULTS,
        };
        const provider = cfg.provider === 'OLLAMA' ? 'OLLAMA' : 'OMLX';
        setProfiles(nextProfiles);
        setSelected(provider);
        showProfile(provider, nextProfiles[provider]);
        form.setFieldsValue({
          batchSize: cfg.batchSize,
          timeoutSeconds: cfg.timeoutSeconds,
          companyName: cfg.companyName,
        });
        savedKey.current = fingerprint(provider, form.getFieldsValue());
        setDirty(false);
      })
      .catch(() => notification.error({ message: 'Failed to load LLM settings' }))
      .finally(() => setLoading(false));
    // form is stable for the lifetime of this page
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [form]);

  const selectProvider = (next: LlmProvider) => {
    if (next === selected) return;
    const current = readProfile(form.getFieldsValue(), selected);
    const nextProfiles = { ...profiles, [selected]: current };
    setProfiles(nextProfiles);
    setSelected(next);
    showProfile(next, nextProfiles[next]);
    setDirty(fingerprint(next, form.getFieldsValue()) !== savedKey.current);
    setResult(null);
  };

  return (
    <div style={{ padding: 28, maxWidth: 640 }}>
      <Title level={3} style={{ fontFamily: HEADING_FONT }}>
        LLM Settings
      </Title>
      <Text type="secondary">
        Choose one provider. Only that provider is used for mapping. Save the configuration before
        testing the connection.
      </Text>
      <Form
        form={form}
        layout="vertical"
        style={{ marginTop: 24 }}
        disabled={loading}
        onValuesChange={() => {
          setDirty(fingerprint(selected, form.getFieldsValue()) !== savedKey.current);
          setResult(null);
        }}
        onFinish={(values) => {
          const profile = readProfile(values, selected);
          const payload: OllamaConfig = {
            ...values,
            provider: selected,
            baseUrl: profile.baseUrl,
            chatModel: profile.chatModel,
            embeddingUrl: profile.embeddingUrl,
            embeddingModel: profile.embeddingModel,
            apiKey: selected === 'OMLX' ? profile.apiKey : values.apiKey,
            ollamaSettings: selected === 'OLLAMA' ? profile : profiles.OLLAMA,
            omlxSettings: selected === 'OMLX' ? profile : profiles.OMLX,
          };
          setSaving(true);
          saveOllamaConfig(payload)
            .then((saved) => {
              const nextProfiles = {
                OLLAMA: saved.ollamaSettings ?? OLLAMA_DEFAULTS,
                OMLX: saved.omlxSettings ?? OMLX_DEFAULTS,
              };
              const provider = saved.provider === 'OLLAMA' ? 'OLLAMA' : 'OMLX';
              setProfiles(nextProfiles);
              setSelected(provider);
              showProfile(provider, nextProfiles[provider]);
              form.setFieldsValue({
                batchSize: saved.batchSize,
                timeoutSeconds: saved.timeoutSeconds,
                companyName: saved.companyName,
              });
              savedKey.current = fingerprint(provider, form.getFieldsValue());
              setDirty(false);
              notification.success({ message: 'Saved' });
            })
            .catch((err) =>
              notification.error({ message: 'Save failed', description: err.response?.data?.message }),
            )
            .finally(() => setSaving(false));
        }}
      >
        <Radio.Group
          value={selected}
          onChange={(event) => selectProvider(event.target.value as LlmProvider)}
          style={{ marginBottom: 12 }}
        >
          <Radio value="OLLAMA">Ollama</Radio>
          <Radio value="OMLX">oMLX</Radio>
        </Radio.Group>
        <div style={{ marginBottom: 20, color: token.colorSuccess, fontWeight: 600 }}>
          Active LLM Provider: {providerLabel(selected)}
        </div>

        {selected === 'OLLAMA' ? (
          <>
            <Form.Item name="baseUrl" label="Chat Model URL" rules={[{ required: true }]}>
              <Input placeholder={OLLAMA_DEFAULTS.baseUrl} />
            </Form.Item>
            <Form.Item name="chatModel" label="Chat Model Name" rules={[{ required: true }]}>
              <Input placeholder={OLLAMA_DEFAULTS.chatModel} />
            </Form.Item>
            <Form.Item name="embeddingUrl" label="Embedding Model URL" rules={[{ required: true }]}>
              <Input placeholder={OLLAMA_DEFAULTS.embeddingUrl} />
            </Form.Item>
            <Form.Item name="embeddingModel" label="Embedding Model Name" rules={[{ required: true }]}>
              <Input placeholder={OLLAMA_DEFAULTS.embeddingModel} />
            </Form.Item>
          </>
        ) : (
          <>
            <Form.Item name="baseUrl" label="Base URL" rules={[{ required: true }]}>
              <Input placeholder={OMLX_DEFAULTS.baseUrl} />
            </Form.Item>
            <Form.Item
              name="apiKey"
              label="API Key"
              rules={[{ required: true, message: 'API key is required' }]}
            >
              <Input.Password autoComplete="new-password" />
            </Form.Item>
            <Form.Item name="chatModel" label="Chat Model Name" rules={[{ required: true }]}>
              <Input placeholder={OMLX_DEFAULTS.chatModel} />
            </Form.Item>
            <Form.Item name="embeddingModel" label="Embedding Model Name" rules={[{ required: true }]}>
              <Input placeholder={OMLX_DEFAULTS.embeddingModel} />
            </Form.Item>
          </>
        )}

        <Form.Item name="batchSize" label="Batch size" style={{ marginTop: 28 }}>
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
        </Space>
        <div style={{ marginTop: 16 }}>
          <AdminGate>
            <Button
              disabled={dirty}
              loading={testing}
              onClick={() => {
                const baseUrl = String(form.getFieldValue('baseUrl') ?? '').trim();
                const chatModel = String(form.getFieldValue('chatModel') ?? '').trim();
                const embeddingModel = String(form.getFieldValue('embeddingModel') ?? '').trim();
                const embeddingUrl =
                  selected === 'OMLX'
                    ? baseUrl
                    : String(form.getFieldValue('embeddingUrl') ?? '').trim();
                const apiKey = String(form.getFieldValue('apiKey') ?? '');
                setTesting(true);
                testOllama({
                  provider: selected,
                  baseUrl,
                  chatModel,
                  embeddingUrl,
                  embeddingModel,
                  apiKey: selected === 'OMLX' ? apiKey : undefined,
                })
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
          {result && (
            <div style={{ marginTop: 12 }}>
              <Text strong>{result.connected ? 'Connected' : 'Failed'}</Text>
              {resultLines(result.message).map((line) => (
                <div key={line}>{line}</div>
              ))}
            </div>
          )}
        </div>
      </Form>
    </div>
  );
}
