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
import { DownloadOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { downloadMappingSample, fetchColumnMappingsByType } from '../api';
import ColumnMappingEditor from '../components/ColumnMappingEditor';
import { HDFC_IMPORT_TYPE, SYSTEM_ATTRIBUTE_LABELS } from '../constants';
import type { MappingLine, MappingTemplate } from '../types';
import { useIsAdmin } from '@/components/AdminGate';

const { Text } = Typography;

function TemplateSection({
  template,
  onTemplateChange,
}: {
  template: MappingTemplate | null;
  onTemplateChange: (template: MappingTemplate | null) => void;
}) {
  const isAdmin = useIsAdmin();
  const [editing, setEditing] = useState(false);
  const [creating, setCreating] = useState(false);
  const [headerFields, setHeaderFields] = useState<string[]>([]);
  const [transactionColumns, setTransactionColumns] = useState<string[]>([]);
  const [editMappings, setEditMappings] = useState<Record<string, string>>({});

  const splitLines = (lines: MappingLine[]) => {
    const headers: string[] = [];
    const txns: string[] = [];
    const mappings: Record<string, string> = {};
    for (const line of lines) {
      mappings[line.excelColumnName] = line.systemAttribute;
      if (
        [
          'AccountNumber',
          'CustomerName',
          'FromDate',
          'ToDate',
          'OpeningBalance',
          'ClosingBalance',
        ].includes(line.systemAttribute)
      ) {
        headers.push(line.excelColumnName);
      } else {
        txns.push(line.excelColumnName);
      }
    }
    return { headers, txns, mappings };
  };

  const startEdit = () => {
    if (!template) return;
    const split = splitLines(template.lines);
    setHeaderFields(split.headers);
    setTransactionColumns(split.txns);
    setEditMappings(split.mappings);
    setEditing(true);
    setCreating(false);
  };

  const startCreate = () => {
    setHeaderFields([]);
    setTransactionColumns([]);
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
    <Card size="small" title="HDFC Bank Statement" style={{ marginBottom: 16 }}>
      {editing || creating ? (
        <ColumnMappingEditor
          headerFields={headerFields}
          transactionColumns={transactionColumns}
          mappings={editMappings}
          onMappingsChange={setEditMappings}
          onHeaderFieldsChange={setHeaderFields}
          onTransactionColumnsChange={setTransactionColumns}
          allowAddHeaders
          defaultTemplateName={
            creating ? 'New HDFC bank statement template' : template?.templateName
          }
          onSaved={(saved) => {
            onTemplateChange(saved);
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
          <Text type="secondary">Active template: {template.templateName}</Text>
          <Table
            rowKey={(r) => `${r.excelColumnName}-${r.systemAttribute}`}
            columns={columns}
            dataSource={template.lines}
            pagination={false}
            size="small"
          />
          {isAdmin && (
            <Space>
              <Button icon={<EditOutlined />} onClick={startEdit}>
                Edit
              </Button>
              <Button icon={<PlusOutlined />} onClick={startCreate}>
                Create New Template
              </Button>
            </Space>
          )}
        </Space>
      ) : (
        <Space direction="vertical">
          <Empty description="No active template" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          {isAdmin && (
            <Button type="primary" icon={<PlusOutlined />} onClick={startCreate}>
              Create New Template
            </Button>
          )}
        </Space>
      )}
    </Card>
  );
}

export default function ColumnMappingTemplatesSection() {
  const [template, setTemplate] = useState<MappingTemplate | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const grouped = await fetchColumnMappingsByType();
      setTemplate(grouped[HDFC_IMPORT_TYPE]?.[0] ?? null);
    } catch {
      notification.error({ message: 'Failed to load mapping templates' });
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
      <Space wrap style={{ marginBottom: 16 }}>
        <Button
          icon={<DownloadOutlined />}
          onClick={() => {
            downloadMappingSample().catch(() =>
              notification.error({ message: 'Failed to download sample file' }),
            );
          }}
        >
          Download Sample File
        </Button>
      </Space>
      <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
        Map HDFC statement header labels and transaction columns once. Subsequent uploads
        pre-fill from this template.
      </Text>
      <TemplateSection template={template} onTemplateChange={setTemplate} />
    </div>
  );
}
