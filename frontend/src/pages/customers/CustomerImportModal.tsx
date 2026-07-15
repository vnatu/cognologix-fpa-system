import { useState, useCallback } from 'react';
import * as XLSX from 'xlsx';
import {
  Button,
  Collapse,
  Modal,
  Radio,
  Spin,
  Table,
  Typography,
  Upload,
  notification,
} from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { UploadFile } from 'antd/es/upload';
import { HEADING_FONT } from '@/theme/antdTheme';
import {
  checkCustomerImportConflicts,
  importCustomers,
} from './api';
import type {
  ConflictResolution,
  CustomerImportConflicts,
  CustomerImportResult,
  CustomerImportRowError,
} from './types';

const { Dragger } = Upload;
const { Text, Paragraph } = Typography;

const TEMPLATE_HEADERS = [
  'Customer Code',
  'Customer Name',
  'Zoho Books Customer Ref',
  'Lifecycle Status',
  'DSO Days',
  'Relationship Owner Employee ID',
];

type ImportStep = 1 | 2 | 3 | 4;

interface Props {
  open: boolean;
  onClose: () => void;
  onImported: () => void;
}

export function downloadCustomerImportTemplate(): void {
  const ws = XLSX.utils.aoa_to_sheet([TEMPLATE_HEADERS]);
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, ws, 'Customers');
  XLSX.writeFile(wb, 'customer_import_template.xlsx');
}

