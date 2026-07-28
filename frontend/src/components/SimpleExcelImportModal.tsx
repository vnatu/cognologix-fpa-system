import { useCallback, useState } from 'react';
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

const { Dragger } = Upload;
const { Text, Paragraph } = Typography;

export interface SimpleImportResult {
  totalRows: number;
  created: number;
  skipped: number;
  errors: Array<{ rowNumber: number; reason: string }>;
}

interface Props {
  open: boolean;
  title: string;
  onClose: () => void;
  onImported: () => void;
  importFile: (file: File) => Promise<SimpleImportResult>;
  /** File accept attribute — defaults to Excel. */
  accept?: string;
  hint?: string;
}

export default function SimpleExcelImportModal({
  open,
  title,
  onClose,
  onImported,
  importFile,
  accept = '.xlsx,.xls',
  hint = 'Supports .xlsx and .xls only',
}: Props) {
  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [file, setFile] = useState<File | null>(null);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [importing, setImporting] = useState(false);
  const [result, setResult] = useState<SimpleImportResult | null>(null);

  const reset = useCallback(() => {
    setStep(1);
    setFile(null);
    setFileList([]);
    setImporting(false);
    setResult(null);
  }, []);

  const handleClose = () => {
    const hadResult = !!result;
    reset();
    onClose();
    if (hadResult) onImported();
  };

  const handleImport = async () => {
    if (!file) return;
    setImporting(true);
    setStep(2);
    try {
      const importResult = await importFile(file);
      setResult(importResult);
      setStep(3);
    } catch {
      notification.error({ message: `${title} failed` });
      setStep(1);
    } finally {
      setImporting(false);
    }
  };

  const errorColumns: ColumnsType<{ rowNumber: number; reason: string }> = [
    { title: 'Row', dataIndex: 'rowNumber', key: 'rowNumber', width: 80 },
    { title: 'Reason', dataIndex: 'reason', key: 'reason' },
  ];

  const footer =
    step === 1
      ? [
          <Button key="cancel" onClick={handleClose}>
            Cancel
          </Button>,
          <Button key="import" type="primary" disabled={!file} onClick={handleImport}>
            Import
          </Button>,
        ]
      : step === 3
        ? [
            <Button
              key="close"
              type="primary"
              onClick={handleClose}
              style={{ fontFamily: HEADING_FONT, fontWeight: 600 }}
            >
              Close
            </Button>,
          ]
        : null;

  return (
    <Modal
      title={<span style={{ fontFamily: HEADING_FONT, fontWeight: 700 }}>{title}</span>}
      open={open}
      onCancel={handleClose}
      footer={footer}
      destroyOnClose
      width={560}
    >
      <Spin spinning={importing}>
        {step === 1 && (
          <Dragger
            accept={accept}
            maxCount={1}
            fileList={fileList}
            beforeUpload={(f) => {
              setFile(f);
              setFileList([
                {
                  uid: f.name,
                  name: f.name,
                  status: 'done',
                },
              ]);
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
            <p className="ant-upload-hint">{hint}</p>
          </Dragger>
        )}

        {step === 2 && <Paragraph>Importing…</Paragraph>}

        {step === 3 && result && (
          <div>
            <Paragraph>
              <Text strong>{result.created}</Text> created,{' '}
              <Text strong>{result.skipped}</Text> skipped
              {result.errors.length > 0 && (
                <>
                  , <Text strong>{result.errors.length}</Text> error
                  {result.errors.length === 1 ? '' : 's'}
                </>
              )}
              .
            </Paragraph>
            {result.errors.length > 0 && (
              <Table
                dataSource={result.errors}
                columns={errorColumns}
                rowKey={(row) => `${row.rowNumber}-${row.reason}`}
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
