import { useEffect, useState } from 'react';
import { Button, Input, Select, Table, Typography, notification } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { AdminGate } from '@/components/AdminGate';
import { HEADING_FONT } from '@/theme/antdTheme';
import { deleteMapping, fetchMappings } from '../api';
import type { LearnedMapping, VoucherType } from '../types';

const { Title, Text } = Typography;

export default function LearnedMappingsPage() {
  const [search, setSearch] = useState('');
  const [voucherType, setVoucherType] = useState<VoucherType | undefined>();
  const [rows, setRows] = useState<LearnedMapping[]>([]);
  const [loading, setLoading] = useState(true);

  const load = () => {
    setLoading(true);
    fetchMappings(search, 0, 200)
      .then((p) => {
        const filtered = voucherType
          ? p.content.filter((m) => m.voucherType === voucherType)
          : p.content;
        setRows(filtered);
      })
      .catch(() => notification.error({ message: 'Failed to load mappings' }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [search, voucherType]);

  const columns: ColumnsType<LearnedMapping> = [
    { title: 'Narration', dataIndex: 'normalisedNarration' },
    { title: 'Voucher', dataIndex: 'voucherType', width: 110 },
    { title: 'Ledger', dataIndex: 'ledgerName' },
    { title: 'Uses', dataIndex: 'useCount', width: 80 },
    {
      title: '',
      width: 90,
      render: (_, r) => (
        <AdminGate>
          <Button
            danger
            type="link"
            onClick={() =>
              deleteMapping(r.id).then(() => {
                notification.success({ message: 'Mapping deleted' });
                load();
              })
            }
          >
            Delete
          </Button>
        </AdminGate>
      ),
    },
  ];

  return (
    <div style={{ padding: 28 }}>
      <Title level={3} style={{ fontFamily: HEADING_FONT }}>
        Learned mappings
      </Title>
      <Text type="secondary">
        Not editable here. Correct a mapping on a run review to teach FinSync.
      </Text>
      <div style={{ display: 'flex', gap: 12, margin: '16px 0' }}>
        <Input.Search placeholder="Search narration or ledger" allowClear onSearch={setSearch} style={{ maxWidth: 320 }} />
        <Select
          allowClear
          placeholder="Voucher type"
          style={{ width: 160 }}
          value={voucherType}
          onChange={setVoucherType}
          options={[
            { value: 'PAYMENT', label: 'Payment' },
            { value: 'RECEIPT', label: 'Receipt' },
            { value: 'CONTRA', label: 'Contra' },
          ]}
        />
      </div>
      <Table rowKey="id" loading={loading} dataSource={rows} columns={columns} />
    </div>
  );
}
