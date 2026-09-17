import { useState } from 'react';
import {
  Button,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Typography,
  notification,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { saveColumnMapping } from '../api';
import {
  HEADER_ATTRIBUTES,
  HDFC_IMPORT_TYPE,
  SYSTEM_ATTRIBUTE_LABELS,
  TRANSACTION_ATTRIBUTES,
} from '../constants';
import type { MappingTemplate } from '../types';
import { normalizeHeader } from '@/utils/excelHeaders';

const { Text } = Typography;

interface Props {
  headerFields: string[];
  transactionColumns: string[];
  mappings: Record<string, string>;
  onMappingsChange: (mappings: Record<string, string>) => void;
  onHeaderFieldsChange?: (headers: string[]) => void;
  onTransactionColumnsChange?: (headers: string[]) => void;
  allowAddHeaders?: boolean;
  onSaved?: (template: MappingTemplate) => void;
  onCancel?: () => void;
  defaultTemplateName?: string;
  showActions?: boolean;
}

export default function ColumnMappingEditor({
  headerFields,
  transactionColumns,
  mappings,
  onMappingsChange,
  onHeaderFieldsChange,
  onTransactionColumnsChange,
  allowAddHeaders = false,
  onSaved,
  onCancel,
  defaultTemplateName = 'HDFC bank statement mapping',
  showActions = true,
}: Props) {
  const [saving, setSaving] = useState(false);
  const [nameModalOpen, setNameModalOpen] = useState(false);
  const [templateName, setTemplateName] = useState(defaultTemplateName);
  const [newHeaderField, setNewHeaderField] = useState('');
  const [newTxnHeader, setNewTxnHeader] = useState('');

  const usedAttributes = new Set(Object.values(mappings).filter(Boolean));

  const handleSave = async (name: string) => {
    const lines = mappingsToLines([...headerFields, ...transactionColumns], mappings);
    if (lines.length === 0) {
      notification.warning({ message: 'Map at least one column before saving' });
      return;
    }
    setSaving(true);
    try {
      const saved = await saveColumnMapping({
        importType: HDFC_IMPORT_TYPE,
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

  const addHeader = (
    value: string,
    existing: string[],
    onChange?: (headers: string[]) => void,
    reset?: (v: string) => void,
  ) => {
    const trimmed = value.trim();
    if (!trimmed || existing.includes(trimmed) || !onChange) return;
    onChange([...existing, trimmed]);
    reset?.('');
  };

  const section = (
    title: string,
    headers: string[],
    attributes: readonly { attr: string; label: string }[],
  ) => {
    const options = attributes.map((a) => ({
      label: SYSTEM_ATTRIBUTE_LABELS[a.attr] ?? a.attr,
      value: a.attr,
    }));
    const columns: ColumnsType<{ header: string }> = [
      { title: 'Source label', dataIndex: 'header', key: 'header' },
      {
        title: 'System attribute',
        key: 'attribute',
        render: (_, { header }) => (
          <Select
            allowClear
            placeholder="Select attribute"
            style={{ width: '100%' }}
            value={mappings[header] || undefined}
            options={options.map((opt) => ({
              ...opt,
              disabled: usedAttributes.has(opt.value) && mappings[header] !== opt.value,
            }))}
            onChange={(value) =>
              onMappingsChange({ ...mappings, [header]: value ?? '' })
            }
          />
        ),
      },
    ];
    return (
      <div>
        <Text strong style={{ display: 'block', marginBottom: 8 }}>
          {title}
        </Text>
        <Table
          rowKey="header"
          columns={columns}
          dataSource={headers.map((header) => ({ header, key: header }))}
          pagination={false}
          size="small"
        />
      </div>
    );
  };

  return (
    <>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {section('Header Fields', headerFields, HEADER_ATTRIBUTES)}
        {allowAddHeaders && (
          <Space>
            <Input
              placeholder="Header field label"
              value={newHeaderField}
              onChange={(e) => setNewHeaderField(e.target.value)}
              onPressEnter={() =>
                addHeader(newHeaderField, headerFields, onHeaderFieldsChange, setNewHeaderField)
              }
            />
            <Button
              icon={<PlusOutlined />}
              onClick={() =>
                addHeader(newHeaderField, headerFields, onHeaderFieldsChange, setNewHeaderField)
              }
            >
              Add header field
            </Button>
          </Space>
        )}
        {section('Transaction Columns', transactionColumns, TRANSACTION_ATTRIBUTES)}
        {allowAddHeaders && (
          <Space>
            <Input
              placeholder="Transaction column name"
              value={newTxnHeader}
              onChange={(e) => setNewTxnHeader(e.target.value)}
              onPressEnter={() =>
                addHeader(
                  newTxnHeader,
                  transactionColumns,
                  onTransactionColumnsChange,
                  setNewTxnHeader,
                )
              }
            />
            <Button
              icon={<PlusOutlined />}
              onClick={() =>
                addHeader(
                  newTxnHeader,
                  transactionColumns,
                  onTransactionColumnsChange,
                  setNewTxnHeader,
                )
              }
            >
              Add transaction column
            </Button>
          </Space>
        )}
      </Space>

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

export function buildInitialMappings(
  headers: string[],
  templateLines: Array<{ excelColumnName: string; systemAttribute: string }>,
): Record<string, string> {
  const byExcel = new Map(
    templateLines.map((l) => [normalizeHeader(l.excelColumnName), l.systemAttribute]),
  );
  const mappings: Record<string, string> = {};
  for (const header of headers) {
    const attr = byExcel.get(normalizeHeader(header));
    if (attr) mappings[header] = attr;
  }
  return mappings;
}

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

export function importReviewWarnings(
  headers: string[],
  mappings: Record<string, string>,
  requiredAttributes: string[],
): { unmapped: string[]; missingRequiredAttributes: string[] } {
  const unmapped = headers.filter((h) => !mappings[h]);
  const mappedAttributes = new Set(Object.values(mappings).filter(Boolean));
  const missingRequiredAttributes = requiredAttributes.filter(
    (attr) => !mappedAttributes.has(attr),
  );
  return { unmapped, missingRequiredAttributes };
}
