import { useCallback, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Button,
  Empty,
  Input,
  Modal,
  Select,
  Space,
  Steps,
  Typography,
  Upload,
  notification,
} from 'antd';
import { DownloadOutlined, InboxOutlined } from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';
import axios from 'axios';
import { HEADING_FONT } from '@/theme/antdTheme';
import { useIsAdmin } from '@/components/AdminGate';
import {
  downloadMappingSample,
  fetchColumnMapping,
  parseStatementHeaders,
  saveColumnMapping,
  uploadStatement,
} from './api';
import ColumnMappingEditor, {
  buildInitialMappings,
  importReviewWarnings,
  mappingsToLines,
} from './components/ColumnMappingEditor';
import {
  HDFC_IMPORT_TYPE,
  MONTH_OPTIONS,
  REQUIRED_ATTRIBUTES,
  SYSTEM_ATTRIBUTE_LABELS,
} from './constants';
import type { MappingTemplate, ReconRun } from './types';

const { Dragger } = Upload;
const { Title, Text } = Typography;

const current = new Date();
const YEAR_OPTIONS = Array.from({ length: 6 }, (_, i) => current.getFullYear() - 2 + i);

export default function NewRunPage() {
  const isAdmin = useIsAdmin();
  const navigate = useNavigate();
  const [step, setStep] = useState(0);
  const [periodMonth, setPeriodMonth] = useState(current.getMonth() + 1);
  const [periodYear, setPeriodYear] = useState(current.getFullYear());
  const [file, setFile] = useState<File | null>(null);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [headerFields, setHeaderFields] = useState<string[]>([]);
  const [transactionColumns, setTransactionColumns] = useState<string[]>([]);
  const [headerFieldValues, setHeaderFieldValues] = useState<Record<string, string>>({});
  const [rowCount, setRowCount] = useState(0);
  const [mappings, setMappings] = useState<Record<string, string>>({});
  const [template, setTemplate] = useState<MappingTemplate | null>(null);
  const [parsing, setParsing] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [savingTemplate, setSavingTemplate] = useState(false);
  const [saveNameModalOpen, setSaveNameModalOpen] = useState(false);
  const [templateNameInput, setTemplateNameInput] = useState('');
  const [uploadResult, setUploadResult] = useState<ReconRun | null>(null);

  const allHeaders = useMemo(
    () => [...headerFields, ...transactionColumns],
    [headerFields, transactionColumns],
  );

  const warnings = importReviewWarnings(allHeaders, mappings, REQUIRED_ATTRIBUTES);

  const mappedHeaderLabel = (attr: string) =>
    allHeaders.find((h) => mappings[h] === attr);

  const detectedAccount = useMemo(() => {
    const label = mappedHeaderLabel('AccountNumber');
    return label ? headerFieldValues[label] : undefined;
  }, [allHeaders, mappings, headerFieldValues]);

  const detectedFrom = useMemo(() => {
    const label = mappedHeaderLabel('FromDate');
    return label ? headerFieldValues[label] : undefined;
  }, [allHeaders, mappings, headerFieldValues]);

  const detectedTo = useMemo(() => {
    const label = mappedHeaderLabel('ToDate');
    return label ? headerFieldValues[label] : undefined;
  }, [allHeaders, mappings, headerFieldValues]);

  const periodLabel = `${MONTH_OPTIONS.find((m) => m.value === periodMonth)?.label ?? periodMonth} ${periodYear}`;

  const handleFileSelect = async (selected: File) => {
    setParsing(true);
    try {
      const parsed = await parseStatementHeaders(selected);
      const tmpl = await fetchColumnMapping();
      setFile(selected);
      setFileList([{ uid: '-1', name: selected.name, status: 'done' }]);
      setHeaderFields(parsed.headerFields);
      setTransactionColumns(parsed.transactionColumns);
      setHeaderFieldValues(parsed.headerFieldValues ?? {});
      setRowCount(parsed.rowCount);
      setTemplate(tmpl);
      setMappings(buildInitialMappings(parsed.headers, tmpl?.lines ?? []));
      setStep(1);
    } catch {
      notification.error({ message: 'Failed to parse file headers' });
      setFile(null);
      setFileList([]);
    } finally {
      setParsing(false);
    }
    return false;
  };

  const ensureMappingId = async (templateName: string): Promise<string> => {
    const lines = mappingsToLines(allHeaders, mappings);
    const saved = await saveColumnMapping({
      importType: HDFC_IMPORT_TYPE,
      templateName,
      lines,
    });
    setTemplate(saved);
    return saved.id;
  };

  const doUpload = async () => {
    if (!file) return;
    setUploading(true);
    try {
      const mappingId = await ensureMappingId(
        template?.templateName ?? 'HDFC bank statement mapping',
      );
      const result = await uploadStatement(file, mappingId);
      setUploadResult(result);
      setStep(3);
      notification.success({ message: `Imported ${result.totalTransactions} transactions` });
      navigate(`/bank-reconciliation/runs/${result.id}`);
    } catch (err) {
      const message =
        axios.isAxiosError(err) && typeof err.response?.data?.message === 'string'
          ? err.response.data.message
          : 'Upload failed';
      notification.error({ message });
    } finally {
      setUploading(false);
    }
  };

  const confirmSaveTemplate = async () => {
    const name = templateNameInput.trim();
    if (!name) {
      notification.warning({ message: 'Enter a template name' });
      return;
    }
    setSavingTemplate(true);
    try {
      const saved = await saveColumnMapping({
        importType: HDFC_IMPORT_TYPE,
        templateName: name,
        lines: mappingsToLines(allHeaders, mappings),
      });
      setTemplate(saved);
      setSaveNameModalOpen(false);
      notification.success({ message: `Template "${name}" saved` });
    } catch {
      notification.error({ message: 'Failed to save template' });
    } finally {
      setSavingTemplate(false);
    }
  };

  const reset = useCallback(() => {
    setStep(0);
    setFile(null);
    setFileList([]);
    setHeaderFields([]);
    setTransactionColumns([]);
    setHeaderFieldValues({});
    setRowCount(0);
    setMappings({});
    setUploadResult(null);
  }, []);

  if (!isAdmin) {
    return (
      <div style={{ padding: 48 }}>
        <Empty description="Admin access required" />
      </div>
    );
  }

  return (
    <div style={{ padding: 28, maxWidth: 960 }}>
      <Title level={3} style={{ fontFamily: HEADING_FONT, marginBottom: 8 }}>
        New reconciliation run
      </Title>
      <Text type="secondary">
        Map HDFC statement columns once, then reuse the template on later uploads.
      </Text>

      <Steps
        current={step}
        style={{ margin: '24px 0' }}
        items={[
          { title: 'Period & file' },
          { title: 'Column mapping' },
          { title: 'Review' },
          { title: 'Result' },
        ]}
      />

      {step === 0 && (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <div>
            <Text strong>Statement period</Text>
            <Space style={{ display: 'flex', marginTop: 8 }} wrap>
              <Select
                style={{ width: 180 }}
                value={periodMonth}
                options={MONTH_OPTIONS}
                onChange={setPeriodMonth}
              />
              <Select
                style={{ width: 120 }}
                value={periodYear}
                options={YEAR_OPTIONS.map((y) => ({ value: y, label: String(y) }))}
                onChange={setPeriodYear}
              />
            </Space>
          </div>
          <Button
            icon={<DownloadOutlined />}
            onClick={() => {
              downloadMappingSample().catch(() =>
                notification.error({ message: 'Failed to download sample file' }),
              );
            }}
          >
            Download sample template
          </Button>
          <Dragger
            accept=".csv,.xls,.xlsx"
            maxCount={1}
            fileList={fileList}
            disabled={parsing}
            beforeUpload={handleFileSelect}
            onRemove={() => {
              setFile(null);
              setFileList([]);
            }}
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">Drop HDFC CSV or Excel here, or click to browse</p>
            <p className="ant-upload-hint">
              Headers are parsed for mapping. Admin only.
            </p>
          </Dragger>
        </Space>
      )}

      {step === 1 && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {template ? (
            <Alert
              type="info"
              showIcon
              message={`Using saved template — ${template.templateName}. You can adjust mappings below.`}
            />
          ) : (
            <Alert
              type="warning"
              showIcon
              message="No saved template found. Map your columns below to create one."
            />
          )}
          <ColumnMappingEditor
            headerFields={headerFields}
            transactionColumns={transactionColumns}
            mappings={mappings}
            onMappingsChange={setMappings}
            showActions={false}
          />
          <Space>
            <Button onClick={() => setStep(0)}>Back</Button>
            <Button type="primary" onClick={() => setStep(2)}>
              Continue to review
            </Button>
          </Space>
        </Space>
      )}

      {step === 2 && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Alert
            type="info"
            message={`Ready to import ${rowCount} transactions from ${file?.name ?? 'selected file'} for ${periodLabel}.`}
            description={
              <Space direction="vertical" size={4}>
                <Text>Detected account number: {detectedAccount ?? '—'}</Text>
                <Text>
                  Statement period: {detectedFrom ?? '—'} to {detectedTo ?? '—'}
                </Text>
                <Text>Transaction count: {rowCount}</Text>
              </Space>
            }
          />
          {warnings.unmapped.length > 0 && (
            <Alert
              type="warning"
              showIcon
              message="Unmapped columns in file"
              description={warnings.unmapped.join(', ')}
            />
          )}
          {warnings.missingRequiredAttributes.length > 0 && (
            <Alert
              type="warning"
              showIcon
              message="Required fields not mapped"
              description={warnings.missingRequiredAttributes
                .map((attr) => SYSTEM_ATTRIBUTE_LABELS[attr] ?? attr)
                .join(', ')}
            />
          )}
          <Space>
            <Button onClick={() => setStep(1)}>Back</Button>
            <Button
              onClick={() => {
                setTemplateNameInput(template?.templateName ?? 'HDFC bank statement mapping');
                setSaveNameModalOpen(true);
              }}
            >
              Save mapping as template
            </Button>
            <Button type="primary" loading={uploading} onClick={doUpload}>
              Upload
            </Button>
          </Space>
        </Space>
      )}

      {step === 3 && uploadResult && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Alert
            type="success"
            showIcon
            message={`Imported ${uploadResult.totalTransactions} transactions. Opening run review.`}
          />
          <Button onClick={reset}>Start another upload</Button>
        </Space>
      )}

      <Modal
        title="Template name"
        open={saveNameModalOpen}
        onCancel={() => setSaveNameModalOpen(false)}
        onOk={confirmSaveTemplate}
        confirmLoading={savingTemplate}
        okText="Save"
      >
        <Input
          value={templateNameInput}
          onChange={(e) => setTemplateNameInput(e.target.value)}
          placeholder="Template name"
        />
      </Modal>
    </div>
  );
}
