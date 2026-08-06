import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Collapse,
  Form,
  Input,
  InputNumber,
  Modal,
  notification,
  Select,
  Skeleton,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  theme,
  Typography,
  Upload,
} from 'antd';
import {
  DownloadOutlined,
  ExportOutlined,
  InboxOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { formatCurrency } from '@/utils/formatDate';
import SimpleExcelImportModal from '@/components/SimpleExcelImportModal';
import { useDateFormat } from '@/context/DateFormatContext';
import { HEADING_FONT } from '@/theme/antdTheme';
import { fetchCustomers } from '@/pages/customers/api';
import type { CustomerSummary } from '@/pages/customers/types';
import {
  buildFyMonthCols,
  billableRatio,
  num,
  parseFiscalYearDates,
  STATUS_COLOR,
  TYPE_LABELS,
} from './utils';
import type {
  ClientRevenuePlanEntry,
  ForecastType,
  ForecastVersion,
  FyMonthCol,
  HcPlanMonth,
  OverheadBudgetEntry,
  OverheadLineItem,
  PlanDetail,
  PlanSummary,
  SalaryBudgetMonth,
} from './types';
import {
  createDraftVersion,
  createPlan,
  downloadHcPlanImportSample,
  downloadOverheadBudgetImportSample,
  downloadRevenuePlanImportSample,
  downloadSalaryBudgetImportSample,
  exportAllPlanInputs,
  exportHcPlan,
  exportOverheadBudget,
  exportRevenuePlan,
  exportSalaryBudget,
  fetchHcPlan,
  fetchOverheadBudget,
  fetchOverheadLineItems,
  fetchPlan,
  fetchPlans,
  fetchRevenuePlan,
  fetchSalaryBudget,
  importAllPlanInputs,
  importHcPlan,
  importOverheadBudget,
  importRevenuePlan,
  importSalaryBudget,
  publishVersion,
  saveHcPlan,
  saveOverheadBudget,
  saveRevenuePlan,
  saveSalaryBudget,
  type PlanInputImportResult,
  type PlanInputZipImportResult,
} from './api';
import { useIsAdmin } from '@/components/AdminGate';
import { useUnsavedChanges } from '@/context/UnsavedChangesContext';
import type { FormInstance } from 'antd/es/form';
import type { UploadFile } from 'antd/es/upload';

const { Title, Text, Paragraph } = Typography;
const { Dragger } = Upload;

const PLAN_SETUP_PAGE = 'plan_setup';

function planSetupPeriod(
  planId: string,
  forecastTypeId: string,
  versionId: string,
): string {
  return `${planId}_${forecastTypeId}_${versionId}`;
}

/** Registers a Plan Setup section draft for inactivity / forced logout. */
function usePlanSectionDraft<T>(
  section: string,
  planId: string,
  forecastTypeId: string,
  versionId: string,
  data: T,
  dirty: boolean,
  setData: (value: T) => void,
  setDirty: (value: boolean) => void,
) {
  const { register, peekDraft } = useUnsavedChanges();
  const period = planSetupPeriod(planId, forecastTypeId, versionId);
  const dirtyRef = useRef(dirty);
  const dataRef = useRef(data);
  dirtyRef.current = dirty;
  dataRef.current = data;

  useEffect(() => {
    return register(`plan_setup_${section}_${period}`, {
      pageName: PLAN_SETUP_PAGE,
      period,
      isDirty: () => dirtyRef.current,
      getDraft: () => ({ [section]: dataRef.current }),
    });
  }, [register, section, period]);

  useEffect(() => {
    const onRestore = (event: Event) => {
      const detail = (event as CustomEvent<{ period: string }>).detail;
      if (!detail || detail.period !== period) return;
      const draft = peekDraft<Record<string, T>>(PLAN_SETUP_PAGE, period);
      const sectionData = draft?.[section];
      if (sectionData != null) {
        setData(sectionData);
        setDirty(true);
      }
    };
    window.addEventListener('fpa-restore-plan-draft', onRestore);
    return () => window.removeEventListener('fpa-restore-plan-draft', onRestore);
  }, [period, section, peekDraft, setData, setDirty]);
}

export default function PlanSetupPage() {
  const { token } = theme.useToken();
  const { formatDate } = useDateFormat();
  const isAdmin = useIsAdmin();
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [selectedPlanId, setSelectedPlanId] = useState<string | null>(null);
  const [plan, setPlan] = useState<PlanDetail | null>(null);
  const [loading, setLoading] = useState(false);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [createForm] = Form.useForm();

  const loadPlans = useCallback(async () => {
    try {
      const data = await fetchPlans();
      setPlans(data);
    } catch (error) {
      notification.error({
        message: 'Failed to load plans',
        description: String(error),
      });
    }
  }, []);

  useEffect(() => {
    loadPlans();
  }, [loadPlans]);

  const loadPlan = useCallback(async (planId: string) => {
    setLoading(true);
    try {
      const data = await fetchPlan(planId);
      setPlan(data);
    } catch (error) {
      notification.error({
        message: 'Failed to load plan',
        description: String(error),
      });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selectedPlanId) {
      loadPlan(selectedPlanId);
    } else {
      setPlan(null);
    }
  }, [selectedPlanId, loadPlan]);

  const handleCreatePlan = useCallback(async () => {
    try {
      const values = await createForm.validateFields();
      const newPlan = await createPlan({
        fiscalYear: values.fiscalYear,
        openingHc: values.openingHc,
        fiscalYearStart: values.fiscalYearStart || undefined,
        fiscalYearEnd: values.fiscalYearEnd || undefined,
      });
      await loadPlans();
      setSelectedPlanId(newPlan.id);
      setCreateModalOpen(false);
      createForm.resetFields();
      notification.success({ message: 'Financial year plan created' });
    } catch (error) {
      notification.error({
        message: 'Failed to create plan',
        description: String(error),
      });
    }
  }, [createForm, loadPlans]);

  const sortedTypes = useMemo(() => {
    if (!plan) return [];
    return [...plan.forecastTypes].sort((a, b) => {
      if (a.primary && !b.primary) return -1;
      if (!a.primary && b.primary) return 1;
      return 0;
    });
  }, [plan]);

  if (!plans.length && !loading) {
    return (
      <div style={{ padding: 24 }}>
        <Card>
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <Title level={3} style={{ fontFamily: HEADING_FONT, margin: 0 }}>
              No Financial Year Plans
            </Title>
            {isAdmin ? (
              <Button
                type="primary"
                onClick={() => setCreateModalOpen(true)}
              >
                Create First Plan
              </Button>
            ) : (
              <Text type="secondary">No plans have been created yet.</Text>
            )}
          </Space>
        </Card>

        {isAdmin && (
          <NewFinancialYearModal
            open={createModalOpen}
            form={createForm}
            onOk={handleCreatePlan}
            onCancel={() => {
              setCreateModalOpen(false);
              createForm.resetFields();
            }}
          />
        )}
      </div>
    );
  }

  return (
    <div style={{ padding: 24 }}>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Card>
          <Space>
            <Select
              style={{ minWidth: 200 }}
              placeholder="Select financial year"
              value={selectedPlanId}
              onChange={setSelectedPlanId}
              options={plans.map((p) => ({
                label: p.fiscalYear,
                value: p.id,
              }))}
            />
            {isAdmin && (
              <Button type="primary" onClick={() => setCreateModalOpen(true)}>
                New Financial Year
              </Button>
            )}
          </Space>
        </Card>

        {loading && <Skeleton active />}

        {plan && !loading && (
          <Tabs
            items={sortedTypes.map((type) => ({
              key: type.id,
              label: TYPE_LABELS[type.typeName] ?? type.typeName,
              children: (
                <ForecastTypePanel
                  plan={plan}
                  forecastType={type}
                  token={token}
                  formatDate={formatDate}
                  isAdmin={isAdmin}
                  onReload={() => loadPlan(plan.id)}
                />
              ),
            }))}
          />
        )}
      </Space>

      {isAdmin && (
        <NewFinancialYearModal
          open={createModalOpen}
          form={createForm}
          onOk={handleCreatePlan}
          onCancel={() => {
            setCreateModalOpen(false);
            createForm.resetFields();
          }}
        />
      )}
    </div>
  );
}

function NewFinancialYearModal({
  open,
  form,
  onOk,
  onCancel,
}: {
  open: boolean;
  form: FormInstance;
  onOk: () => void;
  onCancel: () => void;
}) {
  const applyFyDates = (raw: string) => {
    const dates = parseFiscalYearDates(raw);
    if (dates) {
      form.setFieldsValue({
        fiscalYearStart: dates.start,
        fiscalYearEnd: dates.end,
      });
    }
  };

  return (
    <Modal
      title="New Financial Year Plan"
      open={open}
      onOk={onOk}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="fiscalYear"
          label="Fiscal Year"
          rules={[
            { required: true, message: 'Required' },
            {
              pattern: /^FY\d{4}$/i,
              message: 'Must match FY#### format (e.g. FY2627)',
            },
          ]}
        >
          <Input
            placeholder="FY2627"
            onChange={(e) => applyFyDates(e.target.value)}
          />
        </Form.Item>
        <Form.Item
          name="openingHc"
          label="Opening HC"
          rules={[{ required: true, message: 'Required' }]}
        >
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          name="fiscalYearStart"
          label="Fiscal Year Start"
          extra="Auto-filled from FY code (editable)"
        >
          <Input placeholder="YYYY-MM-DD" />
        </Form.Item>
        <Form.Item
          name="fiscalYearEnd"
          label="Fiscal Year End"
          extra="Auto-filled from FY code (editable)"
        >
          <Input placeholder="YYYY-MM-DD" />
        </Form.Item>
      </Form>
    </Modal>
  );
}

interface ForecastTypePanelProps {
  plan: PlanDetail;
  forecastType: ForecastType;
  token: ReturnType<typeof theme.useToken>['token'];
  formatDate: (date: string | Date | null | undefined) => string;
  isAdmin: boolean;
  onReload: () => void;
}

function ForecastTypePanel({
  plan,
  forecastType,
  token,
  formatDate,
  isAdmin,
  onReload,
}: ForecastTypePanelProps) {
  const currentVersion = useMemo(() => {
    const draft = forecastType.versions.find((v) => v.status === 'DRAFT');
    if (draft) return draft;
    return forecastType.versions.find((v) => v.status === 'ACTIVE') ?? null;
  }, [forecastType.versions]);

  const supersededVersions = useMemo(() => {
    return forecastType.versions.filter((v) => v.status === 'SUPERSEDED');
  }, [forecastType.versions]);

  const handlePublish = useCallback(async () => {
    if (!currentVersion || currentVersion.status !== 'DRAFT') return;
    try {
      await publishVersion(plan.id, forecastType.id, currentVersion.id);
      notification.success({ message: 'Version published' });
      onReload();
    } catch (error) {
      notification.error({
        message: 'Failed to publish version',
        description: String(error),
      });
    }
  }, [plan.id, forecastType.id, currentVersion, onReload]);

  const handleCreateRevision = useCallback(async () => {
    if (!currentVersion || currentVersion.status !== 'ACTIVE') return;
    const hasDraft = forecastType.versions.some((v) => v.status === 'DRAFT');
    if (hasDraft) return;
    try {
      await createDraftVersion(plan.id, forecastType.id);
      notification.success({ message: 'Draft revision created' });
      onReload();
    } catch (error) {
      notification.error({
        message: 'Failed to create revision',
        description: String(error),
      });
    }
  }, [plan.id, forecastType.id, currentVersion, forecastType.versions, onReload]);

  if (!currentVersion) {
    return (
      <Card>
        <Text type="secondary">No version available</Text>
      </Card>
    );
  }

  const isDraft = currentVersion.status === 'DRAFT';
  /** ADMIN + DRAFT may edit / save; VIEWER (and non-draft) see InputNumbers disabled. */
  const canEdit = isAdmin && isDraft;
  const [importAllOpen, setImportAllOpen] = useState(false);
  const { peekDraft, discardDraft } = useUnsavedChanges();
  const periodKey = planSetupPeriod(plan.id, forecastType.id, currentVersion.id);
  const [draftBanner, setDraftBanner] = useState(false);

  useEffect(() => {
    const draft = peekDraft(PLAN_SETUP_PAGE, periodKey);
    setDraftBanner(!!draft);
  }, [peekDraft, periodKey]);

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      {draftBanner && (
        <Alert
          type="info"
          showIcon
          message="You have unsaved changes from your previous session. Would you like to restore them?"
          description="Restore reloads the draft into session storage for this plan version. Re-open each grid section after restoring, or discard to keep the last saved server values."
          action={
            <Space>
              <Button
                size="small"
                type="primary"
                onClick={() => {
                  window.dispatchEvent(
                    new CustomEvent('fpa-restore-plan-draft', {
                      detail: { period: periodKey },
                    }),
                  );
                  setDraftBanner(false);
                  discardDraft(PLAN_SETUP_PAGE, periodKey);
                  notification.success({ message: 'Draft restored into plan grids' });
                }}
              >
                Restore
              </Button>
              <Button
                size="small"
                onClick={() => {
                  discardDraft(PLAN_SETUP_PAGE, periodKey);
                  setDraftBanner(false);
                }}
              >
                Discard
              </Button>
            </Space>
          }
        />
      )}
      <Card>
        <Space wrap>
          <Text strong>Version {currentVersion.versionNumber}</Text>
          <Tag color={STATUS_COLOR[currentVersion.status]}>
            {currentVersion.status}
          </Tag>
          {isAdmin && isDraft && (
            <Button type="primary" onClick={handlePublish}>
              Publish
            </Button>
          )}
          {isAdmin &&
            currentVersion.status === 'ACTIVE' &&
            !forecastType.versions.some((v) => v.status === 'DRAFT') && (
              <Button onClick={handleCreateRevision}>Create Revision</Button>
            )}
          <Button
            icon={<ExportOutlined />}
            onClick={() => {
              exportAllPlanInputs(plan.id, forecastType.id, currentVersion.id).catch(
                () =>
                  notification.error({
                    message: 'Failed to export all plan inputs',
                  }),
              );
            }}
          >
            Export All Inputs
          </Button>
          {canEdit && (
            <Button
              icon={<UploadOutlined />}
              onClick={() => setImportAllOpen(true)}
            >
              Import All Inputs
            </Button>
          )}
          {!isDraft && isAdmin && (
            <Text type="secondary">
              Published versions are read-only — create a revision to edit.
            </Text>
          )}
        </Space>

        {supersededVersions.length > 0 && (
          <Collapse
            style={{ marginTop: 16 }}
            items={[
              {
                key: 'history',
                label: 'Version history',
                children: (
                  <Space direction="vertical">
                    {supersededVersions.map((v) => (
                      <div key={v.id}>
                        <Text>
                          Version {v.versionNumber} — Published{' '}
                          {formatDate(v.publishedAt)}, Superseded{' '}
                          {formatDate(v.supersededAt)}
                        </Text>
                      </div>
                    ))}
                  </Space>
                ),
              },
            ]}
          />
        )}
      </Card>

      <ImportAllPlanInputsModal
        open={importAllOpen}
        onClose={() => setImportAllOpen(false)}
        onImported={onReload}
        importZip={(file) =>
          importAllPlanInputs(plan.id, forecastType.id, currentVersion.id, file)
        }
      />

      <HcPlanPanel
        plan={plan}
        forecastType={forecastType}
        version={currentVersion}
        canEdit={canEdit}
        token={token}
      />

      <ClientRevenuePlanPanel
        plan={plan}
        forecastType={forecastType}
        version={currentVersion}
        canEdit={canEdit}
        token={token}
      />

      <SalaryBudgetPanel
        plan={plan}
        forecastType={forecastType}
        version={currentVersion}
        canEdit={canEdit}
        token={token}
      />

      <OverheadBudgetPanel
        plan={plan}
        forecastType={forecastType}
        version={currentVersion}
        canEdit={canEdit}
        token={token}
      />
    </Space>
  );
}

