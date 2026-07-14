import { useState } from 'react';
import {
  Button,
  Input,
  Modal,
  Select,
  Space,
  Table,
  notification,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { saveMappingTemplate } from '../api';
import {
  ATTRIBUTES_BY_IMPORT_TYPE,
  SYSTEM_ATTRIBUTE_LABELS,
} from '../constants';
import type { ImportType, MappingTemplate } from '../types';

interface Props {
  importType: ImportType;
  excelHeaders: string[];
  mappings: Record<string, string>;
  onMappingsChange: (mappings: Record<string, string>) => void;
  onHeadersChange?: (headers: string[]) => void;
  allowAddHeaders?: boolean;
  onSaved?: (template: MappingTemplate) => void;
  onCancel?: () => void;
  defaultTemplateName?: string;
  showActions?: boolean;
}

export default function ColumnMappingEditor({
  importType,
  excelHeaders,
  mappings,
  onMappingsChange,
  onHeadersChange,
  allowAddHeaders = false,
  onSaved,
  onCancel,
  defaultTemplateName = 'Import mapping',
  showActions = true,
}: Props) {
  const [saving, setSaving] = useState(false);
  const [nameModalOpen, setNameModalOpen] = useState(false);
  const [templateName, setTemplateName] = useState(defaultTemplateName);
  const [newHeader, setNewHeader] = useState('');

  const attributes = ATTRIBUTES_BY_IMPORT_TYPE[importType];
  const attributeOptions = attributes.map((attr) => ({
    label: SYSTEM_ATTRIBUTE_LABELS[attr] ?? attr,
    value: attr,
  }));

  const usedAttributes = new Set(
    Object.values(mappings).filter(Boolean),
  );

  const handleSave = async (name: string) => {
    const lines = excelHeaders
      .filter((h) => mappings[h])
      .map((h) => ({
        excelColumnName: h,
        systemAttribute: mappings[h],
      }));
    if (lines.length === 0) {
      notification.warning({ message: 'Map at least one column before saving' });
      return;
    }
    setSaving(true);
    try {
      const saved = await saveMappingTemplate({
        importType,
        templateName: name,
        lines,
      });
      notification.success({ message: 'Mapping template saved' });
      onSaved?.(saved);
    } catch {
      notification.error({ message: 'Failed to save mapping template' });
    } finally {
      setSaving(false);
      setNameModalOpen(false);
    }
  };

  const addHeader = () => {
    const trimmed = newHeader.trim();
    if (!trimmed || excelHeaders.includes(trimmed)) return;
    onHeadersChange?.([...excelHeaders, trimmed]);
    setNewHeader('');
  };

  const columns: ColumnsType<{ header: string }> = [
    {
      title: 'Excel Column',
      dataIndex: 'header',
      key: 'header',
    },
    {
      title: 'System Attribute',
      key: 'attribute',
      render: (_, { header }) => (
        <Select
          allowClear
          placeholder="Select attribute"
          style={{ width: '100%' }}
          value={mappings[header] || undefined}
          options={attributeOptions.map((opt) => ({
            ...opt,
            disabled:
              usedAttributes.has(opt.value) && mappings[header] !== opt.value,
          }))}
          onChange={(value) =>
            onMappingsChange({ ...mappings, [header]: value ?? '' })
          }
        />
      ),
    },
  ];

  const data = excelHeaders.map((header) => ({ header, key: header }));

  return (
    <>
      <Table
        rowKey="header"
        columns={columns}
        dataSource={data}
        pagination={false}
        size="small"
      />

      {allowAddHeaders && (
        <Space style={{ marginTop: 12 }}>
          <Input
            placeholder="Excel column name"
            value={newHeader}
            onChange={(e) => setNewHeader(e.target.value)}
            onPressEnter={addHeader}
          />
          <Button icon={<PlusOutlined />} onClick={addHeader}>
            Add column
          </Button>
        </Space>
      )}

      {showActions && (
        <Space style={{ marginTop: 16 }}>
          <Button
            type="primary"
            loading={saving}
            onClick={() => {
              setTemplateName(defaultTemplateName);
              setNameModalOpen(true);
            }}
          >
            Save template
          </Button>
          {onCancel && <Button onClick={onCancel}>Cancel</Button>}
        </Space>
      )}

      <Modal
        title="Template name"
        open={nameModalOpen}
        onCancel={() => setNameModalOpen(false)}
        onOk={() => handleSave(templateName)}
        confirmLoading={saving}
        okText="Save"
      >
        <Input
          value={templateName}
          onChange={(e) => setTemplateName(e.target.value)}
          placeholder="Template name"
        />
      </Modal>
    </>
  );
}

/** Build initial mappings from a saved template and file headers. */
export function buildInitialMappings(
  headers: string[],
  templateLines: Array<{ excelColumnName: string; systemAttribute: string }>,
): Record<string, string> {
  const byExcel = new Map(
    templateLines.map((l) => [l.excelColumnName, l.systemAttribute]),
  );
  const mappings: Record<string, string> = {};
  for (const header of headers) {
    const attr = byExcel.get(header);
    if (attr) mappings[header] = attr;
  }
  return mappings;
}

/** Lines for upload/save from current header mappings. */
export function mappingsToLines(
  headers: string[],
  mappings: Record<string, string>,
): Array<{ excelColumnName: string; systemAttribute: string }> {
  return headers
    .filter((h) => mappings[h])
    .map((h) => ({
      excelColumnName: h,
      systemAttribute: mappings[h],
    }));
}

/** Review-step warnings based on the current file and mappings only. */
export function importReviewWarnings(
  headers: string[],
  mappings: Record<string, string>,
  requiredAttributes: string[],
): {
  unmapped: string[];
  missingRequiredAttributes: string[];
} {
  const unmapped = headers.filter((h) => !mappings[h]);
  const mappedAttributes = new Set(
    Object.values(mappings).filter(Boolean),
  );
  const missingRequiredAttributes = requiredAttributes.filter(
    (attr) => !mappedAttributes.has(attr),
  );
  return { unmapped, missingRequiredAttributes };
}
