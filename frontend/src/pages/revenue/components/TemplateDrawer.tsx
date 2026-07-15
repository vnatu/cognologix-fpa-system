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
import { IMPORT_TYPE_LABELS, SYSTEM_ATTRIBUTE_LABELS } from '../constants';
import type { MappingLine, MappingTemplate, RevenueImportType } from '../types';

const { Text } = Typography;

interface Props {
  importType: RevenueImportType;
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

  const columns: ColumnsType<MappingLine> = [
    { title: 'Excel Column', dataIndex: 'excelColumnName', key: 'excel' },
    {
      title: 'System Attribute',
      dataIndex: 'systemAttribute',
      key: 'attr',
      render: (a: string) => SYSTEM_ATTRIBUTE_LABELS[a] ?? a,
    },
  ];

  return (
    <Drawer
      title={`${IMPORT_TYPE_LABELS[importType]} — Mapping Templates`}
      open={open}
      onClose={onClose}
      width={560}
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
          defaultTemplateName={
            creating
              ? `New ${IMPORT_TYPE_LABELS[importType]} template`
              : template?.templateName
          }
          onSaved={(saved) => {
            setTemplate(saved);
            onTemplateSaved?.(saved);
            setEditing(false);
            setCreating(false);
          }}
          onCancel={() => {
            setEditing(false);
            setCreating(false);
          }}
        />
      ) : template ? (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="Name">
              {template.templateName}
            </Descriptions.Item>
            <Descriptions.Item label="Updated">
              {formatDateTime(template.updatedAt)}
            </Descriptions.Item>
          </Descriptions>
          <Table
            rowKey={(r) => `${r.excelColumnName}-${r.systemAttribute}`}
            columns={columns}
            dataSource={template.lines}
            pagination={false}
            size="small"
          />
          <Space>
            <Button icon={<EditOutlined />} onClick={startEdit}>
              Edit
            </Button>
            <Button icon={<PlusOutlined />} onClick={startCreate}>
              Create New
            </Button>
          </Space>
          <Text type="secondary">
            Canonical template home is Settings → Revenue.
          </Text>
        </Space>
      ) : (
        <Empty description="No saved template for this import type">
          <Button type="primary" icon={<PlusOutlined />} onClick={startCreate}>
            Create template
          </Button>
        </Empty>
      )}
    </Drawer>
  );
}