interface PanelProps {
  plan: PlanDetail;
  forecastType: ForecastType;
  version: ForecastVersion;
  canEdit: boolean;
  token: ReturnType<typeof theme.useToken>['token'];
}

interface PlanInputExcelToolbarProps {
  canEdit: boolean;
  exportFn: () => Promise<void>;
  downloadSampleFn: () => Promise<void>;
  importFn: (file: File) => Promise<PlanInputImportResult>;
  onImported: () => void;
  importTitle: string;
}

function PlanInputExcelToolbar({
  canEdit,
  exportFn,
  downloadSampleFn,
  importFn,
  onImported,
  importTitle,
}: PlanInputExcelToolbarProps) {
  const [importOpen, setImportOpen] = useState(false);

  return (
    <>
      <Space wrap>
        <Button
          icon={<ExportOutlined />}
          onClick={() => {
            exportFn().catch(() =>
              notification.error({ message: 'Export failed' }),
            );
          }}
        >
          Export
        </Button>
        <Button
          icon={<DownloadOutlined />}
          onClick={() => {
            downloadSampleFn().catch(() =>
              notification.error({ message: 'Failed to download sample file' }),
            );
          }}
        >
          Download Sample
        </Button>
        {canEdit && (
          <Button icon={<UploadOutlined />} onClick={() => setImportOpen(true)}>
            Import
          </Button>
        )}
      </Space>
      <SimpleExcelImportModal
        open={importOpen}
        title={importTitle}
        onClose={() => setImportOpen(false)}
        onImported={onImported}
        importFile={importFn}
        accept=".xlsx"
        hint="Supports .xlsx only"
      />
    </>
  );
}