export default function CustomerImportModal({ open, onClose, onImported }: Props) {
  const [step, setStep] = useState<ImportStep>(1);
  const [file, setFile] = useState<File | null>(null);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [conflicts, setConflicts] = useState<CustomerImportConflicts | null>(null);
  const [conflictResolution, setConflictResolution] = useState<ConflictResolution>('SKIP');
  const [checkingConflicts, setCheckingConflicts] = useState(false);
  const [importing, setImporting] = useState(false);
  const [result, setResult] = useState<CustomerImportResult | null>(null);

  const reset = useCallback(() => {
    setStep(1);
    setFile(null);
    setFileList([]);
    setConflicts(null);
    setConflictResolution('SKIP');
    setCheckingConflicts(false);
    setImporting(false);
    setResult(null);
  }, []);

  const handleClose = () => {
    reset();
    onClose();
  };

  const handleFileSelected = async (selected: File) => {
    setFile(selected);
    setFileList([{ uid: '-1', name: selected.name, status: 'done' }]);
    setCheckingConflicts(true);
    try {
      const detected = await checkCustomerImportConflicts(selected);
      setConflicts(detected);
      if (detected.existingCodes.length === 0) {
        setConflictResolution('SKIP');
        setStep(3);
      } else {
        setStep(2);
      }
    } catch {
      notification.error({ message: 'Failed to check import conflicts' });
      setFile(null);
      setFileList([]);
    } finally {
      setCheckingConflicts(false);
    }
  };

  const handleImport = async () => {
    if (!file) return;
    setImporting(true);
    try {
      const importResult = await importCustomers(file, conflictResolution);
      setResult(importResult);
      setStep(4);
      onImported();
    } catch {
      notification.error({ message: 'Customer import failed' });
    } finally {
      setImporting(false);
    }
  };

  const newCount = conflicts?.newCodes.length ?? 0;
  const existingCount = conflicts?.existingCodes.length ?? 0;

  const errorColumns: ColumnsType<CustomerImportRowError> = [
    { title: 'Row Number', dataIndex: 'rowNumber', key: 'rowNumber', width: 110 },
    {
      title: 'Customer Code',
      dataIndex: 'customerCode',
      key: 'customerCode',
      render: (v: string) => v || '—',
    },
    { title: 'Reason', dataIndex: 'reason', key: 'reason' },
  ];

  const footer = () => {
    if (step === 1) {
      return [
        <Button key="cancel" onClick={handleClose}>
          Cancel
        </Button>,
      ];
    }
    if (step === 2) {
      return [
        <Button key="back" onClick={() => setStep(1)}>
          Back
        </Button>,
        <Button key="next" type="primary" onClick={() => setStep(3)}>
          Next
        </Button>,
      ];
    }
    if (step === 3) {
      return [
        <Button key="back" onClick={() => setStep(conflicts?.existingCodes.length ? 2 : 1)}>
          Back
        </Button>,
        <Button
          key="import"
          type="primary"
          loading={importing}
          onClick={handleImport}
        >
          Import
        </Button>,
      ];
    }
    return [
      <Button
        key="close"
        type="primary"
        onClick={handleClose}
        style={{ fontFamily: HEADING_FONT, fontWeight: 600 }}
      >
        Close
      </Button>,
    ];
  };

  return (
    <Modal
      title={
        <span style={{ fontFamily: HEADING_FONT, fontWeight: 700 }}>
          Import Customers
        </span>
      }
      open={open}
      onCancel={handleClose}
      footer={footer()}
      width={560}
      destroyOnHidden
    >
      <Spin spinning={checkingConflicts || importing}>
        {step === 1 && (
          <div>
            <Paragraph type="secondary">
              Upload an Excel file (.xlsx or .xls) with customer data. Use
              &quot;Download Sample File&quot; for the correct column headers.
            </Paragraph>
            <Dragger
              accept=".xlsx,.xls"
              maxCount={1}
              fileList={fileList}
              beforeUpload={(f) => {
                void handleFileSelected(f);
                return false;
              }}
              onRemove={() => {
                setFile(null);
                setFileList([]);
                setConflicts(null);
                setStep(1);
              }}
            >
              <p className="ant-upload-drag-icon">
                <InboxOutlined />
              </p>
              <p className="ant-upload-text">Click or drag file to upload</p>
              <p className="ant-upload-hint">Supports .xlsx and .xls only</p>
            </Dragger>
          </div>
        )}

        {step === 2 && conflicts && (
          <div>
            <Paragraph>
              <Text strong>{existingCount}</Text>
              {' '}
              customer{existingCount === 1 ? '' : 's'}
              {' '}
              in this file already exist in the system. What would you like to do?
            </Paragraph>
            <Radio.Group
              value={conflictResolution}
              onChange={(e) => setConflictResolution(e.target.value)}
              style={{ display: 'flex', flexDirection: 'column', gap: 12 }}
            >
              <Radio value="SKIP">
                Skip existing customers (only import new ones)
              </Radio>
              <Radio value="REPLACE">
                Replace existing customers (update their details)
              </Radio>
            </Radio.Group>
            <Collapse
              style={{ marginTop: 16 }}
              items={[
                {
                  key: 'codes',
                  label: `View conflicting Customer Codes (${existingCount})`,
                  children: (
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                      {conflicts.existingCodes.map((code) => (
                        <code key={code}>{code}</code>
                      ))}
                    </div>
                  ),
                },
              ]}
            />
          </div>
        )}

        {step === 3 && (
          <div>
            <Paragraph>
              <Text strong>{newCount}</Text>
              {' '}
              new customer{newCount === 1 ? '' : 's'}
              {' '}
              will be created
              {existingCount > 0 && (
                <>
                  , and <Text strong>{existingCount}</Text>
                  {' '}
                  existing customer{existingCount === 1 ? '' : 's'}
                  {' '}
                  will be {conflictResolution === 'SKIP' ? 'skipped' : 'replaced'}.
                </>
              )}
              .
            </Paragraph>
            {file && (
              <Text type="secondary">File: {file.name}</Text>
            )}
          </div>
        )}

        {step === 4 && result && (
          <div>
            <Paragraph>
              <Text strong>{result.created}</Text> created,{' '}
              <Text strong>{result.updated}</Text> updated,{' '}
              <Text strong>{result.skipped}</Text> skipped
              {result.errors.length > 0 && (
                <>
                  , <Text strong>{result.errors.length}</Text> row
                  {result.errors.length === 1 ? '' : 's'} with errors
                </>
              )}
              .
            </Paragraph>
            {result.errors.length > 0 && (
              <Table
                dataSource={result.errors}
                columns={errorColumns}
                rowKey={(row) => `${row.rowNumber}-${row.customerCode}`}
                size="small"
                pagination={{ pageSize: 5, hideOnSinglePage: true }}
                style={{ marginTop: 16 }}
              />
            )}
          </div>
        )}
      </Spin>
    </Modal>
  );
}
