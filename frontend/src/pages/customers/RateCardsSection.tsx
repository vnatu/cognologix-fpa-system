import { useEffect, useState, useCallback, useMemo } from 'react';
import {
  Button,
  Collapse,
  DatePicker,
  Divider,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  notification,
  Select,
  Skeleton,
  Space,
  Table,
  Tag,
  Typography,
  Checkbox,
} from 'antd';
import {
  PlusOutlined,
  MinusCircleOutlined,
  DownloadOutlined,
  UploadOutlined,
  ExportOutlined,
  EditOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useDateFormat } from '@/context/DateFormatContext';
import { HEADING_FONT } from '@/theme/antdTheme';
import {
  fetchCustomers,
  fetchRateCards,
  fetchProjectCodes,
  createRateCard,
  updateRateCard,
  downloadRateCardImportSample,
  exportRateCards,
} from './api';
import RateCardImportModal from './RateCardImportModal';
import type {
  CustomerSummary,
  ProjectCode,
  RateCard,
  RateCardLine,
  RateCardType,
  RateCurrency,
} from './types';

const { Text } = Typography;

const RATE_CARD_TYPE_OPTIONS = [
  { label: 'Flat — single blended rate', value: 'FLAT' },
  { label: 'Tiered — by Job Level', value: 'TIERED' },
];

const CURRENCY_OPTIONS = [
  { label: '₹ INR', value: 'INR' },
  { label: '$ USD', value: 'USD' },
];

interface LineFormValue {
  jobLevel?: string;
  rateAmount: number;
}

interface CreateFormValues {
  name: string;
  rateCardType: RateCardType;
  currency: RateCurrency;
  effectiveFrom: dayjs.Dayjs;
  /** Empty = blended (customer-level). */
  projectCodeIds?: string[];
  lines: LineFormValue[];
}

interface EditFormValues {
  name: string;
  rateCardType: RateCardType;
  currency: RateCurrency;
  /** Empty = blended (customer-level). */
  projectCodeIds?: string[];
  effectiveFrom: dayjs.Dayjs;
  effectiveTo: dayjs.Dayjs;
  lines: LineFormValue[];
}

interface Props {
  selectedCustomerId: string | null;
  onSelectCustomer: (id: string) => void;
}

function formatRate(amount: number, currency: RateCurrency) {
  const sym = currency === 'INR' ? '₹' : '$';
  return `${sym}${Number(amount).toLocaleString()}`;
}