function ImportAllPlanInputsModal({
  open,
  onClose,
  onImported,
  importZip,
}: {
  open: boolean;
  onClose: () => void;
  onImported: () => void;
  importZip: (file: File) => Promise<PlanInputZipImportResult>;
}) {
  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [file, setFile] = useState<File | null>(null);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [importing, setImporting] = useState(false);
  const [result, setResult] = useState<PlanInputZipImportResult | null>(null);

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
      const importResult = await importZip(file);
      setResult(importResult);
      setStep(3);
    } catch {
      notification.error({ message: 'Import All Inputs failed' });
      setStep(1);
    } finally {
      setImporting(false);
    }
  };

  const footer =
    step === 1
      ? [
          <Button key="cancel" onClick={handleClose}>
            Cancel
          </Button>,
          <Button
            key="import"
            type="primary"
            disabled={!file}
            onClick={handleImport}
          >
            Import
          </Button>,
        ]
      : step === 3
        ? [
            <Button key="close" type="primary" onClick={handleClose}>
              Close
            </Button>,
          ]
        : null;

  return (
    <Modal
      title={
        <span style={{ fontFamily: HEADING_FONT, fontWeight: 700 }}>
          Import All Inputs
        </span>
      }
      open={open}
      onCancel={handleClose}
      footer={footer}
      destroyOnClose
      width={640}
    >
      <Spin spinning={importing}>
        {step === 1 && (
          <>
            <Paragraph type="secondary">
              Upload the ZIP from Export All Inputs (
              <Text code>hc_plan.xlsx</Text>,{' '}
              <Text code>salary_budget.xlsx</Text>,{' '}
              <Text code>client_revenue_plan.xlsx</Text>,{' '}
              <Text code>overhead_budget.xlsx</Text>).
            </Paragraph>
            <Dragger
              accept=".zip"
              maxCount={1}
              fileList={fileList}
              beforeUpload={(f) => {
                setFile(f);
                setFileList([{ uid: f.name, name: f.name, status: 'done' }]);
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
              <p className="ant-upload-text">Click or drag ZIP to upload</p>
              <p className="ant-upload-hint">Supports .zip only</p>
            </Dragger>
          </>
        )}

        {step === 2 && <Paragraph>Importing plan inputs…</Paragraph>}

        {step === 3 && result && (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            {result.parts.map((part) => (
              <Card key={part.fileName} size="small" title={part.label}>
                {part.missing && (
                  <Text type="warning">
                    Missing {part.fileName} in ZIP — skipped
                  </Text>
                )}
                {part.error && <Text type="danger">{part.error}</Text>}
                {part.result && (
                  <Text>
                    <Text strong>{part.result.created}</Text> created,{' '}
                    <Text strong>{part.result.skipped}</Text> skipped
                    {part.result.errors.length > 0 && (
                      <>
                        , <Text strong>{part.result.errors.length}</Text> error
                        {part.result.errors.length === 1 ? '' : 's'}
                      </>
                    )}
                  </Text>
                )}
                {part.result && part.result.errors.length > 0 && (
                  <Table
                    style={{ marginTop: 8 }}
                    size="small"
                    pagination={false}
                    rowKey={(r) => `${part.fileName}-${r.rowNumber}-${r.reason}`}
                    dataSource={part.result.errors}
                    columns={[
                      {
                        title: 'Row',
                        dataIndex: 'rowNumber',
                        width: 70,
                      },
                      { title: 'Reason', dataIndex: 'reason' },
                    ]}
                  />
                )}
              </Card>
            ))}
          </Space>
        )}
      </Spin>
    </Modal>
  );
}

function HcPlanPanel({
  plan,
  forecastType,
  version,
  canEdit,
}: PanelProps) {
  const [data, setData] = useState<HcPlanMonth[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  usePlanSectionDraft(
    'hcPlan',
    plan.id,
    forecastType.id,
    version.id,
    data,
    dirty,
    setData,
    setDirty,
  );

  const cols = useMemo(() => buildFyMonthCols(plan), [plan]);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await fetchHcPlan(plan.id, forecastType.id, version.id);
      setData(result);
    } catch (error) {
      notification.error({
        message: 'Failed to load HC plan',
        description: String(error),
      });
    } finally {
      setLoading(false);
    }
  }, [plan.id, forecastType.id, version.id]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const monthMap = useMemo(() => {
    const map = new Map<string, HcPlanMonth>();
    data.forEach((m) => {
      map.set(`${m.planYear}-${m.planMonth}`, m);
    });
    return map;
  }, [data]);

  const getValue = useCallback(
    (col: FyMonthCol, field: keyof HcPlanMonth): number => {
      const m = monthMap.get(col.key);
      return m ? num(m[field]) : 0;
    },
    [monthMap],
  );

  const setValue = useCallback(
    (col: FyMonthCol, field: keyof HcPlanMonth, value: number) => {
      setDirty(true);
      setData((prev) => {
        const existing = prev.find(
          (m) => m.planMonth === col.planMonth && m.planYear === col.planYear,
        );
        if (existing) {
          return prev.map((m) =>
            m.planMonth === col.planMonth && m.planYear === col.planYear
              ? { ...m, [field]: value }
              : m,
          );
        } else {
          return [
            ...prev,
            {
              planMonth: col.planMonth,
              planYear: col.planYear,
              plannedHires: 0,
              plannedExits: 0,
              plannedBillableHc: 0,
              plannedBenchHc: 0,
              plannedSupportHc: 0,
              plannedLeadershipHc: 0,
              plannedManagementHc: 0,
              [field]: value,
            },
          ];
        }
      });
    },
    [],
  );

  const handleSave = useCallback(async () => {
    setSaving(true);
    try {
      const months = cols.map((col) => {
        const existing = data.find(
          (m) => m.planMonth === col.planMonth && m.planYear === col.planYear,
        );
        return (
          existing ?? {
            planMonth: col.planMonth,
            planYear: col.planYear,
            plannedHires: 0,
            plannedExits: 0,
            plannedBillableHc: 0,
            plannedBenchHc: 0,
            plannedSupportHc: 0,
            plannedLeadershipHc: 0,
            plannedManagementHc: 0,
          }
        );
      });
      await saveHcPlan(plan.id, forecastType.id, version.id, months);
      notification.success({ message: 'HC plan saved' });
      setDirty(false);
      await loadData();
    } catch (error) {
      notification.error({
        message: 'Failed to save HC plan',
        description: String(error),
      });
    } finally {
      setSaving(false);
    }
  }, [plan.id, forecastType.id, version.id, cols, data, loadData]);

  const columns = [
    {
      title: 'Metric',
      dataIndex: 'metric',
      key: 'metric',
      fixed: 'left' as const,
      width: 180,
    },
    ...cols.map((col) => ({
      title: col.label,
      dataIndex: col.key,
      key: col.key,
      width: 100,
      align: 'right' as const,
    })),
  ];

  const rows = [
    { key: 'hires', metric: 'Planned Hires', field: 'plannedHires' as const },
    { key: 'exits', metric: 'Exits', field: 'plannedExits' as const },
    {
      key: 'billable',
      metric: 'Billable HC',
      field: 'plannedBillableHc' as const,
    },
    { key: 'bench', metric: 'Bench', field: 'plannedBenchHc' as const },
    { key: 'support', metric: 'Support', field: 'plannedSupportHc' as const },
    {
      key: 'leadership',
      metric: 'Leadership',
      field: 'plannedLeadershipHc' as const,
    },
    {
      key: 'management',
      metric: 'Management',
      field: 'plannedManagementHc' as const,
    },
    { key: 'ratio', metric: 'Planned Billable Ratio %', field: null },
  ];

  const dataSource = rows.map((row) => {
    const record: Record<string, any> = {
      key: row.key,
      metric: row.metric,
    };
    cols.forEach((col) => {
      if (row.field === null) {
        const billable = getValue(col, 'plannedBillableHc');
        const total =
          billable +
          getValue(col, 'plannedBenchHc') +
          getValue(col, 'plannedSupportHc') +
          getValue(col, 'plannedLeadershipHc') +
          getValue(col, 'plannedManagementHc');
        record[col.key] = billableRatio(billable, total).toFixed(1);
      } else {
        record[col.key] = (
          <InputNumber
            size="small"
            min={0}
            disabled={!canEdit}
            value={getValue(col, row.field)}
            onChange={(v) => setValue(col, row.field!, v ?? 0)}
            style={{ width: '100%' }}
          />
        );
      }
    });
    return record;
  });

  return (
    <Collapse
      items={[
        {
          key: 'hc',
          label: <Title level={4} style={{ fontFamily: HEADING_FONT, margin: 0 }}>HC Plan</Title>,
          children: (
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              {loading ? (
                <Skeleton active />
              ) : (
                <>
                  <PlanInputExcelToolbar
                    canEdit={canEdit}
                    exportFn={() =>
                      exportHcPlan(plan.id, forecastType.id, version.id)
                    }
                    downloadSampleFn={() =>
                      downloadHcPlanImportSample(
                        plan.id,
                        forecastType.id,
                        version.id,
                      )
                    }
                    importFn={(file) =>
                      importHcPlan(plan.id, forecastType.id, version.id, file)
                    }
                    onImported={loadData}
                    importTitle="Import HC Plan"
                  />
                  <Table
                    dataSource={dataSource}
                    columns={columns}
                    pagination={false}
                    scroll={{ x: true }}
                    size="small"
                  />
                  {canEdit && (
                    <Button
                      type="primary"
                      onClick={handleSave}
                      loading={saving}
                    >
                      Save HC Plan
                    </Button>
                  )}
                </>
              )}
            </Space>
          ),
        },
      ]}
    />
  );
}

