import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Skeleton,
  Table,
  Tag,
  Typography,
  notification,
  theme,
} from 'antd';
import { EyeOutlined, LockOutlined, UnlockOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { formatCurrency } from '@/utils/formatDate';
import { HEADING_FONT } from '@/theme/antdTheme';
import { fetchExpenseHistory } from './api';
import type { MonthHistory } from './types';

const { Text } = Typography;

const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

export default function ExpenseHistoryPage() {
  const navigate = useNavigate();
  const { token } = theme.useToken();
  const [loading, setLoading] = useState(true);
  const [rows, setRows] = useState<MonthHistory[]>([]);

  useEffect(() => {
    setLoading(true);
    fetchExpenseHistory()
      .then(setRows)
      .catch(() => notification.error({ message: 'Failed to load expense history' }))
      .finally(() => setLoading(false));
  }, []);

  const columns: ColumnsType<MonthHistory> = [
    {
      title: 'Month',
      key: 'month',
      render: (_, r) => MONTH_NAMES[r.month - 1] ?? r.month,
    },
    {
      title: 'Year',
      dataIndex: 'year',
      key: 'year',
    },
    {
      title: 'Total Amount (Rs Lakhs)',
      key: 'total',
      align: 'right',
      render: (_, r) => formatCurrency(Number(r.totalAmount)),
    },
    {
      title: 'Lock Status',
      key: 'locked',
      render: (_, r) =>
        r.locked ? (
          <Tag color="error" icon={<LockOutlined />}>
            Locked
          </Tag>
        ) : (
          <Tag color="success" icon={<UnlockOutlined />}>
            Open
          </Tag>
        ),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, r) => (
        <Button
          type="link"
          icon={<EyeOutlined />}
          onClick={() =>
            navigate(`/expenses/entry?month=${r.month}&year=${r.year}`)
          }
        >
          View
        </Button>
      ),
    },
  ];

  return (
    <div style={{ padding: 28, maxWidth: 960 }}>
      <div style={{ marginBottom: 20 }}>
        <div
          style={{
            fontFamily: HEADING_FONT,
            fontWeight: 700,
            fontSize: 22,
            color: token.colorTextHeading,
          }}
        >
          Expense History
        </div>
        <Text type="secondary">Months with recorded expense actuals</Text>
      </div>

      {loading ? (
        <Skeleton active paragraph={{ rows: 6 }} />
      ) : (
        <Table<MonthHistory>
          rowKey={(r) => `${r.year}-${r.month}`}
          columns={columns}
          dataSource={rows}
          pagination={false}
          locale={{ emptyText: 'No expense months yet' }}
        />
      )}
    </div>
  );
}
