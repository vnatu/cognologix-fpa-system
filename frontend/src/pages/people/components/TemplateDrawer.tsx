import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Descriptions,
  Drawer,
  Empty,
  Space,
  Spin,
  Table,
  Typography,
} from 'antd';
import { EditOutlined, PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { useDateFormat } from '@/context/DateFormatContext';
import { fetchMappingTemplate } from '../api';
import ColumnMappingEditor from './ColumnMappingEditor';
import {
  ATTRIBUTES_BY_IMPORT_TYPE,
  IMPORT_TYPE_LABELS,
  SYSTEM_ATTRIBUTE_LABELS,
} from '../constants';
import type { ImportType, MappingLine, MappingTemplate } from '../types';

const { Text } = Typography;

interface Props {
  importType: ImportType;
  open: boolean;
  onClose: () => void;
  onTemplateSaved?: (template: MappingTemplate) => void;
}

export default function TemplateDrawer({
  importType,
  open,
  onClose,
  onTemplateSaved,
}: Props) {
  const { formatDateTime } = useDateFormat();
  const [template, setTemplate] = useState<MappingTemplate | null>(null);
  const [loading, setLoading] = useState(false);
  const [editing, setEditing] = useState(false);
  const [creating, setCreating] = useState(false);
  const [editHeaders, setEditHeaders] = useState<string[]>([]);
  const [editMappings, setEditMappings] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setTemplate(await fetchMappingTemplate(importType));
    } finally {
      setLoading(false);
    }
  }, [importType]);

  useEffect(() => {
    if (open) {
      setEditing(false);
      setCreating(false);
      load();
    }
  }, [open, load]);

  const startEdit = () => {
    if (!template) return;
    const headers = template.lines.map((l) => l.excelColumnName);
    const mappings: Record<string, string> = {};
    for (const line of template.lines) {
      mappings[line.excelColumnName] = line.systemAttribute;
    }
    setEditHeaders(headers);
    setEditMappings(mappings);
    setEditing(true);
    setCreating(false);
  };

  const startCreate = () => {
    setEditHeaders([]);
    setEditMappings({});
    setCreating(true);
    setEditing(false);
  };

  const handleSaved = (saved: MappingTemplate) => {
    setTemplate(saved);
    setEditing(false);
    setCreating(false);
    onTemplateSaved?.(saved);
  };

  const readOnlyColumns: ColumnsType<MappingLine> = [
    {
      title: 'Excel Column',
      dataIndex: 'excelColumnName',
      key: 'excelColumnName',
    },
    {
      title: 'System Attribute',
      dataIndex: 'systemAttribute',
      key: 'systemAttribute',
      render: (attr: string) => SYSTEM_ATTRIBUTE_LABELS[attr] ?? attr,
    },
  ];

  return (
    <Drawer
      title={`${IMPORT_TYPE_LABELS[importType]} — Mapping Template`}
      open={open}
      onClose={onClose}
      width="50%"
      destroyOnClose
    >
      {loading ? (
        <Spin />
      ) : editing || creating ? (
        <ColumnMappingEditor
          importType={importType}
          excelHeaders={editHeaders}
          mappings={editMappings}
          onMappingsChange={setEditMappings}
          onHeadersChange={setEditHeaders}
          allowAddHeaders
          onSaved={handleSaved}
          onCancel={() => {
            setEditing(false);
            setCreating(false);
          }}
          defaultTemplateName={
            creating
              ? `New ${IMPORT_TYPE_LABELS[importType]} template`
              : template?.templateName
          }
        />
      ) : template ? (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <Descriptions column={1} size="small">
            <Descriptions.Item label="Template name">
              {template.templateName}
            </Descriptions.Item>
            <Descriptions.Item label="Last updated">
              {formatDateTime(template.updatedAt)}
            </Descriptions.Item>
          </Descriptions>
          <Table
            rowKey={(r) => r.excelColumnName}
            columns={readOnlyColumns}
            dataSource={template.lines}
            pagination={false}
            size="small"
          />
          <Space>
            <Button icon={<EditOutlined />} onClick={startEdit}>
              Edit
            </Button>
            <Button icon={<PlusOutlined />} onClick={startCreate}>
              Create new template
            </Button>
          </Space>
        </Space>
      ) : (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <Empty description="No active template for this import type" />
          <Button type="primary" icon={<PlusOutlined />} onClick={startCreate}>
            Create new template
          </Button>
        </Space>
      )}

      {!editing && !creating && template && (
        <Text
          type="secondary"
          style={{ display: 'block', marginTop: 16, fontSize: 12 }}
        >
          Attributes available:{' '}
          {ATTRIBUTES_BY_IMPORT_TYPE[importType]
            .map((a) => SYSTEM_ATTRIBUTE_LABELS[a])
            .join(', ')}
        </Text>
      )}
    </Drawer>
  );
}