export default function RateCardsSection({
  selectedCustomerId,
  onSelectCustomer,
}: Props) {
  const [customers, setCustomers] = useState<CustomerSummary[]>([]);
  const [showInternal, setShowInternal] = useState(false);
  const [customersLoading, setCustomersLoading] = useState(true);
  const [rateCards, setRateCards] = useState<RateCard[]>([]);
  const [projectCodes, setProjectCodes] = useState<ProjectCode[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editCard, setEditCard] = useState<RateCard | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<CreateFormValues>();
  const [editForm] = Form.useForm<EditFormValues>();
  const rateCardType = Form.useWatch('rateCardType', form);
  const editRateCardType = Form.useWatch('rateCardType', editForm);
  const createProjectCodeIds = Form.useWatch('projectCodeIds', form) ?? [];

  useEffect(() => {
    setCustomersLoading(true);
    fetchCustomers(showInternal)
      .then(setCustomers)
      .catch(() => notification.error({ message: 'Failed to load customers' }))
      .finally(() => setCustomersLoading(false));
  }, [showInternal]);

  const selectedCustomer = customers.find((c) => c.id === selectedCustomerId);
  const isInternalSelected = selectedCustomer?.internal === true;

  const loadRateCards = useCallback(async (customerId: string) => {
    setLoading(true);
    try {
      const [cards, codes] = await Promise.all([
        fetchRateCards(customerId),
        fetchProjectCodes(customerId),
      ]);
      setRateCards(cards);
      setProjectCodes(codes);
    } catch {
      notification.error({ message: 'Failed to load rate cards' });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selectedCustomerId) loadRateCards(selectedCustomerId);
    else {
      setRateCards([]);
      setProjectCodes([]);
    }
  }, [selectedCustomerId, loadRateCards]);

  const projectCodeOptions = useMemo(
    () =>
      projectCodes.map((pc) => ({
        label: pc.description
          ? `${pc.projectCode} — ${pc.description}`
          : pc.projectCode,
        value: pc.id,
      })),
    [projectCodes],
  );

  const activeCards = useMemo(
    () =>
      rateCards
        .filter((c) => !c.effectiveTo)
        .sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom)),
    [rateCards],
  );

  const historyCards = useMemo(
    () =>
      rateCards
        .filter((c) => !!c.effectiveTo)
        .sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom)),
    [rateCards],
  );

  const openModal = () => {
    form.resetFields();
    form.setFieldsValue({
      rateCardType: 'FLAT',
      currency: 'INR',
      lines: [{}],
      projectCodeIds: [],
    });
    setModalOpen(true);
  };

  const openEditModal = (card: RateCard) => {
    setEditCard(card);
    editForm.resetFields();
    editForm.setFieldsValue({
      name: card.name,
      rateCardType: card.rateCardType,
      currency: card.currency,
      projectCodeIds: (card.projectCodes ?? []).map((p) => p.id),
      lines:
        card.lines.length > 0
          ? card.lines.map((l) => ({
              jobLevel: l.jobLevel,
              rateAmount: l.rateAmount,
            }))
          : [{}],
    });
  };

  const handleSave = async () => {
    let values: CreateFormValues;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    if (!selectedCustomerId) return;
    setSaving(true);
    try {
      await createRateCard(selectedCustomerId, {
        name: values.name,
        rateCardType: values.rateCardType,
        currency: values.currency,
        effectiveFrom: values.effectiveFrom.format('YYYY-MM-DD'),
        projectCodeIds: values.projectCodeIds ?? [],
        lines: values.lines.map((l) => ({
          jobLevel: values.rateCardType === 'TIERED' ? l.jobLevel : undefined,
          rateAmount: l.rateAmount,
        })),
      });
      notification.success({ message: 'Rate card created' });
      setModalOpen(false);
      loadRateCards(selectedCustomerId);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Save failed';
      notification.error({ message: 'Error', description: msg });
    } finally {
      setSaving(false);
    }
  };

  const handleEditSave = async () => {
    let values: EditFormValues;
    try {
      values = await editForm.validateFields();
    } catch {
      return;
    }
    if (!selectedCustomerId || !editCard) return;
    setSaving(true);
    try {
      await updateRateCard(selectedCustomerId, editCard.id, {
        name: values.name,
        rateCardType: values.rateCardType,
        currency: values.currency,
        effectiveTo: values.effectiveTo.format('YYYY-MM-DD'),
        effectiveFrom: values.effectiveFrom.format('YYYY-MM-DD'),
        projectCodeIds: values.projectCodeIds ?? [],
        lines: values.lines.map((l) => ({
          jobLevel: values.rateCardType === 'TIERED' ? l.jobLevel : undefined,
          rateAmount: l.rateAmount,
        })),
      });
      notification.success({
        message: 'Rate card updated',
        description: 'Previous version closed; new version created.',
      });
      setEditCard(null);
      loadRateCards(selectedCustomerId);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Save failed';
      notification.error({ message: 'Error', description: msg });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <h3
          style={{
            fontFamily: HEADING_FONT,
            fontWeight: 700,
            fontSize: 16,
            margin: 0,
          }}
        >
          Rate Cards
        </h3>
        <Space>
          <Button
            icon={<ExportOutlined />}
            onClick={() => {
              exportRateCards().catch(() =>
                notification.error({ message: 'Failed to export rate cards' }),
              );
            }}
          >
            Export Rate Cards
          </Button>
          <Button
            icon={<DownloadOutlined />}
            onClick={() => {
              downloadRateCardImportSample().catch(() =>
                notification.error({ message: 'Failed to download sample file' }),
              );
            }}
          >
            Download Sample File
          </Button>
          <Button icon={<UploadOutlined />} onClick={() => setImportOpen(true)}>
            Import Rate Cards
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={openModal}
            disabled={!selectedCustomerId || isInternalSelected}
          >
            New Rate Card
          </Button>
        </Space>
      </div>

      {customersLoading ? (
        <Skeleton active paragraph={{ rows: 1 }} />
      ) : (
        <>
          <Checkbox
            checked={showInternal}
            onChange={(e) => setShowInternal(e.target.checked)}
            style={{ marginBottom: 12 }}
          >
            Show internal BUs
          </Checkbox>
          <Form.Item label="Customer" style={{ marginBottom: 20, maxWidth: 360 }}>
            <Select
              showSearch
              placeholder="Select a customer"
              value={selectedCustomerId ?? undefined}
              onChange={onSelectCustomer}
              options={customers.map((c) => ({
                label: (
                  <span>
                    {c.customerCode} — {c.customerName}
                    {c.internal ? (
                      <Tag style={{ marginLeft: 8 }}>Internal</Tag>
                    ) : null}
                  </span>
                ),
                value: c.id,
              }))}
              filterOption={(input, option) => {
                const c = customers.find((x) => x.id === option?.value);
                if (!c) return false;
                return `${c.customerCode} ${c.customerName}`
                  .toLowerCase()
                  .includes(input.toLowerCase());
              }}
            />
          </Form.Item>
        </>
      )}

      {isInternalSelected ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="Rate cards are not managed for internal BUs in Phase 1"
        />
      ) : !selectedCustomerId ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="Select a customer to view their rate cards"
        />
      ) : loading ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : rateCards.length === 0 ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="No active rate card — create one below"
        />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {activeCards.map((card) => (
            <RateCardCard
              key={card.id}
              card={card}
              onEdit={() => openEditModal(card)}
            />
          ))}
          {historyCards.length > 0 && (
            <Collapse
              ghost
              style={{ marginTop: 8 }}
              items={[
                {
                  key: 'history',
                  label: (
                    <Text type="secondary">
                      Prior versions ({historyCards.length})
                    </Text>
                  ),
                  children: (
                    <div
                      style={{
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 12,
                      }}
                    >
                      {historyCards.map((rc) => (
                        <RateCardCard key={rc.id} card={rc} />
                      ))}
                    </div>
                  ),
                },
              ]}
            />
          )}
        </div>
      )}

      {/* New Rate Card Modal */}
      <Modal
        title={
          <span style={{ fontFamily: HEADING_FONT, fontWeight: 700 }}>
            New Rate Card
            {createProjectCodeIds.length > 0 ? (
              <Tag color="blue" style={{ marginLeft: 10, fontWeight: 500 }}>
                Project-scoped
              </Tag>
            ) : (
              <Tag style={{ marginLeft: 10, fontWeight: 500 }}>Blended</Tag>
            )}
          </span>
        }
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSave}
        okText="Create Rate Card"
        confirmLoading={saving}
        width={580}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Space style={{ width: '100%', display: 'flex' }} size={12}>
            <Form.Item
              name="name"
              label="Rate Card Name"
              rules={[{ required: true, message: 'Name is required' }]}
              style={{ flex: 2 }}
            >
              <Input placeholder="e.g. FY2526 Standard" maxLength={255} />
            </Form.Item>
            <Form.Item
              name="effectiveFrom"
              label="Effective From"
              rules={[{ required: true, message: 'Date is required' }]}
              style={{ flex: 1 }}
            >
              <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
            </Form.Item>
          </Space>

          <Form.Item
            name="projectCodeIds"
            label="Project Codes (leave empty for blended rate)"
            initialValue={[]}
          >
            <Select
              mode="multiple"
              allowClear
              options={projectCodeOptions}
              placeholder="Leave empty for blended rate"
              optionFilterProp="label"
            />
          </Form.Item>

          <Space style={{ width: '100%', display: 'flex' }} size={12}>
            <Form.Item
              name="rateCardType"
              label="Rate Card Type"
              rules={[{ required: true }]}
              style={{ flex: 1 }}
            >
              <Select
                options={RATE_CARD_TYPE_OPTIONS}
                onChange={() => form.setFieldValue('lines', [{}])}
              />
            </Form.Item>
            <Form.Item
              name="currency"
              label="Currency"
              rules={[{ required: true }]}
              style={{ flex: 1 }}
            >
              <Select options={CURRENCY_OPTIONS} />
            </Form.Item>
          </Space>

          <Divider plain style={{ marginTop: 4 }}>
            <Text type="secondary" style={{ fontSize: 13 }}>
              {rateCardType === 'FLAT' ? 'Blended Rate' : 'Rates by Job Level'}
            </Text>
          </Divider>

          <RateLinesFields rateCardType={rateCardType} />
        </Form>
      </Modal>

      {/* Edit Rate Card Modal */}
      <Modal
        title={
          <span style={{ fontFamily: HEADING_FONT, fontWeight: 700 }}>
            Edit Rate Card
          </span>
        }
        open={!!editCard}
        onCancel={() => setEditCard(null)}
        onOk={handleEditSave}
        okText="Save New Version"
        confirmLoading={saving}
        width={580}
        destroyOnHidden
      >
        <Form form={editForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="name"
            label="Rate Card Name"
            rules={[{ required: true, message: 'Name is required' }]}
          >
            <Input maxLength={255} />
          </Form.Item>

          <Form.Item
            name="projectCodeIds"
            label="Project Codes (leave empty for blended rate)"
            initialValue={[]}
          >
            <Select
              mode="multiple"
              allowClear
              options={projectCodeOptions}
              placeholder="Leave empty for blended rate"
              optionFilterProp="label"
            />
          </Form.Item>

          <Space style={{ width: '100%', display: 'flex' }} size={12}>
            <Form.Item
              name="rateCardType"
              label="Rate Card Type"
              rules={[{ required: true }]}
              style={{ flex: 1 }}
            >
              <Select
                options={RATE_CARD_TYPE_OPTIONS}
                onChange={() => editForm.setFieldValue('lines', [{}])}
              />
            </Form.Item>
            <Form.Item
              name="currency"
              label="Currency"
              rules={[{ required: true }]}
              style={{ flex: 1 }}
            >
              <Select options={CURRENCY_OPTIONS} />
            </Form.Item>
          </Space>

          <Space style={{ width: '100%', display: 'flex' }} size={12}>
            <Form.Item
              name="effectiveTo"
              label="Effective To (close current)"
              rules={[{ required: true, message: 'Close date is required' }]}
              style={{ flex: 1 }}
              extra="Date the current version ends"
            >
              <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
            </Form.Item>
            <Form.Item
              name="effectiveFrom"
              label="Effective From (new version)"
              dependencies={['effectiveTo']}
              rules={[
                { required: true, message: 'Start date is required' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    const close = getFieldValue('effectiveTo');
                    if (!value || !close || value.isAfter(close)) {
                      return Promise.resolve();
                    }
                    return Promise.reject(
                      new Error('Must be after Effective To'),
                    );
                  },
                }),
              ]}
              style={{ flex: 1 }}
              extra="No default — Finance must specify"
            >
              <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
            </Form.Item>
          </Space>

          <Divider plain style={{ marginTop: 4 }}>
            <Text type="secondary" style={{ fontSize: 13 }}>
              {editRateCardType === 'FLAT'
                ? 'Blended Rate'
                : 'Rates by Job Level'}
            </Text>
          </Divider>

          <RateLinesFields rateCardType={editRateCardType} />
        </Form>
      </Modal>

      <RateCardImportModal
        open={importOpen}
        customers={customers}
        onClose={() => setImportOpen(false)}
        onImported={() => {
          if (selectedCustomerId) loadRateCards(selectedCustomerId);
        }}
      />
    </div>
  );
}

