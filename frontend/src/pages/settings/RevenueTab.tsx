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
import { fetchMappingTemplatesByType } from '@/pages/revenue/api';
import ColumnMappingEditor from '@/pages/revenue/components/ColumnMappingEditor';
import {
  IMPORT_TYPE_LABELS,
  SYSTEM_ATTRIBUTE_LABELS,
} from '@/pages/revenue/constants';
import type {
  MappingLine,
  MappingTemplate,
  RevenueImportType,
} from '@/pages/revenue/types';

const { Text } = Typography;

const IMPORT_TYPES: RevenueImportType[] = [
  'ZOHO_BOOKS_INVOICES',
  'ZOHO_BOOKS_CREDIT_NOTES',
];

function TemplateSection({
  importType,
  template,
  onTemplateChange,
}: {
  importType: RevenueImportType;
  template: MappingTemplate | null;
  onTemplateChange: (
    importType: RevenueImportType,
    template: MappingTemplate | null,
  ) => void;
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
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Text type="secondary">{template.templateName}</Text>
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
        </Space>
      ) : (
        <Empty description="No template configured">
          <Button type="primary" icon={<PlusOutlined />} onClick={startCreate}>
            Create New
          </Button>
        </Empty>
      )}
    </Card>
  );
}

export default function RevenueTab() {
  const [templates, setTemplates] = useState<
    Partial<Record<RevenueImportType, MappingTemplate | null>>
  >({});
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const grouped = await fetchMappingTemplatesByType();
      const next: Partial<Record<RevenueImportType, MappingTemplate | null>> =
        {};
      for (const type of IMPORT_TYPES) {
        next[type] = grouped[type]?.[0] ?? null;
      }
      setTemplates(next);
    } catch {
      notification.error({ message: 'Failed to load revenue mapping templates' });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (loading) {
    return <Skeleton active paragraph={{ rows: 8 }} />;
  }

  return (
    <div>
      <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
        Column mapping templates for Zoho Books imports. Also available
        contextually on the Revenue Import screens.
      </Text>
      {IMPORT_TYPES.map((type) => (
        <TemplateSection
          key={type}
          importType={type}
          template={templates[type] ?? null}
          onTemplateChange={(importType, template) =>
            setTemplates((prev) => ({ ...prev, [importType]: template }))
          }
        />
      ))}
    </div>
  );
}
