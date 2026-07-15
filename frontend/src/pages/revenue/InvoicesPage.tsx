import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Button,
  Collapse,
  Empty,
  InputNumber,
  Select,
  Skeleton,
  Space,
  Table,
  Tag,
  Typography,
  notification,
} from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import * as XLSX from 'xlsx';
import { HEADING_FONT } from '@/theme/antdTheme';
import { useDateFormat } from '@/context/DateFormatContext';
import { formatCurrency } from '@/utils/formatDate';
import { fetchCustomers } from '@/pages/customers/api';
import type { CustomerSummary } from '@/pages/customers/types';
import { fetchInvoices, fetchUploadsForPeriod } from './api';
import {
  IMPORT_TYPE_LABELS,
  INVOICE_STATUS_OPTIONS,
  MONTH_OPTIONS,
  STATUS_TAG_COLOR,
} from './constants';
import type {
  InvoiceListItem,
  RevenueImportType,
  UploadSummary,
} from './types';

const { Title, Text } = Typography;

function periodLabel(month: number, year: number): string {
  const m = MONTH_OPTIONS.find((o) => o.value === month)?.label ?? String(month);
  return `${m} ${year}`;
}

export default function InvoicesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { formatDate, formatDateTime } = useDateFormat();

  const [customers, setCustomers] = useState<CustomerSummary[]>([]);
  const [rows, setRows] = useState<InvoiceListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [uploads, setUploads] = useState<UploadSummary[]>([]);
  const [uploadsLoading, setUploadsLoading] = useState(false);

  const customerId = searchParams.get('customerId') ?? undefined;
  const periodMonth = searchParams.get('periodMonth')
    ? Number(searchParams.get('periodMonth'))
    : undefined;
  const periodYear = searchParams.get('periodYear')
    ? Number(searchParams.get('periodYear'))
    : undefined;
  const status = searchParams.get('status') ?? undefined;
  const importType = (searchParams.get('importType') as RevenueImportType | null)
    ?? undefined;

  const updateFilter = (key: string, value: string | number | undefined) => {
    const next = new URLSearchParams(searchParams);
    if (value == null || value === '') next.delete(key);
    else next.set(key, String(value));
    setPage(0);
    setSearchParams(next);
  };

  const loadCustomers = useCallback(async () => {
    try {
      setCustomers(await fetchCustomers(false));
    } catch {
      notification.error({ message: 'Failed to load customers' });
    }
  }, []);

  const loadInvoices = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchInvoices({
        customerId,
        periodMonth,
        periodYear,
        status,
        importType,
        page,
        size: 20,
      });
      setRows(data.content);
      setTotal(data.totalElements);
    } catch {
      notification.error({ message: 'Failed to load invoices' });
    } finally {
      setLoading(false);
    }
  }, [customerId, periodMonth, periodYear, status, importType, page]);

  const loadUploads = useCallback(async () => {
    if (periodMonth == null || periodYear == null) {
      setUploads([]);
      return;
    }
    setUploadsLoading(true);
    try {
      setUploads(await fetchUploadsForPeriod(periodMonth, periodYear));
    } catch {
      notification.error({ message: 'Failed to load upload history' });
    } finally {
      setUploadsLoading(false);
    }
  }, [periodMonth, periodYear]);

  useEffect(() => {
    loadCustomers();
  }, [loadCustomers]);

  useEffect(() => {
    loadInvoices();
  }, [loadInvoices]);

  useEffect(() => {
    loadUploads();
  }, [loadUploads]);

  const exportExcel = async () => {
    try {
      const data = await fetchInvoices({
        customerId,
        periodMonth,
        periodYear,
        status,
        importType,
        page: 0,
        size: Math.max(total, 1),
      });
      const sheetRows = data.content.map((r) => ({
        Type:
          r.importType === 'ZOHO_BOOKS_INVOICES' ? 'Invoice' : 'Credit Note',
        'Document #': r.documentNumber,
        Client: r.customerId,
        Period: periodLabel(r.periodMonth, r.periodYear),
        Date: r.documentDate ?? '',
        Currency: r.currency,
        Amount: r.amount,
        'INR Equivalent': r.amountInr ?? '',
        Status: r.status ?? '',
        Balance: r.balance ?? '',
      }));
      const ws = XLSX.utils.json_to_sheet(sheetRows);
      const wb = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(wb, ws, 'Invoices');
      XLSX.writeFile(wb, 'revenue_invoices_export.xlsx');
    } catch {
      notification.error({ message: 'Export failed' });
    }
  };

  const columns: ColumnsType<InvoiceListItem> = useMemo(
    () => [
      {
        title: 'Type',
        dataIndex: 'importType',
        key: 'type',
        width: 120,
        render: (t: RevenueImportType) =>
          t === 'ZOHO_BOOKS_INVOICES' ? (
            <Tag color="blue">Invoice</Tag>
          ) : (
            <Tag color="orange">Credit Note</Tag>
          ),
      },
      {
        title: 'Invoice/Credit Note #',
        dataIndex: 'documentNumber',
        key: 'documentNumber',
      },
      { title: 'Client', dataIndex: 'customerId', key: 'customerId' },
      {
        title: 'Period',
        key: 'period',
        render: (_, r) => periodLabel(r.periodMonth, r.periodYear),
      },
      {
        title: 'Date',
        dataIndex: 'documentDate',
        key: 'documentDate',
        render: (d: string | null) => formatDate(d),
      },
      { title: 'Currency', dataIndex: 'currency', key: 'currency', width: 90 },
      {
        title: 'Amount',
        dataIndex: 'amount',
        key: 'amount',
        align: 'right',
        render: (v: number) => formatCurrency(v),
      },
      {
        title: 'INR Equivalent',
        dataIndex: 'amountInr',
        key: 'amountInr',
        align: 'right',
        render: (v: number | null) => formatCurrency(v),
      },
      {
        title: 'Status',
        dataIndex: 'status',
        key: 'status',
        render: (s: string | null) =>
          s ? (
            <Tag color={STATUS_TAG_COLOR[s] ?? 'default'}>{s}</Tag>
          ) : (
            '—'
          ),
      },
      {
        title: 'Balance',
        dataIndex: 'balance',
        key: 'balance',
        align: 'right',
        render: (v: number | null) => (v == null ? '—' : formatCurrency(v)),
      },
    ],
    [formatDate],
  );

  const uploadColumns: ColumnsType<UploadSummary> = [
    {
      title: 'Type',
      dataIndex: 'importType',
      key: 'importType',
      render: (t: RevenueImportType) => IMPORT_TYPE_LABELS[t],
    },
    { title: 'Version', dataIndex: 'versionNumber', key: 'versionNumber', width: 80 },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s: string) => (
        <Tag color={s === 'ACTIVE' ? 'green' : 'default'}>{s}</Tag>
      ),
    },
    { title: 'Uploaded by', dataIndex: 'uploadedBy', key: 'uploadedBy' },
    {
      title: 'Uploaded at',
      dataIndex: 'uploadedAt',
      key: 'uploadedAt',
      render: (d: string) => formatDateTime(d),
    },
    { title: 'Filename', dataIndex: 'originalFilename', key: 'originalFilename' },
    { title: 'Rows', dataIndex: 'rowCount', key: 'rowCount', width: 70 },
  ];

  const onTableChange = (pagination: TablePaginationConfig) => {
    setPage((pagination.current ?? 1) - 1);
  };

  return (
    <div style={{ padding: 24 }}>
      <Space
        style={{
          width: '100%',
          justifyContent: 'space-between',
          marginBottom: 16,
        }}
      >
        <Title level={4} style={{ fontFamily: HEADING_FONT, margin: 0 }}>
          Invoice List
        </Title>
        <Button icon={<DownloadOutlined />} onClick={exportExcel}>
          Export
        </Button>
      </Space>

      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          placeholder="Client"
          style={{ width: 220 }}
          value={customerId}
          options={customers.map((c) => ({
            value: c.customerCode,
            label: `${c.customerName} (${c.customerCode})`,
          }))}
          onChange={(v) => updateFilter('customerId', v)}
        />
        <Select
          allowClear
          placeholder="Period Month"
          style={{ width: 150 }}
          value={periodMonth}
          options={MONTH_OPTIONS}
          onChange={(v) => updateFilter('periodMonth', v)}
        />
        <InputNumber
          placeholder="Year"
          style={{ width: 110 }}
          value={periodYear}
          min={2000}
          max={2100}
          onChange={(v) => updateFilter('periodYear', v ?? undefined)}
        />
        <Select
          allowClear
          placeholder="Status"
          style={{ width: 160 }}
          value={status}
          options={INVOICE_STATUS_OPTIONS.map((s) => ({ value: s, label: s }))}
          onChange={(v) => updateFilter('status', v)}
        />
        <Select
          allowClear
          placeholder="Type"
          style={{ width: 150 }}
          value={importType}
          options={[
            { value: 'ZOHO_BOOKS_INVOICES', label: 'Invoice' },
            { value: 'ZOHO_BOOKS_CREDIT_NOTES', label: 'Credit Note' },
          ]}
          onChange={(v) => updateFilter('importType', v)}
        />
      </Space>

      {loading ? (
        <Skeleton active paragraph={{ rows: 8 }} />
      ) : (
        <Table
          rowKey="id"
          size="small"
          columns={columns}
          dataSource={rows}
          locale={{ emptyText: <Empty description="No invoices found" /> }}
          pagination={{
            current: page + 1,
            pageSize: 20,
            total,
            showSizeChanger: false,
          }}
          onChange={onTableChange}
        />
      )}

      <Collapse
        style={{ marginTop: 24 }}
        items={[
          {
            key: 'uploads',
            label: 'Upload history',
            children:
              periodMonth == null || periodYear == null ? (
                <Text type="secondary">
                  Select a period month and year to view upload audit history.
                </Text>
              ) : uploadsLoading ? (
                <Skeleton active paragraph={{ rows: 3 }} />
              ) : (
                <Table
                  rowKey="id"
                  size="small"
                  columns={uploadColumns}
                  dataSource={uploads}
                  pagination={false}
                  locale={{
                    emptyText: (
                      <Empty description="No uploads for this period" />
                    ),
                  }}
                />
              ),
          },
        ]}
      />
    </div>
  );
}