function RateLinesFields({ rateCardType }: { rateCardType?: RateCardType }) {
  if (rateCardType === 'FLAT') {
    return (
      <Form.Item
        label="Rate (per person / month)"
        name={['lines', 0, 'rateAmount']}
        rules={[{ required: true, message: 'Amount required' }]}
      >
        <InputNumber
          min={0.01}
          step={500}
          placeholder="150000"
          style={{ width: '100%' }}
          formatter={(v) => `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
          parser={(v) => Number(v!.replace(/,/g, '')) as never}
        />
      </Form.Item>
    );
  }

  return (
    <Form.List name="lines">
      {(fields, { add, remove }) => (
        <>
          {fields.map(({ key, name }) => (
            <Space
              key={key}
              align="baseline"
              style={{ display: 'flex', marginBottom: 8 }}
            >
              <Form.Item
                name={[name, 'jobLevel']}
                rules={[{ required: true, message: 'Job level required' }]}
                noStyle
              >
                <Input placeholder="e.g. L3" style={{ width: 140 }} maxLength={100} />
              </Form.Item>
              <Form.Item
                name={[name, 'rateAmount']}
                rules={[{ required: true, message: 'Amount required' }]}
                noStyle
              >
                <InputNumber
                  min={0.01}
                  step={500}
                  placeholder="Rate"
                  style={{ width: 160 }}
                  formatter={(v) => `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                  parser={(v) => Number(v!.replace(/,/g, '')) as never}
                />
              </Form.Item>
              {fields.length > 1 && (
                <MinusCircleOutlined
                  style={{ color: '#f05756' }}
                  onClick={() => remove(name)}
                />
              )}
            </Space>
          ))}
          <Button
            type="dashed"
            onClick={() => add({})}
            icon={<PlusOutlined />}
            style={{ width: '100%', marginTop: 4 }}
          >
            Add Job Level
          </Button>
        </>
      )}
    </Form.List>
  );
}

