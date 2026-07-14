import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Empty,
  Skeleton,
  Space,
  Table,
  Typography,
  notification,
} from 'antd';
import { EditOutlined, PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { fetchMappingTemplatesByType } from '../api';
import ColumnMappingEditor from '../components/ColumnMappingEditor';
import {
  IMPORT_TYPE_LABELS,
  SYSTEM_ATTRIBUTE_LABELS,
} from '../constants';
import type { ImportType, MappingLine, MappingTemplate } from '../types';

const { Text } = Typography;

const IMPORT_TYPES: ImportType[] = [
  'ZOHO_PEOPLE',
  'ZOHO_PAYROLL',
  'ZOHO_PAYROLL_FNF',
  'ZOHO_PEOPLE_EXITED',
];

function TemplateSection({
  importType,
  template,
  onTemplateChange,
}: {
  importType: ImportType;
  template: MappingTemplate | null;
  onTemplateChange: (importType: ImportType, template: MappingTemplate | null) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [creating, setCreating] = useState(false);
  const [editHeaders, setEditHeaders] = useState<string[]>([]);
  const [editMappings, setEditMappings] = useState<Record<string, string>>({});

  const startEdit = () => {
    if (!template) return;
    setEditHeaders(template.lines.map((l) => l.excelColumnName));
    const mappings: Record<string, string> = {};
    for (const line of template.lines) {
      mappings[line.excelColumnName] = line.systemAttribute;
    }
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
    <Card
      size="small"
      title={IMPORT_TYPE_LABELS[importType]}
      style={{ marginBottom: 16 }}
    >
      {editing || creating ? (
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
            onTemplateChange(importType, saved);
            setEditing(false);
            setCreating(false);
          }}
          onCancel={() => {
            setEditing(false);
            setCreating(false);
          }}
        />
      ) : template ? (
        <Space direction="vertical" style={{ width: '100%' }}>
          <Text type="secondary">Active template: {template.templateName}</Text>
          <Table
            rowKey="excelColumnName"
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
              Create New Template
            </Button>
          </Space>
        </Space>
      ) : (
        <Space direction="vertical">
          <Empty description="No active template" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          <Button type="primary" icon={<PlusOutlined />} onClick={startCreate}>
            Create New Template
          </Button>
        </Space>
      )}
    </Card>
  );
}

export default function ColumnMappingTemplatesSection() {
  const [templatesByType, setTemplatesByType] = useState<
    Partial<Record<ImportType, MappingTemplate[]>>
  >({});
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setTemplatesByType(await fetchMappingTemplatesByType());
    } catch {
      notification.error({ message: 'Failed to load mapping templates' });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const handleTemplateChange = (
    importType: ImportType,
    template: MappingTemplate | null,
  ) => {
    setTemplatesByType((prev) => {
      const next = { ...prev };
      if (template) {
        next[importType] = [template];
      } else {
        delete next[importType];
      }
      return next;
    });
  };

  if (loading) {
    return <Skeleton active paragraph={{ rows: 8 }} />;
  }

  return (
    <div>
      {IMPORT_TYPES.map((type) => (
        <TemplateSection
          key={type}
          importType={type}
          template={templatesByType[type]?.[0] ?? null}
          onTemplateChange={handleTemplateChange}
        />
      ))}
    </div>
  );
}