function ClientRevenuePlanPanel({
  plan,
  forecastType,
  version,
  canEdit,
}: PanelProps) {
  const [data, setData] = useState<ClientRevenuePlanEntry[]>([]);
  const [customers, setCustomers] = useState<CustomerSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  usePlanSectionDraft(
    'revenuePlan',
    plan.id,
    forecastType.id,
    version.id,
    data,
    dirty,
    setData,
    setDirty,
  );

  const cols = useMemo(() => buildFyMonthCols(plan), [plan]);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [revData, custData] = await Promise.all([
        fetchRevenuePlan(plan.id, forecastType.id, version.id),
        fetchCustomers(false),
      ]);
      setData(revData);
      setCustomers(custData);
    } catch (error) {
      notification.error({
        message: 'Failed to load revenue plan',
        description: String(error),
      });
    } finally {
      setLoading(false);
    }
  }, [plan.id, forecastType.id, version.id]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const getValue = useCallback(
    (
      customerId: string,
      col: FyMonthCol,
      field: 'plannedTmRevenue' | 'plannedFixedBidRevenue',
    ): number => {
      const entry = data.find(
        (e) =>
          e.customerId === customerId &&
          e.planMonth === col.planMonth &&
          e.planYear === col.planYear,
      );
      return entry ? num(entry[field]) : 0;
    },
    [data],
  );

  const setValue = useCallback(
    (
      customerId: string,
      col: FyMonthCol,
      field: 'plannedTmRevenue' | 'plannedFixedBidRevenue',
      value: number,
    ) => {
      setDirty(true);
      setData((prev) => {
        const existing = prev.find(
          (e) =>
            e.customerId === customerId &&
            e.planMonth === col.planMonth &&
            e.planYear === col.planYear,
        );
        if (existing) {
          return prev.map((e) =>
            e.customerId === customerId &&
            e.planMonth === col.planMonth &&
            e.planYear === col.planYear
              ? { ...e, [field]: value }
              : e,
          );
        } else {
          return [
            ...prev,
            {
              customerId,
              planMonth: col.planMonth,
              planYear: col.planYear,
              plannedTmRevenue: field === 'plannedTmRevenue' ? value : 0,
              plannedFixedBidRevenue:
                field === 'plannedFixedBidRevenue' ? value : 0,
            },
          ];
        }
      });
    },
    [],
  );

  const handleSave = useCallback(async () => {
    setSaving(true);
    try {
      const entries: ClientRevenuePlanEntry[] = [];
      customers.forEach((cust) => {
        cols.forEach((col) => {
          const tm = getValue(cust.id, col, 'plannedTmRevenue');
          const fb = getValue(cust.id, col, 'plannedFixedBidRevenue');
          if (tm !== 0 || fb !== 0) {
            entries.push({
              customerId: cust.id,
              planMonth: col.planMonth,
              planYear: col.planYear,
              plannedTmRevenue: tm,
              plannedFixedBidRevenue: fb,
            });
          }
        });
      });
      await saveRevenuePlan(plan.id, forecastType.id, version.id, entries);
      notification.success({ message: 'Revenue plan saved' });
      setDirty(false);
      await loadData();
    } catch (error) {
      notification.error({
        message: 'Failed to save revenue plan',
        description: String(error),
      });
    } finally {
      setSaving(false);
    }
  }, [
    plan.id,
    forecastType.id,
    version.id,
    customers,
    cols,
    getValue,
    loadData,
  ]);

  const columns = [
    {
      title: 'Metric',
      dataIndex: 'metric',
      key: 'metric',
      fixed: 'left' as const,
      width: 180,
    },
    ...cols.map((col) => ({
      title: `${col.label} (Rs L)`,
      dataIndex: col.key,
      key: col.key,
      width: 120,
      align: 'right' as const,
    })),
  ];

  return (
    <Collapse
      items={[
        {
          key: 'revenue',
          label: (
            <Title level={4} style={{ fontFamily: HEADING_FONT, margin: 0 }}>
              Client Revenue Plan
            </Title>
          ),
          children: (
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              {loading ? (
                <Skeleton active />
              ) : (
                <>
                  <PlanInputExcelToolbar
                    canEdit={canEdit}
                    exportFn={() =>
                      exportRevenuePlan(plan.id, forecastType.id, version.id)
                    }
                    downloadSampleFn={() =>
                      downloadRevenuePlanImportSample(
                        plan.id,
                        forecastType.id,
                        version.id,
                      )
                    }
                    importFn={(file) =>
                      importRevenuePlan(
                        plan.id,
                        forecastType.id,
                        version.id,
                        file,
                      )
                    }
                    onImported={loadData}
                    importTitle="Import Client Revenue Plan"
                  />
                  {customers.map((cust) => {
                    const rows = [
                      {
                        key: 'tm',
                        metric: 'Planned T&M Revenue (Rs L)',
                        field: 'plannedTmRevenue' as const,
                      },
                      {
                        key: 'fb',
                        metric: 'Planned Fixed-Bid',
                        field: 'plannedFixedBidRevenue' as const,
                      },
                      {
                        key: 'total',
                        metric: 'Planned Total (read-only)',
                        field: null,
                      },
                    ];

                    const dataSource = rows.map((row) => {
                      const record: Record<string, any> = {
                        key: row.key,
                        metric: row.metric,
                      };
                      cols.forEach((col) => {
                        if (row.field === null) {
                          const tm = getValue(cust.id, col, 'plannedTmRevenue');
                          const fb = getValue(
                            cust.id,
                            col,
                            'plannedFixedBidRevenue',
                          );
                          record[col.key] = formatCurrency(tm + fb);
                        } else {
                          record[col.key] = (
                            <InputNumber
                              size="small"
                              min={0}
                              precision={3}
                              step={0.001}
                              disabled={!canEdit}
                              value={getValue(cust.id, col, row.field)}
                              onChange={(v) =>
                                setValue(cust.id, col, row.field!, v ?? 0)
                              }
                              style={{ width: '100%' }}
                            />
                          );
                        }
                      });
                      return record;
                    });

                    return (
                      <Card
                        key={cust.id}
                        title={`${cust.customerCode} — ${cust.customerName}`}
                        size="small"
                      >
                        <Table
                          dataSource={dataSource}
                          columns={columns}
                          pagination={false}
                          scroll={{ x: true }}
                          size="small"
                        />
                      </Card>
                    );
                  })}
                  {canEdit && (
                    <Button
                      type="primary"
                      onClick={handleSave}
                      loading={saving}
                    >
                      Save Revenue Plan
                    </Button>
                  )}
                </>
              )}
            </Space>
          ),
        },
      ]}
    />
  );
}

