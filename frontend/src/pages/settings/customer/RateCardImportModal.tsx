import { useState, useCallback, useMemo } from 'react';
import {
  Button,
  Modal,
  Select,
  Spin,
  Table,
  Tag,
  Typography,
  Upload,
  notification,
} from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import type { UploadFile } from 'antd/es/upload';
import { HEADING_FONT } from '@/theme/antdTheme';
import { importRateCards } from './api';
import type {
  CustomerSummary,
  RateCardImportResult,
  RateCardImportRowError,
  RateCardImportSkipped,
} from './types';

const { Dragger } = Upload;
const { Text, Paragraph } = Typography;

type ImportStep = 1 | 2 | 3;

interface Props {
  open: boolean;
  customers: CustomerSummary[];
  onClose: () => void;
  onImported: () => void;
}

export default function RateCardImportModal({
  open,
  customers,
  onClose,
  onImported,
}: Props) {
  const [step, setStep] = useState<ImportStep>(1);
  const [file, setFile] = useState<File | null>(null);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [result, setResult] = useState<RateCardImportResult | null>(null);
  const [filterCustomerCode, setFilterCustomerCode] = useState<string | 'ALL'>('ALL');

  const reset = useCallback(() => {
    setStep(1);
    setFile(null);
    setFileList([]);
    setResult(null);
    setFilterCustomerCode('ALL');
  }, []);

  const handleClose = () => {
    reset();
    onClose();
  };

  const handleImport = async () => {
    if (!file) return;
    setStep(2);
    try {
      const importResult = await importRateCards(file);
      setResult(importResult);
      setStep(3);
      onImported();
    } catch {
      notification.error({ message: 'Rate card import failed' });
      setStep(1);
    }
  };

  const customerFilterOptions = useMemo(() => {
    const codes = new Set<string>();
    customers.forEach((c) => codes.add(c.customerCode));
    result?.errors.forEach((e) => {
      if (e.customerCode) codes.add(e.customerCode);
    });
    result?.skipped.forEach((s) => codes.add(s.customerCode));
    return [
      { label: 'All customers', value: 'ALL' },
      ...Array.from(codes)
        .sort()
        .map((code) => ({ label: code, value: code })),
    ];
  }, [customers, result]);

  const filteredErrors = useMemo(() => {
    if (!result || filterCustomerCode === 'ALL') return result?.errors ?? [];
    return result.errors.filter((e) => e.customerCode === filterCustomerCode);
  }, [result, filterCustomerCode]);

  const filteredSkipped = useMemo(() => {
    if (!result || filterCustomerCode === 'ALL') return result?.skipped ?? [];
    return result.skipped.filter((s) => s.customerCode === filterCustomerCode);
  }, [result, filterCustomerCode]);

  const errorColumns: ColumnsType<RateCardImportRowError> = [
    { title: 'Row Number', dataIndex: 'rowNumber', key: 'rowNumber', width: 110 },
    { title: 'Customer Code', dataIndex: 'customerCode', key: 'customerCode', width: 130 },
    { title: 'Rate Card Name', dataIndex: 'rateCardName', key: 'rateCardName' },
    { title: 'Reason', dataIndex: 'reason', key: 'reason' },
  ];

  const skippedColumns: ColumnsType<RateCardImportSkipped> = [
    { title: 'Customer Code', dataIndex: 'customerCode', key: 'customerCode', width: 130 },
    { title: 'Rate Card Name', dataIndex: 'rateCardName', key: 'rateCardName' },
    { title: 'Effective From', dataIndex: 'effectiveFrom', key: 'effectiveFrom', width: 130 },
  ];

  const footer = () => {
    if (step === 1) {
      return [
        <Button key="cancel" onClick={handleClose}>
          Cancel
        </Button>,
        <Button
          key="import"
          type="primary"
          disabled={!file}
          onClick={handleImport}
          style={{ fontFamily: HEADING_FONT, fontWeight: 600 }}
        >
          Import
        </Button>,
      ];
    }
    if (step === 2) {
      return null;
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
          Import Rate Cards
        </span>
      }
      open={open}
      onCancel={step === 2 ? undefined : handleClose}
      closable={step !== 2}
      maskClosable={step !== 2}
      footer={footer()}
      width={640}
      destroyOnHidden
    >
      {step === 1 && (
        <div>
          <Paragraph type="secondary">
            Upload an Excel file with one row per rate card line. Use
            &quot;Download Sample File&quot; for the correct column headers.
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
        <div style={{ textAlign: 'center', padding: '32px 0' }}>
          <Spin size="large" />
          <Paragraph style={{ marginTop: 16 }}>Importing rate cards…</Paragraph>
        </div>
      )}

      {step === 3 && result && (
        <div>
          <Select
            style={{ width: '100%', marginBottom: 16 }}
            value={filterCustomerCode}
            onChange={setFilterCustomerCode}
            options={customerFilterOptions}
          />

          <Paragraph>
            <Tag color="success" style={{ fontSize: 13 }}>
              {result.rateCardsCreated} created
            </Tag>
            <Tag color="warning" style={{ fontSize: 13, marginLeft: 8 }}>
              {result.rateCardsSkipped} skipped
            </Tag>
          </Paragraph>

          {result.rateCardsSkipped > 0 && (
            <Paragraph type="secondary" style={{ fontSize: 13 }}>
              Skipped because an active rate card already exists for these customers.
            </Paragraph>
          )}

          {filteredSkipped.length > 0 && (
            <>
              <Text strong style={{ display: 'block', marginBottom: 8 }}>
                Skipped rate cards
              </Text>
              <Table
                dataSource={filteredSkipped}
                columns={skippedColumns}
                rowKey={(row) => `${row.customerCode}-${row.rateCardName}-${row.effectiveFrom}`}
                size="small"
                pagination={{ pageSize: 5, hideOnSinglePage: true }}
                style={{ marginBottom: 16 }}
              />
            </>
          )}

          {filteredErrors.length > 0 && (
            <>
              <Text strong style={{ display: 'block', marginBottom: 8 }}>
                Row errors
              </Text>
              <Table
                dataSource={filteredErrors}
                columns={errorColumns}
                rowKey={(row) => `${row.rowNumber}-${row.customerCode}-${row.rateCardName}`}
                size="small"
                pagination={{ pageSize: 5, hideOnSinglePage: true }}
              />
            </>
          )}
        </div>
      )}
    </Modal>
  );
}
