import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Skeleton,
  Space,
  Table,
  Typography,
  notification,
} from 'antd';
import {
  DownloadOutlined,
  ExportOutlined,
  PlusOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import axios from 'axios';
import {
  createFxRate,
  downloadFxRateImportSample,
  exportFxRates,
  fetchFxRates,
  importFxRates,
  type FxRate,
} from '@/api/general';
import { AdminGate } from '@/components/AdminGate';
import SimpleExcelImportModal from '@/components/SimpleExcelImportModal';
import { useDateFormat } from '@/context/DateFormatContext';
import { HEADING_FONT } from '@/theme/antdTheme';

const { Text } = Typography;

interface AddFxRateForm {
  currencyPair: string;
  rate: number;
  effectiveFrom: Dayjs;
}

export default function FxRatesSection() {
  const { formatDate } = useDateFormat();
  const [rates, setRates] = useState<FxRate[]>([]);
  const [loading, setLoading] = useState(true);
  const [importOpen, setImportOpen] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<AddFxRateForm>();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRates(await fetchFxRates());
    } catch {
      notification.error({ message: 'Failed to load FX rates' });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const openAddModal = () => {
    form.setFieldsValue({
      currencyPair: 'USD_INR',
      rate: undefined,
      effectiveFrom: dayjs(),
    });
    setAddOpen(true);
  };

  const handleAdd = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      await createFxRate({
        currencyPair: values.currencyPair.trim(),
        rate: values.rate,
        effectiveFrom: values.effectiveFrom.format('YYYY-MM-DD'),
      });
      notification.success({ message: 'FX rate created' });
      setAddOpen(false);
      form.resetFields();
      await load();
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const message =
          typeof err.response?.data?.error === 'string'
            ? err.response.data.error
            : 'Failed to create FX rate';
        notification.error({ message });
      }
      // validation errors from Form are surfaced inline
    } finally {
      setSaving(false);
    }
  };

  const columns: ColumnsType<FxRate> = useMemo(
    () => [
      {
        title: 'Currency Pair',
        dataIndex: 'currencyPair',
        key: 'currencyPair',
      },
      {
        title: 'Rate',
        dataIndex: 'rate',
        key: 'rate',
        align: 'right',
        render: (v: number) =>
          Number(v).toLocaleString(undefined, {
            minimumFractionDigits: 4,
            maximumFractionDigits: 4,
          }),
      },
      {
        title: 'Effective From',
        dataIndex: 'effectiveFrom',
        key: 'effectiveFrom',
        render: (d: string) => formatDate(d),
      },
      {
        title: 'Effective To',
        dataIndex: 'effectiveTo',
        key: 'effectiveTo',
        render: (d: string | null) => (d ? formatDate(d) : ''),
      },
      {
        title: 'Created By',
        dataIndex: 'createdBy',
        key: 'createdBy',
        render: (v: string) => <Text style={{ color: '#555555' }}>{v}</Text>,
      },
    ],
    [formatDate],
  );

  return (
    <div style={{ marginBottom: 40 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'flex-end',
          justifyContent: 'space-between',
          gap: 16,
          marginBottom: 16,
        }}
      >
        <div>
          <div
            style={{
              fontFamily: HEADING_FONT,
              fontWeight: 700,
              fontSize: 17,
              color: '#232323',
            }}
          >
            FX Rates
          </div>
          <div style={{ fontSize: 13, color: '#888888', marginTop: 3 }}>
            Effective-dated currency exchange rates used for USD→INR conversions.
          </div>
        </div>
        <Space wrap>
          <AdminGate>
            <Button type="primary" icon={<PlusOutlined />} onClick={openAddModal}>
              Add FX Rate
            </Button>
          </AdminGate>
          <Button
            icon={<ExportOutlined />}
            onClick={() => {
              exportFxRates().catch(() =>
                notification.error({ message: 'Failed to export FX rates' }),
              );
            }}
          >
            Export
          </Button>
          <Button
            icon={<DownloadOutlined />}
            onClick={() => {
              downloadFxRateImportSample().catch(() =>
                notification.error({ message: 'Failed to download sample file' }),
              );
            }}
          >
            Download Sample File
          </Button>
          <AdminGate>
            <Button icon={<UploadOutlined />} onClick={() => setImportOpen(true)}>
              Import
            </Button>
          </AdminGate>
        </Space>
      </div>

      {loading ? (
        <Skeleton active paragraph={{ rows: 4 }} />
      ) : (
        <Table<FxRate>
          rowKey="id"
          columns={columns}
          dataSource={rates}
          pagination={false}
          size="middle"
          bordered={false}
          style={{ background: '#ffffff', borderRadius: 8, border: '1px solid #d8d8d8' }}
        />
      )}

      <Alert
        type="info"
        showIcon
        style={{ marginTop: 12 }}
        message="Currency Pair format: USD_INR. Creating a new rate for the same pair automatically closes the current active rate."
      />

      <Modal
        title="Add FX Rate"
        open={addOpen}
        onCancel={() => {
          setAddOpen(false);
          form.resetFields();
        }}
        onOk={handleAdd}
        okText="Save"
        confirmLoading={saving}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
          <Form.Item
            name="currencyPair"
            label="Currency Pair"
            rules={[
              { required: true, message: 'Currency pair is required' },
              { max: 10, message: 'At most 10 characters' },
            ]}
          >
            <Input placeholder="USD_INR" />
          </Form.Item>
          <Form.Item
            name="rate"
            label="Rate"
            rules={[{ required: true, message: 'Rate is required' }]}
          >
            <InputNumber
              style={{ width: '100%' }}
              min={0.0001}
              step={0.0001}
              precision={4}
              placeholder="e.g. 83.2500"
            />
          </Form.Item>
          <Form.Item
            name="effectiveFrom"
            label="Effective From"
            rules={[{ required: true, message: 'Effective from is required' }]}
          >
            <DatePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
          </Form.Item>
        </Form>
      </Modal>

      <SimpleExcelImportModal
        open={importOpen}
        title="Import FX Rates"
        onClose={() => setImportOpen(false)}
        onImported={load}
        importFile={importFxRates}
      />
    </div>
  );
}