// ── Rate Card display card ────────────────────────────────────────────────────

interface RateCardCardProps {
  card: RateCard;
  onEdit?: () => void;
}

function RateCardCard({ card, onEdit }: RateCardCardProps) {
  const { formatDate } = useDateFormat();
  const isActive = !card.effectiveTo;
  const sortedLines = [...card.lines].sort((a, b) => a.rateAmount - b.rateAmount);
  const codes = card.projectCodes ?? [];

  const flatColumns: ColumnsType<RateCardLine> = [
    {
      title: 'Rate',
      dataIndex: 'rateAmount',
      key: 'rateAmount',
      render: (v: number) => (
        <Text strong>{formatRate(v, card.currency)}</Text>
      ),
    },
  ];

  const tieredColumns: ColumnsType<RateCardLine> = [
    {
      title: 'Job Level',
      dataIndex: 'jobLevel',
      key: 'jobLevel',
      render: (v?: string) => <Text code>{v ?? '—'}</Text>,
    },
    {
      title: 'Rate',
      dataIndex: 'rateAmount',
      key: 'rateAmount',
      render: (v: number) => (
        <Text strong>{formatRate(v, card.currency)}</Text>
      ),
    },
  ];

  return (
    <div
      style={{
        border: `1px solid ${isActive ? '#52c41a' : '#d9d9d9'}`,
        borderRadius: 8,
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          background: isActive ? '#f6ffed' : '#fafafa',
          padding: '10px 16px',
          borderBottom: '1px solid',
          borderColor: isActive ? '#b7eb8f' : '#f0f0f0',
        }}
      >
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            flexWrap: 'wrap',
          }}
        >
          {isActive && (
            <Tag color="success" style={{ fontWeight: 600, margin: 0 }}>
              Active
            </Tag>
          )}
          <Tag
            color={card.rateCardType === 'FLAT' ? 'geekblue' : 'purple'}
            style={{ margin: 0 }}
          >
            {card.rateCardType}
          </Tag>
          <Text strong style={{ fontSize: 14 }}>
            {card.name}
          </Text>
          <Tag style={{ margin: 0 }}>{card.currency}</Tag>
          <Text type="secondary" style={{ fontSize: 12, marginLeft: 'auto' }}>
            {formatDate(card.effectiveFrom)}
            {card.effectiveTo
              ? ` → ${formatDate(card.effectiveTo)}`
              : ' → present'}
          </Text>
          {isActive && onEdit && (
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={onEdit}
              style={{ paddingInline: 4 }}
            >
              Edit
            </Button>
          )}
        </div>
        <div style={{ marginTop: 8, display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          {codes.length > 0 ? (
            codes.map((pc) => (
              <Tag key={pc.id} color="blue" style={{ margin: 0 }}>
                {pc.projectCode}
              </Tag>
            ))
          ) : (
            <Tag style={{ margin: 0 }}>Blended</Tag>
          )}
        </div>
      </div>

      <Table
        dataSource={sortedLines}
        columns={card.rateCardType === 'FLAT' ? flatColumns : tieredColumns}
        rowKey="id"
        size="small"
        pagination={false}
        style={{ margin: 0 }}
      />
    </div>
  );
}
