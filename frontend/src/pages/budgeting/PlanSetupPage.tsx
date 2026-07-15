import { useCallback, useEffect, useMemo, useState } from 'react';
import {
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
  Table,
  Tabs,
  Tag,
  theme,
  Typography,
} from 'antd';
import { formatCurrency } from '@/utils/formatDate';
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
  fetchHcPlan,
  fetchOverheadBudget,
  fetchOverheadLineItems,
  fetchPlan,
  fetchPlans,
  fetchRevenuePlan,
  fetchSalaryBudget,
  publishVersion,
  saveHcPlan,
  saveOverheadBudget,
  saveRevenuePlan,
  saveSalaryBudget,
} from './api';

const { Title, Text } = Typography;

export default function PlanSetupPage() {
  const { token } = theme.useToken();
  const { formatDate } = useDateFormat();
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

  const fyDates = useMemo(() => {
    const fy = createForm.getFieldValue('fiscalYear');
    if (!fy) return null;
    return parseFiscalYearDates(fy);
  }, [createForm.getFieldValue('fiscalYear')]);

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
            <Button
              type="primary"
              onClick={() => setCreateModalOpen(true)}
            >
              Create First Plan
            </Button>
          </Space>
        </Card>

        <Modal
          title="New Financial Year Plan"
          open={createModalOpen}
          onOk={handleCreatePlan}
          onCancel={() => {
            setCreateModalOpen(false);
            createForm.resetFields();
          }}
        >
          <Form form={createForm} layout="vertical">
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
              <Input placeholder="FY2627" />
            </Form.Item>
            <Form.Item
              name="openingHc"
              label="Opening HC"
              rules={[{ required: true, message: 'Required' }]}
            >
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="Fiscal Year Start">
              <Input value={fyDates?.start ?? ''} disabled />
            </Form.Item>
            <Form.Item label="Fiscal Year End">
              <Input value={fyDates?.end ?? ''} disabled />
            </Form.Item>
          </Form>
        </Modal>
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
            <Button type="primary" onClick={() => setCreateModalOpen(true)}>
              New Financial Year
            </Button>
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
                  onReload={() => loadPlan(plan.id)}
                />
              ),
            }))}
          />
        )}
      </Space>

      <Modal
        title="New Financial Year Plan"
        open={createModalOpen}
        onOk={handleCreatePlan}
        onCancel={() => {
          setCreateModalOpen(false);
          createForm.resetFields();
        }}
      >
        <Form form={createForm} layout="vertical">
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
            <Input placeholder="FY2627" />
          </Form.Item>
          <Form.Item
            name="openingHc"
            label="Opening HC"
            rules={[{ required: true, message: 'Required' }]}
          >
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="Fiscal Year Start">
            <Input value={fyDates?.start ?? ''} disabled />
          </Form.Item>
          <Form.Item label="Fiscal Year End">
            <Input value={fyDates?.end ?? ''} disabled />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

interface ForecastTypePanelProps {
  plan: PlanDetail;
  forecastType: ForecastType;
  token: ReturnType<typeof theme.useToken>['token'];
  formatDate: (date: string | Date | null | undefined) => string;
  onReload: () => void;
}

function ForecastTypePanel({
  plan,
  forecastType,
  token,
  formatDate,
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

  const isEditable = currentVersion.status === 'DRAFT';

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card>
        <Space>
          <Text strong>Version {currentVersion.versionNumber}</Text>
          <Tag color={STATUS_COLOR[currentVersion.status]}>
            {currentVersion.status}
          </Tag>
          {currentVersion.status === 'DRAFT' && (
            <Button type="primary" onClick={handlePublish}>
              Publish
            </Button>
          )}
          {currentVersion.status === 'ACTIVE' &&
            !forecastType.versions.some((v) => v.status === 'DRAFT') && (
              <Button onClick={handleCreateRevision}>Create Revision</Button>
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

      <HcPlanPanel
        plan={plan}
        forecastType={forecastType}
        version={currentVersion}
        isEditable={isEditable}
        token={token}
      />

      <ClientRevenuePlanPanel
        plan={plan}
        forecastType={forecastType}
        version={currentVersion}
        isEditable={isEditable}
        token={token}
      />

      <SalaryBudgetPanel
        plan={plan}
        forecastType={forecastType}
        version={currentVersion}
        isEditable={isEditable}
        token={token}
      />

      <OverheadBudgetPanel
        plan={plan}
        forecastType={forecastType}
        version={currentVersion}
        isEditable={isEditable}
        token={token}
      />
    </Space>
  );
}

interface PanelProps {
  plan: PlanDetail;
  forecastType: ForecastType;
  version: ForecastVersion;
  isEditable: boolean;
  token: ReturnType<typeof theme.useToken>['token'];
}

function HcPlanPanel({
  plan,
  forecastType,
  version,
  isEditable,
}: PanelProps) {
  const [data, setData] = useState<HcPlanMonth[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

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
      } else if (isEditable) {
        record[col.key] = (
          <InputNumber
            size="small"
            min={0}
            value={getValue(col, row.field)}
            onChange={(v) => setValue(col, row.field!, v ?? 0)}
            style={{ width: '100%' }}
          />
        );
      } else {
        record[col.key] = getValue(col, row.field);
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
                  <Table
                    dataSource={dataSource}
                    columns={columns}
                    pagination={false}
                    scroll={{ x: true }}
                    size="small"
                  />
                  {isEditable && (
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
  isEditable,
}: PanelProps) {
  const [data, setData] = useState<ClientRevenuePlanEntry[]>([]);
  const [customers, setCustomers] = useState<CustomerSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

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
                        } else if (isEditable) {
                          record[col.key] = (
                            <InputNumber
                              size="small"
                              min={0}
                              value={getValue(cust.id, col, row.field)}
                              onChange={(v) =>
                                setValue(cust.id, col, row.field!, v ?? 0)
                              }
                              style={{ width: '100%' }}
                            />
                          );
                        } else {
                          record[col.key] = formatCurrency(
                            getValue(cust.id, col, row.field),
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
                  {isEditable && (
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
  isEditable,
}: PanelProps) {
  const [data, setData] = useState<SalaryBudgetMonth[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

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
      } else if (isEditable) {
        record[col.key] = (
          <InputNumber
            size="small"
            min={0}
            value={getValue(col, row.field)}
            onChange={(v) => setValue(col, row.field!, v ?? 0)}
            style={{ width: '100%' }}
          />
        );
      } else {
        record[col.key] = formatCurrency(getValue(col, row.field));
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
                  <Table
                    dataSource={dataSource}
                    columns={columns}
                    pagination={false}
                    scroll={{ x: true }}
                    size="small"
                  />
                  {isEditable && (
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
  isEditable,
}: PanelProps) {
  const [data, setData] = useState<OverheadBudgetEntry[]>([]);
  const [lineItems, setLineItems] = useState<OverheadLineItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

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
                  {Array.from(categoriesMap.entries()).map(
                    ([category, lines]) => {
                      const dataSource = lines.map((line) => {
                        const record: Record<string, any> = {
                          key: line.lineCode,
                          lineItem: line.displayName,
                        };
                        cols.forEach((col) => {
                          if (isEditable) {
                            record[col.key] = (
                              <InputNumber
                                size="small"
                                min={0}
                                value={getValue(line.lineCode, col)}
                                onChange={(v) =>
                                  setValue(line.lineCode, col, v ?? 0)
                                }
                                style={{ width: '100%' }}
                              />
                            );
                          } else {
                            record[col.key] = formatCurrency(
                              getValue(line.lineCode, col),
                            );
                          }
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

                  {isEditable && (
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
