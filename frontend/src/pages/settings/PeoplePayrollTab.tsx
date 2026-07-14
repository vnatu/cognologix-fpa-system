import { Space, Typography } from 'antd';
import { HEADING_FONT } from '@/theme/antdTheme';
import ClassificationRulesSection from '@/pages/people/settings/ClassificationRulesSection';
import ColumnMappingTemplatesSection from '@/pages/people/settings/ColumnMappingTemplatesSection';

const { Title } = Typography;

export default function PeoplePayrollTab() {
  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <div>
        <Title level={5} style={{ fontFamily: HEADING_FONT, marginBottom: 12 }}>
          Classification Rules
        </Title>
        <ClassificationRulesSection />
      </div>
      <div>
        <Title level={5} style={{ fontFamily: HEADING_FONT, marginBottom: 12 }}>
          Column Mapping Templates
        </Title>
        <ColumnMappingTemplatesSection />
      </div>
    </Space>
  );
}