function SalaryBudgetPanel({
  plan,
  forecastType,
  version,
  canEdit,
}: PanelProps) {
  const [data, setData] = useState<SalaryBudgetMonth[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  usePlanSectionDraft(
    'salaryBudget',
    plan.id,
    forecastType.id,
    version.id,
    data,
    dirty,
    setData,
    setDirty,
  );

  const cols = useMemo(() => buildFyMonthCols(plan), [plan]);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await fetchSalaryBudget(
        plan.id,
        forecastType.id,
        version.id,
      );
      setData(result);
    } catch (error) {
      notification.error({
        message: 'Failed to load salary budget',
        description: String(error),
      });
    } finally {
      setLoading(false);
    }
  }, [plan.id, forecastType.id, version.id]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const monthMap = useMemo(() => {
    const map = new Map<string, SalaryBudgetMonth>();
    data.forEach((m) => {
      map.set(`${m.planYear}-${m.planMonth}`, m);
    });
    return map;
  }, [data]);

  const getValue = useCallback(
    (col: FyMonthCol, field: keyof SalaryBudgetMonth): number => {
      const m = monthMap.get(col.key);
      return m ? num(m[field]) : 0;
    },
    [monthMap],
  );

  const setValue = useCallback(
    (col: FyMonthCol, field: keyof SalaryBudgetMonth, value: number) => {
      setDirty(true);
      setData((prev) => {
        const existing = prev.find(
          (m) => m.planMonth === col.planMonth && m.planYear === col.planYear,
        );
        if (existing) {
          return prev.map((m) =>
            m.planMonth === col.planMonth && m.planYear === col.planYear
              ? { ...m, [field]: value }
              : m,
          );
        } else {
          return [
            ...prev,
            {
              planMonth: col.planMonth,
              planYear: col.planYear,
              billableSalaries: 0,
              benchSalaries: 0,
              supportSalaries: 0,
              cofoundersSalaries: 0,
              seniorMgmtSalaries: 0,
              [field]: value,
            },
          ];
        }
      });
    },
    [],
  );

  const handleSave = useCallback(async () => {
    setSaving(true);
    try {
      const months = cols.map((col) => {
        const existing = data.find(
          (m) => m.planMonth === col.planMonth && m.planYear === col.planYear,
        );
        return (
          existing ?? {
            planMonth: col.planMonth,
            planYear: col.planYear,
            billableSalaries: 0,
            benchSalaries: 0,
            supportSalaries: 0,
            cofoundersSalaries: 0,
            seniorMgmtSalaries: 0,
          }
        );
      });
      await saveSalaryBudget(plan.id, forecastType.id, version.id, months);
      notification.success({ message: 'Salary budget saved' });
      setDirty(false);
      await loadData();
    } catch (error) {
      notification.error({
        message: 'Failed to save salary budget',
        description: String(error),
      });
    } finally {
      setSaving(false);
    }
  }, [plan.id, forecastType.id, version.id, cols, data, loadData]);

  const columns = [
    {
      title: 'Category',
      dataIndex: 'category',
      key: 'category',
      fixed: 'left' as const,
      width: 180,
    },
    ...cols.map((col) => ({
      title: `${col.label} (Rs L)`,
      dataIndex: col.key,
      key: col.key,
      width: 120,
      align: 'right' as const,
    })),
  ];

  const rows = [
    {
      key: 'billable',
      category: 'Billable',
      field: 'billableSalaries' as const,
    },
    { key: 'bench', category: 'Bench', field: 'benchSalaries' as const },
    { key: 'support', category: 'Support', field: 'supportSalaries' as const },
    {
      key: 'cofounders',
      category: 'Co-Founders',
      field: 'cofoundersSalaries' as const,
    },
    {
      key: 'senior',
      category: 'Senior Mgmt',
      field: 'seniorMgmtSalaries' as const,
    },
    { key: 'total', category: 'Total', field: null },
  ];

  const dataSource = rows.map((row) => {
    const record: Record<string, any> = {
      key: row.key,
      category: row.category,
    };
    cols.forEach((col) => {
      if (row.field === null) {
        const total =
          getValue(col, 'billableSalaries') +
          getValue(col, 'benchSalaries') +
          getValue(col, 'supportSalaries') +
          getValue(col, 'cofoundersSalaries') +
          getValue(col, 'seniorMgmtSalaries');
        record[col.key] = formatCurrency(total);
      } else {
        record[col.key] = (
          <InputNumber
            size="small"
            min={0}
            precision={3}
            step={0.001}
            disabled={!canEdit}
            value={getValue(col, row.field)}
            onChange={(v) => setValue(col, row.field!, v ?? 0)}
            style={{ width: '100%' }}
          />
        );
      }
    });
    return record;
  });

  return (
    <Collapse
      items={[
        {
          key: 'salary',
          label: (
            <Title level={4} style={{ fontFamily: HEADING_FONT, margin: 0 }}>
              Salary Budget
            </Title>
          ),
          children: (
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              {loading ? (
                <Skeleton active />
              ) : (
                <>
                  <PlanInputExcelToolbar
                    canEdit={canEdit}
                    exportFn={() =>
                      exportSalaryBudget(plan.id, forecastType.id, version.id)
                    }
                    downloadSampleFn={() =>
                      downloadSalaryBudgetImportSample(
                        plan.id,
                        forecastType.id,
                        version.id,
                      )
                    }
                    importFn={(file) =>
                      importSalaryBudget(
                        plan.id,
                        forecastType.id,
                        version.id,
                        file,
                      )
                    }
                    onImported={loadData}
                    importTitle="Import Salary Budget"
                  />
                  <Table
                    dataSource={dataSource}
                    columns={columns}
                    pagination={false}
                    scroll={{ x: true }}
                    size="small"
                  />
                  {canEdit && (
                    <Button
                      type="primary"
                      onClick={handleSave}
                      loading={saving}
                    >
                      Save Salary Budget
                    </Button>
                  )}
                </>
              )}
            </Space>
          ),
        },
      ]}
    />
  );
}

