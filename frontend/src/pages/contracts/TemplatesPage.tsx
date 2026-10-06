import { useEffect, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Skeleton,
  Space,
  Typography,
  Upload,
  notification,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { AdminGate } from '@/components/AdminGate';
import { HEADING_FONT } from '@/theme/antdTheme';
import { useDateFormat } from '@/context/DateFormatContext';
import { apiError, createTemplate, downloadTemplate, fetchContractTypes, fetchTemplates } from './api';
import type { ContractType, TemplateGroup } from './types';

const { Title, Paragraph, Text } = Typography;

export default function TemplatesPage() {
  const { formatDateTime } = useDateFormat();
  const [loading, setLoading] = useState(true);
  const [groups, setGroups] = useState<TemplateGroup[]>([]);
  const [types, setTypes] = useState<ContractType[]>([]);
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [form] = Form.useForm();

  const load = () => {
    setLoading(true);
    fetchTemplates()
      .then(setGroups)
      .catch((error) => notification.error({ message: apiError(error, 'Failed to load templates') }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    fetchContractTypes().then(setTypes).catch(() => undefined);
  }, []);

  const submit = async () => {
    try {
      const values = await form.validateFields();
      if (!file) {
        notification.error({ message: 'A PDF or Word file is required' });
        return;
      }
      setSaving(true);
      await createTemplate({
        contractTypeId: values.contractTypeId,
        templateName: values.templateName,
        description: values.description,
        file,
      });
      notification.success({ message: 'Template uploaded' });
      setOpen(false);
      form.resetFields();
      setFile(null);
      load();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
      notification.error({ message: apiError(error, 'Failed to upload template') });
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div style={{ padding: 28 }}>
        <Skeleton active />
      </div>
    );
  }

  return (
    <div style={{ padding: 28 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Title level={3} style={{ fontFamily: HEADING_FONT, marginTop: 0 }}>
          Templates
        </Title>
        <AdminGate fallback="hide">
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>
            Upload Template
          </Button>
        </AdminGate>
      </div>
      {groups.every((group) => group.templates.length === 0) ? (
        <Empty description="No templates uploaded yet" />
      ) : (
        groups.map((group) => (
          <div key={group.contractTypeId} style={{ marginBottom: 24 }}>
            <Title level={5} style={{ fontFamily: HEADING_FONT }}>
              {group.displayName}
            </Title>
            {group.templates.length === 0 ? (
              <Text type="secondary">No templates for this type</Text>
            ) : (
              <Row gutter={[16, 16]}>
                {group.templates.map((template) => (
                  <Col xs={24} md={12} xl={8} key={template.id}>
                    <Card
                      title={<span style={{ fontFamily: HEADING_FONT }}>{template.templateName}</span>}
                      extra={
                        <Button
                          size="small"
                          onClick={() =>
                            downloadTemplate(template.id, template.filename || 'template').catch((error) =>
                              notification.error({ message: apiError(error, 'Download failed') }),
                            )
                          }
                        >
                          Download
                        </Button>
                      }
                    >
                      <Paragraph type="secondary">{template.description || 'No description'}</Paragraph>
                      <Space direction="vertical" size={0}>
                        <Text type="secondary">{template.filename}</Text>
                        <Text type="secondary">
                          {formatDateTime(template.uploadedAt)} · {template.uploadedBy}
                        </Text>
                      </Space>
                    </Card>
                  </Col>
                ))}
              </Row>
            )}
          </div>
        ))
      )}

      <Modal
        open={open}
        title="Upload template"
        onCancel={() => setOpen(false)}
        onOk={submit}
        confirmLoading={saving}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="contractTypeId" label="Contract type" rules={[{ required: true }]}>
            <Select options={types.map((type) => ({ value: type.id, label: type.displayName }))} />
          </Form.Item>
          <Form.Item name="templateName" label="Template name" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="Description">
            <Input.TextArea rows={3} />
          </Form.Item>
          <Upload
            accept=".pdf,.doc,.docx,application/pdf"
            maxCount={1}
            beforeUpload={(next) => {
              setFile(next);
              return false;
            }}
            onRemove={() => setFile(null)}
            fileList={file ? [{ uid: 'file', name: file.name, status: 'done' }] : []}
          >
            <Button>Select PDF or Word file</Button>
          </Upload>
        </Form>
      </Modal>
    </div>
  );
}
