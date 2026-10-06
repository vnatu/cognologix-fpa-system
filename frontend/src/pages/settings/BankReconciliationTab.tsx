import { Typography } from 'antd';
import { HEADING_FONT } from '@/theme/antdTheme';
import ColumnMappingTemplatesSection from '@/pages/bank-reconciliation/settings/ColumnMappingTemplatesSection';

const { Title, Text } = Typography;

export default function BankReconciliationTab() {
  return (
    <div>
      <Title level={5} style={{ fontFamily: HEADING_FONT, marginBottom: 8 }}>
        Column Mapping Templates
      </Title>
      <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
        HDFC bank statement header and transaction column mappings (ADR-019).
      </Text>
      <ColumnMappingTemplatesSection />
    </div>
  );
}