function OverheadBudgetPanel({
  plan,
  forecastType,
  version,
  canEdit,
}: PanelProps) {
  const [data, setData] = useState<OverheadBudgetEntry[]>([]);
  const [lineItems, setLineItems] = useState<OverheadLineItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);
  usePlanSectionDraft(
    'overheadBudget',
    plan.id,
    forecastType.id,
    version.id,
    data,
    dirty,
    setData,
    setDirty,
  );

  const cols = useMemo(() => buildFyMonthCols(plan), [plan]);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [budgetData, linesData] = await Promise.all([
        fetchOverheadBudget(plan.id, forecastType.id, version.id),
        fetchOverheadLineItems(),
      ]);
      setData(budgetData);
      setLineItems(linesData);
    } catch (error) {
      notification.error({
        message: 'Failed to load overhead budget',
        description: String(error),
      });
    } finally {
      setLoading(false);
    }
  }, [plan.id, forecastType.id, version.id]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const categoriesMap = useMemo(() => {
    const map = new Map<string, OverheadLineItem[]>();
    lineItems.forEach((line) => {
      const existing = map.get(line.category) ?? [];
      existing.push(line);
      map.set(line.category, existing);
    });
    // Sort within each category
    map.forEach((lines) => lines.sort((a, b) => a.sortOrder - b.sortOrder));
    return map;
  }, [lineItems]);

  const getValue = useCallback(
    (lineCode: string, col: FyMonthCol): number => {
      const entry = data.find(
        (e) =>
          e.overheadLine === lineCode &&
          e.planMonth === col.planMonth &&
          e.planYear === col.planYear,
      );
      return entry ? num(entry.amount) : 0;
    },
    [data],
  );

  const setValue = useCallback(
    (lineCode: string, col: FyMonthCol, value: number) => {
      setDirty(true);
      setData((prev) => {
        const existing = prev.find(
          (e) =>
            e.overheadLine === lineCode &&
            e.planMonth === col.planMonth &&
            e.planYear === col.planYear,
        );
        if (existing) {
          return prev.map((e) =>
            e.overheadLine === lineCode &&
            e.planMonth === col.planMonth &&
            e.planYear === col.planYear
              ? { ...e, amount: value }
              : e,
          );
        } else {
          return [
            ...prev,
            {
              planMonth: col.planMonth,
              planYear: col.planYear,
              overheadLine: lineCode,
              amount: value,
            },
          ];
        }
      });
    },
    [],
  );

  const handleSave = useCallback(async () => {
    setSaving(true);
    try {
      const entries: OverheadBudgetEntry[] = [];
      lineItems.forEach((line) => {
        cols.forEach((col) => {
          const amt = getValue(line.lineCode, col);
          if (amt !== 0) {
            entries.push({
              planMonth: col.planMonth,
              planYear: col.planYear,
              overheadLine: line.lineCode,
              amount: amt,
            });
          }
        });
      });
      await saveOverheadBudget(plan.id, forecastType.id, version.id, entries);
      notification.success({ message: 'Overhead budget saved' });
      setDirty(false);
      await loadData();
    } catch (error) {
      notification.error({
        message: 'Failed to save overhead budget',
        description: String(error),
      });
    } finally {
      setSaving(false);
    }
  }, [
    plan.id,
    forecastType.id,
    version.id,
    lineItems,
    cols,
    getValue,
    loadData,
  ]);

  const columns = [
    {
      title: 'Line Item',
      dataIndex: 'lineItem',
      key: 'lineItem',
      fixed: 'left' as const,
      width: 200,
    },
    ...cols.map((col) => ({
      title: `${col.label} (Rs L)`,
      dataIndex: col.key,
      key: col.key,
      width: 120,
      align: 'right' as const,
    })),
  ];

  return (
    <Collapse
      items={[
        {
          key: 'overhead',
          label: (
            <Title level={4} style={{ fontFamily: HEADING_FONT, margin: 0 }}>
              Overhead Budget
            </Title>
          ),
          children: (
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              {loading ? (
                <Skeleton active />
              ) : (
                <>
                  <PlanInputExcelToolbar
                    canEdit={canEdit}
                    exportFn={() =>
                      exportOverheadBudget(plan.id, forecastType.id, version.id)
                    }
                    downloadSampleFn={() =>
                      downloadOverheadBudgetImportSample(
                        plan.id,
                        forecastType.id,
                        version.id,
                      )
                    }
                    importFn={(file) =>
                      importOverheadBudget(
                        plan.id,
                        forecastType.id,
                        version.id,
                        file,
                      )
                    }
                    onImported={loadData}
                    importTitle="Import Overhead Budget"
                  />
                  {Array.from(categoriesMap.entries()).map(
                    ([category, lines]) => {
                      const dataSource = lines.map((line) => {
                        const record: Record<string, any> = {
                          key: line.lineCode,
                          lineItem: line.displayName,
                        };
                        cols.forEach((col) => {
                          record[col.key] = (
                            <InputNumber
                              size="small"
                              min={0}
                              precision={3}
                              step={0.001}
                              disabled={!canEdit}
                              value={getValue(line.lineCode, col)}
                              onChange={(v) =>
                                setValue(line.lineCode, col, v ?? 0)
                              }
                              style={{ width: '100%' }}
                            />
                          );
                        });
                        return record;
                      });

                      // Subtotal row
                      const subtotalRecord: Record<string, any> = {
                        key: `${category}-subtotal`,
                        lineItem: <strong>{category} Subtotal</strong>,
                      };
                      cols.forEach((col) => {
                        const total = lines.reduce(
                          (sum, line) => sum + getValue(line.lineCode, col),
                          0,
                        );
                        subtotalRecord[col.key] = (
                          <strong>{formatCurrency(total)}</strong>
                        );
                      });

                      return (
                        <Collapse
                          key={category}
                          items={[
                            {
                              key: category,
                              label: category,
                              children: (
                                <Table
                                  dataSource={[...dataSource, subtotalRecord]}
                                  columns={columns}
                                  pagination={false}
                                  scroll={{ x: true }}
                                  size="small"
                                />
                              ),
                            },
                          ]}
                        />
                      );
                    },
                  )}

                  {/* Grand total */}
                  <Card size="small">
                    <Space direction="vertical" style={{ width: '100%' }}>
                      <Text strong>Grand Total</Text>
                      <div
                        style={{
                          display: 'flex',
                          gap: 16,
                          flexWrap: 'wrap',
                        }}
                      >
                        {cols.map((col) => {
                          const total = lineItems.reduce(
                            (sum, line) => sum + getValue(line.lineCode, col),
                            0,
                          );
                          return (
                            <div key={col.key}>
                              <Text type="secondary">{col.label}:</Text>{' '}
                              <Text strong>{formatCurrency(total)}</Text>
                            </div>
                          );
                        })}
                      </div>
                    </Space>
                  </Card>

                  {canEdit && (
                    <Button
                      type="primary"
                      onClick={handleSave}
                      loading={saving}
                    >
                      Save Overhead Budget
                    </Button>
                  )}
                </>
              )}
            </Space>
          ),
        },
      ]}
    />
  );
}
