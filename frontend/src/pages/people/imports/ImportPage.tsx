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
  fetchPayrollMappingTemplates,
  fetchPeriods,
  parseHeaders,
  saveMappingTemplate,
  uploadSnapshot,
} from '../api';
import ColumnMappingEditor, {
  buildInitialMappings,
  importReviewWarnings,
  mappingsToLines,
} from '../components/ColumnMappingEditor';
import TemplateDrawer from '../components/TemplateDrawer';
import { IMPORT_TYPE_LABELS, REQUIRED_ATTRIBUTES_BY_IMPORT_TYPE, isPayrollImportType, payrollSnapshotDetailPath, payrollTemplateLabel, snapshotDetailPath, SYSTEM_ATTRIBUTE_LABELS } from '../constants';
import type { ImportType, MappingTemplate, SnapshotUploadResult } from '../types';
import { buildImportableVersionOptions, EXPAND_PERIOD_AFTER_UPLOAD_KEY } from '../utils';

const { Dragger } = Upload;
const { Title, Text } = Typography;

interface Props {
  importType: ImportType;
}

export default function ImportPage({ importType }: Props) {
  const [step, setStep] = useState(0);
  const [periodOptions, setPeriodOptions] = useState<
    ReturnType<typeof buildImportableVersionOptions>
  >([]);
  const [periodVersionId, setPeriodVersionId] = useState<string | null>(null);
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
  const [uploadResult, setUploadResult] = useState<SnapshotUploadResult | null>(
    null,
  );
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [saveNameModalOpen, setSaveNameModalOpen] = useState(false);
  const [templateNameInput, setTemplateNameInput] = useState('');
  const [payrollTemplates, setPayrollTemplates] = useState<MappingTemplate[]>([]);
  const [selectedMappingId, setSelectedMappingId] = useState<string | null>(null);

  const isFnfImport = importType === 'ZOHO_PAYROLL_FNF';

  const loadPeriods = useCallback(async () => {
    try {
      const periods = await fetchPeriods();
      const options = buildImportableVersionOptions(periods);
      setPeriodOptions(options);
      if (options.length > 0) {
        setPeriodVersionId((current) => current ?? options[0].periodVersionId);
      }
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 401) {
        notification.error({ message: 'Session expired — please sign in again' });
      } else {
        notification.error({ message: 'Failed to load periods' });
      }
    }
  }, []);

  useEffect(() => {
    loadPeriods();
  }, [loadPeriods]);

  const loadTemplate = useCallback(async () => {
    if (isFnfImport) return;
    try {
      setTemplate(await fetchMappingTemplate(importType));
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 401) return;
      notification.error({ message: 'Failed to load mapping template' });
    }
  }, [importType, isFnfImport]);

  useEffect(() => {
    loadTemplate();
  }, [loadTemplate]);

  const loadPayrollTemplates = useCallback(async () => {
    if (!isFnfImport) return;
    try {
      const templates = await fetchPayrollMappingTemplates();
      setPayrollTemplates(templates);
      if (templates.length > 0 && !selectedMappingId) {
        const preferred =
          templates.find((t) => t.importType === 'ZOHO_PAYROLL_FNF') ??
          templates[0];
        setSelectedMappingId(preferred.id);
        setTemplate(preferred);
        if (headers.length > 0) {
          setMappings(buildInitialMappings(headers, preferred.lines));
        }
      }
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 401) return;
      notification.error({ message: 'Failed to load payroll templates' });
    }
  }, [headers, isFnfImport, selectedMappingId]);

  useEffect(() => {
    loadPayrollTemplates();
  }, [loadPayrollTemplates]);

  const applySelectedTemplate = (mappingId: string) => {
    const tmpl = payrollTemplates.find((t) => t.id === mappingId);
    if (!tmpl) return;
    setSelectedMappingId(mappingId);
    setTemplate(tmpl);
    if (headers.length > 0) {
      setMappings(buildInitialMappings(headers, tmpl.lines));
    }
  };

  const handleFileSelect = async (selected: File) => {
    setParsing(true);
    try {
      const parsed = await parseHeaders(selected);
      setFile(selected);
      setFileList([
        {
          uid: '-1',
          name: selected.name,
          status: 'done',
        },
      ]);
      setHeaders(parsed.headers);
      setRowCount(parsed.rowCount);
      let tmpl = template;
      if (isFnfImport) {
        const templates = await fetchPayrollMappingTemplates();
        setPayrollTemplates(templates);
        tmpl =
          templates.find((t) => t.id === selectedMappingId) ??
          templates.find((t) => t.importType === 'ZOHO_PAYROLL_FNF') ??
          templates[0] ??
          null;
        if (tmpl) {
          setSelectedMappingId(tmpl.id);
        }
      } else {
        tmpl = template ?? (await fetchMappingTemplate(importType));
      }
      setTemplate(tmpl);
      setMappings(
        buildInitialMappings(parsed.headers, tmpl?.lines ?? []),
      );
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
    warnings.unmapped.length > 0 || warnings.missingRequiredAttributes.length > 0;

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
    if (!file || !periodVersionId) return;
    const previousVersionId = periodVersionId;
    setUploading(true);
    try {
      const mappingId =
        isFnfImport && selectedMappingId
          ? selectedMappingId
          : await ensureMappingId(
              template?.templateName ?? defaultTemplateName(),
            );
      const result = await uploadSnapshot(
        periodVersionId,
        importType,
        mappingId,
        file,
      );
      setUploadResult(result);
      setPeriodVersionId(result.periodVersionId);
      if (result.periodVersionId !== previousVersionId) {
        const periods = await fetchPeriods();
        const options = buildImportableVersionOptions(periods);
        setPeriodOptions(options);
        const bumpedPeriod = periods.find((p) =>
          p.versions.some((v) => v.id === result.periodVersionId),
        );
        if (bumpedPeriod) {
          sessionStorage.setItem(
            EXPAND_PERIOD_AFTER_UPLOAD_KEY,
            bumpedPeriod.id,
          );
        }
        notification.info({
          message: 'New period version created',
          description:
            'A corrected upload created a new version. Continue uploading remaining snapshot types against the new version.',
        });
      }
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
    setSelectedMappingId(null);
    setPayrollTemplates([]);
  };

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
            <Select
              style={{ width: '100%', marginTop: 8, maxWidth: 400 }}
              placeholder="Select period version"
              value={periodVersionId ?? undefined}
              options={periodOptions.map((o) => ({
                label: o.label,
                value: o.periodVersionId,
              }))}
              onChange={setPeriodVersionId}
              notFoundContent="No importable period versions — create a period or ensure the latest version is not finalised"
            />
          </div>
          <Dragger
            accept=".xlsx,.xls"
            maxCount={1}
            fileList={fileList}
            disabled={!periodVersionId || parsing}
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
              File is parsed locally for headers before upload
            </p>
          </Dragger>
        </Space>
      )}

      {step === 1 && (
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {isFnfImport && payrollTemplates.length > 0 && (
            <div>
              <Text strong>Mapping template</Text>
              <Select
                style={{ width: '100%', marginTop: 8, maxWidth: 480 }}
                placeholder="Select a payroll mapping template"
                value={selectedMappingId ?? undefined}
                options={payrollTemplates.map((t) => ({
                  label: `${payrollTemplateLabel(t.importType)} — ${t.templateName}`,
                  value: t.id,
                }))}
                onChange={applySelectedTemplate}
              />
              <Text type="secondary" style={{ display: 'block', marginTop: 6 }}>
                F&F uses the same column format as regular payroll — choose any
                payroll template.
              </Text>
            </div>
          )}
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
            message={`Ready to import ${rowCount} rows with ${Object.values(mappings).filter(Boolean).length} mapped columns.`}
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
            message={`Successfully imported ${uploadResult.rowsImported} rows.`}
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
              description={`These Excel column names are in your mapping template but were not in the uploaded file: ${uploadResult.missingColumns.join(', ')}`}
            />
          )}
          {uploadResult.unrecognizedBuCodes.length > 0 && (
            <Alert
              type="warning"
              showIcon
              message="Unrecognised BU codes"
              description={uploadResult.unrecognizedBuCodes.join(', ')}
            />
          )}
          <Space>
            {uploadResult.periodVersionId && (
              <Link
                to={
                  isPayrollImportType(importType)
                    ? payrollSnapshotDetailPath(uploadResult.periodVersionId)
                    : snapshotDetailPath(uploadResult.periodVersionId, importType)
                }
              >
                <Button>View imported rows</Button>
              </Link>
            )}
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
        title="Continue with unmapped columns?"
        open={confirmOpen}
        onCancel={() => setConfirmOpen(false)}
        onOk={doUpload}
        okText="Continue"
        cancelText="Go back"
        confirmLoading={uploading}
      >
        Some columns are unmapped or required fields are not mapped. Do you want to continue anyway?
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
