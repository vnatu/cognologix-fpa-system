import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Alert,
  Button,
  Input,
  Modal,
  Select,
  Space,
  Steps,
  Typography,
  Upload,
  notification,
} from 'antd';
import { InboxOutlined, SettingOutlined } from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';
import axios from 'axios';
import { HEADING_FONT } from '@/theme/antdTheme';
import {
  fetchMappingTemplate,
  parseHeaders,
  saveMappingTemplate,
  uploadCreditNotes,
  uploadInvoices,
} from '../api';
import ColumnMappingEditor, {
  buildInitialMappings,
  importReviewWarnings,
  mappingsToLines,
} from '../components/ColumnMappingEditor';
import TemplateDrawer from '../components/TemplateDrawer';
import {
  IMPORT_TYPE_LABELS,
  MONTH_OPTIONS,
  REQUIRED_ATTRIBUTES_BY_IMPORT_TYPE,
  SYSTEM_ATTRIBUTE_LABELS,
  yearOptions,
} from '../constants';
import type { MappingTemplate, RevenueImportType, UploadResult } from '../types';

const { Dragger } = Upload;
const { Title, Text } = Typography;

interface Props {
  importType: RevenueImportType;
}

export default function ImportPage({ importType }: Props) {
  const now = new Date();
  const [step, setStep] = useState(0);
  const [periodMonth, setPeriodMonth] = useState(now.getMonth() + 1);
  const [periodYear, setPeriodYear] = useState(now.getFullYear());
  const [file, setFile] = useState<File | null>(null);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [headers, setHeaders] = useState<string[]>([]);
  const [rowCount, setRowCount] = useState(0);
  const [mappings, setMappings] = useState<Record<string, string>>({});
  const [template, setTemplate] = useState<MappingTemplate | null>(null);
  const [templateDrawerOpen, setTemplateDrawerOpen] = useState(false);
  const [parsing, setParsing] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [savingTemplate, setSavingTemplate] = useState(false);
  const [uploadResult, setUploadResult] = useState<UploadResult | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [saveNameModalOpen, setSaveNameModalOpen] = useState(false);
  const [templateNameInput, setTemplateNameInput] = useState('');

  const loadTemplate = useCallback(async () => {
    try {
      setTemplate(await fetchMappingTemplate(importType));
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 401) return;
      notification.error({ message: 'Failed to load mapping template' });
    }
  }, [importType]);

  useEffect(() => {
    loadTemplate();
  }, [loadTemplate]);

  const handleFileSelect = async (selected: File) => {
    setParsing(true);
    try {
      const parsed = await parseHeaders(importType, selected);
      setFile(selected);
      setFileList([{ uid: '-1', name: selected.name, status: 'done' }]);
      setHeaders(parsed.headers);
      setRowCount(parsed.rowCount);
      const tmpl = template ?? (await fetchMappingTemplate(importType));
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

  const warnings = importReviewWarnings(
    headers,
    mappings,
    REQUIRED_ATTRIBUTES_BY_IMPORT_TYPE[importType],
  );
  const hasWarnings =
    warnings.unmapped.length > 0 ||
    warnings.missingRequiredAttributes.length > 0;

  const defaultTemplateName = () =>
    template?.templateName ?? `${IMPORT_TYPE_LABELS[importType]} template`;

  const ensureMappingId = async (templateName: string): Promise<string> => {
    const lines = mappingsToLines(headers, mappings);
    const saved = await saveMappingTemplate({
      importType,
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
        template?.templateName ?? defaultTemplateName(),
      );
      const result =
        importType === 'ZOHO_BOOKS_INVOICES'
          ? await uploadInvoices(periodMonth, periodYear, mappingId, file)
          : await uploadCreditNotes(periodMonth, periodYear, mappingId, file);
      setUploadResult(result);
      setStep(3);
      notification.success({
        message: `Imported ${result.rowsImported} rows`,
      });
    } catch (err) {
      const message =
        axios.isAxiosError(err) && typeof err.response?.data?.error === 'string'
          ? err.response.data.error
          : 'Upload failed';
      notification.error({ message });
    } finally {
      setUploading(false);
      setConfirmOpen(false);
    }
  };

  const handleUploadClick = () => {
    if (hasWarnings) {
      setConfirmOpen(true);
    } else {
      doUpload();
    }
  };

  const openSaveTemplateModal = () => {
    setTemplateNameInput(defaultTemplateName());
    setSaveNameModalOpen(true);
  };

  const confirmSaveTemplate = async () => {
    const name = templateNameInput.trim();
    if (!name) {
      notification.warning({ message: 'Enter a template name' });
      return;
    }
    setSavingTemplate(true);
    try {
      const lines = mappingsToLines(headers, mappings);
      const saved = await saveMappingTemplate({
        importType,
        templateName: name,
        lines,
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

  const reset = () => {
    setStep(0);
    setFile(null);
    setFileList([]);
    setHeaders([]);
    setRowCount(0);
    setMappings({});
    setUploadResult(null);
  };

  const invoicesLink = (() => {
    const params = new URLSearchParams({
      periodMonth: String(periodMonth),
      periodYear: String(periodYear),
      importType,
    });
    return `/revenue/invoices?${params.toString()}`;
  })();

  return (
    <div style={{ padding: 24 }}>
      <Space
        style={{
          width: '100%',
          justifyContent: 'space-between',
          marginBottom: 20,
        }}
      >
        <Title level={4} style={{ fontFamily: HEADING_FONT, margin: 0 }}>
          {IMPORT_TYPE_LABELS[importType]} Import
        </Title>
        <Button
          type="link"
          icon={<SettingOutlined />}
          onClick={() => setTemplateDrawerOpen(true)}
        >
          Manage Templates
        </Button>
      </Space>

      <Steps
        current={step}
        style={{ marginBottom: 32, maxWidth: 720 }}
        items={[
          { title: 'Upload file' },
          { title: 'Column mapping' },
          { title: 'Review' },
          { title: 'Result' },
        ]}
      />

      {step === 0 && (
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <div>
            <Text strong>Reporting period</Text>
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
                options={yearOptions().map((y) => ({ label: String(y), value: y }))}
                onChange={setPeriodYear}
              />
            </Space>
          </div>
          <Dragger
            accept=".xlsx,.xls"
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
            <p className="ant-upload-text">
              Click or drag an Excel file (.xlsx, .xls)
            </p>
            <p className="ant-upload-hint">
              File is parsed for headers before upload. Period is tagged
              manually (not linked to People & Payroll periods).
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
            importType={importType}
            excelHeaders={headers}
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
            message={`Ready to import ${rowCount} rows with ${Object.values(mappings).filter(Boolean).length} mapped columns for ${MONTH_OPTIONS.find((m) => m.value === periodMonth)?.label} ${periodYear}.`}
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
            <Button onClick={openSaveTemplateModal}>
              Save mapping as template
            </Button>
            <Button
              type="primary"
              loading={uploading}
              onClick={handleUploadClick}
            >
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
            message={`Successfully imported ${uploadResult.rowsImported} rows (version ${uploadResult.versionNumber}).`}
          />
          {uploadResult.unmappedColumns.length > 0 && (
            <Alert
              type="warning"
              showIcon
              message="Unmapped columns"
              description={uploadResult.unmappedColumns.join(', ')}
            />
          )}
          {uploadResult.missingColumns.length > 0 && (
            <Alert
              type="warning"
              showIcon
              message="Template columns not found in file"
              description={uploadResult.missingColumns.join(', ')}
            />
          )}
          {uploadResult.unrecognizedCustomerCodes.length > 0 && (
            <Alert
              type="warning"
              showIcon
              message="Unrecognised customer codes"
              description={uploadResult.unrecognizedCustomerCodes.join(', ')}
            />
          )}
          {uploadResult.duplicateNumbers.length > 0 && (
            <Alert
              type="warning"
              showIcon
              message="Duplicate document numbers skipped"
              description={uploadResult.duplicateNumbers.join(', ')}
            />
          )}
          <Space>
            <Link to={invoicesLink}>
              <Button>View uploaded records</Button>
            </Link>
            <Button type="primary" onClick={reset}>
              Import another file
            </Button>
          </Space>
        </Space>
      )}

      <Modal
        title="Save mapping template"
        open={saveNameModalOpen}
        onCancel={() => setSaveNameModalOpen(false)}
        onOk={confirmSaveTemplate}
        confirmLoading={savingTemplate}
        okText="Save"
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <Text type="secondary">
            Give this column mapping a name. It will be reused the next time you
            import {IMPORT_TYPE_LABELS[importType]} data.
          </Text>
          <Input
            placeholder="Template name"
            value={templateNameInput}
            onChange={(e) => setTemplateNameInput(e.target.value)}
            onPressEnter={confirmSaveTemplate}
            maxLength={255}
            showCount
          />
        </Space>
      </Modal>

      <Modal
        title="Continue with mapping warnings?"
        open={confirmOpen}
        onCancel={() => setConfirmOpen(false)}
        onOk={doUpload}
        okText="Continue"
        cancelText="Go back"
        confirmLoading={uploading}
      >
        Some columns are unmapped or required fields are not mapped. Do you want
        to continue anyway?
      </Modal>

      <TemplateDrawer
        importType={importType}
        open={templateDrawerOpen}
        onClose={() => setTemplateDrawerOpen(false)}
        onTemplateSaved={(t) => setTemplate(t)}
      />
    </div>
  );
}
