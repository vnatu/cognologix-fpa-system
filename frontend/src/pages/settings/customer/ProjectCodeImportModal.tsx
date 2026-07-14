import { useState, useCallback } from 'react';
import {
  Button,
  Modal,
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
import { importProjectCodes } from './api';
import type { ProjectCodeImportResult, ProjectCodeImportRowError } from './types';

const { Dragger } = Upload;
const { Text, Paragraph } = Typography;

type ImportStep = 1 | 2 | 3;

interface Props {
  open: boolean;
  onClose: () => void;
  onImported: () => void;
}

export default function ProjectCodeImportModal({ open, onClose, onImported }: Props) {
  const [step, setStep] = useState<ImportStep>(1);
  const [file, setFile] = useState<File | null>(null);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [importing, setImporting] = useState(false);
  const [result, setResult] = useState<ProjectCodeImportResult | null>(null);

  const reset = useCallback(() => {
    setStep(1);
    setFile(null);
    setFileList([]);
    setImporting(false);
    setResult(null);
  }, []);

  const handleClose = () => {
    reset();
    onClose();
    if (result) {
      onImported();
    }
  };

  const handleImport = async () => {
    if (!file) return;
    setImporting(true);
    setStep(2);
    try {
      const importResult = await importProjectCodes(file);
      setResult(importResult);
      setStep(3);
    } catch {
      notification.error({ message: 'Project code import failed' });
      setStep(1);
    } finally {
      setImporting(false);
    }
  };

  const errorColumns: ColumnsType<ProjectCodeImportRowError> = [
    { title: 'Row Number', dataIndex: 'rowNumber', key: 'rowNumber', width: 110 },
    {
      title: 'Customer Code',
      dataIndex: 'customerCode',
      key: 'customerCode',
      render: (v: string) => v || '—',
    },
    {
      title: 'Project Code',
      dataIndex: 'projectCode',
      key: 'projectCode',
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
        <Button key="import" type="primary" disabled={!file} onClick={handleImport}>
          Import
        </Button>,
      ];
    }
    if (step === 3) {
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
    }
    return null;
  };

  return (
    <Modal
      title={
        <span style={{ fontFamily: HEADING_FONT, fontWeight: 700 }}>
          Import Project Codes
        </span>
      }
      open={open}
      onCancel={handleClose}
      footer={footer()}
      width={560}
      destroyOnHidden
      closable={step !== 2}
      maskClosable={step !== 2}
    >
      <Spin spinning={importing}>
        {step === 1 && (
          <div>
            <Paragraph type="secondary">
              Upload an Excel file (.xlsx or .xls). Existing project codes for a
              customer are skipped automatically.
            </Paragraph>
            <Dragger
              accept=".xlsx,.xls"
              maxCount={1}
              fileList={fileList}
              beforeUpload={(f) => {
                setFile(f);
                setFileList([{ uid: '-1', name: f.name, status: 'done' }]);
                return false;
              }}
              onRemove={() => {
                setFile(null);
                setFileList([]);
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

        {step === 2 && (
          <Paragraph>Importing project codes…</Paragraph>
        )}

        {step === 3 && result && (
          <div>
            <Paragraph>
              <Text strong>{result.created}</Text> created,{' '}
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
                rowKey={(row) => `${row.rowNumber}-${row.customerCode}-${row.projectCode}`}
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
